package com.spybot.web.service.namegen

import kotlin.math.abs

/**
 * A phoneme described by articulatory features on ordered numeric scales, so that "adjacent
 * articulations sound similar" falls out of plain subtraction. This is what lets the matcher
 * treat German /yː/ and English /iː/ (differ only in lip rounding) as near-identical while
 * keeping /a/ and /u/ far apart - the graded similarity that bucket encoders like Soundex or
 * Metaphone cannot express.
 */
sealed interface Phoneme {
    val symbol: String

    /** [height]: 0.0 open .. 1.0 close. [backness]: 0.0 front .. 1.0 back. */
    data class Vowel(
        override val symbol: String,
        val height: Double,
        val backness: Double,
        val rounded: Boolean,
        val long: Boolean = false,
        val rhotic: Boolean = false,
        /** Unstressed/reduced (schwa-like): audibly different from a full vowel even at the same height. */
        val reduced: Boolean = false,
    ) : Phoneme

    /** A diphthong is scored against its start and end targets, so it stays one syllable nucleus. */
    data class Diphthong(
        override val symbol: String,
        val start: Vowel,
        val end: Vowel,
    ) : Phoneme

    /** [place]: 0.0 bilabial .. 1.0 glottal (see [Place]). */
    data class Consonant(
        override val symbol: String,
        val place: Double,
        val manner: Manner,
        val voiced: Boolean,
        val rhotic: Boolean = false,
    ) : Phoneme

    val isVowel: Boolean
        get() = this is Vowel || this is Diphthong
}

enum class Manner { PLOSIVE, FRICATIVE, AFFRICATE, NASAL, LATERAL, APPROXIMANT, TRILL }

object Place {
    const val BILABIAL = 0.0
    const val LABIODENTAL = 0.1
    const val DENTAL = 0.2
    const val ALVEOLAR = 0.3
    const val POSTALVEOLAR = 0.45
    const val PALATAL = 0.6
    const val VELAR = 0.75
    const val UVULAR = 0.85
    const val GLOTTAL = 1.0
}

object Phonemes {
    private fun v(
        symbol: String,
        height: Double,
        backness: Double,
        rounded: Boolean,
        long: Boolean = false,
        rhotic: Boolean = false,
        reduced: Boolean = false,
    ) = Phoneme.Vowel(symbol, height, backness, rounded, long, rhotic, reduced)

    private fun c(
        symbol: String,
        place: Double,
        manner: Manner,
        voiced: Boolean,
        rhotic: Boolean = false,
    ) = Phoneme.Consonant(symbol, place, manner, voiced, rhotic)

