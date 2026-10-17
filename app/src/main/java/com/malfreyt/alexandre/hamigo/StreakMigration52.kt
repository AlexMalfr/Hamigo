package com.malfreyt.alexandre.hamigo

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** Temporary compatibility for 0.52–0.56. Remove at 0.57, keeping StreakHistory and its saved data. */
internal object StreakMigration52 {
    fun apply(state:JSONObject,now:Instant=Instant.now(),zone:ZoneId=ZoneId.systemDefault()):Boolean {
        if(state.has("streakHistory")){StreakHistory.validate(state.getJSONObject("streakHistory"));return false}
        val calendar=now.atZone(zone).toLocalDate();val today=StudyDay.date(now,zone)
        val xp=state.optJSONObject("dailyXp") ?: JSONObject()
        val active=xp.keys().asSequence().filter {xp.optInt(it)>0}.toSet()
        val previous=LearningRules.streak(active,calendar)
        val seeds=active.filter {java.time.LocalDate.parse(it)<=today}.toMutableSet()
        val bridges=JSONObject()
        if(previous>0) {
            // A still-valid streak from yesterday must not become an extra active day today.
            val anchor=when {
                today.toString() in seeds->today
                today.minusDays(1).toString() in seeds->today.minusDays(1)
                else->today.also {seeds+=it.toString()}
            }
            bridges.put(anchor.toString(),previous)
        }
        state.put("streakHistory",JSONObject().put("version",1).put("seedDays",JSONArray(seeds.sorted()))
            .put("bridges",bridges).put("cutoverAt",today.atTime(3,0).atZone(zone).toInstant().toEpochMilli()))
        return true
    }
}
