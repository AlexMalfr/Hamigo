package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.*
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal data class FeedbackSettings(val sound:Boolean=true,val haptics:Boolean=true)
internal object FeedbackPreferences {
    const val SOUND="interactionSound"
    const val HAPTICS="interactionHaptics"
    fun read(prefs:SharedPreferences)=FeedbackSettings(prefs.getBoolean(SOUND,true),prefs.getBoolean(HAPTICS,true))
    fun save(prefs:SharedPreferences,value:FeedbackSettings)=synchronized(Progress.CLOUD_LOCK) {
        prefs.edit().putBoolean(SOUND,value.sound).putBoolean(HAPTICS,value.haptics)
            .apply() // Device preferences: do not overwrite another device's audio choices via GitHub.
    }
}

/** Educational audio remains available when interface sounds are off; it takes precedence. */
internal object FeedbackAudioGate {
    private val owners=java.util.Collections.synchronizedSet(mutableSetOf<Any>())
    val busy get()=owners.isNotEmpty()
    fun ownedBy(owner:Any)=owners.contains(owner)
    fun reserve(owner:Any){owners.add(owner);MorseSidetone.stopOthers(owner);FrequencyReceiverFeedback.stopOthers(owner);AppFeedback.active?.silence()}
    /** The receiver may resume only when no educational playback owns the audio. */
    fun tryReserveReceiver(owner:Any):Boolean {
        synchronized(owners) {if(owners.any {it!==owner})return false;owners.add(owner)}
        AppFeedback.active?.silence()
        return true
    }
    fun release(owner:Any){owners.remove(owner)}
}

