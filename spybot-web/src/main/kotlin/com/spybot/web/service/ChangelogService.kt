package com.spybot.web.service

import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import java.time.LocalDate

data class ChangelogEntry(
    val version: String,
    val date: LocalDate,
    val commitHash: String,
    val features: List<String>,
)

/**
 * Reads CHANGELOG.md (bundled onto the classpath at build time - see the processResources
 * config in spybot-web/build.gradle.kts) and parses it into structured entries for the
 * /changelog page. The file is the source of truth: entries are added by hand in the same PR
 * as the change they describe.
 */
@Service
class ChangelogService {
    private val log = LoggerFactory.getLogger(javaClass)

    private val parsedEntries: List<ChangelogEntry> by lazy { parse() }

    fun entries(): List<ChangelogEntry> = parsedEntries

    private fun parse(): List<ChangelogEntry> {
        val resource = ClassPathResource("CHANGELOG.md")
        if (!resource.exists()) {
            log.warn("CHANGELOG.md not found on the classpath; the changelog page will be empty")
            return emptyList()
        }

        val text = resource.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return parseText(text)
    }

    internal fun parseText(text: String): List<ChangelogEntry> {
        val blocks = text.split(Regex("(?m)^## "))
        return blocks.drop(1).mapNotNull { block -> parseEntry(block) }
    }

    private fun parseEntry(block: String): ChangelogEntry? {
        val lines = block.lines()
        val header = lines.firstOrNull() ?: return null
        val match = HEADER_PATTERN.matchEntire(header.trim())
        if (match == null) {
            log.warn("Skipping malformed CHANGELOG.md entry header: {}", header)
            return null
        }

        val (version, dateText, commitHash) = match.destructured
        val date =
            try {
                LocalDate.parse(dateText)
            } catch (error: Exception) {
                log.warn("Skipping CHANGELOG.md entry with unparseable date: {}", header, error)
                return null
            }

        val features =
            lines
                .drop(1)
                .filter { it.trimStart().startsWith("- ") }
                .map { it.trimStart().removePrefix("- ").trim() }

        return ChangelogEntry(version = version, date = date, commitHash = commitHash, features = features)
    }

    companion object {
        private val HEADER_PATTERN = Regex("""^(\S+) - (\d{4}-\d{2}-\d{2}) \(([0-9a-fA-F]+)\)$""")
    }
}
