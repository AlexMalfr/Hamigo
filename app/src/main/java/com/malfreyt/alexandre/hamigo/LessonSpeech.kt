package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.runtime.*
import java.util.Locale
import java.util.UUID

internal object LessonNarration {
    fun segments(lesson:Lesson,maxLength:Int):List<String> {
        require(maxLength>=2)
        return (listOf(lesson.title,lesson.summary)+lesson.body+listOf(lesson.formula)).filter {it.isNotBlank()}.flatMap {paragraph ->
            val result=mutableListOf<String>();var rest=FrenchSpeech.prepare(paragraph).trim()
            while(rest.length>maxLength) {
                var end=rest.lastIndexOfAny(charArrayOf(' ','\n'),maxLength).takeIf {it>0} ?: maxLength
                if(end==maxLength && Character.isHighSurrogate(rest[end-1]))end--
                result+=rest.substring(0,end);rest=rest.substring(end).trimStart()
            }
            if(rest.isNotEmpty())result+=rest
            result
        }
    }
}

/** French narration owned by one visible screen; startup and speech can both be cancelled. */
internal class LessonSpeech(private val context:Context,eager:Boolean=true) {
    var reading by mutableStateOf(false);private set
    var speaking by mutableStateOf(false);private set
    private val main=Handler(Looper.getMainLooper())
    private val manager=context.getSystemService(AudioManager::class.java)
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener {if(it<=0)stop()}.build()
    private var ready=false
    @Volatile private var closed=false
    private val setup=java.util.concurrent.Executors.newSingleThreadExecutor()
    private var pending:List<String>?=null
    private var prefix:String?=null
    private var lastId:String?=null
    private var engine:TextToSpeech?=null
    init {if(eager)prepare()}
    private fun prepare() {
        if(engine!=null||closed)return
        engine=TextToSpeech(context.applicationContext) {status ->
            if(!closed)runCatching {setup.execute {
                val current=engine ?: return@execute
                val language=if(status==TextToSpeech.SUCCESS)current.setLanguage(Locale.FRANCE) else TextToSpeech.ERROR
                current.setAudioAttributes(attributes)
                current.setOnUtteranceProgressListener(object:UtteranceProgressListener() {
                    override fun onStart(id:String?) {main.post {if(prefix!=null && id?.startsWith(prefix!!)==true)speaking=true}}
                    override fun onDone(id:String?) {main.post {if(id==lastId)stop()}}
                    @Deprecated("Android callback") override fun onError(id:String?)=failed(id)
                    override fun onError(id:String?,errorCode:Int)=failed(id)
                    private fun failed(id:String?) {main.post {if(prefix!=null && id?.startsWith(prefix!!)==true) {stop();notice("La lecture audio a été interrompue.")}}}
                })
                main.post {
                    if(!closed) {
                        ready=language>=TextToSpeech.LANG_AVAILABLE
                        val text=pending;pending=null
                        if(ready&&reading&&text!=null)start(text)
                        else if(!ready){reading=false;FeedbackAudioGate.release(this@LessonSpeech);engine?.shutdown();engine=null;notice("La voix française n’est pas disponible sur cet appareil.")}
                    }
                }
            }}
        }
    }
    fun toggle(lesson:Lesson) {
        toggleSegments(LessonNarration.segments(lesson,TextToSpeech.getMaxSpeechInputLength()))
    }
    fun toggleText(text:String) {
        toggle(Lesson("question-audio",text,"",emptyList(),"","",emptyList()))
    }
    private fun toggleSegments(texts:List<String>) {
        if(reading){stop();return}
        if(closed||texts.isEmpty())return
        if(!ready){pending=texts;reading=true;FeedbackAudioGate.reserve(this);prepare();return}
        start(texts)
    }
    private fun start(texts:List<String>) {
        stopMorse();stop()
        if(manager.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){notice("La lecture audio n’est pas disponible pour le moment.");return}
        prefix=UUID.randomUUID().toString();lastId="$prefix:${texts.lastIndex}";reading=true
        FeedbackAudioGate.reserve(this)
        texts.forEachIndexed {index,text ->
            if(engine?.speak(text,if(index==0)TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,null,"$prefix:$index")==TextToSpeech.ERROR) {
                stop();notice("La lecture audio n’a pas pu démarrer.");return
            }
        }
    }
    fun stop() {pending=null;prefix=null;lastId=null;reading=false;speaking=false;FeedbackAudioGate.release(this);if(ready)engine?.stop();manager.abandonAudioFocusRequest(focus)}
    fun close() {closed=true;stop();setup.shutdownNow();engine?.shutdown();engine=null}
    private fun notice(text:String) {Toast.makeText(context,text,Toast.LENGTH_SHORT).show()}
}
