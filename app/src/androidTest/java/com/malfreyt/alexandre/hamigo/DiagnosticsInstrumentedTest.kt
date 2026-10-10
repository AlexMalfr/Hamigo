package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.platform.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DiagnosticsInstrumentedTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val saved=mutableMapOf<String,Map<String,Any?>>()
    private val fixture=object:ExternalResource() {
        override fun before() {
            check(Build.HARDWARE in listOf("ranchu","goldfish"))
            for(file in listOf("hamigo","hamigo_social","hamigo_secure","hamigo_diagnostics")) {
                val prefs=context.getSharedPreferences(file,Context.MODE_PRIVATE);saved[file]=prefs.all.toMap();check(prefs.edit().clear().commit())
            }
            check(context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().putBoolean("welcomed",true).putBoolean("autoSync",false).putBoolean("reminderEnabled",false)
                .putString("progress","{\"schema\":2,\"xp\":9,\"completed\":[],\"reviews\":{},\"syncBase\":{\"xp\":9,\"answers\":0,\"correct\":0,\"completed\":[],\"dailyXp\":{},\"awarded\":{}},\"syncEvents\":{}}").commit())
            ProgressSyncScheduler.cancel(context)
        }
        override fun after() {
            stopMorse()
            saved.forEach {(file,data)->val e=context.getSharedPreferences(file,Context.MODE_PRIVATE).edit().clear()
                data.forEach {(k,v)->when(v){is String->e.putString(k,v);is Boolean->e.putBoolean(k,v);is Int->e.putInt(k,v);is Long->e.putLong(k,v);is Float->e.putFloat(k,v);is Set<*>->e.putStringSet(k,v.filterIsInstance<String>().toSet())}}
                check(e.commit())}
            ProgressSyncScheduler.schedule(context);DailyReminder.schedule(context)
        }
    }
    private val ui=createAndroidComposeRule<MainActivity>()
    @get:Rule val rules:RuleChain=RuleChain.outerRule(fixture).around(ui)
    private lateinit var model:AppModel
    @Before fun load(){model=ViewModelProvider(ui.activity)[AppModel::class.java];ui.waitUntil(60_000){model.content!=null&&!model.socialRefreshing};ui.runOnIdle {model.showWelcome=false}}
    private fun q()=Question("debug-fixture","Quelle commande ouvre le contact ?",listOf("Le manipulateur","Le fusible"),0,"Le manipulateur commande le signal.")
    @Test fun catalogFindsChoiceTextAndTestsTheOpaqueIdWithoutTouchingRealProgress() {
        val before=raw()
        val target=model.content!!.courseQuestions.first {"Vendre des communications" in it.choices}
        assertFalse(target.prompt.contains("vendre",true))
        ui.runOnIdle {model.debugToolsOpen=true}
        ui.onNodeWithTag("diagnostic-tab-Questions").performClick()
        ui.onNodeWithTag("diagnostic-search").performTextInput("vendre communications")
        ui.waitUntil(10000) {ui.onAllNodesWithTag("diagnostic-question-${target.id}").fetchSemanticsNodes().isNotEmpty()}
        androidx.test.espresso.Espresso.pressBack() // Browse the full result after typing, keeping the query.
        val location=DiagnosticData.courseLocations(model.content!!).getValue(target.id).first()
        text(location).performScrollTo().assertIsDisplayed()
        capture("content47-catalog-choice")
        ui.onNodeWithTag("diagnostic-question-${target.id}").performClick()
        ui.onNodeWithTag("diagnostic-question-detail").performScrollToNode(hasText(location))
        text(location).assertIsDisplayed()
        text("Tester cette question").performScrollTo()
        text("Tester cette question").performClick()
        inside(hasText(target.choices[target.answer])).performScrollTo().performClick()
        text("Vérifier").performClick();text("Continuer").performClick()
        assertEquals(before,raw())
    }
    @Test fun frequencyToleranceAcceptsTheDisplayedEndpointAndReceiverUsesAnActualFrenchVoice() {
        val before=raw();val question=model.content!!.allQuestions.getValue("proc-band-147.0")
        ui.runOnIdle {model.diagnostics.freeze(true);model.startQuestions("Réception",listOf(question))}
        ui.onNodeWithTag("question-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {it(147.05f)}
        capture("content47-frequency-14705")
        ui.onNodeWithText("147,05 MHz").assertExists()
        ui.onNodeWithTag("question-answers").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) {it(0f,100_000f)}
        val precision=ui.onNodeWithText("Précision acceptée",substring=true)
        precision.assertIsDisplayed()
        assertTrue(precision.fetchSemanticsNode().boundsInRoot.bottom<=ui.onNodeWithTag("question-tools").fetchSemanticsNode().boundsInRoot.top+1f)
        capture("content47-frequency-bottom")
        ui.onNodeWithText("Vérifier").performClick()
        assertEquals(true,model.session!!.feedback);assertEquals(before,raw())
        val receiver=FrequencyReceiverFeedback(context,147.0)
        try {
            ui.waitUntil(20000){receiver.voiceReady||receiver.voiceFailure!=null}
            assertNull(receiver.voiceFailure,receiver.voiceFailure)
            assertTrue(receiver.voiceDurationMillis>=10000)
            receiver.tune(147f);ui.waitUntil(5000){receiver.playing}
            assertTrue(FeedbackAudioGate.ownedBy(receiver))
            Thread.sleep(2600);assertTrue(receiver.playing)
            receiver.tune(146.95f);ui.waitUntil(5000){receiver.playing}
            assertTrue(FeedbackAudioGate.ownedBy(receiver))
            val narration=Any();FeedbackAudioGate.reserve(narration)
            receiver.tune(147f);assertFalse(FeedbackAudioGate.ownedBy(receiver))
            FeedbackAudioGate.release(narration)
            ui.waitUntil(5000){receiver.playing}
            receiver.close();ui.waitUntil { !receiver.playing }
            assertFalse(FeedbackAudioGate.busy)
        } finally {receiver.close()}
        ui.runOnIdle {FeedbackPreferences.save(model.progress.prefs,FeedbackSettings(sound=false));model.leaveSession();model.startQuestions("Sans sons",listOf(question))}
        ui.onNodeWithTag("question-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {it(146.9f)}
        assertFalse(FeedbackAudioGate.busy)
        ui.onNodeWithText("Vérifier").performClick();assertEquals(false,model.session!!.feedback)
        assertEquals(before,raw())
    }
    @Test fun diagnosticFrequencyGesturesPlayOnlyTheVisibleSessionAndRespectItsMute() {
        val before=raw();val question=model.content!!.allQuestions.getValue("proc-band-147.0")
        ui.runOnIdle {model.diagnostics.freeze(true);model.startQuestions("Séance derrière le diagnostic",listOf(question))}
        ui.onNodeWithTag("question-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {it(147f)}
        ui.waitUntil(5000){FeedbackAudioGate.busy}
        ui.runOnIdle {model.debugToolsOpen=true}
        ui.waitUntil(5000){!FeedbackAudioGate.busy}
        ui.onNodeWithTag("diagnostic-tab-Questions").performClick()
        ui.onNodeWithTag("diagnostic-search").performTextInput(question.id)
        ui.waitUntil(10000){ui.onAllNodesWithTag("diagnostic-question-${question.id}").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("diagnostic-question-${question.id}").performClick()
        text("Tester cette question").performClick()
        inside(hasTestTag("question-slider")).performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {it(147f)}
        ui.waitUntil(5000){FeedbackAudioGate.busy}
        capture("frequency-diagnostic-playing")
        Thread.sleep(2600);assertTrue(FeedbackAudioGate.busy)
        inside(hasText("+ 0,05")).performScrollTo().performClick()
        ui.waitUntil(5000){FeedbackAudioGate.busy}
        inside(hasContentDescription("Couper les sons")).performClick()
        ui.waitUntil(5000){!FeedbackAudioGate.busy}
        inside(hasText("− 0,05")).performClick()
        ui.waitForIdle();assertFalse(FeedbackAudioGate.busy)
        assertTrue(FeedbackPreferences.read(model.progress.prefs).sound)
        inside(hasContentDescription("Activer les sons")).performClick()
        inside(hasText("+ 0,05")).performClick()
        ui.waitUntil(5000){FeedbackAudioGate.busy}
        inside(hasContentDescription("Quitter la séance")).performClick()
        ui.waitUntil(5000){!FeedbackAudioGate.busy}
        assertEquals(before,raw())
        ui.runOnIdle {model.debugToolsOpen=false}
        ui.onNodeWithTag("question-slider").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) {it(146.95f)}
        ui.waitUntil(5000){FeedbackAudioGate.busy}
        ui.runOnIdle {model.leaveSession()}
        ui.waitUntil(5000){!FeedbackAudioGate.busy}
        assertEquals(before,raw())
    }
    @Test fun diagnosticExamBankFilterListsAndOpensTheActualImportedQuestions() {
        val before=raw();val target=model.content!!.exam.first {it.image!=null}
        ui.runOnIdle {model.debugToolsOpen=true}
        ui.onNodeWithTag("diagnostic-tab-Questions").performClick()
        ui.onNodeWithTag("diagnostic-bank-Exam1").performScrollTo().performClick()
        ui.onNodeWithTag("diagnostic-search").performTextInput(target.id)
        ui.waitUntil(10000){ui.onAllNodesWithTag("diagnostic-question-${target.id}").fetchSemanticsNodes().isNotEmpty()}
        androidx.test.espresso.Espresso.pressBack()
        capture("content47-catalog-exam1")
        ui.onNodeWithTag("diagnostic-question-${target.id}").performScrollTo().performClick()
        ui.onNodeWithTag("diagnostic-question-detail").performScrollToNode(hasText("Hors Parcours · Exam1"))
        text("Hors Parcours · Exam1").assertIsDisplayed()
        text("Tester cette question").performScrollTo().performClick()
        inside(hasText(target.choices.first())).performScrollTo().assertIsDisplayed()
        capture("content47-exam1-test")
        assertEquals(before,raw())
    }
    @Test fun morseOrderingShowsPlainLabelsWithoutGivingAwayTheDurations() {
        val before=raw()
        val question=model.content!!.lessons.first {it.id=="c15-l09"}.questions.first {it.kind=="order"}
        ui.runOnIdle {model.diagnostics.freeze(true);model.startQuestions("Temporisation Morse",listOf(question))}
        listOf("Point","Trait","Pause entre les mots").forEach {ui.onNodeWithText(it).assertExists()}
        ui.onNodeWithText("1 unité",substring=true).assertDoesNotExist()
        capture("content47-morse-order")
        ui.onNodeWithTag("question-answers").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) {it(0f,100_000f)}
        assertTrue(ui.onNodeWithTag("order-board").fetchSemanticsNode().boundsInRoot.bottom<=ui.onNodeWithTag("question-tools").fetchSemanticsNode().boundsInRoot.top+1f)
        capture("content47-morse-order-bottom")
        assertEquals(before,raw())
    }
    @Test fun morseSettingsExposeLiveAudioAndDiagnosticCopiesKeepItLocal() {
        val before=raw()
        ui.runOnIdle {model.route="settings"}
        ui.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("morse-gameplay"))
        ui.onNodeWithTag("morse-gameplay").performScrollTo()
        ui.onNodeWithTag("morse-live-sound").assertIsOn().performClick().assertIsOff()
        assertFalse(GameplayPreferences.read(model.progress.prefs).liveSound)
        assertFalse(model.progress.export().contains(GameplayPreferences.LIVE_SOUND))
        ui.onNodeWithText("Un bouton").performClick()
        ui.onNodeWithTag("morse-live-sound").assertIsOff()
        ui.onNodeWithTag("morse-gameplay").performScrollTo()
        capture("morse-live-settings-off")
        ui.onNodeWithTag("morse-live-sound").performClick().assertIsOn()
        capture("morse-live-settings-on")
        ui.onNodeWithTag("morse-single-key").performScrollTo().assertIsDisplayed()
        capture("morse-live-settings-key")
        val demo=AppModel().apply {initializeDiagnostics(context,model,model.content!!)}
        try {
            assertTrue(GameplayPreferences.read(demo.progress.prefs).liveSound)
            GameplayPreferences.save(demo.progress.prefs,GameplayPreferences.read(demo.progress.prefs).copy(liveSound=false))
            assertTrue(GameplayPreferences.read(model.progress.prefs).liveSound)
        } finally {demo.disposeDiagnostics()}
        assertEquals(before,raw())
    }
    private fun inside(matcher:SemanticsMatcher)=ui.onNode(matcher and hasAnyAncestor(hasTestTag("diagnostics-screen")))
    private fun text(value:String)=inside(hasText(value))
    private fun raw()=model.progress.prefs.getString("progress",null)
    private fun capture(name:String) {
        ui.waitForIdle();repeat(2) {val latch=CountDownLatch(1);instrumentation.runOnMainSync {ui.activity.window.decorView.let {v->v.viewTreeObserver.registerFrameCommitCallback {latch.countDown()};v.invalidate()}};assertTrue(latch.await(5,TimeUnit.SECONDS))}
        val bitmap=instrumentation.uiAutomation.takeScreenshot();val dir=File(context.getExternalFilesDir(null),when {name.startsWith("content47-")->"ui-0.47";name.startsWith("morse-live-")->"ui-0.46";else->"ui-0.45"}).apply {mkdirs()}
        try {File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}
    }

    @Test fun calculatorOpensTheCatalogAndTestsAnExactIdWithoutRecordingAnything() {
        ui.runOnIdle {model.startQuestions("Question réelle",listOf(q()))}
        val before=raw();val original=model.session
        ui.onNodeWithContentDescription("Ouvrir la calculatrice").performClick()
        ui.onNodeWithTag("calculator-expression").performTextInput(DiagnosticAccess.CODE)
        ui.onNodeWithTag("calculator-key-=").performScrollTo().performClick()
        ui.onNodeWithTag("diagnostics-screen").assertIsDisplayed();capture("menu")
        ui.onNodeWithTag("diagnostic-enabled").performClick().assertIsOn()
        assertTrue(context.getSharedPreferences("hamigo_diagnostics",Context.MODE_PRIVATE).getBoolean("enabled",false))
        ui.onNodeWithTag("diagnostic-tab-Questions").performClick()
        val target=model.content!!.exam.first {it.id.endsWith("20081")}
        ui.onNodeWithTag("diagnostic-search").performTextInput("20081")
        ui.waitUntil(10_000){ui.onAllNodesWithTag("diagnostic-question-${target.id}").fetchSemanticsNodes().isNotEmpty()}
        capture("catalog-id");ui.onNodeWithTag("diagnostic-question-${target.id}").performClick()
        text("Tester cette question").performClick()
        inside(hasText(target.choices[target.answer])).performScrollTo().performClick()
        text("Vérifier").performClick();text("Continuer").performClick()
        assertEquals(before,raw());assertSame(original,model.session)
        text("Revenir au parcours").performClick()
        ui.onNodeWithContentDescription("Retour des diagnostics").performClick()
        ui.onNodeWithTag("diagnostic-hud").assertIsDisplayed();capture("hud-question")
        assertEquals(before,raw())
    }

    @Test fun frozenSessionsCannotEarnXpCompleteLessonsOrChangeSpacedRepetition() {
        val before=raw()
        ui.runOnIdle {model.diagnostics.freeze(true);model.startQuestions("Gel",listOf(q()),lessonId=model.content!!.lessons.first().id);model.answer(true);model.next()}
        assertEquals(before,raw());assertEquals(0,model.session!!.gain);assertTrue(model.session!!.testing)
        ui.runOnIdle {model.leaveSession();model.startQuestions("Examen gelé",listOf(q().copy(section="regulation")),exam=true);model.beginExamPart();model.answer(true);model.finishExamPart()}
        assertEquals(before,raw());assertEquals(0,model.session!!.gain);capture("frozen-result")
        ui.runOnIdle {model.leaveSession();model.diagnostics.freeze(false);model.startQuestions("Normal",listOf(q()));model.answer(true)}
        assertTrue(model.progress.xp>9);assertTrue(q().id in model.progress.reviews)
    }

    @Test fun clientSandboxIsInMemoryReversibleAndBlocksEveryGithubTransport() {
        val before=raw();val realName=model.progress.name
        val calls=mutableListOf<String>();val gateway=object:GitHubGateway {
            override suspend fun api(method:String,path:String,token:String?,body:String?):String {calls+=path;error("Unexpected transport")}
            override suspend fun rawBackup(rawUrl:String,owner:String,gist:String,fileName:String):String {calls+=rawUrl;error("Unexpected transport")}
        }
        val guarded=GitHubSync(context,gateway)
        ui.runOnIdle {model.debugToolsOpen=true}
        ui.onNodeWithTag("diagnostic-sandbox").performClick().assertIsOn()
        val demo=model.diagnostics.sandbox!!
        val originalFeedback=FeedbackPreferences.read(model.progress.prefs)
        ui.runOnIdle {FeedbackPreferences.save(demo.progress.prefs,originalFeedback.copy(sound=false,haptics=false))}
        ui.waitUntil { !model.interactionFeedback.settings.sound&&!model.interactionFeedback.settings.haptics }
        assertEquals(originalFeedback,FeedbackPreferences.read(model.progress.prefs))
        assertTrue(DiagnosticAccess.syncPaused(context));assertFalse(demo.sync.tokens.hasToken())
        val fakeBefore=demo.progress.xp
        ui.onNodeWithTag("diagnostic-value-XP total").performTextReplacement("9999")
        ui.onNodeWithTag("diagnostic-value-XP aujourd’hui").performTextReplacement("80")
        ui.onNodeWithTag("diagnostic-value-XP aujourd’hui").performImeAction()
        ui.onNodeWithTag("diagnostic-controls").performScrollToNode(hasText("Appliquer les données factices"))
        text("Appliquer les données factices").performClick();capture("sandbox-controls")
        assertEquals(9999,demo.progress.xp);assertEquals(80,demo.progress.todayXp)
        ui.onNodeWithTag("diagnostic-controls").performScrollToNode(hasTestTag("diagnostic-edit-lessons"))
        ui.onNodeWithTag("diagnostic-edit-lessons").performClick()
        ui.onNodeWithTag("diagnostic-complete-all").performClick()
        assertEquals(model.content!!.lessons.map {it.id}.toSet(),demo.progress.completed)
        val savedXp=demo.progress.xp;val savedReviews=demo.progress.reviews.toMap()
        val last=model.content!!.lessons.last()
        ui.onNodeWithTag("diagnostic-lessons").performScrollToNode(hasTestTag("diagnostic-lesson-${last.id}"))
        ui.onNodeWithTag("diagnostic-lesson-${last.id}").performClick().assertIsOff()
        assertFalse(last.id in demo.progress.completed);assertEquals(savedXp,demo.progress.xp);assertEquals(savedReviews,demo.progress.reviews)
        ui.onNodeWithTag("diagnostic-complete-none").performClick()
        ui.onNodeWithTag("diagnostic-lesson-${last.id}").performClick().assertIsOn()
        assertEquals(setOf(last.id),demo.progress.completed);assertEquals(before,raw());capture("sandbox-lessons")
        ui.onNodeWithTag("diagnostic-lesson-done").performClick()
        ui.runOnIdle {demo.startQuestions("Démo",listOf(q()),lessonId=demo.content!!.lessons.first().id);demo.answer(true);demo.next()}
        assertTrue(demo.progress.xp>fakeBefore);assertEquals(before,raw());assertEquals(realName,model.progress.name)
        assertEquals(demo.progress.xp,demo.displayedProgress!!.xp)
        runBlocking {assertTrue(runCatching {guarded.readProfile("https://gist.github.com/demo/"+"a".repeat(32))}.exceptionOrNull() is kotlinx.coroutines.CancellationException)}
        assertTrue(calls.isEmpty())
        ui.onNodeWithContentDescription("Retour des diagnostics").performClick();capture("sandbox-result")
        ui.onNodeWithTag("diagnostic-open").performClick()
        ui.onNodeWithTag("diagnostic-controls").performScrollToNode(hasTestTag("diagnostic-revert"))
        ui.onNodeWithTag("diagnostic-revert").performClick()
        assertNull(model.diagnostics.sandbox);assertFalse(DiagnosticAccess.syncPaused(context));assertEquals(before,raw());assertEquals(realName,model.progress.name)
        ui.waitUntil {model.interactionFeedback.settings==originalFeedback}
        val memory=DiagnosticContext(context)
        memory.getSharedPreferences("hamigo_secure",Context.MODE_PRIVATE).edit().putString("fixture","fake").commit()
        assertFalse(context.getSharedPreferences("hamigo_secure",Context.MODE_PRIVATE).contains("fixture"))
        // Fake settings cannot schedule/cancel real alarms, notifications or background work.
        val noPlatform=DiagnosticContext(object:android.content.ContextWrapper(context) {
            override fun getSystemService(name:String):Any?=error("Sandbox must not use platform service: $name")
        })
        DailyReminder.configure(noPlatform,true);DailyReminder.schedule(noPlatform)
        assertFalse(DailyReminder.showTest(noPlatform))
        ProgressSyncScheduler.schedule(noPlatform);ProgressSyncScheduler.enqueue(noPlatform)
    }

    @Test fun sliderAndFlashcardGesturesUseDistinctCuesWithoutRecomposeFeedback() {
        data class Event(val cue:FeedbackCue,val sound:Boolean,val tactile:Boolean)
        val events=CopyOnWriteArrayList<Event>()
        ui.runOnIdle {model.interactionFeedback.observer={cue,sound,haptic->events+=Event(cue,sound,haptic)};model.startQuestions("Fréquence",listOf(model.content!!.allQuestions.values.first {it.kind=="frequency"}))}
        ui.onNodeWithTag("question-slider").performScrollTo()
        ui.onNodeWithTag("question-answers").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy){it(0f,100_000f)}
        ui.onNodeWithTag("question-slider").performTouchInput {down(center)}
        repeat(5) {Thread.sleep(70);ui.onNodeWithTag("question-slider").performTouchInput {moveBy(androidx.compose.ui.geometry.Offset((centerRight.x-centerLeft.x)*.02f,0f))}}
        ui.onNodeWithTag("question-slider").performTouchInput {up()}
        assertTrue("Scale detents: $events",events.any {it.cue==FeedbackCue.SLIDER_TICK||it.cue==FeedbackCue.SLIDER_MARK});assertTrue("Release: $events",events.any {it.cue==FeedbackCue.SLIDER_RELEASE})
        assertFalse(events.filter {it.cue.name.startsWith("SLIDER")}.any {it.sound})
        capture("slider")
        events.clear();ui.runOnIdle {model.revision++};Thread.sleep(120);assertTrue(events.isEmpty())
        ui.runOnIdle {model.startQuestions("Flashcard",listOf(model.content!!.flashcards.first()))}
        ui.onNodeWithTag("flashcard").performClick();Thread.sleep(160);ui.onNodeWithTag("flashcard").performClick()
        assertEquals(2,events.count {it.cue==FeedbackCue.FLASH_FLIP});capture("flashcard")
        ui.runOnIdle {FeedbackPreferences.save(model.progress.prefs,FeedbackSettings(haptics=false))}
        Thread.sleep(120);ui.onNodeWithTag("flashcard").performClick();assertFalse(events.last {it.cue==FeedbackCue.FLASH_FLIP}.tactile)
    }

    @Test fun typeTourMocksAndStatisticsAreAvailableWithoutChangingTheRealProgress() {
        val before=raw()
        ui.runOnIdle {model.debugToolsOpen=true}
        ui.onNodeWithTag("diagnostic-tab-Types").performClick();capture("types")
        text("Tester tous les types").performClick();inside(hasTestTag("question-pico")).assertIsDisplayed()
        // Dialog back discards only this isolated test session.
        androidx.test.espresso.Espresso.pressBack()
        ui.onNodeWithTag("diagnostic-tab-Aperçus").performClick();text("Moi · objectif atteint").performClick();capture("mock-profile")
        ui.onNodeWithContentDescription("Retour des diagnostics").performClick()
        ui.onNodeWithTag("diagnostic-tab-Infos").performClick();capture("stats")
        assertEquals(before,raw())
    }

    @Test fun morseTestQuestionsUseTheChosenInputModeAndTimingWithoutChangingRealPreferences() {
        val chosen=MorseInputSettings(singleKey=true,thresholdMs=430)
        ui.runOnIdle {GameplayPreferences.save(model.progress.prefs,chosen);model.debugToolsOpen=true}
        val before=raw()
        val q=model.content!!.allQuestions.values.first {it.kind=="morseEncode"}
        ui.onNodeWithTag("diagnostic-tab-Questions").performClick()
        ui.onNodeWithTag("diagnostic-search").performTextInput(q.id)
        ui.waitUntil(10_000){ui.onAllNodesWithTag("diagnostic-question-${q.id}").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("diagnostic-question-${q.id}").performClick()
        text("Tester cette question").performClick()
        inside(hasTestTag("morse-single-key")).performScrollTo().assertIsDisplayed()
        inside(hasContentDescription("Manipulateur Morse : appui court pour un point, appui d’au moins 430 millisecondes pour un trait")).assertExists()
        inside(hasTestTag("morse-single-key")).performTouchInput {down(center);advanceEventTime(360);up()}
        inside(hasContentDescription("point") and !hasTestTag("morse-single-key") and !hasAnyAncestor(hasTestTag("morse-single-key"))).assertExists()
        capture("morse-configured-test")
        androidx.test.espresso.Espresso.pressBack()
        ui.onNodeWithContentDescription("Retour des diagnostics").performClick()
        ui.runOnIdle {model.startClientSandbox()}
        val demo=model.diagnostics.sandbox!!
        assertEquals(chosen,GameplayPreferences.read(demo.progress.prefs))
        ui.runOnIdle {GameplayPreferences.save(demo.progress.prefs,MorseInputSettings(false,150));model.stopClientSandbox()}
        assertEquals(chosen,GameplayPreferences.read(model.progress.prefs));assertEquals(before,raw())
    }

    @Test fun teamPreviewAndSandboxSettingsRegisterLaunchersWithoutStartingExternalActions() {
        val before=raw()
        val registry=DiagnosticContext(context).activityResultRegistry
        var launcher:androidx.activity.result.ActivityResultLauncher<android.content.Intent>?=null
        var result:androidx.activity.result.ActivityResult?=null
        val outgoing=java.util.concurrent.atomic.AtomicInteger()
        val monitor=object:android.app.Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent:android.content.Intent):android.app.Instrumentation.ActivityResult? {
                if(intent.action==android.content.Intent.ACTION_VIEW){outgoing.incrementAndGet();return android.app.Instrumentation.ActivityResult(0,null)}
                return null
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            ui.runOnIdle {launcher=registry.register("diagnostic-test",androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()){result=it}
                launcher!!.launch(android.content.Intent(android.content.Intent.ACTION_VIEW,android.net.Uri.parse(ContentFeedback.FORM_URL)))}
            ui.waitUntil {result!=null}
            assertEquals(android.app.Activity.RESULT_CANCELED,result!!.resultCode);assertEquals(0,outgoing.get())
            ui.runOnIdle {model.debugToolsOpen=true}
            ui.onNodeWithTag("diagnostic-tab-Aperçus").performClick()
            text("Équipe · deux amis").performClick()
            ui.onNodeWithContentDescription("Aperçu de démonstration, défilement uniquement").assertIsDisplayed()
            capture("mock-team-top")
            ui.onNodeWithContentDescription("Aperçu de démonstration, défilement uniquement").performTouchInput {swipeUp()}
            capture("mock-team-friends")
            ui.onNodeWithContentDescription("Retour des diagnostics").performClick()
            ui.onNodeWithContentDescription("Retour des diagnostics").performClick()
            ui.runOnIdle {model.startClientSandbox();model.diagnostics.sandbox!!.route="friends"}
            ui.onNodeWithTag("friends-list").assertIsDisplayed();capture("sandbox-team")
            ui.runOnIdle {model.diagnostics.sandbox!!.route="settings"}
            ui.onNodeWithContentDescription("Aperçu de démonstration, défilement uniquement").assertIsDisplayed();capture("sandbox-settings")
            ui.runOnIdle {model.stopClientSandbox()}
            assertEquals(before,raw());assertEquals(0,outgoing.get())
        } finally {instrumentation.removeMonitor(monitor);instrumentation.runOnMainSync {launcher?.unregister()}}
    }
}
