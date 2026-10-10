package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

class ReceiverSignalTest {
    @Test fun displayedFrequencyBoundariesAreAcceptedWithoutAcceptingTheNextDetent() {
        assertTrue(LearningRules.sliderCorrect(147.05f,147.0,.05,.05))
        assertTrue(LearningRules.sliderCorrect(146.95f,147.0,.05,.05))
        assertFalse(LearningRules.sliderCorrect(147.1f,147.0,.05,.05))
        assertFalse(LearningRules.sliderCorrect(146.9f,147.0,.05,.05))
    }
    @Test fun theSignalGetsClearerSymmetricallyAndDoesNotTreatToleranceAsAnOnOffVerdict() {
        assertEquals(1.0,ReceiverSignal.clarity(147.0,147.0),0.0)
        assertEquals(ReceiverSignal.clarity(146.95,147.0),ReceiverSignal.clarity(147.05,147.0),1e-10)
        assertTrue(ReceiverSignal.clarity(147.05,147.0)>.8)
        assertTrue(ReceiverSignal.clarity(147.1,147.0)<ReceiverSignal.clarity(147.05,147.0))
        assertTrue(ReceiverSignal.clarity(146.0,147.0)<.001)
    }
    @Test fun stereoPcmIsDownmixedResampledAndBoundedWithoutIncludingAFileHeader() {
        val bytes=ByteBuffer.allocate(160*4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(160){bytes.putShort(4000);bytes.putShort(12000)}
        val mono=ReceiverVoicePcm.decode(bytes.array(),32000,2,16)
        assertEquals(80,mono.size);assertTrue(mono.all {it==7000.toShort()})
        assertTrue(ReceiverVoicePcm.decode(byteArrayOf(128.toByte(),255.toByte(),0),16000,1,8).any {it!=0.toShort()})
    }
    @Test fun absentVoiceStillProducesQuietBoundedNoiseWithoutCrashing() {
        val samples=ShortArray(1600);ReceiverSignal().render(samples,143.0,147.0,null)
        assertTrue(samples.any {it!=0.toShort()});assertTrue(samples.all {kotlin.math.abs(it.toInt())<1800})
        val voice=ShortArray(16000){5000};val near=ShortArray(16000);ReceiverSignal().render(near,147.0,147.0,voice)
        assertTrue(near.takeLast(4000).average()>4800)
    }
    @Test fun longTtsPcmResamplesPastTheOldIntegerOverflowBoundary() {
        val bytes=ByteBuffer.allocate(24000*12*2).order(ByteOrder.LITTLE_ENDIAN)
        repeat(24000*12){bytes.putShort(5000)}
        val output=ReceiverVoicePcm.decode(bytes.array(),24000,1,16)
        assertEquals(16000*12,output.size)
        assertTrue(output.all {it==7000.toShort()})
    }
    @Test fun detuningChangesTheVoiceSpectrumRatherThanOnlyTurningDownItsVolume() {
        val voice=ShortArray(16000){i->(3500*sin(2*PI*400*i/16000)+2500*sin(2*PI*2400*i/16000)).toInt().toShort()}
        fun voiceOnly(frequency:Double):DoubleArray {
            val spoken=ShortArray(16000);val noise=ShortArray(16000)
            ReceiverSignal().render(spoken,frequency,147.0,voice)
            ReceiverSignal().render(noise,frequency,147.0,null)
            return DoubleArray(16000){spoken[it].toDouble()-noise[it]}
        }
        fun amplitude(signal:DoubleArray,hz:Int):Double {
            val real=(2000 until 14000).sumOf {signal[it]*cos(2*PI*hz*it/16000)}
            val imaginary=(2000 until 14000).sumOf {signal[it]*sin(2*PI*hz*it/16000)}
            return hypot(real,imaginary)
        }
        val near=voiceOnly(147.0);val far=voiceOnly(147.15)
        val nearRatio=amplitude(near,2400)/amplitude(near,400)
        val farRatio=amplitude(far,2400)/amplitude(far,400)
        assertTrue("Detuning must remove voice detail, not just scale it",farRatio<nearRatio*.8)
        assertTrue(far.any {abs(it)>100})
    }
    @Test fun messageLoopsWithAQuietGapWithoutStoppingTheReceiverNoise() {
        val samples=ShortArray(24000)
        ReceiverSignal().render(samples,147.0,147.0,ShortArray(8000){5000})
        assertTrue(samples.slice(9000..13000).all {abs(it.toInt())<100})
        assertTrue(samples.slice(15000..17000).average()>4800)
        assertTrue(samples.slice(9000..13000).any {it!=0.toShort()})
        assertTrue(samples.all {abs(it.toInt())<=10000})
    }
}
