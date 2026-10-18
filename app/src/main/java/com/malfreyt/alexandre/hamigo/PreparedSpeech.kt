package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.compose.runtime.*
import java.io.File
import java.util.Locale
import java.util.UUID

/** Synthesize first, then play: loading can settle without animating over audible speech. */
internal class PreparedSpeech(private val context:Context,private val locale:Locale,private val unavailable:String,eager:Boolean=true):AutoCloseable {
    var active by mutableStateOf(false);private set
    // Narration lifetime after its first sound, including silence between synthesized files.
    var speaking by mutableStateOf(false);private set
    var preparing by mutableStateOf(false);private set
    var mouthActivity by mutableFloatStateOf(0f);private set
    private val envelopes=mutableMapOf<Int,SpeechEnvelope?>()
    private var meter:Runnable?=null
    private val main=Handler(Looper.getMainLooper())
    private val setup=java.util.concurrent.Executors.newSingleThreadExecutor()
    private val manager=context.getSystemService(AudioManager::class.java)
    private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes).setOnAudioFocusChangeListener {if(it<=0)stop()}.build()
    private var focused=false
    @Volatile private var closed=false
    private var engine:TextToSpeech?=null
    private var ready=false
    private var queued=false
    private var token:String?=null
    private var texts=emptyList<String>()
    private val files=mutableListOf<File>()
    private var complete=BooleanArray(0)
    private var index=0
    private var player:MediaPlayer?=null
    private var waiting=false
    private var timing=SpeechLoadingTiming()
    private var reveal:Runnable?=null
    private var launch:Runnable?=null
    init {
        if(cleaned.compareAndSet(false,true))runCatching {context.cacheDir.listFiles {_,name->name.startsWith("hamigo-speech-")&&name.endsWith(".wav")}.orEmpty().forEach {it.delete()}}
        if(eager)prepareEngine()
    }
    private fun prepareEngine() {
        if(engine!=null||closed)return
        runCatching {engine=TextToSpeech(context.applicationContext) {status->main.post {
            if(!closed)runCatching {setup.execute {
                val current=engine ?: return@execute
                val language=runCatching {if(status==TextToSpeech.SUCCESS)current.setLanguage(locale) else TextToSpeech.ERROR}.getOrDefault(TextToSpeech.ERROR)
                current.setOnUtteranceProgressListener(object:UtteranceProgressListener() {
                    override fun onStart(id:String?)=Unit // Synthesis is not playback.
                    override fun onDone(id:String?){main.post {
                        val prefix=token
                        if(!closed&&active&&prefix!=null&&id?.startsWith(prefix+":")==true) {
                            val part=id.substringAfterLast(':').toIntOrNull()
                            if(part!=null&&part in complete.indices) {
                                val file=files[part]
                                setup.execute {
                                    val envelope=SpeechEnvelope.fromWav(file)
                                    main.post {if(active&&token==prefix&&part in complete.indices) {
                                        envelopes[part]=envelope;complete[part]=true;prepareCurrent()
                                    }}
                                }
                            }
                        }
                    }}
                    @Deprecated("Android callback") override fun onError(id:String?){main.post {if(token!=null&&id?.startsWith(token+":")==true)fail("La lecture audio a été interrompue.")}}
                    override fun onError(id:String?,errorCode:Int)=onError(id)
                })
                main.post {
                    if(!closed&&engine===current) {
                        ready=language>=TextToSpeech.LANG_AVAILABLE
                        if(ready)queue() else {current.shutdown();engine=null;if(active)fail(unavailable)}
                    }
                }
            }}
        }}}.onFailure {engine=null;if(active)fail(unavailable)}
    }
    fun start(segments:List<String>) {
        stop()
        if(closed||segments.isEmpty())return
        stopMorse();texts=segments.toList();complete=BooleanArray(texts.size);index=0;token=UUID.randomUUID().toString();active=true
        FeedbackAudioGate.reserve(this);beginWait()
        if(ready)queue() else prepareEngine()
    }
    private fun queue() {
        if(!active||!ready||queued||closed)return
        queued=true
        val prefix=token ?: return
        runCatching {
            texts.forEachIndexed {part,text->
                val file=File.createTempFile("hamigo-speech-",".wav",context.cacheDir);files+=file
                check(engine?.synthesizeToFile(text,null,file,"$prefix:$part")==TextToSpeech.SUCCESS)
            }
        }.onFailure {fail("La lecture audio n’a pas pu démarrer.")}
    }
    private fun beginWait() {
        waiting=true;preparing=false;timing=SpeechLoadingTiming()
        reveal?.let(main::removeCallbacks)
        reveal=Runnable {if(active&&waiting&&!speaking){timing.revealed(SystemClock.uptimeMillis());preparing=true}}
        main.postDelayed(reveal!!,timing.revealDelayMs)
    }
    private fun prepareCurrent() {
        if(closed||!active||player!=null||index !in complete.indices||!complete[index])return
        val expectedToken=token;val part=index
        runCatching {
            val media=MediaPlayer();player=media
            media.setAudioAttributes(attributes);media.setDataSource(files[part].absolutePath)
            media.setOnPreparedListener {
                if(active&&token==expectedToken&&index==part&&player===media) {
                    reveal?.let(main::removeCallbacks)
                    val at=timing.playbackAt(SystemClock.uptimeMillis())
                    launch=Runnable {
                        if(active&&token==expectedToken&&index==part&&player===media) {
                            if(!focused){focused=manager.requestAudioFocus(focus)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED}
                            if(!focused){fail("La lecture audio n’est pas disponible pour le moment.");return@Runnable}
                            runCatching {media.start();waiting=false;preparing=false;speaking=true;followAudio(media,expectedToken,part)}.onFailure {fail("La lecture audio a été interrompue.")}
                        }
                    }
                    main.postAtTime(launch!!,at)
                }
            }
            media.setOnCompletionListener {
                if(player===media&&token==expectedToken) {
                    stopMeter();media.release();player=null;files[part].delete();envelopes.remove(part);index++
                    if(index>=texts.size)stop() else {beginWait();prepareCurrent()}
                }
            }
            media.setOnErrorListener {_,_,_->if(player===media)fail("La lecture audio a été interrompue.");true}
            media.prepareAsync()
        }.onFailure {fail("La lecture audio n’a pas pu démarrer.")}
    }
    private fun followAudio(media:MediaPlayer,expectedToken:String?,part:Int) {
        stopMeter()
        meter=object:Runnable {
            override fun run() {
                if(active&&token==expectedToken&&player===media&&speaking) {
                    mouthActivity=runCatching {envelopes[part]?.mouthAt(media.currentPosition) ?: 1f}.getOrDefault(0f)
                    main.postDelayed(this,16)
                }
            }
        }
        main.post(meter!!)
    }
    private fun stopMeter(){meter?.let(main::removeCallbacks);meter=null;mouthActivity=0f}
    fun stop() {
        stopMeter();envelopes.clear()
        token=null;active=false;speaking=false;preparing=false;waiting=false;queued=false
        reveal?.let(main::removeCallbacks);launch?.let(main::removeCallbacks);reveal=null;launch=null
        player?.let {runCatching {it.release()}};player=null
        if(ready)engine?.stop()
        files.forEach {it.delete()};files.clear();texts=emptyList();complete=BooleanArray(0);index=0
        FeedbackAudioGate.release(this);if(focused)manager.abandonAudioFocusRequest(focus);focused=false
    }
    private fun fail(message:String){stop();Toast.makeText(context,message,Toast.LENGTH_SHORT).show()}
    override fun close(){closed=true;stop();setup.shutdownNow();engine?.shutdown();engine=null}
    companion object {private val cleaned=java.util.concurrent.atomic.AtomicBoolean(false)}
}
