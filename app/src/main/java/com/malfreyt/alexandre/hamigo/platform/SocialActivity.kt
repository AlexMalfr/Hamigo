package com.malfreyt.alexandre.hamigo.platform

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Only public aggregates leave the app: no question IDs, exact event times or answers. */
internal object SocialActivity {
    data class Hours(val points:List<HourlyPoint>,val complete:Boolean)
    fun hours(state:JSONObject,date:LocalDate,zone:ZoneId=ZoneId.systemDefault()):Hours {
        val awards=com.malfreyt.alexandre.hamigo.StudyActivity.earnedEvents(state)
        val hourly=LongArray(24)
        awards.filter {com.malfreyt.alexandre.hamigo.StudyActivity.day(it,zone)==date.toString()}.forEach {e->
            val at=e.optLong("at");if(at>0) {
                val local=Instant.ofEpochMilli(at).atZone(zone)
                hourly[local.hour]+=e.optInt("xp")
            }
        }
        val total=state.optJSONObject("dailyXp")?.optLong(date.toString()) ?: 0L
        // Old baselines can lack timestamps. Never invent their distribution or exceed the total.
        if(hourly.sum()>total)return Hours(emptyList(),false)
        return Hours(hourly.asList().mapIndexedNotNull {h,x->if(x>0)HourlyPoint(h,x.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) else null},hourly.sum()==total)
    }
    fun date(profile:ShareProgress,at:Instant=Instant.now()):LocalDate {
        val zone=profile.zoneId?.let {runCatching {ZoneId.of(it)}.getOrNull()} ?: ZoneId.systemDefault()
        return if(profile.dayStartHour==3)com.malfreyt.alexandre.hamigo.StudyDay.date(at,zone) else at.atZone(zone).toLocalDate()
    }
    fun hourOrder(profile:ShareProgress)=if(profile.dayStartHour==3)(3..23).toList()+(0..2).toList() else (0..23).toList()

    fun dayXp(profile:ShareProgress,date:LocalDate):Int?=profile.activityHistory?.xp(date) ?: profile.dailyXp.firstOrNull {it.day==date.toString()}?.xp
    fun todayXp(profile:ShareProgress,date:LocalDate=date(profile)):Int?=dayXp(profile,date)
    fun earliest(profile:ShareProgress):LocalDate?=profile.activityHistory?.start ?: profile.dailyXp.minOfOrNull {LocalDate.parse(it.day)}
    fun week(profile:ShareProgress,date:LocalDate=date(profile)):List<Pair<LocalDate,Int?>> {
        return com.malfreyt.alexandre.hamigo.CalendarWeek.days(date).map {it to if(it>date(profile))0 else dayXp(profile,it)}
    }
    fun weeklyXp(profile:ShareProgress,date:LocalDate=date(profile)):Int=if(profile.dailyXp.isEmpty()&&profile.activityHistory==null)profile.weeklyXp else
        week(profile,date).sumOf {it.second?.toLong() ?: 0L}.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
