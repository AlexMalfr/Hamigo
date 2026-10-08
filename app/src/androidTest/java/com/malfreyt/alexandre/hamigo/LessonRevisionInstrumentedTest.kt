package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.BackEventCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.platform.ProgressSyncScheduler
import com.malfreyt.alexandre.hamigo.platform.CloudProgress
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LessonRevisionInstrumentedTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private var saved=emptyMap<String,Any?>()
    private val fixture=object:ExternalResource() {
        override fun before() {
            check(Build.HARDWARE in listOf("ranchu","goldfish") || Build.FINGERPRINT.contains("generic")) {"Emulator fixtures only"}
            val prefs=context.getSharedPreferences("hamigo",Context.MODE_PRIVATE)
            saved=prefs.all.mapValues {(_,v)->if(v is Set<*>)v.toSet() else v}
            check(prefs.edit().clear().putBoolean("welcomed",true).putBoolean("autoSync",false).putString("name","Cours test").commit())
            ProgressSyncScheduler.cancel(context)
        }
        override fun after() {
            val edit=context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().clear()
            saved.forEach {(k,v)->when(v) {
                is String->edit.putString(k,v);is Int->edit.putInt(k,v);is Long->edit.putLong(k,v)
                is Float->edit.putFloat(k,v);is Boolean->edit.putBoolean(k,v);is Set<*>->edit.putStringSet(k,v.filterIsInstance<String>().toSet())
            }}
            check(edit.commit());stopMorse();ProgressSyncScheduler.schedule(context)
        }
    }
    private val ui=createAndroidComposeRule<MainActivity>()
    @get:Rule val rules:RuleChain=RuleChain.outerRule(fixture).around(ui)
    private lateinit var model:AppModel
    @Before fun load() {
        model=ViewModelProvider(ui.activity)[AppModel::class.java]
        ui.waitUntil(60_000) {model.content!=null}
        ui.runOnIdle {model.showWelcome=false;model.route="path"}
    }

    @Test fun courseMenuReadsCompletedAndUnfinishedLessonsWithoutStartingQuestions() {
        val content=model.content!!
        val first=content.lessons.first();val second=content.lessons[1]
        assertEquals(97,content.lessons.size)
        assertTrue(content.lessons.all {content.referencesFor(it).isNotEmpty()})
        assertEquals(40,content.courseSources.size)
        ui.runOnIdle {model.progress.complete(first.id);model.refresh()}
        ui.onNodeWithTag("path-list").performScrollToIndex(3)
        ui.onNodeWithText(first.title+" ✅").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText(first.summary).assertIsDisplayed()
        val firstMenu=ui.onNodeWithTag("lesson-menu-${first.id}").fetchSemanticsNode().boundsInRoot
        val secondMenu=ui.onNodeWithTag("lesson-menu-${second.id}").fetchSemanticsNode().boundsInRoot
        assertEquals("Les menus restent sur le même bord",firstMenu.center.x,secondMenu.center.x,1f)
        val firstCopy=ui.onNodeWithTag("lesson-copy-${first.id}").fetchSemanticsNode().boundsInRoot
        val secondCopy=ui.onNodeWithTag("lesson-copy-${second.id}").fetchSemanticsNode().boundsInRoot
        assertEquals("Les cours gardent leur indentation alternée",with(ui.density){22.dp.toPx()},secondCopy.left-firstCopy.left,1f)
        capture("path-completed-next")
        for(lesson in listOf(first,second)) {
            ui.onNodeWithTag("lesson-menu-${lesson.id}").performScrollTo().performClick()
            capture("course-menu-${lesson.id}")
            ui.onNodeWithText("Lire le cours").performClick()
            ui.runOnIdle {assertTrue(model.lessonPreviewOnly);assertNull(model.session);assertEquals(lesson,model.lesson)}
            ui.onNodeWithText("À toi de jouer ·",substring=true).assertDoesNotExist()
            capture("course-readonly-${lesson.id}")
            val category=content.referencesFor(lesson).first()
            val row="lesson-memo-${category.id}"
            ui.onNodeWithTag("lesson-introduction-list").performScrollToNode(hasTestTag(row))
            capture("course-linked-memos-${lesson.id}")
            val position=ui.onNodeWithTag(row).fetchSemanticsNode().boundsInRoot
            val answers=model.progress.totalAnswers
            ui.onNodeWithTag(row).performClick()
            ui.runOnIdle {assertEquals(category,model.resource);assertEquals(lesson,model.lesson)}
            capture("course-memo-${lesson.id}")
            ui.onNodeWithContentDescription("Retour au cours").performClick()
            ui.onNodeWithTag(row).assertIsDisplayed()
            assertEquals(position.top,ui.onNodeWithTag(row).fetchSemanticsNode().boundsInRoot.top,1f)
            ui.runOnIdle {assertEquals(answers,model.progress.totalAnswers);ui.activity.onBackPressedDispatcher.onBackPressed()}
        }
        ui.runOnIdle {model.startLesson(first)}
        ui.onNodeWithText("À toi de jouer ·",substring=true).assertIsDisplayed()
        capture("course-normal-introduction")
    }

    @Test fun linkedMemoPredictiveBackCancelsOrMorphsIntoItsCourseRow() {
        val lesson=model.content!!.lessons.first {model.content!!.referencesFor(it).size>=2}
        val category=model.content!!.referencesFor(lesson).last()
        ui.runOnIdle {model.previewLesson(lesson)}
        val row="lesson-memo-${category.id}"
        ui.onNodeWithTag("lesson-introduction-list").performScrollToNode(hasTestTag(row))
        ui.onNodeWithTag(row).performClick()
        fun drag() {
            ui.runOnIdle {ui.activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f,200f,0f,BackEventCompat.EDGE_LEFT))}
            ui.runOnIdle {ui.activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(120f,200f,.6f,BackEventCompat.EDGE_LEFT))}
            ui.waitForIdle()
        }
        drag();capture("course-memo-back-drag")
        ui.runOnIdle {ui.activity.onBackPressedDispatcher.dispatchOnBackCancelled()}
        ui.waitForIdle()
        ui.runOnIdle {assertEquals(category,model.resource);assertEquals(lesson,model.lesson);assertTrue(model.lessonPreviewOnly)}
        drag()
        ui.runOnIdle {ui.activity.onBackPressedDispatcher.onBackPressed()}
        ui.waitUntil(5_000) {model.resource==null}
        ui.onNodeWithTag(row).assertIsDisplayed()
        ui.runOnIdle {assertEquals(lesson,model.lesson);assertEquals("path",model.route);assertTrue(model.lessonPreviewOnly);assertNull(model.session)}
        ui.onNodeWithText("À toi de jouer ·",substring=true).assertDoesNotExist()
        capture("course-memo-back-complete")
    }

    @Test fun revisionCardUsesStudiedQuestionsAndCorrectAnswersRescheduleDueItems() {
        ui.runOnIdle {model.route="practice"}
        ui.onNodeWithTag("practice-list").performScrollToNode(hasText("Lancer les révisions"))
        ui.onNodeWithText("Lancer les révisions").assertIsNotEnabled()
        capture("revisions-empty")
        val lesson=model.content!!.lessons.first()
        ui.runOnIdle {
            model.progress.complete(lesson.id)
            lesson.questions.forEach {model.progress.answer(it.id,true)}
            model.progress.answer("exam-not-studied",false)
            val backup=JSONObject(model.progress.cloudExport())
            val reviews=backup.getJSONObject("progress").getJSONObject("reviews")
            lesson.questions.take(3).forEach {reviews.getJSONObject(it.id).put("due",1)}
            model.progress.import(backup.toString());model.refresh()
            assertEquals(3,model.progress.due(model.content!!).size)
        }
        val eligible=CourseRevisionBuilder.eligible(model.content!!.lessons,model.progress.completed,model.progress.reviews)
        ui.onNodeWithTag("practice-list").performScrollToIndex(3)
        ui.onNodeWithText("Réviser · ${minOf(10,eligible.size)} questions").performScrollTo().assertIsDisplayed()
        capture("revisions-ready")
        ui.onNodeWithText("Réviser · ${minOf(10,eligible.size)} questions").performClick()
        ui.runOnIdle {
            val session=model.session!!
            assertEquals("practice",session.returnRoute);assertNull(session.lessonId)
            assertTrue(session.questions.all {it.id in eligible.map {q->q.id}})
            val dueIds=lesson.questions.take(3).map {it.id}.toSet()
            assertTrue(session.questions.first().id !in dueIds)
            val index=session.questions.indexOfFirst {it.id in dueIds}
            assertTrue(index>0)
            repeat(index) {model.answer(true);model.next()}
            val id=session.current!!.id;val previous=model.progress.reviews.getValue(id)
            model.answer(true)
            val updated=model.progress.reviews.getValue(id)
            assertTrue(updated.due>System.currentTimeMillis());assertEquals(previous.repetitions+1,updated.repetitions)
            assertEquals(setOf(lesson.id),model.progress.completed)
        }
        capture("revisions-srs-answer")
    }

    @Test fun memoAndExamRappelsMatchParcoursEvenWithoutAnyCompletedLesson() {
        val content=model.content!!
        val memo=content.flashcards.first();val exam=content.activeExam.first()
        val ids=setOf(memo.id,exam.id)
        ui.runOnIdle {
            listOf(memo,exam).forEach {model.progress.answer(it.id,false)}
            val backup=JSONObject(model.progress.cloudExport())
            ids.forEach {backup.getJSONObject("progress").getJSONObject("reviews").getJSONObject(it).put("due",1)}
            model.progress.import(backup.toString());model.refresh()
            assertTrue(model.progress.completed.isEmpty())
            assertEquals(ids,model.progress.due(content).map {it.id}.toSet())
        }
        ui.onNodeWithText("2 notions à revoir").performScrollTo().assertIsDisplayed()
        capture("parcours-two-memo-exam-rappels")
        ui.runOnIdle {model.route="practice"}
        ui.onNodeWithTag("practice-list").performScrollToIndex(3)
        ui.onNodeWithText("RÉVISIONS",substring=false).assertIsDisplayed()
        ui.onNodeWithText("(2 à revoir).",substring=true).assertIsDisplayed()
        ui.onNodeWithText("2 à revoir",substring=false).assertDoesNotExist()
        ui.onNodeWithText("Réviser · 2 questions").assertIsEnabled()
        capture("revisions-two-memo-exam-rappels")
        ui.onNodeWithText("Réviser · 2 questions").performClick()
        ui.runOnIdle {
            assertEquals(ids,model.session!!.questions.map {it.id}.toSet())
            repeat(2) {model.answer(true);model.next()}
            assertTrue(model.progress.due(content).isEmpty())
            assertTrue(model.progress.completed.isEmpty())
            model.leaveSession();model.refresh()
        }
        ui.onNodeWithTag("practice-list").performScrollToIndex(3)
        ui.onNodeWithText("(0 à revoir).",substring=true).assertIsDisplayed()
        capture("revisions-rappels-completed")
    }

    @Test fun dailyGoalKeepsItsOrdinalThroughBackupAndDoesNotRewritePreferences() {
        ui.runOnIdle {
            val p=model.progress
            DailyGoals.keys.forEachIndexed {index,key->
                p.prefs.edit().putInt("dailyGoal",key).putLong("preferencesUpdatedAt",123L).commit()
                assertEquals(index,p.dailyGoalChoice);assertEquals(DailyGoals.values[index],p.dailyGoal)
                assertEquals(key,p.prefs.getInt("dailyGoal",0));assertEquals(123L,p.prefs.getLong("preferencesUpdatedAt",0))
                val backup=p.cloudExport()
                assertEquals(key,JSONObject(backup).getJSONObject("preferences").getInt("dailyGoal"))
                p.setDailyGoalChoice((index+1)%4);p.import(backup)
                assertEquals(index,p.dailyGoalChoice);assertEquals(DailyGoals.values[index],p.dailyGoal)
            }
            val remote=JSONObject(p.cloudExport())
            val before=p.xp
            CloudProgress.recordEvent(remote.getJSONObject("progress"),java.time.LocalDate.now().toString(),3,answers=1,correct=1)
            remote.put("name","Autre appareil").put("profileUpdatedAt",System.currentTimeMillis()+10_000)
            remote.put("preferencesUpdatedAt",System.currentTimeMillis()+10_000)
            p.mergeCloud(remote.toString())
            assertEquals(before+3,p.xp);assertEquals("Autre appareil",p.name)
            assertEquals(3,p.dailyGoalChoice);assertEquals(240,p.dailyGoal)
            model.refresh();model.route="settings"
        }
        ui.onNodeWithText("240 XP",substring=false).performScrollTo().assertIsSelected()
        capture("goals-settings-intensive")
        ui.runOnIdle {model.route="path";model.showWelcome=true}
        ui.onNodeWithTag("onboarding-name").performTextInput("Objectif test")
        ui.onNodeWithText("Continuer",substring=false).performClick()
        ui.onNodeWithText("240 XP",substring=false).assertIsDisplayed()
        capture("goals-onboarding")
    }

    private fun capture(name:String) {
        ui.waitForIdle()
        repeat(2) {
            val frame=CountDownLatch(1)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                ui.activity.window.decorView.let {view->view.viewTreeObserver.registerFrameCommitCallback {frame.countDown()};view.invalidate()}
            }
            assertTrue(frame.await(5,TimeUnit.SECONDS))
        }
        val dir=File(context.getExternalFilesDir(null),"lesson-revision-0.38-r2").apply {mkdirs()}
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try {File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}
    }
}
