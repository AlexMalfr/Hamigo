package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*

internal enum class SpeechInputMode { PUSH_TO_TALK, TOGGLE }

/** The chosen gesture is backed up; Android permission and recognition service stay device-local. */
internal object SpeechInputPreferences {
    const val MODE="speechInputMode"
    fun read(prefs:SharedPreferences):SpeechInputMode =
        if(prefs.getString(MODE,null)=="toggle")SpeechInputMode.TOGGLE else SpeechInputMode.PUSH_TO_TALK
    fun save(prefs:SharedPreferences,mode:SpeechInputMode)=synchronized(Progress.CLOUD_LOCK) {
        if(read(prefs)==mode&&prefs.contains(MODE))return@synchronized
        SyncedAppSettings.save(prefs,mapOf(MODE to if(mode==SpeechInputMode.TOGGLE)"toggle" else "ptt"))
    }
}

@Composable internal fun rememberSpeechInputMode():SpeechInputMode {
    val context=androidx.compose.ui.platform.LocalContext.current
    val prefs=remember(context){context.getSharedPreferences("hamigo",Context.MODE_PRIVATE)}
    var mode by remember(prefs){mutableStateOf(SpeechInputPreferences.read(prefs))}
    DisposableEffect(prefs) {
        val listener=SharedPreferences.OnSharedPreferenceChangeListener {_,key->
            if(key==SpeechInputPreferences.MODE)mode=SpeechInputPreferences.read(prefs)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {prefs.unregisterOnSharedPreferenceChangeListener(listener)}
    }
    return mode
}
