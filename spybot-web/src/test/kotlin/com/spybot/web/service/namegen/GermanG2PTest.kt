package com.spybot.web.service.namegen

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class GermanG2PTest {
    private fun ipa(word: String) = Phonemes.symbolsOf(GermanG2P.transcribe(word))

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        // the names the matcher acceptance tests depend on
        "Gysi, ɡyːziː",
        "Lesch, lɛʃ",
        "Lanz, lants",
        "Jauch, jaʊx",
        // rules exercised one at a time
        "Scholz, ʃɔlts",
        "Stefan, ʃteːfaːn",
        "Kohl, koːl",
        "Bohlen, boːlən",
        "Dieter, diːtɐ",
        "Merkel, mɛɐkəl",
        "Böhmermann, bøːmɐman",
        "Lauterbach, laʊtɐbax",
        "Wagenknecht, vaːɡənknɛçt",
        "Schweiger, ʃvaɪɡɐ",
        "Raab, ʁaːp",
        "Brandt, bʁant",
        "Klopp, klɔp",
        "Willy, vɪli",
        "Gottschalk, ɡɔtʃalk",
        "Heufer-Umlauf, hɔʏfɐʊmlaʊf",
        "Habeck, haːbɛk",
        "Christian, kʁɪstiːaːn",
        "Sahra, zaːʁaː",
        "Anke, aŋkə",
        "Frank, fʁaŋk",
    )
    fun `transcribes German names`(
        word: String,
        expected: String,
    ) {
        assertEquals(expected, ipa(word))
    }
}
