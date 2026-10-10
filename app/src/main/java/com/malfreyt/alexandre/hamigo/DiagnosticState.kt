package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.*
import com.malfreyt.alexandre.hamigo.platform.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

internal object DiagnosticAccess {
    val CODE get()=codeForYear(LocalDate.now().year)
    fun codeForYear(year:Int)="73887388$year"
    @Volatile private var livePreferences:SharedPreferences?=null
    fun bind(preferences:SharedPreferences){livePreferences=preferences}
    val networkPaused get()=livePreferences?.getBoolean("sandboxActive",false)==true
    fun matches(expression:String,year:Int=LocalDate.now().year)=expression.filterNot(Char::isWhitespace)==codeForYear(year)
    fun syncPaused(context:Context)=context.getSharedPreferences("hamigo_diagnostics",Context.MODE_PRIVATE).getBoolean("sandboxActive",false)
}
internal val LocalOpenDiagnostics=staticCompositionLocalOf<(() -> Unit)?> {null}
internal val LocalDiagnosticsCovered=compositionLocalOf {false}

internal class DiagnosticSettings(context:Context) {
    private val prefs=context.applicationContext.getSharedPreferences("hamigo_diagnostics",Context.MODE_PRIVATE)
    var enabled by mutableStateOf(prefs.getBoolean("enabled",false));private set
    var frozen by mutableStateOf(prefs.getBoolean("frozen",false));private set
    var sandbox by mutableStateOf<AppModel?>(null);private set
    init {prefs.edit().putBoolean("sandboxActive",false).apply();DiagnosticAccess.bind(prefs)} // Fake data is deliberately temporary.
    fun enable(value:Boolean){enabled=value;prefs.edit().putBoolean("enabled",value).apply()}
    fun freeze(value:Boolean){frozen=value;prefs.edit().putBoolean("frozen",value).apply()}
    fun pauseSync(){prefs.edit().putBoolean("sandboxActive",true).commit()}
    fun useSandbox(value:AppModel?){sandbox=value;prefs.edit().putBoolean("sandboxActive",value!=null).commit()}
}