    // Vowel heights: open 0.0, near-open 0.17, open-mid 0.33, mid 0.5, close-mid 0.67, near-close 0.83, close 1.0
    private val I = v("i", 1.0, 0.0, rounded = false)
    private val I_LONG = v("iː", 1.0, 0.0, rounded = false, long = true)
    private val I_SHORT = v("ɪ", 0.83, 0.1, rounded = false)
    private val Y = v("y", 1.0, 0.0, rounded = true)
    private val Y_LONG = v("yː", 1.0, 0.0, rounded = true, long = true)
    private val Y_SHORT = v("ʏ", 0.83, 0.1, rounded = true)
    private val E = v("e", 0.67, 0.0, rounded = false)
    private val E_LONG = v("eː", 0.67, 0.0, rounded = false, long = true)
    private val E_OPEN = v("ɛ", 0.33, 0.0, rounded = false)
    private val E_OPEN_LONG = v("ɛː", 0.33, 0.0, rounded = false, long = true)
    private val AE = v("æ", 0.17, 0.0, rounded = false)
    private val OE_CLOSE = v("ø", 0.67, 0.0, rounded = true)
    private val OE_CLOSE_LONG = v("øː", 0.67, 0.0, rounded = true, long = true)
    private val OE_OPEN = v("œ", 0.33, 0.0, rounded = true)
    private val A = v("a", 0.0, 0.25, rounded = false)
    private val A_LONG = v("aː", 0.0, 0.25, rounded = false, long = true)
    private val A_BACK = v("ɑ", 0.0, 1.0, rounded = false)
    private val A_BACK_LONG = v("ɑː", 0.0, 1.0, rounded = false, long = true)
    private val O_OPEN_ROUND = v("ɒ", 0.0, 1.0, rounded = true)
    private val CARET = v("ʌ", 0.33, 0.7, rounded = false)
    private val SCHWA = v("ə", 0.5, 0.5, rounded = false, reduced = true)
    private val SCHWA_OPEN = v("ɐ", 0.17, 0.5, rounded = false, rhotic = true, reduced = true)
    private val SCHWA_RHOTIC = v("ɚ", 0.33, 0.5, rounded = false, rhotic = true, reduced = true)
    private val E_CENTRAL = v("ɜ", 0.33, 0.5, rounded = false)
    private val E_CENTRAL_LONG = v("ɜː", 0.33, 0.5, rounded = false, long = true)
    private val O_OPEN = v("ɔ", 0.33, 1.0, rounded = true)
    private val O_OPEN_LONG = v("ɔː", 0.33, 1.0, rounded = true, long = true)
    private val O = v("o", 0.67, 1.0, rounded = true)
    private val O_LONG = v("oː", 0.67, 1.0, rounded = true, long = true)
    private val U_SHORT = v("ʊ", 0.83, 0.9, rounded = true)
    private val U = v("u", 1.0, 1.0, rounded = true)
    private val U_LONG = v("uː", 1.0, 1.0, rounded = true, long = true)

    private val vowels =
        listOf(
            I,
            I_LONG,
            I_SHORT,
            Y,
            Y_LONG,
            Y_SHORT,
            E,
            E_LONG,
            E_OPEN,
            E_OPEN_LONG,
            AE,
            OE_CLOSE,
            OE_CLOSE_LONG,
            OE_OPEN,
            A,
            A_LONG,
            A_BACK,
            A_BACK_LONG,
            O_OPEN_ROUND,
            CARET,
            SCHWA,
            SCHWA_OPEN,
            SCHWA_RHOTIC,
            E_CENTRAL,
            E_CENTRAL_LONG,
            O_OPEN,
            O_OPEN_LONG,
            O,
            O_LONG,
            U_SHORT,
            U,
            U_LONG,
        )

    private val diphthongs =
        listOf(
            Phoneme.Diphthong("aɪ", A, I_SHORT),
            Phoneme.Diphthong("aʊ", A, U_SHORT),
            Phoneme.Diphthong("ɔʏ", O_OPEN, Y_SHORT),
            Phoneme.Diphthong("ɔɪ", O_OPEN, I_SHORT),
            Phoneme.Diphthong("eɪ", E, I_SHORT),
            Phoneme.Diphthong("oʊ", O, U_SHORT),
            Phoneme.Diphthong("əʊ", SCHWA, U_SHORT),
        ) +
            // German vocalised /r/ after a vowel ("Karl", "Merkel", "Jürgen") forms one nucleus with
            // it, not a second syllable. GermanG2P emits "ɐ" for coda r; pairing it with the vowel
            // here keeps syllable counts honest.
            listOf(
                A,
                A_LONG,
                E_OPEN,
                E_OPEN_LONG,
                E_LONG,
                I_SHORT,
                I_LONG,
                O_OPEN,
                O_LONG,
                U_SHORT,
                U_LONG,
                Y_SHORT,
                Y_LONG,
                OE_OPEN,
                OE_CLOSE_LONG,
            ).map { Phoneme.Diphthong(it.symbol + "ɐ", it, SCHWA_OPEN) }

