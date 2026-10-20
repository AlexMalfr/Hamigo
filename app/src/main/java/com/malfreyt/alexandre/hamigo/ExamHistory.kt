package com.malfreyt.alexandre.hamigo

import com.malfreyt.alexandre.hamigo.platform.CloudProgress
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.ln
import kotlin.random.Random

/** A text transcript owns its wording and answers; images remain references to the downloaded bank. */
data class ExamResponse(
    val index:Int,val questionId:String,val section:String,val topic:String,val prompt:String,
    val choices:List<String>,val correctAnswer:Int,val selectedAnswer:Int,val omitted:Boolean,
    val explanation:String,val source:String,val imageReference:String?=null
) {
    val correct:Boolean get()=!omitted&&selectedAnswer==correctAnswer
}
data class ExamAttempt(
    val id:String,val at:Long,val startedAt:Long,val elapsedMillis:Long,
    val regulationScore:Int,val techniqueScore:Int,val responses:List<ExamResponse>,
    val customized:Boolean=false,val bankGeneration:String?=null
) {
    val totalScore:Int get()=regulationScore+techniqueScore
    val passed:Boolean get()=regulationScore>=10&&techniqueScore>=10
}
data class ExamTopicMistakes(val section:String,val topic:String,val errors:Int,val attempts:Int) {
    val rate:Float get()=if(attempts==0)0f else errors.toFloat()/attempts
}

object ExamHistory {
    const val MAX_ATTEMPTS=100
    const val MAX_BYTES=4*1024*1024
    private val uuid=Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")
    private val attemptKeys=setOf("at","startedAt","elapsedMillis","regulationScore","techniqueScore","customized","bankGeneration","responses")
    private val responseKeys=setOf("index","questionId","section","topic","prompt","choices","correctAnswer","selectedAnswer","omitted","explanation","source","imageReference")

    fun fromSession(session:Session,at:Long=System.currentTimeMillis(),bankGeneration:String?=session.bankGeneration):ExamAttempt {
        require(session.exam&&session.done&&session.questions.size==40&&session.finalizedExamParts.containsAll(setOf(0,1))) {"Seul un examen complet peut rejoindre l’historique."}
        val responses=session.questions.mapIndexed {index,q->
            val response=session.responses[index]
            val omitted=response==null||response.omitted
            ExamResponse(index,q.id,q.section,q.topic,q.prompt,q.choices,q.answer,
                if(omitted)-1 else response!!.choiceIndex,omitted,q.explanation,q.source,q.image)
        }
        return ExamAttempt(session.examAttemptId,at,session.examStartedAt.coerceAtMost(at),session.elapsedMillis,
            session.regulationScore,session.techniqueScore,responses,session.title=="Examen personnalisé",bankGeneration)
            .also {validateAttempt(it)}
    }

    fun write(attempt:ExamAttempt):JSONObject {
        validateAttempt(attempt)
        return JSONObject().put("at",attempt.at).put("startedAt",attempt.startedAt).put("elapsedMillis",attempt.elapsedMillis)
            .put("regulationScore",attempt.regulationScore).put("techniqueScore",attempt.techniqueScore)
            .put("customized",attempt.customized).put("bankGeneration",attempt.bankGeneration)
            .put("responses",JSONArray(attempt.responses.map {r->JSONObject().put("index",r.index).put("questionId",r.questionId)
                .put("section",r.section).put("topic",r.topic).put("prompt",r.prompt).put("choices",JSONArray(r.choices))
                .put("correctAnswer",r.correctAnswer).put("selectedAnswer",r.selectedAnswer).put("omitted",r.omitted)
                .put("explanation",r.explanation).put("source",r.source).put("imageReference",r.imageReference)}))
    }

    fun read(records:JSONObject?):List<ExamAttempt> {
        if(records==null)return emptyList()
        validate(records)
        return records.keys().asSequence().map {id->decode(id,records.getJSONObject(id))}.sortedWith(compareByDescending<ExamAttempt> {it.at}.thenBy {it.id}).toList()
    }

    fun validate(records:JSONObject) {
        require(records.length()<=MAX_ATTEMPTS&&records.toString().toByteArray(Charsets.UTF_8).size<=MAX_BYTES) {"Historique d’examens trop volumineux."}
        records.keys().forEach {id->validateAttempt(decode(id,records.getJSONObject(id)))}
    }

