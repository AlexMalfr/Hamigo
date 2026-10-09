package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
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
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class InteractionFeedbackInstrumentedTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val saved=mutableMapOf<String,Map<String,Any?>>()
    private var animationScale="1"
    private fun shell(command:String)=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {descriptor->
        java.io.FileInputStream(descriptor.fileDescriptor).bufferedReader().use {it.readText().trim()}
    }
    private val fixture=object:ExternalResource() {
        override fun before() {
            check(Build.HARDWARE in listOf("ranchu","goldfish"))
            animationScale=shell("settings get global animator_duration_scale")
            shell("settings put global animator_duration_scale 1")
            for(name in listOf("hamigo","hamigo_social","hamigo_secure")) {
                val prefs=context.getSharedPreferences(name,Context.MODE_PRIVATE);saved[name]=prefs.all.toMap()
                check(prefs.edit().clear().commit())
            }
            check(context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().putBoolean("welcomed",true).putBoolean("autoSync",false).putBoolean("reminderEnabled",false).commit())
            ProgressSyncScheduler.cancel(context)
        }
        override fun after() {
            stopMorse()
            saved.forEach {(name,values)->
                val edit=context.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear()
                values.forEach {(k,v)->when(v) {
                    is String->edit.putString(k,v);is Boolean->edit.putBoolean(k,v);is Int->edit.putInt(k,v);is Long->edit.putLong(k,v);is Float->edit.putFloat(k,v)
                    is Set<*>->edit.putStringSet(k,v.filterIsInstance<String>().toSet())
                }}
                check(edit.commit())
            }
            DailyReminder.schedule(context);ProgressSyncScheduler.schedule(context)
            shell(if(animationScale=="null")"settings delete global animator_duration_scale" else "settings put global animator_duration_scale $animationScale")
        }
    }
    private val ui=createAndroidComposeRule<MainActivity>()
    @get:Rule val rules:RuleChain=RuleChain.outerRule(fixture).around(ui)
    private lateinit var model:AppModel
    private data class Event(val cue:FeedbackCue,val soundAllowed:Boolean,val hapticAllowed:Boolean)
    private val events=CopyOnWriteArrayList<Event>()
    @Before fun load() {
        model=ViewModelProvider(ui.activity)[AppModel::class.java]
        ui.waitUntil(60_000){model.content!=null&&model.interactionFeedback.soundsReady}
        ui.runOnIdle {model.showWelcome=false;model.interactionFeedback.observer={cue,sound,haptic->events+=Event(cue,sound,haptic)}}
    }
    private fun question()=Question("feedback-fixture","Quelle commande ouvre le contact ?",listOf("Le manipulateur","Le fusible"),0,"Le manipulateur commande le signal.")
    private fun capture(name:String) {
        ui.waitForIdle()
        repeat(2) {
            val frame=java.util.concurrent.CountDownLatch(1)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                ui.activity.window.decorView.let {view->view.viewTreeObserver.registerFrameCommitCallback {frame.countDown()};view.invalidate()}
            }
            assertTrue(frame.await(5,java.util.concurrent.TimeUnit.SECONDS))
        }
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir=File(context.getExternalFilesDir(null),"ui-0.44").apply {mkdirs()}
        try {File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}
    }
    private fun beginEnding(cue:FeedbackCue) {
        // Effects and delays use the test frame clock; advance it while waiting for launch.
        for(i in 0..80) {
            ui.mainClock.advanceTimeByFrame();ui.waitForIdle()
            if(events.any {it.cue==cue})return
            Thread.sleep(8)
        }
        fail("Ending $cue did not start; session presented=${model.session?.resultPresented}")
    }

    @Test fun soundAndHapticsAreIndependentAndQuestionMutePersistsWithoutMutingMorse() {
        ui.runOnIdle {model.route="settings"}
        ui.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("feedback-settings"))
        ui.onNodeWithTag("feedback-settings").performScrollTo()
        ui.onNodeWithTag("feedback-sound").assertIsOn()
        ui.onNodeWithTag("feedback-haptics").assertIsOn()
        capture("settings-feedback")
        ui.onNodeWithTag("feedback-haptics").performClick().assertIsOff()
        ui.runOnIdle {model.route="practice";model.startQuestions("Feedback",listOf(question(),question().copy(id="feedback-second")))}
        ui.onNodeWithTag("question-sound-toggle").assertIsOn().performClick().assertIsOff()
        capture("question-muted")
        ui.runOnIdle {model.answer(true);model.next()}
        ui.onNodeWithTag("question-sound-toggle").assertIsOff()
        ui.runOnIdle {playMorse(".")}
        ui.waitUntil(5000){FeedbackAudioGate.busy}
        ui.waitUntil(5000){!FeedbackAudioGate.busy}
        assertEquals(FeedbackSettings(sound=false,haptics=false),FeedbackPreferences.read(model.progress.prefs))
        val progress=model.progress.export()
        assertTrue(progress.contains("feedback-fixture"))
        assertFalse("Device audio choices must not alter the existing backup schema",progress.contains(FeedbackPreferences.SOUND))
        ui.runOnIdle {model.progress.mergeCloud(progress)}
        assertFalse(FeedbackPreferences.read(model.progress.prefs).sound)
    }

    @Test fun oneVerdictReplacesTheButtonClickAndRecompositionDoesNotReplayIt() {
        ui.runOnIdle {model.startQuestions("Feedback",listOf(question(),question().copy(id="feedback-next")))}
        ui.onNodeWithText("Le manipulateur",substring=false).performClick()
        ui.waitUntil(3000){events.any {it.cue==FeedbackCue.SELECT}}
        events.clear()
        ui.onNodeWithText("Vérifier").performClick()
        ui.waitUntil(3000){events.any {it.cue==FeedbackCue.SUCCESS}}
        Thread.sleep(150)
        assertEquals(1,events.count {it.cue==FeedbackCue.SUCCESS})
        assertEquals(0,events.count {it.cue==FeedbackCue.CLICK})
        ui.runOnIdle {model.revision++}
        Thread.sleep(100)
        assertEquals(1,events.count {it.cue==FeedbackCue.SUCCESS})
        ui.onNodeWithText("Continuer").performClick()
        ui.onNodeWithText("Le fusible",substring=false).performClick()
        ui.onNodeWithText("Vérifier").performClick()
        ui.waitUntil(3000){events.any {it.cue==FeedbackCue.ERROR}}
        assertEquals(1,events.count {it.cue==FeedbackCue.ERROR})
        capture("answer-error")
    }

    @Test fun educationalAudioSuppressesInterfaceSoundsAndPauseSuppressesAllFeedback() {
        val owner=Any()
        ui.runOnIdle {FeedbackAudioGate.reserve(owner);model.interactionFeedback.event(FeedbackCue.SUCCESS)}
        assertFalse(events.last().soundAllowed)
        ui.runOnIdle {
            FeedbackAudioGate.release(owner)
            FeedbackPreferences.save(model.progress.prefs,FeedbackSettings(false,false))
            model.interactionFeedback.event(FeedbackCue.ERROR)
        }
        assertFalse(events.last().soundAllowed);assertFalse(events.last().hapticAllowed)
        val count=events.size
        ui.runOnIdle {model.interactionFeedback.foreground(false);model.interactionFeedback.event(FeedbackCue.COMPLETE)}
        assertEquals(count,events.size)
        ui.runOnIdle {model.interactionFeedback.foreground(true)}
    }

    @Test fun resultsLaunchOneBottomBurstAndKeepTheReturnButtonUsable() {
        ui.mainClock.autoAdvance=false
        ui.runOnIdle {
            model.route="practice";model.startQuestions("Mix terminé",listOf(question()))
            model.answer(true);model.next()
        }
        beginEnding(FeedbackCue.COMPLETE)
        ui.mainClock.advanceTimeByFrame();ui.waitForIdle()
        ui.onNodeWithTag("session-confetti").assertExists()
        ui.mainClock.advanceTimeByFrame();ui.waitForIdle()
        ui.mainClock.advanceTimeBy(700);ui.waitForIdle();capture("confetti-rise")
        ui.mainClock.advanceTimeBy(1300);ui.waitForIdle();capture("confetti-apex")
        assertEquals(1,events.count {it.cue==FeedbackCue.COMPLETE})
        ui.mainClock.advanceTimeBy(3000);ui.waitForIdle()
        ui.onNodeWithTag("session-confetti").assertDoesNotExist()
        ui.runOnIdle {model.revision++}
        ui.mainClock.advanceTimeBy(300);ui.waitForIdle()
        assertEquals(1,events.count {it.cue==FeedbackCue.COMPLETE})
        ui.onNodeWithText("Revenir aux défis").performClick()
        ui.mainClock.autoAdvance=true
        ui.runOnIdle {assertNull(model.session)}
    }

    @Test fun examDraftsDoNotLeakCorrectnessAndFailedResultsUseTheSofterEnding() {
        ui.runOnIdle {
            model.route="practice";model.startQuestions("Examen",listOf(question().copy(section="regulation"),question().copy(id="feedback-exam2",section="regulation")),exam=true)
            model.beginExamPart();model.answer(true);model.answer(false)
        }
        Thread.sleep(120)
        assertFalse(events.any {it.cue in listOf(FeedbackCue.SUCCESS,FeedbackCue.ERROR)})
        ui.mainClock.autoAdvance=false
        ui.runOnIdle {model.finishExamPart()}
        beginEnding(FeedbackCue.FINISH)
        ui.mainClock.advanceTimeByFrame();ui.waitForIdle()
        assertEquals(1,events.count {it.cue==FeedbackCue.FINISH})
        ui.mainClock.advanceTimeByFrame();ui.waitForIdle()
        assertEquals(0,events.count {it.cue==FeedbackCue.COMPLETE})
        ui.mainClock.advanceTimeBy(1700);ui.waitForIdle();capture("confetti-consolidate")
        ui.mainClock.autoAdvance=true
    }
}
