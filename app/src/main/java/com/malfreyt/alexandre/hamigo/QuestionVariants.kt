package com.malfreyt.alexandre.hamigo

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import kotlin.random.Random

/** An answer is recalled as words, without requiring a particular accent or punctuation. */
object WrittenAnswers {
    private val measurement=Regex("(?<![\\p{L}\\p{N}])([+−-]?\\d+(?:[.,]\\d+)?)\\s*([fpnumkMGTµμ]?)(Hz|V|A|W|Ω|ohm|F|H|s|m|J|C)(?![\\p{L}\\p{N}])")
    fun containsUnitSymbol(text:String)=measurement.containsMatchIn(text)
    fun normalize(text:String):String = Normalizer.normalize(measurement.replace(text) {match->
        // SI prefixes carry meaning: a milliwatt and a megawatt must never compare equal.
        val number=match.groupValues[1].replace('−','-').replace(',','.').toBigDecimal().stripTrailingZeros().toPlainString()
        val symbol=(match.groupValues[2]+match.groupValues[3]).replace('µ','u').replace('μ','u')
        (if(number.startsWith('-'))"moins "+number.drop(1) else number)+" mesure "+symbol.map {it.code}.joinToString(" ")
    },Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"),"").lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+")," ").trim().replace(Regex("\\s+")," ")
    fun correct(input:String,accepted:List<String>) = normalize(input).let {answer ->
        answer.isNotEmpty() && accepted.any {normalize(it)==answer}
    }
}

/** Presentation is deliberately separate from stable question identity and the XP ledger. */
internal object QuestionVariants {
    val nato = "Alfa Bravo Charlie Delta Echo Foxtrot Golf Hotel India Juliett Kilo Lima Mike November Oscar Papa Quebec Romeo Sierra Tango Uniform Victor Whiskey X-ray Yankee Zulu".split(' ')
    private val digitsFr="zéro un deux trois quatre cinq six sept huit neuf".split(' ')
    private val digitsEn="zero one two three four five six seven eight nine".split(' ')
    private val names=listOf("Emma","Léo","Mia","Tom","Tim","Mimi","Sam","Nina","Anne","Luc","Émile","Alex","Lina","Marc")
    private fun letters(value:String) = Normalizer.normalize(value,Normalizer.Form.NFD).replace(Regex("\\p{M}+"),"").uppercase(Locale.ROOT)
    fun spell(value:String,digits:List<String>?=null) = letters(value).map {c ->
        when {c in 'A'..'Z'->nato[c-'A'];c.isDigit()&&digits!=null->digits[c-'0'];else->c.toString()}
    }.joinToString(" ")

    /** Common speech-service spellings, without accepting a different letter or word order. */
    fun natoAnswers(value:String):List<String> = listOf(null,digitsFr,digitsEn).flatMap {digits->
        spell(value,digits).split(' ').fold(listOf("")) {prefixes,word->
            val spellings=when(word) {
                "Alfa"->listOf(word,"Alpha")
                "Juliett"->listOf(word,"Juliet","Juliette")
                "Whiskey"->listOf(word,"Whisky")
                "X-ray"->listOf(word,"Xray","X ray")
                else->listOf(word)
            }
            prefixes.flatMap {prefix->spellings.map {"$prefix $it".trim()}}
        }
    }.distinct()

