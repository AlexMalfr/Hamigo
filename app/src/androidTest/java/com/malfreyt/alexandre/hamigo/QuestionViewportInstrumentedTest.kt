package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.view.WindowInsets
import android.view.inspector.WindowInspector
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.platform.DailyReminder
import com.malfreyt.alexandre.hamigo.platform.ProgressSyncScheduler
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class QuestionViewportInstrumentedTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val saved=mutableMapOf<String,Map<String,Any?>>()
    private val fixture=object:ExternalResource() {
        override fun before() {
            check(Build.HARDWARE in listOf("ranchu","goldfish") || Build.FINGERPRINT.contains("generic")) {"Emulator fixtures only"}
            for(file in listOf("hamigo","hamigo_social","hamigo_secure")) {
                val prefs=context.getSharedPreferences(file,Context.MODE_PRIVATE)
                saved[file]=prefs.all.mapValues {(_,v)->if(v is Set<*>)v.toSet() else v}
                check(prefs.edit().clear().commit())
            }
            check(context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit()
                .putBoolean("welcomed",true).putBoolean("autoSync",false).putBoolean("reminderEnabled",false).commit())
            ProgressSyncScheduler.cancel(context)
        }
        override fun after() {
            saved.forEach {(file,values)->
                val edit=context.getSharedPreferences(file,Context.MODE_PRIVATE).edit().clear()
                values.forEach {(key,value)->when(value) {
                    is String->edit.putString(key,value);is Boolean->edit.putBoolean(key,value);is Int->edit.putInt(key,value)
                    is Long->edit.putLong(key,value);is Float->edit.putFloat(key,value)
                    is Set<*>->edit.putStringSet(key,value.filterIsInstance<String>().toSet())
                }}
                check(edit.commit())
            }
            ProgressSyncScheduler.schedule(context);DailyReminder.schedule(context)
        }
    }
    private val ui=createAndroidComposeRule<MainActivity>()
    @get:Rule val rules:RuleChain=RuleChain.outerRule(fixture).around(ui)
    private lateinit var model:AppModel
    @Before fun load() {
        model=ViewModelProvider(ui.activity)[AppModel::class.java]
        ui.waitUntil(60_000){model.content!=null}
        ui.runOnIdle {model.showWelcome=false}
    }

    @Test fun shortPromptUsesFortyPercentAndCentresPicoWithTheText() {
        val q=Question("layout-short","Quelle est l'unité de résistance ?",listOf("Ohm","Volt","Watt"),0,"",kind="choice")
        ui.runOnIdle {model.startQuestions("Présentation",listOf(q))}
        val stage=ui.onNodeWithTag("question-stage").fetchSemanticsNode().boundsInRoot
        val prompt=ui.onNodeWithTag("question-prompt-area").fetchSemanticsNode().boundsInRoot
        val group=ui.onNodeWithTag("question-prompt-group").fetchSemanticsNode().boundsInRoot
        val tolerance=with(ui.density){2.dp.toPx()}
        assertTrue("A short prompt uses the real 40% share of the viewport",abs(prompt.height-stage.height*.4f)<=tolerance)
        assertTrue("The intrinsic Pico/text group is centred in that share",abs(group.center.y-prompt.center.y)<=tolerance)
        capture("short-centred-40")

        // Compare the actual formats at full size; dense prompts may naturally grow beyond 40%.
        for(kind in listOf("truefalse","match","order","number","cloze","resistor","waveform")) {
            val example=model.content!!.allQuestions.values.first {it.kind==kind&&it.image==null&&(kind!="match"||it.pairs.size==3)}
            ui.runOnIdle {model.startQuestions("Présentation $kind",listOf(example))}
            ui.onNodeWithTag("question-pico").assertIsDisplayed()
            capture("layout-40-$kind")
        }
    }

    @Test fun longPromptGrowsAndEveryAnswerSharesOneScrollWithClearanceAboveTools() {
        val q=Question("layout-long",("Une station transmet un signal vers une antenne distante. ").repeat(24),
            listOf("Première réponse","Deuxième réponse","Troisième réponse","Dernière réponse"),0,"",kind="choice")
        ui.runOnIdle {model.startQuestions("Question longue",listOf(q))}
        val stage=ui.onNodeWithTag("question-stage").fetchSemanticsNode().boundsInRoot
        val prompt=ui.onNodeWithTag("question-prompt-area").fetchSemanticsNode().boundsInRoot
        assertTrue("The long prompt grows beyond its indicative share",prompt.height>stage.height*.4f+10f)
        ui.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).assertCountEquals(1)
        capture("long-one-page-start")
        ui.onNodeWithTag("question-answers").performSemanticsAction(SemanticsActions.ScrollBy){it(0f,100_000f)}
        val last=ui.onNodeWithText(q.choices.last())
        last.assertIsDisplayed()
        val tools=ui.onNodeWithTag("question-tools").fetchSemanticsNode().boundsInRoot
        assertTrue("The last choice can scroll wholly above the floating tools",last.fetchSemanticsNode().boundsInRoot.bottom<=tools.top+1f)
        capture("long-one-page-end")
    }

    @Test fun openingImeKeepsTheWindowArrangementAndClearsTheFocusedAnswer() {
        val q=Question("layout-number","Pour R = 10 Ω et I = 2 A, calcule U.",emptyList(),0,"",kind="number",value=20.0,unit="V")
        ui.runOnIdle {model.startQuestions("Saisie numérique",listOf(q))}
        fun toolBounds(label:String)=ui.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
        val beforeNotes=toolBounds("Ouvrir le brouillon");val beforeCalculator=toolBounds("Ouvrir la calculatrice")
        val wasVertical=beforeNotes.bottom<beforeCalculator.top
        capture("number-before-ime")
        ui.onNodeWithTag("question-number-input").performClick()
        ui.waitUntil(30_000){WindowInspector.getGlobalWindowViews().any {it.rootWindowInsets?.isVisible(WindowInsets.Type.ime())==true}}
        ui.waitUntil(10_000) {
            val field=ui.onNodeWithTag("question-number-input").fetchSemanticsNode().boundsInRoot
            field.bottom<=minOf(toolBounds("Ouvrir le brouillon").top,toolBounds("Ouvrir la calculatrice").top)
        }
        val notes=toolBounds("Ouvrir le brouillon");val calculator=toolBounds("Ouvrir la calculatrice")
        assertEquals("The keyboard must not turn a column into a row",wasVertical,notes.bottom<calculator.top)
        val stage=ui.onNodeWithTag("question-stage").fetchSemanticsNode().boundsInRoot
        val gap=stage.bottom-maxOf(notes.bottom,calculator.bottom)
        assertTrue("The tools leave room below their shadows",gap>=with(ui.density){14.dp.toPx()})
        ui.onNodeWithText("Vérifier").assertIsDisplayed()
        capture("number-with-ime")
        Espresso.closeSoftKeyboard()
        ui.waitUntil(10_000){WindowInspector.getGlobalWindowViews().none {it.rootWindowInsets?.isVisible(WindowInsets.Type.ime())==true}}
        capture("number-ime-closed")
    }

    @Test fun onlyTheWholeWindowSizeControlsTheToolsOrientation() {
        fun size(height:Int)=with(ui.density){IntSize(400.dp.roundToPx(),height.dp.roundToPx())}
        val wholeWindow=mutableStateOf(size(800))
        val viewport=mutableStateOf(440.dp)
        val compact=mutableStateOf(false)
        val q=Question("layout-window","Quelle unité mesure une tension ?",listOf("Volt","Ohm"),0,"",kind="choice")
        ui.activity.setContent {
            val actual=LocalWindowInfo.current
            val simulated=remember(actual){object:WindowInfo by actual {override val containerSize:IntSize get()=wholeWindow.value}}
            HamigoTheme {
                CompositionLocalProvider(LocalWindowInfo provides simulated) {
                    Box(Modifier.fillMaxSize().background(Cream)) {
                        QuestionStage(Modifier.fillMaxWidth().height(viewport.value),rememberScrollState(),compact=compact.value,
                            prompt={QuestionPrompt(q,q.id,null){QuestionText(q.prompt,towardsLeft=it)}},tools={
                                FloatingActionButton({},containerColor=Mist){Icon(Icons.Rounded.EditNote,"Ouvrir le brouillon")}
                                FloatingActionButton({},containerColor=Teal,contentColor=Color.White){Icon(Icons.Rounded.Calculate,"Ouvrir la calculatrice")}
                            }) {
                            q.choices.forEach {AnswerTile(it,false,true){}}
                        }
                    }
                }
            }
        }
        fun vertical():Boolean {
            val notes=ui.onNodeWithContentDescription("Ouvrir le brouillon").fetchSemanticsNode().boundsInRoot
            val calculator=ui.onNodeWithContentDescription("Ouvrir la calculatrice").fetchSemanticsNode().boundsInRoot
            return notes.bottom<calculator.top
        }
        assertTrue(vertical())
        ui.runOnIdle {viewport.value=210.dp;compact.value=true}
        assertTrue("An IME-sized viewport keeps the tall window's column",vertical())
        capture("simulated-tall-window-small-viewport")
        ui.runOnIdle {wholeWindow.value=size(480)}
        assertFalse("A genuinely short split window uses a row",vertical())
        capture("simulated-short-window")
        ui.runOnIdle {wholeWindow.value=size(800)}
        assertTrue("Growing the app window restores the column",vertical())
    }

    private fun capture(name:String) {
        ui.waitForIdle()
        repeat(2) {
            val frame=CountDownLatch(1)
            instrumentation.runOnMainSync {ui.activity.window.decorView.let {v->v.viewTreeObserver.registerFrameCommitCallback {frame.countDown()};v.invalidate()}}
            assertTrue(frame.await(5,TimeUnit.SECONDS))
        }
        val dir=File(context.getExternalFilesDir(null),"question-layout-0.43").apply {mkdirs()}
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        try {File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}
    }
}
