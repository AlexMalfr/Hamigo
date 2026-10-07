package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso
import android.graphics.Bitmap
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.malfreyt.alexandre.hamigo.platform.ProgressSyncScheduler
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoNavigationInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var saved: Map<String, *> = emptyMap<String, Any>()
    private val fixture = object : ExternalResource() {
        override fun before() {
            check(android.os.Build.MODEL.startsWith("sdk_") || android.os.Build.FINGERPRINT.contains("generic")) { "Emulator fixtures only" }
            val prefs = context.getSharedPreferences("hamigo", Context.MODE_PRIVATE)
            saved = prefs.all.mapValues { (_, value) -> if (value is Set<*>) value.toSet() else value }
            check(prefs.edit().clear().putBoolean("welcomed", true).putBoolean("autoSync", false).putString("name", "Mémo test").commit())
            ProgressSyncScheduler.cancel(context)
        }
        override fun after() {
            val editor = context.getSharedPreferences("hamigo", Context.MODE_PRIVATE).edit().clear()
            saved.forEach { (key, value) -> restore(editor, key, value) }
            check(editor.commit())
            stopMorse()
            ProgressSyncScheduler.schedule(context)
        }
    }
    private val ui = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(ui)
    private lateinit var model: AppModel
    @Before fun load() {
        model = ViewModelProvider(ui.activity)[AppModel::class.java]
        ui.waitUntil(60_000) { model.content != null }
        ui.runOnIdle { model.showWelcome = false; model.route = "resources" }
    }

    @Test fun returningFromLastFicheKeepsLibraryScrollAndDetailHeaderStaysVisible() {
        val libraryScroll = hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        val regionsTitle=model.content!!.references.first {it.id=="itu-regions"}.title
        ui.onNode(libraryScroll).performScrollToNode(hasText(regionsTitle))
        val positionBefore = ui.onNode(libraryScroll).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("La bibliothèque doit avoir quitté le début de la liste", positionBefore > 0f)
        ui.onNodeWithText(regionsTitle).performClick()
        ui.onNodeWithContentDescription("Retour aux mémos").performClick()
        // Check the restored position before doing any further scroll.
        ui.onNodeWithText(regionsTitle).assertIsDisplayed()
        val positionAfter = ui.onNode(libraryScroll).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals("Le retour doit conserver la position de la bibliothèque", positionBefore, positionAfter, 0.001f)
        ui.runOnIdle { model.resource = model.content!!.references.first { it.id == "resistors" } }
        ui.onNode(libraryScroll).performScrollToNode(hasText("5 anneaux"))
        ui.onNodeWithText(model.content!!.references.first {it.id=="resistors"}.title).assertIsDisplayed()
        ui.onNodeWithText("Réviser avec les flashcards").assertIsDisplayed()
        ui.onNodeWithText("Ordre aléatoire").assertIsDisplayed()
    }

    @Test fun stickyHeaderOnlyCastsItsShadowAfterDetachingFromTheTop() {
        fun belowHeaderReds(): List<Float> {
            val header=ui.onNodeWithTag("memo-library-header",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            val root=ui.onRoot().fetchSemanticsNode().boundsInRoot
            val pixels=ui.onRoot().captureToImage().toPixelMap()
            val y=(header.bottom-root.top).toInt()+1
            return listOf(1,(header.left-root.left+with(ui.density){4.dp.toPx()}).toInt(),pixels.width-2)
                .map { pixels[it,y].red }
        }
        val initial=belowHeaderReds()
        initial.forEach { assertEquals(Cream.red,it,.02f) }
        ui.onNodeWithTag("memo-library-list").performScrollToNode(hasTestTag("memo-row-radio-regulations"))
        ui.onNodeWithTag("memo-library-title").assertIsDisplayed()
        val pinned=belowHeaderReds()
        pinned.forEach { assertTrue("The pinned header needs a visible shadow right through both side gutters.",it<Cream.red-.10f) }
        assertEquals("The shadow must have the same density at the left edge.",pinned[1],pinned[0],.02f)
        assertEquals("The shadow must have the same density at the right edge.",pinned[1],pinned[2],.02f)
        ui.onNodeWithTag("memo-library-list").performScrollToIndex(0)
        belowHeaderReds().forEachIndexed { i,red -> assertEquals("Returning to the top should remove the header shadow.",initial[i],red,.02f) }
    }

    @Test fun morseInputUsesOnlySignalButtonsAndClearRemovesItsTranslation() {
        ui.runOnIdle { model.resource = model.content!!.references.first { it.id == "morse" } }
        ui.onNodeWithText("Texte ↔ Morse, avec le son").performClick()
        ui.onNodeWithText("Morse → texte").performScrollTo().performClick()
        ui.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)).assertCountEquals(0)
        ui.onNodeWithText("SOS", substring = false).performScrollTo().assertIsDisplayed()
        ui.onNodeWithContentDescription("Tout effacer").performScrollTo().performClick()
        ui.onNodeWithText("Le résultat apparaît ici.").assertExists()
        ui.onNodeWithText("... --- ...", substring = false).assertDoesNotExist()
    }

    private fun keyboardVisible(): Boolean {
        var visible = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            visible = ViewCompat.getRootWindowInsets(ui.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return visible
    }

    @Test fun librarySearchFocusesAndCollapsesOnlyWhenDismissedEmpty() {
        ui.onNodeWithContentDescription("Rechercher un mémo").performClick()
        ui.onNodeWithTag("memo-search-input").assertIsFocused()
        try { ui.waitUntil(5_000) { keyboardVisible() } }
        finally { capture("search-ime-initial") }
        capture("search-keyboard-open")
        Espresso.pressBack()
        ui.waitUntil(5_000) { !keyboardVisible() }
        ui.onNodeWithTag("memo-search-input").assertDoesNotExist()
        ui.onNodeWithTag("memo-library-title").assertIsDisplayed()
        ui.onNodeWithContentDescription("Rechercher un mémo").performClick()
        ui.waitUntil(5_000) { keyboardVisible() }
        ui.onNodeWithTag("memo-search-input").performTextInput("binaire")
        Espresso.pressBack()
        ui.waitUntil(5_000) { !keyboardVisible() }
        ui.onNodeWithTag("memo-search-input").assertTextContains("binaire").assertIsNotFocused()
        ui.onNodeWithText("Binaire et portes logiques").assertIsDisplayed()
        capture("search-filter-kept")
        ui.onNodeWithText("Binaire et portes logiques").performClick()
        ui.onNodeWithContentDescription("Retour aux mémos").performClick()
        ui.onNodeWithTag("memo-search-input").assertTextContains("binaire").assertIsNotFocused()
        assertTrue("Returning to the filtered library must not reopen the IME", !keyboardVisible())
        ui.onNodeWithTag("memo-search-input").performClick().performTextClearance()
        ui.waitUntil(5_000) { keyboardVisible() }
        // Tap a non-interactive part of the header, exercising outside-tap dismissal.
        ui.onNodeWithTag("memo-library-title").performTouchInput { click() }
        ui.waitUntil(5_000) { !keyboardVisible() }
        ui.onNodeWithTag("memo-search-input").assertDoesNotExist()
        capture("search-outside-collapsed")
    }

    @Test fun dedicatedLogicFicheAndSandboxRenderDistinctiveSymbols() {
        ui.runOnIdle { model.resource = model.content!!.references.first { it.id == "binary-logic" } }
        ui.onNodeWithText("Entrées → porte → sortie").performClick()
        ui.onNodeWithTag("logic-tool-output").assertTextEquals("S = 0")
        ui.onNodeWithText("A = 0").performClick()
        ui.onNodeWithTag("logic-tool-output").assertTextEquals("S = 0")
        ui.onNodeWithText("B = 0").performClick()
        ui.onNodeWithTag("logic-tool-output").assertTextEquals("S = 1")
        capture("logic-sandbox")
        ui.onNodeWithText("Entrées → porte → sortie").performClick()
        for ((term,tags) in listOf("Porte ET" to listOf("and"),"Porte OU" to listOf("or"),"Porte NON" to listOf("not"),"NON ET et NON OU" to listOf("nand","nor"),"OU exclusif" to listOf("xor"))) {
            val scroll = hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
            ui.onNode(scroll).performScrollToNode(hasText(term))
            for (tag in tags) ui.onNodeWithTag("logic-gate-$tag").performScrollTo().assertIsDisplayed()
            capture("logic-$term")
        }
    }

    @Test fun dedicatedChapterKeepsTheExistingBinaryLessonAndShowsGateExercises() {
        val content = model.content!!
        val chapter = content.chapters.first { it.id == "c22" }
        assertEquals(4, chapter.lessons.size)
        assertEquals("c20-l01", chapter.lessons.first().id)
        assertEquals(1, content.lessons.count { it.id == "c20-l01" })
        assertTrue(content.chapters.first { it.id == "c20" }.lessons.none { it.id == "c20-l01" })
        ui.runOnIdle { model.route="path"; model.startLesson(chapter.lessons[2]) }
        ui.onNodeWithTag("logic-gate-and",useUnmergedTree=true).performScrollTo().assertIsDisplayed()
        capture("logic-lesson-symbols")
        ui.runOnIdle {
            model.lesson=null
            model.startQuestions("Logique",listOf(chapter.lessons[2].questions.first()),chapter.lessons[2].id)
        }
        ui.onNodeWithTag("logic-gate-and").assertIsDisplayed()
        ui.onAllNodesWithText("ET",substring=false).assertCountEquals(1)
        capture("logic-question")
        ui.runOnIdle {
            model.session=null
            model.startQuestions("Flashcards",listOf(content.flashcards.first { it.id=="flash-logic-digital-03f25a16bfc5" }))
        }
        ui.onNodeWithTag("flashcard").performScrollTo()
        capture("logic-flashcard")
        ui.onNodeWithTag("logic-gate-and",useUnmergedTree=true).assertIsDisplayed()
        ui.onNodeWithTag("flashcard").performClick()
        ui.onNodeWithTag("logic-gate-and",useUnmergedTree=true).performScrollTo().assertIsDisplayed()
        capture("logic-flashcard-answer")
    }

    private fun capture(name: String) {
        ui.waitForIdle()
        // Compose can have updated scroll semantics before Android submits the
        // corresponding frame. Wait for the hardware drawing, not a timed sleep.
        if (android.os.Build.VERSION.SDK_INT >= 29) repeat(2) {
            val frame = CountDownLatch(1)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val view=ui.activity.window.decorView
                view.viewTreeObserver.registerFrameCommitCallback { frame.countDown() }
                view.invalidate()
            }
            assertTrue("The screenshot needs a submitted frame",frame.await(5,TimeUnit.SECONDS))
        }
        val dir=File(context.getExternalFilesDir(null),"memo-logic-0.36").apply { mkdirs() }
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try { File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
        finally { bitmap.recycle() }
    }

    private fun restore(edit: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is String -> edit.putString(key, value)
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is Float -> edit.putFloat(key, value)
            is Boolean -> edit.putBoolean(key, value)
            is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }
}