    private val consonants =
        listOf(
            c("p", Place.BILABIAL, Manner.PLOSIVE, voiced = false),
            c("b", Place.BILABIAL, Manner.PLOSIVE, voiced = true),
            c("m", Place.BILABIAL, Manner.NASAL, voiced = true),
            c("f", Place.LABIODENTAL, Manner.FRICATIVE, voiced = false),
            c("v", Place.LABIODENTAL, Manner.FRICATIVE, voiced = true),
            // Labial-velar; placed near /v/ because German speakers realise English /w/ as [v].
            c("w", 0.15, Manner.APPROXIMANT, voiced = true),
            c("θ", Place.DENTAL, Manner.FRICATIVE, voiced = false),
            c("ð", Place.DENTAL, Manner.FRICATIVE, voiced = true),
            c("t", Place.ALVEOLAR, Manner.PLOSIVE, voiced = false),
            c("d", Place.ALVEOLAR, Manner.PLOSIVE, voiced = true),
            c("n", Place.ALVEOLAR, Manner.NASAL, voiced = true),
            c("s", Place.ALVEOLAR, Manner.FRICATIVE, voiced = false),
            c("z", Place.ALVEOLAR, Manner.FRICATIVE, voiced = true),
            c("l", Place.ALVEOLAR, Manner.LATERAL, voiced = true),
            c("r", Place.ALVEOLAR, Manner.TRILL, voiced = true, rhotic = true),
            c("ɹ", Place.ALVEOLAR, Manner.APPROXIMANT, voiced = true, rhotic = true),
            c("ʃ", Place.POSTALVEOLAR, Manner.FRICATIVE, voiced = false),
            c("ʒ", Place.POSTALVEOLAR, Manner.FRICATIVE, voiced = true),
            c("tʃ", Place.POSTALVEOLAR, Manner.AFFRICATE, voiced = false),
            c("dʒ", Place.POSTALVEOLAR, Manner.AFFRICATE, voiced = true),
            c("ç", Place.PALATAL, Manner.FRICATIVE, voiced = false),
            c("j", Place.PALATAL, Manner.APPROXIMANT, voiced = true),
            c("k", Place.VELAR, Manner.PLOSIVE, voiced = false),
            c("ɡ", Place.VELAR, Manner.PLOSIVE, voiced = true),
            c("ŋ", Place.VELAR, Manner.NASAL, voiced = true),
            c("x", Place.VELAR, Manner.FRICATIVE, voiced = false),
            c("ʁ", Place.UVULAR, Manner.FRICATIVE, voiced = true, rhotic = true),
            c("h", Place.GLOTTAL, Manner.FRICATIVE, voiced = false),
        )

    private val bySymbol: Map<String, Phoneme> = (vowels + diphthongs + consonants).associateBy { it.symbol }
    private val maxSymbolLength = bySymbol.keys.maxOf { it.length }

    // Stress marks, syllable dots, and diacritics carry no information the matcher uses.
    private val ignored = setOf('ˈ', 'ˌ', '.', ' ', '/', '[', ']', '͡', '̯', 'ʰ')

    /**
     * Tokenises an IPA string into phonemes, longest symbol first. The ASCII letter 'g' is
     * accepted as an alias for IPA /ɡ/ because it is a very easy typo in hand-written data.
     */
    fun parse(ipa: String): List<Phoneme> {
        val text = ipa.replace('g', 'ɡ')
        val result = mutableListOf<Phoneme>()
        var index = 0
        while (index < text.length) {
            if (text[index] in ignored) {
                index++
                continue
            }
            var matched: Phoneme? = null
            for (length in minOf(maxSymbolLength, text.length - index) downTo 1) {
                matched = bySymbol[text.substring(index, index + length)]
                if (matched != null) {
                    index += length
                    break
                }
            }
            result += matched ?: throw IllegalArgumentException("Unknown IPA symbol '${text[index]}' at index $index in '$ipa'")
        }
        return result
    }

