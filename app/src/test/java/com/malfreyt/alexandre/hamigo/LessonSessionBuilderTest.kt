package com.malfreyt.alexandre.hamigo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonSessionBuilderTest {
    @Test fun randomizedAnswerPositionsRetainEveryCorrectAnswerAndSignal() {
        val original = listOf(
            Question("single", "Une seule réponse", listOf("A", "B", "C"), 1, ""),
            Question("truth", "Une affirmation", listOf("Vrai", "Faux"), 0, "", kind = "truefalse"),
            Question("blank", "Une case ___", listOf("Alpha", "Bravo", "Charlie"), 2, "", kind = "cloze"),
            Question("multi", "Plusieurs réponses", listOf("A", "B", "C", "D"), 0, "", kind = "multiselect", bands = listOf("1", "3")),
            Question("wave", "Un tracé", listOf("A", "B", "C", "D"), 2, "", kind = "waveform", bands = listOf("am", "fm", "sine", "square")),
            Question("sequence", "Des étapes", listOf("D'abord", "Ensuite", "Enfin"), 0, "", kind = "order"),
            Question("pairs", "Des paires", emptyList(), 0, "", kind = "match", pairs = listOf(PairItem("A", "1"), PairItem("B", "2")))
        )
        val lesson = Lesson("test", "Repères", "", emptyList(), "", "Sujet témoin", original)
        for (seed in 0 until 30) {
            val session = LessonSessionBuilder.create(lesson, seed)
            assertEquals(original.map { it.id }.toSet(), session.map { it.id }.toSet())
            assertEquals(original.size, session.size)
            for (question in session) {
                val before = original.single { it.id == question.id }
                when (question.kind) {
                    "multiselect" -> assertEquals(selectedTexts(before), selectedTexts(question))
                    "waveform" -> assertEquals(before.bands[before.answer], question.bands[question.answer])
                    "order" -> assertEquals(before.choices, question.choices)
                    "match" -> assertEquals(before.pairs, question.pairs)
                    else -> assertEquals(before.choices[before.answer], question.choices[question.answer])
                }
            }
        }
        assertEquals(original, lesson.questions)
    }

    @Test fun aSeedReplaysTheSameLessonWhileNewSeedsRenewMorsePractice() {
        val alphabet = ('A'..'Z').map(Char::toString)
        val question = Question("alphabet", "Reconnais le caractère Morse", alphabet, 0, "", topic = "Morse")
        val lesson = Lesson("morse-test", "Alphabet morse", "", emptyList(), "", "Morse", listOf(question))
        val replay = LessonSessionBuilder.create(lesson, 42)
        assertEquals(replay, LessonSessionBuilder.create(lesson, 42))
        assertEquals(3, replay.size)
        val generated = (0 until 12).flatMap { LessonSessionBuilder.create(lesson, it) }.filter { it.id != question.id }
        assertTrue(generated.map { it.id }.distinct().size > 2)
        for (extra in generated) {
            assertTrue(extra.kind in setOf("morseListen", "morseEncode"))
            val restored = checkNotNull(ExtendedPracticeGenerator.resolve(extra.id))
            assertEquals(restored.bands, extra.bands)
            if (extra.kind == "morseListen") {
                assertTrue(extra.choices.all { it in alphabet })
                assertEquals(restored.choices[restored.answer], extra.choices[extra.answer])
            } else assertEquals(restored, extra)
        }
    }

    private fun selectedTexts(question: Question) = question.bands.map { question.choices[it.toInt()] }.toSet()
}
