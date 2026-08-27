package com.spybot.web.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ChangelogServiceTest {
    private val service = ChangelogService()

    @Test
    fun `parses multiple entries newest first, in file order`() {
        val text =
            """
            # Changelog

            ## v1.4.0 - 2026-08-27 (a1b2c3d)
            - Added a changelog page
            - Fixed a recorder hang

            ## v1.3.0 - 2026-08-20 (9f8e7d6)
            - Earlier change
            """.trimIndent()

        val entries = service.parseText(text)

        assertEquals(2, entries.size)
        assertEquals(
            ChangelogEntry(
                version = "v1.4.0",
                date = LocalDate.of(2026, 8, 27),
                commitHash = "a1b2c3d",
                features = listOf("Added a changelog page", "Fixed a recorder hang"),
            ),
            entries[0],
        )
        assertEquals("v1.3.0", entries[1].version)
    }

    @Test
    fun `parses a single entry`() {
        val text =
            """
            # Changelog

            ## v1.0.0 - 2026-01-01 (0000000)
            - First release
            """.trimIndent()

        val entries = service.parseText(text)

        assertEquals(1, entries.size)
        assertEquals(listOf("First release"), entries[0].features)
    }

    @Test
    fun `skips a malformed entry header instead of failing the whole file`() {
        val text =
            """
            # Changelog

            ## not a valid header at all
            - This entry should be skipped

            ## v1.0.0 - 2026-01-01 (0000000)
            - This entry should still parse
            """.trimIndent()

        val entries = service.parseText(text)

        assertEquals(1, entries.size)
        assertEquals("v1.0.0", entries[0].version)
    }

    @Test
    fun `skips an entry with an unparseable date`() {
        val text =
            """
            # Changelog

            ## v2.0.0 - not-a-date (0000000)
            - Skipped

            ## v1.0.0 - 2026-01-01 (0000000)
            - Kept
            """.trimIndent()

        val entries = service.parseText(text)

        assertEquals(1, entries.size)
        assertEquals("v1.0.0", entries[0].version)
    }

    @Test
    fun `returns an empty list for a file with no entries`() {
        val entries = service.parseText("# Changelog\n\nNothing here yet.\n")

        assertTrue(entries.isEmpty())
    }

    @Test
    fun `ignores non-bullet lines within an entry block`() {
        val text =
            """
            # Changelog

            ## v1.0.0 - 2026-01-01 (0000000)
            Some preamble text that is not a bullet.
            - A real feature bullet
            """.trimIndent()

        val entries = service.parseText(text)

        assertEquals(listOf("A real feature bullet"), entries[0].features)
    }
}
