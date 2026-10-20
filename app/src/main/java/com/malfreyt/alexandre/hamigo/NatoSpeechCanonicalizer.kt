package com.malfreyt.alexandre.hamigo

import java.text.Normalizer
import java.util.Locale

/** Exact vocabulary aliases only: never compare a transcript with the expected answer. */
internal object NatoSpeechCanonicalizer {
    val vocabulary=QuestionVariants.nato+listOf("Alpha","Juliette","Whisky","zéro","un","deux","trois","quatre","cinq","six","sept","huit","neuf")
    private fun key(value:String)=Normalizer.normalize(value,Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT)
    private val aliases=mapOf(
        "Alfa" to listOf("alfa","alpha"), "Bravo" to listOf("bravo","bravos"),
        "Charlie" to listOf("charlie","charly"), "Delta" to listOf("delta"),
        "Echo" to listOf("echo","écho","eco"), "Foxtrot" to listOf("foxtrot"),
        "Golf" to listOf("golf","golfe"), "Hotel" to listOf("hotel","hôtel"),
        "India" to listOf("india"), "Juliett" to listOf("juliett","juliet","juliette","joliette"),
        "Kilo" to listOf("kilo"), "Lima" to listOf("lima"), "Mike" to listOf("mike"),
        "November" to listOf("november","novembre"), "Oscar" to listOf("oscar","oskar"),
        "Papa" to listOf("papa"), "Quebec" to listOf("quebec","québec"),
        "Romeo" to listOf("romeo","roméo"), "Sierra" to listOf("sierra"),
        "Tango" to listOf("tango"), "Uniform" to listOf("uniform","uniforme"),
        "Victor" to listOf("victor","viktor"), "Whiskey" to listOf("whiskey","whisky"),
        "X-ray" to listOf("xray","israël"), "Yankee" to listOf("yankee"), "Zulu" to listOf("zulu","zoulou")
    ).flatMap {(canonical,forms)->forms.map {key(it) to canonical}}.toMap()
    fun applies(question:Question):Boolean {
        if(question.kind!="spoken")return false
        val prompt=key(question.prompt)
        return question.skillIds.any {it.startsWith("nato-")} ||
            Regex("\"(?:mode|type)\"\\s*:\\s*\"(?:nato|nato-call)\"").containsMatchIn(question.template) ||
            "alphabet international" in prompt || "otan" in prompt || Regex("\\bepell\\p{L}*").containsMatchIn(prompt)
    }
    fun canonicalize(transcript:String):String {
        val words=Regex("[\\p{L}\\p{N}]+").findAll(transcript).map {it.value}.toList()
        val output=mutableListOf<String>()
        var index=0
        while(index<words.size) {
            val first=key(words[index])
            val second=words.getOrNull(index+1)?.let(::key)
            val third=words.getOrNull(index+2)?.let(::key)
            when {
                first=="sous"&&second=="l"&&third=="eau" -> {output+="Zulu";index+=3}
                first=="sous"&&second=="leau" -> {output+="Zulu";index+=2}
                first=="x"&&second=="ray" -> {output+="X-ray";index+=2}
                first=="fox"&&second=="trot" -> {output+="Foxtrot";index+=2}
                else -> {output+=aliases[first] ?: words[index];index++}
            }
        }
        return output.joinToString(" ")
    }
}
