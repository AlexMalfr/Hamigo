package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
import java.util.concurrent.CopyOnWriteArraySet

/** Continuous reception after the first gesture, yielding to narration and Morse. */
internal class FrequencyReceiverFeedback(context:Context,private val target:Double):AutoCloseable {
    private val lock=Object()
    private val main=Handler(Looper.getMainLooper())
    @Volatile private var closed=false
    @Volatile private var requested=false
    @Volatile private var streaming=false
    @Volatile private var frequency=143.0
    @Volatile private var track:AudioTrack?=null
    @Volatile private var voice=cachedVoice
    internal val voiceReady get()=voice?.isNotEmpty()==true
    internal val playing get()=streaming && track?.playState==AudioTrack.PLAYSTATE_PLAYING
    internal val voiceDurationMillis get()=(voice?.size ?: 0)*1000L/16000
    @Volatile internal var voiceFailure:String?=null;private set
    private var engine:TextToSpeech?=null
    private var file:File?=null
    private val worker=Thread({runReceiver()},"Hamigo-Receiver").apply {isDaemon=true}
    init {outputs.add(this);worker.start();if(voice==null)main.post {if(!closed)prepareVoice(context.applicationContext)}}
    fun tune(value:Float) {
        if(closed || !value.isFinite())return
        synchronized(lock){
            if(closed)return
            frequency=value.toDouble();requested=true;lock.notifyAll()
        }
    }
    fun stop() {
        synchronized(lock){requested=false;streaming=false;FeedbackAudioGate.release(this)}
        track?.let {runCatching {it.pause();it.flush()}}
    }
    private fun suspendForPriority() {
        synchronized(lock){streaming=false;FeedbackAudioGate.release(this)}
        track?.let {runCatching {it.pause();it.flush()}}
    }
    override fun close() {
        closed=true;stop();outputs.remove(this)
        synchronized(lock){lock.notifyAll()};if(Thread.currentThread()!==worker)worker.interrupt()
        main.post {engine?.stop();engine?.shutdown();engine=null;file?.delete();file=null}
    }
    private fun prepareVoice(context:Context) {
        val bytes=ByteArrayOutputStream();var rate=16000;var channels=1;var bits=16
        val utterance="receiver-voice"
        runCatching {
            file=File.createTempFile("hamigo-receiver-",".wav",context.cacheDir)
            engine=TextToSpeech(context) {status->main.post {
                val current=engine
                if(closed||current==null)return@post
                if(status!=TextToSpeech.SUCCESS || current.setLanguage(Locale.FRANCE)<TextToSpeech.LANG_AVAILABLE) {
                    voiceFailure="French TTS initialization failed ($status)"
                    current.shutdown();engine=null;file?.delete();file=null;return@post
                }
                current.setOnUtteranceProgressListener(object:UtteranceProgressListener() {
                    override fun onStart(id:String?)=Unit
                    override fun onBeginSynthesis(id:String?,sampleRateInHz:Int,audioFormat:Int,channelCount:Int) {
                        rate=sampleRateInHz;channels=channelCount;bits=when(audioFormat){AudioFormat.ENCODING_PCM_8BIT->8;AudioFormat.ENCODING_PCM_FLOAT->32;else->16}
                    }
                    override fun onAudioAvailable(id:String?,audio:ByteArray?) {if(audio!=null)synchronized(bytes){if(bytes.size()+audio.size<=4_000_000)bytes.write(audio)}}
                    override fun onDone(id:String?) {
                        val pcm=runCatching {synchronized(bytes){ReceiverVoicePcm.decode(bytes.toByteArray(),rate,channels,bits)}}
                            .onFailure {voiceFailure="PCM conversion: ${it.javaClass.simpleName} ($rate Hz, $channels channels, $bits bits)"}.getOrNull()
                        if(pcm?.isEmpty()==true)voiceFailure="No PCM returned by TTS"
                        if(!closed && pcm?.isNotEmpty()==true){voice=pcm;cachedVoice=pcm}
                        finished()
                    }
                    @Deprecated("Android callback") override fun onError(id:String?){voiceFailure="TTS synthesis failed";finished()}
                    override fun onError(id:String?,errorCode:Int){voiceFailure="TTS synthesis failed ($errorCode)";finished()}
                    private fun finished(){main.post {if(engine===current){current.shutdown();engine=null};file?.delete();file=null}}
                })
                current.setSpeechRate(.95f)
                if(current.synthesizeToFile("Ici Pico, à l'écoute sur la fréquence d'entraînement. Bonjour à toutes les stations. Nous faisons un essai de réception. Tourne doucement le réglage et cherche la voix la plus claire. Si tu entends ce message sans trop de souffle, notre contact passe bien. Je reprends le message depuis le début.",null,file!!,utterance)==TextToSpeech.ERROR) {
                    voiceFailure="TTS refused synthesis"
                    current.shutdown();engine=null;file?.delete();file=null
                }
            }}
        }.onFailure {voiceFailure="TTS preparation: ${it.javaClass.simpleName}";file?.delete();file=null}
    }
    private fun runReceiver() {
        val signal=ReceiverSignal();val buffer=ShortArray(160)
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
            while(!closed) {
                synchronized(lock){while(!closed && !requested)lock.wait()}
                if(closed)break
                if(!FeedbackAudioGate.tryReserveReceiver(this)) {
                    synchronized(lock){if(!closed&&requested)lock.wait(120)}
                    continue
                }
                var current:AudioTrack?=null
                try {
                    val minimum=AudioTrack.getMinBufferSize(16000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)
                    check(minimum>0)
                    current=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setBufferSizeInBytes(maxOf(minimum,640)).setTransferMode(AudioTrack.MODE_STREAM).build()
                    synchronized(lock){
                        if(!closed&&requested&&FeedbackAudioGate.ownedBy(this)){track=current;streaming=true;signal.restartEnvelope();current.play()}
                    }
                    while(!closed && requested && streaming) {
                        signal.render(buffer,frequency,target,voice)
                        if(current.write(buffer,0,buffer.size,AudioTrack.WRITE_BLOCKING)<=0){if(streaming&&!closed)requested=false;break}
                    }
                } catch(_:Exception) {if(streaming&&!closed)requested=false}
                finally {
                    streaming=false;track=null;current?.let {runCatching {it.stop()};runCatching {it.release()}}
                    FeedbackAudioGate.release(this)
                }
            }
        } catch(_:InterruptedException) { /* Screen disposed or muted. */ }
        catch(_:Exception) { /* Audio failure must not interrupt a question. */ }
        finally {close()}
    }
    companion object {
        @Volatile private var cachedVoice:ShortArray?=null
        private val outputs=CopyOnWriteArraySet<FrequencyReceiverFeedback>()
        fun stopOthers(owner:Any){outputs.forEach {if(it!==owner)it.suspendForPriority()}}
    }
}

@Composable internal fun rememberFrequencyReceiver(enabled:Boolean,key:Any?,target:Double):FrequencyReceiverFeedback? {
    val context=LocalContext.current
    val factory=remember(context,key,target){{FrequencyReceiverFeedback(context,target)}}
    return rememberInteractiveAudio(enabled,factory)
}
