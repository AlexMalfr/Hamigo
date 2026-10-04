package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** The international spelling alphabet is spoken in English, not with French letter names. */
class ReferenceAudio(private val context: Context) {
    private var ready = false
    private var engine: TextToSpeech? = null
    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) ready = (engine?.setLanguage(Locale.UK) ?: TextToSpeech.ERROR) >= TextToSpeech.LANG_AVAILABLE
        }
    }
    fun spell(word: String) {
        stopMorse()
        if (!ready) {
            Toast.makeText(context, "La voix anglaise n’est pas encore disponible sur cet appareil.", Toast.LENGTH_SHORT).show()
            return
        }
        engine?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "hamigo-spelling")
    }
    fun morse(code: String) { engine?.stop(); playMorse(code) }
    fun close() { engine?.stop(); engine?.shutdown(); stopMorse() }
}

@Composable fun rememberReferenceAudio(): ReferenceAudio {
    val context = LocalContext.current
    val audio = remember(context) { ReferenceAudio(context) }
    DisposableEffect(audio) { onDispose { audio.close() } }
    return audio
}
