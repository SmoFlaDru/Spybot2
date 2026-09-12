package com.spybot.web.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import java.time.Instant

class ChangelogServiceTest {
    private val service = ChangelogService()

    @Test
    fun `parses sections newest first with commit, timestamp, tags and bullets`() {
        val json =
            """
            [
              {"commit": "abc1234", "committedAt": "2026-09-11T22:37:49Z", "tags": ["v3.1.0"], "bullets": ["Newer change (#12)"]},
              {"commit": "def5678", "committedAt": "2026-08-31T10:00:00Z", "tags": [], "bullets": ["Older change", "Another older change"]}
            ]
            """.trimIndent()

        val entries = service.parseJson(json.toByteArray())

        assertEquals(2, entries.size)
        assertEquals(ChangelogEntry("abc1234", Instant.parse("2026-09-11T22:37:49Z"), listOf("v3.1.0"), listOf("Newer change (#12)")), entries[0])
        assertEquals("v3.1.0", entries[0].title)
        assertEquals("def5678", entries[1].title)
        assertEquals(listOf("Older change", "Another older change"), entries[1].bullets)
    }

    @Test
    fun `an uncommitted section has no commit or timestamp`() {
        val json = """[{"commit": null, "committedAt": null, "tags": [], "bullets": ["Local, not committed"]}]"""

        val entries = service.parseJson(json.toByteArray())

        assertEquals(false, entries.single().isCommitted)
        assertEquals("Not committed yet", entries.single().title)
    }

    @Test
    fun `tolerates missing optional fields`() {
        val entries = service.parseJson("""[{"commit": "abc1234", "committedAt": "2026-01-01T00:00:00Z"}]""".toByteArray())

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
