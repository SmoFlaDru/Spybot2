package com.spybot.web.service.namegen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhoneticMatcherTest {
    private fun score(
        name: String,
        slang: String,
    ): Double = PhoneticMatcher.score(Phonemes.parse(name), Phonemes.parse(slang)).score

    // The three examples the feature was specified with. They are NOT hardcoded anywhere in
    // main/ - if any of these drop below the threshold the engine has regressed.

    @Test
    fun `Harry becomes Carry - an onset swap with an identical rime`() {
        assertTrue(score("hɛɹi", "kæɹi") >= PhoneticMatcher.THRESHOLD)
    }

    @Test
    fun `Gysi becomes Easy - spelled nothing alike, sounds almost the same`() {
        assertTrue(score("ɡyːziː", "izi") >= PhoneticMatcher.THRESHOLD)
    }

    @Test
    fun `Lesch becomes Flash - an onset insertion with a near-identical rime`() {
        assertTrue(score("lɛʃ", "flæʃ") >= PhoneticMatcher.THRESHOLD)
    }

    @Test
    fun `finds puns beyond the specified examples`() {
        assertTrue(score("ʃɔn", "spɔn") >= PhoneticMatcher.THRESHOLD, "Sean × spawn")
        assertTrue(score("luk", "nuk") >= PhoneticMatcher.THRESHOLD, "Luke × nuke")
        assertTrue(score("hɛnɹi", "ɛntɹi") >= PhoneticMatcher.THRESHOLD, "Henry × entry")
        assertTrue(score("veɪdɚ", "beɪtɚ") >= PhoneticMatcher.THRESHOLD, "Vader × baiter")
        assertTrue(score("ɡeɪts", "beɪts") >= PhoneticMatcher.THRESHOLD, "Gates × baits")
    }

    @Test
    fun `unrelated words score well below the threshold`() {
        assertTrue(score("mɛɐkəl", "flæʃ") < 0.6, "Merkel × flash")
        assertTrue(score("hæŋks", "difjuz") < 0.6, "Hanks × defuse")
        assertTrue(score("oʊbɑmə", "pik") < 0.6, "Obama × peek")
    }

    @Test
    fun `identical pronunciation is not a pun`() {
        assertEquals(0.0, score("kɪŋ", "kɪŋ"))
    }

    @Test
    fun `rime starts at the second-to-last vowel`() {
        assertEquals("ɛɹi", Phonemes.symbolsOf(PhoneticMatcher.rime(Phonemes.parse("hɛɹi"))))
        assertEquals("ɛʃ", Phonemes.symbolsOf(PhoneticMatcher.rime(Phonemes.parse("lɛʃ"))))
        assertEquals("ɪŋ", Phonemes.symbolsOf(PhoneticMatcher.rime(Phonemes.parse("pɪŋ"))))
    }

    @Test
    fun `rhyme matters more than the onset`() {
        // Same rime, different onset should beat same onset, different rime.
        assertTrue(score("hɛɹi", "kæɹi") > score("hɛɹi", "hɛdʃɑt"))
    }
}