/** RAM-only preferences and a context that cannot launch external actions. No copies of credentials. */
internal class DiagnosticContext(base:Context):ContextWrapper(base),ActivityResultRegistryOwner {
    private val stores=mutableMapOf<String,MemoryPreferences>()
    // Native screens may register launchers even in read-only previews. Keep their owner local,
    // and return cancellation instead of opening a permission dialog, picker or external app.
    override val activityResultRegistry=object:ActivityResultRegistry() {
        override fun <I,O> onLaunch(requestCode:Int,contract:ActivityResultContract<I,O>,input:I,options:ActivityOptionsCompat?) {
            Handler(Looper.getMainLooper()).post {dispatchResult(requestCode,Activity.RESULT_CANCELED,null)}
        }
    }
    override fun getApplicationContext():Context=this
    override fun getSharedPreferences(name:String,mode:Int):SharedPreferences=stores.getOrPut(name){MemoryPreferences()}
    override fun startActivity(intent:Intent)=Unit
    override fun startActivity(intent:Intent,options:Bundle?)=Unit
}
internal class MemoryPreferences:SharedPreferences {
    private val values=linkedMapOf<String,Any?>()
    private val listeners=linkedSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
    override fun getAll():MutableMap<String,* > = synchronized(this){values.toMutableMap()}
    override fun contains(key:String)=synchronized(this){key in values}
    override fun getString(key:String,defValue:String?)=synchronized(this){values[key] as? String ?: defValue}
    override fun getStringSet(key:String,defValues:MutableSet<String>?)=synchronized(this){(values[key] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: defValues?.toMutableSet()}
    override fun getInt(key:String,defValue:Int)=synchronized(this){values[key] as? Int ?: defValue}
    override fun getLong(key:String,defValue:Long)=synchronized(this){values[key] as? Long ?: defValue}
    override fun getFloat(key:String,defValue:Float)=synchronized(this){values[key] as? Float ?: defValue}
    override fun getBoolean(key:String,defValue:Boolean)=synchronized(this){values[key] as? Boolean ?: defValue}
    override fun registerOnSharedPreferenceChangeListener(listener:SharedPreferences.OnSharedPreferenceChangeListener){listeners.add(listener)}
    override fun unregisterOnSharedPreferenceChangeListener(listener:SharedPreferences.OnSharedPreferenceChangeListener){listeners.remove(listener)}
    override fun edit():SharedPreferences.Editor=object:SharedPreferences.Editor {
        private val changes=linkedMapOf<String,Any?>();private var cleared=false
        override fun putString(key:String,value:String?)=apply {changes[key]=value}
        override fun putStringSet(key:String,values:Set<String>?)=apply {changes[key]=values?.toSet()}
        override fun putInt(key:String,value:Int)=apply {changes[key]=value}
        override fun putLong(key:String,value:Long)=apply {changes[key]=value}
        override fun putFloat(key:String,value:Float)=apply {changes[key]=value}
        override fun putBoolean(key:String,value:Boolean)=apply {changes[key]=value}
        override fun remove(key:String)=apply {changes[key]=null}
        override fun clear()=apply {cleared=true}
        override fun commit():Boolean {
            val changed=synchronized(this@MemoryPreferences) {
                val keys=if(cleared)(values.keys+changes.keys).toSet() else changes.keys.toSet()
                if(cleared)values.clear()
                changes.forEach {(key,v)->if(v==null)values.remove(key) else values[key]=v}
                keys
            }
            changed.forEach {key->listeners.toList().forEach {it.onSharedPreferenceChanged(this@MemoryPreferences,key)}}
            return true
        }
        override fun apply(){commit()}
    }
}

internal object DiagnosticData {
    /** Rebase this RAM-only ledger so arbitrary checks/unchecks do not earn XP or get re-added by old events. */
    fun setCompleted(model:AppModel,ids:Set<String>) {
        check(model.diagnosticModel)
        synchronized(Progress.CLOUD_LOCK) {
            model.progress.reload()
            val root=JSONObject(model.progress.prefs.getString("progress","{}") ?: "{}")
            val valid=model.content!!.lessons.map {it.id}.toSet()
            val completed=JSONArray(ids.filter {it in valid}.sorted())
            root.put("completed",completed)
                .put("syncBase",JSONObject().put("xp",model.progress.xp).put("answers",model.progress.totalAnswers)
                    .put("correct",model.progress.totalCorrect).put("completed",JSONArray(completed.toString()))
                    .put("dailyXp",root.optJSONObject("dailyXp") ?: JSONObject()).put("awarded",root.optJSONObject("awarded") ?: JSONObject()))
                .put("syncEvents",JSONObject())
            model.progress.prefs.edit().putString("progress",root.toString()).commit()
            model.refresh()
        }
    }
    fun seed(model:AppModel,kind:String="active",xp:Int=1240,todayXp:Int=24,days:Int=7,completed:Int=8) {
        check(model.diagnosticModel)
        val content=model.content ?: return
        val today=LocalDate.now();val daily=JSONObject()
        if(kind!="empty") {
            val offset=if(kind=="broken")2 else 0
            val priorDays=(days.coerceIn(0,90)-if(kind!="broken"&&todayXp>0)1 else 0).coerceAtLeast(0)
            (1..priorDays).forEach {n->daily.put(today.minusDays((n+offset).toLong()).toString(),30+(n%4)*12)}
            if(kind!="broken"&&todayXp>0)daily.put(today.toString(),todayXp.coerceIn(0,10000))
        }
        val root=JSONObject().put("schema",2).put("xp",if(kind=="empty")0 else xp.coerceIn(0,1000000))
            .put("dailyXp",daily).put("completed",JSONArray(content.lessons.take(if(kind=="empty")0 else completed.coerceAtLeast(0)).map {it.id}))
            .put("answers",if(kind=="empty")0 else 410).put("correct",if(kind=="empty")0 else 356)
        root.put("syncBase",JSONObject().put("xp",root.getInt("xp")).put("answers",root.getInt("answers")).put("correct",root.getInt("correct"))
            .put("dailyXp",JSONObject(daily.toString())).put("awarded",JSONObject()).put("completed",JSONArray(root.getJSONArray("completed").toString())))
            .put("syncEvents",JSONObject())
        model.progress.prefs.edit().putString("name","Camille · Démo").putString("progress",root.toString()).putInt("dailyGoal",60)
            .putBoolean("welcomed",true).putBoolean("autoSync",false).putBoolean("reminderEnabled",false).commit()
        model.refresh()
        model.friends=listOf(Friend(ShareProgress("Léa · Démo",960,5,6,180)),Friend(ShareProgress("Sam · Démo",1720,12,11,240)))
    }
    fun origin(q:Question)=when {q.id.startsWith("exam1-")||q.image?.startsWith("exam1/")==true||q.source.startsWith("https://exam1.r-e-f.org/")->"Exam1";q.kind=="flash"->"Mémo";q.id.startsWith("proc-")||q.id.startsWith("extra-")||q.source.contains("procédurale")->"Variante";else->"Hamigo"}
    fun typeName(kind:String)=when(kind) {
        "choice"->"Choix unique";"truefalse"->"Vrai / faux";"match"->"Associations";"order"->"Ordre";"sort"->"Classement"
        "number"->"Calcul numérique";"frequency"->"Fréquence";"estimate"->"Estimation";"binary"->"Binaire"
        "multiselect"->"Choix multiples";"morseEncode"->"Composer du Morse";"morseListen"->"Écouter du Morse"
        "cloze"->"Texte à trou";"resistor"->"Résistance";"waveform"->"Signal";"flash"->"Flashcard";else->kind
    }
    fun representatives(content:Content)=content.allQuestions.values.groupBy {it.kind}.toSortedMap().map {(_,items)->items.firstOrNull {it.image==null} ?: items.first()}
    fun courseLocations(content:Content):Map<String,List<String>> {
        val locations=linkedMapOf<String,MutableList<String>>()
        content.chapters.forEachIndexed {chapterIndex,chapter->chapter.lessons.forEachIndexed {lessonIndex,lesson->
            lesson.questions.forEachIndexed {questionIndex,q->
                locations.getOrPut(q.id){mutableListOf()}.add("Parcours · ${chapterIndex+1}. ${chapter.title} → ${lessonIndex+1}. ${lesson.title} · question ${questionIndex+1}")
            }
        }}
        return locations
    }
    fun search(all:List<Question>,query:String,kind:String="",bank:String="",courseLocations:Map<String,List<String>> = emptyMap()):List<Question> {
        val words=query.trim().lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
        return all.filter {q->
            if(kind.isNotBlank()&&q.kind!=kind)return@filter false
            if(bank.isNotBlank()&&origin(q)!=bank)return@filter false
            val corpus=(listOf(q.id,q.prompt,q.explanation,q.topic,q.section,q.kind,q.source,q.image.orEmpty(),q.unit,
                q.visual,q.value?.toString().orEmpty(),q.tolerance.toString(),q.answer.toString(),CourseQuestionMigration47.legacyId(q.id))+
                q.choices+q.bands+q.pairs.flatMap {listOf(it.left,it.right)}+courseLocations[q.id].orEmpty()).joinToString(" ").lowercase()
            words.all(corpus::contains)
        }
    }
}
