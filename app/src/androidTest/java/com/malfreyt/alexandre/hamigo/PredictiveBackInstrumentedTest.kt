package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.BackEventCompat
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import java.io.File
import kotlin.math.hypot

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

    @Test fun ordinaryBackReturnsEverySecondaryMainDestinationToParcours() {
        listOf("practice", "resources", "friends", "profile").forEach { route ->
            ui.runOnIdle { model.route = route }
            ui.waitForIdle()
            ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
            ui.waitForIdle()
            ui.runOnIdle {
                assertEquals("Back from $route should return to the main learning page.", "path", model.route)
                assertFalse("Back between tabs must keep Hamigo open.", ui.activity.isFinishing)
            }
            ui.onNodeWithText("HAMIGO").assertIsDisplayed()
            navigationTab("Parcours").assertIsSelected()
        }
    }

    @Test fun predictiveBackFromBothEdgesCancelsOrReturnsEveryMainDestinationToParcours() {
        val destinations = listOf(
            "practice" to "Défis", "resources" to "Mémo", "friends" to "Équipe", "profile" to "Moi"
        )
        listOf(BackEventCompat.EDGE_LEFT, BackEventCompat.EDGE_RIGHT).forEach { edge ->
            destinations.forEach { (route, label) ->
                ui.runOnIdle { model.route = route }
                ui.waitForIdle()
                capture("$route-before")
                startAndProgress(.45f, edge)
                capture("$route-back-${if (edge == BackEventCompat.EDGE_LEFT) "left" else "right"}")
                // The destination is genuinely visible before release; cancellation preserves the source tab.
                ui.onNodeWithText("HAMIGO").assertIsDisplayed()
                ui.runOnIdle {
                    assertEquals(route, model.route)
                    ui.activity.onBackPressedDispatcher.dispatchOnBackCancelled()
                }
                ui.waitForIdle()
                ui.runOnIdle { assertEquals("Cancelling from $route on edge $edge", route, model.route) }
                navigationTab(label).assertIsSelected()
                ui.onNodeWithText("HAMIGO").assertDoesNotExist()

                startAndProgress(.85f, edge)
                ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
                ui.waitForIdle()
                ui.runOnIdle {
                    assertEquals("Completing from $route on edge $edge", "path", model.route)
                    assertFalse(ui.activity.isFinishing)
                }
                ui.onNodeWithText("HAMIGO").assertIsDisplayed()
                navigationTab("Parcours").assertIsSelected()
            }
        }
    }

    @Test fun startingAnotherGestureDuringCancellationKeepsItsPreviewAndTheMemoFilter() {
        ui.onNodeWithContentDescription("Rechercher un mémo").performClick()
        ui.onNode(hasSetTextAction()).performTextInput("Morse")
        startAndProgress(.6f)

        ui.mainClock.autoAdvance = false
        try {
            ui.runOnIdle { ui.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
            ui.mainClock.advanceTimeBy(32)
            ui.runOnIdle { assertEquals("resources", model.route) }
            ui.runOnIdle {
                ui.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 200f, 0f, BackEventCompat.EDGE_LEFT))
            }
            ui.runOnIdle {
                ui.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(100f, 200f, .45f, BackEventCompat.EDGE_LEFT))
            }
            // Longer than the old rebound: it must not erase the newly started gesture's preview.
            ui.mainClock.advanceTimeBy(240)
            ui.onNodeWithText("HAMIGO").assertIsDisplayed()
            ui.onNode(hasSetTextAction()).assertTextContains("Morse")
            ui.runOnIdle {
                assertEquals("resources", model.route)
                ui.activity.onBackPressedDispatcher.dispatchOnBackCancelled()
            }
            ui.mainClock.advanceTimeBy(240)
            ui.onNodeWithText("HAMIGO").assertDoesNotExist()
            ui.onNode(hasSetTextAction()).assertTextContains("Morse")
            navigationTab("Mémo").assertIsSelected()
        } finally {
            ui.mainClock.autoAdvance = true
        }
    }

    @Test fun backFromSettingsAndLessonStillVisitsTheirParentBeforeParcours() {
        ui.runOnIdle { model.route = "settings" }
        startAndProgress(.7f, BackEventCompat.EDGE_RIGHT)
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.waitForIdle()
        ui.runOnIdle { assertEquals("profile", model.route) }
        navigationTab("Moi").assertIsSelected()

        ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.waitForIdle()
        ui.runOnIdle {
            assertEquals("path", model.route)
            model.startLesson(model.content!!.lessons.first())
        }
        startAndProgress(.7f)
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
        ui.waitForIdle()
        ui.runOnIdle {
            assertNull(model.lesson)
            assertEquals("path", model.route)
            assertFalse(ui.activity.isFinishing)
        }
        navigationTab("Parcours").assertIsSelected()
    }

    @Test fun cancellingTheMemoGestureKeepsItsFilterAndScrollWhileCompletingItOpensTheLibrary() {
        ui.onNodeWithContentDescription("Rechercher un mémo").performClick()
        ui.onNode(hasSetTextAction()).performTextInput("a")
        ui.onNode(verticalScroll).performScrollToNode(hasTestTag("memo-row-transmission-lines"))
        val libraryBefore = scrollPosition()
        assertTrue("The library must actually be scrolled before opening the fiche.", libraryBefore > 0f)
        ui.onNodeWithTag("memo-row-nato").assertDoesNotExist()
        // Restored/deep-linked details can point at a row outside the library's saved viewport.
        ui.runOnIdle { model.resource = model.content!!.references.first { it.id == "nato" } }
        val fiche = model.resource!!
        ui.onNodeWithContentDescription("Filtrer cette fiche").performClick()
        ui.onNode(hasSetTextAction()).performTextInput("a")
        ui.onNode(verticalScroll).performScrollToNode(hasText("Yankee"))
        ui.onNodeWithText("Yankee").assertIsDisplayed()
        val before = scrollPosition()
        assertTrue("The fiche must actually be scrolled before the gesture.", before > 0f)

        startAndProgress(.6f)
        assertEquals("A preview must preserve the library's saved viewport.", libraryBefore, libraryScrollPosition(), .001f)
        ui.onNodeWithTag("memo-row-nato").assertDoesNotExist()
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        ui.waitForIdle()
        ui.runOnIdle { assertEquals("nato", model.resource?.id) }
        ui.onNode(hasSetTextAction()).assertTextContains("a")
        ui.onNodeWithText("Yankee").assertIsDisplayed()
        assertEquals(before, scrollPosition(), .001f)

        startAndProgress(.45f)
        assertEquals("Cancellation must preserve the next preview's original viewport.", libraryBefore, libraryScrollPosition(), .001f)
        completeAndInspectAnimation(
            "memo-offscreen-row-commit",
            assertPending = { assertSame("Finding the offscreen row must keep the fiche alive.", fiche, model.resource) },
            assertCompleted = { assertNull(model.resource); assertEquals("resources", model.route) },
            flattenIntoRow = true,
            targetAfterPreparation = { memoRowBounds("nato") },
        )
        ui.onNodeWithTag("memo-library-title").assertIsDisplayed()
        ui.onNodeWithTag("memo-row-nato").assertIsDisplayed()
        ui.onNode(hasSetTextAction()).assertTextContains("a")
        ui.onNodeWithContentDescription("Retour aux mémos").assertDoesNotExist()
    }

    @Test fun directlyOpenedFicheExcludedByTheSavedFilterKeepsItOnCancelAndRevealsItsOwnRowOnCommit() {
        ui.onNodeWithContentDescription("Rechercher un mémo").performClick()
        ui.onNode(hasSetTextAction()).performTextInput("Morse")
        ui.onNodeWithTag("memo-row-nato").assertDoesNotExist()
        // Compare the saved viewport with the same keyboard/inset geometry after returning.
        ui.runOnIdle {
            WindowInsetsControllerCompat(ui.activity.window, ui.activity.window.decorView)
                .hide(WindowInsetsCompat.Type.ime())
        }
        ui.waitUntil(5000) {
            ViewCompat.getRootWindowInsets(ui.activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == false
        }
        ui.waitForIdle()
        ui.onNode(verticalScroll).performScrollToNode(hasTestTag("memo-row-radio-regulations"))
        ui.onNodeWithTag("memo-row-radio-regulations").assertIsDisplayed()
        val libraryBefore = libraryScrollPosition()
        val fiche = model.content!!.references.first { it.id == "nato" }
        ui.runOnIdle { model.resource = fiche }

        startAndProgress(.6f)
        ui.mainClock.autoAdvance = false
        try {
            ui.runOnIdle { ui.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
            ui.mainClock.advanceTimeBy(240)
            ui.runOnIdle {
                assertSame("Cancelling a return must keep the directly opened fiche alive.", fiche, model.resource)
            }
            ui.onNodeWithTag("memo-library-list", useUnmergedTree = true).assertDoesNotExist()
        } finally {
            ui.mainClock.autoAdvance = true
        }
        // Inspect the original library, rather than the independent unfiltered preview.
        ui.onNodeWithContentDescription("Retour aux mémos").performClick()
        ui.onNode(hasSetTextAction()).assertTextContains("Morse")
        ui.onNodeWithTag("memo-row-radio-regulations").assertIsDisplayed()
        assertEquals("Cancelling must preserve the saved filtered scroll.", libraryBefore, libraryScrollPosition(), .001f)
        ui.onNodeWithTag("memo-row-nato").assertDoesNotExist()

        ui.runOnIdle { model.resource = fiche }
        startAndProgress(.45f)
        completeAndInspectAnimation(
            "memo-excluded-filter-row-commit",
            assertPending = { assertSame("The exact fiche must remain open while its row is revealed.", fiche, model.resource) },
            assertCompleted = { assertNull(model.resource); assertEquals("resources", model.route) },
            flattenIntoRow = true,
            targetAfterPreparation = { memoRowBounds("nato") },
        )
        ui.onNodeWithTag("memo-row-nato").assertIsDisplayed()
        ui.onNodeWithTag("memo-library-title").assertIsDisplayed()
        assertEquals("", ui.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        navigationTab("Mémo").assertIsSelected()
    }

    @Test fun completingTheMemoGestureShrinksThePageIntoItsOwnTabBeforeNavigationAndKeepsTheFilter() {
        ui.onNodeWithContentDescription("Rechercher un mémo").performClick()
        ui.onNode(hasSetTextAction()).performTextInput("Morse")
        listOf(BackEventCompat.EDGE_LEFT, BackEventCompat.EDGE_RIGHT).forEach { edge ->
            val target = navigationIconBounds("resources")
            ui.onNode(hasSetTextAction()).assertTextContains("Morse")
            startAndProgress(.45f, edge)
            val edgeName = if (edge == BackEventCompat.EDGE_LEFT) "left" else "right"
            completeAndInspectAnimation(
                "memo-commit-$edgeName", target,
                assertPending = {
                    assertEquals("The source route must survive until the visual return finishes.", "resources", model.route)
                },
                assertCompleted = { assertEquals("path", model.route) },
            )
            ui.onNodeWithText("HAMIGO").assertIsDisplayed()
            navigationTab("Parcours").assertIsSelected()
            navigationTab("Mémo").performClick()
            ui.onNode(hasSetTextAction()).assertTextContains("Morse")
            navigationTab("Mémo").assertIsSelected()
        }
    }

    @Test fun completingDetailGesturesKeepsTheirContentUntilThePageReachesItsParentDestination() {
        ui.onNode(verticalScroll).performScrollToNode(hasTestTag("memo-row-nato"))
        ui.onNodeWithTag("memo-row-nato").performClick()
        val fiche = model.resource!!
        startAndProgress(.45f)
        val memoTarget = memoRowBounds("nato")
        completeAndInspectAnimation(
            "memo-detail-commit", memoTarget,
            assertPending = { assertSame("The fiche must stay alive while its page flattens into its own row.", fiche, model.resource) },
            assertCompleted = { assertNull(model.resource); assertEquals("resources", model.route) },
            flattenIntoRow = true,
        )
        ui.onNodeWithTag("memo-library-title").assertIsDisplayed()
        ui.onNodeWithTag("memo-row-nato").assertIsDisplayed()

        ui.runOnIdle { model.route = "settings" }
        startAndProgress(.45f, BackEventCompat.EDGE_RIGHT)
        val profileTarget = ui.onNodeWithContentDescription("Réglages").fetchSemanticsNode().boundsInRoot
        completeAndInspectAnimation(
            "settings-commit", profileTarget,
            assertPending = { assertEquals("settings", model.route) },
            assertCompleted = { assertEquals("profile", model.route) },
        )
        navigationTab("Moi").assertIsSelected()

        val pathTarget = navigationIconBounds("path")
        ui.runOnIdle { model.route = "path"; model.startLesson(model.content!!.lessons.first()) }
        val lesson = model.lesson!!
        startAndProgress(.45f)
        completeAndInspectAnimation(
            "lesson-commit", pathTarget,
            assertPending = { assertSame("The lesson must stay alive while its page shrinks.", lesson, model.lesson) },
            assertCompleted = { assertNull(model.lesson); assertEquals("path", model.route) },
        )
        navigationTab("Parcours").assertIsSelected()
        ui.onNodeWithText("HAMIGO").assertIsDisplayed()
    }

    @Test fun interruptingTheReturnWithAnotherTabOrGestureNeverCommitsTheOldDestination() {
        ui.onNodeWithContentDescription("Rechercher un mémo").performClick()
        ui.onNode(hasSetTextAction()).performTextInput("Morse")
        startAndProgress(.45f)
        ui.mainClock.autoAdvance = false
        try {
            ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
            ui.mainClock.advanceTimeBy(64)
            ui.runOnIdle { assertEquals("resources", model.route) }
            navigationTab("Équipe").performClick()
            ui.mainClock.advanceTimeBy(336)
            ui.runOnIdle { assertEquals("The old Back completion must not override a newer tab choice.", "friends", model.route) }
            navigationTab("Équipe").assertIsSelected()
            ui.onNodeWithText("HAMIGO").assertDoesNotExist()

            navigationTab("Mémo").performClick()
            ui.mainClock.advanceTimeByFrame()
            ui.onNode(hasSetTextAction()).assertTextContains("Morse")
            startAndProgress(.45f)
            ui.mainClock.advanceTimeByFrame()
            ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
            ui.mainClock.advanceTimeBy(64)
            ui.runOnIdle { assertEquals("resources", model.route) }
            startAndProgress(.45f)
            // Pass the interrupted animation's old deadline while the new gesture is still open.
            ui.mainClock.advanceTimeBy(336)
            ui.runOnIdle { assertEquals("A newer gesture must keep the old return from navigating.", "resources", model.route) }
            ui.onNodeWithText("HAMIGO").assertIsDisplayed()
            ui.onNode(hasSetTextAction()).assertTextContains("Morse")
            ui.runOnIdle { ui.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
            ui.mainClock.advanceTimeBy(240)
            ui.runOnIdle { assertEquals("resources", model.route) }
            navigationTab("Mémo").assertIsSelected()
            ui.onNodeWithText("HAMIGO").assertDoesNotExist()
            ui.onNode(hasSetTextAction()).assertTextContains("Morse")
        } finally {
            ui.mainClock.autoAdvance = true
        }
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
        val fixedPage = foregroundBounds()

        startAndProgress(.5f)
        ui.onNodeWithTag("back-destination-resources").assertDoesNotExist()
        assertEquals("A session needing confirmation must not move with predictive Back.",fixedPage,foregroundBounds())
        ui.onNodeWithTag("back-foreground",useUnmergedTree=true).assertIsDisplayed()
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

    @Test fun flashcardsFlipBothWaysAndKeepRatingButtonsUntilOneRatingAdvances() {
        val cards=listOf(
            Question("flip-one","Question recto",listOf("Réponse verso"),0,"Explication verso",kind="flash"),
            Question("flip-two","Deuxième recto",listOf("Deuxième verso"),0,"",kind="flash"),
        )
        ui.runOnIdle { model.startQuestions("Cartes",cards) }
        ui.onNodeWithText("Question recto").assertIsDisplayed()
        ui.onNodeWithText("Réponse verso").assertDoesNotExist()
        ui.onNodeWithText("À revoir").assertDoesNotExist()
        ui.onNodeWithText("Retourner la carte").performClick()
        ui.onNodeWithText("Réponse verso").assertIsDisplayed()
        ui.onNodeWithText("Question recto").assertDoesNotExist()
        capture("flashcard-answer")
        ui.mainClock.autoAdvance=false
        try {
            ui.onNodeWithTag("flashcard").performClick()
            ui.mainClock.advanceTimeBy(96)
            capture("flashcard-flip-middle")
            ui.mainClock.advanceTimeBy(320)
        } finally { ui.mainClock.autoAdvance=true }
        ui.onNodeWithText("Question recto").assertIsDisplayed()
        ui.onNodeWithText("Réponse verso").assertDoesNotExist()
        listOf("À revoir","Difficile","Bien","Facile").forEach { ui.onNodeWithText(it).assertIsDisplayed() }
        ui.runOnIdle { assertEquals(0,model.session!!.index);assertTrue(model.session!!.responses.isEmpty()) }
        capture("flashcard-question-after-reveal")
        ui.onNodeWithText("Bien").performClick()
        ui.runOnIdle { assertEquals(1,model.session!!.index);assertEquals(1,model.session!!.responses.size) }
        ui.onNodeWithText("Deuxième recto").assertIsDisplayed()
        ui.onNodeWithText("À revoir").assertDoesNotExist()
    }

    @Test fun completedSessionsReturnToTheirStartingTabAndCanCancelPredictiveBack() {
        listOf("path" to "Parcours", "practice" to "Défis").forEach { (route, label) ->
            ui.runOnIdle {
                model.route = route
                model.startQuestions("Retour après séance", listOf(Question("finished-$route", "Témoin", listOf("Oui", "Non"), 0, "")))
                model.answer(true); model.next()
                assertTrue(model.session!!.done)
            }
            val finished = model.session!!
            startAndProgress(.5f)
            ui.onNodeWithTag("back-destination-$route").assertIsDisplayed()
            ui.onNodeWithText("Une pause radio ?").assertDoesNotExist()
            capture("completed-$route-preview")
            ui.runOnIdle { ui.activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
            ui.waitForIdle()
            ui.runOnIdle { assertSame(finished, model.session); assertEquals(route, model.route) }
            startAndProgress(.8f)
            ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
            ui.waitForIdle()
            ui.runOnIdle { assertNull(model.session); assertEquals(route, model.route) }
            ui.onNodeWithText("Une pause radio ?").assertDoesNotExist()
            navigationTab(label).assertIsSelected()
            capture("completed-$route-return")
        }
    }

    private fun startAndProgress(progress: Float, edge: Int = BackEventCompat.EDGE_LEFT) {
        val start = if (edge == BackEventCompat.EDGE_LEFT) 0f else ui.activity.resources.displayMetrics.widthPixels.toFloat()
        val direction = if (edge == BackEventCompat.EDGE_LEFT) 1f else -1f
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(start, 200f, 0f, edge)) }
        ui.runOnIdle { ui.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(start + direction * 120f, 200f, progress, edge)) }
        ui.waitForIdle()
    }
    private fun navigationTab(label: String) = ui.onNode(
        hasText(label) and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
    )
    private fun navigationIconBounds(route: String): Rect =
        ui.onNodeWithTag("navigation-icon-$route", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun foregroundBounds(): Rect =
        ui.onNodeWithTag("back-foreground", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun memoRowBounds(id: String): Rect =
        ui.onNodeWithTag("memo-row-$id", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    private fun completeAndInspectAnimation(
        name: String,
        target: Rect? = null,
        assertPending: () -> Unit,
        assertCompleted: () -> Unit,
        flattenIntoRow: Boolean = false,
        targetAfterPreparation: (() -> Rect)? = null,
    ) {
        val released = foregroundBounds()
        ui.mainClock.autoAdvance = false
        try {
            ui.runOnIdle { ui.activity.onBackPressedDispatcher.onBackPressed() }
            ui.runOnIdle { assertPending() }
            ui.onNodeWithTag("back-foreground", useUnmergedTree = true).assertExists()

            // Freeze the real Compose animation halfway through the short return, before model commit.
            ui.mainClock.advanceTimeBy(112)
            ui.runOnIdle { assertPending() }
            val middle = foregroundBounds()
            val destination = targetAfterPreparation?.invoke() ?: requireNotNull(target)
            if (flattenIntoRow) {
                assertTrue(
                    "The fiche must flatten vertically into its row while retaining most of its width.",
                    middle.width / released.width > middle.height / released.height + .3f,
                )
            } else {
                assertTrue("The released page must visibly shrink before disappearing.", middle.width < released.width * .8f)
            }
            assertTrue("The released page must visibly lose height before disappearing.", middle.height < released.height * .8f)
            assertTrue(
                "The page must travel toward its parent destination, rather than merely fade in place.",
                distanceToTarget(middle, destination) < distanceToTarget(released, destination) * .8,
            )
            capture("$name-middle")

            // An offscreen row needs one or two layout frames before the same return animation starts.
            ui.mainClock.advanceTimeBy(if (targetAfterPreparation == null) 160 else 192)
            ui.runOnIdle { assertCompleted() }
            // Keep the navigation deadline assertion above, then render the adopted list position.
            repeat(2) { ui.mainClock.advanceTimeByFrame() }
            ui.waitForIdle()
            capture("$name-finished")
        } finally {
            ui.mainClock.autoAdvance = true
        }
        ui.waitForIdle()
    }

    private fun distanceToTarget(page: Rect, target: Rect): Double = hypot(
        (page.center.x - target.center.x).toDouble(), (page.center.y - target.center.y).toDouble(),
    )

    private fun capture(name: String) {
        val directory = requireNotNull(context.getExternalFilesDir("navigation-audit"))
        check(directory.isDirectory || directory.mkdirs())
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
    }
    private fun scrollPosition(): Float = ui.onNode(verticalScroll).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun libraryScrollPosition(): Float = ui.onNodeWithTag("memo-library-list", useUnmergedTree = true)
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
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
