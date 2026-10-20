package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.platform.CloudProgress
import com.malfreyt.alexandre.hamigo.platform.DailyReminder
import com.malfreyt.alexandre.hamigo.platform.ProgressSyncScheduler
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** Uses AppModel's real synchronous preference listener, not the listener-free diagnostics model. */
@RunWith(AndroidJUnit4::class)
class AtomicLearningAnswerInstrumentedTest {
    @get:Rule val ui=createComposeRule()
    private val target get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val store=ViewModelStore()
    private val now=1_800_000_000_000L
    private val clock=Clock.fixed(Instant.ofEpochMilli(now),ZoneOffset.UTC)
    private lateinit var isolated:IsolatedContext
    private lateinit var model:AppModel
    private val content get()=model.content!!

    @Before fun initializeRealModel() {
        isolated=IsolatedContext(target)
        isolated.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit()
            .putBoolean("welcomed",true).putBoolean("autoSync",false).commit()
        ui.runOnUiThread {
            model=ViewModelProvider(store,object:ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T:ViewModel> create(modelClass:Class<T>):T=AppModel() as T
            })[AppModel::class.java]
            model.initialize(isolated,startExamUpdates=false)
        }
        ui.waitUntil(60_000) {model.content!=null&&!model.socialRefreshing}
        ui.runOnIdle {
            model.progress=Progress(isolated,sideEffects=false,clock=clock)
            model.refresh()
        }
    }

    @After fun close() {
        ui.runOnUiThread {store.clear()}
        if(::isolated.isInitialized)isolated.cleanup()
        DailyReminder.schedule(target)
        ProgressSyncScheduler.schedule(target)
    }

    @Test fun firstSuccessReachesTheModelListenerWithItsSkillAndXpAlreadyRecorded() {
        val q=content.lessons.flatMap {it.questions}.first {content.skillsFor(it).isNotEmpty()}
        val published=mutableListOf<JSONObject>()
        val listener=SharedPreferences.OnSharedPreferenceChangeListener {prefs,key->
            if(key=="progress")published+=JSONObject(prefs.getString("progress","{}")!!)
        }
        model.progress.prefs.registerOnSharedPreferenceChangeListener(listener)
        try {
            ui.runOnIdle {
                model.startQuestions("Première réussite",listOf(q))
                model.answer(true,choiceIndex=q.answer)
                assertEquals(3,model.session!!.gain)
                assertEquals(1,published.size)
                val state=published.single()
                assertEquals(3,state.getInt("xp"))
                assertEquals(1,state.getInt("answers"))
                assertEquals(1,state.getJSONObject("syncEvents").length())
                content.skillsFor(q).forEach {id->
                    val observed=SkillLearning.read(state.getJSONObject("skillReviews")).getValue(id)
                    assertFalse(observed.seeded)
                    assertEquals(1,observed.attempts)
                    assertEquals(1,observed.repetitions)
                    assertEquals(0,observed.lapses)
                    assertEquals(now+86_400_000,observed.due)
                    assertEquals(observed,model.displayedProgress!!.skillReviews.getValue(id))
                }
            }
        } finally {model.progress.prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }

    @Test fun firstFailureAndItsCorrectionCountOneSkillFailureAndKeepBothUuidAnswerEvents() {
        val q=content.lessons.flatMap {it.questions}.first {content.skillsFor(it).isNotEmpty()}
        ui.runOnIdle {
            model.startQuestions("Erreur puis correction",listOf(q))
            model.answer(false)
            content.skillsFor(q).forEach {id->
                val failed=model.displayedProgress!!.skillReviews.getValue(id)
                assertFalse(failed.seeded)
                assertEquals(1,failed.lapses)
                assertEquals(1,failed.attempts)
            }
            model.next()
            assertEquals(q.id,model.session!!.current!!.id)
            model.answer(true,choiceIndex=model.session!!.current!!.answer)
            content.skillsFor(q).forEach {id->
                val corrected=model.displayedProgress!!.skillReviews.getValue(id)
                assertEquals(1,corrected.lapses)
                assertEquals(1,corrected.attempts)
                assertEquals(0,corrected.successes)
                assertEquals(0,corrected.repetitions)
                assertFalse(corrected.mastered)
                assertEquals(now+600_000,corrected.due)
            }
            assertEquals(1,model.progress.xp)
            assertEquals(2,model.progress.totalAnswers)
            assertEquals(1,model.progress.totalCorrect)
            assertEquals(1,model.session!!.gain)
            val state=JSONObject(model.progress.export()).getJSONObject("progress")
            assertEquals(2,state.getJSONObject("syncEvents").length())
            assertEquals(1,state.getJSONObject("reviews").length())
        }
    }

    @Test fun legacyImportThenAtomicRecallSurvivesMergeWithoutLosingEventsOrReplayingXp() {
        val questions=content.lessons.flatMap {it.questions}.filter {content.skillsFor(it).isNotEmpty()}
        val oldQuestion=questions.first()
        val oldSkills=content.skillsFor(oldQuestion).toSet()
        val freshQuestion=questions.first {content.skillsFor(it).none(oldSkills::contains)}
        val legacy=Progress(DiagnosticContext(target),sideEffects=false,clock=clock)
        legacy.answer(oldQuestion.id,false)
        legacy.complete("retained-legacy-lesson")
        val original=legacy.export()
        val previousEvents=JSONObject(original).getJSONObject("progress").getJSONObject("syncEvents")
        ui.runOnIdle {
            model.restore(original)
            oldSkills.forEach {id->
                val seeded=model.progress.skillReviews.getValue(id)
                assertTrue(seeded.seeded)
                assertEquals(0,seeded.repetitions)
                assertEquals(1,seeded.lapses)
            }
            model.startQuestions("Nouveau rappel",listOf(freshQuestion))
            model.answer(true,choiceIndex=freshQuestion.answer)
            val exported=model.progress.export()
            val state=JSONObject(exported).getJSONObject("progress")
            assertEquals(10,state.getInt("xp"))
            assertEquals(2,state.getInt("answers"))
            assertEquals(1,state.getInt("correct"))
            assertEquals(previousEvents.length()+1,state.getJSONObject("syncEvents").length())
            previousEvents.keys().forEach {id->assertEquals(
                CloudProgress.canonical(previousEvents.getJSONObject(id)),
                CloudProgress.canonical(state.getJSONObject("syncEvents").getJSONObject(id))) }
            assertTrue("retained-legacy-lesson" in model.progress.completed)
            content.skillsFor(freshQuestion).forEach {id->
                assertEquals(1,model.progress.skillReviews.getValue(id).repetitions)
                assertFalse(model.progress.skillReviews.getValue(id).seeded)
            }
            val merged=CloudProgress.merge(exported,original)
            assertEquals(CloudProgress.canonical(JSONObject(merged)),
                CloudProgress.canonical(JSONObject(CloudProgress.merge(original,exported))))
            model.progress.import(merged)
            assertEquals(10,model.progress.xp)
            assertEquals(2,model.progress.totalAnswers)
            assertEquals(2,model.progress.reviews.size)
            assertTrue("retained-legacy-lesson" in model.progress.completed)
            content.skillsFor(freshQuestion).forEach {id->assertEquals(1,model.progress.skillReviews.getValue(id).repetitions)}
            val beforeInvalid=model.progress.export()
            val invalid=JSONObject(beforeInvalid).apply {getJSONObject("progress").put("skillReviews",JSONArray())}
            assertThrows(Exception::class.java) {model.progress.import(invalid.toString())}
            assertEquals(beforeInvalid,model.progress.export())
        }
    }

    @Test fun aLateSessionCorrectionCannotBecomeNewSkillEvidenceAfterItsSampleWasEvicted() {
        val base=content.lessons.flatMap {it.questions}.first {content.skillsFor(it).isNotEmpty()}
        ui.runOnIdle {
            val questions=(0 until 100).map {base.copy(id="long-session-$it")}
            model.startQuestions("Une longue séance",questions)
            val session=model.session!!
            val failed=questions.maxBy {UUID.nameUUIDFromBytes("${session.attemptId}:${it.id}".toByteArray()).toString()}
            val first=session.questions.removeAt(session.questions.indexOfFirst {it.id==failed.id})
            session.questions.add(0,first)
            repeat(questions.size) {n->
                model.progress=Progress(isolated,sideEffects=false,clock=Clock.offset(clock,java.time.Duration.ofMillis(n.toLong())))
                val q=model.session!!.current!!
                model.answer(q.id!=failed.id,choiceIndex=q.answer)
                model.next()
            }
            assertEquals(failed.id,model.session!!.current!!.id)
            val before=model.progress.skillReviews
            content.skillsFor(failed).forEach {id->
                val skill=before.getValue(id)
                val attemptId=UUID.nameUUIDFromBytes("${session.attemptId}:${failed.id}".toByteArray()).toString()
                assertFalse(skill.history.containsKey(attemptId))
                assertNotEquals(attemptId,skill.lastAttemptId)
                assertEquals(100,skill.attempts)
                assertEquals(1,skill.lapses)
                assertFalse(skill.mastered)
            }
            val previousXp=model.progress.xp
            model.answer(true,choiceIndex=model.session!!.current!!.answer)
            assertEquals(before,model.progress.skillReviews)
            assertEquals(previousXp,model.progress.xp)
            assertEquals(101,model.progress.totalAnswers)
            assertEquals(100,model.progress.totalCorrect)
        }
    }

    private class IsolatedContext(base:Context):ContextWrapper(base) {
        private val prefix="atomic-learning-${UUID.randomUUID()}"
        private val names=mutableSetOf<String>()
        override fun getApplicationContext():Context=this
        override fun getSharedPreferences(name:String,mode:Int):SharedPreferences {
            val unique="$prefix-$name"
            synchronized(names) {names+=unique}
            return baseContext.getSharedPreferences(unique,mode)
        }
        fun cleanup()=synchronized(names) {names.toList()}.forEach {baseContext.deleteSharedPreferences(it)}
    }
}
