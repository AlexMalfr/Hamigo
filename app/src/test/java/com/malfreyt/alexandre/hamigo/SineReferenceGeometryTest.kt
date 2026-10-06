package com.malfreyt.alexandre.hamigo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class SineReferenceGeometryTest {
    @Test fun crestMarkersCoincideWithPositiveWaveMaximaAndOnePeriodApart() {
        val g = SineReferenceGeometry
        for (x in listOf(g.firstPeakX, g.secondPeakX)) {
            assertEquals(g.crestY, g.ordinate(x), 0.000001f)
            assertTrue(g.ordinate(x - .001f) > g.ordinate(x))
            assertTrue(g.ordinate(x + .001f) > g.ordinate(x))
        }
        assertEquals(g.span / g.cycles, g.secondPeakX - g.firstPeakX, 0.000001f)
    }

    @Test fun rmsGuideMatchesTheNumericallyIntegratedSignal() {
        val g = SineReferenceGeometry
        val samples = 4096
        val meanSquare = (0 until samples).sumOf { i ->
            val value = (g.baseline - g.ordinate(g.left + g.span * i / samples)).toDouble()
            value * value
        } / samples
        assertEquals(sqrt(meanSquare), (g.baseline - g.effectiveY).toDouble(), 0.000001)
        assertEquals(g.baseline, g.ordinate(g.left), 0.000001f)
        assertEquals(g.baseline, g.ordinate(g.left + g.span), 0.000001f)
    }
}
