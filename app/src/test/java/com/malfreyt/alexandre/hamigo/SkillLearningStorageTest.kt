package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SkillLearningStorageTest {
    private val now=1_800_000_000_000L

    @Test fun aHundredThousandFurtherAttemptsKeepConstantEvidenceAndSequentialCounters() {
        var state:SkillReview?=null
        for(n in 0..100_000)state=SkillLearning.record(state,"question-${n%100}",n%5!=0,if(n%5!=0)4 else 1,now+n,"attempt:$n")
        val result=checkNotNull(state)
        assertEquals(100_001,result.attempts)
        assertEquals(80_000,result.successes)
        assertEquals(20_001,result.lapses)
        assertEquals(SkillLearning.MAX_RECENT,result.history.size)
        assertNotNull(result.last)
        assertNotNull(result.failure)
        assertFalse(result.mastered)
        assertEquals(result,SkillLearning.merge(result,result))
    }

    @Test fun twoLargeOfflineBranchesMergeWithoutSummingSharedCountersOrGrowingTheSample() {
        var left:SkillReview?=null
        var right:SkillReview?=null
        for(n in 0..50_000) {
            left=SkillLearning.record(left,"left-${n%100}",true,4,now+n,"left:$n")
            right=SkillLearning.record(right,"right-${n%100}",n%17!=0,if(n%17!=0)4 else 1,now+1_000+n,"right:$n")
        }
        val a=checkNotNull(left);val b=checkNotNull(right)
        val merged=SkillLearning.merge(a,b)
        assertEquals(50_001,merged.attempts)
        assertEquals(SkillLearning.MAX_RECENT,merged.history.size)
        assertEquals(merged,SkillLearning.merge(b,a))
        assertEquals(merged,SkillLearning.merge(merged,a))
        assertEquals(merged,SkillLearning.merge(merged,b))
        assertFalse(merged.mastered)
        assertEquals(b.failure,merged.failure)
    }

    @Test fun anUnknownConcurrentFailureInvalidatesARecallEvenWhenThatRecallHappenedLater() {
        val first=SkillLearning.record(null,"first",true,4,now,"first")
        val mastered=SkillLearning.record(first,"second",true,4,first.due,"second")
        assertTrue(mastered.mastered)
        val failed=SkillLearning.record(first,"failure",false,1,now+3_600_000,"failure")
        val merged=SkillLearning.merge(mastered,failed)
        assertEquals(0,merged.repetitions)
        assertNull(merged.recall)
        assertFalse(merged.mastered)
        assertEquals(failed.due,merged.due)
        val acknowledged=SkillLearning.record(merged,"recall-after-sync",true,4,mastered.due,"acknowledged")
        assertEquals(1,acknowledged.repetitions)
        assertFalse(acknowledged.mastered)
        val spaced=SkillLearning.record(acknowledged,"another-date",true,4,acknowledged.due,"another-date")
        assertTrue(spaced.mastered)
    }

    @Test fun twoIndependentFirstRecallsNeverBecomeTwoSpacedRecallsOnMerge() {
        val a=SkillLearning.record(null,"a",true,4,now,"a")
        val b=SkillLearning.record(null,"b",true,4,now+1_000,"b")
        val merged=SkillLearning.merge(a,b)
        assertEquals(1,merged.attempts)
        assertEquals(1,merged.repetitions)
        assertFalse(merged.mastered)
    }

    @Test fun registersConvergeAssociativelyAfterFailuresRecallsAndSampleEviction() {
        var common=SkillLearning.record(null,"common",true,4,now,"common")
        common=SkillLearning.record(common,"common-spaced",true,4,common.due,"common-spaced")
        var a=SkillLearning.record(common,"a",true,4,common.due,"a")
        val failed=SkillLearning.record(common,"b-failure",false,1,common.due-1_000,"b-failure")
        var b=failed
        var c=SkillLearning.record(failed,"c-recall",true,4,failed.due,"c-recall")
        c=SkillLearning.record(c,"c-spaced",true,4,c.due,"c-spaced")
        for(n in 0 until 200) {
            a=SkillLearning.record(a,"a-$n",true,4,common.due+n,"a:$n")
            b=SkillLearning.record(b,"b-$n",true,4,failed.updatedAt+n,"b:$n")
            c=SkillLearning.record(c,"c-$n",true,4,c.updatedAt+1,"c:$n")
        }
        val left=SkillLearning.merge(SkillLearning.merge(a,b),c)
        val right=SkillLearning.merge(a,SkillLearning.merge(b,c))
        assertEquals(left,right)
        for(order in listOf(listOf(a,b,c),listOf(c,a,b),listOf(b,c,a),listOf(c,b,a))) {
            val result=order.reduce(SkillLearning::merge)
            assertEquals(left,result)
            assertEquals(result,SkillLearning.merge(result,result))
        }
        assertTrue(left.mastered)
        assertEquals(c.recall,left.recall)
    }

    @Test fun losingAnInspectionSampleNeverLosesItsFailureRegisterOrReintroducesItsOldEvents() {
        val keys=(0..200).map {"sample:$it"}.sortedBy {UUID.nameUUIDFromBytes(it.toByteArray()).toString()}
        val failureKey=keys.last()
        var result=SkillLearning.record(null,"forgotten-sample",false,1,now,failureKey)
        val failed=result
        for((n,key) in keys.dropLast(1).withIndex())result=SkillLearning.record(result,"early-$n",true,4,now+n+1,key)
        assertFalse(result.history.containsKey(failed.lastAttemptId))
        assertEquals(failed.failure,result.failure)
        assertEquals(1,result.lapses)
        assertEquals(result,SkillLearning.merge(result,failed))
        assertEquals(201,result.attempts)
        assertFalse(result.mastered)
    }
}
