package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

internal object LessonNarration {
    fun segments(lesson:Lesson,maxLength:Int):List<String> {
        require(maxLength>=2)
        return segments(lesson.narration.ifEmpty {listOf(lesson.title,lesson.summary)+lesson.body+listOf(lesson.formula)},maxLength)
    }
    fun segments(paragraphs:List<String>,maxLength:Int):List<String> {
        require(maxLength>=2)
        return paragraphs.filter {it.isNotBlank()}.flatMap {paragraph ->
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

/** Narration owned by one visible screen, including cancellable and stable preparation. */
internal class LessonSpeech(context:Context,eager:Boolean=true) {
    private val playback=PreparedSpeech(context,Locale.FRANCE,"La voix française n’est pas disponible sur cet appareil.",eager)
    val reading get()=playback.active
    val speaking get()=playback.speaking
    val preparing get()=playback.preparing
    val mouthActivity get()=playback.mouthActivity
    fun toggle(lesson:Lesson) {
        if(reading)stop() else playback.start(LessonNarration.segments(lesson,TextToSpeech.getMaxSpeechInputLength()))
    }
    fun toggleText(text:String){if(reading)stop() else playback.start(LessonNarration.segments(listOf(text),TextToSpeech.getMaxSpeechInputLength()))}
    fun stop()=playback.stop()
    fun close()=playback.close()
}
