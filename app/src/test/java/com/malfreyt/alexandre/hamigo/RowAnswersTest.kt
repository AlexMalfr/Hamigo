package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test

class RowAnswersTest {
    private val rows=listOf(AnswerRow("call","Lettre A",acceptedAnswers=listOf("Alfa","Alpha")),
        AnswerRow("method","Trouver U",listOf("U = RI","I = U/R","R = U/I"),0))
    @Test fun everyRowMustBeAnsweredAndCorrectBeforeTheQuestionIsPassed() {
        assertFalse(RowAnswers.complete(rows,mapOf("call" to "Alfa")))
        assertFalse(RowAnswers.correct(rows,mapOf("call" to "Alfa","method" to "I = U/R")))
        assertFalse(RowAnswers.correct(rows,mapOf("call" to "Bravo","method" to "U = RI")))
        assertTrue(RowAnswers.correct(rows,mapOf("call" to " alpha ","method" to "U = RI")))
        assertFalse(RowAnswers.correct(emptyList(),emptyMap()))
    }
    @Test fun dropdownGradingUsesTheChosenMeaningRatherThanItsShuffledPosition() {
        assertTrue(RowAnswers.correct(rows[1],"U = RI"))
        assertFalse(RowAnswers.correct(rows[1],"0"))
        assertEquals("U = RI",RowAnswers.solution(rows[1]))
    }
}
