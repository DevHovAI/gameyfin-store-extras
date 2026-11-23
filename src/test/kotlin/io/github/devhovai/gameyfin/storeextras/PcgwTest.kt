package io.github.devhovai.gameyfin.storeextras

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PcgwTest {
    private fun wikitext(name: String): String = parseObject(fixture(name).body)!!["parse"].obj()!!["wikitext"].str()!!

    private fun redirectTo(page: String) = HttpResult(302, "", "https://www.pcgamingwiki.com/wiki/$page")

    @Test fun `player counts accept numbers and ranges and reject unknown, empty and zero`() {
        assertEquals("4", PcgwParser.players("4"))
        assertEquals("100", PcgwParser.players(" 100 "))
        assertEquals("2–4", PcgwParser.players("2-4"))
        assertEquals("2–16", PcgwParser.players("2 – 16"))
        assertEquals("4", PcgwParser.players("4-4"))
        assertEquals("8+", PcgwParser.players("8+"))
        assertNull(PcgwParser.players("unknown"))
        assertNull(PcgwParser.players(""))
        assertNull(PcgwParser.players(null))
        assertNull(PcgwParser.players("0"))
    }

    @Test fun `Project Zomboid is local 4, LAN 100, online 100`() {
        assertEquals(
            listOf(
                MultiplayerMode("Local", "4", coop = false, versus = false),
                MultiplayerMode("LAN", "100", coop = false, versus = false),
                MultiplayerMode("Online", "100", coop = false, versus = false),
            ),
            PcgwParser.modes(wikitext("pcgw_project_zomboid.json")),
        )
    }

    @Test fun `Baldur's Gate 3 counts are co-op`() {
        val modes = PcgwParser.modes(wikitext("pcgw_baldurs_gate_3.json"))
        assertEquals(listOf("2", "4", "4"), modes.map { it.players })
        assertTrue(modes.all { it.coop && !it.versus })
    }

    @Test fun `a single-player game has no multiplayer template and no modes`() {
        assertEquals(emptyList(), PcgwParser.modes(wikitext("pcgw_disco_elysium.json")))
    }

    @Test fun `ranges, unknown counts and mode lists are understood`() {
        assertEquals(
            listOf(
                MultiplayerMode("Local", "2–4", coop = true, versus = true),
                MultiplayerMode("LAN", null, coop = true, versus = false),
                MultiplayerMode("Online", "2–16", coop = false, versus = false),
            ),
            PcgwParser.modes(wikitext("pcgw_synthetic_ranges.json")),
        )
    }

    @Test fun `modes that are not true are skipped`() {
        val text = "{{Network/Multiplayer\n|local play = hackable\n|local play players = 2\n|lan play = false\n|lan play players = 8\n|online play = unknown\n}}"
        assertEquals(emptyList(), PcgwParser.modes(text))
    }

    @Test fun `badges show counts, singular, ranges and modes`() {
        assertEquals("![Local 4 players](https://img.shields.io/badge/Local-4%20players-4c566a)", Badges.multiplayer(MultiplayerMode("Local", "4", false, false)))
        assertEquals("![Online 100 players](https://img.shields.io/badge/Online-100%20players-4c566a)", Badges.multiplayer(MultiplayerMode("Online", "100", false, false)))
        assertEquals("![LAN 1 player](https://img.shields.io/badge/LAN-1%20player-4c566a)", Badges.multiplayer(MultiplayerMode("LAN", "1", false, false)))
        assertEquals("![Local 2–4 players, Co-op](https://img.shields.io/badge/Local-2%E2%80%934%20players%2C%20Co--op-4c566a)", Badges.multiplayer(MultiplayerMode("Local", "2–4", true, false)))
        assertEquals("![Online multiplayer, Co-op, Versus](https://img.shields.io/badge/Online%20multiplayer%2C%20Co--op%2C%20Versus-4c566a)", Badges.multiplayer(MultiplayerMode("Online", null, true, true)))
    }

    @Test fun `finds the page through the Steam app id redirect and verifies the id`() {
        val http = FakeHttp(
            listOf(
                "api/appid.php?appid=108600" to redirectTo("Project_Zomboid"),
                "action=parse" to fixture("pcgw_project_zomboid.json"),
            ),
        )
        val modes = PcgwClient(http).multiplayer(108600, null, "Project Zomboid")
        assertEquals(listOf("4", "100", "100"), modes.map { it.players })
        assertTrue(http.requested.single { it.contains("action=parse") }.contains("page=Project+Zomboid"))
    }

    @Test fun `falls back to a title search when the id is not known, and still verifies the id`() {
        val http = FakeHttp(
            listOf(
                "api/appid.php" to HttpResult(200, "not found"),
                "list=search" to fixture("pcgw_search_zomboid.json"),
                "action=parse" to fixture("pcgw_project_zomboid.json"),
            ),
        )
        assertEquals(3, PcgwClient(http).multiplayer(108600, null, "Project Zomboid").size)
        // The same page under another id is not accepted.
        assertEquals(emptyList(), PcgwClient(http).multiplayer(999, null, "Project Zomboid"))
    }

    @Test fun `GOG ids go through gog_php and the GOG id in the infobox`() {
        val http = FakeHttp(
            listOf(
                "api/gog.php?page=1453298883" to redirectTo("Project_Zomboid"),
                "action=parse" to fixture("pcgw_project_zomboid.json"),
            ),
        )
        assertEquals(3, PcgwClient(http).multiplayer(null, "1453298883", "Project Zomboid").size)
    }

    @Test fun `results and failures are cached`() {
        var now = 0L
        val http = FakeHttp(listOf("api/appid.php" to null))
        val client = PcgwClient(http) { now }
        repeat(3) { assertEquals(emptyList(), client.multiplayer(1, null, "X")) }
        assertEquals(1, http.requested.size)
        now += PcgwClient.FAILURE_TTL_MS + 1
        client.multiplayer(1, null, "X")
        assertEquals(2, http.requested.size)

        val ok = FakeHttp(listOf("api/appid.php" to redirectTo("Project_Zomboid"), "action=parse" to fixture("pcgw_project_zomboid.json")))
        val c2 = PcgwClient(ok) { now }
        repeat(3) { c2.multiplayer(108600, null, "Project Zomboid") }
        assertEquals(2, ok.requested.size)
    }

    @Test fun `garbage from PCGamingWiki yields no modes instead of an error`() {
        val http = FakeHttp(
            listOf(
                "api/appid.php" to redirectTo("Broken"),
                "action=parse" to HttpResult(200, "<html>not json"),
            ),
        )
        assertEquals(emptyList(), PcgwClient(http).multiplayer(1, null, "Broken"))
    }

    @Test fun `Project Zomboid description carries the player count badges`() {
        val http = FakeHttp(
            listOf(
                "storesearch" to fixture("steam_search_zomboid.json"),
                "steampowered.com/api/appdetails" to fixture("steam_details_zomboid.json"),
                "appreviews" to steamReviewsRedirect,
                "steamspy" to fixture("steamspy_zomboid.json"),
                "catalog.gog.com" to HttpResult(500, ""),
                "api/appid.php" to redirectTo("Project_Zomboid"),
                "action=parse" to fixture("pcgw_project_zomboid.json"),
            ),
        )
        val game = StoreExtras(SteamClient(http), GogClient(http), PcgwClient(http)).fetchByTitle("Project Zomboid", emptySet(), 1).single()
        val line = game.description!!.lines().first()
        assertTrue("Local-4%20players" in line, line)
        assertTrue("LAN-100%20players" in line, line)
        assertTrue("Online-100%20players" in line, line)
    }

    @Test fun `a PCGamingWiki outage does not touch the rest of the metadata`() {
        val http = FakeHttp(
            listOf(
                "storesearch" to fixture("steam_search_zomboid.json"),
                "steampowered.com/api/appdetails" to fixture("steam_details_zomboid.json"),
                "appreviews" to steamReviewsRedirect,
                "steamspy" to fixture("steamspy_zomboid.json"),
                "catalog.gog.com" to HttpResult(500, ""),
                "pcgamingwiki" to null,
            ),
        )
        val game = StoreExtras(SteamClient(http), GogClient(http), PcgwClient(http)).fetchByTitle("Project Zomboid", emptySet(), 1).single()
        assertEquals("108600", game.originalId)
        assertTrue(game.description != null && "players" !in game.description!!.lines().first())
    }
}
