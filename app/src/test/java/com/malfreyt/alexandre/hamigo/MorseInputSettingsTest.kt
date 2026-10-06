package com.malfreyt.alexandre.hamigo

import org.junit.Assert.assertEquals
import org.junit.Test

class MorseInputSettingsTest {
    @Test fun pressAtTheThresholdBecomesADash() {
        val settings = MorseInputSettings(singleKey = true, thresholdMs = 300)
        assertEquals('.', settings.symbolFor(0))
        assertEquals('.', settings.symbolFor(299))
        assertEquals('-', settings.symbolFor(300))
        assertEquals('-', settings.symbolFor(1200))
    }

    @Test fun beginnerTimingIsConfigurableAndInvalidValuesAreBounded() {
        assertEquals('.', MorseInputSettings(true, 600).symbolFor(450))
        assertEquals('-', MorseInputSettings(true, 150).symbolFor(450))
        assertEquals('.', MorseInputSettings(true, -1).symbolFor(149))
        assertEquals('-', MorseInputSettings(true, 9000).symbolFor(600))
    }

    @Test fun previewRecognizesLetterAndWordPausesAtTheirBoundaries() {
        val settings=MorseInputSettings(true,300)
        val first=MorsePreviewState().record('.',0,120,settings)
        assertEquals("..",first.record('.',569,689,settings).code)
        assertEquals(". .",first.record('.',570,690,settings).code)
        assertEquals(". .",first.record('.',1169,1289,settings).code)
        assertEquals(". / .",first.record('.',1170,1290,settings).code)
    }

    @Test fun aLongHeldSignalDoesNotBecomeAPauseAndSilenceUpgradesWithoutDuplicateSeparators() {
        val settings=MorseInputSettings(true,300)
        val first=MorsePreviewState().record('.',0,120,settings)
        assertEquals(".-",first.record('-',220,5000,settings).code)
        val letter=first.pause(settings.letterPauseMs,settings)
        assertEquals(". ",letter.code)
        val word=letter.pause(settings.wordPauseMs,settings)
        assertEquals(". / ",word.code)
        assertEquals(word,word.pause(settings.wordPauseMs,settings))
        assertEquals(". / -",word.record('-',1170,1500,settings).code)
        assertEquals(MorsePreviewState(),MorsePreviewState().pause(9000,settings))
    }

    @Test fun previewPausesTrackTheConfiguredTempo() {
        assertEquals(225L,MorseInputSettings(true,150).letterPauseMs)
        assertEquals(525L,MorseInputSettings(true,150).wordPauseMs)
        assertEquals(900L,MorseInputSettings(true,600).letterPauseMs)
        assertEquals(2100L,MorseInputSettings(true,600).wordPauseMs)
    }
}
