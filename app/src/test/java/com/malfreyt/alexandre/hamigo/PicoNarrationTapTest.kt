package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test

class PicoNarrationTapTest {
    @Test fun oneTapControlsAudioWhileOnlyABurstGetsTemporaryExpressions() {
        val taps=PicoNarrationTap();var toggles=0;var stops=0
        fun tap(time:Long)=taps.tap(time,{toggles++},{stops++})
        tap(1000);assertEquals(1,toggles);assertFalse(taps.playful)
        tap(2000);assertEquals(2,toggles);assertFalse(taps.playful)
        tap(2100);tap(2200);assertTrue(taps.playful);assertEquals(3,toggles);assertEquals(1,stops)
        tap(2300);assertTrue(taps.closedEyes)
        taps.reset();assertFalse(taps.playful);tap(6000);assertEquals(4,toggles)
        tap(6050);tap(6100);taps.audio {toggles++};assertFalse(taps.playful)
    }
}
