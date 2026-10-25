package dev.rosalogia.photoduel.core.data.highlights

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class SeriesKind { DAY, TRIP }

/** A stretch of days worth picking highlights from. [end] is inclusive. */
data class SeriesSuggestion(
    val kind: SeriesKind,
    val start: LocalDate,
    val end: LocalDate,
    val photoCount: Int,
)

/**
 * Suggests series from capture times alone: busy single days, and trips, i.e. runs of consecutive
 * days that each have a fair number of photos. A trip's days are also offered one by one when busy.
 */
object SeriesSuggester {
    const val BUSY_DAY = 20
    const val TRIP_DAY = 8
    const val TRIP_TOTAL = 40
    const val MAX_SUGGESTIONS = 40

    fun suggest(times: List<Long>, zone: ZoneId): List<SeriesSuggestion> {
        val perDay = times.groupingBy { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }.eachCount().toSortedMap()
        val out = ArrayList<SeriesSuggestion>()

        var runStart: LocalDate? = null
        var runEnd: LocalDate? = null
        var runTotal = 0
        fun closeRun() {
            val s = runStart
            val e = runEnd
            if (s != null && e != null && e > s && runTotal >= TRIP_TOTAL) out += SeriesSuggestion(SeriesKind.TRIP, s, e, runTotal)
            runStart = null
            runEnd = null
            runTotal = 0
        }
        for ((day, count) in perDay) {
            if (count >= BUSY_DAY) out += SeriesSuggestion(SeriesKind.DAY, day, day, count)
            if (count >= TRIP_DAY) {
                if (runEnd != null && runEnd!!.plusDays(1) != day) closeRun()
                if (runStart == null) runStart = day
                runEnd = day
                runTotal += count
            } else {
                closeRun()
            }
        }
        closeRun()
        // Newest first; a trip comes before the days it contains.
        return out.sortedWith(compareByDescending<SeriesSuggestion> { it.end }.thenBy { it.kind != SeriesKind.TRIP })
            .take(MAX_SUGGESTIONS)
    }

    /** [start, end) in epoch millis for whole local days [first]..[last]. */
    fun range(first: LocalDate, last: LocalDate, zone: ZoneId): Pair<Long, Long> =
        first.atStartOfDay(zone).toInstant().toEpochMilli() to last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** A suggested target: about a fifth of the candidates, within sensible bounds. */
    fun defaultTarget(candidates: Int): Int =
        if (candidates <= 3) candidates else (candidates / 5.0).let { Math.round(it).toInt() }.coerceIn(3, 30).coerceAtMost(candidates)
}
