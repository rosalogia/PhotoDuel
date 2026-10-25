package dev.rosalogia.photoduel.core.data.highlights

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class SeriesSuggesterTest {
    private val zone = ZoneOffset.UTC

    private fun shots(day: LocalDate, n: Int): List<Long> {
        val noon = day.atTime(12, 0).toInstant(zone).toEpochMilli()
        return List(n) { noon + it * 60_000L }
    }

    @Test
    fun busyDaysAndTripsNewestFirst() {
        val d = LocalDate.of(2026, 7, 1)
        val times = shots(d, 25) + shots(d.plusDays(1), 10) + shots(d.plusDays(2), 12) + // a 3-day trip of 47
            shots(d.plusDays(10), 30) + // a busy day alone
            shots(d.plusDays(20), 5) // too quiet
        val s = SeriesSuggester.suggest(times, zone)
        assertThat(s).containsExactly(
            SeriesSuggestion(SeriesKind.DAY, d.plusDays(10), d.plusDays(10), 30),
            SeriesSuggestion(SeriesKind.TRIP, d, d.plusDays(2), 47),
            SeriesSuggestion(SeriesKind.DAY, d, d, 25),
        ).inOrder()
    }

    @Test
    fun aQuietDayBreaksATrip() {
        val d = LocalDate.of(2026, 7, 1)
        val times = shots(d, 30) + shots(d.plusDays(1), 3) + shots(d.plusDays(2), 30)
        assertThat(SeriesSuggester.suggest(times, zone).map { it.kind }).containsExactly(SeriesKind.DAY, SeriesKind.DAY)
    }

    @Test
    fun rangeCoversWholeDays() {
        val d = LocalDate.of(2026, 7, 1)
        val (start, end) = SeriesSuggester.range(d, d.plusDays(1), zone)
        assertThat(end - start).isEqualTo(2 * 86_400_000L)
    }

    @Test
    fun defaultTargetIsAboutAFifth() {
        assertThat(SeriesSuggester.defaultTarget(2)).isEqualTo(2)
        assertThat(SeriesSuggester.defaultTarget(10)).isEqualTo(3)
        assertThat(SeriesSuggester.defaultTarget(50)).isEqualTo(10)
        assertThat(SeriesSuggester.defaultTarget(1000)).isEqualTo(30)
    }
}
