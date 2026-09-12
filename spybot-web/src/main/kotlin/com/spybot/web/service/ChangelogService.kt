package com.spybot.web.service

import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule
import tools.jackson.module.kotlin.readValue
import java.time.Instant

/**
 * One group of changelog bullets: everything a single master commit added to CHANGELOG.md.
 * [commit] and [committedAt] are null only for bullets that exist in the working tree but aren't
 * committed yet, which can only happen on a developer machine.
 */
data class ChangelogEntry(
    val commit: String?,
    val committedAt: Instant?,
    val tags: List<String>,
    val bullets: List<String>,
) {
    val isCommitted: Boolean
        get() = commit != null

    /** What the page leads with: a release tag when the commit has one, otherwise the commit. */
    val title: String
        get() = tags.firstOrNull() ?: commit ?: "Not committed yet"
}

/**
 * Reads changelog.json, generated at build time by the `generateChangelog` Gradle task in
 * spybot-web/build.gradle.kts. CHANGELOG.md itself is a flat list of bullets; the task uses git
 * to group them by the master commit that added each one, so the file never has to know about
 * versions, dates or hashes - a release is a tag on master.
 */
@Service
class ChangelogService {
    private val log = LoggerFactory.getLogger(javaClass)

    private val parsedEntries: List<ChangelogEntry> by lazy { load() }

    fun entries(): List<ChangelogEntry> = parsedEntries

    private fun load(): List<ChangelogEntry> {
        val resource = ClassPathResource("changelog.json")
        if (!resource.exists()) {
            log.warn("changelog.json not found on the classpath; the changelog page will be empty")
            return emptyList()
        }
        return resource.inputStream.use { parseJson(it.readBytes()) }
    }

    internal fun parseJson(json: ByteArray): List<ChangelogEntry> {
        val raw: List<RawSection> = mapper.readValue(json)
        return raw.map { ChangelogEntry(it.commit, it.committedAt?.let(Instant::parse), it.tags, it.bullets) }
    }

    private data class RawSection(
        val commit: String?,
        val committedAt: String?,
        val tags: List<String> = emptyList(),
        val bullets: List<String> = emptyList(),
    )

    companion object {
        private val mapper: JsonMapper = JsonMapper.builder().addModule(kotlinModule()).build()
    }
}
