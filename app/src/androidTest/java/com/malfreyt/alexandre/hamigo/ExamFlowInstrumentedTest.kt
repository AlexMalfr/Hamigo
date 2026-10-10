package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.platform.DailyReminder
import com.malfreyt.alexandre.hamigo.platform.ProgressSyncScheduler
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExamFlowInstrumentedTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val store=ViewModelStore()
    private lateinit var isolated:IsolatedContext
    private lateinit var model:AppModel

    @Before fun setUp() {
        isolated=IsolatedContext(context)
        isolated.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().putBoolean("welcomed",true).putBoolean("autoSync",false).commit()
        instrumentation.runOnMainSync {
            model=ViewModelProvider(store,object:ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=AppModel() as T
            })[AppModel::class.java]
            model.initialize(isolated,startExamUpdates=false)
            model.startQuestions("Examen témoin",List(40) {index->Question("exam-flow-$index","Question $index",listOf("Oui","Non"),0,"Une explication",section=if(index<20)"regulation" else "technique")},exam=true)
        }
    }

    @After fun tearDown() {
        instrumentation.runOnMainSync {store.clear()}
        isolated.cleanup()
        DailyReminder.schedule(context); ProgressSyncScheduler.schedule(context)
    }

    @Test fun draftsCanBeChangedAndEachPhaseAwardsOnlyTheFinalResponses() {
        instrumentation.runOnMainSync {
            val s=model.session!!
            assertTrue(s.examIntroPending)
            model.answer(true)
            assertTrue(s.responses.isEmpty())
            model.beginExamPart()
            repeat(20) {model.answer(true,display="Oui",choiceIndex=0)}
            assertTrue(s.examReviewing)
            assertEquals(0,model.progress.xp)
            assertEquals(0,s.regulationScore)
            model.revisitExamQuestion(0)
            model.answer(false,display="Non",choiceIndex=1)
            assertEquals("Non",s.responses[0]!!.display)
            model.reviewExamPart(); model.finishExamPart()
            assertEquals(19,s.regulationScore)
            assertEquals(58,model.progress.xp)
            assertEquals(20,s.index)
            assertTrue(s.examIntroPending)
            model.finishExamPart()
            model.revisitExamQuestion(0)
            assertEquals(58,model.progress.xp)
            assertEquals(20,s.index)
            model.beginExamPart()
            repeat(20) {model.answer(true,display="Oui",choiceIndex=0)}
            model.finishExamPart()
            assertTrue(s.done)
            assertEquals(20,s.techniqueScore)
            assertEquals(118,model.progress.xp)
            model.finishExamPart()
            assertEquals(118,model.progress.xp)
            assertEquals(40,s.responses.size)
        }
    }

    @Test fun timeoutsFinalizeUnansweredQuestionsAndDoNotConsumeTheNextIntroduction() {
        instrumentation.runOnMainSync {
            val s=model.session!!
            model.beginExamPart()
            model.answer(true,display="Oui",choiceIndex=0)
            model.answer(false,display="Non",choiceIndex=1)
            s.started=System.currentTimeMillis()-16*60_000L
            model.timeoutExamPart()
            assertEquals(1,s.regulationScore)
            assertEquals(18,s.unanswered)
            assertEquals(4,model.progress.xp)
            assertTrue(s.examIntroPending)
            assertEquals(30*60_000L,s.examTimeRemaining)
            model.timeoutExamPart()
            assertEquals(18,s.unanswered)
            model.beginExamPart()
            s.started=System.currentTimeMillis()-31*60_000L
            model.timeoutExamPart()
            assertTrue(s.done)
            assertEquals(38,s.unanswered)
            assertEquals(45*60_000L,s.elapsedMillis)
            assertEquals(4,model.progress.xp)
        }
    }

    private class IsolatedContext(base:Context):ContextWrapper(base) {
        private val prefix="exam-flow-test-${UUID.randomUUID()}"
        private val names=mutableSetOf<String>()
        override fun getApplicationContext():Context=this
        override fun getSharedPreferences(name:String,mode:Int):SharedPreferences {
            val key="$prefix-$name"; synchronized(names) {names+=key}
            return baseContext.getSharedPreferences(key,mode)
        }
        fun cleanup() {synchronized(names) {names.toList()}.forEach {baseContext.deleteSharedPreferences(it)}}
    }
}
