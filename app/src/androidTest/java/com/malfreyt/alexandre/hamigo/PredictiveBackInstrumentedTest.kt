package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.activity.BackEventCompat
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.platform.DailyReminder
import com.malfreyt.alexandre.hamigo.platform.ProgressSyncScheduler
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Dispatches the real Activity callbacks consumed by Compose's PredictiveBackHandler. */
@RunWith(AndroidJUnit4::class)
class PredictiveBackInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var saved: Map<String, *> = emptyMap<String, Any>()
    private val fixture = object : ExternalResource() {
        override fun before() {
            check(Build.HARDWARE in listOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("generic")) {
                "Navigation fixtures must run on the emulator, never the user's phone."
            }
            val prefs = context.getSharedPreferences("hamigo", Context.MODE_PRIVATE)
            saved = prefs.all.mapValues { (_, value) -> if (value is Set<*>) value.toSet() else value }
            check(prefs.edit().clear().putBoolean("welcomed", true).putBoolean("autoSync", false)
                .putString("friends", "[]").putString("name", "Retour test").commit())
            ProgressSyncScheduler.cancel(context)
        }
        override fun after() {
            val edit = context.getSharedPreferences("hamigo", Context.MODE_PRIVATE).edit().clear()
            saved.forEach { (key, value) -> restore(edit, key, value) }
            check(edit.commit())
            stopMorse()
            DailyReminder.schedule(context)
            ProgressSyncScheduler.schedule(context)
        }
    }
    private val ui = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(ui)
    private lateinit var model: AppModel
    private val verticalScroll = hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

    @Before fun loadContent() {
        model = ViewModelProvider(ui.activity)[AppModel::class.java]
        ui.waitUntil(60_000) { model.content != null }
        ui.runOnIdle { model.showWelcome = false; model.route = "resources" }
    }

    @Test fun cancellingTheMemoGestureKeepsItsFilterAndScrollWhileCompletingItOpensTheLibrary() {
        ui.runOnIdle { model.resource = model.content!!.references.first { it.id == "nato" } }
        ui.onNodeWithContentDescription("Filtrer cette fiche").performClick()
        ui.onNode(hasSetTextAction()).performTextInput("a")
        ui.onNode(verticalScroll).performScrollToNode(hasText("Yankee"))
        ui.onNodeWithText("Yankee").assertIsDisplayed()
        val before = scrollPosition()
        assertTrue("The fiche must actually be scrolled before the gesture.", before > 0f)

        startAndProgress(.6f)
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        ui.waitForIdle()
        ui.runOnIdle { assertEquals("nato", model.resource?.id) }
        ui.onNode(hasSetTextAction()).assertTextContains("a")
        ui.onNodeWithText("Yankee").assertIsDisplayed()
        assertEquals(before, scrollPosition(), .001f)

        startAndProgress(.85f)
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.waitUntil(5000) { model.resource == null }
        ui.onNodeWithText("Les petits mémos").assertIsDisplayed()
        ui.onNodeWithContentDescription("Retour aux mémos").assertDoesNotExist()
    }

    @Test fun examBackWarnsAboutTheDraftAndDismissalKeepsSavedAndUnsubmittedAnswers() {
        ui.runOnIdle {
            val questions = (0 until 40).map { index ->
                Question("back-exam-$index", "Question de navigation ${index + 1}", listOf("Choix A", "Choix B"), 0,
                    "Explication", section = if (index < 20) "regulation" else "technique")
            }
            model.startQuestions("Examen de navigation", questions, exam = true)
            model.beginExamPart()
        }
        ui.onNodeWithText("Choix A").performScrollTo().performClick()
        ui.onNodeWithText("Enregistrer et continuer").performClick()
        ui.onNodeWithText("Question de navigation 2").assertExists()
        ui.onNodeWithText("Choix B").performScrollTo().performClick()
        ui.onNodeWithText("Enregistrer et continuer").assertIsEnabled()
        val original = model.session!!

        startAndProgress(.5f)
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        ui.waitForIdle()
        ui.runOnIdle {
            assertSame(original, model.session)
            assertEquals(1, original.index)
            assertEquals("Choix A", original.responses.getValue(0).display)
            assertEquals(0, model.progress.xp)
        }
        ui.onNodeWithText("Enregistrer et continuer").assertIsEnabled()
        ui.onNodeWithText("Une pause radio ?").assertDoesNotExist()

        startAndProgress(.9f)
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.onNodeWithText("Une pause radio ?").assertIsDisplayed()
        ui.onNodeWithText("Les réponses de l’épreuve en cours seront perdues", substring = true).assertIsDisplayed()
        ui.onNodeWithText("Continuer", substring = false).performClick()
        ui.onNodeWithText("Enregistrer et continuer").assertIsEnabled()
        ui.runOnIdle {
            assertSame(original, model.session)
            assertEquals(1, original.index)
            assertEquals("Choix A", original.responses.getValue(0).display)
            assertTrue(original.finalizedExamParts.isEmpty())
        }
    }

    private fun startAndProgress(progress: Float) {
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 200f, 0f, BackEventCompat.EDGE_LEFT)) }
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(120f, 200f, progress, BackEventCompat.EDGE_LEFT)) }
        ui.waitForIdle()
    }
    private fun scrollPosition(): Float = ui.onNode(verticalScroll).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
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