    fun create(question:Question,name:String,friendNames:List<String>,knownCharacters:String="",seed:Int=Random.nextInt()):Question {
        val random=Random(seed)
        var q=question.copy(presentationSeed=seed)
        val template=question.template.takeIf {it.isNotBlank()}?.let {runCatching {JSONObject(it)}.getOrNull()}
        if(template!=null) {
            val allowed=letters(template.optString("characters").ifBlank {knownCharacters}).filter {it.isLetterOrDigit()}.toSet()
            val fallback=template.optString("fallback","SAM")
            fun safeName(candidate:String) = letters(candidate.substringBefore(' ').trim()).let {word ->
                word.length in 2..10 && word.all {it in 'A'..'Z'&&(allowed.isEmpty()||it in allowed)}
            }
            val person=(listOf(name)+friendNames).map {it.substringBefore(' ').trim()}.filter(::safeName).distinct().shuffled(random).firstOrNull()
                ?: names.filter(::safeName).randomOrNull(random) ?: fallback.takeIf(::safeName)
                ?: (allowed.filter {it in 'A'..'Z'}.sorted().firstOrNull()?.toString()?.repeat(2) ?: "Sam")
            val chars=(if(allowed.isEmpty())('A'..'Z').toSet() else allowed).filter {it in 'A'..'Z'}
            val numbers=allowed.filter {it.isDigit()}
            val call=if('F' in chars&&numbers.isNotEmpty()) "F${numbers.random(random)}"+(1..3).map {chars.random(random)}.joinToString("")
                else if(chars.isNotEmpty())(1..template.optInt("length",3).coerceIn(1,8)).map {chars.random(random)}.joinToString("")
                else if(numbers.isNotEmpty())(1..template.optInt("length",2).coerceIn(1,8)).map {numbers.random(random)}.joinToString("") else fallback
            val type=template.optString("type")
            val value=if(type.endsWith("name"))letters(person) else call
            fun fill(text:String)=text.replace("{{name}}",person).replace("{{call}}",call).replace("{{value}}",value)
            q=q.copy(prompt=fill(template.optString("sourceText").ifBlank {q.prompt}),
                explanation=fill(template.optString("explanationText").ifBlank {q.explanation}))
            when(type) {
                "nato-call" -> q=q.copy(acceptedAnswers=natoAnswers(call),explanation=q.explanation+"\n${spell(call,digitsFr)}.")
                "morse-name","morse-call" -> {
                    val encoded=MorseReference.encode(value)
                    if(encoded.unsupported.isEmpty()) {
                        val mode=template.optString("mode",if(q.kind=="morseEncode")"encode" else "listen")
                        q=q.copy(bands=listOf(encoded.output),acceptedAnswers=when(mode) {
                            "nato"->natoAnswers(value)
                            else->listOf(value)
                        })
                        if(q.kind=="morseListen") {
                            val wrong=linkedSetOf<String>()
                            val frenchCall=value.length==5&&value[0]=='F'&&value[1].isDigit()
                            repeat(80) {wrong+=value.mapIndexed {index,c->
                                if(frenchCall&&index<2)c else if(c.isDigit())numbers.randomOrNull(random) ?: c else chars.randomOrNull(random) ?: c
                            }.joinToString("")}
                            val choices=(listOf(value)+wrong.filter {it!=value}.take(3)).shuffled(random)
                            q=q.copy(choices=choices,answer=choices.indexOf(value))
                        }
                        q=q.copy(explanation=q.explanation+"\nLe groupe transmis est $value.")
                    }
                }
            }
        }
        if(q.distractors.isNotEmpty() && q.kind in setOf("choice","cloze","resistor","morseListen")) {
            val correct=q.choices.getOrNull(q.answer) ?: return q
            val wrong=(q.choices.filterIndexed {index,_->index!=q.answer}+q.distractors)
                .filter {WrittenAnswers.normalize(it)!=WrittenAnswers.normalize(correct)}.distinctBy(WrittenAnswers::normalize).shuffled(random)
            val choices=(wrong.take((q.choices.size.coerceAtLeast(4)-1).coerceAtMost(5))+correct).shuffled(random)
            q=q.copy(choices=choices,answer=choices.indexOf(correct))
        }
        return q
    }
}

/** No row starts already paired, including two-item exercises. Seeded for reproducible diagnostics. */
internal fun questionDerangement(size:Int,seed:Int):List<Int> {
    if(size<2)return (0 until size).toList()
    val random=Random(seed)
    repeat(40) {
        val order=(0 until size).shuffled(random)
        if(order.indices.none {order[it]==it})return order
    }
    val shift=random.nextInt(1,size)
    return (0 until size).map {(it+shift)%size}
}

internal fun rightQuestionOrder(q:Question)=questionDerangement(q.pairs.size,if(q.presentationSeed==0)q.id.hashCode() else q.presentationSeed)

/** Definition and effective session data are both useful when a template or pool is involved. */
internal fun questionDebugJson(q:Question):String {
    val effective=JSONObject().put("id",q.id).put("kind",q.kind).put("prompt",q.prompt)
        .put("choices",JSONArray(q.choices)).put("answer",q.answer).put("explanation",q.explanation)
        .put("topic",q.topic).put("section",q.section).put("skillIds",JSONArray(q.skillIds))
        .put("acceptedAnswers",JSONArray(q.acceptedAnswers)).put("bands",JSONArray(q.bands))
        .put("morseComprehension",q.morseComprehension)
        .put("studyToolsHint",q.studyToolsHint)
        .put("pairs",JSONArray(q.pairs.map {JSONObject().put("left",it.left).put("right",it.right)}))
        .put("rows",JSONArray(q.rows.map {row->JSONObject().put("id",row.id).put("prompt",row.prompt)
            .put("choices",JSONArray(row.choices)).put("answer",row.answer).put("acceptedAnswers",JSONArray(row.acceptedAnswers))}))
        .put("value",q.value).put("unit",q.unit).put("tolerance",q.tolerance).put("visual",q.visual)
        .put("presentationSeed",q.presentationSeed).put("source",q.source)
    val root=JSONObject().put("session",effective)
    q.rawJson.takeIf {it.isNotBlank()}?.let {raw->runCatching {root.put("definition",JSONObject(raw))}}
    return root.toString(2)
}
