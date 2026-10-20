package com.malfreyt.alexandre.hamigo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExamHistoryInstrumentedTest {
    private fun attempt(n:Int):ExamAttempt {
        val responses=(0..39).map {i->ExamResponse(i,"$i",if(i<20)"regulation" else "technique","Sujet ${i%4}","Question $i",listOf("A","B","C","D"),0,if(i%2==0)0 else 1,false,"Correction $i","https://exam1.r-e-f.org/questions-listing?questionId=$i")}
        return ExamAttempt(UUID.nameUUIDFromBytes("history-$n".toByteArray()).toString(),1_700_000_000_000L+n,1_700_000_000_000,1_800_000,10,10,responses)
    }
    private fun records(vararg attempts:ExamAttempt)=JSONObject().also {json->attempts.forEach {json.put(it.id,ExamHistory.write(it))}}

    @Test fun finalizedHistoryRoundTripsAndMergesCommutativelyWithoutDoubleCounting() {
        val a=attempt(1);val b=attempt(2)
        val left=records(a);val right=records(a,b)
        val before=left.toString()
        val merged=ExamHistory.mergeJson(left,right)
        assertEquals(ExamHistory.read(merged),ExamHistory.read(ExamHistory.mergeJson(right,left)))
        assertEquals(listOf(b,a),ExamHistory.read(merged));assertEquals(before,left.toString())
        val context=DiagnosticContext(InstrumentationRegistry.getInstrumentation().targetContext)
        val progress=Progress(context,sideEffects=false)
        val originalXp=progress.xp
        assertTrue(progress.recordExam(a));assertFalse(progress.recordExam(a))
        assertEquals(originalXp,progress.xp);assertEquals(listOf(a),progress.examHistory)
        val imported=Progress(DiagnosticContext(context),sideEffects=false)
        imported.import(progress.export());assertEquals(progress.examHistory,imported.examHistory)
    }

    @Test fun mergedHistoryKeepsTheNewestHundredAndResolvesSameIdDeterministically() {
        val left=records(*(0..99).map(::attempt).toTypedArray());val right=records(*(50..149).map(::attempt).toTypedArray())
        val merged=ExamHistory.mergeJson(left,right)
        assertEquals(100,merged.length());assertEquals(attempt(149).id,ExamHistory.read(merged).first().id)
        assertEquals(attempt(50).id,ExamHistory.read(merged).last().id)
        val a=attempt(1);val changed=a.copy(responses=a.responses.mapIndexed {i,r->if(i==0)r.copy(prompt="Autre texte conservé") else r})
        assertEquals(
            ExamHistory.read(ExamHistory.mergeJson(records(a),records(changed))),
            ExamHistory.read(ExamHistory.mergeJson(records(changed),records(a)))
        )
    }

    @Test fun importRejectsIncompleteTranscriptsInconsistentScoresUnsafeImagePathsAndWrongTypes() {
        val a=attempt(1);val valid=ExamHistory.write(a)
        val invalid=listOf(
            JSONObject(valid.toString()).put("regulationScore",19),
            JSONObject(valid.toString()).put("elapsedMillis",45*60_000+1),
            JSONObject(valid.toString()).put("at",1.5),
            JSONObject(valid.toString()).put("customized","false"),
            JSONObject(valid.toString()).put("responses",JSONArray().put(valid.getJSONArray("responses").get(0))),
            JSONObject(valid.toString()).apply {getJSONArray("responses").getJSONObject(0).put("selectedAnswer",12)},
            JSONObject(valid.toString()).apply {getJSONArray("responses").getJSONObject(0).put("imageReference","../../backup.json")},
            JSONObject(valid.toString()).put("unexpectedPersonalData","irrelevant")
        )
        invalid.forEach {bad->assertThrows(Exception::class.java){ExamHistory.validate(JSONObject().put(a.id,bad))}}
        assertThrows(Exception::class.java){ExamHistory.validate(JSONObject().put("not-an-id",valid))}
    }

    @Test fun archivingATranscriptPreservesTheLearningLedgerAndSurvivesAChangedQuestionBank() {
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        val progress=Progress(DiagnosticContext(target),sideEffects=false)
        progress.answer("0",correct=false)
        val before=JSONObject(progress.export()).getJSONObject("progress")
        val generation="a".repeat(64)
        val original=attempt(3).let {a->a.copy(bankGeneration=generation,responses=a.responses.mapIndexed {i,r->
            if(i==0)r.copy(imageReference="exam-bank/$generation/0.png") else r})}
        assertTrue(progress.recordExam(original))
        val after=JSONObject(progress.export()).getJSONObject("progress").apply {remove("examHistory")}
        assertEquals(com.malfreyt.alexandre.hamigo.platform.CloudProgress.canonical(before),com.malfreyt.alexandre.hamigo.platform.CloudProgress.canonical(after))
        val newQuestions=original.responses.map {r->Question(r.questionId,"Nouvel énoncé ${r.index}",r.choices.reversed(),3,"Nouvelle correction",topic=r.topic,section=r.section)}
        val updated=Content(target,ExamBankSnapshot("b".repeat(64),"updated-fixture",newQuestions,emptySet()))
        assertEquals("Nouvel énoncé 0",updated.allQuestions.getValue("0").prompt)
        val archived=progress.examHistory.single()
        assertEquals("Question 0",archived.responses.first().prompt)
        assertEquals(listOf("A","B","C","D"),archived.responses.first().choices)
        assertEquals(0,archived.responses.first().correctAnswer)
        assertEquals("Correction 0",archived.responses.first().explanation)
        assertEquals("exam-bank/$generation/0.png",archived.responses.first().imageReference)
        val exported=progress.export()
        repeat(4){progress.examHistory.single().responses.forEach {r->r.choices[r.correctAnswer]}}
        assertEquals(exported,progress.export())
    }

    @Test fun aBankUpdateDuringAnExamKeepsTheGenerationCapturedWhenItsQuestionsWereDrawn() {
        val target=InstrumentationRegistry.getInstrumentation().targetContext
        val ram=DiagnosticContext(target)
        val generationA="a".repeat(64);val generationB="b".repeat(64)
        val original=attempt(7)
        val questionsA=original.responses.map {r->Question(r.questionId,r.prompt,r.choices,r.correctAnswer,r.explanation,
            topic=r.topic,section=r.section,image="exam-bank/$generationA/${r.questionId}.png")}
        val contentA=Content(target,ExamBankSnapshot(generationA,"fixture-a",questionsA,emptySet()))
        val model=AppModel().apply {
            progress=Progress(ram,sideEffects=false)
            diagnostics=DiagnosticSettings(ram)
            content=contentA
        }
        try {
            model.startQuestions("Examen blanc",questionsA,exam=true)
            val session=model.session!!
            assertEquals(generationA,session.bankGeneration)
            val questionsB=questionsA.map {q->q.copy(prompt="Énoncé actualisé ${q.id}",image="exam-bank/$generationB/${q.id}.png")}
            model.content=contentA.withExam(ExamBankSnapshot(generationB,"fixture-b",questionsB,emptySet()))
            assertEquals(generationB,model.content!!.examSnapshot!!.generation)
            assertEquals(questionsA.map {it.prompt},session.questions.map {it.prompt})
            session.questions.forEachIndexed {i,q->session.responses[i]=SessionResponse(true,choiceIndex=q.answer)}
            session.regulationScore=20;session.techniqueScore=20
            session.finalizedExamParts+=listOf(0,1)
            session.index=40;session.examIntroPending=false;session.examReviewing=false
            assertTrue(model.progress.recordExam(ExamHistory.fromSession(session)))
            val archived=model.progress.examHistory.single()
            assertEquals(generationA,archived.bankGeneration)
            assertTrue(archived.responses.all {it.imageReference=="exam-bank/$generationA/${it.questionId}.png"})
            assertEquals(session.attemptId,archived.id)
            assertEquals(20,archived.regulationScore);assertEquals(20,archived.techniqueScore)
            model.startQuestions("Entraînement ordinaire",listOf(questionsB.first()))
            assertNull(model.session!!.bankGeneration)
        } finally {
            DiagnosticAccess.bind(target.getSharedPreferences("hamigo_diagnostics",android.content.Context.MODE_PRIVATE))
        }
    }
}
