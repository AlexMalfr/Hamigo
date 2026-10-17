package com.malfreyt.alexandre.hamigo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeechPreparationInstrumentedTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    @Test fun coldNarrationShowsPreparationBeforePlaybackAndCanBeCancelledDuringStartup() {
        lateinit var voice:LessonSpeech
        instrumentation.runOnMainSync {
            voice=LessonSpeech(instrumentation.targetContext,eager=false)
            voice.toggleText("La radio transmet une information grâce à une onde électromagnétique. ".repeat(12))
            assertTrue(voice.reading);assertFalse(voice.preparing);assertFalse(voice.speaking)
            voice.stop();assertFalse(voice.reading);assertFalse(voice.preparing)
        }
        try {
            // A completed initialization after cancellation must not resurrect the pending speech.
            Thread.sleep(1200)
            instrumentation.runOnMainSync {assertFalse(voice.reading);assertFalse(voice.speaking)}
            instrumentation.runOnMainSync {voice.toggleText("Le courant électrique se mesure en ampères. ".repeat(30))}
            val limit=System.currentTimeMillis()+20_000
            var played=false
            while(System.currentTimeMillis()<limit&&!played) {
                instrumentation.runOnMainSync {played=voice.speaking;if(played){assertTrue(voice.reading);assertFalse(voice.preparing)}}
                if(!played)Thread.sleep(80)
            }
            assertTrue("The emulator must actually start its installed French voice.",played)
        } finally {instrumentation.runOnMainSync {voice.close();assertFalse(voice.reading);assertFalse(voice.speaking)}}
    }
    @Test fun theAlphabetFirstTapIsQueuedAndATapOnTheSameWordCancelsIt() {
        lateinit var voice:ReferenceAudio
        instrumentation.runOnMainSync {
            voice=ReferenceAudio(instrumentation.targetContext)
            voice.spell("November")
            assertEquals("November",voice.activeWord);assertFalse(voice.preparing);assertFalse(voice.speaking)
            voice.spell("November")
            assertNull(voice.activeWord);assertFalse(voice.preparing)
        }
        try {Thread.sleep(600);instrumentation.runOnMainSync {assertNull(voice.activeWord);assertFalse(voice.speaking)}}
        finally {instrumentation.runOnMainSync {voice.close()}}
    }
}
