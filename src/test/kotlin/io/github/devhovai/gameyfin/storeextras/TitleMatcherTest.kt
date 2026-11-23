package io.github.devhovai.gameyfin.storeextras

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TitleMatcherTest {
    @Test fun `exact match ignoring case, trademarks and punctuation`() {
        assertEquals(100, TitleMatcher.score("Baldur's Gate 3", "BALDURS GATE 3™"))
        assertEquals(100, TitleMatcher.score("Stronghold HD", "Stronghold: HD"))
    }

    @Test fun `edition suffix still matches`() {
        assertTrue(TitleMatcher.score("Anno 1503", "Anno 1503 History Edition") >= TitleMatcher.MIN_SCORE)
    }

    @Test fun `sequels do not match their predecessor`() {
        assertTrue(TitleMatcher.score("Baldur's Gate", "Baldur's Gate 3") < TitleMatcher.MIN_SCORE)
        assertTrue(TitleMatcher.score("Anno", "Anno II") < TitleMatcher.MIN_SCORE)
    }

    @Test fun `unrelated titles are rejected`() {
        assertTrue(TitleMatcher.score("Project Zomboid", "Pizza Connection 2") < TitleMatcher.MIN_SCORE)
    }

    @Test fun `best returns highest scores first and respects the limit`() {
        val hits = listOf("Anno 1503 History Edition", "Anno 1503", "Something else")
        assertEquals(listOf("Anno 1503"), TitleMatcher.best("Anno 1503", hits, 1) { it })
    }
}
