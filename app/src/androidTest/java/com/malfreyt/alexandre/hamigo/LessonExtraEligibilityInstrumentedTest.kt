package com.malfreyt.alexandre.hamigo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the shipped lessons: keywords in a question must never introduce a later calculation. */
@RunWith(AndroidJUnit4::class)
class LessonExtraEligibilityInstrumentedTest {
    private val content by lazy {Content(InstrumentationRegistry.getInstrumentation().targetContext,examSnapshot=null)}
    private fun lesson(id:String)=content.lessons.single {it.id==id}
    private fun extras(lesson:Lesson,seed:Int):List<Question> {
        val session=LessonSessionBuilder.create(lesson,seed)
        assertEquals(session.size,session.map {it.id}.distinct().size)
        assertTrue(session.map {it.id}.containsAll(lesson.questions.map {it.id}))
        return session.filter {candidate->lesson.questions.none {it.id==candidate.id}}
    }

    @Test fun namingTheOhmInTheUnitsLessonNeverAddsAnOhmsLawCalculation() {
        val units=lesson("c03-l01")
        assertTrue("The old keyword candidate really exists",ExtendedPracticeGenerator.lessonExtraPool(units)
            .any {it.id.substringAfterLast('-') in setOf("2","5")})
        for(seed in 0 until 64) {
            assertTrue(extras(units,seed).isEmpty())
            // Courses remain optional: merely listing a prerequisite cannot prove it was studied.
            assertTrue(extras(units.copy(prerequisiteSkillIds=listOf("ohm-law")),seed).isEmpty())
        }
    }

    @Test fun theSeriesLessonAddsFreshSeriesValuesWithoutTestingParallelBeforeItIsTaught() {
        val series=lesson("c04-l01")
        assertTrue(ExtendedPracticeGenerator.lessonExtraPool(series).any {it.id.endsWith("-1")})
        val generated=(0 until 64).flatMap {seed->extras(series,seed)}
        assertTrue("Series practice must remain available",generated.map {it.id}.distinct().size>10)
        generated.forEach {question->
            assertTrue("Unexpected parallel calculation: ${question.id}",question.id.endsWith("-0"))
            assertEquals("number",question.kind)
            assertEquals(ExtendedPracticeGenerator.resolve(question.id),question)
        }
    }

    @Test fun capacitorAndInductorIntroductionsNeverReceiveLaterXlOrXcCalculations() {
        for(id in listOf("c05-l02","c05-l03")) {
            val introduction=lesson(id)
            assertTrue(ExtendedPracticeGenerator.lessonExtraPool(introduction).any {it.id.endsWith("-14")||it.id.endsWith("-15")})
            for(seed in 0 until 64)assertTrue("Premature reactance in $id, seed $seed",extras(introduction,seed).isEmpty())
        }
    }

    @Test fun realMorseLessonsRenewPracticeWithinTheirTaughtCharactersAndKeepPublishedVariantMeanings() {
        for(id in listOf("c15-l01","c15-l02","c15-l07","c15-l08")) {
            val morse=lesson(id)
            val generated=(0 until 64).flatMap {seed->extras(morse,seed)}
            assertTrue("No renewed Morse practice for $id",generated.map {it.id}.distinct().size>2)
            generated.forEach {question->
                val original=checkNotNull(ExtendedPracticeGenerator.resolve(question.id))
                assertEquals(original.bands,question.bands)
                val decoded=MorseReference.decode(normalizedMorse(question.bands.first()))
                assertTrue(decoded.unsupported.isEmpty())
                assertTrue("Untaught Morse target in $id",decoded.output.all {it.isWhitespace()||it in morse.knownCharacters})
                if(question.kind=="morseListen") {
                    assertTrue("Untaught distractor in $id",question.choices.all {choice->choice.all {it.isWhitespace()||it in morse.knownCharacters}})
                    assertEquals(original.choices[original.answer],question.choices[question.answer])
                } else assertEquals(original,question)
            }
        }
        val onlyE=lesson("c15-l01").copy(knownCharacters="E")
        val constrained=(0 until 64).flatMap {seed->extras(onlyE,seed)}
        assertTrue(constrained.isNotEmpty())
        assertTrue(constrained.all {MorseReference.decode(normalizedMorse(it.bands.first())).output=="E"})
    }
}
