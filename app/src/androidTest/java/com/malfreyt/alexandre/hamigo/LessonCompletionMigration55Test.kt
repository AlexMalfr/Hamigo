package com.malfreyt.alexandre.hamigo

import org.json.JSONArray
import org.json.JSONObject
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LessonCompletionMigration55Test {
    private val mapping=mapOf("new-part" to setOf("published-parent"),"combined" to setOf("published-parent","other-parent"))

    @Test fun anOldCompleteLessonKeepsItsSplitPartsWithoutAnyRewardOrLedgerRewrite() {
        val state=JSONObject().put("completed",JSONArray(listOf("published-parent","unknown-history")))
            .put("xp",42).put("answers",12).put("reviews",JSONObject().put("old-question",JSONObject().put("due",123)))
        assertTrue(LessonCompletionMigration55.apply(state,mapping))
        assertEquals(setOf("published-parent","unknown-history"),LessonCompletionMigration55.read(state))
        assertTrue(state.getJSONArray("completed").toString().contains("new-part"))
        assertFalse(state.getJSONArray("completed").toString().contains("combined"))
        assertEquals(42,state.getInt("xp"));assertEquals(12,state.getInt("answers"))
        assertEquals(123,state.getJSONObject("reviews").getJSONObject("old-question").getInt("due"))
        val once=state.toString();assertFalse(LessonCompletionMigration55.apply(state,mapping));assertEquals(once,state.toString())
    }

    @Test fun passingTheNewFirstPartCannotAutoCompleteTheOtherPartsLater() {
        val state=JSONObject().put("completed",JSONArray())
        LessonCompletionMigration55.apply(state,mapping)
        state.put("completed",JSONArray(listOf("published-parent","other-parent")))
        assertFalse(LessonCompletionMigration55.apply(state,mapping))
        assertTrue(LessonCompletionMigration55.read(state).isEmpty())
        assertEquals(2,state.getJSONArray("completed").length())
        assertFalse(state.getJSONArray("completed").toString().contains("new-part"))
    }

    @Test fun syncKeepsHistoricalEvidenceWithoutPromotingNewCompletionsToLegacy() {
        val old=JSONObject().put("completed",JSONArray(listOf("published-parent")))
        val fresh=JSONObject().put("completed",JSONArray())
        LessonCompletionMigration55.capture(old);LessonCompletionMigration55.capture(fresh)
        fresh.put("completed",JSONArray(listOf("other-parent")))
        assertEquals(LessonCompletionMigration55.merged(old,fresh).toString(),LessonCompletionMigration55.merged(fresh,old).toString())
        val merged=JSONObject().put("completed",JSONArray(listOf("published-parent","other-parent")))
            .put(LessonCompletionMigration55.FIELD,LessonCompletionMigration55.merged(old,fresh))
        LessonCompletionMigration55.apply(merged,mapping)
        assertTrue(merged.getJSONArray("completed").toString().contains("new-part"))
        assertFalse(merged.getJSONArray("completed").toString().contains("combined"))
        assertThrows(IllegalArgumentException::class.java) {LessonCompletionMigration55.capture(JSONObject().put(LessonCompletionMigration55.FIELD,"invalid"))}
    }

    @Test fun aLegacyLedgerAlsoSuppliesCompletionsWhenItsCompletedCacheIsMissing() {
        val state=JSONObject().put("syncBase",JSONObject().put("completed",JSONArray(listOf("published-parent"))))
            .put("syncEvents",JSONObject().put("event",JSONObject().put("completed","other-parent")))
        assertTrue(LessonCompletionMigration55.apply(state,mapping))
        assertEquals(setOf("published-parent","other-parent"),LessonCompletionMigration55.read(state))
        assertTrue(state.getJSONArray("completed").toString().contains("combined"))
        assertFalse(LessonCompletionMigration55.apply(state,mapping))
    }
}
