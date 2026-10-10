package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class MorseLiveEnvelopeTest {
    @Test fun heldKeyStartsInTheFirstBufferAndReleaseFadesToSilence() {
        val signal = MorseLiveEnvelope()
        val buffer = ShortArray(480)
        signal.render(buffer); assertTrue(buffer.all { it == 0.toShort() })
        signal.key(true); signal.render(buffer)
        assertTrue(buffer.take(48).any { it != 0.toShort() }) // No 180 ms preamble.
        repeat(50) { signal.render(buffer); assertTrue(buffer.any { abs(it.toInt()) > 6000 }) }
        signal.key(false); signal.render(buffer)
        assertTrue(buffer.takeLast(100).all { it == 0.toShort() }); assertFalse(signal.active)
    }
    @Test fun presetDotsAndDashesKeepTheirDurationsAndQueuedSignalsHaveAGap() {
        val signal = MorseLiveEnvelope(16000)
        signal.signal('.'); signal.signal('-')
        val samples = ShortArray(16000 * 600 / 1000); signal.render(samples)
        fun audible(ms: Int) = abs(samples[ms * 16].toInt()) + abs(samples[ms * 16 + 1].toInt()) > 500
        assertTrue(audible(80)); assertFalse(audible(110)); assertFalse(audible(170))
        assertTrue(audible(200)); assertTrue(audible(440)); assertFalse(audible(470))
        assertFalse(signal.active)
    }
    @Test fun cancelClearsQueuedSignalsAndLeavesNoStuckTone() {
        val signal = MorseLiveEnvelope()
        signal.key(true); signal.signal('-'); signal.signal('.')
        signal.render(ShortArray(480)); signal.stop()
        val samples = ShortArray(48000); signal.render(samples)
        assertTrue(samples.drop(480).all { it == 0.toShort() }); assertFalse(signal.active)
        signal.signal('.'); signal.render(ShortArray(480)); assertTrue(signal.active)
    }
    @Test fun envelopeAvoidsClicksAndNeverExceedsItsAmplitude() {
        val signal = MorseLiveEnvelope()
        signal.key(true); val samples = ShortArray(480); signal.render(samples)
        assertEquals(0.toShort(), samples.first())
        assertTrue(samples.all { abs(it.toInt()) <= 7000 })
        assertTrue(samples.take(48).all { abs(it.toInt()) < 1200 })
        signal.key(false); signal.render(samples)
        assertEquals(0.toShort(), samples.last())
        signal.signal('x'); signal.render(samples); assertTrue(samples.all { it == 0.toShort() })
    }
}
