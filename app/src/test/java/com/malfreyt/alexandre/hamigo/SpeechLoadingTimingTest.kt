package com.malfreyt.alexandre.hamigo

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechLoadingTimingTest {
    @Test fun fastPreparationDoesNotAddAnyPlaybackDelay() {
        val timing=SpeechLoadingTiming()
        assertEquals(120L,timing.playbackAt(120))
    }
    @Test fun aVisiblePreparationSurvivesUntilItCanBePerceived() {
        val timing=SpeechLoadingTiming();timing.revealed(180)
        assertEquals(580L,timing.playbackAt(210))
        assertEquals(900L,timing.playbackAt(900))
    }
    @Test fun aLaterParagraphHasItsOwnPreparationWindow() {
        val initial=SpeechLoadingTiming();initial.revealed(200)
        val next=SpeechLoadingTiming()
        assertEquals(4000L,next.playbackAt(4000))
    }
}