internal class AppFeedback(context:Context) {
    private val app=context.applicationContext
    private val originalPrefs=app.getSharedPreferences("hamigo",Context.MODE_PRIVATE)
    private var prefs=originalPrefs
    private val preferenceOwners=linkedMapOf<Any,SharedPreferences>()
    var settings by mutableStateOf(FeedbackPreferences.read(prefs));private set
    private val manager=app.getSystemService(AudioManager::class.java)
    private val vibrator=if(Build.VERSION.SDK_INT>=31)app.getSystemService(VibratorManager::class.java).defaultVibrator else app.getSystemService(Vibrator::class.java)
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private val pool=SoundPool.Builder().setMaxStreams(2).setAudioAttributes(attributes).build()
    private val main=Handler(Looper.getMainLooper())
    private val ids=mutableMapOf<FeedbackCue,Int>()
    private val loaded=mutableSetOf<Int>()
    internal val soundsReady get()=ids.isNotEmpty()&&ids.values.all {it in loaded}
    private val streams=mutableSetOf<Int>()
    private val throttle=FeedbackThrottle()
    private var pendingClick:Runnable?=null
    private var closed=false
    private var foreground=false
    // Observed in instrumented tests; contains no personal data and emits no sound by itself.
    internal var observer:((FeedbackCue,Boolean,Boolean)->Unit)?=null
    private val listener=SharedPreferences.OnSharedPreferenceChangeListener {_,key->
        if(key==FeedbackPreferences.SOUND||key==FeedbackPreferences.HAPTICS) {
            settings=FeedbackPreferences.read(prefs)
            if(!settings.sound)silence()
            if(!settings.haptics)vibrator?.cancel()
        }
    }
    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
        pool.setOnLoadCompleteListener {_,id,status->if(status==0)loaded.add(id)}
        val resources=mapOf("ui_click" to R.raw.ui_click,"ui_success" to R.raw.ui_success,"ui_error" to R.raw.ui_error,
            "ui_complete" to R.raw.ui_complete,"ui_finish" to R.raw.ui_finish,"ui_pico" to R.raw.ui_pico)
        val samples=resources.mapValues {pool.load(app,it.value,1)}
        FeedbackCue.entries.forEach {cue->FeedbackDesign.sound(cue)?.let {ids[cue]=samples.getValue(it)}}
    }
    fun foreground(value:Boolean) {foreground=value;if(value)active=this else {if(active===this)active=null;silence();vibrator?.cancel()}}
    /** Diagnostic screens can exercise mute without persisting changes to the real device preferences. */
    fun selectPreferences(owner:Any,value:SharedPreferences?) {
        if(closed)return
        if(value==null)preferenceOwners.remove(owner) else preferenceOwners[owner]=value
        val next=preferenceOwners.values.lastOrNull() ?: originalPrefs
        if(next===prefs)return
        prefs.unregisterOnSharedPreferenceChangeListener(listener);prefs=next
        prefs.registerOnSharedPreferenceChangeListener(listener);settings=FeedbackPreferences.read(prefs)
        if(!settings.sound)silence();if(!settings.haptics)vibrator?.cancel()
    }
    fun click() {
        pendingClick?.let(main::removeCallbacks)
        val task=Runnable {pendingClick=null;emit(FeedbackCue.CLICK)}
        pendingClick=task;main.postDelayed(task,45) // A verdict or snap replaces the ordinary click.
    }
    fun event(cue:FeedbackCue) {
        pendingClick?.let(main::removeCallbacks);pendingClick=null
        emit(cue)
    }
    private fun emit(cue:FeedbackCue) {
        if(closed||!foreground||!throttle.allow(cue,SystemClock.uptimeMillis()))return
        val audible=settings.sound&&FeedbackDesign.sound(cue)!=null&&!FeedbackAudioGate.busy&&manager.getStreamVolume(AudioManager.STREAM_MUSIC)>0
        val tactile=settings.haptics&&vibrator?.hasVibrator()==true&&
            Settings.System.getInt(app.contentResolver,Settings.System.HAPTIC_FEEDBACK_ENABLED,1)!=0&&FeedbackDesign.haptic(cue)!=null
        observer?.invoke(cue,audible,tactile)
        if(audible) {
            val id=ids[cue]
            if(id!=null&&id in loaded) {
                streams.forEach(pool::stop);streams.clear()
                val volume=FeedbackDesign.volume(cue)
                val stream=pool.play(id,volume,volume,if(cue==FeedbackCue.CLICK)0 else 1,0,1f)
                if(stream!=0)streams.add(stream)
            }
        }
        if(tactile)runCatching {
            val pattern=FeedbackDesign.haptic(cue)!!
            val effect=if(vibrator.hasAmplitudeControl())VibrationEffect.createWaveform(pattern.times,pattern.strengths,-1)
                else VibrationEffect.createWaveform(pattern.times,-1)
            if(Build.VERSION.SDK_INT>=33)vibrator.vibrate(effect,VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_TOUCH).build())
            else @Suppress("DEPRECATION") vibrator.vibrate(effect,attributes)
        }
    }
    fun silence(){
        if(Looper.myLooper()!=main.looper){main.post {silence()};return}
        pendingClick?.let(main::removeCallbacks);pendingClick=null;streams.forEach(pool::stop);streams.clear()
    }
    fun close(){if(closed)return;foreground(false);closed=true;main.removeCallbacksAndMessages(null);prefs.unregisterOnSharedPreferenceChangeListener(listener);pool.release()}
    companion object {internal var active:AppFeedback?=null;private set}
}

internal val LocalAppFeedback=staticCompositionLocalOf<AppFeedback?> {null}
@Composable internal fun FeedbackPreferenceScope(feedback:AppFeedback,prefs:SharedPreferences,content:@Composable ()->Unit) {
    val owner=remember {Any()}
    DisposableEffect(feedback,prefs){feedback.selectPreferences(owner,prefs);onDispose {feedback.selectPreferences(owner,null)}}
    content()
}
@Composable internal fun FeedbackHost(feedback:AppFeedback,content:@Composable ()->Unit) {
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(feedback,lifecycle) {
        feedback.foreground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        val observer=LifecycleEventObserver {_,event->when(event) {
            Lifecycle.Event.ON_RESUME->feedback.foreground(true)
            Lifecycle.Event.ON_PAUSE,Lifecycle.Event.ON_DESTROY->feedback.foreground(false)
            else->Unit
        }}
        lifecycle.addObserver(observer)
        onDispose {lifecycle.removeObserver(observer);feedback.foreground(false)}
    }
    CompositionLocalProvider(LocalAppFeedback provides feedback,content=content)
}
@Composable internal fun feedbackClick(action:()->Unit):()->Unit {
    val feedback=LocalAppFeedback.current
    return {feedback?.click();action()}
}
@Composable internal fun feedbackAction(cue:FeedbackCue,action:()->Unit):()->Unit {
    val feedback=LocalAppFeedback.current
    return {feedback?.event(cue);action()}
}
