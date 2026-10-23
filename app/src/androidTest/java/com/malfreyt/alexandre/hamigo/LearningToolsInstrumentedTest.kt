package com.malfreyt.alexandre.hamigo

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.view.inspector.WindowInspector
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextRange
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual lesson/Mémo tool hosts, with RAM-only models and the real keyboard/dialog windows. */
@RunWith(AndroidJUnit4::class)
class LearningToolsInstrumentedTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private lateinit var content:Content
    private lateinit var owner:AppModel
    private lateinit var model:AppModel
    private var horizontal=false
    private var retainedByActivity=false

    @Before fun prepare() {
        ui.prepareTestWindow()
        ui.runOnUiThread {ui.activity.window.decorView.windowInsetsController?.hide(WindowInsets.Type.ime())}
        ui.waitUntil(30_000){WindowInspector.getGlobalWindowViews().all {(it.rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0)==0}}
        content=Content(context,examSnapshot=null)
        ui.runOnUiThread {
            val memory=DiagnosticContext(context)
            owner=AppModel().apply {
                progress=Progress(memory,sideEffects=false)
                diagnostics=DiagnosticSettings(memory)
                interactionFeedback=AppFeedback(memory)
            }
            model=AppModel().apply {initializeDiagnostics(context,owner,this@LearningToolsInstrumentedTest.content)}
            model.tour.finish()
        }
    }
    @After fun close()=ui.runOnUiThread {if(!retainedByActivity)model.disposeDiagnostics();owner.interactionFeedback.close()}
    private fun show() {
        ui.setContent {ToolHost()}
    }
    @Composable private fun ToolHost() {
        HamigoTheme {FeedbackHost(owner.interactionFeedback) {
            CompositionLocalProvider(LocalContext provides model.diagnosticContext,LocalDiagnosticsCovered provides false) {
                val arranged=questionToolsHorizontal()
                SideEffect {horizontal=arranged}
                HamigoAppContent(model,model)
            }
        }}
    }
    private fun tools() {
        val minimum=with(ui.density){48.dp.toPx()}
        ui.waitUntil(10_000) {listOf("learning-tool-scratchpad","learning-tool-calculator").all {tag->
            ui.onAllNodesWithTag(tag,useUnmergedTree=true).fetchSemanticsNodes().singleOrNull()?.boundsInRoot?.let {it.width>=minimum&&it.height>=minimum}==true
        }}
        val notes=ui.onNodeWithTag("learning-tool-scratchpad",useUnmergedTree=true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val calculator=ui.onNodeWithTag("learning-tool-calculator",useUnmergedTree=true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue(notes.width>=minimum&&notes.height>=minimum)
        assertTrue(calculator.width>=minimum&&calculator.height>=minimum)
        if(horizontal) {
            assertEquals(notes.center.y,calculator.center.y,1f)
            assertTrue(notes.right<calculator.left)
        } else {
            assertEquals(notes.center.x,calculator.center.x,1f)
            assertTrue(notes.bottom<calculator.top)
        }
    }
    private fun closeNotes() {
        ui.onNodeWithContentDescription("Fermer le brouillon").performClick()
        ui.waitUntil(10_000) {ui.onAllNodesWithTag("scratchpad-surface",useUnmergedTree=true).fetchSemanticsNodes().isEmpty()}
        if(Build.VERSION.SDK_INT>=30)ui.waitUntil(10_000) {!keyboard()}
    }
    private fun closeCalculator() {
        ui.onNodeWithContentDescription("Fermer la calculatrice").performClick()
        ui.waitUntil(10_000) {ui.onAllNodesWithTag("calculator-surface",useUnmergedTree=true).fetchSemanticsNodes().isEmpty()}
    }
    private fun keyboard()=Build.VERSION.SDK_INT>=30&&WindowInspector.getGlobalWindowViews().any {it.rootWindowInsets?.isVisible(WindowInsets.Type.ime())==true}

    @Test fun anIntroductionKeepsItsReadingAndFooterWithoutFloatingTools() {
        val lesson=content.lessons.first().copy(studyToolsHint=true)
        ui.runOnIdle {model.startLesson(lesson)}
        val before=owner.progress.export();val local=model.progress.export()
        show()
        ui.onNodeWithTag("learning-tools").assertDoesNotExist()
        ui.onNodeWithTag("study-tools-hint").assertDoesNotExist()
        ui.onNodeWithTag("lesson-introduction-list").performScrollToNode(hasText(lesson.body.last()))
        ui.onNodeWithText(lesson.body.last()).assertIsDisplayed()
        ui.onNodeWithText("À toi de jouer",substring=false).assertIsDisplayed()
        capture("intro-reading-without-tools")
        assertEquals(before,owner.progress.export());assertEquals(local,model.progress.export())
    }

    @Test fun theMemoLibraryKeepsItsToolArrangementWhenTheRealSearchKeyboardOpens() {
        ui.runOnIdle {model.route="resources";model.lesson=null;model.resource=null}
        val before=owner.progress.export()
        show();tools()
        val initial=horizontal
        capture("memo-library-tools")
        ui.onNodeWithContentDescription("Rechercher un mémo").performClick()
        if(Build.VERSION.SDK_INT>=30)ui.waitUntil(30_000) {keyboard()}
        ui.onNodeWithTag("memo-search-input").assertIsDisplayed()
        tools()
        assertEquals("IME alone must not select the short-window tool row",initial,horizontal)
        ui.onNodeWithTag("memo-search-input").assertIsFocused()
        assertTrue("The actual keyboard remains open after the responsive layout",keyboard())
        capture("memo-library-search-keyboard")
        ui.onNodeWithTag("learning-tool-scratchpad").performClick()
        ui.onNodeWithTag("scratchpad-surface",useUnmergedTree=true).assertIsDisplayed()
        capture("memo-library-scratchpad")
        ui.onNodeWithContentDescription("Texte").performClick()
        ui.onNodeWithTag("scratchpad-text").performTextInput("Notes de la bibliothèque")
        closeNotes()
        ui.onNodeWithTag("memo-library-list").performScrollToNode(hasTestTag("memo-row-units"))
        // LazyColumn may expose only the row's top beneath the raised navigation dock.
        // Move its actual tap target into the unobstructed part of the viewport.
        val library=ui.onNodeWithTag("memo-library-list").fetchSemanticsNode().boundsInRoot
        val row=ui.onNodeWithTag("memo-row-units").fetchSemanticsNode().boundsInRoot
        val lift=(row.center.y-library.top-library.height*.55f).coerceIn(0f,library.height*.4f)
        if(lift>1f)ui.onNodeWithTag("memo-library-list").performTouchInput {
            swipe(center,center.copy(y=center.y-lift))
        }
        capture("memo-library-before-open-fiche")
        ui.onNodeWithTag("memo-row-units").performClick()
        ui.runOnIdle {assertEquals("units",model.resource?.id)}
        ui.waitUntil(10_000) {ui.onAllNodesWithTag("learning-tool-scratchpad").fetchSemanticsNodes().size==1}
        capture("memo-library-opened-fiche")
        ui.onNodeWithTag("learning-tool-scratchpad").performClick()
        ui.onNodeWithTag("scratchpad-text").assertDoesNotExist()
        ui.onNodeWithContentDescription("Texte").performClick()
        ui.onNodeWithTag("scratchpad-text").performTextInput("Notes propres à la fiche")
        closeNotes()
        ui.onNodeWithContentDescription("Retour aux mémos").performClick()
        ui.onNodeWithTag("learning-tool-scratchpad").performClick()
        ui.onNodeWithTag("scratchpad-text").assertTextContains("Notes de la bibliothèque")
        closeNotes()
        assertEquals(before,owner.progress.export())
    }

    @Test fun theMemoLibraryEndsWithAnAccessibleCourseLinkRatherThanAnEmptyToolReserve() {
        ui.runOnIdle {model.route="resources";model.lesson=null;model.resource=null}
        val before=owner.progress.export()
        show();tools()
        ui.onNodeWithTag("memo-library-list").performScrollToNode(hasTestTag("memo-course-link"))
        // The raised navigation button covers the default bottom-center swipe origin.
        ui.onNodeWithTag("memo-library-list").performTouchInput {
            swipe(Offset(width*.2f,height*.6f),Offset(width*.2f,height*.15f),durationMillis=300)
        }
        capture("memo-library-compact-footer")
        val link=ui.onNodeWithTag("memo-course-link").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val dock=ui.onNodeWithTag("learning-tools",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val finalMemo=content.references.groupBy {it.group.ifBlank {"Repères radio"}}.values.last().last()
        val row=ui.onNodeWithTag("memo-row-${finalMemo.id}").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("The final memo must clear the floating tools: row=$row, tools=$dock",row.bottom<=dock.top)
        assertFalse("The centered source remains tappable beside the tools",link.overlaps(dock))
        assertTrue("The source must follow the final memo at the top of the footer",
            link.top-row.bottom in 0f..with(ui.density){32.dp.toPx()})
        assertEquals(before,owner.progress.export())
    }

    @Test fun eachMemoReallyHasBothToolsAndChangingTheFicheResetsTheirLocalWork() {
        val first=content.references.first {it.id=="units"}
        val second=content.references.first {it.id=="resistors"}
        ui.runOnIdle {model.route="resources";model.resource=first}
        val before=owner.progress.export();val local=model.progress.export()
        show();tools();capture("memo-detail-tools")
        ui.onNodeWithTag("learning-tool-scratchpad").performClick()
        ui.onNodeWithContentDescription("Texte").performClick()
        ui.onNodeWithTag("scratchpad-text").performTextInput("12 V, 200 mA")
        closeNotes()
        ui.onNodeWithTag("learning-tool-calculator").performClick()
        CalculatorEditorDriver(ui).performTextInput("12/0.2")
        ui.onNodeWithTag("calculator-key-=").performScrollTo().performClick()
        ui.onNodeWithTag("calculator-result").assertTextEquals("60")
        closeCalculator()
        ui.runOnIdle {model.resource=second}
        ui.waitForIdle();tools()
        ui.onNodeWithTag("learning-tool-scratchpad").performClick()
        ui.onNodeWithTag("scratchpad-text").assertDoesNotExist()
        ui.onNodeWithContentDescription("Texte").performClick()
        ui.onNodeWithTag("scratchpad-text").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.EditableText,androidx.compose.ui.text.AnnotatedString("")))
        closeNotes()
        ui.onNodeWithTag("learning-tool-calculator").performClick()
        CalculatorEditorDriver(ui).assertTextEquals("")
        ui.onNodeWithTag("calculator-result").assertTextEquals("=")
        closeCalculator()
        ui.onNodeWithTag("memo-detail-list").performScrollToNode(hasTestTag("memo-footer-pico"))
        ui.onNodeWithTag("memo-detail-list").performTouchInput {swipeUp()}
        ui.onNodeWithTag("memo-footer-pico").assertIsDisplayed()
        val pico=ui.onNodeWithTag("memo-footer-pico",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val dock=ui.onNodeWithTag("learning-tools",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertFalse("The centered Pico must remain outside the floating tools",pico.overlaps(dock))
        val feedback=ui.onNodeWithTag("memo-feedback",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue("The feedback action must remain above the floating tools",feedback.bottom<=dock.top)
        capture("memo-detail-compact-footer")
        // Returning to the old fiche after visiting another one starts a fresh fiche document.
        ui.runOnIdle {model.resource=first}
        ui.onNodeWithTag("learning-tool-scratchpad").performClick()
        ui.onNodeWithTag("scratchpad-text").assertDoesNotExist()
        closeNotes()
        ui.onNodeWithTag("learning-tool-calculator").performClick()
        CalculatorEditorDriver(ui).assertTextEquals("")
        closeCalculator()
        assertEquals(before,owner.progress.export())
        assertEquals(local,model.progress.export())
    }

    @Test fun consultingALinkedMemoAndCancellingPredictiveBackPreserveItsToolsAndCourseScroll() {
        val lesson=content.lessons.single {it.id=="c03-l03"}
        val category=content.referencesFor(lesson).first()
        ui.runOnIdle {model.startLesson(lesson)}
        val before=owner.progress.export();val local=model.progress.export()
        show()
        ui.onNodeWithTag("learning-tools").assertDoesNotExist()
        val row="lesson-memo-${category.id}"
        ui.onNodeWithTag("lesson-introduction-list").performScrollToNode(hasTestTag(row))
        ui.onNodeWithTag(row).performClick()
        ui.runOnIdle {assertEquals(category,model.resource);assertEquals(lesson,model.lesson)}
        ui.onNodeWithTag("learning-tool-scratchpad").performClick()
        ui.onNodeWithTag("scratchpad-text").assertDoesNotExist()
        ui.onNodeWithContentDescription("Texte").performClick()
        ui.onNodeWithTag("scratchpad-text").performTextInput("Fiche : 200 mA")
        closeNotes()
        ui.onNodeWithTag("learning-tool-calculator").performClick()
        CalculatorEditorDriver(ui).assertTextEquals("")
        CalculatorEditorDriver(ui).performTextInput("3*7")
        ui.onNodeWithTag("calculator-key-=").performScrollTo().performClick()
        ui.onNodeWithTag("calculator-result").assertTextEquals("21")
        closeCalculator()
        ui.runOnIdle {ui.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f,200f,0f,BackEventCompat.EDGE_LEFT))}
        ui.runOnIdle {ui.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(120f,200f,.6f,BackEventCompat.EDGE_LEFT))}
        ui.onNode(hasTestTag("learning-tools") and hasAnyAncestor(hasTestTag("back-destination-path")),useUnmergedTree=true).assertDoesNotExist()
        ui.runOnIdle {ui.activity.onBackPressedDispatcher.dispatchOnBackCancelled()}
        ui.waitUntil(5_000) {ui.onAllNodesWithTag("back-destination-path").fetchSemanticsNodes().isEmpty()}
        ui.runOnIdle {assertEquals(category,model.resource)}
        ui.onNodeWithTag("learning-tool-calculator").performClick()
        CalculatorEditorDriver(ui).assertTextEquals("3*7")
        ui.onNodeWithTag("calculator-result").assertTextEquals("21")
        closeCalculator()
        ui.onNodeWithContentDescription("Retour au cours").performClick()
        ui.onNodeWithTag(row).assertIsDisplayed()
        ui.onNodeWithTag("learning-tools").assertDoesNotExist()
        ui.onNodeWithTag(row).performClick()
        ui.onNodeWithTag("learning-tool-scratchpad").performClick()
        ui.onNodeWithTag("scratchpad-text").assertTextContains("Fiche : 200 mA")
        closeNotes()
        ui.onNodeWithTag("learning-tool-calculator").performClick()
        CalculatorEditorDriver(ui).assertTextEquals("3*7")
        ui.onNodeWithTag("calculator-result").assertTextEquals("21")
        closeCalculator()
        assertEquals(before,owner.progress.export());assertEquals(local,model.progress.export())
    }

    @Test fun recreatingTheRealHostRetainsTheViewModelAndBothToolDocuments() {
        val original=ui.activity
        ui.runOnIdle {
            val factory=object:ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T:ViewModel> create(modelClass:Class<T>):T {
                    require(modelClass==AppModel::class.java)
                    return model as T
                }
            }
            assertSame(model,ViewModelProvider(original,factory)[AppModel::class.java])
            retainedByActivity=true
            model.route="resources";model.lesson=null;model.resource=null
        }
        val before=owner.progress.export();val local=model.progress.export()
        show()
        ui.onNodeWithTag("learning-tool-scratchpad").performClick()
        ui.onNodeWithContentDescription("Texte").performClick()
        ui.onNodeWithTag("scratchpad-text").performTextInput("Notes conservées à la rotation")
        val retainedPad=LearningToolsNavigation.library(model).scratchpad
        ui.runOnIdle {
            retainedPad.color=0xFFB54B3D.toInt();retainedPad.brushWidth=5f;retainedPad.toolbarRight=true;retainedPad.paperScroll=1.25f
            retainedPad.replaceInk(listOf(InkStroke(listOf(InkPoint(.2f,.1f,1f),InkPoint(.6f,.15f,.8f)),retainedPad.color,retainedPad.brushWidth)))
        }
        closeNotes()
        ui.onNodeWithTag("learning-tool-calculator").performClick()
        CalculatorEditorDriver(ui).performTextInput("9+7")
        ui.onNodeWithTag("calculator-key-=").performScrollTo().performClick()
        ui.onNodeWithTag("calculator-result").assertTextEquals("16")
        CalculatorEditorDriver(ui).select(1,2)
        val application=original.application
        val callback=object:Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity:Activity,savedInstanceState:Bundle?) {
                if(activity is ComponentActivity&&activity.javaClass==original.javaClass&&activity!==original)
                    activity.setContent {ToolHost()}
            }
            override fun onActivityStarted(activity:Activity)=Unit
            override fun onActivityResumed(activity:Activity)=Unit
            override fun onActivityPaused(activity:Activity)=Unit
            override fun onActivityStopped(activity:Activity)=Unit
            override fun onActivitySaveInstanceState(activity:Activity,outState:Bundle)=Unit
            override fun onActivityDestroyed(activity:Activity)=Unit
        }
        application.registerActivityLifecycleCallbacks(callback)
        try {
            ui.activityRule.scenario.recreate()
            ui.waitUntil(10_000) {ui.onAllNodesWithTag("calculator-surface",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
            ui.runOnIdle {assertNotSame(original,ui.activity);assertSame(model,ViewModelProvider(ui.activity)[AppModel::class.java])}
            CalculatorEditorDriver(ui).assertTextEquals("9+7")
            assertEquals(TextRange(1,2),CalculatorEditorDriver(ui).selection())
            ui.onNodeWithTag("calculator-result").assertTextEquals("16")
            closeCalculator()
            ui.onNodeWithTag("learning-tool-scratchpad").performClick()
            ui.onNodeWithTag("scratchpad-text").assertTextContains("Notes conservées à la rotation")
            ui.activityRule.scenario.recreate()
            ui.waitUntil(10_000) {ui.onAllNodesWithTag("scratchpad-text").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithTag("scratchpad-text").assertTextContains("Notes conservées à la rotation")
            ui.onNodeWithContentDescription("Dessin").performClick()
            ui.onNodeWithTag("scratchpad-ink").assertIsDisplayed()
            ui.runOnIdle {
                assertSame(retainedPad,LearningToolsNavigation.library(model).scratchpad)
                assertEquals(1,retainedPad.strokes.size);assertEquals(0xFFB54B3D.toInt(),retainedPad.strokes.single().color)
                assertEquals(5f,retainedPad.brushWidth,.001f);assertEquals(1.25f,retainedPad.paperScroll,.001f);assertTrue(retainedPad.toolbarRight)
            }
            closeNotes()
            assertEquals(before,owner.progress.export());assertEquals(local,model.progress.export())
        } finally {application.unregisterActivityLifecycleCallbacks(callback)}
    }

    private fun capture(name:String) {
        ui.waitForIdle()
        if(Build.VERSION.SDK_INT>=29)repeat(2) {
            val frame=CountDownLatch(1)
            instrumentation.runOnMainSync {
                val view=WindowInspector.getGlobalWindowViews().lastOrNull {it.hasWindowFocus()} ?: ui.activity.window.decorView
                view.viewTreeObserver.registerFrameCommitCallback {frame.countDown()};view.invalidate()
            }
            assertTrue(frame.await(5,TimeUnit.SECONDS))
        }
        val bitmap=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory=File(context.getExternalFilesDir(null),"parcours-progressif/learning-tools").apply {mkdirs()}
        try {File(directory,"$name.png").outputStream().use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}}
        finally {bitmap.recycle()}
    }
}
