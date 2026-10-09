package com.malfreyt.alexandre.hamigo

import android.os.SystemClock
import androidx.compose.runtime.*
import kotlinx.coroutines.delay

/** The first tap always controls narration. A burst has its own short-lived visual state. */
internal class PicoNarrationTap {
    private var lastTap=Long.MIN_VALUE
    private var taps=0
    var playful by mutableStateOf(false); private set
    var burst by mutableIntStateOf(0); private set
    var closedEyes by mutableStateOf(false); private set
    var mood by mutableStateOf(MascotMood.GOOFY); private set
    fun tap(now:Long,toggle:()->Unit,stop:()->Unit) {
        taps=if(lastTap!=Long.MIN_VALUE && now-lastTap in 0..450)taps+1 else 1
        lastTap=now
        if(taps>=3) {
            stop();playful=true;burst++
            closedEyes=taps%3==1
            mood=if(taps%3==2)MascotMood.SHOCKED else MascotMood.GOOFY
        } else if(!playful)toggle()
    }
    fun audio(toggle:()->Unit) {reset();toggle()}
    fun reset(){playful=false;closedEyes=false;taps=0;lastTap=Long.MIN_VALUE}
}

@Composable internal fun rememberPicoNarrationTap(key:String):PicoNarrationTap {
    val state=remember(key){PicoNarrationTap()}
    val feedback=LocalAppFeedback.current
    LaunchedEffect(state.burst) {if(state.playful){feedback?.event(FeedbackCue.PICO);delay(2800);state.reset()}}
    return state
}
