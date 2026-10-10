package com.malfreyt.alexandre.hamigo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FrenchSpeechCorpusTest {
    @Test fun everyIntroductionUsesTheSharedPreparationAndAllMorseCharactersHaveExactPlayableExamples() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val content=Content(context);val introductions=JSONArray()
        content.lessons.forEach {lesson->
            val segments=LessonNarration.segments(lesson,4000)
            assertTrue(segments.isNotEmpty());assertTrue(segments.none {it.contains('\uE000')})
            introductions.put(JSONObject().put("id",lesson.id).put("title",lesson.title).put("spoken",JSONArray(segments)))
        }
        val examples=content.lessons.flatMap {l->l.body.flatMap {MorseExamples.table(it)}}.filter {it.label.length==1}
        assertEquals(42,examples.size)
        examples.forEach {example->
            assertEquals(MorseReference.alphabet.getValue(example.label[0]),example.code)
            val speech=FrenchSpeech.prepare("${example.label} : ${example.code}")
            assertTrue(speech.contains("se lit"));assertFalse(speech.contains("pause entre mots"))
            assertEquals(example.code.length,MorseSignalAudio.render(example.code).signalRanges.size)
        }
        val punctuation=content.lessons.first {it.id=="c15-l10"}
        assertTrue(morseTextParts(punctuation.body.first()).none {it.code})
        assertFalse(LessonNarration.segments(punctuation,4000).joinToString(" ").contains("égale"))
        val prefixes=content.lessons.first {it.id=="c16-l01"}
        val unitParagraph=prefixes.body.first {it.contains("et F")}
        val unitSpoken=FrenchSpeech.prepare(unitParagraph)
        assertTrue(unitSpoken,unitSpoken.contains("ohms et farads"))
        val directory=File(context.getExternalFilesDir(null),"ui-0.47-intro").apply {mkdirs()}
        File(directory,"intro-speech-audit.json").writeText(JSONObject().put("lessons",content.lessons.size).put("playableCharacters",examples.size).put("introductions",introductions).toString(2))
    }
    @Test fun everyQuestionCanBePreparedAndLongSpokenTextRespectsTheEngineLimit() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val content=Content(context);val examples=JSONArray()
        var count=0
        content.allQuestions.values.forEach {q->
            (listOf(q.prompt,q.explanation)+q.choices+q.pairs.flatMap {listOf(it.left,it.right)}).forEach {text->
                val prepared=FrenchSpeech.prepare(text)
                assertFalse("Leaked placeholder in ${q.id}",prepared.contains('\uE000'))
                if(text.isNotBlank())assertTrue("Lost text in ${q.id}",prepared.isNotBlank())
                count++
            }
        }
        val predicates=listOf<(Question)->Boolean>(
            {it.prompt.contains('━')}, {it.prompt.contains("µF") || it.prompt.contains("MHz")},
            {it.prompt.contains(" = ")}, {it.prompt.contains("√") || it.prompt.contains("²")},
            {it.prompt.contains("QRM") || it.prompt.contains("λ") || it.prompt.contains("dB")})
        val representative=predicates.flatMap {predicate->content.allQuestions.values.filter(predicate).take(5)}.distinctBy {it.id}
        representative.forEach {q->examples.put(JSONObject().put("id",q.id).put("source",q.prompt).put("spoken",FrenchSpeech.prepare(q.prompt)))}
        val long=Lesson("speech-test","","",listOf(List(90){"P = U²/R ; un signal de 145,05 MHz ; Morse ━•"}.joinToString(". ")),"","",emptyList())
        val pieces=LessonNarration.segments(long,120)
        assertTrue(pieces.all {it.length<=120});assertTrue(pieces.joinToString(" ").contains("trait, point"))
        val directory=File(context.getExternalFilesDir(null),"ui-0.47").apply {mkdirs()}
        File(directory,"speech-audit.json").writeText(JSONObject().put("questions",content.allQuestions.size).put("texts",count).put("examples",examples).toString(2))
    }
}
