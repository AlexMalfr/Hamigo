package com.malfreyt.alexandre.hamigo

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class IntroductionEditorialInstrumentedTest {
    @get:Rule val ui=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var owner:AppModel
    private lateinit var model:AppModel
    private lateinit var content:Content
    @Before fun prepare() {
        check(Build.HARDWARE in listOf("ranchu","goldfish")) {"Emulator fixtures only"}
        ui.prepareTestWindow()
        content=Content(context,examSnapshot=null)
        ui.runOnUiThread {
            val memory=DiagnosticContext(context)
            owner=AppModel().apply {progress=Progress(memory,sideEffects=false);diagnostics=DiagnosticSettings(memory);interactionFeedback=AppFeedback(memory)}
            model=AppModel().apply {initializeDiagnostics(context,owner,this@IntroductionEditorialInstrumentedTest.content)}
        }
        ui.setContent {HamigoTheme {FeedbackHost(owner.interactionFeedback) {
            CompositionLocalProvider(LocalContext provides model.diagnosticContext,LocalDiagnosticsCovered provides false) {HamigoAppContent(model,model)}
        }}}
    }
    @After fun close()=ui.runOnUiThread {model.disposeDiagnostics();owner.interactionFeedback.close()}
    private fun open(id:String) {ui.runOnIdle {model.lesson=null;model.startLesson(content.lessons.first {it.id==id})};ui.waitForIdle()}
    private fun capture(name:String,dialog:Boolean=false) {
        val file=File(context.getExternalFilesDir(null),"introduction-editorial/$name.png");file.parentFile!!.mkdirs()
        if(dialog) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500,5_000)
            ui.awaitForegroundFrames()
            val bitmap=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            try {file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}
        }else file.outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    @Test fun allActualIntroductionsHavePresentationAndKeepTheirOriginalPlainNarration() {
        assertEquals(173,content.lessons.size)
        content.lessons.forEach {lesson->
            assertTrue(lesson.id,lesson.presentation.isNotEmpty())
            assertTrue(lesson.id,lesson.presentation.keys.all {it in lesson.body.indices})
            assertEquals(lesson.id,lesson.body.size+2,lesson.narration.size)
            assertFalse(lesson.id,lesson.narration.any {it.contains("**")||it.contains("<b>")})
        }
        open(HamigoWelcomeLesson)
        ui.onNodeWithTag("lesson-introduction-list").performScrollToNode(hasTestTag("lesson-tip-$HamigoWelcomeLesson"))
        ui.onNodeWithText("Astuce : touche Pico pour écouter cette fiche ; touche-le à nouveau pour arrêter la lecture.").assertIsDisplayed()
        capture("welcome-tip")
        for(index in listOf(1,2,3,4,5)) {
            ui.onNodeWithTag("lesson-introduction-list").performScrollToNode(hasTestTag("lesson-paragraph-$index"))
            capture("welcome-$index")
        }
        ui.onNodeWithTag("app-term-report",useUnmergedTree=true).assertIsDisplayed()
        ui.onNodeWithTag("learning-tool-calculator").assertDoesNotExist()
    }
    @Test fun aCourseReferenceOpensInPlaceAndClosingItPreservesScrollSessionAndProgress() {
        open("l-morse-vers-epellation")
        val paragraph=content.lessons.first {it.id=="l-morse-vers-epellation"}.body.first()
        val list=ui.onNodeWithTag("lesson-introduction-list")
        list.performScrollToNode(hasText(paragraph))
        val node=ui.onNodeWithText(paragraph,useUnmergedTree=true)
        val beforeBounds=node.fetchSemanticsNode().boundsInRoot
        val before=model.progress.export();val opening=model.lessonOpening;val origin=model.lesson
        val layouts=mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
        val layout=layouts.single();val phrase="l’alphabet international";val at=layout.layoutInput.text.text.indexOf(phrase)
        assertTrue(at>=0)
        val glyph=layout.getBoundingBox(at+5)
        node.performTouchInput {click(Offset(glyph.center.x,glyph.center.y))}
        ui.onNodeWithTag("lesson-reference-dialog").assertIsDisplayed()
        ui.onNodeWithText("L’alphabet international").assertIsDisplayed()
        capture("reference-dialog",dialog=true)
        ui.onNodeWithTag("lesson-reference-content").performTouchInput {swipeUp()}
        ui.onNodeWithTag("lesson-reference-close").assertIsDisplayed().performClick()
        ui.onNodeWithTag("lesson-reference-dialog").assertDoesNotExist()
        assertEquals(beforeBounds.top,node.fetchSemanticsNode().boundsInRoot.top,1f)
        ui.runOnIdle {assertEquals(origin,model.lesson);assertEquals(opening,model.lessonOpening);assertNull(model.session);assertEquals(before,model.progress.export())}
        capture("reference-return")
    }
    @Test fun representativeIntrosKeepMorseTablesLogicDrawingsAndAllContentScrollable() {
        for(id in listOf("c03-l03","c15-l01","c22-l03","l-examen-inscription","l-sstv-reception")) {
            open(id)
            ui.onNodeWithTag("lesson-introduction-list").performScrollToNode(hasTestTag("lesson-paragraph-0"))
            capture("$id-start")
            val lesson=model.lesson!!
            if(id=="c15-l01") {
                ui.onNodeWithTag("lesson-introduction-list").performScrollToNode(hasTestTag("lesson-morse-row-M"))
                ui.onNodeWithTag("lesson-morse-listen-M").performScrollTo().assertIsDisplayed()
                capture("$id-morse")
            }
            ui.onNodeWithTag("lesson-introduction-list").performScrollToNode(hasTestTag("lesson-paragraph-${lesson.body.lastIndex}"))
            capture("$id-end")
            ui.onNodeWithText("À toi de jouer").assertIsDisplayed()
        }
    }
    @Test fun actualReportChoiceHasItsFlagWithoutChangingTheAnswerOrPromptTouchAction() {
        val q=content.lessons.first {it.id==HamigoWelcomeLesson}.questions.last()
        ui.runOnIdle {model.startQuestions("Bienvenue",listOf(q))}
        ui.onNodeWithText(q.choices[q.answer],useUnmergedTree=true).performScrollTo()
        val tools=ui.onNodeWithTag("question-tools").fetchSemanticsNode().boundsInRoot
        val choice=ui.onNodeWithText(q.choices[q.answer],useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val lift=(choice.bottom-tools.top+12f).coerceAtLeast(0f)
        if(lift>0)ui.onNodeWithTag("question-answers").performSemanticsAction(SemanticsActions.ScrollBy){it(0f,lift)}
        capture("welcome-report-question-revealed")
        ui.onNodeWithTag("app-term-report",useUnmergedTree=true).assertIsDisplayed()
        ui.onNodeWithText(q.choices[q.answer],useUnmergedTree=true).assertExists()
        val read=ui.onNodeWithTag("question-narration-actions",useUnmergedTree=true).fetchSemanticsNode().config[SemanticsActions.CustomActions].single()
        assertEquals("Lire la question et les choix",read.label)
        assertNull(model.session!!.feedback)
        capture("welcome-report-question")
    }
}
