package com.spybot.core.jooq

import org.jooq.Field

/**
 * Narrows a `Field<T?>` to `Field<T>` for a column that is NOT NULL in the schema.
 *
 * jOOQ's Kotlin generator keeps every `TableField`'s type parameter nullable, because any column
 * can come back null from an outer join. For a plain read of a NOT NULL column that nullability
 * is fictional, and papering over it with `?: 0L` silently turns an impossible state into a wrong
 * value. This converter makes the type honest and fails loudly if the impossible ever happens -
 * and it is what lets `Records.mapping(::Dto)` type-check against a DTO with non-null fields.
 */
fun <T : Any> Field<T?>.notNull(): Field<T> = convertFrom { it ?: error("column '$name' is NOT NULL but returned null") }
