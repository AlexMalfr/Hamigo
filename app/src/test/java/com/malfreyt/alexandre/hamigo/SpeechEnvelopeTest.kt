package com.malfreyt.alexandre.hamigo

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class SpeechEnvelopeTest {
    @Test fun mouthIgnoresShortGapsAndEnergyOnlyGentlyModulatesTheRegularCycle() {
        val envelope=SpeechEnvelope.fromRms(FloatArray(100) {i->when {
            i<10||i>=70->0f
            i in 30..32->0f // A gap within a word, not a pause.
            i%2==0->.02f
            else->.2f
        }})
        assertEquals(0f,envelope.mouthAt(50),0f)
        for(ms in 100..730 step 10)assertTrue(envelope.mouthAt(ms) in .9f..1f)
        assertEquals(0f,envelope.mouthAt(740),0f)
        assertEquals(0f,envelope.mouthAt(2000),0f)
        val spoken=(200..690 step 10).map(envelope::mouthAt)
        assertTrue("Syllable volume must not repeatedly close and reopen the mouth",spoken.zipWithNext().all {(a,b)->kotlin.math.abs(a-b)<.015f})
    }
    @Test fun waveformPausesAreDetectedAtTenMillisecondResolutionIncludingStereoCancellation() {
        val pcm=ByteBuffer.allocate(16000*2*2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(16000) {i->val sample=if(i in 3200..6399 || i in 9600..12799)12000 else 0;pcm.putShort(sample.toShort());pcm.putShort((-sample).toShort())}
        val bytes=pcm.array();val wav=ByteBuffer.allocate(44+bytes.size).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36+bytes.size).put("WAVEfmt ".toByteArray()).putInt(16)
        wav.putShort(1).putShort(2).putInt(16000).putInt(64000).putShort(4).putShort(16)
        wav.put("data".toByteArray()).putInt(bytes.size).put(bytes)
        val file=File.createTempFile("speech-envelope-",".wav")
        try {
            file.writeBytes(wav.array());val envelope=SpeechEnvelope.fromWav(file)!!
            assertEquals(100,envelope.size)
            assertEquals(0f,envelope.at(100),0f);assertTrue(envelope.at(210)>.9f)
            assertEquals(0f,envelope.at(410),0f);assertTrue(envelope.at(610)>.9f)
            assertEquals(0f,envelope.at(810),0f);assertEquals(0f,envelope.at(1200),0f)
        } finally {file.delete()}
    }
    @Test fun quietVoicesRemainAnimatedButLowNoiseDoesNotHoldTheMouthOpen() {
        val envelope=SpeechEnvelope.fromRms(floatArrayOf(.0001f,.002f,.03f,.06f,.0002f))
        assertEquals(0f,envelope.at(0),0f);assertTrue(envelope.at(30)>.9f);assertEquals(0f,envelope.at(40),0f)
    }
    @Test fun a22050HzVoiceDoesNotAccumulateTimingDriftDuringLongSpeech() {
        val rate=22050;val frames=rate*10
        val wav=ByteBuffer.allocate(44+frames*2).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36+frames*2).put("WAVEfmt ".toByteArray()).putInt(16)
        wav.putShort(1).putShort(1).putInt(rate).putInt(rate*2).putShort(2).putShort(16)
        wav.put("data".toByteArray()).putInt(frames*2)
        repeat(frames) {wav.putShort(if(it>=frames-441)10000.toShort() else 0.toShort())}
        val file=File.createTempFile("speech-22050-",".wav")
        try {
            file.writeBytes(wav.array());val envelope=SpeechEnvelope.fromWav(file)!!
            assertEquals(0f,envelope.at(9950),0f)
            assertTrue("The final 20 ms must stay aligned to the actual audio clock",envelope.at(9990)>.9f)
            assertEquals(0f,envelope.at(10050),0f)
        }finally {file.delete()}
    }
    @Test fun invalidOrTruncatedAudioFailsSafely() {
        val file=File.createTempFile("bad-speech-",".wav")
        try {file.writeText("RIFF");assertNull(SpeechEnvelope.fromWav(file))}finally{file.delete()}
    }
}
