package io.github.devhovai.gameyfin.storeextras

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import org.gameyfin.pluginapi.gamemetadata.Platform
import java.net.URLEncoder
import java.time.Instant
import java.nio.charset.StandardCharsets

data class SteamHit(val id: Int, val name: String)

data class SteamMovie(
    val name: String?,
    val mp4: String?,
    val webm: String?,
    val hls: String?,
    val dash: String?,
) {
    /** mp4 plays everywhere; Steam currently only publishes HLS/DASH, which Gameyfin's player handles as well. */
    val bestUrl: String? get() = mp4 ?: webm ?: hls ?: dash
}

data class SteamApp(
    val id: Int,
    val name: String,
    val type: String,
    val platforms: Set<Platform>,
    val developers: Set<String>,
    val publishers: Set<String>,
    val headerImage: String?,
    val metacritic: Int?,
    val movies: List<SteamMovie>,
    val release: Instant?,
    val categories: Set<Int> = emptySet(),
    val metacriticUrl: String? = null,
    /** Store page text as HTML: `detailed_description`, else `about_the_game`, else `short_description`. */
    val descriptionHtml: String? = null,
)

data class ReviewCounts(val positive: Long, val negative: Long, val source: String) {
    val total: Long get() = positive + negative

    /** Share of positive reviews as 0..100, or null when there are too few votes to mean anything. */
    fun percent(minVotes: Long = MIN_VOTES): Int? =
        if (total < minVotes) null else Math.round(100.0 * positive / total).toInt()

    /** Steam's label for the score ("Very Positive"), derived the way the store does it; null when there are too few votes. */
    fun sentiment(): String? {
        val percent = percent() ?: return null
        val many = total >= 500
        val some = total >= 50
        return when {
            percent >= 95 && many -> "Overwhelmingly Positive"
            percent >= 80 && some -> "Very Positive"
            percent >= 80 -> "Positive"
            percent >= 70 -> "Mostly Positive"
            percent >= 40 -> "Mixed"
            percent >= 20 -> "Mostly Negative"
            many -> "Overwhelmingly Negative"
            some -> "Very Negative"
            else -> "Negative"
        }
    }

    companion object {
        const val MIN_VOTES = 10L
    }
}

internal val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal fun parseObject(body: String): JsonObject? =
    try { lenientJson.parseToJsonElement(body) as? JsonObject } catch (_: Exception) { null }

internal fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
internal fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.let { it.intOrNull ?: it.contentOrNull?.toIntOrNull() }
internal fun JsonElement?.long(): Long? = (this as? JsonPrimitive)?.contentOrNull?.toLongOrNull()
internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject
internal fun JsonElement?.arr(): JsonArray? = this as? JsonArray
internal fun JsonElement?.strings(): Set<String> = arr()?.mapNotNull { it.str() }?.toSet() ?: emptySet()

internal fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

