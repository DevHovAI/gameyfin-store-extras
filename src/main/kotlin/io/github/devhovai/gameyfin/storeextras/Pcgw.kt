package io.github.devhovai.gameyfin.storeextras

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * One way of playing together, as listed in the `Network/Multiplayer` template of a PCGamingWiki page.
 * [kind] is "Local", "LAN" or "Online"; [players] is a count ("4"), a range ("2–4") or null when unknown.
 * Only facts (yes/no, counts, mode names) are taken from the wiki, never its prose.
 */
data class MultiplayerMode(val kind: String, val players: String?, val coop: Boolean, val versus: Boolean)

object PcgwParser {
    private val kinds = listOf("local" to "Local", "lan" to "LAN", "online" to "Online")
    private val range = Regex("""^(\d{1,4})\s*[-–—]\s*(\d{1,4})$""")
    private val single = Regex("""^(\d{1,4})\+?$""")

    /** Steam app ids / GOG ids named in the infobox (`|steam appid = 108600`, may list several). */
    fun ids(wikitext: String, field: String): Set<String> =
        Regex("""(?m)^\|\s*$field\s*=(.*)$""").findAll(wikitext)
            .flatMap { Regex("""\d+""").findAll(it.groupValues[1]).map { m -> m.value } }.toSet()

    /** "4" -> "4", "2-4" -> "2–4", "unknown", "" and "0" -> null. */
    fun players(raw: String?): String? {
        val v = raw?.trim().orEmpty()
        range.matchEntire(v)?.let { m ->
            val (a, b) = m.groupValues[1].toInt() to m.groupValues[2].toInt()
            return if (a == b) a.toString().takeIf { a > 0 } else "${minOf(a, b)}–${maxOf(a, b)}"
        }
        return single.matchEntire(v)?.let { m -> m.value.takeIf { m.groupValues[1].toInt() > 0 } }
    }

    /** Modes in the order local, LAN, online; only those marked `true`. Empty when the page has no such template. */
    fun modes(wikitext: String): List<MultiplayerMode> {
        val start = wikitext.indexOf("{{Network/Multiplayer")
        if (start < 0) return emptyList()
        val end = wikitext.indexOf("\n}}", start).let { if (it < 0) wikitext.length else it }
        val fields = HashMap<String, String>()
        for (line in wikitext.substring(start, end).lines()) {
            val m = Regex("""^\|\s*([^=]+?)\s*=(.*)$""").matchEntire(line.trim()) ?: continue
            fields[m.groupValues[1].lowercase()] = m.groupValues[2].replace(Regex("""<!--.*?-->|<ref.*?(/>|</ref>)"""), "").trim()
        }
        return kinds.mapNotNull { (key, label) ->
            if (fields["$key play"]?.lowercase() != "true") return@mapNotNull null
            val text = fields["$key play modes"].orEmpty().lowercase()
            MultiplayerMode(
                kind = label,
                players = players(fields["$key play players"]),
                coop = listOf("co-op", "coop", "cooperative").any(text::contains),
                versus = listOf("versus", "pvp").any(text::contains),
            )
        }
    }
}

/**
 * Looks up multiplayer player counts on PCGamingWiki (MediaWiki API, no key needed; Cargo is closed to anonymous
 * users). The page is found through PCGamingWiki's app id redirect (`api/appid.php`, `api/gog.php`), falling back to
 * a title search; every candidate is checked against the id in its infobox. Results, including "nothing found" and
 * failures, are cached; the fetcher keeps at least one second between requests. Never throws.
 */
class PcgwClient(
    private val http: HttpFetcher,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Entry(val modes: List<MultiplayerMode>, val expiresAt: Long)

    private val cache = ConcurrentHashMap<String, Entry>()

    fun multiplayer(steamAppId: Int?, gogId: String?, title: String): List<MultiplayerMode> {
        val key = when {
            steamAppId != null -> "steam:$steamAppId"
            gogId != null -> "gog:$gogId"
            else -> return emptyList()
        }
        cache[key]?.takeIf { it.expiresAt > clock() }?.let { return it.modes }
        val found = try {
            lookup(steamAppId, gogId, title)
        } catch (_: Exception) {
            null
        }
        cache[key] = Entry(found.orEmpty(), clock() + if (found == null) FAILURE_TTL_MS else TTL_MS)
        return found.orEmpty()
    }

    /** Null on failure or when no page with a matching id was found. */
    private fun lookup(steamAppId: Int?, gogId: String?, title: String): List<MultiplayerMode>? {
        val (idUrl, field, id) = if (steamAppId != null) Triple("$SITE/api/appid.php?appid=$steamAppId", "steam appid", steamAppId.toString())
        else Triple("$SITE/api/gog.php?page=${enc(gogId!!)}", "gogcom id", gogId)
        val candidates = LinkedHashSet<String>()
        val redirect = http.get(idUrl) ?: return null
        pageTitle(redirect)?.let { candidates += it }
        if (candidates.isEmpty() && redirect.status == 200) candidates += search(title)
        for (page in candidates.take(MAX_CANDIDATES)) {
            val wikitext = wikitext(page) ?: continue
            if (id in PcgwParser.ids(wikitext, field)) return PcgwParser.modes(wikitext)
        }
        return null
    }

    private fun pageTitle(res: HttpResult): String? {
        if (res.status !in 300..399) return null
        val path = res.location?.substringBefore('#')?.substringBefore('?')?.substringAfter("/wiki/", "") ?: return null
        return URLDecoder.decode(path, StandardCharsets.UTF_8).replace('_', ' ').trim().ifEmpty { null }
    }

    private fun search(title: String): List<String> {
        val res = http.get("$SITE/w/api.php?action=query&list=search&srsearch=${enc(title)}&srlimit=$MAX_CANDIDATES&format=json&formatversion=2")
            ?.takeIf { it.ok } ?: return emptyList()
        val hits = parseObject(res.body)?.get("query").obj()?.get("search").arr() ?: return emptyList()
        return hits.mapNotNull { it.obj()?.get("title").str() }
    }

    private fun wikitext(page: String): String? {
        val res = http.get("$SITE/w/api.php?action=parse&page=${enc(page)}&prop=wikitext&redirects=1&format=json&formatversion=2")
            ?.takeIf { it.ok } ?: return null
        return parseObject(res.body)?.get("parse").obj()?.get("wikitext").str()
    }

    companion object {
        const val SITE = "https://www.pcgamingwiki.com"
        const val MAX_CANDIDATES = 2
        const val TTL_MS = 24L * 60 * 60 * 1000
        const val FAILURE_TTL_MS = 60L * 60 * 1000
    }
}
