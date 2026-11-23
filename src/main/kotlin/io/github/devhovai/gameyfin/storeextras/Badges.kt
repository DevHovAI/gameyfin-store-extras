package io.github.devhovai.gameyfin.storeextras

import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * shields.io static badges as Markdown. Gameyfin renders descriptions as Markdown (raw HTML is dropped), so a linked
 * image is the only way to show a coloured badge.
 */
object Badges {
    private const val BASE = "https://img.shields.io/badge/"
    const val STEAM_COLOR = "1b2838"
    const val GOG_COLOR = "86328a"
    const val MODE_COLOR = "4c566a"

    /** Escapes one path segment of a static badge URL: `-` -> `--`, `_` -> `__`, everything else outside A-Za-z0-9.~ percent-encoded. */
    fun segment(text: String): String {
        val sb = StringBuilder()
        for (b in text.toByteArray(StandardCharsets.UTF_8)) {
            val c = (b.toInt() and 0xFF).toChar()
            when {
                c == '-' -> sb.append("--")
                c == '_' -> sb.append("__")
                c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '.' || c == '~' -> sb.append(c)
                else -> sb.append('%').append("%02X".format(b.toInt() and 0xFF))
            }
        }
        return sb.toString()
    }

    /** Image URL of a badge; without a [label] the badge only shows [message]. */
    fun imageUrl(label: String?, message: String, color: String, logo: String? = null): String {
        val path = if (label == null) "${segment(message)}-$color" else "${segment(label)}-${segment(message)}-$color"
        return BASE + path + (logo?.let { "?logo=$it" } ?: "")
    }

    /** `![alt](image)`, wrapped in a link to [href] when given. */
    fun markdown(label: String?, message: String, color: String, logo: String? = null, href: String? = null): String {
        val alt = listOfNotNull(label, message).joinToString(" ").replace(Regex("""[\[\]\\]"""), "")
        val image = "![$alt](${imageUrl(label, message, color, logo)})"
        return if (href == null) image else "[$image](${href.replace("(", "%28").replace(")", "%29").replace(" ", "%20")})"
    }

    fun steam(percent: Int, sentiment: String?, appId: Int): String =
        markdown("Steam", listOfNotNull("$percent%", sentiment).joinToString(" "), STEAM_COLOR, "steam", "https://store.steampowered.com/app/$appId/")

    /** [rating] is GOG's 0..50 review score. */
    fun gog(rating: Int?, storeLink: String): String =
        markdown("GOG", rating?.let { String.format(Locale.ROOT, "%.1f/5", it / 10.0) } ?: "Store page", GOG_COLOR, "gogdotcom", storeLink)

    fun metacritic(score: Int, url: String?): String {
        val color = when {
            score >= 75 -> "66cc33"
            score >= 50 -> "ffcc33"
            else -> "ff0000"
        }
        return markdown("Metacritic", score.toString(), color, "metacritic", url)
    }

    fun steamDeckVerified(): String = markdown("Steam Deck", "Verified", "2da44e", "steamdeck")

    fun mode(label: String): String = markdown(null, label, MODE_COLOR)

    /** Badge line, a blank line, then the body; null when there is no body so that lower priority descriptions still show. */
    fun description(badges: List<String>, body: String?): String? {
        val text = body?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (badges.isEmpty()) text else badges.joinToString(" ") + "\n\n" + text
    }
}
