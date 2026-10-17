package com.malfreyt.alexandre.hamigo

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** International alphabet playback shares the same loading and cancellation rules as narration. */
class ReferenceAudio(context:Context) {
    private val playback=PreparedSpeech(context,Locale.UK,"La voix anglaise n’est pas disponible sur cet appareil.")
    private var word by mutableStateOf<String?>(null)
    val activeWord get()=if(playback.active)word else null
    val speaking get()=playback.speaking
    val preparing get()=playback.preparing
    fun spell(value:String) {
        if(activeWord==value)playback.stop() else {word=value;playback.start(listOf(value))}
    }
    fun morse(code:String){playback.stop();playMorse(code)}
    fun close(){playback.close();stopMorse()}
}

@Composable fun rememberReferenceAudio(): ReferenceAudio {
    val context = LocalContext.current
    val audio = remember(context) { ReferenceAudio(context) }
    DisposableEffect(audio) { onDispose { audio.close() } }
    return audio
}
