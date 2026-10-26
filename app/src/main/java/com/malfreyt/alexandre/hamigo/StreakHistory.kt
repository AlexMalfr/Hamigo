package com.malfreyt.alexandre.hamigo

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

/** Permanent streak projection, independent of calendar-day XP and reward identities. */
internal object StreakHistory {
    /** New progress starts empty; saved history is validated and kept byte-for-byte. */
    fun initialize(state:JSONObject):Boolean {
        if(state.has("streakHistory")){validate(state.getJSONObject("streakHistory"));return false}
        state.put("streakHistory",JSONObject().put("version",1).put("seedDays",JSONArray())
            .put("bridges",JSONObject()).put("cutoverAt",0L))
        return true
    }
    fun merge(a:JSONObject,b:JSONObject):JSONObject {
        val left=a.getJSONObject("streakHistory");val right=b.getJSONObject("streakHistory")
        val seeds=(days(left)+days(right)).sorted()
        val bridges=JSONObject()
        listOf(left,right).forEach { h->h.getJSONObject("bridges").let {map->map.keys().forEach {day->bridges.put(day,maxOf(bridges.optInt(day),map.getInt(day)))}} }
        val sorted=JSONObject();bridges.keys().asSequence().sorted().forEach {sorted.put(it,bridges.getInt(it))}
        return JSONObject().put("version",1).put("seedDays",JSONArray(seeds)).put("bridges",sorted)
            .put("cutoverAt",listOf(left.getLong("cutoverAt"),right.getLong("cutoverAt")).filter {it>0L}.minOrNull() ?: 0L)
    }
    private fun days(history:JSONObject):Set<String> = history.getJSONArray("seedDays").let {array->(0 until array.length()).map {array.getString(it)}.toSet()}
    fun count(state:JSONObject,today:LocalDate=StudyDay.date()):Int {
        val h=state.getJSONObject("streakHistory")
        val active=days(h).toMutableSet()
        val events=state.optJSONObject("syncEvents") ?: JSONObject()
        val legacySkipped=IgnoredLessons.legacyBonusLessons(events)
        val historicalCompletions=state.optJSONObject("syncBase")?.optJSONArray("completed")?.let {array->
            (0 until array.length()).map {array.getString(it)}.toSet()}.orEmpty()
        events.keys().forEach {id->val e=events.getJSONObject(id)
            val earned=if(e.optInt("xp")==0&&e.optString("completed") in historicalCompletions)0 else StudyActivity.rewardXp(e,legacySkipped)
            if(earned>0&&e.optLong("at")>=h.getLong("cutoverAt")) {
                active+=e.optString("streakDay").ifBlank {StudyDay.date(Instant.ofEpochMilli(e.getLong("at"))).toString()}
            }
        }
        var result=LearningRules.streak(active,today)
        if(result==0)return 0
        val end=if(today.toString() in active)today else today.minusDays(1)
        val start=end.minusDays(result.toLong()-1)
        h.getJSONObject("bridges").let {bridges->bridges.keys().forEach {key->
            val anchor=LocalDate.parse(key)
            if(anchor in start..end)result=maxOf(result,(bridges.getLong(key)+java.time.temporal.ChronoUnit.DAYS.between(anchor,end)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }}
        return result
    }
    fun validate(h:JSONObject) {
        fun integral(key:String,container:JSONObject=h):Long {
            val n=container.get(key);require(n is Number&&n.toDouble().isFinite()&&n.toDouble()%1.0==0.0&&n.toDouble() in 0.0..9_007_199_254_740_991.0)
            return n.toLong()
        }
        require(integral("version")==1L);integral("cutoverAt")
        val array=h.getJSONArray("seedDays");require(array.length()<=100_000)
        (0 until array.length()).forEach {require(array.get(it) is String);val day=array.getString(it);require(day.length==10);LocalDate.parse(day)}
        val bridges=h.getJSONObject("bridges");require(bridges.length()<=36_600)
        bridges.keys().forEach {require(it.length==10);LocalDate.parse(it);require(integral(it,bridges) in 1..Int.MAX_VALUE.toLong())}
    }
}
