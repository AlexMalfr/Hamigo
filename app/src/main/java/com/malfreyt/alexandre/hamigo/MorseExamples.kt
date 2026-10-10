package com.malfreyt.alexandre.hamigo

/** Explicit label/code correspondences, distinct from separators inside a transmission. */
internal data class MorseExample(val label:String,val code:String,val range:IntRange,val labelRange:IntRange)

internal object MorseExamples {
    private const val signals="•●━.−–—-"
    private val assignment=Regex("(?<![\\p{L}\\p{N}])([A-ZÉ0-9]{1,12}|[.,?/@=+():\\\"'−-])\\s*[:=]\\s*([$signals]+(?:[ \\t]+[$signals]+|[ \\t]*/[ \\t]*[$signals]+)*)(?![\\p{L}\\p{N}$signals])")
    fun find(text:String):List<MorseExample> = assignment.findAll(text).filter {match->
        // A minus sign before a variable is an equation, not a one-dash Morse example.
        match.groupValues[2].any {it in "•●━"} || !Regex("^\\s*[\\p{L}\\p{N}]").containsMatchIn(text.substring(match.range.last+1))
    }.map {match->
        MorseExample(match.groupValues[1],normalizedMorse(match.groupValues[2]),match.range,match.groups[1]!!.range)
    }.toList()
    /** A standalone list can become rows without deleting any surrounding explanation. */
    fun table(text:String):List<MorseExample> {
        val entries=find(text)
        if(entries.isEmpty())return emptyList()
        var cursor=0
        for(entry in entries) {
            if(text.substring(cursor,entry.range.first).any { !it.isWhitespace() && it !in "·;" })return emptyList()
            cursor=entry.range.last+1
        }
        if(text.substring(cursor).any { !it.isWhitespace() && it !in "·;" })return emptyList()
        return entries
    }
}
