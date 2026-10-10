package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test

class MorseVisualTest {
    @Test fun notationAcceptsStoredAndPastedSignalsWithWordDividers() {
        assertTrue(isMorseNotation("... --- ... / .-"))
        assertTrue(isMorseNotation("••• ——— ••• / •−"))
        assertEquals("... --- ... / .-", normalizedMorse("••• ——— ••• / •−"))
        assertEquals("... --- ... / .-", normalizedMorse("●●● ━━━ ●●● / ●━"))
        assertFalse(isMorseNotation("SOS"))
        assertFalse(isMorseNotation(""))
        assertFalse(isMorseNotation("12.5"))
    }

    @Test fun unicodeCodeDoesNotConsumeTheSentencesFullStop() {
        val parts = morseTextParts("I correspond à ••.")
        assertEquals(listOf(".."), parts.filter { it.code }.map { normalizedMorse(it.text) })
        assertEquals(".", parts.last().text)
        assertFalse(parts.last().code)
    }

    @Test fun labelledAsciiCodeDisambiguatesPunctuationWithoutChangingPureCode() {
        assertEquals(listOf(".-"), morseTextParts("A se compose d’un point puis d’un trait : .-.")
            .filter { it.code }.map { normalizedMorse(it.text) })
        assertEquals(listOf(".-."), morseTextParts("R : .-.").filter { it.code }.map { normalizedMorse(it.text) })
        assertEquals(listOf(".-."), morseTextParts(".-.").filter { it.code }.map { normalizedMorse(it.text) })
    }

    @Test fun singleSignalsAndWordDividersRemainGraphicalInsideProse() {
        assertEquals(listOf(".", "/", "-"), morseTextParts("E = . / T = -").filter { it.code }.map { normalizedMorse(it.text) })
        assertEquals(listOf(".", "-"), morseTextParts("E : ● · T : ━").filter { it.code }.map { normalizedMorse(it.text) })
        assertEquals(listOf("/"), morseTextParts("Mots séparés par /").filter { it.code }.map { it.text })
        assertTrue(morseTextParts("U = R × I. Une résistance vaut 4,7 Ω.").none { it.code })
    }

    @Test fun territorialMapTargetsCoverAllFrenchPrefixesIncludingGuyana() {
        listOf("F", "TK", "FG", "FM", "FR", "FH", "FK", "FO", "FW", "FP", "FS", "FJ", "FT", "FY").forEach {
            assertNotNull("Missing map target for " + it, callsignMapQuery(it))
        }
        assertEquals("Guyane française", callsignMapQuery("FY"))
        assertNull(callsignMapQuery("/P"))
    }
    @Test fun literalPunctuationAndAssignmentLabelsAreNotWordDividers() {
        assertFalse(isMorseNotation("/"))
        assertTrue(morseTextParts("Cette étape travaille ?, /, ., =, ,, @.").none {it.code})
        val table="? : ••━━•• · / : ━••━• · . : •━•━•━"
        assertEquals(listOf("?","/","."),MorseExamples.table(table).map {it.label})
        assertEquals(listOf("..--..","-..-.",".-.-.-"),morseTextParts(table).filter {it.code}.map {normalizedMorse(it.text)})
        assertTrue(MorseExamples.table("Une explication. E : •").isEmpty())
        assertTrue(MorseExamples.find("T = - I").isEmpty())
    }
}
