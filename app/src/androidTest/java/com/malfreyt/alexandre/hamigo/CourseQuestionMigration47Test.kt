package com.malfreyt.alexandre.hamigo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.platform.CloudProgress
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

/** Only JSON fixtures and RAM preferences; no real Gist or personal progress. Remove at 0.52. */
@RunWith(AndroidJUnit4::class)
class CourseQuestionMigration47Test {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val old="c15-l01-q01"
    private val fresh get()=CourseQuestionMigration47.id(old)
    private val day get()=LocalDate.now().toString()
    private fun review(time:Long)=JSONObject().put("due",1000).put("interval",6.0).put("ease",2.7).put("repetitions",4).put("lapses",2).put("updatedAt",time)
    private fun state():JSONObject {
        val paid=JSONObject().put(old,day)
        return JSONObject().put("schema",2).put("xp",120).put("answers",40).put("correct",30)
            .put("completed",JSONArray(listOf("c15-l01"))).put("dailyXp",JSONObject().put(day,12))
            .put("reviews",JSONObject().put(old,review(10))).put("awarded",JSONObject(paid.toString()))
            .put("syncBase",JSONObject().put("xp",120).put("answers",40).put("correct",30).put("completed",JSONArray(listOf("c15-l01")))
                .put("dailyXp",JSONObject().put(day,12)).put("awarded",paid)).put("syncEvents",JSONObject())
    }
    private fun backup(state:JSONObject)=JSONObject().put("app","hamigo").put("schema",2).put("name","Fixture")
        .put("profileUpdatedAt",1).put("preferencesUpdatedAt",1).put("preferences",JSONObject().put("dailyGoal",60))
        .put("friends",JSONObject()).put("progress",state)
    @Test fun migrationIsBijectiveKeepsLedgerAndSrsAndNeverRepaysTodaysAward() {
        assertEquals(850,CourseQuestionAliases47.ids.size);assertEquals(850,CourseQuestionAliases47.ids.values.toSet().size)
        assertTrue(CourseQuestionAliases47.ids.values.all {UUID.fromString(it.removePrefix("q-")).version()==4})
        val fake=DiagnosticContext(context);val original=state()
        fake.getSharedPreferences("hamigo",0).edit().putString("progress",original.toString()).putString("name","Fixture").putInt("dailyGoal",100).commit()
        val progress=Progress(fake,sideEffects=false)
        assertEquals(120,progress.xp);assertEquals(40,progress.totalAnswers);assertEquals(30,progress.totalCorrect)
        assertEquals(setOf("c15-l01"),progress.completed);assertEquals(12,progress.todayXp)
        assertEquals(Review(1000,6.0,2.7,4,2),progress.reviews.getValue(fresh));assertFalse(old in progress.reviews)
        assertEquals("Fixture",progress.name);assertEquals(3,progress.dailyGoalChoice)
        assertEquals(0,progress.answer(fresh,true))
        val exported=progress.export();assertFalse(exported.contains(old));assertEquals(1,JSONObject(exported).getJSONObject("progress").getInt("questionIdsVersion"))
        val before=fake.getSharedPreferences("hamigo",0).getString("progress",null)
        Progress(fake,sideEffects=false).reload();assertEquals(before,fake.getSharedPreferences("hamigo",0).getString("progress",null))
    }
    @Test fun oldAndNewDeviceFilesMergeIdempotentlyWithoutDuplicateXpOrAttempts() {
        val left=state();left.getJSONObject("syncBase").put("awarded",JSONObject());left.put("awarded",JSONObject())
        val id=UUID.randomUUID().toString()
        val event=JSONObject().put("day",day).put("xp",3).put("answers",1).put("correct",1).put("at",20).put("awardKey","answer:$old:$day")
        left.getJSONObject("syncEvents").put(id,event)
        val right=JSONObject(left.toString());CourseQuestionMigration47.apply(right)
        val a=backup(left).toString();val b=backup(right).toString()
        val merged=CloudProgress.merge(a,b);val result=JSONObject(merged).getJSONObject("progress")
        assertEquals(123,result.getInt("xp"));assertEquals(41,result.getInt("answers"));assertEquals(1,result.getJSONObject("syncEvents").length())
        assertEquals("answer:$fresh:$day",result.getJSONObject("syncEvents").getJSONObject(id).getString("awardKey"))
        assertEquals(merged,CloudProgress.merge(b,a));assertEquals(merged,CloudProgress.merge(merged,a))
    }
    @Test fun mixedAliasesChooseTheNewestReviewAndKeepUnknownIdsAndRemovedQuestionHistory() {
        val original=state();val reviews=original.getJSONObject("reviews")
        reviews.put(fresh,review(30));reviews.put("external-custom",review(50))
        val retired="c15-l02-q14";reviews.put(retired,review(60))
        val migration=CourseQuestionMigration47.apply(original)
        assertTrue(migration);assertFalse(CourseQuestionMigration47.apply(original))
        assertEquals(30L,original.getJSONObject("reviews").getJSONObject(fresh).getLong("updatedAt"))
        assertTrue(original.getJSONObject("reviews").has("external-custom"))
        assertTrue(original.getJSONObject("reviews").has(CourseQuestionMigration47.id(retired)))
        assertEquals(120,original.getInt("xp"))
    }
    @Test fun manualImportAndCloudRestoreCanonicalizeOldIdsWithoutChangingDeviceAudioChoices() {
        val fake=DiagnosticContext(context);val progress=Progress(fake,sideEffects=false)
        GameplayPreferences.save(progress.prefs,MorseInputSettings(true,450,false))
        val oldBackup=backup(state()).toString()
        progress.import(oldBackup)
        assertTrue(fresh in progress.reviews);assertEquals(120,progress.xp)
        assertFalse(GameplayPreferences.read(progress.prefs).liveSound)
        val exported=progress.export();assertFalse(exported.contains(old))
        progress.mergeCloud(oldBackup);assertEquals(120,progress.xp)
        assertTrue(fresh in progress.reviews);assertFalse(GameplayPreferences.read(progress.prefs).liveSound)
        val malformed=backup(state());malformed.getJSONObject("progress").put("questionIdsVersion",99)
        val beforeRejected=progress.export()
        assertTrue(runCatching {progress.import(malformed.toString())}.isFailure)
        assertEquals(beforeRejected,progress.export())
    }
    @Test fun packagedBankResolvesEveryMembershipAndTheMorseRepeatAndGiveawayAreGone() {
        val content=Content(context)
        assertEquals(842,content.courseQuestions.size)
        assertTrue(content.lessons.flatMap {it.questions}.all {q->q.id.startsWith("q-") && content.courseQuestions.any {it===q}})
        assertEquals(1,content.courseQuestions.count {it.prompt.startsWith("Une lettre se termine")})
        val order=content.lessons.first {it.id=="c15-l09"}.questions.first {it.kind=="order"}
        assertEquals(listOf("Point","Trait","Pause entre les mots"),order.choices)
        assertTrue(order.explanation.contains("sept"))
        assertTrue(content.activeExam.all {it.id.toIntOrNull()!=null})
        val before=content.allQuestions.keys
        val moved=content.lessons.reversed().flatMap {it.questions.reversed()}.map {it.id}.toSet()
        assertTrue(before.containsAll(moved))
    }
}
