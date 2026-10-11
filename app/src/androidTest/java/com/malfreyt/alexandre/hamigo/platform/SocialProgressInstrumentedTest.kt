package com.malfreyt.alexandre.hamigo.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.ZoneId
import java.time.YearMonth

@RunWith(AndroidJUnit4::class)
class SocialProgressInstrumentedTest {
    private val day=LocalDate.of(2026,10,12)
    private val zone=ZoneId.of("Europe/Paris")
    private fun event(xp:Int,hour:Int,key:String="answer:stable:${day}",lesson:String?=null)=JSONObject()
        .put("xp",xp).put("day",day.toString()).put("at",day.atTime(hour,15).atZone(zone).toInstant().toEpochMilli())
        .put("awardKey",key).also {lesson?.let {id->it.put("completed",id)}}
    private fun state(xp:Int,events:JSONObject,base:JSONObject=JSONObject())=JSONObject().put("dailyXp",JSONObject().put(day.toString(),xp)).put("syncEvents",events).put("syncBase",base)
    @Test fun hourlySummaryDeduplicatesAwardsFromSeveralDevicesWithoutMutatingTheLedger() {
        val events=JSONObject().put("a",event(3,9)).put("b",event(3,10)).put("c",event(6,12,lesson="stable-lesson"))
        val raw=state(9,events);val before=raw.toString()
        val summary=SocialActivity.hours(raw,day,zone)
        assertEquals(listOf(HourlyPoint(9,3),HourlyPoint(12,6)),summary.points);assertTrue(summary.complete)
        assertEquals(before,raw.toString())
        val json=ShareProgress("Alice",109,2,1,dailyXp=listOf(DailyPoint(day.toString(),9)),dailyGoal=60,hourlyDay=day.toString(),hourlyXp=summary.points,hourlyComplete=summary.complete,zoneId=zone.id).toJson()
        assertFalse(json.contains("stable"));assertFalse(json.contains("at\""))
        val decoded=ShareProgress.fromJson(json);assertEquals(60,decoded.dailyGoal);assertEquals(summary.points,decoded.hourlyXp);assertTrue(decoded.hourlyComplete)
    }
    @Test fun historicalBaselineAndPreviouslyPaidAwardsNeverBecomeInventedHours() {
        val base=JSONObject().put("completed",JSONArray(listOf("old-lesson"))).put("awarded",JSONObject().put("stable",day.toString()))
        val raw=state(17,JSONObject().put("a",event(3,9)).put("b",event(6,12,lesson="old-lesson")).put("c",event(3,16,"answer:new-stable:$day")),base)
        val summary=SocialActivity.hours(raw,day,zone)
        assertEquals(listOf(HourlyPoint(16,3)),summary.points);assertFalse(summary.complete)
    }
    @Test fun timestampsThatCannotAccountForTheReportedTotalAreNotPublishedAsComplete() {
        val summary=SocialActivity.hours(state(2,JSONObject().put("a",event(3,9))),day,zone)
        assertTrue(summary.points.isEmpty());assertFalse(summary.complete)
        assertTrue(SocialActivity.hours(state(0,JSONObject()),day,zone).complete)
    }
    @Test fun olderGistsRemainReadableAndUnknownDaysAreNotZeros() {
        val old=ShareProgress.fromJson("{\"schema\":2,\"name\":\"Camille\",\"xp\":99,\"streak\":2,\"lessons\":1,\"weeklyXp\":20,\"updatedAt\":\"2026-10-12T08:00:00Z\"}")
        assertNull(old.dailyGoal);assertNull(SocialActivity.todayXp(old,day));assertFalse(old.hourlyComplete)
        assertTrue(SocialActivity.week(old,day).all {it.second==null});assertEquals(20,SocialActivity.weeklyXp(old,day))
        assertEquals(old,ShareProgress.fromJson(old.toJson()))
    }
    @Test fun publishingTheAppBuildRemainsOptionalAndSeparateFromLearningData() {
        val versioned=ShareProgress("Camille",99,2,1,appVersion="0.49+12345678",appVersionCode=49)
        assertEquals(versioned,ShareProgress.fromJson(versioned.toJson()))
        val old=versioned.copy(appVersion=null,appVersionCode=null)
        assertNull(ShareProgress.fromJson(old.toJson()).appVersion)
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val p=com.malfreyt.alexandre.hamigo.Progress(context,sideEffects=false);val before=p.export()
        val actual=p.snapshot();assertEquals(com.malfreyt.alexandre.hamigo.BuildConfig.VERSION_NAME,actual.appVersion)
        assertEquals(com.malfreyt.alexandre.hamigo.BuildConfig.VERSION_CODE,actual.appVersionCode)
        assertEquals(before,p.export())
        listOf(JSONObject(versioned.toJson()).put("appVersion",42),JSONObject(versioned.toJson()).put("appVersion","<script>"),
            JSONObject(versioned.toJson()).put("appVersion","0.49\nforged"),JSONObject(versioned.toJson()).put("appVersionCode",0),
            JSONObject(versioned.toJson()).put("appVersionCode",49.5)).forEach {bad->assertThrows(Exception::class.java){ShareProgress.fromJson(bad.toString())}}
    }
    @Test fun compactHistoryPreservesMonthsZerosAndCoverageWithoutExposingPrivateEvents() {
        val end=LocalDate.of(2024,4,15)
        val history=PublicActivityHistory.fromDailyXp(JSONObject().put("2024-02-01",10).put("2024-02-29",22).put("2024-04-01",30),end)
        val summary=ShareProgress("Camille",100,3,2,activityHistory=history)
        val parsed=ShareProgress.fromJson(summary.toJson()).activityHistory!!
        assertEquals(history,parsed);assertEquals(22,parsed.xp(LocalDate.of(2024,2,29)))
        assertEquals(0,parsed.xp(LocalDate.of(2024,3,12)));assertNull(parsed.xp(LocalDate.of(2024,1,31)));assertNull(parsed.xp(end.plusDays(1)))
        assertEquals(history.start,SocialActivity.earliest(summary));assertEquals(0,SocialActivity.weeklyXp(summary,end))
    }
    @Test fun historyRejectsWrongCoverageMalformedNumbersAndConflictingRecentPoints() {
        val valid=PublicActivityHistory.fromDailyXp(JSONObject().put(day.toString(),24),day).toJson()
        listOf(JSONObject(valid.toString()).put("start",day.plusDays(1).toString()),
            JSONObject(valid.toString()).put("months",JSONObject().put("2026-10","-1")),
            JSONObject(valid.toString()).put("months",JSONObject().put("2026-10",List(31){"2147483648"}.joinToString(","))),
            JSONObject(valid.toString()).put("months",JSONObject().put("2026-11",List(30){"0"}.joinToString(",")))
        ).forEach {assertThrows(Exception::class.java){PublicActivityHistory.read(it)}}
        val inconsistent=JSONObject(ShareProgress("Camille",100,1,1,dailyXp=listOf(DailyPoint(day.toString(),30))).toJson()).put("activityHistory",valid)
        assertThrows(Exception::class.java){ShareProgress.fromJson(inconsistent.toString())}
    }
    @Test fun longHistoryFitsTheExistingPayloadLimitAndKeepsNewestTotals() {
        val start=LocalDate.of(1930,1,1);val end=LocalDate.of(2026,10,12)
        val months=generateSequence(YearMonth.from(start)){it.plusMonths(1)}.takeWhile {it<=YearMonth.from(end)}.associateWith {m->(1..m.lengthOfMonth()).map {if(m.atDay(it)<=end)Int.MAX_VALUE else 0}}
        val history=PublicActivityHistory(start,end,months)
        val json=ShareProgress("Camille",Int.MAX_VALUE,1,1,activityHistory=history).toJson()
        assertTrue(json.length<=32_768);val parsed=ShareProgress.fromJson(json).activityHistory!!
        assertTrue(parsed.start>start);assertEquals(Int.MAX_VALUE,parsed.xp(end));assertNull(parsed.xp(start))
    }
    @Test fun rollingWeekExpiresOldDaysInsteadOfRepeatingTheLastPublishedScore() {
        val profile=ShareProgress("Camille",999,3,4,weeklyXp=999,dailyXp=listOf(DailyPoint(day.minusDays(7).toString(),100),DailyPoint(day.minusDays(6).toString(),15),DailyPoint(day.toString(),30)))
        assertEquals(45,SocialActivity.weeklyXp(profile,day));assertEquals(30,SocialActivity.todayXp(profile,day))
        assertEquals(0,SocialActivity.weeklyXp(profile,day.plusDays(7)))
    }
    @Test fun malformedOrInconsistentPublicHourlyDataIsRejected() {
        val valid=ShareProgress("Camille",6,1,1,dailyXp=listOf(DailyPoint(day.toString(),6)),dailyGoal=60,hourlyDay=day.toString(),hourlyXp=listOf(HourlyPoint(9,6)),hourlyComplete=true,zoneId=zone.id).toJson()
        listOf(
            JSONObject(valid).put("dailyGoal",0),
            JSONObject(valid).put("zoneId","not-a-zone"),
            JSONObject(valid).put("hourlyXp",JSONArray().put(JSONObject().put("hour",24).put("xp",6))),
            JSONObject(valid).put("hourlyXp",JSONArray().put(JSONObject().put("hour",9).put("xp",3))),
            JSONObject(valid).put("hourlyXp",JSONArray().put(JSONObject().put("hour",9).put("xp",3)).put(JSONObject().put("hour",9).put("xp",3)))
        ).forEach {bad->assertThrows(Exception::class.java){ShareProgress.fromJson(bad.toString())}}
    }
}
