package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test

class LessonNarrationTest {
    @Test fun narrationIncludesTheCourseInOrderWithoutItsQuestionAnswers() {
        val l=Lesson("course","Titre","Résumé",listOf("Un paragraphe","Un autre"),"U = R × I","",listOf(Question("q","Question",listOf("Solution"),0,"Correction")))
        assertEquals(listOf("Titre","Résumé","Un paragraphe","Un autre","U = R × I"),LessonNarration.segments(l,4000))
    }
    @Test fun longParagraphsStayWithinTheEngineLimitWithoutLosingWordsOrSplittingEmoji() {
        val text=(1..100).joinToString(" ") {"mot$it 📻"}
        val l=Lesson("course","","",listOf(text),"","",emptyList())
        val pieces=LessonNarration.segments(l,27)
        assertTrue(pieces.size>1);assertTrue(pieces.all {it.length<=27})
        assertEquals(text,pieces.joinToString(" "))
        assertTrue(pieces.none {Character.isHighSurrogate(it.last()) || Character.isLowSurrogate(it.first())})
    }
}
