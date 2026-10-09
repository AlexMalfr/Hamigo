package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
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
    private val main=android.os.Handler(android.os.Looper.getMainLooper())
    private var utterance:String?=null
    private fun finished(id:String?) {main.post {if(id==utterance)FeedbackAudioGate.release(this)}}
    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) ready = (engine?.setLanguage(Locale.UK) ?: TextToSpeech.ERROR) >= TextToSpeech.LANG_AVAILABLE
            engine?.setOnUtteranceProgressListener(object:UtteranceProgressListener() {
                override fun onStart(id:String?)=Unit
                override fun onDone(id:String?)=finished(id)
                @Deprecated("Android callback") override fun onError(id:String?)=finished(id)
                override fun onStop(id:String?,interrupted:Boolean)=finished(id)
            })
        }
    }
    fun spell(word: String) {
        stopMorse()
        if (!ready) {
            Toast.makeText(context, "La voix anglaise n’est pas encore disponible sur cet appareil.", Toast.LENGTH_SHORT).show()
            return
        }
        FeedbackAudioGate.reserve(this)
        utterance=java.util.UUID.randomUUID().toString()
        if(engine?.speak(word, TextToSpeech.QUEUE_FLUSH, null, utterance)==TextToSpeech.ERROR)FeedbackAudioGate.release(this)
    }
    fun morse(code: String) {utterance=null;engine?.stop();FeedbackAudioGate.release(this);playMorse(code) }
    fun close() {utterance=null;engine?.stop();engine?.shutdown();FeedbackAudioGate.release(this);stopMorse() }
}

@Composable fun rememberReferenceAudio(): ReferenceAudio {
    val context = LocalContext.current
    val audio = remember(context) { ReferenceAudio(context) }
    DisposableEffect(audio) { onDispose { audio.close() } }
    return audio
}
