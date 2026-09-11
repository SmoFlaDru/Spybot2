package com.spybot.web.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import java.time.LocalDate

class ChangelogServiceTest {
    private val service = ChangelogService()

    @Test
    fun `parses sections newest first with commit, date, tags and bullets`() {
        val json =
            """
            [
              {"commit": "abc1234", "date": "2026-09-11", "tags": ["v3.1.0"], "bullets": ["Newer change (#12)"]},
              {"commit": "def5678", "date": "2026-08-31", "tags": [], "bullets": ["Older change", "Another older change"]}
            ]
            """.trimIndent()

        val entries = service.parseJson(json.toByteArray())

        assertEquals(2, entries.size)
        assertEquals(ChangelogEntry("abc1234", LocalDate.of(2026, 9, 11), listOf("v3.1.0"), listOf("Newer change (#12)")), entries[0])
        assertEquals("v3.1.0", entries[0].title)
        assertEquals("def5678", entries[1].title)
        assertEquals(listOf("Older change", "Another older change"), entries[1].bullets)
    }

    @Test
    fun `an uncommitted section has no commit or date`() {
        val json = """[{"commit": null, "date": null, "tags": [], "bullets": ["Local, not committed"]}]"""

        val entries = service.parseJson(json.toByteArray())

        assertEquals(false, entries.single().isCommitted)
        assertEquals("Not committed yet", entries.single().title)
    }

    @Test
    fun `tolerates missing optional fields`() {
        val entries = service.parseJson("""[{"commit": "abc1234", "date": "2026-01-01"}]""".toByteArray())

        assertEquals(emptyList<String>(), entries.single().tags)
        assertEquals(emptyList<String>(), entries.single().bullets)
    }

    @Test
    fun `the generated changelog for this repository is on the classpath and non-empty`() {
        // changelog.json is produced from CHANGELOG.md by the generateChangelog Gradle task, which
        // runs before tests. Guards the wiring end to end: file present, bullets attributed.
        val entries = service.parseJson(ClassPathResource("changelog.json").inputStream.use { it.readBytes() })

        assertTrue(entries.isNotEmpty())
        assertTrue(entries.all { it.bullets.isNotEmpty() })
        assertTrue(entries.any { it.isCommitted })
        assertTrue(entries.flatMap { it.bullets }.any { it.contains("(#208)") })
    }
}
