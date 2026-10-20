package com.malfreyt.alexandre.hamigo

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import kotlin.math.sin
import kotlin.random.Random

internal data class MorsePcm(val samples:ShortArray,val rate:Int,val signalRanges:List<IntRange>)
internal object MorseSignalAudio {
    const val LEAD_MS=800
    const val DOT_MS=90
    const val MAX_DURATION_MS=180_000
    private const val RATE=16000
    private fun timing(code:String):Pair<Int,List<IntRange>> {
        val normalized=normalizeMorse(code)
        require(normalized.length<=12_000){"Le signal Morse dépasse la longueur prise en charge."}
        val words=normalized.split('/').map {it.trim().split(Regex("\\s+")).filter(String::isNotBlank)}.filter(List<String>::isNotEmpty)
        val rate=RATE;val dot=rate*DOT_MS/1000
        val lead=rate*LEAD_MS/1000;val tail=rate*LEAD_MS/1000
        val ranges=mutableListOf<IntRange>();var cursor=lead
        words.forEachIndexed {wi,letters->letters.forEachIndexed {li,symbols->symbols.forEachIndexed {si,c->
            if(c=='.'||c=='-') {
                val length=dot*if(c=='.')1 else 3
                ranges+=cursor until cursor+length;cursor+=length
                cursor+=dot*when {si<symbols.lastIndex->1;li<letters.lastIndex->3;wi<words.lastIndex->7;else->1}
            }
        }}}
        if(ranges.isEmpty())return 0 to emptyList()
        require(cursor+tail<=rate*(MAX_DURATION_MS/1000)){"Le signal Morse dépasse trois minutes."}
        return cursor+tail to ranges
    }
    fun durationMs(code:String):Long=timing(code).first*1000L/RATE
    fun render(code:String):MorsePcm {
        val (length,ranges)=timing(code);val rate=RATE
        if(length==0)return MorsePcm(ShortArray(0),rate,emptyList())
        val samples=ShortArray(length);val random=Random(70044)
        var noise=0.0
        samples.indices.forEach {i->
            // Very low receiver hiss opens the audio path before the first dot/dash.
            noise+=.68*(random.nextDouble(-1.0,1.0)-noise)
            val fade=minOf(1.0,i/(rate*.03),(samples.size-1-i)/(rate*.04)).coerceAtLeast(0.0)
            samples[i]=(noise*160*fade).toInt().toShort()
        }
        ranges.forEach {range->range.forEach {i->
            val position=i-range.first
            val envelope=minOf(1.0,position/96.0,(range.last-i)/96.0).coerceAtLeast(0.0)
            samples[i]=(samples[i]+sin(2*Math.PI*700*position/rate)*10000*envelope).toInt().coerceIn(-32768,32767).toShort()
        }}
        return MorsePcm(samples,rate,ranges)
    }
}

private val morseAudioScope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
private var morseAudioJob:Job?=null
internal object MorsePlayback {var code by mutableStateOf<String?>(null);internal set}
internal fun stopMorsePlayback(){val old=morseAudioJob;morseAudioJob=null;MorsePlayback.code=null;old?.cancel()}
fun stopMorse(){stopMorsePlayback();MorseSidetone.stopOthers()}
fun playMorse(code:String) {
    stopMorse()
    MorsePlayback.code=normalizeMorse(code)
    val owner=Any();FeedbackAudioGate.reserve(owner)
    val job=morseAudioScope.launch {
        var track:AudioTrack?=null
        try {
            val pcm=MorseSignalAudio.render(code)
            if(pcm.samples.isEmpty())return@launch
            ensureActive()
            val current=AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(pcm.rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(pcm.samples.size*2).setTransferMode(AudioTrack.MODE_STATIC).build()
            track=current
            check(current.write(pcm.samples,0,pcm.samples.size)==pcm.samples.size)
            ensureActive();current.play()
            delay(pcm.samples.size*1000L/pcm.rate+60)
        } catch(cancelled:CancellationException) {throw cancelled}
        catch(_:Exception) { /* A missing audio device must not terminate a lesson. */ }
        finally {track?.let {runCatching {it.stop()};it.release()}}
    }
    morseAudioJob=job
    job.invokeOnCompletion {if(morseAudioJob===job){morseAudioJob=null;MorsePlayback.code=null};FeedbackAudioGate.release(owner)}
}
