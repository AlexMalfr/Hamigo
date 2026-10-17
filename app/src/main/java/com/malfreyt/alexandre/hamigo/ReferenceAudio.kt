package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/** Queues the first tap during startup; the international alphabet is spoken in English. */
class ReferenceAudio(private val context: Context) {
    var activeWord by mutableStateOf<String?>(null);private set
    var speaking by mutableStateOf(false);private set
    val preparing get()=activeWord!=null&&!speaking
    private var ready = false
    private var unavailable=false
    private var closed=false
    private var engine: TextToSpeech? = null
    private val main=android.os.Handler(android.os.Looper.getMainLooper())
    private var utterance:String?=null
    private fun finished(id:String?) {main.post {if(id!=null&&id==utterance)stop()}}
    init {
        engine = TextToSpeech(context.applicationContext) { status -> main.post {if(!closed) {
            ready = status == TextToSpeech.SUCCESS
            if (ready) ready = (engine?.setLanguage(Locale.UK) ?: TextToSpeech.ERROR) >= TextToSpeech.LANG_AVAILABLE
            engine?.setOnUtteranceProgressListener(object:UtteranceProgressListener() {
                override fun onStart(id:String?) {main.post {if(id!=null&&id==utterance)speaking=true}}
                override fun onDone(id:String?)=finished(id)
                @Deprecated("Android callback") override fun onError(id:String?)=finished(id)
                override fun onStop(id:String?,interrupted:Boolean)=finished(id)
            })
            unavailable=!ready
            activeWord?.let {if(ready)start(it) else {stop();notice()}}
        }}}
    }
    fun spell(word: String) {
        if(closed)return
        if(activeWord==word){stop();return}
        stop();stopMorse()
        if(unavailable){notice();return}
        activeWord=word
        FeedbackAudioGate.reserve(this)
        if(ready)start(word)
    }
    private fun start(word:String) {
        utterance=java.util.UUID.randomUUID().toString()
        if(engine?.speak(word, TextToSpeech.QUEUE_FLUSH, null, utterance)==TextToSpeech.ERROR){stop();notice()}
    }
    private fun notice(){Toast.makeText(context,"La voix anglaise n’est pas disponible sur cet appareil.",Toast.LENGTH_SHORT).show()}
    private fun stop(){utterance=null;activeWord=null;speaking=false;engine?.stop();FeedbackAudioGate.release(this)}
    fun morse(code: String) {stop();playMorse(code)}
    fun close() {closed=true;stop();engine?.shutdown();engine=null;stopMorse()}
}

@Composable fun rememberReferenceAudio(): ReferenceAudio {
    val context = LocalContext.current
    val audio = remember(context) { ReferenceAudio(context) }
    DisposableEffect(audio) { onDispose { audio.close() } }
    return audio
}
