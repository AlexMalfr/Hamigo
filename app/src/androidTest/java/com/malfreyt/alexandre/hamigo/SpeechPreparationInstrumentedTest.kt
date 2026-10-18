package com.malfreyt.alexandre.hamigo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpeechPreparationInstrumentedTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    @Test fun theRealFrenchAudioDrivesTheMouthAndSilencesKeepTheSpeakingState() {
        lateinit var voice:LessonSpeech
        val context=instrumentation.targetContext
        val directory=java.io.File(context.getExternalFilesDir(null),"speech-0.53").apply {mkdirs()}
        val measurements=org.json.JSONArray()
        instrumentation.runOnMainSync {
            voice=LessonSpeech(context,eager=false)
            voice.toggleText("Bonjour. Écoute cette phrase. Maintenant, une autre phrase. La tension se mesure en volts. Et le courant, en ampères. ".repeat(2))
        }
        var audible=false;var gapAfterAudio=false;var resumed=false;var copied=false
        try {
            val limit=System.currentTimeMillis()+35000
            while(System.currentTimeMillis()<limit&&!resumed) {
                instrumentation.runOnMainSync {
                    if(voice.speaking) {
                        val level=voice.mouthActivity
                        measurements.put(org.json.JSONObject().put("at",android.os.SystemClock.uptimeMillis()).put("activity",level))
                        if(level>.2f){if(gapAfterAudio)resumed=true;audible=true}
                        if(audible&&level==0f)gapAfterAudio=true
                        if(!copied) {
                            context.cacheDir.listFiles {_,name->name.startsWith("hamigo-speech-")&&name.endsWith(".wav")}.orEmpty().firstOrNull()?.let {file->
                                assertNotNull("The installed engine must yield an analyzable audio file",SpeechEnvelope.fromWav(file))
                                file.copyTo(java.io.File(directory,"french-pauses.wav"),overwrite=true);copied=true
                            }
                        }
                        assertTrue(voice.reading);assertFalse(voice.preparing)
                    }
                }
                Thread.sleep(16)
            }
            assertTrue("An actual syllable must animate the jaw",audible)
            assertTrue("A pause closes it without leaving the speaking expression",gapAfterAudio)
            assertTrue("The next syllable opens it again",resumed)
        } finally {
            java.io.File(directory,"mouth-activity.json").writeText(measurements.toString(2))
            instrumentation.runOnMainSync {voice.close();assertEquals(0f,voice.mouthActivity,0f)}
        }
    }
    @Test fun multipleAudioFilesKeepTheSpeechExpressionUntilTheEntireNarrationEnds() {
        lateinit var voice:PreparedSpeech
        val samples=org.json.JSONArray()
        instrumentation.runOnMainSync {
            voice=PreparedSpeech(instrumentation.targetContext,java.util.Locale.FRANCE,"Voix indisponible",eager=false)
            voice.start(listOf("Bonjour.","Voici une deuxième phrase.","Le courant se mesure en ampères.",
                "Puis vient une autre partie.","La tension se mesure en volts.","La lecture est maintenant terminée."))
        }
        var began=false;var ended=false;var firstStartedAt=0L
        try {
            val limit=System.currentTimeMillis()+45000
            while(System.currentTimeMillis()<limit&&!ended) {
                instrumentation.runOnMainSync {
                    samples.put(org.json.JSONObject().put("at",android.os.SystemClock.uptimeMillis()).put("active",voice.active).put("speaking",voice.speaking).put("mouth",voice.mouthActivity))
                    if(voice.speaking&&!began){began=true;firstStartedAt=System.currentTimeMillis()}
                    if(began&&voice.active) {
                        assertTrue("No resting expression between audio files",voice.speaking)
                        assertFalse("No new thinking face between already-started paragraphs",voice.preparing)
                    }
                    if(began&&!voice.active){ended=true;assertFalse(voice.speaking);assertEquals(0f,voice.mouthActivity,0f)}
                }
                Thread.sleep(8)
            }
            assertTrue("The real French voice must begin",began)
            assertTrue("All six synthesized paragraphs must finish",ended)
            assertTrue("This must cover actual playback, not only synthesis",System.currentTimeMillis()-firstStartedAt>2000)
        }finally {
            val dir=java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"speech-0.53").apply {mkdirs()}
            java.io.File(dir,"multi-file-lifetime.json").writeText(samples.toString(2))
            instrumentation.runOnMainSync {voice.close();assertFalse(voice.active);assertFalse(voice.speaking)}
        }
    }
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