    /** Same ID is one immutable attempt; canonical conflict selection is independent of merge order. */
    fun mergeJson(left:JSONObject?,right:JSONObject?):JSONObject {
        left?.let(::validate);right?.let(::validate)
        val byId=(read(left)+read(right)).groupBy {it.id}.mapValues {(_,values)->
            values.maxBy {CloudProgress.canonical(write(it))}
        }.values.sortedWith(compareByDescending<ExamAttempt> {it.at}.thenBy {it.id}).take(MAX_ATTEMPTS).toMutableList()
        fun result()=JSONObject().also {json->byId.sortedBy {it.id}.forEach {json.put(it.id,write(it))}}
        var json=result()
        while(json.toString().toByteArray(Charsets.UTF_8).size>MAX_BYTES&&byId.isNotEmpty()) {byId.removeAt(byId.lastIndex);json=result()}
        return json
    }

    private fun decode(id:String,json:JSONObject):ExamAttempt {
        require(json.keys().asSequence().all {it in attemptKeys}) {"Champ d’examen inconnu."}
        val responses=json.getJSONArray("responses")
        require(responses.length()==40) {"Examen incomplet."}
        val items=(0 until responses.length()).map {i->val r=responses.getJSONObject(i)
            require(r.keys().asSequence().all {it in responseKeys})
            val choices=r.getJSONArray("choices");require(choices.length() in 2..12)
            ExamResponse(integer(r,"index",0,39).toInt(),text(r,"questionId",256),text(r,"section",16),text(r,"topic",256,blank=true),
                text(r,"prompt",8000), (0 until choices.length()).map {n->(choices.get(n) as? String).also {require(it!=null&&it.length<=4000)}!!},
                integer(r,"correctAnswer",0,11).toInt(),integer(r,"selectedAnswer",-1,11).toInt(),boolean(r,"omitted"),
                text(r,"explanation",12000,blank=true),text(r,"source",2048,blank=true),optionalText(r,"imageReference",512))
        }
        return ExamAttempt(id,integer(json,"at",1,9_007_199_254_740_991),integer(json,"startedAt",1,9_007_199_254_740_991),
            integer(json,"elapsedMillis",0,45*60_000),integer(json,"regulationScore",0,20).toInt(),integer(json,"techniqueScore",0,20).toInt(),
            items,boolean(json,"customized"),optionalText(json,"bankGeneration",128))
    }
    private fun integer(json:JSONObject,key:String,min:Long,max:Long):Long {
        val value=json.opt(key)
        require(value is Number&&value.toDouble().isFinite()&&value.toDouble()%1.0==0.0&&value.toDouble() in min.toDouble()..max.toDouble()) {"Valeur $key invalide."}
        return value.toLong()
    }
    private fun text(json:JSONObject,key:String,max:Int,blank:Boolean=false):String {
        val value=json.opt(key);require(value is String&&value.length<=max&&(blank||value.isNotBlank())) {"Texte $key invalide."};return value
    }
    private fun optionalText(json:JSONObject,key:String,max:Int)=if(json.has(key))text(json,key,max) else null
    private fun boolean(json:JSONObject,key:String):Boolean {val value=json.opt(key);require(value is Boolean);return value}

    fun validateAttempt(a:ExamAttempt) {
        require(uuid.matches(a.id)&&a.at in 1..9_007_199_254_740_991&&a.startedAt in 1..a.at&&a.elapsedMillis in 0..45*60_000L)
        require(a.regulationScore in 0..20&&a.techniqueScore in 0..20&&a.responses.size==40)
        require(a.responses.map {it.index}==(0..39).toList()&&a.responses.map {it.questionId}.distinct().size==40)
        require(a.bankGeneration==null||a.bankGeneration.matches(Regex("[a-f0-9]{64}")))
        a.responses.forEach {r->
            require(r.questionId.isNotBlank()&&r.questionId.length<=256&&r.topic.length<=256&&r.prompt.isNotBlank()&&r.prompt.length<=8000)
            require(r.section==if(r.index<20)"regulation" else "technique")
            require(r.choices.size in 2..12&&r.choices.all {it.length<=4000}&&r.correctAnswer in r.choices.indices)
            require(if(r.omitted)r.selectedAnswer==-1 else r.selectedAnswer in r.choices.indices)
            require(r.explanation.length<=12000&&r.source.length<=2048)
            require(r.imageReference==null||r.imageReference.matches(Regex("exam-bank/[a-f0-9]{64}/[0-9]+\\.png")))
        }
        require(a.responses.take(20).count {it.correct}==a.regulationScore&&a.responses.drop(20).count {it.correct}==a.techniqueScore) {"Scores différents du transcript."}
    }

