package io.github.devhovai.gameyfin.storeextras

import org.gameyfin.pluginapi.gamemetadata.GameMetadata
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Gameyfin merges search results of different providers when `title.normalizeGameTitle()` and the release year
 * (in the system time zone) are equal (GameService.groupKey). These tests rebuild that key, so a result that
 * would land in a group of its own fails here.
 */
class GroupingCompatibilityTest {
    private fun String.normalizeGameTitle(): String =
        filter { it.isLetterOrDigit() || it.isWhitespace() }.lowercase().replaceRomanNumerals().replace(Regex("\\s+"), " ")

    // Copy of Gameyfin's String.replaceRomanNumerals (app/core/Utils.kt), simplified to the numbers games use.
    private fun String.replaceRomanNumerals(): String =
        Regex("""(?<=\s|^)([MDCLXVI]+)(?=\s|$)""", RegexOption.IGNORE_CASE).replace(this) { m ->
            val values = m.value.uppercase().map { mapOf('M' to 1000, 'D' to 500, 'C' to 100, 'L' to 50, 'X' to 10, 'V' to 5, 'I' to 1)[it]!! }
            val n = values.indices.sumOf { i -> if (i + 1 < values.size && values[i] < values[i + 1]) -values[i] else values[i] }
            if (n in 1..3999) n.toString() else m.value
        }

    private fun key(m: GameMetadata, zone: ZoneId) =
        m.title.normalizeGameTitle() to m.release?.atZone(zone)?.year

    private fun extras(vararg routes: Pair<String, HttpResult?>): StoreExtras {
        val http = FakeHttp(routes.toList(), setOf("library_hero.jpg"))
        return StoreExtras(SteamClient(http), GogClient(http))
    }

    private val steamRoutes = arrayOf(
        "appreviews" to steamReviewsRedirect,
        "steamspy" to HttpResult(503, ""),
        "catalog.gog.com" to HttpResult(200, """{"products":[]}"""),
    )

    @Test fun `Project Zomboid groups as 2013 like the Steam plugin would report it`() {
        val game = extras(
            "storesearch" to fixture("steam_search_zomboid.json"),
            "steampowered.com/api/appdetails" to fixture("steam_details_zomboid.json"),
            *steamRoutes,
        ).fetchByTitle("Project Zomboid", emptySet(), 1).single()
        assertEquals("project zomboid" to 2013, key(game, ZoneId.of("Europe/Berlin")))
        assertNotNull(game.platforms)
    }

    @Test fun `Baldur's Gate 3 groups as 2023`() {
        val game = extras(
            "storesearch" to fixture("steam_search_bg3.json"),
            "steampowered.com/api/appdetails" to fixture("steam_details_bg3.json"),
            *steamRoutes,
        ).fetchByTitle("Baldur's Gate 3", emptySet(), 1).single()
        assertEquals("baldurs gate 3" to 2023, key(game, ZoneId.of("Europe/Berlin")))
    }

    @Test fun `Anno 1503 History Edition from Steam groups as 2024`() {
        val game = extras(
            "storesearch" to fixture("steam_search_anno1503.json"),
            "steampowered.com/api/appdetails" to fixture("steam_details_anno1503.json"),
            *steamRoutes,
        ).fetchByTitle("Anno 1503", emptySet(), 1).single()
        assertEquals("anno 1503 history edition" to 2024, key(game, ZoneId.of("Europe/Berlin")))
    }

    @Test fun `GOG fallback carries the original release date`() {
        val game = extras(
            "storesearch" to fixture("steam_search_empty.json"),
            "catalog.gog.com" to fixture("gog_catalog_anno1503.json"),
            "api.gog.com" to HttpResult(200, """{"videos":[]}"""),
        ).fetchByTitle("Anno 1503", emptySet(), 1).single()
        assertEquals("anno 1503 ad" to 2003, key(game, ZoneId.of("Europe/Berlin")))
    }

    @Test fun `GOG date fills a Steam entry that has none`() {
        val e = extras(
            "storesearch" to fixture("steam_search_bg3.json"),
            "steampowered.com/api/appdetails" to HttpResult(200, fixture("steam_details_bg3.json").body.replace("3 Aug, 2023", "Coming soon")),
            "appreviews" to steamReviewsRedirect,
            "steamspy" to HttpResult(503, ""),
            "catalog.gog.com" to fixture("gog_catalog_bg3.json"),
            "api.gog.com" to fixture("gog_product_bg3.json"),
        )
        assertEquals(2023, e.fetchByTitle("Baldur's Gate 3", emptySet(), 1).single().release?.atZone(ZoneId.of("UTC"))?.year)
    }

    @Test fun `release matches the official Steam plugin's instant in every zone`() {
        // The official plugin uses start of day UTC; the same instant means the same year even where
        // Gameyfin's system zone puts 1 January UTC into the previous year.
        val official = java.time.LocalDate.of(2013, 11, 8).atStartOfDay().toInstant(java.time.ZoneOffset.UTC)
        val ours = ReleaseDates.parseSteam("8 Nov, 2013")
        assertEquals(official, ours)
        assertEquals(2012, ReleaseDates.parseSteam("1 Jan, 2013")!!.atZone(ZoneId.of("America/New_York")).year)
    }
}
