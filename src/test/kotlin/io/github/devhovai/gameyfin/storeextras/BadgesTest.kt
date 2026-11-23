package io.github.devhovai.gameyfin.storeextras

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BadgesTest {
    @Test fun `shields escaping doubles dashes and underscores and encodes the rest`() {
        assertEquals("Single--player", Badges.segment("Single-player"))
        assertEquals("a__b", Badges.segment("a_b"))
        assertEquals("97%25%20Overwhelmingly%20Positive", Badges.segment("97% Overwhelmingly Positive"))
        assertEquals("4.6%2F5", Badges.segment("4.6/5"))
        assertEquals("Caf%C3%A9%20%3F%23%26", Badges.segment("Café ?#&"))
        assertEquals("Local%20Co--op", Badges.segment("Local Co-op"))
    }

    @Test fun `Steam badge matches the documented shape`() {
        assertEquals(
            "[![Steam 97% Overwhelmingly Positive](https://img.shields.io/badge/Steam-97%25%20Overwhelmingly%20Positive-1b2838?logo=steam)](https://store.steampowered.com/app/1086940/)",
            Badges.steam(97, "Overwhelmingly Positive", 1086940),
        )
    }

    @Test fun `GOG badge shows the five star score or just links to the store page`() {
        assertEquals(
            "[![GOG 4.6/5](https://img.shields.io/badge/GOG-4.6%2F5-86328a?logo=gogdotcom)](https://www.gog.com/en/game/baldurs_gate_iii)",
            Badges.gog(46, "https://www.gog.com/en/game/baldurs_gate_iii"),
        )
        assertEquals("[![GOG Store page](https://img.shields.io/badge/GOG-Store%20page-86328a?logo=gogdotcom)](https://www.gog.com/en/game/x)", Badges.gog(null, "https://www.gog.com/en/game/x"))
    }

    @Test fun `Metacritic colour follows the score`() {
        assertEquals("[![Metacritic 96](https://img.shields.io/badge/Metacritic-96-66cc33?logo=metacritic)](https://www.metacritic.com/game/pc/x)", Badges.metacritic(96, "https://www.metacritic.com/game/pc/x"))
        assertEquals("![Metacritic 60](https://img.shields.io/badge/Metacritic-60-ffcc33?logo=metacritic)", Badges.metacritic(60, null))
        assertEquals("![Metacritic 20](https://img.shields.io/badge/Metacritic-20-ff0000?logo=metacritic)", Badges.metacritic(20, null))
    }

    @Test fun `mode badges have no label`() {
        assertEquals("![Local Co-op](https://img.shields.io/badge/Local%20Co--op-4c566a)", Badges.mode("Local Co-op"))
    }

    @Test fun `links with parentheses are kept intact`() {
        assertEquals(
            "[![A b](https://img.shields.io/badge/A-b-c)](https://x.org/a%28b%29)",
            Badges.markdown("A", "b", "c", href = "https://x.org/a(b)"),
        )
    }

    @Test fun `description is badges, blank line, body - and null without a body`() {
        assertEquals("B1 B2\n\ntext", Badges.description(listOf("B1", "B2"), "text\n"))
        assertEquals("text", Badges.description(emptyList(), "text"))
        assertNull(Badges.description(listOf("B1"), null))
        assertNull(Badges.description(listOf("B1"), "  "))
    }
}
