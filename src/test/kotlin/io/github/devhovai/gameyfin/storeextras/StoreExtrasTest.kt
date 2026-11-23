package io.github.devhovai.gameyfin.storeextras

import org.gameyfin.pluginapi.gamemetadata.Platform
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreExtrasTest {
    private val heroBg3 = setOf("apps/1086940/library_hero.jpg", "apps/108600/library_hero.jpg")

    private fun extras(vararg routes: Pair<String, HttpResult?>, existing: Set<String> = heroBg3): StoreExtras {
        val http = FakeHttp(routes.toList(), existing)
        return StoreExtras(SteamClient(http), GogClient(http))
    }

    private fun bg3() = extras(
        "storesearch" to fixture("steam_search_bg3.json"),
        "steampowered.com/api/appdetails" to fixture("steam_details_bg3.json"),
        "appreviews" to steamReviewsRedirect,
        "steamspy" to fixture("steamspy_bg3.json"),
        "catalog.gog.com" to fixture("gog_catalog_bg3.json"),
        "api.gog.com" to fixture("gog_product_bg3.json"),
    )

    @Test fun `Baldur's Gate 3 gets rating, critic score, banners and trailers from Steam`() {
        val game = bg3().fetchByTitle("Baldur's Gate 3", emptySet(), 1).single()
        assertEquals("1086940", game.originalId)
        assertEquals(97, game.userRating)
        assertEquals(96, game.criticRating)
        val headers = game.headerUrls!!.toList()
        assertTrue(headers.first().toString().endsWith("/apps/1086940/library_hero.jpg"))
        assertTrue(headers.last().toString().contains("header.jpg"))
        assertTrue(game.videoUrls!!.all { it.toString().contains(".m3u8") })
        assertEquals(setOf("Larian Studios"), game.developedBy)
    }

    @Test fun `the Steam 302 on reviews only pauses reviews - search and details are still asked every time`() {
        val http = FakeHttp(
            listOf(
                "storesearch" to fixture("steam_search_bg3.json"),
                "steampowered.com/api/appdetails" to fixture("steam_details_bg3.json"),
                "appreviews" to steamReviewsRedirect,
                "steamspy" to fixture("steamspy_bg3.json"),
                "catalog.gog.com" to fixture("gog_catalog_bg3.json"),
                "api.gog.com" to fixture("gog_product_bg3.json"),
            ),
            heroBg3,
        )
        val e = StoreExtras(SteamClient(http), GogClient(http))
        repeat(2) { assertEquals("1086940", e.fetchByTitle("Baldur's Gate 3", emptySet(), 1).single().originalId) }
        assertEquals(1, http.requested.count { it.contains("appreviews") })
        assertEquals(2, http.requested.count { it.contains("storesearch") })
        assertEquals(2, http.requested.count { it.contains("steampowered.com/api/appdetails") })
    }

    @Test fun `Project Zomboid has no critic score but still a rating and trailers`() {
        val e = extras(
            "storesearch" to fixture("steam_search_zomboid.json"),
            "steampowered.com/api/appdetails" to fixture("steam_details_zomboid.json"),
            "appreviews" to steamReviewsRedirect,
            "steamspy" to fixture("steamspy_zomboid.json"),
            "catalog.gog.com" to HttpResult(200, """{"products":[]}"""),
        )
        val game = e.fetchByTitle("Project Zomboid", emptySet(), 1).single()
        assertEquals("108600", game.originalId)
        assertNull(game.criticRating)
        assertNotNull(game.userRating)
        assertTrue(game.userRating!! in 1..100)
        assertTrue(game.videoUrls!!.isNotEmpty())
    }

    @Test fun `Anno 1503 is not on Steam by that name and falls back to GOG`() {
        val e = extras(
            "storesearch" to fixture("steam_search_empty.json"),
            "catalog.gog.com" to fixture("gog_catalog_anno1503.json"),
            "api.gog.com" to HttpResult(200, """{"videos":[]}"""),
        )
        val game = e.fetchByTitle("Anno 1503", emptySet(), 1).single()
        assertEquals("gog-1438074791", game.originalId)
        assertTrue(game.headerUrls!!.isNotEmpty())
    }

    @Test fun `GOG fills what Steam lacks`() {
        // SteamSpy unavailable, so Steam has no rating; GOG's catalog supplies it.
        val e = extras(
            "storesearch" to fixture("steam_search_bg3.json"),
            "steampowered.com/api/appdetails" to fixture("steam_details_bg3.json"),
            "appreviews" to steamReviewsRedirect,
            "steamspy" to HttpResult(503, ""),
            "catalog.gog.com" to fixture("gog_catalog_bg3.json"),
            "api.gog.com" to fixture("gog_product_bg3.json"),
        )
        assertEquals(92, e.fetchByTitle("Baldur's Gate 3", emptySet(), 1).single().userRating)
    }

    @Test fun `a title that matches nothing returns nothing`() {
        val e = extras(
            "storesearch" to fixture("steam_search_zomboid.json"),
            "catalog.gog.com" to HttpResult(200, """{"products":[]}"""),
        )
        assertTrue(e.fetchByTitle("Pizza Connection 2", emptySet(), 1).isEmpty())
    }

    @Test fun `platform filter drops games that are not available there`() {
        assertTrue(bg3().fetchByTitle("Baldur's Gate 3", setOf(Platform.LINUX), 1).isEmpty())
        assertEquals(1, bg3().fetchByTitle("Baldur's Gate 3", setOf(Platform.PC_MICROSOFT_WINDOWS), 1).size)
    }

    @Test fun `fetch by steam id and by gog id`() {
        assertEquals("Baldur's Gate 3", bg3().fetchById("1086940")?.title)
        val byGog = bg3().fetchById("gog-1456460669")
        assertEquals("gog-1456460669", byGog?.originalId)
    }

    @Test fun `fetch by id ignores garbage`() {
        assertNull(bg3().fetchById("not-an-id"))
    }

    @Test fun `video urls are plain URIs Gameyfin accepts`() {
        val game = bg3().fetchByTitle("Baldur's Gate 3", emptySet(), 1).single()
        assertTrue(game.videoUrls!!.all { it.scheme == "https" && it.host.isNotBlank() })
        assertTrue(game.videoUrls!!.size <= StoreExtras.MAX_VIDEOS)
        assertTrue(URI("https://www.youtube.com/watch?v=xg53H-H2gqk").host.isNotBlank())
    }
}
