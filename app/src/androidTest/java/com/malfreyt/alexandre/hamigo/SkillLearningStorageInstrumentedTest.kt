package com.malfreyt.alexandre.hamigo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.platform.CloudProgress
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SkillLearningStorageInstrumentedTest {
    private val now=1_800_000_000_000L
    private val context get()=DiagnosticContext(InstrumentationRegistry.getInstrumentation().targetContext)
    private fun emptyBackup()=Progress(context,sideEffects=false).export()
    private fun records(skill:String,review:SkillReview)=JSONObject().put(skill,SkillLearning.write(review))
    private fun backup(base:String,records:JSONObject)=JSONObject(base).apply {
        getJSONObject("progress").put("skillReviewsVersion",SkillLearning.FORMAT_VERSION).put("skillReviews",records)
    }.toString()
    private fun large(prefix:String,count:Int,failEvery:Int=0):SkillReview {
        var result:SkillReview?=null
        for(n in 0 until count) {
            val correct=failEvery==0||n%failEvery!=0
            result=SkillLearning.record(result,"$prefix-${n%100}",correct,if(correct)4 else 1,now+n,"$prefix:$n")
        }
        return checkNotNull(result)
    }
    private fun canonical(json:String)=CloudProgress.canonical(JSONObject(json))

    @Test fun overAHundredThousandSkillAttemptsRemainSmallAndRoundTripWithoutTouchingTheXpLedger() {
        val progress=Progress(context,sideEffects=false)
        progress.answer("published-uuid",false)
        progress.complete("published-lesson")
        val original=progress.export()
        val before=JSONObject(original).getJSONObject("progress")
        val state=large("stress",100_001,5)
        val skillRecords=records("ohm-law",state)
        assertTrue(skillRecords.toString().toByteArray(Charsets.UTF_8).size<20_000)
        SkillLearning.validate(skillRecords)
        val imported=CloudProgress.validate(backup(original,skillRecords))
        progress.import(imported)
        assertEquals(100_001,progress.skillReviews.getValue("ohm-law").attempts)
        assertEquals(state,progress.skillReviews.getValue("ohm-law"))
        val after=JSONObject(progress.export()).getJSONObject("progress")
        for(key in listOf("xp","answers","correct","reviews","awarded","dailyXp","completed","syncBase","syncEvents")) {
            assertEquals("Skill storage changed $key",before.get(key).toString(),after.get(key).toString())
        }
        val roundTrip=Progress(context,sideEffects=false)
        roundTrip.import(progress.export())
        assertEquals(state,roundTrip.skillReviews.getValue("ohm-law"))
        assertEquals(7,roundTrip.xp)
        assertEquals(1,roundTrip.totalAnswers)
        CloudProgress.validate(roundTrip.export())
    }

    @Test fun twoLargeOfflineBranchesAndAnOldPublishedBackupConvergeWithoutReinjectingCounts() {
        val published=emptyBackup()
        val a=backup(published,records("ohm-law",large("a",50_001)))
        val b=backup(published,records("ohm-law",large("b",50_001,17)))
        val merged=CloudProgress.merge(a,b)
        val review=SkillLearning.read(JSONObject(merged).getJSONObject("progress").getJSONObject("skillReviews")).getValue("ohm-law")
        assertEquals(50_001,review.attempts)
        assertEquals(SkillLearning.MAX_RECENT,review.history.size)
        assertFalse(review.mastered)
        assertEquals(canonical(merged),canonical(CloudProgress.merge(b,a)))
        assertEquals(canonical(merged),canonical(CloudProgress.merge(merged,a)))
        assertEquals(canonical(CloudProgress.merge(merged,published)),canonical(CloudProgress.merge(a,CloudProgress.merge(b,published))))
        val restored=Progress(context,sideEffects=false)
        restored.import(merged)
        assertEquals(review,restored.skillReviews.getValue("ohm-law"))
        assertEquals(0,restored.xp)
    }

    @Test fun aSmallUnpublishedPreparatoryJournalConvertsOnceWithoutLosingItsEvents() {
        var initial=SkillLearning.record(null,"legacy-a",true,4,now,"legacy:a")
        initial=SkillLearning.record(initial,"legacy-b",false,1,now+1_000,"legacy:b")
        val oldRecord=SkillLearning.write(initial).apply {
            remove("format");remove("failure");remove("recall");remove("last")
        }
        val source=JSONObject(emptyBackup()).apply {
            getJSONObject("progress").put("skillReviewsVersion",1).put("skillReviews",JSONObject().put("ohm-law",oldRecord))
        }.toString()
        val normalized=CloudProgress.validate(source)
        val progressState=JSONObject(normalized).getJSONObject("progress")
        assertEquals(SkillLearning.FORMAT_VERSION,progressState.getInt("skillReviewsVersion"))
        val converted=progressState.getJSONObject("skillReviews").getJSONObject("ohm-law")
        assertEquals(SkillLearning.FORMAT_VERSION,converted.getInt("format"))
        assertEquals(CloudProgress.canonical(oldRecord.getJSONObject("history")),CloudProgress.canonical(converted.getJSONObject("history")))
        assertEquals(2,SkillLearning.readReview(converted).attempts)
        val merged=CloudProgress.merge(normalized,source)
        assertEquals(canonical(merged),canonical(CloudProgress.merge(merged,source)))
        assertEquals(2,SkillLearning.read(JSONObject(merged).getJSONObject("progress").getJSONObject("skillReviews")).getValue("ohm-law").attempts)
        val restored=Progress(context,sideEffects=false)
        restored.import(merged)
        val once=restored.export()
        assertEquals(once,restored.export())
        assertEquals(2,restored.skillReviews.getValue("ohm-law").attempts)
    }

    @Test fun longPreparatoryJournalsAndInvalidCompactProofsAreRejectedBeforeWriting() {
        val progress=Progress(context,sideEffects=false)
        val original=progress.export()
        val first=SkillLearning.record(null,"first",true,4,now,"first")
        val spaced=SkillLearning.record(first,"spaced",true,4,first.due,"spaced")
        val valid=SkillLearning.write(spaced)
        val longHistory=JSONObject()
        for(n in 0..32)longHistory.put(UUID.nameUUIDFromBytes("old:$n".toByteArray()).toString(),
            JSONObject().put("questionId","old-$n").put("at",now+n).put("correct",true).put("quality",4))
        val oldTooLong=JSONObject(valid.toString()).apply {
            remove("format");remove("failure");remove("recall");remove("last");put("history",longHistory)
        }
        val invalid=listOf(
            oldTooLong,
            JSONObject(valid.toString()).put("format",99),
            JSONObject(valid.toString()).put("history",JSONArray()),
            JSONObject(valid.toString()).put("attempts",2.5),
            JSONObject(valid.toString()).put("unexpectedPersonalData","must be refused"),
            JSONObject(valid.toString()).apply {getJSONObject("recall").remove("previous")},
            JSONObject(valid.toString()).apply {getJSONObject("recall").getJSONObject("previous").put("due",now+1)},
            JSONObject(valid.toString()).apply {
                put("failure",SkillLearning.write(SkillLearning.record(null,"failure",false,1,now,"failure")).getJSONObject("failure"))
            }
        )
        invalid.forEach {record->
            val input=backup(original,JSONObject().put("ohm-law",record))
            assertThrows(Exception::class.java) {progress.import(input)}
            assertEquals(original,progress.export())
        }
    }

    @Test fun theGlobalInspectionBudgetAlsoConvergesWhenSeveralValidFullSamplesMeet() {
        fun bank(offset:Int):JSONObject=JSONObject().also {json->
            for(n in offset until offset+64)json.put("skill-$n",SkillLearning.write(large("skill-$n",40,7)))
            SkillLearning.trimHistory(json)
            SkillLearning.validate(json)
        }
        val a=bank(0);val b=bank(64);val c=bank(128)
        fun merge(x:JSONObject,y:JSONObject)=SkillLearning.mergeJson(x,y).also(SkillLearning::validate)
        val left=merge(merge(a,b),c)
        val right=merge(a,merge(b,c))
        assertEquals(CloudProgress.canonical(left),CloudProgress.canonical(right))
        assertEquals(192,left.length())
        assertEquals(SkillLearning.MAX_RECENT_TOTAL,left.keys().asSequence().sumOf {left.getJSONObject(it).getJSONObject("history").length()})
        assertEquals(CloudProgress.canonical(left),CloudProgress.canonical(merge(left,a)))
        val restored=Progress(context,sideEffects=false)
        restored.import(backup(emptyBackup(),left))
        assertEquals(192,restored.skillReviews.size)
        assertTrue(restored.skillReviews.values.all {it.attempts==40})
    }
}
