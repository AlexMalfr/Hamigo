package com.malfreyt.alexandre.hamigo

import android.speech.SpeechRecognizer
import org.junit.Assert.*
import org.junit.Test

class SpeechCaptureTest {
    @Test fun releasingBeforeReadyInvalidatesAllLateCallbacks() {
        val cycle=SpeechCaptureCycle()
        val first=cycle.begin()
        assertEquals(SpeechCapturePhase.PREPARING,cycle.phase)
        cycle.cancel()
        assertFalse(cycle.ready(first));assertFalse(cycle.complete(first));assertFalse(cycle.active)
        val second=cycle.begin()
        assertFalse(cycle.ready(first));assertTrue(cycle.ready(second))
        assertFalse(cycle.complete(first));assertEquals(SpeechCapturePhase.LISTENING,cycle.phase)
        assertTrue(cycle.processing(second));assertTrue(cycle.complete(second));assertFalse(cycle.active)
    }
    @Test fun aReplacedServiceCannotFinishTheNewServiceOrRestartAfterStop() {
        val cycle=SpeechCaptureCycle()
        val system=cycle.begin();val fallback=cycle.begin()
        assertFalse(cycle.ready(system));assertFalse(cycle.complete(system))
        assertTrue(cycle.ready(fallback));assertTrue(cycle.processing(fallback))
        assertFalse(cycle.ready(fallback));assertTrue(cycle.complete(fallback))
        assertFalse(cycle.ready(fallback));assertFalse(cycle.complete(fallback))
    }
    @Test fun theFinalSyllableTailCannotBeExtendedOrCompleteAnotherCapture() {
        val cycle=SpeechCaptureCycle();val request=cycle.begin()
        assertFalse(cycle.ending(request));assertTrue(cycle.ready(request))
        assertTrue(cycle.ending(request));assertEquals(SpeechCapturePhase.ENDING,cycle.phase)
        assertFalse(cycle.ending(request));assertTrue(cycle.active)
        cycle.cancel()
        val next=cycle.begin();assertTrue(cycle.ready(next))
        assertFalse(cycle.processing(request));assertFalse(cycle.complete(request))
        assertEquals(SpeechCapturePhase.LISTENING,cycle.phase)
        assertTrue(cycle.ending(next));assertTrue(cycle.processing(next));assertTrue(cycle.complete(next))
    }
    @Test fun fallbackIsOnlyPermittedBeforeRecordingAndOnlyOnce() {
        assertTrue(SpeechEnginePolicy.mayRetryOnDevice(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,SpeechCapturePhase.PREPARING,true,false))
        assertFalse(SpeechEnginePolicy.mayRetryOnDevice(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,SpeechCapturePhase.LISTENING,true,false))
        assertFalse(SpeechEnginePolicy.mayRetryOnDevice(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,SpeechCapturePhase.PREPARING,true,true))
        assertFalse(SpeechEnginePolicy.mayRetryOnDevice(SpeechRecognizer.ERROR_NO_MATCH,SpeechCapturePhase.PREPARING,true,false))
    }
    @Test fun natoSpellingIsCanonicalWithoutRepairingAWrongAnswer() {
        assertEquals("Alfa Juliett Juliett Whiskey X-ray Foxtrot",NatoSpeechCanonicalizer.canonicalize("alpha, juliette juliett whisky x ray fox trot"))
        assertEquals("Bravo Bravo cinq inconnu",NatoSpeechCanonicalizer.canonicalize("bravo bravo cinq inconnu"))
        val q=Question("nato","Épelle ce groupe en alphabet international.",emptyList(),0,"",kind="spoken",skillIds=listOf("nato-recall"),acceptedAnswers=listOf("Alfa"))
        assertTrue(NatoSpeechCanonicalizer.applies(q))
        assertFalse(WrittenAnswers.correct(NatoSpeechCanonicalizer.canonicalize("bravo"),q.acceptedAnswers))
        assertFalse(NatoSpeechCanonicalizer.applies(q.copy(prompt="Quel mode ?",skillIds=emptyList())))
        assertFalse(NatoSpeechCanonicalizer.applies(q.copy(kind="text")))
    }
    @Test fun observedFrenchTranscriptionsUseAliasesWithoutLookingAtTheExpectedAnswer() {
        assertEquals("X-ray Echo Juliett Zulu",NatoSpeechCanonicalizer.canonicalize("Israël Eco Joliette sous l eau"))
        assertEquals("Zulu Zulu X-ray inconnu",NatoSpeechCanonicalizer.canonicalize("sous l’eau sous leau israel inconnu"))
        val wrong=NatoSpeechCanonicalizer.canonicalize("Israël")
        assertFalse(WrittenAnswers.correct(wrong,listOf("Alfa")))
        assertTrue(WrittenAnswers.correct(wrong,listOf("X-ray")))
    }
}
