package dev.rosalogia.photoduel.core.common

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class FriendlyTimeTest {
    private val zone = ZoneOffset.UTC
    private val now = at(2026, 10, 25, 9, 0)

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).toInstant(zone).toEpochMilli()

    private fun day(millis: Long) = friendlyTime(millis, now, zone).substringBeforeLast(", ")

    @Test
    fun namesRecentDays() {
        assertEquals("Today", day(at(2026, 10, 25, 0, 33)))
        assertEquals("Yesterday", day(at(2026, 10, 24, 23, 59)))
        assertEquals("Wednesday", day(at(2026, 10, 21, 12, 0)))
    }

    @Test
    fun olderDatesShowTheDate() {
        assertEquals("Sat, Oct 10", day(at(2026, 10, 10, 12, 0)))
        assertEquals("Oct 10, 2025", day(at(2025, 10, 10, 12, 0)))
    }
}
