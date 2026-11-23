package io.github.devhovai.gameyfin.storeextras

import org.gameyfin.pluginapi.gamemetadata.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SteamClientTest {
    @Test fun `search parses store search hits`() {
        val steam = SteamClient(FakeHttp(listOf("storesearch" to fixture("steam_search_bg3.json"))))
        val hits = steam.search("Baldur's Gate 3")
        assertTrue(hits.any { it.id == 1086940 && it.name == "Baldur's Gate 3" })
    }

    @Test fun `details parse platforms, metacritic and HLS trailers`() {
        val steam = SteamClient(FakeHttp(listOf("steampowered.com/api/appdetails" to fixture("steam_details_bg3.json"))))
        val app = assertNotNull(steam.details(1086940))
        assertEquals("game", app.type)
        assertEquals(setOf(Platform.PC_MICROSOFT_WINDOWS, Platform.MAC), app.platforms)
        assertEquals(96, app.metacritic)
        assertTrue(app.movies.isNotEmpty())
        assertTrue(app.movies.all { it.bestUrl!!.contains(".m3u8") })
    }

    @Test fun `legacy mp4 and webm links win over HLS`() {
        val movie = SteamMovie("t", mp4 = "https://x/a.mp4", webm = "https://x/a.webm", hls = "https://x/a.m3u8", dash = null)
        assertEquals("https://x/a.mp4", movie.bestUrl)
        assertEquals("https://x/a.webm", movie.copy(mp4 = null).bestUrl)
        assertEquals("https://x/a.m3u8", movie.copy(mp4 = null, webm = null).bestUrl)
    }

    @Test fun `details of a failed app lookup are null`() {
        val body = HttpResult(200, """{"1":{"success":false}}""")
        assertNull(SteamClient(FakeHttp(listOf("steampowered.com/api/appdetails" to body))).details(1))
    }

    @Test fun `reviews come from Steam when its endpoint answers`() {
        val steam = SteamClient(FakeHttp(listOf("appreviews" to fixture("steam_reviews_synthetic.json"))))
        val counts = assertNotNull(steam.reviews(1086940))
        assertEquals("steam", counts.source)
        assertEquals(90, counts.percent())
    }

    @Test fun `reviews fall back to SteamSpy when Steam redirects, and Steam is not asked again for an hour`() {
        var now = 0L
        val http = FakeHttp(listOf("appreviews" to steamReviewsRedirect, "steamspy" to fixture("steamspy_bg3.json")))
        val steam = SteamClient(http, clock = { now })

        val first = assertNotNull(steam.reviews(1086940))
        assertEquals("steamspy", first.source)
        assertEquals(97, first.percent()) // 737913 / (737913 + 24306)

        steam.reviews(1086940)
        assertEquals(1, http.requested.count { it.contains("appreviews") })

        now += 61 * 60 * 1000L
        steam.reviews(1086940)
        assertEquals(2, http.requested.count { it.contains("appreviews") })
    }

    @Test fun `too few votes yield no rating`() {
        assertNull(ReviewCounts(5, 1, "steam").percent())
        assertEquals(50, ReviewCounts(5, 5, "steam").percent())
    }

    @Test fun `hero url only when the image exists`() {
        val none = SteamClient(FakeHttp(emptyList()))
        assertNull(none.heroUrl(1))
        val some = SteamClient(FakeHttp(emptyList(), existing = setOf("apps/1/library_hero.jpg")))
        assertTrue(some.heroUrl(1)!!.endsWith("/apps/1/library_hero.jpg"))
    }
}
