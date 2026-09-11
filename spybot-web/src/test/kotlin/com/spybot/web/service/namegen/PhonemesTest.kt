package com.spybot.web.service.namegen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PhonemesTest {
    private fun distance(
        a: String,
        b: String,
    ): Double = Phonemes.featureDistance(Phonemes.parse(a).single(), Phonemes.parse(b).single())

    @Test
    fun `parses multi-character symbols longest first`() {
        assertEquals(listOf("ɡ", "yː", "z", "i"), Phonemes.parse("ɡyːzi").map { it.symbol })
        assertEquals(listOf("tʃ", "oʊ", "k"), Phonemes.parse("tʃoʊk").map { it.symbol })
        assertEquals(listOf("f", "l", "æ", "ʃ"), Phonemes.parse("flæʃ").map { it.symbol })
    }

    @Test
    fun `ignores stress marks and accepts ascii g`() {
        assertEquals("ɡeɪts", Phonemes.symbolsOf(Phonemes.parse("ˈgeɪts")))
    }

    @Test
    fun `rejects unknown symbols loudly so bad seed data fails fast`() {
        assertThrows<IllegalArgumentException> { Phonemes.parse("kæ%i") }
    }

    @Test
    fun `German y-umlaut and English ee differ only in rounding`() {
        // This is the whole reason "Gysi" can become "Easy".
        val d = distance("yː", "iː")
        assertTrue(d < 0.2, "expected /yː/ ~ /iː/ to be close, got $d")
    }

    @Test
    fun `adjacent front vowels are close`() {
        assertTrue(distance("ɛ", "æ") < 0.10)
    }

    @Test
    fun `voicing alone is a small difference`() {
        assertEquals(0.20, distance("s", "z"), 0.001)
    }

    @Test
    fun `rhotics of both languages are one sound to a listener`() {
        assertTrue(distance("ʁ", "ɹ") <= 0.15)
        assertTrue(distance("ɐ", "ɚ") <= 0.15)
        assertTrue(distance("ɐ", "ɹ") <= 0.15)
    }

    @Test
    fun `vowel against consonant is maximally different`() {
        assertEquals(1.0, distance("a", "k"))
    }

    @Test
    fun `distant vowels are far apart`() {
        assertTrue(distance("i", "ɑ") > 0.6)
    }
}
