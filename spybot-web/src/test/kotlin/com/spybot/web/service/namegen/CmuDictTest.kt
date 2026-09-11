package com.spybot.web.service.namegen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CmuDictTest {
    @Test
    fun `looks up requested words and maps ARPABET to IPA`() {
        val dict = CmuDict(setOf("harry", "Easy", "sean"))

        assertEquals("hɛɹi", Phonemes.symbolsOf(dict.lookup("Harry")!!))
        assertEquals("izi", Phonemes.symbolsOf(dict.lookup("easy")!!))
        assertEquals("ʃɔn", Phonemes.symbolsOf(dict.lookup("sean")!!))
    }

    @Test
    fun `keeps only the requested vocabulary in memory`() {
        val dict = CmuDict(setOf("harry", "potter"))

        assertEquals(2, dict.size)
        assertNull(dict.lookup("carry"))
    }

    @Test
    fun `unstressed AH becomes schwa, stressed AH becomes caret`() {
        assertEquals("ə", CmuDict.arpabetToIpa("AH0"))
        assertEquals("ʌ", CmuDict.arpabetToIpa("AH1"))
        assertEquals("ŋ", CmuDict.arpabetToIpa("NG"))
    }
}
