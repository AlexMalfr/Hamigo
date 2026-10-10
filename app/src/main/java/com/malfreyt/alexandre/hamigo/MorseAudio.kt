package com.malfreyt.alexandre.hamigo

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlin.math.sin
import kotlin.random.Random

internal data class MorsePcm(val samples:ShortArray,val rate:Int,val signalRanges:List<IntRange>)
internal object MorseSignalAudio {
    const val LEAD_MS=180
    const val DOT_MS=90
    fun render(code:String):MorsePcm {
        val words=normalizeMorse(code).take(240).split('/').map {it.trim().split(Regex("\\s+")).filter(String::isNotBlank)}.filter(List<String>::isNotEmpty)
        val rate=16000;val dot=rate*DOT_MS/1000
        val lead=rate*LEAD_MS/1000;val tail=rate*90/1000
        val ranges=mutableListOf<IntRange>();var cursor=lead
        words.forEachIndexed {wi,letters->letters.forEachIndexed {li,symbols->symbols.forEachIndexed {si,c->
            if(c=='.'||c=='-') {
                val length=dot*if(c=='.')1 else 3
                ranges+=cursor until cursor+length;cursor+=length
                cursor+=dot*when {si<symbols.lastIndex->1;li<letters.lastIndex->3;wi<words.lastIndex->7;else->1}
            }
        }}}
        if(ranges.isEmpty())return MorsePcm(ShortArray(0),rate,emptyList())
        val samples=ShortArray(cursor+tail);val random=Random(70044)
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
internal fun stopMorsePlayback(){morseAudioJob?.cancel();morseAudioJob=null}
fun stopMorse(){stopMorsePlayback();MorseSidetone.stopOthers()}
fun playMorse(code:String) {
    stopMorse()
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
    job.invokeOnCompletion {FeedbackAudioGate.release(owner)}
    morseAudioJob=job
}
