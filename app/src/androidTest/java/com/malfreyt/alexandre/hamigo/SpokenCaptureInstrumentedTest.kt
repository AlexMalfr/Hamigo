package com.malfreyt.alexandre.hamigo

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Real SpeechRecognizer binding and permission checks with synthetic, non-recorded speech only. */
@RunWith(AndroidJUnit4::class)
class SpokenCaptureInstrumentedTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private lateinit var owner:AppModel
    private lateinit var model:AppModel
    private val cues=CopyOnWriteArrayList<FeedbackCue>()
    private val closedAt=CopyOnWriteArrayList<Long>()
    private val engineContexts=CopyOnWriteArrayList<Context>()
    private val platform=object:AnswerRecognitionPlatform {
        override fun available(context:Context)=true
        override fun onDeviceAvailable(context:Context)=false
        override fun create(context:Context,onDevice:Boolean):SpeechRecognizer {
            assertFalse("The diagnostic RAM context must not own the Android recognition service",context is DiagnosticContext)
            assertEquals(PackageManager.PERMISSION_GRANTED,ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO))
            assertFalse(onDevice)
            engineContexts+=context
            return SpeechRecognizer.createSpeechRecognizer(context,ComponentName(instrumentation.context,FakeAnswerRecognitionService::class.java))
        }
    }
    @Before fun prepare() {
        check(Build.HARDWARE in listOf("ranchu","goldfish")) {"Emulator-only fixture"}
        ui.prepareTestWindow()
        // Grant the actual app UID. This exercises the real permission check, without a dialog
        // whose revocation would kill the running instrumentation process on recent Android.
        for(packageContext in listOf(context,instrumentation.context)) {
            // RecognitionService checks the whole attribution chain: client app AND service APK.
            instrumentation.uiAutomation.grantRuntimePermission(packageContext.packageName,Manifest.permission.RECORD_AUDIO)
            assertEquals("RECORD_AUDIO is required for ${packageContext.packageName}",PackageManager.PERMISSION_GRANTED,
                ContextCompat.checkSelfPermission(packageContext,Manifest.permission.RECORD_AUDIO))
        }
        val service=context.packageManager.getServiceInfo(ComponentName(instrumentation.context,FakeAnswerRecognitionService::class.java),0)
        assertTrue("The explicitly selected fake recognition service is enabled and exported",service.enabled&&service.exported)
        assertEquals("android.permission.BIND_SPEECH_RECOGNITION_SERVICE",service.permission)
        val content=Content(context,examSnapshot=null)
        ui.runOnUiThread {
            val memory=DiagnosticContext(context)
            owner=AppModel().apply {
                progress=Progress(memory,sideEffects=false)
                diagnostics=DiagnosticSettings(memory)
                interactionFeedback=AppFeedback(memory)
            }
            model=AppModel().apply {
                initializeDiagnostics(context,owner,content)
                showWelcome=false
                tour.finish()
            }
            owner.interactionFeedback.observer={cue,_,_->cues+=cue;if(cue==FeedbackCue.MIC_CLOSE)closedAt+=android.os.SystemClock.elapsedRealtime()}
        }
    }
    @After fun close() {
        ui.runOnUiThread {
            if(::model.isInitialized)model.disposeDiagnostics()
            if(::owner.isInitialized)owner.interactionFeedback.close()
        }
        engineContexts.clear()
    }
    private fun show(mode:SpeechInputMode) {
        ui.runOnIdle {
            SpeechInputPreferences.save(model.progress.prefs,mode)
            DiagnosticData.testQuestion(model,Question("synthetic-spoken-capture","Épelle A B C avec l’alphabet international.",
                emptyList(),0,"A B C s’épelle Alfa Bravo Charlie.",kind="spoken",acceptedAnswers=listOf("Alfa Bravo Charlie")))
        }
        ui.setContent {HamigoTheme {
            CompositionLocalProvider(LocalAnswerRecognitionPlatform provides platform) {HamigoApp(model)}
        }}
        ui.onNodeWithTag("question-microphone").assertIsEnabled()
        ui.onNodeWithTag("question-use-keyboard").assertIsEnabled()
        // Permission attribution also depends on the client UID really reaching the foreground.
        // Compose semantics may already exist before Android commits the focused Activity window.
        ui.awaitForegroundFrames()
        ui.runOnIdle {assertTrue("Recognition starts only with the Activity actually focused",ui.activity.hasWindowFocus())}
    }
    private fun microphoneState()=ui.onNodeWithTag("question-microphone").fetchSemanticsNode()
        .config.getOrElse(SemanticsProperties.StateDescription){""}
    private fun awaitListening() {
        try {
            ui.waitUntil(15_000){microphoneState()=="Écoute en cours"}
        } catch(error:Throwable) {
            val state=microphoneState()
            val tree=ui.onRoot(useUnmergedTree=true).printToString()
            throw AssertionError("Recognition did not enter listening. State=$state; engines=${engineContexts.size}.\n$tree",error)
        }
        ui.waitUntil(3_000){ui.onAllNodesWithTag("question-speech-partial").fetchSemanticsNodes().isNotEmpty()}
        ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Pico écoute")).assertIsDisplayed()
        ui.onNodeWithTag("question-speech-partial").assertTextContains("alpha")
        ui.onNodeWithTag("question-use-keyboard").assertIsEnabled()
        val waves=ui.onNodeWithTag("question-voice-level").fetchSemanticsNode().boundsInRoot
        val stage=ui.onNodeWithTag("question-stage").fetchSemanticsNode().boundsInRoot
        assertEquals("The voice waveform spans the complete question viewport",stage.width,waves.width,2f)
        assertEquals(stage.center.x,waves.center.x,1f)
    }
    private fun awaitFinalizing() {
        ui.waitUntil(3_000){microphoneState()=="Reconnaissance en cours"}
        ui.onNodeWithTag("question-microphone").assertIsNotEnabled()
        ui.onNodeWithText("Vérifier").assertIsNotEnabled()
        // A second press cannot discard the words still being decoded after stopListening.
        ui.onNodeWithTag("question-microphone").performTouchInput {click(center)}
    }
    private fun awaitResult() {
        ui.waitUntil(15_000){microphoneState()=="Microphone arrêté"&&
            ui.onNodeWithTag("question-text-input").fetchSemanticsNode().config[SemanticsProperties.EditableText].text=="Alfa Bravo Charlie"}
        ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Pico écoute")).assertDoesNotExist()
        ui.onNodeWithTag("question-text-input").assertTextContains("Alfa Bravo Charlie")
        assertTrue(engineContexts.isNotEmpty())
    }
    private fun capture(name:String) {
        ui.awaitForegroundFrames()
        val file=File(context.getExternalFilesDir(null),"spoken-capture/"+name+".png")
        file.parentFile!!.mkdirs()
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot()) {"No screenshot returned for the focused window"}
        try {file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}
    }

    @Test fun pushToTalkUsesTheActivityHostAndCanonicalizesItsResultWithoutUpdatingDiagnosticProgress() {
        show(SpeechInputMode.PUSH_TO_TALK)
        val before=model.progress.export()
        val ownerBefore=owner.progress.export()
        ui.onNodeWithTag("question-text-input").performTextReplacement("Réponse précédente")
        ui.runOnUiThread {ui.activity.window.insetsController?.hide(android.view.WindowInsets.Type.ime())}
        ui.waitUntil(5_000){ui.activity.window.decorView.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())!=true}
        ui.onNodeWithText("Vérifier").assertIsEnabled()
        var releasedAt=0L
        ui.onNodeWithTag("question-microphone").performTouchInput {down(center)}
        try {
            awaitListening()
            assertEquals(1,cues.count {it==FeedbackCue.MIC_OPEN})
            capture("ptt-listening")
        } finally {ui.onNodeWithTag("question-microphone").performTouchInput {releasedAt=android.os.SystemClock.elapsedRealtime();up()}}
        awaitFinalizing()
        assertTrue("The microphone really remains open briefly after release",closedAt.single()-releasedAt>=200L)
        awaitResult()
        assertEquals(1,cues.count {it==FeedbackCue.MIC_CLOSE})
        capture("ptt-result-canonical")
        ui.onNodeWithText("Vérifier").performClick()
        ui.runOnIdle {
            assertEquals(true,model.session!!.feedback)
            assertEquals(before,model.progress.export())
            assertEquals(ownerBefore,owner.progress.export())
        }
    }

    @Test fun toggleStopsOnSecondTapAndTheKeyboardCanCancelAnotherListeningCycle() {
        show(SpeechInputMode.TOGGLE)
        val before=model.progress.export()
        val ownerBefore=owner.progress.export()
        ui.onNodeWithTag("question-microphone").performTouchInput {click(center)}
        awaitListening()
        capture("toggle-listening")
        ui.onNodeWithTag("question-microphone").performTouchInput {click(center)}
        awaitFinalizing()
        awaitResult()
        assertEquals(1,cues.count {it==FeedbackCue.MIC_OPEN})
        assertEquals(1,cues.count {it==FeedbackCue.MIC_CLOSE})
        ui.onNodeWithTag("question-microphone").performTouchInput {click(center)}
        awaitListening()
        ui.onNodeWithTag("question-use-keyboard").performClick()
        ui.waitUntil(10_000){microphoneState()=="Microphone arrêté"}
        ui.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Pico écoute")).assertDoesNotExist()
        ui.onNodeWithTag("question-text-input").performTextReplacement("Bravo")
        ui.onNodeWithTag("question-text-input").assertTextContains("Bravo")
        assertEquals(2,cues.count {it==FeedbackCue.MIC_OPEN})
        assertEquals(2,cues.count {it==FeedbackCue.MIC_CLOSE})
        capture("toggle-keyboard-cancel")
        ui.onNodeWithText("Vérifier").performClick()
        ui.runOnIdle {
            assertEquals(false,model.session!!.feedback)
            assertEquals(before,model.progress.export())
            assertEquals(ownerBefore,owner.progress.export())
        }
    }
}
