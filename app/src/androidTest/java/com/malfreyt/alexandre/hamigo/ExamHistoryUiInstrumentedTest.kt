package com.malfreyt.alexandre.hamigo

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ExamHistoryUiInstrumentedTest {
    @get:Rule val ui=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun bank()=listOf("regulation","technique").flatMap {part->(0 until 60).map {n->Question("$part-$n","Quel choix correspond à la notion étudiée ?",listOf("Le premier choix","Le deuxième choix","Le troisième choix","Le quatrième choix"),0,"La correction explique ici le raisonnement.",topic="Sujet ${n%6}",section=part)}}
    private fun capture(name:String) {
        val file=File(context.getExternalFilesDir(null),"parcours-progressif/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun emptyHistoryExplainsWhatIsKeptAndCustomExamNeedsThreeCompleteAttempts() {
        val content=Content(context,examSnapshot=null)
        val model=AppModel().apply {progress=Progress(DiagnosticContext(context),sideEffects=false);this.content=content}
        ui.setContent {HamigoTheme {ExamHistoryScreen(model,content)}}
        ui.onNodeWithText("Ton premier examen ouvre l’historique.").assertIsDisplayed()
        ui.onNodeWithTag("exam-history-list").performScrollToNode(hasTestTag("start-customized-exam"))
        ui.onNodeWithTag("start-customized-exam").assertIsNotEnabled()
        assertTrue(model.progress.examHistory.isEmpty())
        capture("exam-history-empty")
    }
    @Test fun threeExamsUnlockTheCustomizedSessionAndEveryTranscriptCanBeRead() {
        val questions=bank()
        val content=Content(context,ExamBankSnapshot("a".repeat(64),"diagnostic",questions,emptySet()))
        val memory=DiagnosticContext(context)
        val model=AppModel().apply {progress=Progress(memory,sideEffects=false);diagnostics=DiagnosticSettings(memory);this.content=content}
        val fixtures=ExamHistory.fixtures(questions)
        fixtures.forEach {model.progress.recordExam(it)}
        val before=model.progress.export()
        ui.setContent {HamigoTheme {ExamHistoryScreen(model,content)}}
        ui.onNodeWithTag("exam-score-chart").assertIsDisplayed()
        capture("exam-history-scores")
        val latest=fixtures.maxBy {it.at}
        ui.onNodeWithTag("exam-history-list").performScrollToNode(hasTestTag("exam-attempt-${latest.id}"))
        ui.onNodeWithTag("exam-attempt-${latest.id}").performClick()
        ui.onNodeWithTag("exam-response-0").assertIsDisplayed()
        capture("exam-history-transcript-top")
        ui.onNodeWithTag("exam-transcript-list").performScrollToNode(hasTestTag("exam-response-39"))
        ui.onNodeWithTag("exam-response-39").assertIsDisplayed()
        capture("exam-history-transcript-end")
        ui.onNodeWithContentDescription("Revenir à l’historique").performClick()
        ui.onNodeWithTag("exam-history-list").performScrollToNode(hasTestTag("start-customized-exam"))
        ui.onNodeWithTag("start-customized-exam").assertIsEnabled().performClick()
        ui.runOnIdle {
            val session=model.session!!
            assertTrue(session.exam);assertEquals("Examen personnalisé",session.title);assertEquals(40,session.questions.size)
            assertTrue(session.questions.take(20).all {it.section=="regulation"});assertTrue(session.questions.drop(20).all {it.section=="technique"})
            assertTrue(session.examIntroPending);assertNull(session.feedback)
            assertEquals(before,model.progress.export())
        }
    }
}
