package io.github.devhovai.gameyfin.storeextras

import org.gameyfin.pluginapi.gamemetadata.GameFeature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DescriptionTest {
    private val hero = setOf("apps/1086940/library_hero.jpg", "apps/108600/library_hero.jpg")

    private fun extras(vararg routes: Pair<String, HttpResult?>): Pair<StoreExtras, FakeHttp> {
        val http = FakeHttp(routes.toList(), hero)
        return StoreExtras(SteamClient(http), GogClient(http)) to http
    }

    private fun bg3() = extras(
        "storesearch" to fixture("steam_search_bg3.json"),
        "steampowered.com/api/appdetails" to fixture("steam_details_bg3.json"),
        "ajaxgetdeckappcompatibilityreport" to fixture("steam_deck_verified.json"),
        "appreviews" to steamReviewsRedirect,
        "steamspy" to fixture("steamspy_bg3.json"),
        "catalog.gog.com" to fixture("gog_catalog_bg3.json"),
        "api.gog.com" to fixture("gog_product_bg3.json"),
    )

    @Test fun `Baldur's Gate 3 description starts with store badges, then the Markdown text`() {
        val (e, _) = bg3()
        val description = assertNotNull(e.fetchByTitle("Baldur's Gate 3", emptySet(), 1).single().description)
        val (badges, body) = description.split("\n\n", limit = 2)
        assertTrue(badges.startsWith("[![Steam 97% Overwhelmingly Positive](https://img.shields.io/badge/Steam-97%25%20Overwhelmingly%20Positive-1b2838?logo=steam)](https://store.steampowered.com/app/1086940/) "), badges)
        assertTrue("[![GOG 4.6/5](https://img.shields.io/badge/GOG-4.6%2F5-86328a?logo=gogdotcom)](https://www.gog.com/en/game/baldurs_gate_iii)" in badges, badges)
        assertTrue("[![Metacritic 96](https://img.shields.io/badge/Metacritic-96-66cc33?logo=metacritic)](https://www.metacritic.com/game/pc/baldurs-gate-3)" in badges)
        assertTrue("![Steam Deck Verified](" in badges && "![Single-player](" in badges && "![Online Co-op](" in badges && "![Controller support](" in badges)
        assertFalse("\n" in badges)
        assertTrue(body.startsWith("Gather your party and return to the Forgotten Realms"), body)
    }

    @Test fun `features come from the Steam categories`() {
        val (e, _) = bg3()
        val features = e.fetchByTitle("Baldur's Gate 3", emptySet(), 1).single().features!!
        assertTrue(GameFeature.ONLINE_CO_OP in features && GameFeature.CONTROLLER_SUPPORT in features && GameFeature.SINGLEPLAYER in features)
    }

    @Test fun `the Steam 302 on reviews only skips reviews - search, details, deck report and GOG are still asked`() {
        val (e, http) = bg3()
        repeat(2) { assertEquals("1086940", e.fetchByTitle("Baldur's Gate 3", emptySet(), 1).single().originalId) }
        assertEquals(1, http.requested.count { it.contains("appreviews") }, "reviews are asked once, then the cooldown applies")
        assertEquals(2, http.requested.count { it.contains("storesearch") })
        assertEquals(2, http.requested.count { it.contains("steampowered.com/api/appdetails") })
        assertEquals(2, http.requested.count { it.contains("ajaxgetdeckappcompatibilityreport") })
        assertTrue(http.requested.any { it.contains("catalog.gog.com") })
    }

    @Test fun `only confident title matches get a store badge`() {
        assertTrue(TitleMatcher.score("Anno 1503", "Anno 1503 History Edition") >= StoreExtras.CONFIDENT)
        assertTrue(TitleMatcher.score("Baldur's Gate", "Baldur's Gate 3") < StoreExtras.CONFIDENT)
        assertTrue(TitleMatcher.score("Stronghold", "Stronghold Crusader Definitive Edition") < StoreExtras.CONFIDENT)
    }

    @Test fun `GOG-only titles get the GOG description and badge`() {
        val (e, _) = extras(
            "storesearch" to fixture("steam_search_empty.json"),
            "catalog.gog.com" to HttpResult(
                200,
                """{"products":[{"id":"1207658712","slug":"stronghold","title":"Stronghold HD","productType":"game","reviewsRating":44,"reviewsCount":300,
                    "releaseDate":"2001.10.22","operatingSystems":["windows"],"storeLink":"https://www.gog.com/en/game/stronghold",
                    "features":[{"name":"Single-player","slug":"single"},{"name":"Multiplayer","slug":"multi"}]}]}""",
            ),
            "api.gog.com" to fixture("gog_product_stronghold.json"),
        )
        val game = e.fetchByTitle("Stronghold HD", emptySet(), 1).single()
        val lines = game.description!!.split("\n\n")
        assertEquals(
            "[![GOG 4.4/5](https://img.shields.io/badge/GOG-4.4%2F5-86328a?logo=gogdotcom)](https://www.gog.com/en/game/stronghold) " +
                "![Single-player](https://img.shields.io/badge/Single--player-4c566a) ![Multiplayer](https://img.shields.io/badge/Multiplayer-4c566a)",
            lines.first(),
        )
        assertTrue(lines[1].startsWith("Establish your settlement, build your castle"), lines[1])
        assertEquals(setOf(GameFeature.SINGLEPLAYER, GameFeature.MULTIPLAYER), game.features)
    }

    @Test fun `without any store text there is no description, so lower priority providers keep theirs`() {
        val (e, _) = extras(
            "storesearch" to fixture("steam_search_empty.json"),
            "catalog.gog.com" to HttpResult(200, """{"products":[{"id":"1","title":"Some Game","storeLink":"https://www.gog.com/en/game/some_game"}]}"""),
            "api.gog.com" to HttpResult(200, """{"videos":[]}"""),
        )
        assertNull(e.fetchByTitle("Some Game", emptySet(), 1).single().description)
    }
}
