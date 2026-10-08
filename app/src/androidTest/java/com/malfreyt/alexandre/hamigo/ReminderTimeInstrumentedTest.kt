package com.malfreyt.alexandre.hamigo

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.view.View
import android.widget.TimePicker
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.platform.DailyReminder
import com.malfreyt.alexandre.hamigo.platform.ProgressSyncScheduler
import org.hamcrest.Matcher
import org.json.JSONObject
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
class ReminderTimeInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val saved = mutableMapOf<String, Map<String, Any?>>()
    private val intents = CopyOnWriteArrayList<Intent>()
    private val monitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action in listOf(Intent.ACTION_VIEW, Intent.ACTION_SENDTO)) {
                intents += Intent(intent)
                return Instrumentation.ActivityResult(0, null) // Never open or send a real report.
            }
            return null
        }
    }
    private val fixture = object : ExternalResource() {
        override fun before() {
            check(Build.HARDWARE in listOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("generic"))
            for (file in listOf("hamigo", "hamigo_social", "hamigo_secure")) {
                val prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE)
                saved[file] = prefs.all.mapValues { (_, v) -> if (v is Set<*>) v.toSet() else v }
                check(prefs.edit().clear().commit())
            }
            check(context.getSharedPreferences("hamigo", Context.MODE_PRIVATE).edit()
                .putBoolean("welcomed", true).putBoolean("autoSync", false)
                .putBoolean("reminderEnabled", false).putInt("reminderHour", 20).putInt("reminderMinute", 0).commit())
            ProgressSyncScheduler.cancel(context)
            instrumentation.addMonitor(monitor)
        }
        override fun after() {
            instrumentation.removeMonitor(monitor)
            saved.forEach { (file, values) ->
                val edit = context.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear()
                values.forEach { (k, v) -> when (v) {
                    is String -> edit.putString(k,v); is Int -> edit.putInt(k,v); is Long -> edit.putLong(k,v)
                    is Float -> edit.putFloat(k,v); is Boolean -> edit.putBoolean(k,v)
                    is Set<*> -> edit.putStringSet(k,v.filterIsInstance<String>().toSet())
                } }
                check(edit.commit())
            }
            ProgressSyncScheduler.schedule(context)
            DailyReminder.schedule(context)
        }
    }
    private val ui = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(ui)
    private lateinit var model: AppModel
    @Before fun load() {
        model = ViewModelProvider(ui.activity)[AppModel::class.java]
        ui.waitUntil(60_000) { model.content != null }
        ui.runOnIdle { model.showWelcome = false }
    }

    @Test fun nativeReminderPickerCancelAndConfirmImmediatelySaveWithoutEnablingReminder() {
        ui.runOnIdle { model.progress.answer("time-fixture",true); model.refresh(); model.route="settings" }
        val before = model.progress.export()
        ui.onNodeWithTag("reminder-time-picker").performScrollTo().performClick()
        onView(isAssignableFrom(TimePicker::class.java)).check { view, error -> if (error != null) throw error; assertNotNull(view) }
        capture("reminder-native-picker")
        onView(withId(android.R.id.button2)).perform(click())
        ui.onNodeWithText("20:00").assertExists()
        ui.onNodeWithTag("reminder-time-picker").performClick()
        onView(isAssignableFrom(TimePicker::class.java)).perform(object : ViewAction {
            override fun getConstraints(): Matcher<View> = isAssignableFrom(TimePicker::class.java)
            override fun getDescription() = "Set native clock to 07:35"
            override fun perform(controller: UiController, view: View) { (view as TimePicker).apply { hour=7; minute=35 }; controller.loopMainThreadUntilIdle() }
        })
        onView(withId(android.R.id.button1)).perform(click())
        ui.onNodeWithText("07:35").assertExists()
        ui.onNodeWithTag("save-reminder-time").assertDoesNotExist()
        ui.runOnIdle {
            assertEquals(7,model.progress.prefs.getInt("reminderHour",-1))
            assertEquals(35,model.progress.prefs.getInt("reminderMinute",-1))
            assertFalse(model.progress.prefs.getBoolean("reminderEnabled",true))
            assertEquals(JSONObject(before).getJSONObject("progress").toString(),JSONObject(model.progress.export()).getJSONObject("progress").toString())
        }
        ui.onNodeWithText("Tester le rappel").assertIsDisplayed()
        val clock=ui.onNodeWithTag("reminder-time-picker").fetchSemanticsNode().boundsInRoot
        val test=ui.onNodeWithTag("test-reminder").fetchSemanticsNode().boundsInRoot
        assertTrue(test.left>=clock.right)
        assertEquals(clock.center.y,test.center.y,1f)
        ui.onNodeWithTag("reminder-information",useUnmergedTree=true).assertExists()
        capture("reminder-saved-layout")
    }

    private fun capture(name: String) {
        ui.waitForIdle()
        repeat(2) {
            val frame=CountDownLatch(1)
            instrumentation.runOnMainSync {ui.activity.window.decorView.let {v->v.viewTreeObserver.registerFrameCommitCallback {frame.countDown()};v.invalidate()} }
            assertTrue(frame.await(5,TimeUnit.SECONDS))
        }
        val dir=File(context.getExternalFilesDir(null),"ui-0.38-correction").apply {mkdirs()}
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        try {File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}
    }
}
