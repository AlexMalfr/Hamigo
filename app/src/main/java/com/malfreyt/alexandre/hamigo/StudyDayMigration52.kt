package com.malfreyt.alexandre.hamigo

import org.json.JSONObject

/** Temporary conversion 0.52–0.56. Remove at 0.57, retaining StudyActivity and the day marker. */
internal object StudyDayMigration52 {
    fun apply(state:JSONObject):Boolean {
        if(state.has("activityDayStartHour")) {require(state.getInt("activityDayStartHour")==3);return false}
        StudyActivity.rebuild(state)
        state.put("activityDayStartHour",3)
        return true
    }
}
