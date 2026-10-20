package com.malfreyt.alexandre.hamigo

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** Event identities stay immutable; only their presentation day is projected. */
internal object StudyActivity {
    fun day(event:JSONObject,zone:ZoneId=ZoneId.systemDefault()):String = event.optString("streakDay").ifBlank {
        if(event.optLong("at")>0)StudyDay.date(Instant.ofEpochMilli(event.getLong("at")),zone).toString() else event.getString("day")
    }
    fun questionId(event:JSONObject):String? {
        val key=event.optString("awardKey");val suffix=":"+event.optString("day")
        val prefix=when {key.startsWith("answer3:")->"answer3:";key.startsWith("answer:")->"answer:";else->return null}
        return key.takeIf {it.endsWith(suffix)}?.removePrefix(prefix)?.removeSuffix(suffix)
    }
    /** A real completion suppressed by the old shared skip award still deserves its normal finish bonus. */
    fun rewardXp(event:JSONObject,legacySkipped:Set<String>):Int = when {
        IgnoredLessons.legacyBonusLesson(event)!=null -> 0
        event.optInt("xp")==0 && event.optString("completed").let {it.isNotBlank()&&it in legacySkipped} &&
            event.optInt("answers")==0 && event.optInt("correct")==0 -> 6
        else -> event.optInt("xp")
    }
    fun earnedEvents(state:JSONObject):List<JSONObject> {
        val base=state.optJSONObject("syncBase") ?: JSONObject()
        val completed=base.optJSONArray("completed")?.let {a->(0 until a.length()).map {a.getString(it)}.toSet()}.orEmpty()
        val historical=base.optJSONObject("awarded") ?: JSONObject()
        val events=state.optJSONObject("syncEvents") ?: JSONObject()
        val legacySkipped=IgnoredLessons.legacyBonusLessons(events)
        val awards=linkedMapOf<String,JSONObject>()
        events.keys().asSequence().sorted().forEach {id->
            val original=events.getJSONObject(id);val xp=rewardXp(original,legacySkipped);if(xp<=0)return@forEach
            val e=if(xp==original.optInt("xp"))original else JSONObject(original.toString()).put("xp",xp)
            val lesson=e.optString("completed");if(lesson.isNotBlank()&&lesson in completed)return@forEach
            val rawDay=e.getString("day")
            val key=if(lesson.isNotBlank())"lesson:$lesson" else e.optString("awardKey").ifBlank {"event:$id"}
            if(questionId(e)?.let {historical.optString(it)==rawDay}==true)return@forEach
            val old=awards[key]
            if(old==null||xp>old.optInt("xp")||xp==old.optInt("xp")&&(rawDay<old.getString("day")||rawDay==old.getString("day")&&e.optLong("at")<old.optLong("at")))awards[key]=e
        }
        return awards.values.toList()
    }
    fun rebuild(state:JSONObject) {
        val base=state.optJSONObject("syncBase") ?: return
        val days=JSONObject((base.optJSONObject("dailyXp") ?: JSONObject()).toString())
        val paid=JSONObject((state.optJSONObject("awarded") ?: JSONObject()).toString())
        val eventAwards=mutableMapOf<String,String>()
        earnedEvents(state).forEach {e->
            val date=day(e);days.put(date,(days.optLong(date)+e.getInt("xp")).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            questionId(e)?.let {id->if(date>eventAwards[id].orEmpty())eventAwards[id]=date}
        }
        eventAwards.forEach {(id,date)->paid.put(id,maxOf(date,base.optJSONObject("awarded")?.optString(id).orEmpty()))}
        val sortedPaid=JSONObject();paid.keys().asSequence().sorted().forEach {sortedPaid.put(it,paid.getString(it))}
        state.put("dailyXp",days).put("awarded",sortedPaid).put("activityDayStartHour",3)
    }
}
