package com.malfreyt.alexandre.hamigo

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.malfreyt.alexandre.hamigo.platform.CloudProgress
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId

/** Temporary transition tests, to remove together with the migration at 0.57. */
@RunWith(AndroidJUnit4::class)
class StreakMigration52Test {
    private val zone=ZoneId.of("Europe/Paris")
    private val date=LocalDate.of(2026,10,17)
    private fun old(days:List<LocalDate>):JSONObject=JSONObject().put("xp",123).put("answers",9).put("correct",7)
        .put("completed",JSONArray(listOf("stable-lesson"))).put("reviews",JSONObject().put("q-stable",JSONObject().put("due",1234)))
        .put("dailyXp",JSONObject().also {xp->days.forEach {xp.put(it.toString(),3)}}).put("syncEvents",JSONObject())
    private fun event(state:JSONObject,day:LocalDate,hour:Int) {
        state.getJSONObject("syncEvents").put("event-"+day+"-"+hour,JSONObject().put("xp",3)
            .put("at",day.atTime(hour,0).atZone(zone).toInstant().toEpochMilli()).put("streakDay",StudyDay.date(day.atTime(hour,0).atZone(zone).toInstant(),zone).toString()))
    }
    @Test fun lateNightUpgradeKeepsAnExistingStreakAndNeverChangesXpReviewsOrLessons() {
        val days=(0L..4L).map {date.minusDays(it)}
        val state=old(days);val before=JSONObject(state.toString())
        assertTrue(StreakMigration52.apply(state,date.atTime(1,0).atZone(zone).toInstant(),zone))
        assertEquals(5,StreakHistory.count(state,date.minusDays(1)))
        before.keys().forEach {key->assertEquals(key,before.get(key).toString(),state.get(key).toString())}
        assertFalse(StreakMigration52.apply(state,date.atTime(2,0).atZone(zone).toInstant(),zone))
        // At 03:00 it is the new study day, without an automatically awarded extra day.
        assertEquals(5,StreakHistory.count(state,date))
        event(state,date,4);assertEquals(6,StreakHistory.count(state,date))
        assertEquals(6,StreakHistory.count(state,date.plusDays(1)))
        assertEquals(0,StreakHistory.count(state,date.plusDays(2)))
    }
    @Test fun upgradeDoesNotReviveAnAlreadyBrokenStreakAndNewEventsUseTheThreeAmDay() {
        val state=old(listOf(date.minusDays(4),date.minusDays(3)))
        StreakMigration52.apply(state,date.atTime(12,0).atZone(zone).toInstant(),zone)
        assertEquals(0,StreakHistory.count(state,date))
        event(state,date.plusDays(1),1)
        assertEquals(1,StreakHistory.count(state,date))
        assertEquals(1,StreakHistory.count(state,date.plusDays(1)))
        event(state,date.plusDays(1),4)
        assertEquals(2,StreakHistory.count(state,date.plusDays(1)))
    }
    @Test fun twoOfflineUpgradesMergeWithoutLosingTheBridgeOrDoublingAnything() {
        val left=old((1L..4L).map {date.minusDays(it)});val right=JSONObject(left.toString())
        StreakMigration52.apply(left,date.atTime(1,0).atZone(zone).toInstant(),zone)
        StreakMigration52.apply(right,date.atTime(12,0).atZone(zone).toInstant(),zone)
        val merged=StreakHistory.merge(left,right)
        assertEquals(CloudProgress.canonical(merged),CloudProgress.canonical(StreakHistory.merge(right,left)))
        assertEquals(CloudProgress.canonical(merged),CloudProgress.canonical(StreakHistory.merge(left,JSONObject(right.toString()).put("streakHistory",merged))))
        val combined=JSONObject(left.toString()).put("streakHistory",merged)
        assertEquals(4,StreakHistory.count(combined,date))
        event(combined,date,4)
        assertEquals(5,StreakHistory.count(combined,date))
        event(combined,date.plusDays(1),4)
        assertEquals(6,StreakHistory.count(combined,date.plusDays(1)))
    }
    @Test fun anUpgradeAfterThreeKeepsYesterdaysGraceWithoutAwardingAnExtraDay() {
        val state=old((1L..4L).map {date.minusDays(it)})
        StreakMigration52.apply(state,date.atTime(12,0).atZone(zone).toInstant(),zone)
        assertEquals(4,StreakHistory.count(state,date))
        assertEquals(0,StreakHistory.count(state,date.plusDays(1)))
        event(state,date,16);assertEquals(5,StreakHistory.count(state,date))
    }
}
