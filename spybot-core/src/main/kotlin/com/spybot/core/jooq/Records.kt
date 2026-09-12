package com.spybot.core.jooq

import org.jooq.Record
import java.sql.Timestamp
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

// Typed accessors for the results of hand-written SQL, keyed by the column alias used in the
// query. Missing or NULL values fall back to a zero value; use notNull()/mapping for typed DSL reads.

internal fun Record.string(field: String): String = get(field, String::class.java) ?: ""

internal fun Record.long(field: String): Long = (get(field) as Number?)?.toLong() ?: 0L

internal fun Record.int(field: String): Int = (get(field) as Number?)?.toInt() ?: 0

internal fun Record.double(field: String): Double = (get(field) as Number?)?.toDouble() ?: 0.0

internal fun Record.boolean(field: String): Boolean = get(field, Boolean::class.java) ?: false

internal fun Record.offsetDateTime(field: String): OffsetDateTime? {
    val value = get(field)
    return when (value) {
        null -> null
        is OffsetDateTime -> value
        is Timestamp -> value.toInstant().atOffset(ZoneOffset.UTC)
        is java.time.LocalDateTime -> value.atOffset(ZoneOffset.UTC)
        else -> null
    }
}

internal fun Record.localDate(field: String): LocalDate? =
    when (val value = get(field)) {
        null -> null
        is LocalDate -> value
        is java.sql.Date -> value.toLocalDate()
        is Timestamp -> value.toInstant().atOffset(ZoneOffset.UTC).toLocalDate()
        else -> null
    }

internal fun parseJsonArray(raw: String): List<String> {
    val trimmed = raw.trim()
    if (trimmed.length < 2) {
        return emptyList()
    }
    return trimmed
        .removePrefix("[")
        .removeSuffix("]")
        .split(',')
        .map { it.trim().removePrefix("\"").removeSuffix("\"") }
        .filter { it.isNotBlank() }
}

internal fun OffsetDateTime.toEpochMillis(): Long = toInstant().toEpochMilli()
