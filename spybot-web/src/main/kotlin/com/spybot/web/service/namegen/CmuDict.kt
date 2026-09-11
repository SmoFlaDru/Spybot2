package com.spybot.web.service.namegen

import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource

/**
 * English pronunciations from the CMU Pronouncing Dictionary (cmusphinx/cmudict, BSD licence -
 * see namegen/cmudict.LICENSE). The whole file ships on the classpath so new seed entries just
 * work, but only the words asked for in [wanted] are kept in memory: the dictionary has ~135k
 * entries and the generator needs a couple of hundred.
 *
 * Only the first pronunciation of each word is used; the "word(2)" variants are skipped.
 */
class CmuDict(
    wanted: Set<String>,
    resourcePath: String = "namegen/cmudict.dict",
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val entries: Map<String, List<Phoneme>> = load(wanted, resourcePath)

    val size: Int
        get() = entries.size

    fun lookup(word: String): List<Phoneme>? = entries[normalise(word)]

    private fun load(
        wanted: Set<String>,
        resourcePath: String,
    ): Map<String, List<Phoneme>> {
        val resource = ClassPathResource(resourcePath)
        if (!resource.exists()) {
            log.warn("{} not found on the classpath; English name pronunciations will be unavailable", resourcePath)
            return emptyMap()
        }
        val wantedNormalised = wanted.map(::normalise).toSet()
        val result = HashMap<String, List<Phoneme>>()
        resource.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            for (line in lines) {
                val content = line.substringBefore('#').trim()
                if (content.isEmpty()) continue
                val space = content.indexOf(' ')
                if (space < 0) continue
                val word = content.substring(0, space)
                if (word.endsWith(")") || word !in wantedNormalised || word in result) continue
                val phones =
                    content
                        .substring(space + 1)
                        .trim()
                        .split(' ')
                        .filter { it.isNotEmpty() }
                result[word] = Phonemes.parse(phones.joinToString("") { arpabetToIpa(it) })
            }
        }
        return result
    }

    private fun normalise(word: String): String = word.lowercase()

    companion object {
        /** ARPABET symbol (stress digit already stripped) to IPA. AH is the only stress-sensitive one. */
        private val table =
            mapOf(
                "AA" to "ɑ",
                "AE" to "æ",
                "AO" to "ɔ",
                "AW" to "aʊ",
                "AY" to "aɪ",
                "EH" to "ɛ",
                "ER" to "ɚ",
                "EY" to "eɪ",
                "IH" to "ɪ",
                "IY" to "i",
                "OW" to "oʊ",
                "OY" to "ɔɪ",
                "UH" to "ʊ",
                "UW" to "u",
                "B" to "b",
                "CH" to "tʃ",
                "D" to "d",
                "DH" to "ð",
                "F" to "f",
                "G" to "ɡ",
                "HH" to "h",
                "JH" to "dʒ",
                "K" to "k",
                "L" to "l",
                "M" to "m",
                "N" to "n",
                "NG" to "ŋ",
                "P" to "p",
                "R" to "ɹ",
                "S" to "s",
                "SH" to "ʃ",
                "T" to "t",
                "TH" to "θ",
                "V" to "v",
                "W" to "w",
                "Y" to "j",
                "Z" to "z",
                "ZH" to "ʒ",
            )

        internal fun arpabetToIpa(phone: String): String {
            val stress = phone.lastOrNull()?.takeIf { it.isDigit() }
            val base = if (stress != null) phone.dropLast(1) else phone
            if (base == "AH") return if (stress == '0') "ə" else "ʌ"
            return table[base] ?: throw IllegalArgumentException("Unknown ARPABET phone '$phone'")
        }
    }
}
