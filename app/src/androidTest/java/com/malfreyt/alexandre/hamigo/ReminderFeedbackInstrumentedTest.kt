package com.malfreyt.alexandre.hamigo

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
import com.malfreyt.alexandre.hamigo.platform.CloudProgress
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
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ReminderFeedbackInstrumentedTest {
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

    @Test fun questionAndMemoFeedbackUseStableEncodedMetadataWithoutPersonalData() {
        val q=Question("fixture / é & ?", "Énoncé", listOf("A","B"),0,"Explication",topic="Ohm & RF",kind="choice")
        ui.runOnIdle {model.startQuestions("Signalements",listOf(q),lessonId="lesson & 1")}
        ui.onNodeWithTag("question-feedback").assertIsDisplayed()
        capture("question-flag-header")
        ui.onNodeWithTag("question-feedback").performClick()
        assertEquals(1,intents.size)
        val uri=intents.last().data!!
        assertEquals(Uri.parse(ContentFeedback.FORM_URL).host,uri.host)
        assertEquals(q.id,uri.getQueryParameter("question_id"));assertEquals(q.topic,uri.getQueryParameter("topic"))
        assertEquals("1",uri.getQueryParameter("question_number"));assertEquals("1",uri.getQueryParameter("question_count"))
        assertEquals("lesson & 1",uri.getQueryParameter("lesson_id"))
        assertEquals(BuildConfig.VERSION_NAME,uri.getQueryParameter("app_version"))
        assertEquals(q.prompt,uri.getQueryParameter("title"))
        assertEquals("Signalements",uri.getQueryParameter("session_title"))
        assertTrue(uri.queryParameterNames.none { it.contains("answer") || it.contains("token") || it.contains("gist") || it.contains("user") })
        assertFalse(ContentFeedback.isExam1(q.copy(source="https://exam1.r-e-f.org.evil.test/questions-listing")))
        ui.runOnIdle { assertEquals(0,model.session!!.index);assertTrue(model.session!!.responses.isEmpty());model.session=null;model.lesson=null;model.route="resources";model.resource=model.content!!.references.first {it.id=="nato"} }
        val cat=model.resource!!
        ui.onNodeWithTag("memo-feedback").assertDoesNotExist() // Footer is not in the sticky header.
        ui.onNodeWithTag("memo-detail-list").performScrollToNode(hasTestTag("memo-feedback"))
        ui.onNodeWithTag("memo-feedback").assertIsDisplayed()
        capture("memo-report-footer")
        ui.onNodeWithTag("memo-feedback").performClick()
        assertEquals(2,intents.size)
        assertEquals(cat.id,intents.last().data!!.getQueryParameter("memo_id"))
        assertEquals(cat.title,intents.last().data!!.getQueryParameter("title"))
    }

    @Test fun actualMixIncludesMetadataForProceduralExam1AndNoMailClientFallback() {
        ui.runOnIdle {model.route="practice"}
        ui.onNodeWithText("Lancer mon mix").performScrollTo().performClick()
        val session=model.session!!
        assertEquals(20,session.questions.size)
        val generatedIndex=session.questions.indexOfFirst {!ContentFeedback.isExam1(it)}
        val examIndex=session.questions.indexOfFirst {ContentFeedback.isExam1(it)}
        assertTrue(generatedIndex>=0);assertTrue(examIndex>=0)
        for(index in listOf(generatedIndex,examIndex)) {
            ui.runOnIdle {session.index=index;model.refresh()}
            val q=session.current!!
            ui.onNodeWithTag("question-feedback").performClick()
            if(ContentFeedback.isExam1(q))ui.onNodeWithText("Signaler à Hamigo").performClick()
            val uri=intents.last().data!!
            assertEquals(q.id,uri.getQueryParameter("question_id"))
            assertEquals(q.prompt,uri.getQueryParameter("title"))
            assertEquals((index+1).toString(),uri.getQueryParameter("question_number"))
            assertEquals("20",uri.getQueryParameter("question_count"))
            assertEquals(session.title,uri.getQueryParameter("session_title"))
            assertEquals(BuildConfig.VERSION_NAME,uri.getQueryParameter("app_version"))
            assertEquals(BuildConfig.VERSION_CODE.toString(),uri.getQueryParameter("app_version_code"))
            assertEquals(Build.MODEL,uri.getQueryParameter("device_model"))
            assertNotNull(uri.getQueryParameter("locale"));assertNotNull(uri.getQueryParameter("android_api"))
        }
        val outgoing=mutableListOf<Intent>()
        val noMailClient=object:ContextWrapper(context) {
            override fun startActivity(intent:Intent) {
                if(intent.action==Intent.ACTION_SENDTO)throw ActivityNotFoundException("Isolated no-mail-client fixture")
                outgoing+=intent
            }
        }
        ui.runOnIdle {ContentFeedback.openExam1Contact(noMailClient,session.current!!,session.index,20,null,session.title)}
        val fallback=outgoing.single().data!!
        assertEquals(Uri.parse(ContentFeedback.EXAM1_CONTACT_PAGE).host,fallback.host)
        assertEquals(session.current!!.id,fallback.getQueryParameter("question_id"))
        assertEquals(BuildConfig.VERSION_NAME,fallback.getQueryParameter("app_version"))
        assertEquals(session.title,fallback.getQueryParameter("session_title"))
        assertTrue(session.responses.isEmpty())
        capture("actual-mix-feedback")
    }

    @Test fun exam1FeedbackExplainsSourceAndOpensContactDraftOrHamigoFormWithoutLeavingSession() {
        val q=model.content!!.exam.first()
        assertTrue(ContentFeedback.isExam1(q))
        ui.runOnIdle {model.startQuestions("Exam1",listOf(q))}
        ui.onNodeWithTag("question-feedback").performClick()
        ui.onNodeWithText("Question Exam1").assertIsDisplayed();assertTrue(intents.isEmpty())
        capture("exam1-feedback-dialog")
        ui.onNodeWithText("Annuler").performClick()
        ui.runOnIdle {assertEquals(q,model.session!!.current)}
        ui.onNodeWithTag("question-feedback").performClick()
        ui.onNodeWithText("Contacter Exam1").performClick()
        assertEquals(Intent.ACTION_SENDTO,intents.last().action)
        val draft=intents.last().data!!
        assertEquals("mailto",draft.scheme);assertEquals("jfortin@club.fr",draft.schemeSpecificPart.substringBefore('?'))
        // Mailto URIs are opaque: query decoded through the complete address string.
        assertTrue(Uri.decode(draft.toString()).contains(q.source));assertTrue(Uri.decode(draft.toString()).contains(q.id))
        assertTrue(Uri.decode(draft.toString()).contains(BuildConfig.VERSION_NAME))
        assertTrue(Uri.decode(draft.toString()).contains("question_id="))
        ui.onNodeWithTag("question-feedback").performClick()
        ui.onNodeWithText("Signaler à Hamigo").performClick()
        assertEquals(q.id,intents.last().data!!.getQueryParameter("question_id"))
        ui.runOnIdle {assertEquals(q,model.session!!.current);assertTrue(model.session!!.responses.isEmpty())}
    }

    @Test fun goalCompletionAndCompactTeamDateAreVisibleWithoutDuplicatingSyncSubtitle() {
        val date=LocalDate.now()
        ui.runOnIdle {
            val backup=JSONObject(model.progress.export())
            val root=backup.getJSONObject("progress")
            listOf(0L to 12,1L to 60,2L to 90,4L to 3).forEach {(offset,xp)->CloudProgress.recordEvent(root,date.minusDays(offset).toString(),xp)}
            model.progress.mergeCloud(backup.toString());model.progress.setDailyGoalChoice(1);model.refresh();model.route="profile"
            assertEquals(60,model.progress.dayXp(date.minusDays(1)))
            assertEquals(12,model.progress.dayXp(date))
        }
        ui.onNodeWithTag("profile-list").performScrollToNode(hasTestTag("activity-calendar"))
        capture("profile-goals-calendar")
        ui.onNodeWithTag("profile-list").performScrollToNode(hasTestTag("weekly-goal-line"))
        ui.onNodeWithTag("weekly-goal-line",useUnmergedTree=true).assertExists()
        capture("profile-goals-week")
        ui.runOnIdle {
            model.sync.tokens.store("local-ui-fixture-only-not-a-real-token")
            context.getSharedPreferences("hamigo_social",Context.MODE_PRIVATE).edit()
                .putString("ownerLogin","Fixture").putString("lastSyncedAt","2026-10-08T10:15:00Z").commit()
            model.refresh();model.route="friends"
        }
        ui.onNodeWithTag("team-last-sync").assertIsDisplayed()
        ui.onNodeWithContentDescription("Afficher les réglages de synchronisation").performClick()
        ui.onAllNodes(hasText("Dernière synchro",substring=true)).assertCountEquals(1)
        capture("team-sync-date")
        ui.runOnIdle {model.route="settings"}
        ui.onNodeWithTag("settings-list").performScrollToNode(hasText("Dernière synchro",substring=true))
        capture("settings-sync-date-retained")
    }

    private fun capture(name: String) {
        ui.waitForIdle()
        repeat(2) {
            val frame=CountDownLatch(1)
            instrumentation.runOnMainSync {ui.activity.window.decorView.let {v->v.viewTreeObserver.registerFrameCommitCallback {frame.countDown()};v.invalidate()} }
            assertTrue(frame.await(5,TimeUnit.SECONDS))
        }
        val dir=File(context.getExternalFilesDir(null),"ui-0.42-correction").apply {mkdirs()}
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        try {File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}
    }
}
