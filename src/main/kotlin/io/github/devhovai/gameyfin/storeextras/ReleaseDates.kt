package io.github.devhovai.gameyfin.storeextras

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns store release dates into the [Instant] Gameyfin groups search results by (title + release year).
 *
 * The year only lines up with the official Steam Metadata plugin if the instant is identical, so this follows its
 * conventions: start of day UTC, quarters map to the first month of the quarter, a bare year to 1 January.
 * Dates that are not known yet ("Coming soon", "To be announced") yield null instead of a placeholder.
 */
object ReleaseDates {
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMM, uuuu", Locale.ENGLISH)
    private val monthDayYear = DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.ENGLISH)
    private val gogDate = DateTimeFormatter.ofPattern("uuuu.MM.dd", Locale.ENGLISH)
    private val year = Regex("""\d{4}""")
    private val quarter = Regex("""Q([1-4]) (\d{4})""")

    /** Steam's `release_date.date`, e.g. "8 Nov, 2013", "Nov 8, 2013", "2013", "Q3 2025"; null for "Coming soon" and anything unparseable. */
    fun parseSteam(text: String?): Instant? {
        val s = text?.replace(' ', ' ')?.replace(Regex("\\s+"), " ")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (year.matches(s)) return atUtc(LocalDate.of(s.toInt(), 1, 1))
        quarter.matchEntire(s)?.destructured?.let { (q, y) -> return atUtc(LocalDate.of(y.toInt(), q.toInt() * 3 - 2, 1)) }
        return parse(s, dayMonthYear) ?: parse(s, monthDayYear)
    }

    /** GOG catalog `releaseDate`, e.g. "2023.08.03" (ISO "2023-08-03" and full timestamps are accepted too). */
    fun parseGog(text: String?): Instant? {
        val s = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return parse(s, gogDate) ?: runCatching { LocalDate.parse(s.take(10)) }.getOrNull()?.let(::atUtc)
    }

    private fun parse(s: String, formatter: DateTimeFormatter): Instant? =
        runCatching { LocalDate.parse(s, formatter) }.getOrNull()?.let(::atUtc)

    private fun atUtc(date: LocalDate): Instant = date.atStartOfDay().toInstant(ZoneOffset.UTC)
}
