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
}
