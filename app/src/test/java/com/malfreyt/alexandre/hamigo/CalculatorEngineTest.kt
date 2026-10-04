package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class CalculatorEngineTest {
    @Test fun powerAndUnaryPrecedenceAreMathematical() {
        assertEquals(14.0, CalculatorEngine.evaluate("2+3×4"), 1e-10)
        assertEquals(-4.0, CalculatorEngine.evaluate("-2^2"), 1e-10)
        assertEquals(4.0, CalculatorEngine.evaluate("(-2)^2"), 1e-10)
        assertEquals(512.0, CalculatorEngine.evaluate("2^3^2"), 1e-10)
        assertEquals(.25, CalculatorEngine.evaluate("2^-2"), 1e-10)
    }
    @Test fun radioCalculationsAcceptDecimalsConstantsAndScientificNotation() {
        assertEquals(30.0, CalculatorEngine.evaluate("10*log(1000)"), 1e-10)
        assertEquals(1e-6, CalculatorEngine.evaluate("1EXP-6".replace("EXP", "e")), 1e-14)
        assertEquals(250.0, CalculatorEngine.evaluate("1000×25%"), 1e-10)
        assertEquals(3.0, CalculatorEngine.evaluate("1,5(1+1)"), 1e-10)
        assertEquals(2*PI, CalculatorEngine.evaluate("2π"), 1e-10)
        assertEquals(11.0, CalculatorEngine.evaluate("Ans+1", answer = 10.0), 1e-10)
        assertEquals(4.0, CalculatorEngine.evaluate("sqrt(16)"), 1e-10)
    }
    @Test fun trigonometryRespectsTheSelectedAngleUnit() {
        assertEquals(.5, CalculatorEngine.evaluate("sin(30)"), 1e-10)
        assertEquals(.5, CalculatorEngine.evaluate("sin(pi/6)", CalculatorAngleMode.RADIANS), 1e-10)
        assertEquals(30.0, CalculatorEngine.evaluate("asin(0.5)"), 1e-10)
        assertEquals(PI/6, CalculatorEngine.evaluate("asin(0.5)", CalculatorAngleMode.RADIANS), 1e-10)
    }
    @Test fun invalidOrUnsafeExpressionsAreRejected() {
        listOf("1/0", "sqrt(-1)", "ln(0)", "asin(2)", "tan(90)", "2+", "(1+2", "1.2.3", "unknown(4)", "2^10000").forEach { input ->
            assertThrows("$input should fail", IllegalArgumentException::class.java) { CalculatorEngine.evaluate(input) }
        }
        assertThrows(IllegalArgumentException::class.java) { CalculatorEngine.evaluate("(".repeat(100)+"1"+")".repeat(100)) }
    }
    @Test fun sliderFormattingAndSnappingDoNotExposeFloatingPointNoise() {
        assertEquals("12,3", formatMeasuredNumber(12.299999237, .1))
        assertEquals("144,05", formatMeasuredNumber(144.050003, .05))
        assertEquals("Précision acceptée : ± 0,05 MHz.", toleranceLabel(.05, "MHz"))
        assertEquals(.1f, snapSliderValue(.14f, 0f, 1f, .1f), 1e-6f)
        assertEquals(-.2f, snapSliderValue(-.21f, -1f, 1f, .1f), 1e-6f)
    }
}
