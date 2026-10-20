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
    @Test fun dueMemoAndExamItemsJoinTheCourseBankWithoutDuplicatesOrUnstudiedCourses() {
        val lessons=listOf(lesson("done",listOf(q("base"))),lesson("new",listOf(q("unseen"))))
        val due=listOf(q("base"),q("flash-due"),q("exam-due"))
        val reviews=due.associate {it.id to Review(due=1)}
        val pool=CourseRevisionBuilder.eligible(lessons,setOf("done"),reviews,due)
        assertEquals(setOf("base","flash-due","exam-due"),pool.map {it.id}.toSet())
        assertEquals(3,pool.size)
        assertEquals(due.map {it.id}.toSet(),CourseRevisionBuilder.create(pool,reviews,now=100).map {it.id}.toSet())
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
    @Test fun dueSkillPrefersAnotherEligibleQuestionInsteadOfTheLastFailedUuid() {
        val failed=q("failed").copy(skillIds=listOf("ohm-law"))
        val alternate=q("alternate").copy(skillIds=listOf("ohm-law"))
        val skill=SkillLearning.record(null,failed.id,false,1,1L,"attempt")
        for(seed in 0..30) {
            val selected=CourseRevisionBuilder.create(listOf(failed,alternate),mapOf(failed.id to Review(due=2)),
                mapOf("ohm-law" to skill),{it.skillIds},count=1,now=1_000_000,seed=seed)
            assertEquals(alternate.id,selected.single().id)
        }
    }
    @Test fun knowingOneSkillNeverOpensQuestionsFromAnUntouchedLessonOrANewConcept() {
        val seen=q("seen").copy(skillIds=listOf("known"))
        val alternate=q("alternate").copy(skillIds=listOf("known"))
        val newConcept=q("new-concept").copy(skillIds=listOf("unknown"))
        val future=q("future").copy(skillIds=listOf("known"))
        val lessons=listOf(lesson("partial",listOf(seen,alternate,newConcept)),lesson("untouched",listOf(future)))
        val review=SkillLearning.record(null,seen.id,false,1,1,"seen")
        val eligible=CourseRevisionBuilder.eligible(lessons,emptySet(),mapOf(seen.id to Review()),
            mapOf("known" to review),{it.skillIds})
        assertEquals(setOf(seen.id,alternate.id),eligible.map {it.id}.toSet())
    }
    @Test fun aFutureSkillSuppressesItsOldUuidDueFlagUntilTheConceptIsActuallyDue() {
        val question=q("old-uuid").copy(skillIds=listOf("ohm-law"))
        val skill=SkillLearning.record(null,question.id,true,4,1_000,"first")
        assertTrue(CourseRevisionBuilder.due(listOf(question),mapOf(question.id to Review(due=1)),
            mapOf("ohm-law" to skill),{it.skillIds},now=2_000).isEmpty())
    }
    @Test fun hamigoToolsNeverReturnsAsADueOrRandomRevisionEvenWithOldUuidReviews() {
        val onboarding=q("welcome").copy(skillIds=listOf("hamigo-tools"))
        val radio=q("radio").copy(skillIds=listOf("ohm-law"))
        val lessons=listOf(lesson("welcome-course",listOf(onboarding)),lesson("radio-course",listOf(radio)))
        val old=mapOf(onboarding.id to Review(due=1),radio.id to Review(due=2))
        val completed=lessons.map {it.id}.toSet()
        assertEquals(listOf(radio.id),CourseRevisionBuilder.eligible(lessons,completed,old,listOf(onboarding)).map {it.id})
        val skills=mapOf("hamigo-tools" to SkillLearning.record(null,onboarding.id,false,1,1,"welcome"))
        val pool=CourseRevisionBuilder.eligible(lessons,completed,old,skills,{it.skillIds},listOf(onboarding))
        assertEquals(listOf(radio.id),pool.map {it.id})
        assertEquals(listOf(radio.id),CourseRevisionBuilder.due(listOf(onboarding,radio),old,skills,{it.skillIds},now=1_000_000).map {it.id})
        for(seed in 0..30) {
            assertEquals(listOf(radio.id),CourseRevisionBuilder.create(listOf(onboarding,radio),old,count=10,now=1_000_000,seed=seed).map {it.id})
            assertEquals(listOf(radio.id),CourseRevisionBuilder.create(listOf(onboarding,radio),emptyMap(),skills,{it.skillIds},count=10,now=0,seed=seed).map {it.id})
        }
    }
    @Test fun resolvedAndMixedHamigoSkillsCannotLeakThroughTheConceptFallback() {
        val onboarding=q("legacy-welcome")
        val mixed=q("mixed").copy(skillIds=listOf("ohm-law","hamigo-tools"))
        val radio=q("radio").copy(skillIds=listOf("ohm-law"))
        val pool=listOf(onboarding,mixed,radio)
        val mapping:(Question)->List<String> = {if(it.id==onboarding.id)listOf("hamigo-tools") else it.skillIds}
        val reviews=pool.associate {it.id to Review(due=1)}
        assertEquals(listOf(radio.id),CourseRevisionBuilder.due(pool,reviews,emptyMap(),mapping,now=100).map {it.id})
        assertEquals(listOf(radio.id),CourseRevisionBuilder.create(pool,reviews,emptyMap(),mapping,count=10,now=100).map {it.id})
    }
    @Test fun genericMorseSkillNeverIntroducesUntaughtCharactersOrDistractors() {
        val taught=q("listen-e").copy(kind="morseListen",choices=listOf("E","T"),bands=listOf("."),skillIds=listOf("morse-listening"))
        val unknown=q("listen-f").copy(kind="morseListen",choices=listOf("F","T"),bands=listOf("..-."),skillIds=taught.skillIds)
        val wrongChoice=taught.copy(id="unknown-distractor",choices=listOf("E","Z"))
        val lesson=lesson("early-morse",listOf(taught,unknown,wrongChoice)).copy(knownCharacters="ET",skillIds=taught.skillIds)
        val eligible=CourseRevisionBuilder.eligible(listOf(lesson),setOf(lesson.id),emptyMap(),emptyMap(),{it.skillIds})
        assertEquals(listOf(taught.id),eligible.map {it.id})
    }
    @Test fun aComprehensionSummaryIsProseButItsTransmittedSignalStillNeedsKnownCharacters() {
        val summary=q("summary").copy(kind="morseListen",choices=listOf("La station répond.","Elle change de fréquence."),
            bands=listOf("."),skillIds=listOf("morse-listening"),morseComprehension=true)
        val unknown=summary.copy(id="untaught-signal",bands=listOf("--.."))
        val course=lesson("comprehension",listOf(summary,unknown)).copy(knownCharacters="ET",skillIds=summary.skillIds)
        assertEquals(listOf(summary.id),CourseRevisionBuilder.eligible(listOf(course),setOf(course.id),emptyMap(),emptyMap(),{it.skillIds}).map {it.id})
    }

    @Test fun punctuationNamesAreEligibleOnlyAfterTheirUnderlyingSignsAreKnown() {
        val taught=q("listen-equal").copy(kind="morseListen",choices=listOf("Égal","Point final"),
            bands=listOf("-...-"),skillIds=listOf("morse-listening"))
        val unknownChoice=taught.copy(id="unknown-punctuation-choice",choices=listOf("Égal","Virgule"))
        val unknownSignal=taught.copy(id="unknown-punctuation-signal",bands=listOf("--..--"))
        val prose=taught.copy(id="not-a-punctuation-label",choices=listOf("Égal","Le signe égal"))
        val wordSignal=taught.copy(id="punctuation-does-not-teach-word",choices=listOf("Plus","Égal"),
            bands=listOf(".--. .-.. ..- ..."))
        val course=lesson("punctuation",listOf(taught,unknownChoice,unknownSignal,prose,wordSignal))
            .copy(knownCharacters="=.+",skillIds=taught.skillIds)
        val eligible=CourseRevisionBuilder.eligible(listOf(course),setOf(course.id),emptyMap(),emptyMap(),{it.skillIds})
        assertEquals(listOf(taught.id),eligible.map {it.id})
        assertEquals(taught.bands,eligible.single().bands)
    }

    @Test fun spellingThePunctuationLabelDoesNotTeachItsUnknownSign() {
        val question=q("unknown-equal").copy(kind="morseListen",choices=listOf("E","Égal"),
            bands=listOf("."),skillIds=listOf("morse-listening"))
        val course=lesson("literal-letters",listOf(question)).copy(knownCharacters="EÉGAL",skillIds=question.skillIds)
        assertTrue(CourseRevisionBuilder.eligible(listOf(course),setOf(course.id),emptyMap(),emptyMap(),{it.skillIds}).isEmpty())
    }
}
