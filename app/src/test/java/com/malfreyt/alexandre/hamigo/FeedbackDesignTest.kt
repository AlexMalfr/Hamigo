package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class FeedbackDesignTest {
    @Test fun smallActionsAreThrottledButVerdictsAndTheFirstPlayfulReactionAreNotLost() {
        val gate=FeedbackThrottle()
        assertTrue(gate.allow(FeedbackCue.CLICK,1000))
        assertFalse(gate.allow(FeedbackCue.SELECT,1040))
        assertTrue(gate.allow(FeedbackCue.SUCCESS,1040))
        assertTrue(gate.allow(FeedbackCue.ERROR,1050))
        assertTrue(gate.allow(FeedbackCue.SNAP,1100))
        assertTrue(gate.allow(FeedbackCue.PICO,1100))
        assertFalse(gate.allow(FeedbackCue.PICO,1300))
        assertTrue(gate.allow(FeedbackCue.PICO,1750))
        assertNull(FeedbackDesign.haptic(FeedbackCue.CLICK))
    }
    @Test fun hapticBurstsAreShortBoundedAndEndWithoutARepeatingBuzz() {
        FeedbackCue.entries.mapNotNull(FeedbackDesign::haptic).forEach {pattern->
            assertEquals(pattern.times.size,pattern.strengths.size)
            assertTrue(pattern.times.all {it>=0})
            assertTrue(pattern.times.sum() in 1..450)
            assertTrue(pattern.strengths.all {it in 0..180})
            assertTrue(pattern.strengths.last()>0)
            pattern.times.zip(pattern.strengths.toList()).filter {it.second>0}.forEach {assertTrue(it.first<=35)}
        }
    }
    @Test fun morsePrimesTheRouteWithQuietHissWithoutShorteningTheFirstDotOrTheGaps() {
        val pcm=MorseSignalAudio.render(".- . / -")
        val ranges=pcm.signalRanges
        val dot=pcm.rate*90/1000
        assertEquals(4,ranges.size)
        assertTrue(ranges.first().first>=pcm.rate*150/1000)
        assertEquals(dot,ranges[0].count());assertEquals(dot*3,ranges[1].count())
        assertEquals(dot,ranges[1].first-ranges[0].last-1)
        assertEquals(dot*3,ranges[2].first-ranges[1].last-1)
        assertEquals(dot*7,ranges[3].first-ranges[2].last-1)
        val lead=pcm.samples.take(ranges.first().first)
        assertTrue(lead.any {it!=0.toShort()})
        assertTrue(lead.maxOf {abs(it.toInt())}<170)
        assertTrue(ranges[0].maxOf {abs(pcm.samples[it].toInt())}>9000)
        assertEquals(0,pcm.samples.first().toInt());assertEquals(0,pcm.samples.last().toInt())
        assertTrue(MorseSignalAudio.render("").samples.isEmpty())
    }
}
