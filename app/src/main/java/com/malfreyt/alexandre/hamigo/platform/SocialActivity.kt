package com.malfreyt.alexandre.hamigo.platform

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Only public aggregates leave the app: no question IDs, exact event times or answers. */
internal object SocialActivity {
    data class Hours(val points:List<HourlyPoint>,val complete:Boolean)
    fun hours(state:JSONObject,date:LocalDate,zone:ZoneId=ZoneId.systemDefault()):Hours {
        val baseline=state.optJSONObject("syncBase") ?: JSONObject()
        val completed=baseline.optJSONArray("completed")?.let {array->(0 until array.length()).map {array.getString(it)}}.orEmpty().toSet()
        val oldAwards=baseline.optJSONObject("awarded") ?: JSONObject()
        val events=state.optJSONObject("syncEvents") ?: JSONObject()
        val awards=linkedMapOf<String,JSONObject>()
        // Same award resolution as the cloud ledger; repeated devices do not add extra XP.
        events.keys().asSequence().sorted().forEach {id->
            val e=events.getJSONObject(id);val xp=e.optInt("xp");if(xp<=0)return@forEach
            val lesson=e.optString("completed");if(lesson in completed)return@forEach
            val day=e.optString("day");val key=if(lesson.isNotBlank())"lesson:$lesson" else e.optString("awardKey").ifBlank {"event:$id"}
            if(key.startsWith("answer:")&&key.endsWith(":$day")&&oldAwards.optString(key.removePrefix("answer:").removeSuffix(":$day"))==day)return@forEach
            val previous=awards[key]
            if(previous==null||xp>previous.optInt("xp")||xp==previous.optInt("xp")&&(day<previous.optString("day")||day==previous.optString("day")&&e.optLong("at") in 1 until previous.optLong("at")))awards[key]=e
        }
        val hourly=LongArray(24)
        awards.values.filter {it.optString("day")==date.toString()}.forEach {e->
            val at=e.optLong("at");if(at>0) {
                val local=Instant.ofEpochMilli(at).atZone(zone)
                if(local.toLocalDate()==date)hourly[local.hour]+=e.optInt("xp")
            }
        }
        val total=state.optJSONObject("dailyXp")?.optLong(date.toString()) ?: 0L
        // Old baselines can lack timestamps. Never invent their distribution or exceed the total.
        if(hourly.sum()>total)return Hours(emptyList(),false)
        return Hours(hourly.asList().mapIndexedNotNull {h,x->if(x>0)HourlyPoint(h,x.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) else null},hourly.sum()==total)
    }
    fun date(profile:ShareProgress):LocalDate=LocalDate.now(profile.zoneId?.let {runCatching {ZoneId.of(it)}.getOrNull()} ?: ZoneId.systemDefault())
    fun dayXp(profile:ShareProgress,date:LocalDate):Int?=profile.activityHistory?.xp(date) ?: profile.dailyXp.firstOrNull {it.day==date.toString()}?.xp
    fun todayXp(profile:ShareProgress,date:LocalDate=date(profile)):Int?=dayXp(profile,date)
    fun earliest(profile:ShareProgress):LocalDate?=profile.activityHistory?.start ?: profile.dailyXp.minOfOrNull {LocalDate.parse(it.day)}
    fun week(profile:ShareProgress,date:LocalDate=date(profile)):List<Pair<LocalDate,Int?>> {
        return com.malfreyt.alexandre.hamigo.CalendarWeek.days(date).map {it to if(it>date(profile))0 else dayXp(profile,it)}
    }
    fun weeklyXp(profile:ShareProgress,date:LocalDate=date(profile)):Int=if(profile.dailyXp.isEmpty()&&profile.activityHistory==null)profile.weeklyXp else
        week(profile,date).sumOf {it.second?.toLong() ?: 0L}.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
