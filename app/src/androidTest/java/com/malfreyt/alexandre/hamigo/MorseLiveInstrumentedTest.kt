package com.malfreyt.alexandre.hamigo

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MorseLiveInstrumentedTest {
    @get:Rule val ui = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("hamigo", Context.MODE_PRIVATE)
    private var saved = emptyMap<String, Any?>()
    private class Output : MorseLiveOutput {
        val events = mutableListOf<String>()
        var closed = false
        override fun key(down: Boolean) { events += if (down) "down" else "up" }
        override fun signal(symbol: Char) { events += symbol.toString() }
        override fun stop() { events += "stop" }
        override fun close() { closed = true }
    }
    private val outputs = mutableListOf<Output>()
    private val factory: () -> MorseLiveOutput = { Output().also { outputs += it } }
    @Before fun before() {
        check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
        saved = prefs.all.toMap()
        prefs.edit().putBoolean(GameplayPreferences.SINGLE_KEY, true).putBoolean(GameplayPreferences.LIVE_SOUND, true)
            .putBoolean(FeedbackPreferences.SOUND, true).commit()
    }
    @After fun after() {
        stopMorse()
        prefs.edit().clear().apply { saved.forEach { (k, v) -> when (v) {
            is String -> putString(k,v); is Boolean -> putBoolean(k,v); is Int -> putInt(k,v); is Long -> putLong(k,v)
            is Float -> putFloat(k,v); is Set<*> -> putStringSet(k,v.filterIsInstance<String>().toSet())
        } } }.commit()
    }
    @Test fun audioStartsOnDownBeforeTheAnswerAndStopsOnReleaseOrCancel() {
        var received = ""
        ui.setContent { CompositionLocalProvider(LocalMorseLiveFactory provides factory) { HamigoTheme { MorseSignalInput { received += it } } } }
        val key = ui.onNodeWithTag("morse-single-key")
        key.performTouchInput { down(center) }
        ui.runOnIdle { assertEquals("",received); assertEquals(listOf("down"),outputs.last().events) }
        key.performTouchInput { advanceEventTime(100); up() }
        key.performTouchInput { down(center); advanceEventTime(400); cancel() }
        ui.runOnIdle { assertEquals(".",received); assertEquals(listOf("down","up","down","up"),outputs.last().events) }
        ui.runOnIdle { outputs.last().events.clear() }
        key.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        key.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick) { it() }
        ui.runOnIdle { assertEquals(listOf(".","-"),outputs.last().events) }
    }
    @Test fun bothMutesStopAHeldKeyAndDoNotDisableCompositionOrChangeCloudPreferences() {
        var received = ""
        ui.setContent { CompositionLocalProvider(LocalMorseLiveFactory provides factory) { HamigoTheme { MorseSignalInput { received += it } } } }
        val key = ui.onNodeWithTag("morse-single-key")
        key.performTouchInput { down(center) }
        val first = outputs.last()
        ui.runOnIdle { FeedbackPreferences.save(prefs,FeedbackSettings(sound=false)) }
        ui.waitUntil { first.closed }; key.performTouchInput { up() }
        val count = outputs.size
        key.performClick(); ui.runOnIdle { assertEquals(count,outputs.size); assertEquals("..",received) }
        ui.runOnIdle { FeedbackPreferences.save(prefs,FeedbackSettings(sound=true)) }
        ui.waitUntil { outputs.size > count }; val second = outputs.last()
        val updatedAt = prefs.getLong("preferencesUpdatedAt",0)
        ui.runOnIdle { GameplayPreferences.save(prefs,GameplayPreferences.read(prefs).copy(liveSound=false)) }
        ui.waitUntil { second.closed }; key.performClick()
        ui.runOnIdle {
            assertEquals("...",received); assertEquals(updatedAt,prefs.getLong("preferencesUpdatedAt",0))
            assertFalse(Progress(context,sideEffects=false).export().contains(GameplayPreferences.LIVE_SOUND))
        }
    }
    @Test fun twoButtonsAndAccessibilityProduceTheChosenSignalOnlyOnce() {
        prefs.edit().putBoolean(GameplayPreferences.SINGLE_KEY,false).commit()
        var received = ""
        ui.setContent { CompositionLocalProvider(LocalMorseLiveFactory provides factory) { HamigoTheme { MorseSignalInput { received += it } } } }
        ui.onNodeWithText("Point").performTouchInput { click() }
        ui.onNodeWithText("Trait").performClick()
        ui.runOnIdle { assertEquals(".-",received); assertEquals(listOf(".","-"),outputs.last().events) }
    }
    @Test fun disableDisposeAndBackgroundCloseTheOutputAndResumePreparesANewOne() {
        val enabled = mutableStateOf(true); val shown = mutableStateOf(true)
        val owner = object : LifecycleOwner { override val lifecycle = LifecycleRegistry(this) }
        ui.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        ui.setContent { CompositionLocalProvider(LocalMorseLiveFactory provides factory, LocalLifecycleOwner provides owner) {
            HamigoTheme { if (shown.value) MorseSignalInput(enabled.value) {} }
        } }
        val first = outputs.last(); ui.runOnIdle { enabled.value = false }; ui.waitUntil { first.closed }
        ui.runOnIdle { enabled.value = true }; ui.waitUntil { outputs.size == 2 }
        val second = outputs.last(); ui.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.STARTED }; ui.waitUntil { second.closed }
        ui.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }; ui.waitUntil { outputs.size == 3 }
        val third = outputs.last(); ui.runOnIdle { shown.value = false }; ui.waitUntil { third.closed }
    }
    @Test fun realAudioStreamPreparesAndReleasesPriorityOnPreemptionAndClose() {
        val output = MorseSidetone()
        try {
            ui.waitUntil(5000) { output.ready }
            output.key(true); assertTrue(FeedbackAudioGate.busy)
            val speech = Any(); FeedbackAudioGate.reserve(speech)
            output.key(true) // Voice has priority: this must not reserve the audio gate again.
            FeedbackAudioGate.release(speech)
            assertFalse(FeedbackAudioGate.busy)
            output.signal('-'); assertTrue(FeedbackAudioGate.busy)
            output.close(); ui.waitUntil(5000) { !output.running }
            assertFalse(FeedbackAudioGate.busy); assertFalse(output.ready)
        } finally { output.close() }
    }
}
