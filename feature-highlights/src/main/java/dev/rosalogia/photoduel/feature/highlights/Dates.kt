package dev.rosalogia.photoduel.feature.highlights

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d")
private val shortDay = DateTimeFormatter.ofPattern("MMM d")
private val longDay = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

/** "Sat, Oct 18" for a day, "Oct 12 – Oct 18" for a range. */
fun formatDays(start: LocalDate, end: LocalDate): String =
    if (start == end) {
        start.format(dayFormat) + if (start.year != LocalDate.now().year) ", ${start.year}" else ""
    } else {
        "${start.format(shortDay)} – ${end.format(if (end.year != LocalDate.now().year) longDay else shortDay)}"
    }

fun defaultName(start: LocalDate, end: LocalDate): String = formatDays(start, end)

/** For a stored [start, end) range in millis. */
fun formatRange(startMillis: Long, endMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
    val end = Instant.ofEpochMilli(endMillis - 1).atZone(zone).toLocalDate()
    return formatDays(start, end)
}
