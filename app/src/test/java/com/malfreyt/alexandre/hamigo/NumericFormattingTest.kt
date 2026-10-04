package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow

class NumericFormattingTest {
    @Test fun aFifteenHundredthsToleranceIsNotAdvertisedAsTwoTenths() {
        assertEquals("Précision acceptée : ± 0,15 Ω.", toleranceLabel(.15, "Ω"))
        assertEquals("Précision acceptée : ± 0,05 MHz.", toleranceLabel(.05, "MHz"))
        // Displaying an answer and declaring its allowed error have different rounding needs.
        assertEquals("12,4", formatMeasuredNumber(12.36, .15))
    }

    @Test fun aRelativeReactanceToleranceDoesNotPromiseRejectedAnswers() {
        val expected = 2 * PI * 750 * .012
        val tolerance = expected * .01
        val advertised = advertisedAmount(tolerance)
        assertTrue(advertised <= tolerance)
        assertTrue(LearningRules.numericCorrect((expected + advertised).toString(), expected, tolerance))
        assertFalse(LearningRules.numericCorrect((expected + tolerance * 1.1).toString(), expected, tolerance))
    }

    @Test fun displayedLimitsNeverExceedTheirThresholdAcrossMagnitudes() {
        for (exponent in -300..300 step 10) {
            for (mantissa in listOf(1.001, 1.9999, 2.718281828, 9.9999)) {
                val tolerance = mantissa * 10.0.pow(exponent)
                val advertised = advertisedAmount(tolerance)
                assertTrue("$advertised must not widen $tolerance", advertised <= tolerance)
                assertTrue("$tolerance should retain a meaningful positive limit", advertised > 0)
                assertTrue("$advertised should stay close to $tolerance", advertised >= tolerance * .99)
            }
        }
    }

    @Test fun extremeTolerancesStayReadableAndConservative() {
        for (tolerance in listOf(Double.MIN_VALUE, 1e-100, 1e100, Double.MAX_VALUE)) {
            val label = toleranceLabel(tolerance, "Ω")
            assertTrue("The scientific limit should fit in a short label", label.length < 60)
            assertTrue(advertisedAmount(tolerance) <= tolerance)
        }
    }

    @Test fun anExactAnswerKeepsAZeroToleranceLabel() {
        assertEquals("Précision acceptée : ± 0.", toleranceLabel(0.0, ""))
    }

    private fun advertisedAmount(tolerance: Double): Double = toleranceLabel(tolerance, "")
        .substringAfter("± ").removeSuffix(".").replace(',', '.').toDouble()
}
