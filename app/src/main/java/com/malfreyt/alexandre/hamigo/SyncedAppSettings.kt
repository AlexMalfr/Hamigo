package com.malfreyt.alexandre.hamigo

import android.content.SharedPreferences
import org.json.JSONObject
import com.malfreyt.alexandre.hamigo.platform.CloudProgress

/** New settings use independent registers; legacy backup readers can retain their preference whitelist. */
internal object SyncedAppSettings {
    const val FIELD="appSettings"
    private const val LOCAL="syncedAppSettings"
    val keys=setOf("interactionSound","interactionHaptics","morseLiveSound","morseAutoSpaces","speechInputMode","autoSync")
    fun validate(records:JSONObject) {
        require(records.keys().asSequence().all {it in keys}) {"Réglage synchronisé inconnu."}
        records.keys().forEach {key->
            val r=records.getJSONObject(key)
            require(r.keys().asSequence().toSet()==setOf("value","modifiedAt"))
            val t=r.opt("modifiedAt")
            require(t is Number&&t.toDouble().isFinite()&&t.toDouble()%1.0==0.0&&t.toDouble() in 1.0..9_007_199_254_740_991.0)
            if(key=="speechInputMode")require(r.opt("value") in setOf("ptt","toggle")) else require(r.opt("value") is Boolean)
        }
    }
    fun snapshot(prefs:SharedPreferences):JSONObject {
        val records=JSONObject(prefs.getString(LOCAL,"{}") ?: "{}")
        keys.filter {prefs.contains(it)&&!records.has(it)}.forEach {key->records.put(key,JSONObject().put("modifiedAt",1L).put("value",if(key=="speechInputMode")prefs.getString(key,"ptt") else prefs.getBoolean(key,true)))}
        validate(records);return records
    }
    fun merge(a:JSONObject?,b:JSONObject?):JSONObject=JSONObject().also {out->
        listOfNotNull(a,b).forEach(::validate)
        (a?.keys()?.asSequence()?.toSet().orEmpty()+b?.keys()?.asSequence()?.toSet().orEmpty()).sorted().forEach {key->
            val x=a?.optJSONObject(key);val y=b?.optJSONObject(key)
            val winner=when {x==null->y!!;y==null->x;x.getLong("modifiedAt")>y.getLong("modifiedAt")->x;y.getLong("modifiedAt")>x.getLong("modifiedAt")->y;CloudProgress.canonical(x)>=CloudProgress.canonical(y)->x;else->y}
            out.put(key,JSONObject(winner.toString()))
        }
    }
    fun stage(editor:SharedPreferences.Editor,prefs:SharedPreferences,values:Map<String,Any>,floor:JSONObject?=null) {
        require(values.keys.all {it in keys})
        val records=snapshot(prefs)
        values.forEach {(key,value)->
            val old=records.optJSONObject(key)
            if(old?.opt("value")!=value||floor!=null){records.put(key,JSONObject().put("modifiedAt",maxOf(System.currentTimeMillis(),old?.optLong("modifiedAt")?.plus(1) ?: 1,floor?.optJSONObject(key)?.optLong("modifiedAt")?.plus(1) ?: 1)).put("value",value))}
            when(value){is String->editor.putString(key,value);is Boolean->editor.putBoolean(key,value);else->error("Invalid setting type")}
        }
        validate(records);editor.putString(LOCAL,records.toString())
    }
    fun save(prefs:SharedPreferences,values:Map<String,Any>)=synchronized(Progress.CLOUD_LOCK){prefs.edit().also {stage(it,prefs,values)}.apply()}
    fun restore(editor:SharedPreferences.Editor,prefs:SharedPreferences,records:JSONObject,explicit:Boolean=false){
        validate(records)
        if(explicit){stage(editor,prefs,records.keys().asSequence().associateWith {records.getJSONObject(it).get("value")},floor=records);return}
        records.keys().forEach {key->val value=records.getJSONObject(key).get("value");if(value is Boolean)editor.putBoolean(key,value) else editor.putString(key,value as String)}
        editor.putString(LOCAL,records.toString())
    }
}
