package com.malfreyt.alexandre.hamigo

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tanh
import kotlin.math.pow
import kotlin.random.Random
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object ReceiverVoicePcm {
    /** TTS callbacks are PCM, not a WAV header. Convert stereo/float/8-bit engines to mono 16 kHz. */
    fun decode(bytes:ByteArray,rate:Int,channels:Int,bits:Int):ShortArray {
        require(rate in 8000..96000 && channels in 1..2 && bits in setOf(8,16,32))
        val width=bits/8;val frames=bytes.size/width/channels
        if(frames==0)return ShortArray(0)
        val input=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val mono=DoubleArray(frames) {
            var sum=0.0
            repeat(channels) {sum+=when(bits){8->((input.get().toInt() and 255)-128)/128.0;16->input.short/32768.0;else->input.float.toDouble().takeIf(Double::isFinite) ?: 0.0}}
            sum/channels
        }
        val peak=mono.maxOf {abs(it)}.coerceAtLeast(.01)
        return ShortArray((frames*16000L/rate).toInt()) {i->
            val at=i*rate.toDouble()/16000.0;val left=at.toInt().coerceIn(0,mono.lastIndex);val right=(left+1).coerceAtMost(mono.lastIndex)
            ((mono[left]+(mono[right]-mono[left])*(at-left))*7000/peak).toInt().coerceIn(-7000,7000).toShort()
        }
    }
}

/** Detuning filters and distorts a real voice, progressively buried in receiver noise. */
internal class ReceiverSignal {
    private val random=Random(73147)
    private var position=0;private var noise=0.0;private var clarity=0.0
    private var speechBand=0.0;private var phase=0.0;private var envelope=0.0
    fun restartEnvelope(){envelope=0.0}
    companion object {
        fun clarity(frequency:Double,target:Double):Double {
            val offset=abs(frequency-target)/.12
            return exp(-offset*offset).coerceIn(0.0,1.0)
        }
    }
    fun render(into:ShortArray,frequency:Double,target:Double,voice:ShortArray?) {
        val wanted=clarity(frequency,target)
        into.indices.forEach {i->
            clarity+=(wanted-clarity)/320 // 20 ms smoothing prevents clicks while tuning.
            noise+=.7*(random.nextDouble(-1.0,1.0)-noise)
            val speech=if(voice==null || voice.isEmpty())0 else {
                // A short quiet gap makes the long repeated message sound like a transmission.
                val sample=if(position<voice.size)voice[position].toInt() else 0
                position=(position+1)%(voice.size+5600)
                sample
            }
            val roughness=1-clarity
            speechBand+=(.85-.74*roughness)*(speech-speechBand)
            phase=(phase+2*PI*(18+185*roughness)/16000)%(2*PI)
            val flutter=1-.55*roughness+.55*roughness*sin(phase)
            val drive=1+3.5*roughness
            val saturated=tanh(speechBand*drive/7000)*7000/tanh(drive)
            val distorted=(speechBand*(1-.7*roughness)+saturated*.7*roughness)*flutter*clarity.pow(.7)
            val hiss=noise*(1700*(1-clarity)+90)
            envelope+=(1-envelope)/160
            into[i]=((distorted+hiss)*envelope).toInt().coerceIn(-10000,10000).toShort()
        }
    }
}
