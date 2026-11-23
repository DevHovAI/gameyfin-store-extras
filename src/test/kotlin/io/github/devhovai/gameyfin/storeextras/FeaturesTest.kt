package io.github.devhovai.gameyfin.storeextras

import org.gameyfin.pluginapi.gamemetadata.GameFeature.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FeaturesTest {
    private fun categories(file: String, appId: Int): Set<Int> =
        parseObject(fixture(file).body)!![appId.toString()].obj()!!["data"].obj()!!["categories"].arr()!!.map { it.obj()!!["id"].int()!! }.toSet()

    @Test fun `Project Zomboid categories map to local and online co-op, PvP and workshop`() {
        val f = SteamCategories.features(categories("steam_details_zomboid.json", 108600))
        assertTrue(f.containsAll(setOf(SINGLEPLAYER, MULTIPLAYER, ONLINE_PVP, CO_OP, ONLINE_CO_OP, LOCAL_CO_OP, SPLITSCREEN, CROSS_PLATFORM, WORKSHOP, CONTROLLER_SUPPORT, REMOTE_PLAY)), f.toString())
    }

    @Test fun `Baldur's Gate 3 has no local co-op or PvP`() {
        val f = SteamCategories.features(categories("steam_details_bg3.json", 1086940))
        assertTrue(f.containsAll(setOf(SINGLEPLAYER, ONLINE_CO_OP, CONTROLLER_SUPPORT, ACHIEVEMENTS, CLOUD_SAVES)), f.toString())
        assertTrue(LOCAL_CO_OP !in f && ONLINE_PVP !in f && LOCAL_PVP !in f)
    }

    @Test fun `unknown categories are ignored`() {
        assertEquals(emptySet(), SteamCategories.features(setOf(21, 29, 62, 999)))
    }

    @Test fun `mode badges prefer the specific label over the generic one`() {
        assertEquals(listOf("Single-player", "Online Co-op", "Local Co-op", "Online PvP", "Partial controller support"), SteamCategories.modeLabels(setOf(2, 1, 49, 36, 9, 38, 39, 24, 18)))
        assertEquals(listOf("Single-player", "Co-op"), SteamCategories.modeLabels(setOf(2, 1, 9)))
        assertEquals(listOf("Split Screen"), SteamCategories.modeLabels(setOf(24)))
        assertEquals(listOf("Local PvP", "MMO", "Controller support"), SteamCategories.modeLabels(setOf(37, 24, 20, 28)))
        assertEquals(listOf("PvP"), SteamCategories.modeLabels(setOf(49)))
        assertEquals(emptyList(), SteamCategories.modeLabels(setOf(22, 23)))
    }

    @Test fun `GOG feature names map to features`() {
        assertEquals(setOf(SINGLEPLAYER, CLOUD_SAVES, MULTIPLAYER), GogFeatures.features(setOf("single", "Cloud Saves", "multi")))
        assertEquals(listOf("Single-player", "Multiplayer"), GogFeatures.modeLabels(setOf(SINGLEPLAYER, MULTIPLAYER, CLOUD_SAVES)))
    }
}