    fun topicMistakes(attempts:List<ExamAttempt>):List<ExamTopicMistakes> = attempts.flatMap {it.responses}
        .groupBy {it.section to it.topic.ifBlank {"Autres notions"}}.map {(key,rows)->ExamTopicMistakes(key.first,key.second,rows.count {!it.correct},rows.size)}
        .filter {it.errors>0}.sortedWith(compareByDescending<ExamTopicMistakes> {it.errors}.thenByDescending {it.rate}.thenBy {it.topic})

    /** One extra point describes the recent score slope; it is never a probability of passing. */
    fun trend(attempts:List<ExamAttempt>,score:(ExamAttempt)->Float):Float? {
        if(attempts.size<3)return null
        val recent=attempts.sortedWith(compareBy<ExamAttempt> {it.at}.thenBy {it.id}).takeLast(8)
        val meanX=(recent.size-1)/2.0;val meanY=recent.map {score(it).toDouble()}.average()
        val denominator=recent.indices.sumOf {(it-meanX)*(it-meanX)}
        val slope=recent.indices.sumOf {(it-meanX)*(score(recent[it])-meanY)}/denominator
        return (meanY+slope*(recent.size-meanX)).toFloat().coerceIn(0f,1f)
    }

    fun customizedQuestions(bank:List<Question>,attempts:List<ExamAttempt>,seed:Int=Random.nextInt()):List<Question> {
        require(attempts.size>=3) {"Trois examens complets sont nécessaires."}
        val random=Random(seed);val mistakes=topicMistakes(attempts).associateBy {it.section to it.topic}
        return listOf("regulation","technique").flatMap {section->
            val available=bank.filter {it.section==section}.distinctBy {it.id};require(available.size>=20)
            val groups=available.groupBy {it.topic}.mapValues {(_,items)->items.shuffled(random).toMutableList()}.toMutableMap()
            val chosen=mutableListOf<Question>();val perTopic=mutableMapOf<String,Int>()
            fun take(topic:String) {val q=groups.getValue(topic).removeAt(0);chosen+=q;perTopic[topic]=(perTopic[topic] ?: 0)+1}
            // Reserve room for breadth before emphasizing recurring mistakes.
            groups.keys.shuffled(random).take(8).forEach {take(it)}
            while(chosen.size<20) {
                val remaining=groups.filter {it.value.isNotEmpty()}
                val capped=remaining.filter {(perTopic[it.key] ?: 0)<if(groups.size>=4)6 else 20}.ifEmpty {remaining}
                val weights=capped.keys.map {topic->val m=mistakes[section to topic];topic to (1.0+(m?.let {4.0*it.rate*ln(2.0+it.errors)} ?: 0.0))}
                var ticket=random.nextDouble()*weights.sumOf {it.second};var selected=weights.last().first
                for((topic,weight) in weights){ticket-=weight;if(ticket<=0){selected=topic;break}}
                take(selected)
            }
            chosen.shuffled(random)
        }
    }

    /** Deterministic RAM-only examples for the diagnostics preview. */
    fun fixtures(bank:List<Question>,now:Long=System.currentTimeMillis()):List<ExamAttempt> {
        val questions=listOf("regulation","technique").flatMap {section->bank.filter {it.section==section}.distinctBy {it.id}.take(20)}
        if(questions.size!=40)return emptyList()
        return (0..4).map {n->
            val responses=questions.mapIndexed {i,q->val correct=i%20<if(i<20)8+n*2 else 7+n*3
                ExamResponse(i,q.id,q.section,q.topic,q.prompt,q.choices,q.answer,if(correct)q.answer else (q.answer+1)%q.choices.size,false,q.explanation,q.source,q.image)}
            val end=now-(4-n)*86_400_000L
            ExamAttempt(UUID.nameUUIDFromBytes("hamigo-exam-fixture-$n".toByteArray()).toString(),end,end-1_800_000,1_800_000,
                responses.take(20).count {it.correct},responses.drop(20).count {it.correct},responses)
        }
    }
}
