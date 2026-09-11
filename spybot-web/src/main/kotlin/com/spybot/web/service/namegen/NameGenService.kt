package com.spybot.web.service.namegen

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

data class GeneratedName(
    val displayName: String,
    val realName: String,
    val slang: String,
    val category: String,
    val region: String,
    val score: Double,
)

/**
 * Proposes funny Steam names by swapping part of a well-known person's name for a
 * Counter-Strike term that sounds like it. The pool is computed once from [SeedData] by
 * [PhoneticMatcher]; nothing in it is hand-picked, so a name only appears if the phonetics
 * earn it. Tuning lives in [PhoneticMatcher.THRESHOLD] and the matcher's weights.
 */
@Service
class NameGenService {
    private val log = LoggerFactory.getLogger(javaClass)

    private val pool: List<GeneratedName> by lazy { buildPool() }

    fun generate(): GeneratedName = pool.random()

    fun all(): List<GeneratedName> = pool

    internal fun buildPool(): List<GeneratedName> {
        val englishWords =
            SeedData.people
                .filter { it.lang == Lang.EN }
                .flatMap { listOf(it.firstName, it.lastName) }
                .toSet()
        val cmuDict = CmuDict(englishWords)
        val slang = SeedData.slang.map { it to Phonemes.parse(it.ipa) }

        val pool =
            SeedData.people.flatMap { person ->
                naturalMatches(person, slang, cmuDict) +
                    bestFor(person, Slot.FIRST, slang, cmuDict) +
                    bestFor(person, Slot.LAST, slang, cmuDict)
            }
        log.info("Steam name generator pool built: {} names from {} people and {} terms", pool.size, SeedData.people.size, slang.size)
        return pool
    }

    private enum class Slot { FIRST, LAST }

    private companion object {
        const val MAX_PER_SLOT = 2
    }

    /**
     * A name that already *is* a Counter-Strike term needs no substitution: "Hansi Flick" is a
     * finished Steam name as it stands. Matched by spelling or by identical pronunciation, and
     * ranked above every constructed pun.
     */
    private fun naturalMatches(
        person: FamousPerson,
        slang: List<Pair<SlangTerm, List<Phoneme>>>,
        cmuDict: CmuDict,
    ): List<GeneratedName> =
        Slot.entries
            .mapNotNull { slot ->
                val namePart = if (slot == Slot.FIRST) person.firstName else person.lastName
                val phonemes = pronounce(namePart, if (slot == Slot.FIRST) person.firstIpa else person.lastIpa, person.lang, cmuDict)
                val symbols = phonemes?.let(Phonemes::symbolsOf)
                val term =
                    slang
                        .firstOrNull { (term, termPhonemes) ->
                            term.word.equals(namePart, ignoreCase = true) ||
                                (symbols != null && symbols == Phonemes.symbolsOf(termPhonemes))
                        }?.first ?: return@mapNotNull null
                GeneratedName(
                    displayName = "${person.firstName} ${person.lastName}",
                    realName = "${person.firstName} ${person.lastName}",
                    slang = term.word,
                    category = person.category,
                    region = person.region,
                    score = 1.0,
                )
            }.distinctBy { it.slang }

    /** The best few terms for one name slot; capped so a single person can't dominate the pool. */
    private fun bestFor(
        person: FamousPerson,
        slot: Slot,
        slang: List<Pair<SlangTerm, List<Phoneme>>>,
        cmuDict: CmuDict,
    ): List<GeneratedName> {
        val namePart = if (slot == Slot.FIRST) person.firstName else person.lastName
        val phonemes =
            pronounce(namePart, if (slot == Slot.FIRST) person.firstIpa else person.lastIpa, person.lang, cmuDict) ?: return emptyList()

        return slang
            .asSequence()
            .filter { (term, _) ->
                !term.word.equals(person.firstName, ignoreCase = true) &&
                    !term.word.equals(person.lastName, ignoreCase = true)
            }.map { (term, termPhonemes) -> term to PhoneticMatcher.score(phonemes, termPhonemes) }
            .filter { (_, match) -> match.score >= PhoneticMatcher.THRESHOLD }
            .sortedByDescending { (_, match) -> match.score }
            .take(MAX_PER_SLOT)
            .map { (term, match) ->
                GeneratedName(
                    displayName = if (slot == Slot.FIRST) "${term.display} ${person.lastName}" else "${person.firstName} ${term.display}",
                    realName = "${person.firstName} ${person.lastName}",
                    slang = term.word,
                    category = person.category,
                    region = person.region,
                    score = match.score,
                )
            }.toList()
    }

    private fun pronounce(
        namePart: String,
        override: String?,
        lang: Lang,
        cmuDict: CmuDict,
    ): List<Phoneme>? {
        if (override != null) return Phonemes.parse(override)
        val result =
            when (lang) {
                Lang.DE -> GermanG2P.transcribe(namePart)
                Lang.EN -> cmuDict.lookup(namePart)
            }
        if (result.isNullOrEmpty()) {
            log.warn("No pronunciation for '{}' ({}); add an IPA override in SeedData", namePart, lang)
            return null
        }
        return result
    }
}
