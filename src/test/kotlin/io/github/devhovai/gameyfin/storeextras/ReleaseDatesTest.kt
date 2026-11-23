package io.github.devhovai.gameyfin.storeextras

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReleaseDatesTest {
    private fun steam(text: String?) = ReleaseDates.parseSteam(text)

    @Test fun `both Steam date orders give the same start-of-day UTC instant`() {
        val expected = Instant.parse("2013-11-08T00:00:00Z")
        for (text in listOf("8 Nov, 2013", "Nov 8, 2013", " 8 Nov,  2013 ")) assertEquals(expected, steam(text), text)
    }

    @Test fun `a bare year is 1 January and a quarter its first month`() {
        assertEquals(Instant.parse("2013-01-01T00:00:00Z"), steam("2013"))
        assertEquals(Instant.parse("2025-07-01T00:00:00Z"), steam("Q3 2025"))
    }

    @Test fun `dates that are not known yet or not dates give null`() {
        for (text in listOf("Coming soon", "Coming Soon", "To be announced", "", "  ", "not a date", "Q5 2025", null)) {
            assertNull(steam(text), text)
        }
    }

    @Test fun `GOG dates`() {
        assertEquals(Instant.parse("2023-08-03T00:00:00Z"), ReleaseDates.parseGog("2023.08.03"))
        assertEquals(Instant.parse("2023-08-03T00:00:00Z"), ReleaseDates.parseGog("2023-08-03"))
        assertEquals(Instant.parse("2023-08-03T00:00:00Z"), ReleaseDates.parseGog("2023-08-03T12:00:00+0200"))
        assertNull(ReleaseDates.parseGog("soon"))
        assertNull(ReleaseDates.parseGog(null))
    }
}
