package com.malfreyt.alexandre.hamigo

import com.malfreyt.alexandre.hamigo.platform.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class EncouragementContentTest {
    private val day=LocalDate.of(2026,10,12)
    private fun profile(today:Int=60,goal:Int?=120)=ShareProgress("Camille",1500,5,13,210,
        dailyXp=(6L downTo 0L).map {DailyPoint(day.minusDays(it).toString(),if(it==0L)today else 25)},dailyGoal=goal)
    @Test fun aPartialGoalUsesTheFriendsOwnGoalAndReportedActivity() {
        val data=EncouragementContent.from(profile(),day)
        assertTrue(data.message.contains("60 XP aujourd’hui sur ton objectif de 120 XP"))
        assertTrue(data.message.contains("Encore 60 XP"));assertTrue(data.message.contains("5 jours de série"))
        assertTrue(data.message.contains("13 leçons terminées"));assertTrue(data.message.contains("210 XP sur les 7 derniers jours"))
        assertEquals(.5f,data.ratio!!,0f);assertFalse(data.reached)
    }
    @Test fun aCompletedGoalCongratulatesRatherThanAskingForTheMissingXp() {
        val data=EncouragementContent.from(profile(130),day)
        assertTrue(data.reached);assertEquals(1f,data.ratio!!,0f)
        assertTrue(data.message.contains("130 / 120 XP"));assertTrue(data.message.contains("Bravo"))
        assertFalse(data.message.contains("Encore"));assertFalse(data.message.contains("-10"))
    }
    @Test fun anOlderOrStaleSummaryDoesNotInventTodaysActivityOrGoal() {
        val old=ShareProgress("Noa",300,1,1,90)
        val data=EncouragementContent.from(old,day)
        assertNull(data.ratio);assertFalse(data.message.contains("0 XP aujourd’hui"));assertFalse(data.message.contains("objectif"))
        assertTrue(data.message.contains("1 jour de série · 1 leçon terminée"));assertTrue(data.message.contains("90 XP dans ton dernier bilan hebdo"))
        val stale=EncouragementContent.from(profile(),day.plusDays(8))
        assertNull(stale.ratio);assertFalse(stale.message.contains("Encore"))
    }
}
