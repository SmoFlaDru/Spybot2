package com.spybot.web.jte

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// Pinned to English so month names don't depend on the server's locale ("Sep" vs en_GB's "Sept").
private val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
private val timeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** "13 h 42 min" from a number of seconds; whole hours or minutes only, "0 min" for nothing. */
fun formatDuration(seconds: Double): String {
    val totalMinutes = (seconds / 60).toLong()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours h $minutes min"
        hours > 0 -> "$hours h"
        else -> "$minutes min"
    }
}

/** "3 Sep 2026" */
fun formatDay(day: LocalDate): String = day.format(dayFormat)

/** "14:05 UTC" */
fun formatTime(at: OffsetDateTime): String = at.withOffsetSameInstant(ZoneOffset.UTC).format(timeFormat) + " UTC"
