package com.malfreyt.alexandre.hamigo

import org.json.JSONObject

/** A reversible path choice, separate from successful answers and SRS evidence. */
internal object IgnoredLessons {
    const val FIELD="ignoredLessons"
    /** Only 0.55's erroneous skip awards have this exact signature; keep their immutable records. */
    fun legacyBonusLesson(event:JSONObject):String? {
        val key=event.optString("awardKey")
        return key.takeIf {it.startsWith("lesson:") && it.length>7 && event.optInt("xp")==6 &&
            event.optString("completed").isBlank() && event.optInt("answers")==0 && event.optInt("correct")==0}
            ?.removePrefix("lesson:")
    }
    fun legacyBonusLessons(events:JSONObject?):Set<String> = events?.keys()?.asSequence()
        ?.mapNotNull {legacyBonusLesson(events.getJSONObject(it))}?.toSet().orEmpty()
    /** Re-project only states whose surviving journal proves the erroneous bonus. Never subtract from a compacted baseline. */
    fun repairLegacyBonuses(state:JSONObject):Boolean {
        if(legacyBonusLessons(state.optJSONObject("syncEvents")).isEmpty())return false
        val base=state.optJSONObject("syncBase") ?: return false
        val before=state.optInt("xp") to state.optJSONObject("dailyXp")?.toString()
        val earned=StudyActivity.earnedEvents(state)
        state.put("xp",(base.optLong("xp")+earned.sumOf {it.getInt("xp").toLong()}).coerceIn(0,Int.MAX_VALUE.toLong()).toInt())
        StudyActivity.rebuild(state)
        return before!=(state.optInt("xp") to state.optJSONObject("dailyXp")?.toString())
    }

    fun selected(records:JSONObject?):Set<String> = records?.keys()?.asSequence()?.filter {records.getJSONObject(it).getBoolean("ignored")}?.toSet().orEmpty()
    fun validate(records:JSONObject) {
        require(records.length()<=5000)
        records.keys().forEach {id->
            require(id.length in 1..128)
            val r=records.getJSONObject(id)
            require(r.keys().asSequence().toSet()==setOf("ignored","modifiedAt"))
            require(r.opt("ignored") is Boolean)
            val t=r.opt("modifiedAt")
            require(t is Number&&t.toDouble().isFinite()&&t.toDouble()%1.0==0.0&&t.toDouble() in 1.0..9_007_199_254_740_991.0)
        }
    }
    fun merge(a:JSONObject?,b:JSONObject?):JSONObject=JSONObject().also {out->
        listOfNotNull(a,b).forEach(::validate)
        (a?.keys()?.asSequence()?.toSet().orEmpty()+b?.keys()?.asSequence()?.toSet().orEmpty()).sorted().forEach {id->
            val x=a?.optJSONObject(id);val y=b?.optJSONObject(id)
            val winner=when {x==null->y!!;y==null->x;x.getLong("modifiedAt")>y.getLong("modifiedAt")->x;y.getLong("modifiedAt")>x.getLong("modifiedAt")->y;!x.getBoolean("ignored")->x;else->y}
            out.put(id,JSONObject(winner.toString()))
        }
    }
    fun update(records:JSONObject?,ids:Set<String>,ignored:Boolean,now:Long):JSONObject = merge(records,null).also {out->
        ids.forEach {id->
            require(id.length in 1..128)
            val old=out.optJSONObject(id)
            if(old?.optBoolean("ignored")!=ignored || old==null&&ignored){
                out.put(id,JSONObject().put("ignored",ignored).put("modifiedAt",maxOf(now,old?.optLong("modifiedAt")?.plus(1) ?: 1)))
            }
        }
        validate(out)
    }
}