    fun symbolsOf(phonemes: List<Phoneme>): String = phonemes.joinToString("") { it.symbol }

    private val mannerDistance: Map<Pair<Manner, Manner>, Double> =
        listOf(
            Triple(Manner.PLOSIVE, Manner.AFFRICATE, 0.3),
            Triple(Manner.FRICATIVE, Manner.AFFRICATE, 0.3),
            Triple(Manner.PLOSIVE, Manner.FRICATIVE, 0.5),
            Triple(Manner.NASAL, Manner.PLOSIVE, 0.5),
            Triple(Manner.LATERAL, Manner.APPROXIMANT, 0.25),
            Triple(Manner.TRILL, Manner.APPROXIMANT, 0.3),
            Triple(Manner.TRILL, Manner.LATERAL, 0.4),
            Triple(Manner.TRILL, Manner.FRICATIVE, 0.5),
            Triple(Manner.FRICATIVE, Manner.APPROXIMANT, 0.4),
            Triple(Manner.NASAL, Manner.LATERAL, 0.85),
            Triple(Manner.NASAL, Manner.APPROXIMANT, 0.85),
        ).flatMap { (a, b, d) -> listOf((a to b) to d, (b to a) to d) }.toMap()

    private fun mannerDistance(
        a: Manner,
        b: Manner,
    ): Double = if (a == b) 0.0 else mannerDistance[a to b] ?: 1.0

    private fun vowelDistance(
        a: Phoneme.Vowel,
        b: Phoneme.Vowel,
    ): Double =
        0.45 * abs(a.height - b.height) +
            0.35 * abs(a.backness - b.backness) +
            0.15 * (if (a.rounded != b.rounded) 1.0 else 0.0) +
            0.05 * (if (a.long != b.long) 1.0 else 0.0) +
            0.15 * (if (a.reduced != b.reduced) 1.0 else 0.0)

    private const val GLIDE_MISMATCH = 0.2

    private fun isRhotic(p: Phoneme): Boolean =
        when (p) {
            is Phoneme.Vowel -> p.rhotic
            is Phoneme.Consonant -> p.rhotic
            is Phoneme.Diphthong -> false
        }

    /** 0.0 = identical, 1.0 = maximally different (a vowel against a consonant). */
    fun featureDistance(
        a: Phoneme,
        b: Phoneme,
    ): Double {
        if (a == b) return 0.0
        // German coda /ɐ/, English /ɚ/, /ɹ/ and /ʁ/ are all "the r sound" to a listener.
        if (isRhotic(a) && isRhotic(b)) return 0.1
        // A vowel+ɐ diphthong against a consonantal r: the r matches, the vowel is missing.
        if (a is Phoneme.Diphthong && a.end.rhotic && b is Phoneme.Consonant && b.rhotic) return 0.45
        if (b is Phoneme.Diphthong && b.end.rhotic && a is Phoneme.Consonant && a.rhotic) return 0.45
        return when {
            a is Phoneme.Vowel && b is Phoneme.Vowel -> {
                vowelDistance(a, b)
            }

            a is Phoneme.Diphthong && b is Phoneme.Diphthong -> {
                0.5 * (vowelDistance(a.start, b.start) + vowelDistance(a.end, b.end))
            }

            a is Phoneme.Diphthong && b is Phoneme.Vowel -> {
                0.5 * (vowelDistance(a.start, b) + vowelDistance(a.end, b)) + GLIDE_MISMATCH
            }

            a is Phoneme.Vowel && b is Phoneme.Diphthong -> {
                featureDistance(b, a)
            }

            a is Phoneme.Consonant && b is Phoneme.Consonant -> {
                0.55 * abs(a.place - b.place) +
                    0.4 * mannerDistance(a.manner, b.manner) +
                    0.2 * (if (a.voiced != b.voiced) 1.0 else 0.0)
            }

            else -> {
                1.0
            }
        }.coerceIn(0.0, 1.0)
    }
}
