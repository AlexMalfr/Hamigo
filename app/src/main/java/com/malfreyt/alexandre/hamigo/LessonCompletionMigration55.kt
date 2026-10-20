package com.malfreyt.alexandre.hamigo

import org.json.JSONArray
import org.json.JSONObject

/** Only completions earned before the split can cover all its new parts. */
object LessonCompletionMigration55 {
    const val FIELD="legacyCompletedBefore55"

    fun capture(state:JSONObject):Boolean {
        if(state.has(FIELD)) {read(state);return false}
        val old=strings(state.optJSONArray("completed")).toMutableSet()
        old+=strings(state.optJSONObject("syncBase")?.optJSONArray("completed"))
        state.optJSONObject("syncEvents")?.let {events->events.keys().forEach {id->
            val completed=events.getJSONObject(id).optString("completed")
            if(completed.isNotBlank()) {require(completed.length<=128);old+=completed}
        }}
        require(old.size<=10_000) {"Trop de validations de leçons."}
        state.put(FIELD,JSONArray(old.sorted()))
        return true
    }

    fun read(state:JSONObject):Set<String> {
        require(!state.has(FIELD)||state.opt(FIELD) is JSONArray) {"Historique des validations antérieures invalide."}
        return strings(state.optJSONArray(FIELD))
    }

    fun apply(state:JSONObject,requiredLegacy:Map<String,Set<String>>):Boolean {
        var changed=capture(state)
        val completed=strings(state.optJSONArray("completed"))
        val migrated=completed+SkillLearning.migratedCompleted(read(state),requiredLegacy)
        if(migrated!=completed) {state.put("completed",JSONArray(migrated.sorted()));changed=true}
        return changed
    }

    fun merged(left:JSONObject,right:JSONObject)=JSONArray((read(left)+read(right)).sorted())

    private fun strings(array:JSONArray?):Set<String> {
        if(array==null)return emptySet()
        require(array.length()<=10_000) {"Trop de validations de leçons."}
        return (0 until array.length()).map {index->
            val value=array.get(index)
            require(value is String&&value.length in 1..128) {"Identifiant de validation invalide."}
            value
        }.toSet()
    }
}
