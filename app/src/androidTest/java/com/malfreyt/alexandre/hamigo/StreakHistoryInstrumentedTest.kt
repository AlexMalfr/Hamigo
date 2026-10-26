package com.malfreyt.alexandre.hamigo

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class StreakHistoryInstrumentedTest {
    private val day=LocalDate.of(2026,10,25)
    private fun history()=JSONObject("""{"version":1,"seedDays":["2026-10-23","2026-10-24"],"bridges":{"2026-10-23":8},"cutoverAt":1}""")

    @Test fun savedHistoryKeepsItsContinuityWithoutBeingRewritten() {
        val saved=history();val before=saved.toString()
        val state=JSONObject().put("streakHistory",saved).put("syncEvents",JSONObject())
        assertFalse(StreakHistory.initialize(state))
        assertEquals(before,state.getJSONObject("streakHistory").toString())
        assertEquals(9,StreakHistory.count(state,day))
        assertEquals(0,StreakHistory.count(state,day.plusDays(1)))
    }

    @Test fun missingHistoryStartsEmptyWithoutInferringMasteryFromPastXp() {
        val state=JSONObject().put("dailyXp",JSONObject().put(day.toString(),100))
            .put("completed",JSONArray(listOf("stable-lesson"))).put("syncEvents",JSONObject())
        assertTrue(StreakHistory.initialize(state))
        assertFalse(StreakHistory.initialize(state))
        assertEquals(0,StreakHistory.count(state,day))
        assertEquals(100,state.getJSONObject("dailyXp").getInt(day.toString()))
        assertEquals("stable-lesson",state.getJSONArray("completed").getString(0))
    }

    @Test fun mergingSavedHistoryWithFreshProgressKeepsTheExistingBridge() {
        val existing=JSONObject().put("streakHistory",history())
        val fresh=JSONObject();StreakHistory.initialize(fresh)
        val merged=StreakHistory.merge(existing,fresh)
        val combined=JSONObject().put("streakHistory",merged).put("syncEvents",JSONObject())
        assertEquals(9,StreakHistory.count(combined,day))
        assertEquals(history().getJSONObject("bridges").toString(),merged.getJSONObject("bridges").toString())
        assertEquals(1L,merged.getLong("cutoverAt"))
    }
}
