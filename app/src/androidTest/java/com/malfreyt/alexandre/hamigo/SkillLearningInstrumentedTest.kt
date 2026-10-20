package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.platform.CloudProgress
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SkillLearningInstrumentedTest {
    @Test fun curriculumMigrationPreservesLegacyLedgerAndOnlySeedsExposure() = isolated {context->
        val content=Content(context,examSnapshot=null)
        val question=content.lessons.flatMap {it.questions}.first {content.skillsFor(it).isNotEmpty()}
        val progress=Progress(context,sideEffects=false,clock=Clock.fixed(Instant.ofEpochMilli(1_800_000_000_000),ZoneOffset.UTC))
        progress.answer(question.id,false)
        val legacy=content.lessons.firstOrNull {it.legacyLessonIds.size==1}
        legacy?.let {progress.complete(it.legacyLessonIds.single())}
        val oldBackup=JSONObject(progress.export()).apply {getJSONObject("progress").remove(LessonCompletionMigration55.FIELD)}
        progress.import(oldBackup.toString())
        val before=JSONObject(progress.export()).getJSONObject("progress")
        assertTrue(progress.prepareLearning(content))
        val after=JSONObject(progress.export()).getJSONObject("progress")
        for(key in listOf("xp","answers","correct","syncBase","syncEvents","awarded","reviews","dailyXp")) {
            assertEquals("Migration touched $key",before.get(key).toString(),after.get(key).toString())
        }
        legacy?.let {assertTrue(it.id in progress.completed)}
        content.skillsFor(question).forEach {id->
            val review=progress.skillReviews.getValue(id)
            assertEquals(0,review.repetitions);assertEquals(0,review.attempts);assertFalse(review.mastered)
        }
        val once=progress.export()
        assertFalse(progress.prepareLearning(content))
        assertEquals(once,progress.export())
        assertEquals(progress.skillReviews,Progress(context,sideEffects=false).skillReviews)
    }

    @Test fun skillAttemptsMergeDeduplicateAndLeaveUuidReviewsAndXpUntouched() = isolated {context->
        val progress=Progress(context,sideEffects=false)
        val original=JSONObject(progress.export())
        fun backup(question:String,correct:Boolean,at:Long,attempt:String)=JSONObject(original.toString()).apply {
            getJSONObject("progress").put("skillReviewsVersion",SkillLearning.FORMAT_VERSION).put("skillReviews",JSONObject().put("ohm-law",
                SkillLearning.write(SkillLearning.record(null,question,correct,if(correct)4 else 1,at,attempt))))
        }
        val a=backup("q-a",true,1_800_000_000_000,"session:a")
        val b=backup("q-b",false,1_800_000_001_000,"session:b")
        val merged=CloudProgress.merge(a.toString(),b.toString())
        assertEquals(merged,CloudProgress.merge(b.toString(),a.toString()))
        assertEquals(merged,CloudProgress.merge(merged,a.toString()))
        val state=JSONObject(merged).getJSONObject("progress")
        val skill=SkillLearning.read(state.getJSONObject("skillReviews")).getValue("ohm-law")
        assertEquals(1,skill.attempts);assertEquals(0,skill.repetitions);assertFalse(skill.mastered)
        for(key in listOf("xp","answers","correct"))assertEquals(original.getJSONObject("progress").optInt(key),state.optInt(key))
        for(key in listOf("reviews","syncEvents","awarded"))assertEquals(
            original.getJSONObject("progress").optJSONObject(key)?.toString() ?: "{}",state.optJSONObject(key)?.toString() ?: "{}")
        progress.import(merged)
        assertEquals(skill,Progress(context,sideEffects=false).skillReviews.getValue("ohm-law"))
        val invalid=JSONObject(merged).apply {getJSONObject("progress").put("skillReviews",JSONArray())}
        val before=progress.export()
        assertThrows(Exception::class.java) {progress.import(invalid.toString())}
        assertEquals(before,progress.export())
        val future=JSONObject(merged).apply {getJSONObject("progress").put("skillReviewsVersion",99)}
        assertThrows(Exception::class.java) {CloudProgress.validate(future.toString())}
    }

    @Test fun archivedCourseQuestionsStillResolveButNeverReturnAsNewRevision() = isolated {context->
        val content=Content(context,examSnapshot=null)
        val archived=content.archivedQuestions
        assertTrue("Retired published UUIDs must remain resolvable",archived.isNotEmpty())
        val progress=Progress(context,sideEffects=false,clock=Clock.fixed(Instant.ofEpochMilli(1_800_000_000_000),ZoneOffset.UTC))
        archived.forEach {question->
            assertEquals(question.id,content.allQuestions.getValue(question.id).id)
            assertFalse(content.isReviewEligible(question))
            progress.answer(question.id,false)
        }
        val backup=JSONObject(progress.export()).apply {
            val reviews=getJSONObject("progress").getJSONObject("reviews")
            archived.forEach {reviews.getJSONObject(it.id).put("due",1)}
        }
        progress.import(backup.toString())
        assertTrue(progress.due(content).none {q->archived.any {it.id==q.id}})
    }

    @Test fun hamigoToolsStaysOutOfSrsAfterLegacyImportSkillMigrationAndReload() = isolated {context->
        val content=Content(context,examSnapshot=null)
        val welcome=content.lessons.first {it.id=="l-hamigo-bienvenue"}
        val clock=Clock.fixed(Instant.ofEpochMilli(1_800_000_000_000),ZoneOffset.UTC)
        val progress=Progress(context,sideEffects=false,clock=clock)
        welcome.questions.forEach {question->
            assertTrue("The onboarding questions belong to hamigo-tools", "hamigo-tools" in content.skillsFor(question))
            assertFalse(content.isReviewEligible(question))
            assertFalse(content.isReviewEligible(question.copy(skillIds=emptyList())))
            progress.answer(question.id,false)
        }
        progress.complete(welcome.id)
        val legacy=JSONObject(progress.export()).apply {
            val state=getJSONObject("progress")
            state.remove("skillReviews");state.remove("skillReviewsVersion");state.remove(LessonCompletionMigration55.FIELD)
            val reviews=state.getJSONObject("reviews")
            welcome.questions.forEach {reviews.getJSONObject(it.id).put("due",1)}
        }
        progress.import(legacy.toString())
        assertTrue(progress.due(content).isEmpty())
        progress.prepareLearning(content)
        assertTrue(progress.due(content).isEmpty())
        assertTrue(CourseRevisionBuilder.eligible(content,progress.completed,progress.reviews,progress.skillReviews).isEmpty())
        val reloaded=Progress(context,sideEffects=false,clock=clock)
        reloaded.prepareLearning(content)
        assertTrue(reloaded.due(content).isEmpty())
        assertEquals(welcome.questions.size,reloaded.totalAnswers)
        assertTrue(welcome.id in reloaded.completed)
        assertEquals(progress.xp,reloaded.xp)
        val radio=content.lessons.flatMap {it.questions}.first {content.isReviewEligible(it)&&content.skillsFor(it).isNotEmpty()}
        reloaded.answer(radio.id,false)
        val withRadio=JSONObject(reloaded.export()).apply {getJSONObject("progress").getJSONObject("reviews").getJSONObject(radio.id).put("due",1)}
        reloaded.import(withRadio.toString());reloaded.prepareLearning(content)
        assertTrue("Radio recalls remain available",reloaded.due(content).isNotEmpty())
        assertTrue(reloaded.due(content).all {"hamigo-tools" !in content.skillsFor(it)})
    }

    @Test fun newParentCompletionDoesNotValidateSplitPartsButOldLedgerImportsDo() = isolated {context->
        val content=Content(context,examSnapshot=null)
        val child=content.lessons.first {it.id=="l-morse-rappel-etim"}
        val parent=child.legacyLessonIds.single()
        val progress=Progress(context,sideEffects=false)
        assertTrue(LessonCompletionMigration55.read(JSONObject(progress.export()).getJSONObject("progress")).isEmpty())
        assertEquals(6,progress.complete(parent))
        progress.prepareLearning(content)
        assertTrue(parent in progress.completed)
        assertFalse("A new first-part pass is not evidence for its productive recall",child.id in progress.completed)
        assertFalse(child.id in Progress(context,sideEffects=false).completed)
        val recent=progress.export()
        val oldLedger=JSONObject(recent).apply {
            val state=getJSONObject("progress")
            state.remove(LessonCompletionMigration55.FIELD)
            state.put("completed",JSONArray())
            // The historical completion remains in its immutable sync event.
        }.toString()
        val a=CloudProgress.merge(recent,oldLedger)
        assertEquals(a,CloudProgress.merge(oldLedger,recent))
        assertEquals(a,CloudProgress.merge(a,recent))
        val before=JSONObject(recent).getJSONObject("progress")
        progress.import(a);progress.prepareLearning(content)
        val after=JSONObject(progress.export()).getJSONObject("progress")
        assertTrue(child.id in progress.completed)
        for(key in listOf("xp","answers","correct"))assertEquals("Split migration changed $key",before.optInt(key),after.optInt(key))
        for(key in listOf("reviews","awarded","syncBase","syncEvents")) {
            assertEquals("Split migration changed $key",
                CloudProgress.canonical(before.optJSONObject(key) ?: JSONObject()),
                CloudProgress.canonical(after.optJSONObject(key) ?: JSONObject()))
        }
        assertEquals(0,progress.complete(child.id))
        val stable=progress.export()
        val invalid=JSONObject(stable).apply {getJSONObject("progress").put(LessonCompletionMigration55.FIELD,"invalid")}
        assertThrows(Exception::class.java) {progress.import(invalid.toString())}
        assertEquals(stable,progress.export())
    }

    private fun isolated(block:(Context)->Unit) {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val namespace="skill-learning-${UUID.randomUUID()}"
        val context=object:ContextWrapper(base) {
            override fun getApplicationContext():Context=this
            override fun getSharedPreferences(name:String,mode:Int):SharedPreferences=super.getSharedPreferences("$namespace-$name",mode)
        }
        try {block(context)} finally {listOf("hamigo","hamigo_social","hamigo_secure").forEach {base.deleteSharedPreferences("$namespace-$it")}}
    }
}
