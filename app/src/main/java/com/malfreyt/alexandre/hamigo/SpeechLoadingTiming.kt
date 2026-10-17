package com.malfreyt.alexandre.hamigo

/** A fast preparation stays invisible; a displayed preparation has time to be understood. */
internal class SpeechLoadingTiming {
    val revealDelayMs=160L
    private var revealedAt:Long?=null
    fun revealed(now:Long){revealedAt=now}
    fun playbackAt(now:Long):Long=revealedAt?.let {maxOf(now,it+400L)} ?: now
}