class SteamClient(
    private val http: HttpFetcher,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Only ever consulted before the `appreviews` endpoint; search, appdetails and the rest never look at it. */
    private val reviewsCooldown = Cooldown(clock)

    fun search(term: String): List<SteamHit> {
        val res = http.get("https://store.steampowered.com/api/storesearch?term=${enc(term)}&cc=en&l=en") ?: return emptyList()
        if (!res.ok) return emptyList()
        val items = parseObject(res.body)?.get("items").arr() ?: return emptyList()
        return items.mapNotNull { item ->
            val o = item.obj() ?: return@mapNotNull null
            val id = o["id"].int() ?: return@mapNotNull null
            val name = o["name"].str() ?: return@mapNotNull null
            if (o["type"].str().let { it != null && it != "app" }) null else SteamHit(id, name)
        }
    }

    fun details(appId: Int): SteamApp? {
        val res = http.get("https://store.steampowered.com/api/appdetails?appids=$appId&cc=en&l=en") ?: return null
        if (!res.ok) return null
        val entry = parseObject(res.body)?.get(appId.toString()).obj() ?: return null
        if (entry["success"].toString() != "true") return null
        val d = entry["data"].obj() ?: return null
        val platforms = d["platforms"].obj()?.let { p ->
            buildSet {
                if (p["windows"].toString() == "true") add(Platform.PC_MICROSOFT_WINDOWS)
                if (p["linux"].toString() == "true") add(Platform.LINUX)
                if (p["mac"].toString() == "true") add(Platform.MAC)
            }
        } ?: emptySet()
        val movies = d["movies"].arr()?.mapNotNull { m ->
            val o = m.obj() ?: return@mapNotNull null
            SteamMovie(
                name = o["name"].str(),
                mp4 = o["mp4"].obj()?.let { it["max"].str() ?: it["480"].str() },
                webm = o["webm"].obj()?.let { it["max"].str() ?: it["480"].str() },
                hls = o["hls_h264"].str(),
                dash = o["dash_h264"].str(),
            ).takeIf { it.bestUrl != null }
        } ?: emptyList()
        return SteamApp(
            id = appId,
            name = d["name"].str() ?: return null,
            type = d["type"].str() ?: "",
            platforms = platforms,
            developers = d["developers"].strings(),
            publishers = d["publishers"].strings(),
            headerImage = d["header_image"].str(),
            metacritic = d["metacritic"].obj()?.get("score").int(),
            movies = movies,
            release = ReleaseDates.parseSteam(d["release_date"].obj()?.get("date").str()),
            categories = d["categories"].arr()?.mapNotNull { it.obj()?.get("id").int() }?.toSet() ?: emptySet(),
            metacriticUrl = d["metacritic"].obj()?.get("url").str()?.substringBefore('?')?.takeIf { it.startsWith("https://") },
            descriptionHtml = listOf("detailed_description", "about_the_game", "short_description")
                .firstNotNullOfOrNull { key -> d[key].str()?.takeIf { it.isNotBlank() } },
        )
    }

    /** True when Steam serves the 1920x620 library hero image for this app (not every app has one). */
    fun heroUrl(appId: Int): String? =
        "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/$appId/library_hero.jpg"
            .takeIf { http.exists(it) }

    /**
     * Review counts from Steam's own endpoint; if that is refused or redirected (it is, from some networks),
     * fall back to SteamSpy and stop asking Steam for an hour.
     */
    fun reviews(appId: Int): ReviewCounts? = steamReviews(appId) ?: steamSpyReviews(appId)

    private fun steamReviews(appId: Int): ReviewCounts? {
        if (reviewsCooldown.active) return null
        val res = http.get("https://store.steampowered.com/appreviews/$appId?json=1&language=all&purchase_type=all&num_per_page=0")
        val summary = res?.takeIf { it.ok }?.let { parseObject(it.body) }?.get("query_summary").obj()
        val positive = summary?.get("total_positive").long()
        val negative = summary?.get("total_negative").long()
        if (positive == null || negative == null) {
            reviewsCooldown.start()
            return null
        }
        return ReviewCounts(positive, negative, "steam")
    }

    private fun steamSpyReviews(appId: Int): ReviewCounts? {
        val res = http.get("https://steamspy.com/api.php?request=appdetails&appid=$appId")?.takeIf { it.ok } ?: return null
        val o = parseObject(res.body) ?: return null
        val positive = o["positive"].long() ?: return null
        val negative = o["negative"].long() ?: return null
        return ReviewCounts(positive, negative, "steamspy")
    }

    /** True when the Steam Deck compatibility report lists the game as "Verified". */
    fun deckVerified(appId: Int): Boolean {
        val res = http.get("https://store.steampowered.com/saleaction/ajaxgetdeckappcompatibilityreport?nAppID=$appId")?.takeIf { it.ok }
        return parseObject(res?.body ?: return false)?.get("results").obj()?.get("resolved_category").int() == DECK_VERIFIED
    }

    private companion object {
        const val DECK_VERIFIED = 3
    }
}

/** A one-shot pause: [start] makes [active] true for [millis] on the given clock. */
class Cooldown(private val clock: () -> Long, private val millis: Long = 60 * 60 * 1000L) {
    @Volatile
    private var until = 0L

    val active: Boolean get() = clock() < until

    fun start() {
        until = clock() + millis
    }
}
