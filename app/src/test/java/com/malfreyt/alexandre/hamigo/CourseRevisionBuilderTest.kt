package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test

class CourseRevisionBuilderTest {
    private fun q(id:String)=Question(id,"Question $id",listOf("Oui","Non"),0,"Explication")
    private fun lesson(id:String,questions:List<Question>)=Lesson(id,id,"",emptyList(),"","",questions)
    @Test fun onlyStudiedQuestionsAndCompletedLessonBanksAreEligible() {
        val lessons=listOf(lesson("done",listOf(q("a"),q("b"))),lesson("partial",listOf(q("c"),q("d"))),lesson("new",listOf(q("e"))))
        val reviews=mapOf("c" to Review(),"exam-only" to Review(),"flash-only" to Review())
        assertEquals(setOf("a","b","c"),CourseRevisionBuilder.eligible(lessons,setOf("done"),reviews).map {it.id}.toSet())
        assertTrue(CourseRevisionBuilder.eligible(lessons,emptySet(),emptyMap()).isEmpty())
    }
    @Test fun alreadyReviewedLessonVariantsKeepTheirOriginalIdentity() {
        val l=lesson("bits",listOf(q("base"))).copy(title="Binaire",topic="Circuits numériques")
        val variant=ExtendedPracticeGenerator.lessonExtraPool(l).first()
        val selected=CourseRevisionBuilder.eligible(listOf(l),setOf(l.id),mapOf(variant.id to Review()))
        assertEquals(listOf("base",variant.id),selected.map {it.id})
        assertEquals(variant,selected.last())
    }
    @Test fun overdueItemsArePrioritizedButDistributedThroughTheSession() {
        val pool=(0..15).map {q("q$it")}
        val reviews=(0..9).associate {"q$it" to Review(due=it.toLong()+1)}
        for(seed in 0..99) {
            val selected=CourseRevisionBuilder.create(pool,reviews,10,now=100,seed=seed)
            assertEquals(10,selected.size);assertEquals(10,selected.map {it.id}.toSet().size)
            val due=selected.filter {it.id in reviews}
            assertEquals((0..6).map {"q$it"}.toSet(),due.map {it.id}.toSet())
            assertTrue("Rappels are not a prefix block",selected.take(due.size).any {it.id !in reviews})
            assertTrue("Rappels also occur late in the session",selected.drop(due.size).any {it.id in reviews})
        }
    }
    @Test fun emptySmallAndOnlyDueBanksNeverDuplicateOrInventQuestions() {
        assertTrue(CourseRevisionBuilder.create(emptyList(),emptyMap()).isEmpty())
        assertTrue(CourseRevisionBuilder.create(listOf(q("a")),emptyMap(),0).isEmpty())
        val selected=CourseRevisionBuilder.create(listOf(q("a"),q("a"),q("b")),mapOf("a" to Review(),"b" to Review()),40,now=100,seed=1)
        assertEquals(setOf("a","b"),selected.map {it.id}.toSet());assertEquals(2,selected.size)
    }
    @Test fun futureAndNotYetScheduledQuestionsAreAllowedWithoutTreatingThemAsDue() {
        val pool=(0..11).map {q("q$it")}
        val reviews=mapOf("q0" to Review(due=10),"q1" to Review(due=200))
        val selected=CourseRevisionBuilder.create(pool,reviews,10,now=100,seed=2)
        assertTrue(selected.any {it.id=="q0"})
        assertEquals(5,selected.indexOfFirst {it.id=="q0"})
    }
    @Test fun answerPositionsVaryWithoutChangingTheCorrectMeaningOrIdentity() {
        val question=q("stable")
        val positions=(0..20).map { seed ->
            val revised=CourseRevisionBuilder.create(listOf(question),emptyMap(),seed=seed).single()
            assertEquals(question.id,revised.id)
            assertEquals(question.choices[question.answer],revised.choices[revised.answer])
            revised.answer
        }.toSet()
        assertEquals(setOf(0,1),positions)
    }
}
