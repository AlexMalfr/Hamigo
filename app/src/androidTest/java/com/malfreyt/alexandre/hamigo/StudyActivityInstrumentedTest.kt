package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.platform.*
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.*
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StudyActivityInstrumentedTest {
    private val zone=ZoneId.of("Europe/Paris")
    private class MutableClock(var time:Instant,private val z:ZoneId):Clock() {
        override fun instant()=time
        override fun getZone()=z
        override fun withZone(zone:ZoneId):Clock=Clock.fixed(time,zone)
    }
    @Test fun xpRewardsSnapshotAndStreakShareTheSameThreeAmBoundary() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val preferences=base.getSharedPreferences("study-day-test-"+UUID.randomUUID(),Context.MODE_PRIVATE)
        val context=object:ContextWrapper(base) {override fun getSharedPreferences(name:String,mode:Int)=preferences}
        val clock=MutableClock(LocalDateTime.of(2026,10,18,23,50).atZone(zone).toInstant(),zone)
        val previousLocale=java.util.Locale.getDefault();java.util.Locale.setDefault(java.util.Locale.FRANCE)
        try {
            val p=Progress(context,sideEffects=false,clock=clock)
            assertEquals(3,p.answer("stable-one",true))
            clock.time=LocalDateTime.of(2026,10,19,0,10).atZone(zone).toInstant()
            assertEquals(3,p.todayXp);assertEquals(0,p.answer("stable-one",true))
            assertEquals(3,p.answer("stable-two",true));assertEquals(6,p.todayXp)
            assertEquals(6,p.weeklyXp);assertEquals(1,p.streak)
            val snapshot=p.snapshot()
            assertEquals("2026-10-18",snapshot.dailyXp.last().day)
            assertEquals("2026-10-18",snapshot.hourlyDay);assertEquals(3,snapshot.dayStartHour)
            assertEquals(6,snapshot.hourlyXp.sumOf {it.xp});assertTrue(snapshot.hourlyComplete)
            assertEquals(LocalDate.of(2026,10,18),snapshot.activityHistory!!.end)
            assertEquals(6,ReminderContent.build(p,LocalDate.of(2026,10,18)).todayXp)
            clock.time=LocalDateTime.of(2026,10,19,2,59,59).atZone(zone).toInstant()
            assertEquals(6,p.todayXp)
            clock.time=LocalDateTime.of(2026,10,19,3,0).atZone(zone).toInstant()
            assertEquals(0,p.todayXp);assertEquals(0,p.weeklyXp);assertEquals(1,p.streak)
            assertEquals(3,p.answer("stable-one",true));assertEquals(9,p.xp);assertEquals(2,p.streak)
            assertEquals(6,p.dayXp(LocalDate.of(2026,10,18)))
            assertEquals("2026-10-19",p.snapshot().hourlyDay)
        } finally {java.util.Locale.setDefault(previousLocale);preferences.edit().clear().commit()}
    }
    private fun legacy():JSONObject {
        val events=JSONObject()
        listOf(23 to "2026-10-17",1 to "2026-10-18").forEachIndexed {i,(hour,day)->
            events.put(UUID.nameUUIDFromBytes("event-$i".toByteArray()).toString(),JSONObject()
                .put("day",day).put("xp",3).put("answers",1).put("correct",1)
                .put("at",LocalDate.parse(day).atTime(hour,10).atZone(zone).toInstant().toEpochMilli())
                .put("streakDay","2026-10-17").put("awardKey","answer:q$i:$day"))
        }
        val base=JSONObject().put("xp",10).put("answers",0).put("correct",0).put("completed",JSONArray())
            .put("dailyXp",JSONObject().put("2026-10-10",10)).put("awarded",JSONObject())
        return JSONObject().put("schema",2).put("xp",16).put("answers",2).put("correct",2)
            .put("dailyXp",JSONObject().put("2026-10-10",10).put("2026-10-17",3).put("2026-10-18",3))
            .put("completed",JSONArray()).put("reviews",JSONObject()).put("awarded",JSONObject().put("q0","2026-10-17").put("q1","2026-10-18"))
            .put("syncBase",base).put("syncEvents",events)
    }
    private fun wrapper(state:JSONObject)=JSONObject().put("app","hamigo").put("schema",2).put("name","Démo").put("progress",state)
    @Test fun midnightHistoryMovesWithoutChangingTotalsOrEventIdentitiesAndMergesIdempotently() {
        val old=legacy();val events=CloudProgress.canonical(old.getJSONObject("syncEvents"))
        old.put("streakHistory",JSONObject().put("version",1)
            .put("seedDays",JSONArray(listOf("2026-10-10","2026-10-17")))
            .put("bridges",JSONObject().put("2026-10-17",2))
            .put("cutoverAt",LocalDate.of(2026,10,17).atTime(3,0).atZone(zone).toInstant().toEpochMilli()))
        val updated=JSONObject(old.toString())
        assertTrue(StudyDayMigration52.apply(updated));assertFalse(StudyDayMigration52.apply(updated))
        assertEquals(16,updated.getInt("xp"));assertEquals(2,updated.getInt("answers"))
        assertEquals(6,updated.getJSONObject("dailyXp").getInt("2026-10-17"))
        assertEquals(0,updated.getJSONObject("dailyXp").optInt("2026-10-18"))
        assertEquals(10,updated.getJSONObject("dailyXp").getInt("2026-10-10"))
        assertEquals("2026-10-17",updated.getJSONObject("awarded").getString("q1"))
        assertEquals(events,CloudProgress.canonical(updated.getJSONObject("syncEvents")))
        val left=wrapper(old).toString();val right=wrapper(updated).toString()
        val merged=CloudProgress.merge(left,right)
        assertEquals(merged,CloudProgress.merge(right,left))
        assertEquals(merged,CloudProgress.merge(merged,left))
        val result=JSONObject(merged).getJSONObject("progress")
        assertEquals(16,result.getInt("xp"));assertEquals(6,result.getJSONObject("dailyXp").getInt("2026-10-17"))
        val hours=SocialActivity.hours(result,LocalDate.of(2026,10,17),zone)
        assertEquals(listOf(HourlyPoint(1,3),HourlyPoint(23,3)),hours.points);assertTrue(hours.complete)
        // The old calendar key for q1 already ends in 18; a genuine new study-day award
        // after 03:00 must not collide with that immutable historical identity.
        CloudProgress.recordEvent(result,"2026-10-18",3,1,1,awardKey="answer3:q1:2026-10-18",
            at=LocalDate.of(2026,10,18).atTime(3,5).atZone(zone).toInstant().toEpochMilli())
        val next=CloudProgress.merge(wrapper(result).toString(),merged)
        val nextState=JSONObject(next).getJSONObject("progress")
        assertEquals(19,nextState.getInt("xp"))
        assertEquals(6,nextState.getJSONObject("dailyXp").getInt("2026-10-17"))
        assertEquals(3,nextState.getJSONObject("dailyXp").getInt("2026-10-18"))
        assertEquals(next,CloudProgress.merge(next,merged))
    }
    @Test fun publicDatesUseThePublishersDayBoundaryAndRetainLegacyCompatibility() {
        val profile=ShareProgress("Démo",6,1,0,zoneId=zone.id,dayStartHour=3)
        val decoded=ShareProgress.fromJson(profile.toJson());assertEquals(profile,decoded)
        val midnight=LocalDateTime.of(2026,11,1,0,30).atZone(zone).toInstant()
        assertEquals(LocalDate.of(2026,10,31),SocialActivity.date(decoded,midnight))
        assertEquals(LocalDate.of(2026,11,1),SocialActivity.date(decoded.copy(dayStartHour=0),midnight))
        assertEquals((3..23).toList()+(0..2).toList(),SocialActivity.hourOrder(decoded))
        assertThrows(Exception::class.java) {ShareProgress.fromJson(JSONObject(profile.toJson()).put("dayStartHour",2).toString())}
    }
}
