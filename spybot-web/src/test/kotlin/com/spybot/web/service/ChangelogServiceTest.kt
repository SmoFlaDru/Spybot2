package com.spybot.web.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
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
    fun `parses the Unreleased section as an entry without a date or commit`() {
        val text =
            """
            # Changelog

            ## Unreleased
            - Merged but not yet released (#42)

            ## v1.0.0 - 2026-01-01 (0000000)
            - First release
            """.trimIndent()

        val entries = service.parseText(text)

        assertEquals(2, entries.size)
        assertEquals(ChangelogEntry("Unreleased", null, null, listOf("Merged but not yet released (#42)")), entries[0])
        assertEquals(false, entries[0].isReleased)
        assertEquals(true, entries[1].isReleased)
    }

    @Test
    fun `the real CHANGELOG file parses with every heading recognised`() {
        // Guards the format the Changelog CI check relies on: a heading nobody can parse would be
        // silently skipped on the /changelog page, hiding entries.
        val text = ClassPathResource("CHANGELOG.md").inputStream.bufferedReader().use { it.readText() }
        val outsideCodeFences = text.replace(Regex("""(?s)```.*?```"""), "")
        val headings = outsideCodeFences.lines().count { it.startsWith("## ") }

        val entries = service.parseText(text)

        assertEquals(headings, entries.size)
        assertEquals("Unreleased", entries.first().version)
        assertTrue(entries.first().features.isNotEmpty())
        assertTrue(entries.all { it.features.isNotEmpty() })
    }

    @Test
    fun `ignores example headings inside fenced code blocks`() {
        val text =
            """
            # Changelog

            ```
            ## Unreleased
            - <notable change>
            ```

            ## Unreleased
            - Real entry
            """.trimIndent()

        val entries = service.parseText(text)

        assertEquals(1, entries.size)
        assertEquals(listOf("Real entry"), entries[0].features)
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
