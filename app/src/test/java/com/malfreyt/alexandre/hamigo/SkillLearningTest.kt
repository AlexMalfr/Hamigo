package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test

class SkillLearningTest {
    private val now=1_800_000_000_000L

    @Test fun immediateCorrectionsAndSameSessionVariantsNeverBecomeSpacedMastery() {
        val failed=SkillLearning.record(null,"q-a",false,1,now,"session:a")
        val corrected=SkillLearning.record(failed,"q-a",true,4,now+1_000,"session:a")
        assertEquals(failed,corrected)
        val another=SkillLearning.record(corrected,"q-b",true,5,now+5_000,"session:b")
        assertEquals(0,another.repetitions)
        assertEquals(failed.due,another.due)
        assertEquals(2,another.attempts)
        assertFalse(another.mastered)
        val recalled=SkillLearning.record(another,"q-c",true,4,another.due,"next:c")
        assertEquals(1,recalled.repetitions)
        val repeatedEarly=SkillLearning.record(recalled,"q-d",true,5,recalled.due-1,"early:d")
        assertEquals(recalled.due,repeatedEarly.due)
        assertEquals(1,repeatedEarly.repetitions)
        val spaced=SkillLearning.record(repeatedEarly,"q-e",true,4,recalled.due,"later:e")
        assertEquals(2,spaced.repetitions)
        assertTrue(spaced.mastered)
    }

    @Test fun importedQuestionReviewSeedsUrgencyAndLapsesWithoutInventingSuccesses() {
        val seeded=SkillLearning.seed(now+60_000,3)
        assertTrue(seeded.seeded)
        assertEquals(0,seeded.attempts)
        assertEquals(0,seeded.repetitions)
        assertEquals(3,seeded.lapses)
        val early=SkillLearning.record(seeded,"q-new-variant",true,5,now,"early")
        assertEquals(seeded.due,early.due)
        assertEquals(0,early.repetitions)
        assertFalse(early.mastered)
    }

    @Test fun concurrentAttemptsMergeCommutativelyWithConservativeCountersAndFailureEvidence() {
        val left=SkillLearning.record(null,"q-a",true,4,now,"a")
        val right=SkillLearning.record(null,"q-b",false,1,now+1_000,"b")
        val merged=SkillLearning.merge(left,right)
        assertEquals(merged,SkillLearning.merge(right,left))
        assertEquals(merged,SkillLearning.merge(merged,left))
        assertEquals(merged,SkillLearning.merge(merged,merged))
        assertEquals(1,merged.attempts)
        assertEquals(1,merged.successes)
        assertEquals(0,merged.repetitions)
        assertEquals(now+601_000,merged.due)
        // A corrected copy of the same first attempt cannot hide its failure.
        val correction=SkillLearning.record(null,"q-b",true,5,now+2_000,"b")
        val correctedCopy=SkillLearning.merge(merged,correction)
        assertEquals(merged.review,correctedCopy.review)
        assertEquals(merged.failure,correctedCopy.failure)
        assertEquals(merged.attempts,correctedCopy.attempts)
        assertEquals(correctedCopy,SkillLearning.merge(correctedCopy,correction))
    }

    @Test fun splitLessonsAndCombinedLessonsPreserveLegacyCompletionWithoutRenumbering() {
        val mapping=mapOf("split-a" to setOf("legacy"),"split-b" to setOf("legacy"),
            "combined" to setOf("split-a","other"),"cycle-a" to setOf("cycle-b"),"cycle-b" to setOf("cycle-a"))
        val migrated=SkillLearning.migratedCompleted(setOf("legacy","unknown-id"),mapping)
        assertEquals(setOf("legacy","unknown-id","split-a","split-b"),migrated)
        assertEquals(migrated,SkillLearning.migratedCompleted(migrated,mapping))
        assertTrue("combined" in SkillLearning.migratedCompleted(migrated+"other",mapping))
    }

    @Test fun certificateAndExplorationProgressUseIndependentWeightsAndOnlyActualCompletions() {
        fun lesson(id:String,c:Int,e:Int)=Lesson(id,id,"",emptyList(),"","",emptyList(),certificateWeight=c,explorationWeight=e)
        val lessons=listOf(lesson("intro",0,0),lesson("math",3,1),lesson("radio",1,3))
        assertEquals(PathCompletion(.75f,.25f),SkillLearning.pathCompletion(lessons,setOf("intro","math","old")))
        assertEquals(PathCompletion(0f,0f),SkillLearning.pathCompletion(emptyList(),setOf("old")))
        assertEquals(PathCompletion(1f,1f),SkillLearning.pathCompletion(lessons,lessons.map {it.id}.toSet()))
    }
    @Test fun extremeImportedCountersStayNonNegativeAfterAFurtherFailure() {
        val seed=SkillLearning.seed(Long.MAX_VALUE,Int.MAX_VALUE)
        val failed=SkillLearning.record(seed,"q-edge",false,1,now,"failure")
        assertEquals(Int.MAX_VALUE,failed.lapses)
        assertEquals(now+600_000,failed.due)
        assertEquals(0,failed.repetitions)
    }
}
