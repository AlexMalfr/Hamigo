package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test

class CalculatorEditingTest {
    @Test fun aKeyReplacesTheSelectionAndBackspaceDeletesBesideTheCursor() {
        assertEquals(CalculatorEdit("12+8×4", 4), CalculatorEditing.press(CalculatorEdit("12+35×4", 3, 5), "8"))
        assertEquals(CalculatorEdit("12+3×4", 4), CalculatorEditing.press(CalculatorEdit("12+35×4", 5), "⌫"))
        assertEquals(CalculatorEdit("12+×4", 3), CalculatorEditing.press(CalculatorEdit("12+35×4", 5, 3), "⌫"))
        assertEquals(CalculatorEdit("12+35×4", 0), CalculatorEditing.press(CalculatorEdit("12+35×4", 0), "⌫"))
    }

    @Test fun nestedFunctionsKeepTheirClosingParenthesesAndPutDigitsInside() {
        var input = CalculatorEdit("10×")
        listOf("log", "(", "1", "0", "0", ")", "+", "sin", "3", "0", ")", ")").forEach {
            input = CalculatorEditing.press(input, it)
        }
        assertEquals("10×log((100)+sin(30))", input.text)
        assertEquals(input.text.length, input.start)
        assertEquals(10 * kotlin.math.log10(100.5), CalculatorEngine.evaluate(input.text), 1e-10)
    }

    @Test fun middleOfAnEquationAndSelectedGroupsAreEditable() {
        val grouped = CalculatorEditing.press(CalculatorEdit("2+30×4", 2, 4), "sin")
        assertEquals(CalculatorEdit("2+sin(30)×4", 9), grouped)
        assertEquals(4.0, CalculatorEngine.evaluate(grouped.text), 1e-10)
        val inserted = CalculatorEditing.press(CalculatorEdit("2+×4", 2), "√")
        assertEquals(CalculatorEdit("2+sqrt()×4", 7), inserted)
        val value = CalculatorEditing.press(CalculatorEditing.press(inserted, "9"), ")")
        assertEquals("2+sqrt(9)×4", value.text)
        assertEquals(14.0, CalculatorEngine.evaluate(value.text), 1e-10)
    }

    @Test fun squareAndReciprocalApplyToTheLastAtomRatherThanTheWholeEquation() {
        assertEquals("2+(3)^2", CalculatorEditing.press(CalculatorEdit("2+3"), "x²").text)
        assertEquals("2+1/(sin(30))", CalculatorEditing.press(CalculatorEdit("2+sin(30)"), "1/x").text)
        val value = CalculatorEditing.press(CalculatorEdit("2+1e-6×4", 6), "x²")
        assertEquals(CalculatorEdit("2+(1e-6)^2×4", 10), value)
        assertEquals(2.0 + 4e-12, CalculatorEngine.evaluate(value.text), 1e-14)
    }

    @Test fun replacingTextNeverExceedsTheExpressionBound() {
        val full = CalculatorEdit("1".repeat(512))
        assertEquals(full, CalculatorEditing.press(full, "7"))
        assertEquals(512, CalculatorEditing.press(full.copy(start = 0, end = 1), "7").text.length)
    }
}
