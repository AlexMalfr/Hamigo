package com.malfreyt.alexandre.hamigo

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.*

/** 10 ms RMS windows of the *synthesized* audio. Memory depends on duration, not PCM size. */
internal class SpeechEnvelope private constructor(private val levels:FloatArray,private val intervalMs:Double) {
    fun at(positionMs:Int):Float=levels.getOrElse((positionMs/intervalMs).toInt().coerceAtLeast(0)){0f}
    /** Keep the regular mouth cycle; voice energy adds only a gentle, averaged 10% variation. */
    fun mouthAt(positionMs:Int):Float {
        val index=(positionMs/intervalMs).toInt()
        if(index !in levels.indices)return 0f
        val recent=(index-(40.0/intervalMs).toInt()).coerceAtLeast(0)..index
        // Bridge tiny gaps inside words, but react quickly to an actual pause.
        if(recent.none {levels[it]>0f})return 0f
        val start=(index-(140.0/intervalMs).toInt()).coerceAtLeast(0)
        var energy=0f;var count=0
        for(i in start..index)if(levels[i]>0f){energy+=levels[i];count++}
        return .9f+.1f*(energy/count.coerceAtLeast(1))
    }
    val size get()=levels.size
    companion object {
        const val WINDOW_MS=10
        fun fromWav(file:File):SpeechEnvelope?=runCatching {
            RandomAccessFile(file,"r").use {input->
                fun tag()=ByteArray(4).also(input::readFully).toString(Charsets.US_ASCII)
                fun u16()=input.readUnsignedByte() or (input.readUnsignedByte() shl 8)
                fun u32()=u16().toLong() or (u16().toLong() shl 16)
                require(tag()=="RIFF");u32();require(tag()=="WAVE")
                var encoding=0;var channels=0;var rate=0;var bits=0;var start=0L;var length=0L
                while(input.filePointer+8<=input.length()) {
                    val id=tag();val n=u32();val end=input.filePointer+n
                    require(end<=input.length())
                    when(id) {
                        "fmt "->{require(n>=16);encoding=u16();channels=u16();rate=u32().toInt();u32();u16();bits=u16()}
                        "data"->{start=input.filePointer;length=n}
                    }
                    input.seek(end+(n%2))
                }
                require(encoding in listOf(1,3)&&channels in 1..8&&rate in 8000..192000)
                require(bits in listOf(8,16,24,32)&&(encoding!=3||bits==32)&&start>0)
                val bytes=bits/8;val frameCount=length/(channels*bytes)
                require(frameCount>0&&frameCount<=rate.toLong()*3600)
                val window=(rate*WINDOW_MS/1000).coerceAtLeast(1)
                val rms=FloatArray(((frameCount+window-1)/window).toInt())
                val pcm=java.nio.ByteBuffer.allocate(window*channels*bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                input.seek(start)
                for(i in rms.indices) {
                    val frames=minOf(window.toLong(),frameCount-i.toLong()*window).toInt();var squares=0.0
                    input.readFully(pcm.array(),0,frames*channels*bytes);pcm.position(0)
                    repeat(frames*channels) {
                        val value=when(bits) {
                            8->((pcm.get().toInt() and 255)-128)/128.0
                            16->pcm.short/32768.0
                            24->{val n=(pcm.short.toInt() and 65535) or ((pcm.get().toInt() and 255) shl 16);(n shl 8 shr 8)/8388608.0}
                            else->{val n=pcm.int;if(encoding==3)Float.fromBits(n).toDouble() else n/2147483648.0}
                        }
                        squares+=if(value.isFinite())value*value else 0.0
                    }
                    rms[i]=sqrt(squares/(frames*channels)).toFloat()
                }
                // 22 050 Hz has no integer number of samples per 10 ms. Keep its real interval,
                // otherwise a long utterance drifts away from the MediaPlayer clock.
                fromRms(rms,window*1000.0/rate)
            }
        }.getOrNull()
        internal fun fromRms(rms:FloatArray,intervalMs:Double=WINDOW_MS.toDouble()):SpeechEnvelope {
            val peak=rms.maxOrNull() ?: 0f
            val floor=maxOf(.0015f,peak*.025f)
            return SpeechEnvelope(rms.map {value->
                if(value<=floor)0f else sqrt(((value-floor)/(peak*.45f-floor).coerceAtLeast(.001f)).coerceIn(0f,1f))
            }.toFloatArray(),intervalMs)
        }
    }
}
