package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test

class WrittenAnswersTest {
    @Test fun speechSpellingsCanCombineButCannotChangeTheCallOrItsOrder() {
        val accepted=QuestionVariants.natoAnswers("F4AJWX")
        assertTrue(WrittenAnswers.correct("Foxtrot quatre Alpha Juliette Whisky X ray",accepted))
        assertTrue(WrittenAnswers.correct("Foxtrot four Alfa Juliet Whiskey Xray",accepted))
        assertTrue(WrittenAnswers.correct(QuestionVariants.spell("F4AJWX"),accepted))
        assertFalse(WrittenAnswers.correct("Foxtrot quatre Alpha Juliette Victor X ray",accepted))
        assertFalse(WrittenAnswers.correct("Foxtrot quatre Juliette Alpha Whisky X ray",accepted))
        assertFalse(WrittenAnswers.correct("Foxtrot cinq Alpha Juliette Whisky X ray",accepted))
    }
    @Test fun accentsAndPunctuationDoNotPenalizeRecall() {
        assertTrue(WrittenAnswers.correct("  L’INTENSITÉ ! ",listOf("l'intensité")))
        assertTrue(WrittenAnswers.correct("Foxtrot, quatre, Alfa, Bravo, Charlie",listOf("Foxtrot quatre Alfa Bravo Charlie")))
        assertFalse(WrittenAnswers.correct("Foxtrot quatre Alfa Charlie Bravo",listOf("Foxtrot quatre Alfa Bravo Charlie")))
        assertFalse(WrittenAnswers.correct("",listOf("")))
        assertFalse(WrittenAnswers.correct("tension",listOf("courant","intensité")))
    }
    @Test fun noAssociationOrOrderingStartsWithAnyElementInItsSolvedPlace() {
        for(size in 2..20)for(seed in 0..150) {
            val order=questionDerangement(size,seed)
            assertEquals((0 until size).toSet(),order.toSet())
            assertTrue(order.indices.none {order[it]==it})
        }
        assertEquals(listOf(0),questionDerangement(1,10))
    }
    @Test fun unitSymbolsRetainTheirScaleEvenWhenProseIgnoresCase() {
        assertTrue(WrittenAnswers.correct("1mW",listOf("1 mW")))
        assertTrue(WrittenAnswers.correct("1,0 mW",listOf("1 mW")))
        assertTrue(WrittenAnswers.correct("10 uF",listOf("10 µF")))
        assertFalse(WrittenAnswers.correct("1 MW",listOf("1 mW")))
        assertFalse(WrittenAnswers.correct("-1 mW",listOf("1 mW")))
        assertFalse(WrittenAnswers.correct("1 MHz",listOf("1 mHz")))
    }
    @Test fun subsequentPresentationsCanChangeWhileSeedReproducesABug() {
        assertEquals(questionDerangement(8,42),questionDerangement(8,42))
        assertTrue((1..30).map {questionDerangement(8,it)}.distinct().size>15)
    }
}
