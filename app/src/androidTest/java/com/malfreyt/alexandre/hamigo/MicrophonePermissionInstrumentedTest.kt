package com.malfreyt.alexandre.hamigo

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Permission UI uses a controlled Android registry; no personal settings or recording are touched. */
@RunWith(AndroidJUnit4::class)
class MicrophonePermissionInstrumentedTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var memory:PermissionContext
    private val requests=CopyOnWriteArrayList<String>()
    private var allow=false
    private var engines=0
    private var serviceAvailable=true
    private var screen by mutableStateOf("onboarding")
    private var questionKey by mutableStateOf("question-1")
    private val permissionOwner=object:ActivityResultRegistryOwner {
        override val activityResultRegistry=object:ActivityResultRegistry() {
            override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?) {
                requests+=input.toString()
                Handler(Looper.getMainLooper()).post {memory.allowed=allow;dispatchResult(requestCode,allow)}
            }
        }
    }
    private val platform=object:AnswerRecognitionPlatform {
        override fun available(context:Context)=serviceAvailable
        override fun onDeviceAvailable(context:Context)=false
        override fun create(context:Context,onDevice:Boolean):SpeechRecognizer {engines++;error("These tests never start recognition")}
    }
    private class PermissionContext(base:Context):ContextWrapper(base) {
        var allowed=false
        val intents=CopyOnWriteArrayList<Intent>()
        private val stores=mutableMapOf<String,MemoryPreferences>()
        override fun getApplicationContext():Context=this
        override fun getSharedPreferences(name:String,mode:Int):SharedPreferences=stores.getOrPut(name){MemoryPreferences()}
        override fun checkPermission(permission:String,pid:Int,uid:Int)=if(permission==Manifest.permission.RECORD_AUDIO)
            if(allowed)PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED else super.checkPermission(permission,pid,uid)
        override fun startActivity(intent:Intent){intents+=intent}
    }
    @Before fun prepare() {
        check(Build.HARDWARE in listOf("ranchu","goldfish"))
        ui.prepareTestWindow()
        memory=PermissionContext(context)
        memory.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().putString("progress","sentinel").commit()
    }
    private fun show():StateRestorationTester {
        val restore=StateRestorationTester(ui)
        restore.setContent {HamigoTheme {
            CompositionLocalProvider(LocalContext provides DiagnosticContext(memory),
                LocalSpeechInputHost provides SpeechInputHost(memory,permissionOwner),LocalAnswerRecognitionPlatform provides platform) {
                val question=Question("permission-fixture","Épelle la lettre A en alphabet international.",emptyList(),0,"",kind="spoken",acceptedAnswers=listOf("Alfa"))
                var answer by remember {mutableStateOf("")}
                val occasion=if(screen=="onboarding")MicrophonePermissionOccasion.ONBOARDING else MicrophonePermissionOccasion.QUESTION
                val state=rememberWrittenAnswerState(questionKey,question,{answer=it},true,occasion,requestPermissionOnOpen=true)
                Column {
                    if(screen=="onboarding")androidx.compose.material3.Button(state.permission.request,androidx.compose.ui.Modifier.testTag("request-onboarding")){androidx.compose.material3.Text("Autoriser")}
                    WrittenAnswerInput(question,answer,{answer=it},true,state)
                    SpokenAnswerControls(state,true)
                }
            }
        }}
        return restore
    }
    private fun capture(name:String) {
        ui.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        repeat(2) {
            val frame=CountDownLatch(1)
            instrumentation.runOnMainSync {
                val view=android.view.inspector.WindowInspector.getGlobalWindowViews().last {it.hasWindowFocus()}
                view.viewTreeObserver.registerFrameCommitCallback {frame.countDown()};view.invalidate()
            }
            assertTrue(frame.await(3,TimeUnit.SECONDS))
        }
        val file=File(context.getExternalFilesDir(null),"microphone-permission/$name.png").apply {parentFile!!.mkdirs()}
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        try {file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}
    }
    @Test fun onboardingDenialAllowsOneQuestionRequestThenNeverLoopsAcrossQuestionsOrRestoration() {
        val restore=show()
        ui.waitUntil {requests.size==1&&ui.onAllNodesWithTag("question-microphone-settings").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("question-microphone").assertIsNotEnabled()
        ui.onNodeWithTag("question-use-keyboard").assertIsEnabled()
        ui.runOnIdle {screen="question";questionKey="question-2"}
        ui.waitUntil {requests.size==2}
        ui.waitForIdle()
        ui.onNodeWithTag("question-microphone").assertIsNotEnabled()
        ui.runOnIdle {questionKey="question-3"}
        restore.emulateSavedInstanceStateRestore()
        ui.waitForIdle()
        assertEquals(listOf(Manifest.permission.RECORD_AUDIO,Manifest.permission.RECORD_AUDIO),requests.toList())
        assertEquals(0,engines)
        capture("permission-denied-question")
        ui.onNodeWithTag("question-microphone-settings").performClick()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,memory.intents.single().action)
        assertEquals("package:${context.packageName}",memory.intents.single().dataString)
        assertEquals("sentinel",memory.getSharedPreferences("hamigo",Context.MODE_PRIVATE).getString("progress",null))
    }
    @Test fun grantingOnOpeningAQuestionEnablesTheButtonWithoutStartingAnUnrequestedRecording() {
        allow=true;screen="question"
        val restore=show()
        ui.waitUntil {requests.size==1}
        ui.onNodeWithTag("question-microphone").assertIsEnabled()
        ui.onNodeWithTag("question-microphone-settings").assertDoesNotExist()
        assertEquals(0,engines)
        capture("permission-granted-idle")
        ui.runOnIdle {questionKey="question-2"}
        restore.emulateSavedInstanceStateRestore()
        ui.onNodeWithTag("question-microphone").assertIsEnabled()
        assertEquals(1,requests.size)
        assertEquals(0,engines)
    }
    @Test fun onboardingCanRetryAfterRefusalWithoutRequestingAgainOnRestoration() {
        val restore=show()
        ui.waitUntil {requests.size==1&&ui.onAllNodesWithTag("question-microphone-settings").fetchSemanticsNodes().isNotEmpty()}
        restore.emulateSavedInstanceStateRestore()
        ui.waitForIdle()
        assertEquals(1,requests.size)
        ui.onNodeWithTag("request-onboarding").performClick()
        ui.waitUntil {requests.size==2}
        restore.emulateSavedInstanceStateRestore()
        ui.waitForIdle()
        assertEquals(2,requests.size)
        allow=true
        ui.onNodeWithTag("request-onboarding").performClick()
        ui.waitUntil {requests.size==3&&ui.onAllNodesWithTag("question-microphone-settings").fetchSemanticsNodes().isEmpty()}
        ui.onNodeWithTag("question-microphone").assertIsEnabled()
        assertEquals(0,engines)
    }
    @Test fun onboardingRequestsPermissionEvenWhenNoRecognitionServiceIsInstalled() {
        serviceAvailable=false
        val restore=show()
        ui.waitUntil {requests.size==1}
        ui.onNodeWithTag("question-microphone").assertIsNotEnabled()
        restore.emulateSavedInstanceStateRestore()
        ui.waitForIdle()
        assertEquals(1,requests.size)
        assertEquals(0,engines)
    }
    @Test fun freshOnboardingRequestsTheMicrophoneOnceOffersSyncedGestureChoiceAndCanContinueWithoutIt() {
        val owner=AppModel().apply {
            progress=Progress(DiagnosticContext(context),sideEffects=false)
            diagnostics=DiagnosticSettings(DiagnosticContext(context))
            interactionFeedback=AppFeedback(DiagnosticContext(context))
        }
        val model=AppModel().apply {initializeDiagnostics(context,owner,Content(context,examSnapshot=null))}
        model.progress.prefs.edit().putInt("onboardingStep",3).commit()
        val restore=StateRestorationTester(ui)
        try {
            restore.setContent {HamigoTheme {CompositionLocalProvider(LocalContext provides model.diagnosticContext,
                LocalSpeechInputHost provides SpeechInputHost(memory,permissionOwner),LocalAnswerRecognitionPlatform provides platform) {
                OnboardingScreen(model,simulation=true)
            }}}
            ui.onNodeWithTag("onboarding-morse-profile-0").performScrollTo().assertIsSelected()
            capture("onboarding-morse-default")
            ui.onNodeWithTag("onboarding-continue").performClick()
            ui.onNodeWithTag("onboarding-stage").assertTextEquals("5/6 · Micro")
            ui.waitUntil {requests.size==1&&ui.onAllNodesWithTag("question-microphone-settings").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithTag("onboarding-microphone-authorize").performScrollTo().assertIsEnabled().assertTextContains("Redemander l’autorisation")
            ui.onNodeWithTag("onboarding-speech-mode-PUSH_TO_TALK").performScrollTo().assertIsSelected()
            ui.onNodeWithTag("onboarding-speech-mode-TOGGLE").performClick().assertIsSelected()
            assertEquals(SpeechInputMode.TOGGLE,SpeechInputPreferences.read(model.progress.prefs))
            val record=SyncedAppSettings.snapshot(model.progress.prefs).getJSONObject(SpeechInputPreferences.MODE)
            assertEquals("toggle",record.getString("value"))
            assertTrue(record.getLong("modifiedAt")>1L)
            restore.emulateSavedInstanceStateRestore()
            ui.onNodeWithTag("onboarding-speech-mode-TOGGLE").performScrollTo().assertIsSelected()
            assertEquals(1,requests.size)
            ui.onNodeWithTag("onboarding-stt-settings").assertDoesNotExist()
            ui.onNodeWithTag("question-microphone-settings").performScrollTo().performClick()
            assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,memory.intents.single().action)
            assertEquals("package:${context.packageName}",memory.intents.single().dataString)
            capture("onboarding-microphone")
            ui.onNodeWithTag("onboarding-skip").assertTextEquals("Continuer sans microphone").performClick()
            ui.onNodeWithTag("onboarding-stage").assertTextEquals("6/6 · GitHub")
            assertEquals(1,requests.size)
            assertEquals(0,engines)
        } finally {ui.runOnIdle {model.disposeDiagnostics();owner.interactionFeedback.close()}}
    }
}
