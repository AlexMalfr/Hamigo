package com.malfreyt.alexandre.hamigo

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

internal enum class PicoReaction { PLAY, STUDY, LISTEN, ANSWER }

/** Local, ephemeral reactions. No progress, sound or saved preference is changed. */
@Stable internal class PicoCompanionState {
    var mood by mutableStateOf(MascotMood.HAPPY); private set
    var pose by mutableStateOf(MascotPose.WAVE); private set
    var reaction by mutableIntStateOf(0); private set
    var active by mutableStateOf(false)
    private var lastInteraction=0L
    private var expression=0
    fun react(event:PicoReaction) {
        lastInteraction=SystemClock.uptimeMillis();reaction++
        mood=when(event) {
            PicoReaction.PLAY -> if(reaction%2==0)MascotMood.SHOCKED else MascotMood.GOOFY
            PicoReaction.STUDY -> MascotMood.DETERMINED
            PicoReaction.LISTEN -> MascotMood.THINKING
            PicoReaction.ANSWER -> MascotMood.HAPPY
        }
        pose=if(event==PicoReaction.PLAY)MascotPose.HUG else if(event==PicoReaction.STUDY)MascotPose.POINT else MascotPose.WAVE
    }
    fun idle() {
        if(SystemClock.uptimeMillis()-lastInteraction<6000)return
        expression++
        mood=listOf(MascotMood.HAPPY,MascotMood.THINKING,MascotMood.DETERMINED,MascotMood.GOOFY)[expression%4]
        pose=if(expression%2==0)MascotPose.WAVE else MascotPose.IDLE
    }
}

@Composable internal fun rememberPicoCompanion(key:String,enabled:Boolean=true):PicoCompanionState {
    val state=remember(key) {PicoCompanionState()}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(key,enabled,lifecycle) {
        if(enabled)lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            state.active=true
            try {while(true){delay(12_000);state.idle()}} finally {state.active=false}
        }
    }
    return state
}
