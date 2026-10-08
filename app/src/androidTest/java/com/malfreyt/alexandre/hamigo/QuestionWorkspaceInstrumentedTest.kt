package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inspector.WindowInspector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.malfreyt.alexandre.hamigo.platform.ProgressSyncScheduler
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class QuestionWorkspaceInstrumentedTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private var saved=emptyMap<String,Any?>()
    private val fixture=object:ExternalResource() {
        override fun before() {
            check(Build.MODEL.startsWith("sdk_") || Build.FINGERPRINT.contains("generic")) { "Emulator fixtures only" }
            val prefs=context.getSharedPreferences("hamigo",Context.MODE_PRIVATE)
            saved=prefs.all.mapValues { (_,v)->if(v is Set<*>)v.toSet() else v }
            check(prefs.edit().clear().putBoolean("welcomed",true).putBoolean("autoSync",false).putString("name","Brouillon test").commit())
            ProgressSyncScheduler.cancel(context)
        }
        override fun after() {
            val edit=context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().clear()
            saved.forEach { (key,v)->when(v) {
                is String->edit.putString(key,v); is Boolean->edit.putBoolean(key,v); is Int->edit.putInt(key,v)
                is Long->edit.putLong(key,v); is Float->edit.putFloat(key,v); is Set<*>->edit.putStringSet(key,v.filterIsInstance<String>().toSet())
            } }
            check(edit.commit());ProgressSyncScheduler.schedule(context)
        }
    }
    private val ui=createAndroidComposeRule<MainActivity>()
    @get:Rule val rules:RuleChain=RuleChain.outerRule(fixture).around(ui)
    private lateinit var model:AppModel
    @Before fun load() {
        model=ViewModelProvider(ui.activity)[AppModel::class.java]
        ui.waitUntil(60_000) {model.content!=null}
        ui.runOnIdle {
            model.showWelcome=false
            // Repeated identity checks that index also scopes the workspace.
            val q=Question("workspace-number","Pour R = 10 Ω et I = 2 A, calcule U.",emptyList(),0,"U = R × I.",kind="number",value=20.0,unit="V")
            model.startQuestions("Calculs",listOf(q,q.copy(value=30.0,prompt="Pour R = 10 Ω et I = 3 A, calcule U.")))
        }
    }
    private fun openCalculator()=ui.onNodeWithContentDescription("Ouvrir la calculatrice").performClick()
    private fun openNotes()=ui.onNodeWithContentDescription("Ouvrir le brouillon").performClick()
    private fun field()=ui.onNodeWithTag("calculator-expression")
    private fun closeNotes()=ui.onNodeWithContentDescription("Fermer le brouillon").performClick()

    @Test fun erasingAndQuestionChangesClearBothWorkspacesButReopeningKeepsThem() {
        openCalculator()
        field().performTextInput("20")
        ui.onNodeWithTag("calculator-key-=").performScrollTo().performClick()
        ui.onNodeWithTag("calculator-result").assertTextEquals("20")
        awaitNoIme()
        capture("calculator-pink-keys")
        ui.onNodeWithContentDescription("Fermer la calculatrice").performClick()
        openCalculator();field().assertTextContains("20")
        ui.onNodeWithTag("calculator-key-⌫").performScrollTo().performClick()
        field().assertTextEquals("Calcul","2")
        ui.onNodeWithTag("calculator-key-⌫").performTouchInput { longClick() }
        field().assertTextEquals("Calcul","")
        ui.onNodeWithTag("calculator-result").assertTextEquals("=")
        field().performTextInput("20")
        ui.onNodeWithText("Utiliser dans ma réponse").performScrollTo().performClick()
        openNotes()
        ui.onNodeWithText("Texte",substring=false).performClick()
        ui.onNodeWithTag("scratchpad-text").performTextInput("U = R × I\n10 × 2 = 20")
        capture("scratchpad-text-keyboard")
        closeNotes();openNotes()
        ui.onNodeWithTag("scratchpad-text").assertTextContains("10 × 2 = 20",substring=true)
        ui.onNodeWithText("Dessin",substring=false).performClick()
        awaitNoIme()
        ui.onNodeWithTag("scratchpad-ink").performTouchInput { swipe(Offset(width*.2f,height*.25f),Offset(width*.75f,height*.5f)) }
        ui.runOnIdle { assertEquals(1,inkView().state.strokes.size) }
        capture("scratchpad-drawing")
        ui.onNodeWithText("Stylet seul").performClick()
        val panel=ui.onNodeWithTag("scratchpad-surface",useUnmergedTree=true)
        val opened=panel.fetchSemanticsNode().boundsInRoot
        val origin=ui.onNodeWithContentDescription("Ouvrir le brouillon").fetchSemanticsNode().boundsInRoot
        ui.mainClock.autoAdvance=false
        try {
            closeNotes();ui.mainClock.advanceTimeBy(96)
            val middle=panel.fetchSemanticsNode().boundsInRoot
            assertTrue("The scratchpad shrinks while closing",middle.width<opened.width)
            assertTrue("Its closing motion travels towards its own button",(middle.center-origin.center).getDistance()<(opened.center-origin.center).getDistance()*.85f)
            capture("scratchpad-closing-middle")
            ui.mainClock.advanceTimeBy(160);panel.assertDoesNotExist()
        } finally {ui.mainClock.autoAdvance=true}
        openNotes()
        ui.runOnIdle { assertEquals(1,inkView().state.strokes.size) }
        closeNotes()
        ui.onNodeWithText("Vérifier",substring=false).performClick()
        ui.onNodeWithText("Continuer",substring=false).performClick()
        ui.runOnIdle { assertEquals(1,model.session!!.index) }
        openCalculator();field().assertTextEquals("Calcul","")
        ui.onNodeWithTag("calculator-result").assertTextEquals("=")
        ui.onNodeWithText("Ans",substring=false).performClick()
        ui.onNodeWithTag("calculator-key-=").performScrollTo().performClick()
        ui.onNodeWithTag("calculator-result").assertTextEquals("0")
        ui.onNodeWithContentDescription("Fermer la calculatrice").performClick()
        openNotes()
        ui.runOnIdle { assertTrue(inkView().state.strokes.isEmpty());assertEquals("",inkView().state.text.text);assertTrue(inkView().state.stylusOnly) }
        ui.onNodeWithContentDescription("Annuler le dernier trait").assertIsNotEnabled()
        capture("scratchpad-next-question-empty")
    }

    @Test fun nativeStylusHistoryCancellationPalmAndUndoAreHandled() {
        openNotes()
        ui.runOnIdle {
            val view=inkView()
            val time=SystemClock.uptimeMillis()
            fun event(action:Int,tool:Int,x:Float,y:Float,flags:Int=0):MotionEvent {
                val properties=MotionEvent.PointerProperties().apply { id=4;toolType=tool }
                val coords=MotionEvent.PointerCoords().apply { this.x=x*view.width;this.y=y*view.height;pressure=.7f }
                return MotionEvent.obtain(time,time+8,action,1,arrayOf(properties),arrayOf(coords),0,0,1f,1f,0,0,InputDevice.SOURCE_STYLUS,flags)
            }
            fun send(e:MotionEvent) {try {view.dispatchTouchEvent(e)} finally {e.recycle()} }
            send(event(MotionEvent.ACTION_DOWN,MotionEvent.TOOL_TYPE_STYLUS,.15f,.2f))
            val move=event(MotionEvent.ACTION_MOVE,MotionEvent.TOOL_TYPE_STYLUS,.3f,.3f)
            move.addBatch(time+16,view.width*.5f,view.height*.4f,.8f,1f,0)
            send(move)
            send(event(MotionEvent.ACTION_UP,MotionEvent.TOOL_TYPE_STYLUS,.8f,.3f))
            assertEquals(1,view.state.strokes.size)
            assertTrue("Historical stylus points must reach the ink",view.state.strokes[0].points.size>=4)
            send(event(MotionEvent.ACTION_DOWN,MotionEvent.TOOL_TYPE_FINGER,.2f,.7f))
            send(event(MotionEvent.ACTION_MOVE,MotionEvent.TOOL_TYPE_FINGER,.8f,.8f))
            send(event(MotionEvent.ACTION_CANCEL,MotionEvent.TOOL_TYPE_FINGER,.8f,.8f))
            assertEquals("A canceled palm/finger contact must not commit",1,view.state.strokes.size)
            if(Build.VERSION.SDK_INT>=33) {
                send(event(MotionEvent.ACTION_DOWN,MotionEvent.TOOL_TYPE_FINGER,.2f,.6f))
                send(event(MotionEvent.ACTION_UP,MotionEvent.TOOL_TYPE_FINGER,.8f,.7f,MotionEvent.FLAG_CANCELED))
                assertEquals(1,view.state.strokes.size)
            }
            // A pen arriving after a palm/finger must take over; canceling that
            // other pointer must not discard the pen's unfinished line.
            send(event(MotionEvent.ACTION_DOWN,MotionEvent.TOOL_TYPE_FINGER,.2f,.7f))
            fun twoPointers(action:Int,flags:Int=0):MotionEvent {
                val p0=MotionEvent.PointerProperties().apply {id=4;toolType=MotionEvent.TOOL_TYPE_FINGER}
                val p1=MotionEvent.PointerProperties().apply {id=9;toolType=MotionEvent.TOOL_TYPE_STYLUS}
                val c0=MotionEvent.PointerCoords().apply {x=view.width*.2f;y=view.height*.7f;pressure=1f}
                val c1=MotionEvent.PointerCoords().apply {x=view.width*.1f;y=view.height*.75f;pressure=.5f}
                return MotionEvent.obtain(time,time+24,action,2,arrayOf(p0,p1),arrayOf(c0,c1),0,0,1f,1f,0,0,InputDevice.SOURCE_STYLUS,flags)
            }
            send(twoPointers(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)))
            send(twoPointers(MotionEvent.ACTION_POINTER_UP,if(Build.VERSION.SDK_INT>=33)MotionEvent.FLAG_CANCELED else 0))
            val pen=MotionEvent.PointerProperties().apply {id=9;toolType=MotionEvent.TOOL_TYPE_STYLUS}
            val end=MotionEvent.PointerCoords().apply {x=view.width*.7f;y=view.height*.8f;pressure=.6f}
            send(MotionEvent.obtain(time,time+32,MotionEvent.ACTION_UP,1,arrayOf(pen),arrayOf(end),0,0,1f,1f,0,0,InputDevice.SOURCE_STYLUS,0))
            assertEquals("Only the pen line is committed from the multi-pointer gesture",2,view.state.strokes.size)
            send(event(MotionEvent.ACTION_DOWN,MotionEvent.TOOL_TYPE_ERASER,.5f,.4f))
            send(event(MotionEvent.ACTION_UP,MotionEvent.TOOL_TYPE_ERASER,.5f,.4f))
            assertEquals(1,view.state.strokes.size)
        }
        ui.onNodeWithContentDescription("Annuler le dernier trait").performClick()
        ui.runOnIdle {assertEquals(2,inkView().state.strokes.size)}
        ui.onNodeWithText("Stylet seul").performClick()
        ui.onNodeWithTag("scratchpad-ink").performTouchInput { swipe(Offset(width*.2f,height*.6f),Offset(width*.8f,height*.8f)) }
        ui.runOnIdle {assertEquals("Finger drawing is disabled in stylus-only mode",2,inkView().state.strokes.size)}
        capture("scratchpad-stylus")
        ui.onNodeWithContentDescription("Effacer le brouillon").performClick()
        ui.runOnIdle {assertTrue(inkView().state.strokes.isEmpty())}
    }

    @Test fun bothFloatingButtonsAndTextNotesStayAccessibleAboveTheKeyboard() {
        ui.onNode(hasSetTextAction()).performClick()
        awaitIme()
        ui.onNodeWithContentDescription("Ouvrir la calculatrice").assertIsDisplayed()
        ui.onNodeWithContentDescription("Ouvrir le brouillon").assertIsDisplayed()
        val calculator=ui.onNodeWithContentDescription("Ouvrir la calculatrice").fetchSemanticsNode().boundsInRoot
        val scratch=ui.onNodeWithContentDescription("Ouvrir le brouillon").fetchSemanticsNode().boundsInRoot
        assertTrue("The scratchpad button belongs above the calculator",scratch.bottom<calculator.top)
        val answer=ui.onNodeWithTag("question-number-input").fetchSemanticsNode().boundsInRoot
        assertTrue("Floating tools must not cover the focused answer field",answer.right<=scratch.left)
        capture("question-keyboard-tools")
        Espresso.closeSoftKeyboard()
        openNotes();ui.onNodeWithText("Texte",substring=false).performClick()
        awaitIme()
        ui.onNodeWithTag("scratchpad-text").assertIsFocused().performTextInput("P = U × I")
        ui.onNodeWithContentDescription("Fermer le brouillon").assertIsDisplayed()
        capture("scratchpad-ime")
        closeNotes()
    }
    private fun awaitIme() {
        ui.waitUntil(10_000) { WindowInspector.getGlobalWindowViews().any {it.rootWindowInsets?.isVisible(WindowInsets.Type.ime())==true} }
    }
    private fun awaitNoIme() {
        ui.waitUntil(10_000) { WindowInspector.getGlobalWindowViews().none {it.rootWindowInsets?.isVisible(WindowInsets.Type.ime())==true} }
    }
    private fun inkView():ScratchpadInkView {
        fun find(view:View):ScratchpadInkView? {
            if(view is ScratchpadInkView)return view
            if(view is ViewGroup) for(index in 0 until view.childCount) find(view.getChildAt(index))?.let { return it }
            return null
        }
        return WindowInspector.getGlobalWindowViews().firstNotNullOfOrNull(::find) ?: error("No native drawing view")
    }
    private fun capture(name:String) {
        ui.waitForIdle()
        val frame=CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view=WindowInspector.getGlobalWindowViews().last()
            view.viewTreeObserver.registerFrameCommitCallback {frame.countDown()};view.invalidate()
        }
        assertTrue(frame.await(5,TimeUnit.SECONDS))
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir=File(context.getExternalFilesDir(null),"question-tools-0.37").apply {mkdirs()}
        try {File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}
    }
}
