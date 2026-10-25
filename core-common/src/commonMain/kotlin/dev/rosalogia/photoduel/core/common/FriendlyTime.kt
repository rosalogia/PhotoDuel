package dev.rosalogia.photoduel.core.common

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

private val clock = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val weekday = DateTimeFormatter.ofPattern("EEEE")
private val sameYear = DateTimeFormatter.ofPattern("EEE, MMM d")
private val otherYear = DateTimeFormatter.ofPattern("MMM d, yyyy")

/**
 * When a photo was taken, the way people say it: "Today, 9:41 AM", "Yesterday, 6:02 PM",
 * "Tuesday, 8:15 PM" within the last week, "Sat, Oct 4, 3:30 PM" this year, else "Oct 4, 2024, 3:30 PM".
 */
fun friendlyTime(millis: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
    val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
    val today = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(t.toLocalDate(), today)
    val day = when {
        days == 0L -> "Today"
        days == 1L -> "Yesterday"
        days in 2..6 -> t.format(weekday)
        t.year == today.year -> t.format(sameYear)
        else -> t.format(otherYear)
    }
    return "$day, ${t.format(clock)}"
}
