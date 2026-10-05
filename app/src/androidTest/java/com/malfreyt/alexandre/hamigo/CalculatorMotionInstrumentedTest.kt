package com.malfreyt.alexandre.hamigo

import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real dialog interactions with a paused animation clock; no application preferences are touched. */
@RunWith(AndroidJUnit4::class)
class CalculatorMotionInstrumentedTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()

    private class Harness(initialOpen: Boolean) {
        var open by mutableStateOf(initialOpen)
        var dismissCount = 0
        val inserted = mutableListOf<Double>()
    }

    private fun mount(initialOpen: Boolean = false): Harness {
        val harness = Harness(initialOpen)
        ui.setContent {
            HamigoTheme {
                var anchor by remember { mutableStateOf<Rect?>(null) }
                val view=LocalView.current
                Box(Modifier.fillMaxSize()) {
                    Button({ harness.open = true },Modifier.align(Alignment.BottomEnd).padding(24.dp).size(56.dp).testTag("calculator-origin").onGloballyPositioned { anchor=it.screenBounds(view) }) { Text("Ouvrir") }
                    FloatingCalculator(harness.open, onDismiss = {
                        harness.dismissCount++
                        harness.open = false
                    }, onInsertResult = { harness.inserted += it }, anchorBounds=anchor)
                }
            }
        }
        return harness
    }

    @Test fun openingAndClosingAreVisibleTransitionsAndReopeningRetainsTheCalculation() {
        val harness = mount()
        ui.mainClock.autoAdvance = false
        try {
            ui.runOnIdle { harness.open = true }
            awaitOpeningSurface()
            surface().assertExists()
            val opening = surface().fetchSemanticsNode().boundsInRoot
            ui.mainClock.advanceTimeBy(96)
            val openingMiddle = surface().fetchSemanticsNode().boundsInRoot
            assertTrue("Opening should visibly grow the floating panel.", openingMiddle.width > opening.width)
            capture("calculator-opening-middle")
            ui.mainClock.advanceTimeBy(160)
            surface().assertIsDisplayed()
            val opened = surface().fetchSemanticsNode().boundsInRoot
            assertTrue(opened.width >= openingMiddle.width)

            // Normal input/clicks must compose before freezing only the exit animation.
            ui.mainClock.autoAdvance = true
            expression().performTextInput("20")
            ui.onNode(hasText("=") and hasClickAction()).performClick()
            ui.onNodeWithTag("calculator-result").assertTextEquals("20")
            ui.onNodeWithText("DEG").performClick()
            ui.onNode(hasText("=") and hasClickAction()).performClick()
            ui.mainClock.autoAdvance = false
            ui.onNodeWithContentDescription("Fermer la calculatrice").performClick()
            ui.mainClock.advanceTimeBy(96)
            ui.runOnIdle { assertFalse(harness.open); assertEquals(1, harness.dismissCount) }
            surface().assertExists()
            expression().assertTextContains("20")
            ui.onNodeWithTag("calculator-result").assertTextEquals("20")
            val closingMiddle = surface().fetchSemanticsNode().boundsInRoot
            assertTrue("The content must shrink visibly during its exit.", closingMiddle.width < opened.width)
            val origin=ui.onNodeWithTag("calculator-origin",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
            assertTrue("Closing should travel towards the actual opening button.",
                (closingMiddle.center-origin.center).getDistance() < (opened.center-origin.center).getDistance()*.8f)
            capture("calculator-closing-middle")
            ui.mainClock.advanceTimeBy(160)
            surface().assertDoesNotExist()

            ui.runOnIdle { harness.open = true }
            ui.mainClock.advanceTimeBy(256)
            surface().assertIsDisplayed()
            expression().assertTextContains("20")
            ui.onNodeWithTag("calculator-result").assertTextEquals("20")
            ui.onNodeWithText("RAD").assertIsDisplayed()
        } finally {
            ui.mainClock.autoAdvance = true
        }
    }

    @Test fun aRapidReopenCancelsTheOldExitAndTheResultCanOnlyBeInsertedOnce() {
        val harness = mount(initialOpen = true)
        expression().performTextInput("2+3")
        ui.onNode(hasText("=") and hasClickAction()).performClick()
        ui.mainClock.autoAdvance = false
        try {
            ui.onNodeWithContentDescription("Fermer la calculatrice").performClick()
            ui.mainClock.advanceTimeBy(64)
            surface().assertExists()
            ui.runOnIdle { harness.open = true }
            ui.mainClock.advanceTimeBy(336)
            ui.runOnIdle { assertTrue("An old closing animation must not dismiss the reopened dialog.", harness.open) }
            surface().assertIsDisplayed()
            expression().assertTextContains("2+3")
            ui.onNodeWithTag("calculator-result").assertTextEquals("5")

            val insert = ui.onNodeWithText("Utiliser dans ma réponse").performScrollTo()
                .fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
            // Deliver two clicks in one frame, before recomposition can disable the button.
            ui.runOnIdle { insert(); insert() }
            ui.runOnIdle {
                assertEquals(listOf(5.0), harness.inserted)
                assertEquals(2, harness.dismissCount)
                assertFalse(harness.open)
            }
            ui.mainClock.advanceTimeBy(96)
            surface().assertExists()
            ui.mainClock.advanceTimeBy(160)
            surface().assertDoesNotExist()
        } finally {
            ui.mainClock.autoAdvance = true
        }
    }

    @Test fun systemBackAndAnOutsideTapUseTheSameAnimatedExit() {
        val harness = mount(initialOpen = true)
        ui.mainClock.autoAdvance = false
        try {
            Espresso.pressBack()
            ui.mainClock.advanceTimeBy(96)
            ui.runOnIdle { assertFalse(harness.open); assertFalse(ui.activity.isFinishing) }
            surface().assertExists()
            ui.mainClock.advanceTimeBy(160)
            surface().assertDoesNotExist()

            ui.runOnIdle { harness.open = true }
            ui.mainClock.advanceTimeBy(256)
            surface().assertIsDisplayed()
            tapAboveTheFloatingPanel()
            ui.mainClock.advanceTimeBy(96)
            ui.runOnIdle { assertFalse("A tap on the backdrop should request dismissal.", harness.open) }
            surface().assertExists()
            ui.mainClock.advanceTimeBy(160)
            surface().assertDoesNotExist()
            ui.runOnIdle { assertEquals(2, harness.dismissCount) }
        } finally {
            ui.mainClock.autoAdvance = true
        }
    }

    private fun surface() = ui.onNodeWithTag("calculator-surface", useUnmergedTree = true)
    private fun expression() = ui.onNodeWithTag("calculator-expression")

    private fun awaitOpeningSurface() {
        // LaunchedEffect retains the dialog, then its new window joins the next composition.
        // Observe that attachment while staying within the first 64 ms of the 200 ms opening.
        repeat(4) {
            ui.mainClock.advanceTimeByFrame()
            if (ui.onAllNodesWithTag("calculator-surface", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) return
        }
        surface().assertExists()
    }

    private fun tapAboveTheFloatingPanel() {
        val visible = android.graphics.Rect()
        var statusBarTop = 0
        ui.runOnIdle {
            val decor = ui.activity.window.decorView
            decor.getWindowVisibleDisplayFrame(visible)
            val insets = decor.rootWindowInsets
            @Suppress("DEPRECATION")
            statusBarTop = if (Build.VERSION.SDK_INT >= 30) insets?.getInsets(WindowInsets.Type.statusBars())?.top ?: 0
                else insets?.stableInsetTop ?: 0
        }
        val x = visible.centerX().toFloat()
        val y = maxOf(visible.top, statusBarTop) + 8f * ui.density.density
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP).forEach { action ->
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(automation.injectInputEvent(event, true)) }
            finally { event.recycle() }
        }
        ui.waitForIdle()
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = requireNotNull(context.getExternalFilesDir("calculator-audit"))
        check(directory.isDirectory || directory.mkdirs())
        val bitmap = surface().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use {
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
