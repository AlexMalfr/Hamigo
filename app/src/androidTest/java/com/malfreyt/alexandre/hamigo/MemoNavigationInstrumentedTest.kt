package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
        ui.onNode(libraryScroll).performScrollToNode(hasText("Les 3 régions UIT"))
        val positionBefore = ui.onNode(libraryScroll).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue("La bibliothèque doit avoir quitté le début de la liste", positionBefore > 0f)
        ui.onNodeWithText("Les 3 régions UIT").performClick()
        ui.onNodeWithContentDescription("Retour aux mémos").performClick()
        // Check the restored position before doing any further scroll.
        ui.onNodeWithText("Les 3 régions UIT").assertIsDisplayed()
        val positionAfter = ui.onNode(libraryScroll).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals("Le retour doit conserver la position de la bibliothèque", positionBefore, positionAfter, 0.001f)
        ui.runOnIdle { model.resource = model.content!!.references.first { it.id == "resistors" } }
        ui.onNode(libraryScroll).performScrollToNode(hasText("5 anneaux"))
        ui.onNodeWithText("Code des résistances").assertIsDisplayed()
        ui.onNodeWithText("Réviser avec les flashcards").assertIsDisplayed()
        ui.onNodeWithText("Ordre aléatoire").assertIsDisplayed()
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
