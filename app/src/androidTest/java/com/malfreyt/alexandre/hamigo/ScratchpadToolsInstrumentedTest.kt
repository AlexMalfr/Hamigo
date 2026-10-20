package com.malfreyt.alexandre.hamigo

import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.width
import androidx.compose.ui.unit.height
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import java.io.File

/** Native pen/scroll input and production popup; no progress, account or preferences. */
@RunWith(AndroidJUnit4::class)
class ScratchpadToolsInstrumentedTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Before fun prepareWindow() {check(Build.HARDWARE in listOf("ranchu","goldfish"));ui.prepareTestWindow()}
    private class Harness {
        val state=ScratchpadState()
        var open by mutableStateOf(true)
        var dismissals=0
    }
    private fun mount():Harness=Harness().also {h->
        ui.setContent {HamigoTheme {
            FloatingScratchpad(h.open,{h.open=false;h.dismissals++},h.state,null)
        }}
        ui.onNodeWithTag("scratchpad-ink").assertIsDisplayed()
    }
    private fun ink():ScratchpadInkView {
        fun find(view:View):ScratchpadInkView? {
            if(view is ScratchpadInkView)return view
            if(view is ViewGroup)for(index in 0 until view.childCount)find(view.getChildAt(index))?.let {return it}
            return null
        }
        return WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::find) ?: error("Missing ink sheet")
    }
    private fun draw()=ui.onNodeWithTag("scratchpad-ink").performTouchInput {
        swipe(Offset(width*.2f,height*.3f),Offset(width*.7f,height*.45f),240)
    }
    private fun capture(name:String) {
        ui.awaitForegroundFrames()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val directory=File(instrumentation.targetContext.getExternalFilesDir(null),"scratchpad-tools").apply {mkdirs()}
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot()) {"No screenshot returned for the focused window"}
        try {File(directory,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}finally {bitmap.recycle()}
    }
    @Test fun undoRedoAndConfirmedClearPreserveBothTheDrawingAndTypedNotes() {
        val h=mount()
        draw()
        ui.runOnIdle {assertEquals(1,h.state.strokes.size)}
        visibleTool("Annuler la dernière modification").performClick()
        ui.runOnIdle {assertTrue(h.state.strokes.isEmpty())}
        visibleTool("Rétablir").performClick()
        ui.runOnIdle {assertEquals(1,h.state.strokes.size)}
        visibleTool("Texte").performClick()
        ui.onNodeWithTag("scratchpad-text").performTextInput("U = R × I")
        visibleTool("Effacer le brouillon").performClick()
        ui.onNodeWithText("Annuler",substring=false).performClick()
        ui.runOnIdle {assertEquals("U = R × I",h.state.text.text);assertEquals(1,h.state.strokes.size)}
        visibleTool("Effacer le brouillon").performClick()
        ui.onNodeWithText("Tout effacer",substring=false).performClick()
        ui.runOnIdle {assertTrue(h.state.text.text.isEmpty());assertTrue(h.state.strokes.isEmpty())}
        visibleTool("Annuler la dernière modification").performClick()
        ui.runOnIdle {assertEquals("U = R × I",h.state.text.text);assertEquals(1,h.state.strokes.size)}
        visibleTool("Dessin").performClick()
        ui.runOnUiThread {WindowInspector.getGlobalWindowViews().forEach {it.windowInsetsController?.hide(WindowInsets.Type.ime())}}
        ui.waitUntil(10_000){WindowInspector.getGlobalWindowViews().none {it.rootWindowInsets?.isVisible(WindowInsets.Type.ime())==true}}
        capture("history-restored")
    }
    private fun visibleTool(description:String):SemanticsNodeInteraction {
        if(description.startsWith("Épaisseur ")&&ui.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isEmpty()) {
            ui.onNodeWithContentDescription("Épaisseur du trait").performClick()
        }
        if(description.startsWith("Couleur ")&&ui.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isEmpty()) {
            ui.onNodeWithContentDescription("Autres couleurs").performClick()
            capture("palette-overflow-open")
        }
        val node=ui.onNodeWithContentDescription(description)
        if(!node.isDisplayed())node.performScrollTo()
        return node.assertIsDisplayed()
    }
    @Test fun colorThicknessEraserAndLongSheetScrollingKeepDistinctInkEdits() {
        val h=mount()
        ui.onNodeWithContentDescription("Couleur et épaisseur").assertDoesNotExist()
        ui.onNodeWithContentDescription("Déplacer la feuille").assertDoesNotExist()
        visibleTool("Épaisseur Épaisse").performClick()
        visibleTool("Couleur Corail").performClick()
        draw()
        ui.runOnIdle {assertEquals(5f,h.state.strokes.single().widthDp,.001f);assertEquals(0xFFB54B3D.toInt(),h.state.strokes.single().color)}
        visibleTool("Gomme").performClick()
        draw()
        ui.runOnIdle {assertTrue(h.state.strokes.isEmpty())}
        visibleTool("Annuler la dernière modification").performClick()
        visibleTool("Gomme").performClick()
        ui.onNodeWithTag("scratchpad-ink").performTouchInput {
            down(0,Offset(width*.45f,height*.8f));down(1,Offset(width*.65f,height*.8f))
            moveTo(0,Offset(width*.45f,height*.2f));moveTo(1,Offset(width*.65f,height*.2f))
            up(0);up(1)
        }
        val firstY=h.state.strokes.single().points.last().y
        ui.runOnIdle {assertTrue(h.state.paperScroll>0f);assertEquals(1,h.state.strokes.size)}
        draw()
        ui.runOnIdle {
            assertEquals(2,h.state.strokes.size)
            assertTrue(h.state.strokes.last().points.first().y>firstY)
            val old=h.state.paperScroll
            assertTrue(ink().performAccessibilityAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,null))
            assertTrue(h.state.paperScroll>old)
        }
        var thumb=android.graphics.RectF()
        var scrollBeforeBar=0f
        ui.runOnIdle {thumb=ink().scrollbarThumbBounds();scrollBeforeBar=h.state.paperScroll}
        ui.onNodeWithTag("scratchpad-ink").performTouchInput {
            swipe(Offset(thumb.centerX(),thumb.centerY()),Offset(thumb.centerX(),height*.94f),240)
        }
        ui.runOnIdle {
            assertTrue("The visible scrollbar itself scrolls the sheet.",h.state.paperScroll>scrollBeforeBar)
            assertEquals("Dragging the scrollbar must not draw or erase.",2,h.state.strokes.size)
        }
        capture("long-sheet")
    }
    @Test fun erasureIsImmediateAndTheEntireSweepIsASingleUndoRedoEntry() {
        val h=mount()
        seedTwoStrokes(h)
        ui.runOnIdle {
            val view=ink()
            h.state.eraser=true
            sendContact(view,MotionEvent.ACTION_DOWN,.3f,.25f)
            assertEquals("The first stroke disappears on contact, before a finger is lifted.",1,h.state.strokes.size)
            view.refresh()
            sendContact(view,MotionEvent.ACTION_MOVE,.3f,.65f)
            assertTrue("The second stroke disappears while moving, still before ACTION_UP.",h.state.strokes.isEmpty())
            sendContact(view,MotionEvent.ACTION_UP,.3f,.65f)
        }
        visibleTool("Annuler la dernière modification").performClick()
        ui.runOnIdle {assertEquals("One undo restores the entire eraser sweep.",2,h.state.strokes.size)}
        visibleTool("Rétablir").performClick()
        ui.runOnIdle {assertTrue(h.state.strokes.isEmpty())}
        visibleTool("Annuler la dernière modification").performClick()
        visibleTool("Annuler la dernière modification").performClick()
        ui.runOnIdle {assertTrue("A second undo reaches the edit before this sweep, not an intermediate eraser sample.",h.state.strokes.isEmpty())}
    }
    @Test fun cancelingALiveEraserContactRestoresTheDocumentWithoutAnUndoEntry() {
        val h=mount()
        seedTwoStrokes(h)
        ui.runOnIdle {
            val view=ink()
            h.state.eraser=true
            sendContact(view,MotionEvent.ACTION_DOWN,.3f,.25f)
            assertEquals(1,h.state.strokes.size)
            sendContact(view,MotionEvent.ACTION_MOVE,.3f,.65f)
            assertTrue(h.state.strokes.isEmpty())
            sendContact(view,MotionEvent.ACTION_CANCEL,.3f,.65f)
            assertEquals("A canceled contact restores both strokes.",2,h.state.strokes.size)
        }
        visibleTool("Annuler la dernière modification").performClick()
        ui.runOnIdle {assertTrue("Canceling added no undo entry; undo removes the original seeded edit.",h.state.strokes.isEmpty())}
    }
    private fun seedTwoStrokes(h:Harness) {
        ui.runOnIdle {
            val view=ink()
            val paperHeight=(view.width*6f).coerceAtLeast(view.height.toFloat())
            h.state.replaceInk(listOf(.25f,.65f).map {y->
                InkStroke(listOf(InkPoint(.2f,y*view.height/paperHeight,1f),InkPoint(.5f,y*view.height/paperHeight,1f)))
            })
        }
        ui.waitForIdle()
    }
    private fun sendContact(view:ScratchpadInkView,action:Int,x:Float,y:Float) {
        val time=SystemClock.uptimeMillis()
        val event=MotionEvent.obtain(time,time,action,x*view.width,y*view.height,0)
        try {view.dispatchTouchEvent(event)}finally {event.recycle()}
    }
    @Test fun portraitSelectorsAreInlineAndStylusOnlyOccupiesItsOwnRow() {
        val window=ui.activity.resources.displayMetrics
        assumeTrue(window.heightPixels>window.widthPixels)
        mount()
        val thickness=ui.onNodeWithTag("scratchpad-widths").assertIsDisplayed().getUnclippedBoundsInRoot()
        val color=ui.onNodeWithTag("scratchpad-colors").assertIsDisplayed().getUnclippedBoundsInRoot()
        val stylus=ui.onNodeWithContentDescription("Stylet seul").assertIsDisplayed().getUnclippedBoundsInRoot()
        val sheet=ui.onNodeWithTag("scratchpad-ink").getUnclippedBoundsInRoot()
        assertTrue("Stylus-only stays on the upper action row.",stylus.bottom<=minOf(thickness.top,color.top))
        assertEquals("Thickness and color share a single aligned row.",thickness.top.value,color.top.value,.01f)
        assertTrue("No control intrudes onto the paper.",stylus.bottom<=sheet.top)
        assertTrue("Even the final color remains a real 48 dp touch target: $color",color.width.value>=47.99f&&color.height.value>=47.99f)
        ui.onNodeWithContentDescription("Épaisseur Fine").performClick().assertIsSelected()
        visibleTool("Couleur Violet").performClick()
        ui.onNodeWithTag("scratchpad-colors").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Violet"))
        capture("inline-tools")
    }
    @Test fun theLowerOutsideAreaDoesNotDismissAndReopeningRetainsTheSheet() {
        val h=mount()
        draw()
        ui.runOnIdle {ink().scrollPaperTo(1.25f)}
        ui.onNodeWithTag("scratchpad-backdrop").performTouchInput {click(Offset(2f,height*.8f))}
        ui.runOnIdle {assertTrue(h.open);assertEquals(0,h.dismissals)}
        ui.onNodeWithTag("scratchpad-backdrop").performTouchInput {click(Offset(2f,height*.2f))}
        ui.runOnIdle {assertFalse(h.open);assertEquals(1,h.dismissals)}
        ui.waitForIdle()
        ui.runOnIdle {h.open=true}
        ui.onNodeWithTag("scratchpad-ink").assertIsDisplayed()
        ui.runOnIdle {assertEquals(1,h.state.strokes.size);assertEquals(1.25f,h.state.paperScroll,.001f)}
        capture("reopened-sheet")
    }
    @Test fun theLastPortraitToolRemainsReachableInANarrowWindow() {
        val metrics=ui.activity.resources.displayMetrics
        assumeTrue(metrics.heightPixels>metrics.widthPixels)
        // Run with a real 320 dp emulator viewport as well: Dialog owns its own density.
        val h=mount()
        ui.runOnIdle {h.state.replaceInk(listOf(InkStroke(listOf(InkPoint(.2f,.1f,1f),InkPoint(.5f,.15f,1f)))))}
        ui.onNodeWithContentDescription("Effacer le brouillon").assertIsDisplayed()
        val clear=ui.onNodeWithContentDescription("Effacer le brouillon").getUnclippedBoundsInRoot()
        val surface=ui.onNodeWithTag("scratchpad-surface",useUnmergedTree=true).getUnclippedBoundsInRoot()
        assertTrue("The entire final tool is reachable, not clipped at the edge.",clear.left>=surface.left&&clear.right<=surface.right)
        capture("narrow-tools")
        visibleTool("Effacer le brouillon").performClick()
        ui.onNodeWithText("Tout effacer",substring=false).assertIsDisplayed()
        ui.onNodeWithText("Annuler",substring=false).performClick()
        ui.runOnIdle {assertEquals(1,h.state.strokes.size)}
    }
    @Test fun landscapeToolsCanChangeSidesWithoutChangingTheSheet() {
        val window=ui.activity.resources.displayMetrics
        assumeTrue(window.widthPixels>window.heightPixels)
        val h=mount()
        draw()
        val original=ui.onNodeWithTag("scratchpad-tools").getUnclippedBoundsInRoot()
        val originalSheet=ui.onNodeWithTag("scratchpad-ink").getUnclippedBoundsInRoot()
        assertTrue(original.right<=originalSheet.left)
        val close=ui.onNodeWithContentDescription("Fermer le brouillon").getUnclippedBoundsInRoot()
        val side=ui.onNodeWithContentDescription("Changer le côté des outils").getUnclippedBoundsInRoot()
        val mode=ui.onNodeWithTag("scratchpad-mode").getUnclippedBoundsInRoot()
        val stylus=ui.onNodeWithContentDescription("Stylet seul").getUnclippedBoundsInRoot()
        assertTrue("The full stylus-only control stays above drawing tools.",stylus.top>=mode.bottom)
        ui.onNodeWithText("Stylet uniquement").assertIsDisplayed()
        ui.onNodeWithContentDescription("Stylet seul").performClick().assertIsSelected()
        ui.onNodeWithContentDescription("Stylet seul").performClick().assertIsNotSelected()
        visibleTool("Effacer le brouillon").performClick()
        ui.onNodeWithText("Annuler",substring=false).performClick()
        ui.runOnIdle {assertEquals("Canceling clear preserves the sheet.",1,h.state.strokes.size)}
        assertEquals("The paper starts at the same top as the sidebar's close button.",originalSheet.top.value,close.top.value,1f)
        assertEquals("The side switch is at the foot of the sidebar.",originalSheet.bottom.value,side.bottom.value,1f)
        ui.onNodeWithText("Brouillon",substring=false).assertDoesNotExist()
        val options=ui.onNodeWithTag("scratchpad-tool-options").getUnclippedBoundsInRoot()
        val palette=ui.onNodeWithTag("scratchpad-colors").getUnclippedBoundsInRoot()
        assertTrue("The entire palette fits in the landscape toolbar",palette.top>=options.top&&palette.bottom.value<=options.bottom.value+.01f)
        capture("landscape-tools-left")
        ui.onNodeWithContentDescription("Changer le côté des outils").performClick()
        val tools=ui.onNodeWithTag("scratchpad-tools").getUnclippedBoundsInRoot()
        val sheet=ui.onNodeWithTag("scratchpad-ink").getUnclippedBoundsInRoot()
        assertTrue(tools.left>=sheet.right)
        ui.runOnIdle {assertTrue(h.state.toolbarRight);assertEquals(1,h.state.strokes.size)}
        capture("landscape-tools-right")
    }
}