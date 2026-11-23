package io.github.devhovai.gameyfin.storeextras

import org.gameyfin.pluginapi.gamemetadata.Platform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GogClientTest {
    @Test fun `catalog search maps ids, images and platforms`() {
        val gog = GogClient(FakeHttp(listOf("catalog.gog.com" to fixture("gog_catalog_anno1503.json"))))
        val hit = assertNotNull(gog.search("Anno 1503").firstOrNull())
        assertEquals("1438074791", hit.id)
        assertEquals("Anno 1503 A.D.", hit.title)
        assertEquals(setOf(Platform.PC_MICROSOFT_WINDOWS), hit.platforms)
        assertNotNull(hit.background)
    }

    @Test fun `gog rating is doubled to a 0 to 100 scale`() {
        val gog = GogClient(FakeHttp(listOf("catalog.gog.com" to fixture("gog_catalog_bg3.json"))))
        assertEquals(92, gog.search("Baldur's Gate 3").first { it.id == "1456460669" }.userRating())
    }

    @Test fun `no rating without enough reviews`() {
        val hit = GogHit("1", "t", "game", 50, 3, null, null, emptySet(), emptySet(), emptySet())
        assertNull(hit.userRating())
    }

    @Test fun `youtube embed links become watch links`() {
        val gog = GogClient(FakeHttp(listOf("api.gog.com" to fixture("gog_product_bg3.json"))))
        val videos = gog.videos("1456460669")
        assertEquals("https://www.youtube.com/watch?v=xg53H-H2gqk", videos.first())
    }
}
