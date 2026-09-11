package com.spybot.web.service.namegen

/**
 * Rule-based German grapheme-to-phoneme transcription. German spelling is regular enough that
 * ordered rewrite rules get proper names right far more often than not; the rare misses are
 * handled by an explicit IPA override on the seed entry rather than by piling on exceptions here.
 *
 * Output is a list of [Phoneme]s. Affricates /ts/ and /pf/ are emitted as two phonemes so they
 * line up naturally against English clusters like the /ts/ in "gates".
 */
object GermanG2P {
    private const val VOWEL_LETTERS = "aeiouyäöü"

    fun transcribe(word: String): List<Phoneme> =
        word
            .lowercase()
            .split('-', ' ')
            .filter { it.isNotBlank() }
            .flatMap { part -> Phonemes.parse(transcribePart(part.filter { it.isLetter() })) }

    private fun transcribePart(word: String): String {
        val out = StringBuilder()
        var i = 0
        val n = word.length

        fun at(offset: Int): Char? = word.getOrNull(i + offset)

        fun startsWith(text: String): Boolean = word.startsWith(text, i)

        fun isVowel(ch: Char?): Boolean = ch != null && ch in VOWEL_LETTERS

        fun hasVowelBefore(): Boolean = (0 until i).any { isVowel(word[it]) }

        fun prevIsBackVowel(): Boolean {
            val prev = at(-1) ?: return false
            val prev2 = at(-2)
            if (prev == 'u' && prev2 == 'a') return true // "au"
            if (prev == 'u' && prev2 == 'e') return false // "eu"
            if (prev == 'u' && prev2 == 'ä') return false // "äu"
            return prev == 'a' || prev == 'o' || prev == 'u'
        }

        /** Counts consonant letters following position [from] up to the next vowel or end. */
        fun consonantRunAfter(from: Int): Int {
            var j = from
            while (j < n && !isVowel(word[j])) j++
            return j - from
        }

        /**
         * A vowel is long in an open syllable (single consonant letter then another vowel) or
         * before a single final consonant, short before a cluster or a doubled consonant. "i" is
         * excluded from the final-consonant case: "Til", "Kim" are short.
         */
        fun isLongVowel(letter: Char): Boolean {
            val run = consonantRunAfter(i + 1)
            if (i + 1 >= n) return true // word-final vowel: "Otto" /ɔto/, "Joko"
            if (run == 0) return true
            if (run >= 2) return false
            val afterRun = i + 1 + run
            if (afterRun >= n) return letter != 'i'
            return true
        }

        while (i < n) {
            val ch = word[i]
            when {
                // ---- consonant clusters, longest first ----
                startsWith("tsch") -> {
                    out.append("tʃ")
                    i += 4
                }

                startsWith("tth") -> {
                    out.append("t")
                    i += 3
                }

                startsWith("sch") -> {
                    out.append("ʃ")
                    i += 3
                }

                startsWith("chs") -> {
                    out.append("ks")
                    i += 3
                }

                startsWith("ch") -> {
                    out.append(
                        when {
                            i == 0 -> "k"

                            // Christian, Christoph
                            prevIsBackVowel() -> "x"

                            else -> "ç"
                        },
                    )
                    i += 2
                }

                startsWith("ck") -> {
                    out.append("k")
                    i += 2
                }

                startsWith("ph") -> {
                    out.append("f")
                    i += 2
                }

                startsWith("th") -> {
                    out.append("t")
                    i += 2
                }

                startsWith("dt") -> {
                    out.append("t")
                    i += 2
                }

                startsWith("qu") -> {
                    out.append("kv")
                    i += 2
                }

                startsWith("ng") -> {
                    out.append("ŋ")
                    i += 2
                }

                startsWith("nk") && (i + 2 >= n || isVowel(at(2))) -> {
                    out.append("ŋk")
                    i += 2
                }

                startsWith("sp") && i == 0 -> {
                    out.append("ʃp")
                    i += 2
                }

                startsWith("st") && i == 0 -> {
                    out.append("ʃt")
                    i += 2
                }

                startsWith("ss") -> {
                    out.append("s")
                    i += 2
                }

                startsWith("tz") -> {
                    out.append("ts")
                    i += 2
                }

                ch == 'ß' -> {
                    out.append("s")
                    i += 1
                }

                ch == 'z' -> {
                    out.append("ts")
                    i += 1
                }

                ch == 'x' -> {
                    out.append("ks")
                    i += 1
                }

                ch == 'c' -> {
                    out.append(if (at(1) == 'e' || at(1) == 'i') "ts" else "k")
                    i += 1
                }

                ch == 'v' -> {
                    out.append("f")
                    i += 1
                }

                ch == 'w' -> {
                    out.append("v")
                    i += 1
                }

                ch == 'j' -> {
                    out.append("j")
                    i += 1
                }

                ch == 'y' && (i == 0 || isVowel(at(-1))) && isVowel(at(1)) -> {
                    out.append("j")
                    i += 1
                }

                ch == 's' -> {
                    out.append(if (isVowel(at(1))) "z" else "s")
                    i += 1
                }

                ch == 'h' -> {
                    // Between vowels or word-initial it is a real /h/; after a vowel it only
                    // lengthens that vowel, which the vowel rules handle by looking ahead.
                    if (i == 0 || isVowel(at(1))) out.append("h")
                    i += 1
                }

                ch == 'r' -> {
                    // Vocalised in the coda (before a consonant or at the end), consonantal before a vowel.
                    out.append(if (isVowel(at(1))) "ʁ" else "ɐ")
                    i += 1
                }

                ch == 'b' || ch == 'd' || ch == 'g' -> {
                    val final = i + 1 >= n || (!isVowel(at(1)) && consonantRunAfter(i + 1) + i + 1 >= n)
                    out.append(
                        when (ch) {
                            'b' -> if (final) "p" else "b"
                            'd' -> if (final) "t" else "d"
                            else -> if (final) "k" else "ɡ"
                        },
                    )
                    i += 1
                }

                // Doubled consonants collapse; the preceding vowel was already made short by the cluster rule.
                !isVowel(ch) && at(1) == ch -> {
                    out.append(ch)
                    i += 2
                }

                // ---- diphthongs ----
                startsWith("ei") || startsWith("ai") || startsWith("ey") || startsWith("ay") -> {
                    out.append("aɪ")
                    i += 2
                }

                startsWith("eu") || startsWith("äu") || startsWith("oi") || startsWith("oy") -> {
                    out.append("ɔʏ")
                    i += 2
                }

                startsWith("au") -> {
                    out.append("aʊ")
                    i += 2
                }

                startsWith("ie") -> {
                    out.append("iː")
                    i += 2
                }

                startsWith("aa") -> {
                    out.append("aː")
                    i += 2
                }

                startsWith("ee") -> {
                    out.append("eː")
                    i += 2
                }

                startsWith("oo") -> {
                    out.append("oː")
                    i += 2
                }

                // ---- unstressed syllables: -er before a consonant or at the end vocalises to /ɐ/
                //      (Dieter, Lauterbach, Böhmermann); -e / -en / -el / -em reduce to schwa ----
                startsWith("er") && hasVowelBefore() && !isVowel(at(2)) -> {
                    out.append("ɐ")
                    i += 2
                }

                ch == 'e' && hasVowelBefore() && (i + 1 >= n || (at(1)?.let { it in "nlm" } == true && !isVowel(at(2)))) -> {
                    out.append("ə")
                    i +=
                        1
                }

                ch == 'y' && i + 1 >= n -> {
                    out.append("i")
                    i += 1
                }

                // ---- single vowels: length from the following consonant structure; "h" lengthens ----
                isVowel(ch) -> {
                    val lengthenedByH = at(1) == 'h' && !isVowel(at(2))
                    val long = lengthenedByH || isLongVowel(ch)
                    out.append(
                        when (ch) {
                            'a' -> if (long) "aː" else "a"
                            'e' -> if (long) "eː" else "ɛ"
                            'i' -> if (long) "iː" else "ɪ"
                            'o' -> if (long) "oː" else "ɔ"
                            'u' -> if (long) "uː" else "ʊ"
                            'ä' -> if (long) "ɛː" else "ɛ"
                            'ö' -> if (long) "øː" else "œ"
                            'ü', 'y' -> if (long) "yː" else "ʏ"
                            else -> error("unreachable vowel $ch")
                        },
                    )
                    i += if (lengthenedByH) 2 else 1
                }

                // ---- everything else maps to itself (p t k b d g f m n l) ----
                else -> {
                    out.append(ch)
                    i += 1
                }
            }
        }
        return out.toString()
    }
}
