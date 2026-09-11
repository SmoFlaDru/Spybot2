package com.spybot.web.service.namegen

import kotlin.math.abs
import kotlin.math.max

/**
 * Scores how well a Counter-Strike term can stand in for part of a person's name as a pun.
 *
 * Two ideas carry the whole thing:
 *  - Puns are onset edits. "Harry"→"Carry", "Gysi"→"Easy", "Lesch"→"Flash" all swap, drop or
 *    add the initial consonant(s) and keep the rest, so gaps inside the onset cluster are cheap.
 *  - Rhyme lives at the end of a word. Costs are weighted towards the tail, and the rime
 *    (from the second-to-last vowel onwards) is scored separately and dominates the blend.
 */
object PhoneticMatcher {
    /** Minimum score for a substitution to count as a pun. Tuning knob - see NameGenService. */
    const val THRESHOLD = 0.80

    private const val INDEL = 0.9
    private const val ONSET_INDEL = 0.35
    private const val HEAD_WEIGHT = 0.55
    private const val TAIL_WEIGHT = 1.45
    private const val ALIGN_WEIGHT = 0.30
    private const val RIME_WEIGHT = 0.70
    private const val SYLLABLE_PENALTY = 0.15

    data class Match(
        val score: Double,
        val alignScore: Double,
        val rimeScore: Double,
    )

    fun score(
        name: List<Phoneme>,
        slang: List<Phoneme>,
    ): Match {
        if (name.isEmpty() || slang.isEmpty()) return Match(0.0, 0.0, 0.0)
        // Identical sound is not a joke ("King" × king).
        if (Phonemes.symbolsOf(name) == Phonemes.symbolsOf(slang)) return Match(0.0, 1.0, 1.0)

        val align = alignScore(name, slang, positional = true, onsetDiscount = true)
        val rime = alignScore(rime(name), rime(slang), positional = false, onsetDiscount = false)
        val syllableDifference = abs(syllables(name) - syllables(slang))
        val blended = (ALIGN_WEIGHT * align + RIME_WEIGHT * rime) * (1.0 - SYLLABLE_PENALTY * syllableDifference)
        return Match(blended.coerceIn(0.0, 1.0), align, rime)
    }

    fun syllables(phonemes: List<Phoneme>): Int = phonemes.count { it.isVowel }

    /** From the second-to-last vowel (or the only vowel) to the end - the part that has to rhyme. */
    internal fun rime(phonemes: List<Phoneme>): List<Phoneme> {
        val vowelIndices = phonemes.indices.filter { phonemes[it].isVowel }
        if (vowelIndices.isEmpty()) return phonemes
        val start = if (vowelIndices.size >= 2) vowelIndices[vowelIndices.size - 2] else vowelIndices.last()
        return phonemes.subList(start, phonemes.size)
    }

    /**
     * 1.0 = identical, 0.0 = nothing in common. Weighted edit distance over phonemes, normalised
     * by the cost of aligning nothing at all.
     */
    internal fun alignScore(
        a: List<Phoneme>,
        b: List<Phoneme>,
        positional: Boolean,
        onsetDiscount: Boolean,
    ): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val n = max(a.size, b.size)

        fun weight(position: Int): Double =
            if (!positional || n <= 1) 1.0 else HEAD_WEIGHT + (TAIL_WEIGHT - HEAD_WEIGHT) * position / (n - 1)

        fun positionAt(
            i: Int,
            j: Int,
        ): Int = (max(i, j) - 1).coerceIn(0, n - 1)

        val onsetEndA = if (onsetDiscount) a.indexOfFirst { it.isVowel }.let { if (it < 0) a.size else it } else 0
        val onsetEndB = if (onsetDiscount) b.indexOfFirst { it.isVowel }.let { if (it < 0) b.size else it } else 0

        fun gapCost(inOnset: Boolean): Double = if (inOnset) ONSET_INDEL else INDEL

        val dp = Array(a.size + 1) { DoubleArray(b.size + 1) }
        for (i in 1..a.size) {
            dp[i][0] = dp[i - 1][0] + gapCost(i - 1 < onsetEndA) * weight(positionAt(i, 0))
        }
        for (j in 1..b.size) {
            dp[0][j] = dp[0][j - 1] + gapCost(j - 1 < onsetEndB) * weight(positionAt(0, j))
        }
        for (i in 1..a.size) {
            for (j in 1..b.size) {
                val w = weight(positionAt(i, j))
                val substitute = dp[i - 1][j - 1] + Phonemes.featureDistance(a[i - 1], b[j - 1]) * w
                val delete = dp[i - 1][j] + gapCost(i - 1 < onsetEndA) * w
                val insert = dp[i][j - 1] + gapCost(j - 1 < onsetEndB) * w
                dp[i][j] = minOf(substitute, delete, insert)
            }
        }

        val worstCase = (0 until n).sumOf { weight(it) }
        return (1.0 - dp[a.size][b.size] / worstCase).coerceIn(0.0, 1.0)
    }
}
