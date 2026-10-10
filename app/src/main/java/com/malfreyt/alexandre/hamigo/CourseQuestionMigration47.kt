package com.malfreyt.alexandre.hamigo

import com.malfreyt.alexandre.hamigo.platform.CloudProgress
import org.json.JSONObject

/** Temporary 0.47 compatibility: remove at 0.52, including its generated aliases and call sites. */
internal object CourseQuestionMigration47 {
    const val REMOVE_AT = 52
    private val legacyById by lazy {CourseQuestionAliases47.ids.entries.associate {it.value to it.key}}
    fun id(value:String)=CourseQuestionAliases47.ids[value] ?: value
    fun legacyId(value:String)=legacyById[value].orEmpty()
    fun apply(state:JSONObject):Boolean {
        require(!state.has("questionIdsVersion") || state.opt("questionIdsVersion") is Number && state.optInt("questionIdsVersion")==1) {
            "Version des identifiants de questions non reconnue."
        }
        val before=CloudProgress.canonical(state)
        fun rekey(container:JSONObject,key:String,reviews:Boolean=false) {
            val source=container.optJSONObject(key) ?: return
            val result=JSONObject()
            source.keys().asSequence().sorted().forEach {old->
                val target=id(old);val value=source.get(old)
                val previous=result.opt(target)
                val chosen=when {
                    previous==null->value
                    reviews -> {
                        val a=previous as JSONObject;val b=value as JSONObject
                        if(b.optLong("updatedAt")>a.optLong("updatedAt") ||
                            b.optLong("updatedAt")==a.optLong("updatedAt") && CloudProgress.canonical(b)>CloudProgress.canonical(a)) b else a
                    }
                    value.toString()>previous.toString()->value
                    else->previous
                }
                result.put(target,chosen)
            }
            container.put(key,result)
        }
        rekey(state,"reviews",true);rekey(state,"awarded")
        state.optJSONObject("syncBase")?.let {rekey(it,"awarded")}
        state.optJSONObject("syncEvents")?.let {events->events.keys().forEach {eventId->
            val event=events.getJSONObject(eventId)
            val award=event.optString("awardKey")
            val suffix=":"+event.optString("day")
            if(award.startsWith("answer:") && award.endsWith(suffix)) {
                val old=award.removePrefix("answer:").removeSuffix(suffix)
                event.put("awardKey","answer:${id(old)}$suffix")
            }
        }}
        state.put("questionIdsVersion",1)
        return before!=CloudProgress.canonical(state)
    }
}
