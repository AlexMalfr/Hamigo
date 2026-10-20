package com.malfreyt.alexandre.hamigo

import org.json.JSONObject
import java.util.UUID

/** No answer text or personal name is retained in the skill journal. */
data class SkillAttempt(val id:String,val questionId:String,val at:Long,val correct:Boolean,val quality:Int)
data class SkillFailure(val attempt:SkillAttempt,val ease:Double)
data class SkillRecallPoint(val at:Long,val due:Long)
data class SkillRecall(val attempt:SkillAttempt,val due:Long,val interval:Double,val ease:Double,
                       val repetitions:Int,val failure:SkillFailure?=null,val previous:SkillRecallPoint?=null)

data class SkillReview(
    val due:Long=0L,val interval:Double=0.0,val ease:Double=2.5,
    val repetitions:Int=0,val lapses:Int=0,val attempts:Int=0,val successes:Int=0,
    val updatedAt:Long=0L,val lastQuestionId:String="",val lastAttemptId:String="",
    val seeded:Boolean=false,val seedDue:Long=0L,val seedLapses:Int=0,
    val history:Map<String,SkillAttempt> = emptyMap(),
    val failure:SkillFailure?=null,val recall:SkillRecall?=null,val last:SkillAttempt?=null
) {
    /** Two recalls on distinct due dates, rather than corrections in one sitting. */
    val mastered:Boolean get()=repetitions>=2 && interval>=6.0
    val review:Review get()=Review(due,interval,ease,repetitions,lapses)
}

/** Mergeable evidence, independent from the question/XP ledger which remains authoritative. */
object SkillLearning {
    /** App onboarding remains recorded, but never becomes a spaced radio review. */
    fun isSrsSkill(id:String)=id!="hamigo-tools"

    const val FORMAT_VERSION=2
    const val MAX_RECENT=32
    const val MAX_RECENT_TOTAL=1_024
    const val MAX_BYTES=4*1024*1024
    private const val MAX_SKILLS=2_000
    private const val MAX_TIMESTAMP=9_007_199_254_740_991L
    private val skillId=Regex("[a-zA-Z0-9][a-zA-Z0-9._:-]{0,127}")
    private val attemptId=Regex("[a-fA-F0-9-]{36}")

    fun pathCompletion(lessons:List<Lesson>,completed:Set<String>):PathCompletion {
        fun fraction(weight:(Lesson)->Int):Float {
            val total=lessons.sumOf {weight(it).coerceAtLeast(0).toLong()}
            if(total==0L)return 0f
            val done=lessons.filter {it.id in completed}.sumOf {weight(it).coerceAtLeast(0).toLong()}
            return (done.toDouble()/total).toFloat().coerceIn(0f,1f)
        }
        return PathCompletion(fraction {it.certificateWeight},fraction {it.explorationWeight})
    }

    /** All source lessons are required when a new lesson combines several old lessons. */
    fun migratedCompleted(completed:Set<String>,requiredLegacy:Map<String,Set<String>>):Set<String> {
        val result=completed.toMutableSet()
        do {
            val before=result.size
            requiredLegacy.forEach {(id,parents)->if(parents.isNotEmpty()&&parents.all {it in result})result+=id}
        } while(result.size!=before)
        return result
    }

    /** Existing deterministic generators keep their published IDs and meanings. */
    fun proceduralSkillIds(question:Question):List<String> = when {
        question.id.startsWith("proc-ohm-")->listOf("ohm-law")
        question.id.startsWith("proc-power-")->listOf("electric-power")
        question.id.startsWith("proc-wave-")->listOf("wavelength")
        question.id.startsWith("proc-band-")->listOf("band-allocation")
        question.id.startsWith("extra-")->when(question.id.substringAfterLast('-').toIntOrNull()) {
            0->listOf("series-resistors");1->listOf("parallel-resistors");2,5->listOf("ohm-law")
            3,12->listOf("measurement-units");4->listOf("binary-representation")
            6->listOf("morse-listening");7->listOf("morse-encoding");8->listOf("signal-shapes")
            9,18->listOf("wavelength");10->listOf("decibel-ratios");11->listOf("resistor-colours")
            13->listOf("metric-prefixes");14,15->listOf("reactance-calculation");16->listOf("dbm-reference")
            17->listOf("capacitors");19->listOf("resistivity");else->emptyList()
        }
        else->emptyList()
    }

    fun seed(due:Long,lapses:Int)=compact(true,due.coerceIn(0,MAX_TIMESTAMP),lapses.coerceAtLeast(0),
        0,0,lapses.coerceAtLeast(0),null,null,null,emptyMap())

    fun record(old:SkillReview?,questionId:String,correct:Boolean,quality:Int,now:Long,attempt:String):SkillReview {
        require(quality in 0..5 && now in 0..MAX_TIMESTAMP && questionId.length in 1..256)
        // Session first-pass guards own long-lived deduplication; this sample also catches immediate repeats.
        val id=UUID.nameUUIDFromBytes(attempt.toByteArray(Charsets.UTF_8)).toString()
        return append(old ?: SkillReview(),SkillAttempt(id,questionId,now,correct,if(correct)quality else minOf(quality,2)))
    }

    fun merge(a:SkillReview,b:SkillReview):SkillReview {
        val events=(a.history.keys+b.history.keys).associateWith {id->
            val left=a.history[id];val right=b.history[id]
            when {left==null->right!!;right==null->left;else->chooseAttempt(left,right)}
        }
        val failure=listOfNotNull(a.failure,b.failure).maxWithOrNull(failureOrder)
        val recall=listOfNotNull(a.recall,b.recall).filter {it.failure==failure}.maxWithOrNull(recallOrder)
        val last=listOfNotNull(a.last,b.last).maxWithOrNull(attemptOrder)
        val due=listOf(a,b).filter {it.seeded}.minOfOrNull {it.seedDue} ?: 0L
        // Counts are conservative lower bounds after concurrent branches, never a sum of overlapping journals.
        return compact(a.seeded||b.seeded,due,maxOf(a.seedLapses,b.seedLapses),
            maxOf(a.attempts,b.attempts),maxOf(a.successes,b.successes),maxOf(a.lapses,b.lapses),failure,recall,last,events)
    }

    private fun chooseAttempt(a:SkillAttempt,b:SkillAttempt):SkillAttempt =
        minOf(a,b,compareBy<SkillAttempt> {it.correct}.thenBy {it.quality}.thenBy {it.at}.thenBy {it.questionId})

    private val attemptOrder=compareBy<SkillAttempt> {it.at}.thenBy {it.id}.thenBy {it.correct}
        .thenBy {it.quality}.thenBy {it.questionId}
    private val failureOrder=compareBy<SkillFailure> {it.attempt.at}.thenBy {it.attempt.id}
        .thenByDescending {it.attempt.quality}.thenByDescending {it.ease}.thenBy {it.attempt.questionId}
    private val recallOrder=compareBy<SkillRecall> {it.attempt.at}.thenBy {it.attempt.id}
        .thenBy {it.due}.thenBy {it.interval}.thenBy {it.ease}.thenBy {it.repetitions}
        .thenBy {it.previous?.at ?: 0L}.thenBy {it.previous?.due ?: 0L}
        .thenBy {it.attempt.quality}.thenBy {it.attempt.questionId}

    private fun increment(value:Int)=(value.toLong()+1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    private fun due(at:Long,interval:Double)=(at+if(interval==0.0)600_000L else (interval*86_400_000).toLong())
        .coerceAtMost(MAX_TIMESTAMP)

    private fun append(old:SkillReview,incoming:SkillAttempt):SkillReview {
        val known=old.history[incoming.id] ?: old.last?.takeIf {it.id==incoming.id}
        val event=known?.let {chooseAttempt(it,incoming)} ?: incoming
        if(known==event)return old
        val fresh=known==null
        val events=old.history.toMutableMap().also {it[event.id]=event}
        var failure=old.failure
        var recall=old.recall
        var lapses=old.lapses
        val quality=if(event.correct)event.quality else minOf(2,event.quality)
        val early=event.correct&&(old.seeded||old.attempts>0)&&
            (event.at<old.due||old.recall?.let {event.at<=it.attempt.at}==true)
        if(quality<3&&!early) {
            val next=SpacedRepetition.next(old.review,quality,event.at)
            val candidate=SkillFailure(event,next.ease)
            failure=listOfNotNull(failure,candidate).maxWithOrNull(failureOrder)
            if(failure==candidate)recall=null
            lapses=increment(lapses)
        } else if(fresh&&!early) {
            val next=SpacedRepetition.next(old.review,quality,event.at)
            val interval=next.interval.coerceAtMost(3650.0)
            val previous=old.recall?.let {SkillRecallPoint(it.attempt.at,it.due)}
            recall=SkillRecall(event,due(event.at,interval),interval,next.ease,increment(old.repetitions),failure,previous)
        } else if(!fresh&&recall?.let {it.attempt.id==event.id&&event.quality<it.attempt.quality}==true) {
            // A contradictory downgrade is not a second recall of the same attempt.
            recall=null
        }
        return compact(old.seeded,old.seedDue,old.seedLapses,
            if(fresh)increment(old.attempts) else old.attempts,
            if(fresh&&event.correct)increment(old.successes) else old.successes,lapses,failure,recall,
            listOfNotNull(old.last,event).maxWithOrNull(attemptOrder),events)
    }

    private fun compact(seeded:Boolean,seedDue:Long,seedLapses:Int,attempts:Int,successes:Int,lapses:Int,
                        failure:SkillFailure?,recall:SkillRecall?,last:SkillAttempt?,events:Map<String,SkillAttempt>):SkillReview {
        val valid=recall?.takeIf {it.failure==failure}
        val base=when {
            valid!=null->Review(valid.due,valid.interval,valid.ease,valid.repetitions)
            failure!=null->Review(due(failure.attempt.at,0.0),ease=failure.ease)
            else->Review(due=seedDue)
        }
        // Stable ID ranking, rather than a mutable timestamp, makes the bounded sample a join-semilattice.
        val sample=events.toSortedMap().entries.take(MAX_RECENT).associate {it.key to it.value}
        return SkillReview(base.due,base.interval,base.ease,base.repetitions,maxOf(lapses,seedLapses),
            attempts,successes.coerceAtMost(attempts),last?.at ?: 0L,last?.questionId.orEmpty(),last?.id.orEmpty(),
            seeded,seedDue,seedLapses,sample,failure,valid,last)
    }

    fun read(records:JSONObject?):Map<String,SkillReview> = records?.keys()?.asSequence()?.associateWith {id->
        readReview(records.getJSONObject(id))
    }.orEmpty()

    fun readReview(record:JSONObject):SkillReview {
        require(record.optInt("format",1) in 1..FORMAT_VERSION)
        val events=readHistory(record.optJSONObject("history"))
        if(record.optInt("format",1)==1) {
            require(events.size<=MAX_RECENT) {"Ce format de compétences préparatoire n’a jamais été publié. Son journal de test dépasse la limite de conversion sans perte ; aucune donnée n’a été importée."}
            var converted=if(record.optBoolean("seeded"))seed(record.optLong("seedDue"),record.optInt("seedLapses")) else SkillReview()
            events.values.sortedWith(compareBy<SkillAttempt> {it.at}.thenBy {it.id}).forEach {converted=append(converted,it)}
            return converted
        }
        return compact(record.getBoolean("seeded"),record.getLong("seedDue"),record.getInt("seedLapses"),
            record.getInt("attempts"),record.getInt("successes"),record.getInt("lapses"),
            record.optJSONObject("failure")?.let(::readFailure),record.optJSONObject("recall")?.let(::readRecall),
            record.optJSONObject("last")?.let(::readAttempt),events)
    }

    private fun readHistory(history:JSONObject?):Map<String,SkillAttempt> = history?.keys()?.asSequence()?.associateWith {id->
        val event=history.getJSONObject(id)
        SkillAttempt(id,event.getString("questionId"),event.getLong("at"),event.getBoolean("correct"),event.getInt("quality"))
    }.orEmpty()
    private fun readAttempt(json:JSONObject)=SkillAttempt(json.getString("id"),json.getString("questionId"),json.getLong("at"),json.getBoolean("correct"),json.getInt("quality"))
    private fun writeAttempt(event:SkillAttempt)=JSONObject().put("id",event.id).put("questionId",event.questionId)
        .put("at",event.at).put("correct",event.correct).put("quality",event.quality)
    private fun readFailure(json:JSONObject)=SkillFailure(readAttempt(json.getJSONObject("attempt")),json.getDouble("ease"))
    private fun writeFailure(failure:SkillFailure)=JSONObject().put("attempt",writeAttempt(failure.attempt)).put("ease",failure.ease)
    private fun readRecall(json:JSONObject)=SkillRecall(readAttempt(json.getJSONObject("attempt")),json.getLong("due"),json.getDouble("interval"),
        json.getDouble("ease"),json.getInt("repetitions"),json.optJSONObject("failure")?.let(::readFailure),
        json.optJSONObject("previous")?.let {SkillRecallPoint(it.getLong("at"),it.getLong("due"))})
    private fun writeRecall(recall:SkillRecall)=JSONObject().put("attempt",writeAttempt(recall.attempt)).put("due",recall.due)
        .put("interval",recall.interval).put("ease",recall.ease).put("repetitions",recall.repetitions)
        .put("failure",recall.failure?.let(::writeFailure))
        .put("previous",recall.previous?.let {JSONObject().put("at",it.at).put("due",it.due)})

    fun write(review:SkillReview):JSONObject = JSONObject().put("format",FORMAT_VERSION).put("due",review.due).put("interval",review.interval)
        .put("ease",review.ease).put("repetitions",review.repetitions).put("lapses",review.lapses)
        .put("attempts",review.attempts).put("successes",review.successes).put("updatedAt",review.updatedAt)
        .put("lastQuestionId",review.lastQuestionId).put("lastAttemptId",review.lastAttemptId)
        .put("seeded",review.seeded).put("seedDue",review.seedDue).put("seedLapses",review.seedLapses)
        .put("failure",review.failure?.let(::writeFailure)).put("recall",review.recall?.let(::writeRecall))
        .put("last",review.last?.let(::writeAttempt))
        .put("history",JSONObject().also {history->review.history.toSortedMap().forEach {(id,event)->
            history.put(id,JSONObject().put("questionId",event.questionId).put("at",event.at)
                .put("correct",event.correct).put("quality",event.quality))
        }})

    fun mergeJson(left:JSONObject?,right:JSONObject?):JSONObject {
        val a=read(left);val b=read(right)
        return JSONObject().also {result->(a.keys+b.keys).sorted().forEach {id->
            val x=a[id];val y=b[id]
            result.put(id,write(when {x==null->y!!;y==null->x;else->merge(x,y)}))
        };trimHistory(result)}
    }

    /** Bounds inspection data across all skills; counters and SRS evidence are never truncated. */
    fun trimHistory(records:JSONObject) {
        val entries=records.keys().asSequence().flatMap {skill->
            records.getJSONObject(skill).getJSONObject("history").keys().asSequence().map {event->event to skill}
        }.sortedWith(compareBy<Pair<String,String>> {it.first}.thenBy {it.second}).toList()
        entries.drop(MAX_RECENT_TOTAL).forEach {(event,skill)->records.getJSONObject(skill).getJSONObject("history").remove(event)}
    }

    /** Persist compact data once on local load/import, so display getters never replay a preparatory v1 again. */
    fun normalize(state:JSONObject):Boolean {
        val records=state.optJSONObject("skillReviews") ?: return false
        if(state.optInt("skillReviewsVersion")==FORMAT_VERSION&&records.keys().asSequence().all {records.getJSONObject(it).optInt("format")==FORMAT_VERSION})return false
        val compacted=JSONObject().also {result->records.keys().forEach {id->result.put(id,write(readReview(records.getJSONObject(id))))}}
        trimHistory(compacted)
        state.put("skillReviews",compacted).put("skillReviewsVersion",FORMAT_VERSION)
        return true
    }

    /** Legacy question reviews establish exposure and urgency, never invented spaced successes. */
    fun migrate(state:JSONObject,content:Content):Boolean {
        var changed=normalize(state)
        val records=state.optJSONObject("skillReviews") ?: JSONObject().also {state.put("skillReviews",it);changed=true}
        val seeds=linkedMapOf<String,SkillReview>()
        val old=state.optJSONObject("reviews") ?: JSONObject()
        old.keys().forEach {id->
            val question=content.allQuestions[id] ?: return@forEach
            val review=old.getJSONObject(id)
            content.skillsFor(question).forEach {skill->
                if(!records.has(skill)) {
                    val candidate=seed(review.optLong("due"),review.optInt("lapses"))
                    seeds[skill]=seeds[skill]?.let {merge(it,candidate)} ?: candidate
                }
            }
        }
        seeds.forEach {(id,review)->records.put(id,write(review));changed=true}
        if(state.optInt("skillReviewsVersion")!=FORMAT_VERSION) {state.put("skillReviewsVersion",FORMAT_VERSION);changed=true}
        trimHistory(records)
        return changed
    }

    fun validate(records:JSONObject) {
        require(records.length()<=MAX_SKILLS) {"Trop de compétences dans la sauvegarde."}
        var total=0
        records.keys().forEach {id->
            require(skillId.matches(id)) {"Identifiant de compétence invalide."}
            val record=records.getJSONObject(id)
            require(record.keys().asSequence().all {it in setOf("format","due","interval","ease","repetitions","lapses",
                "attempts","successes","updatedAt","lastQuestionId","lastAttemptId","seeded","seedDue","seedLapses",
                "history","failure","recall","last")}) {"Champ de compétence inconnu."}
            val format=if(record.has("format"))whole(record,"format",FORMAT_VERSION.toLong()).toInt() else 1
            require(format in 1..FORMAT_VERSION)
            require(record.opt("seeded") is Boolean)
            whole(record,"seedDue",MAX_TIMESTAMP);whole(record,"seedLapses",Int.MAX_VALUE.toLong())
            whole(record,"due",MAX_TIMESTAMP);whole(record,"updatedAt",MAX_TIMESTAMP)
            for(key in listOf("repetitions","lapses","attempts","successes"))whole(record,key,Int.MAX_VALUE.toLong())
            for(key in listOf("ease","interval")) {
                val value=record.get(key)
                require(value is Number&&value.toDouble().isFinite()&&value.toDouble()>=(if(key=="ease")1.3 else 0.0))
            }
            require(record.opt("lastQuestionId") is String&&record.getString("lastQuestionId").length<=256)
            require(record.opt("lastAttemptId") is String&&record.getString("lastAttemptId").let {it.isEmpty()||attemptId.matches(it)})
            val events=record.getJSONObject("history")
            require(events.length()<=MAX_RECENT) {"Journal notionnel préparatoire trop long : conversion sans perte impossible ; import refusé."}
            total+=events.length()
            require(total<=MAX_RECENT_TOTAL) {"L’échantillon des compétences est trop volumineux."}
            if(format==FORMAT_VERSION) {
                listOf("failure","recall","last").forEach {key->require(!record.has(key)||record.opt(key) is JSONObject)}
                val failure=record.optJSONObject("failure")?.let {validateFailure(it);readFailure(it)}
                val recall=record.optJSONObject("recall")?.let {validateRecall(it);readRecall(it)}
                record.optJSONObject("last")?.let(::validateAttempt)
                require(recall==null||recall.failure==failure) {"Rappel construit sans connaître le dernier échec."}
                require((record.getInt("attempts")==0)==!record.has("last"))
                require(record.getInt("successes")<=record.getInt("attempts"))
            }
            events.keys().forEach {key->
                require(attemptId.matches(key)) {"Identifiant de tentative invalide."}
                val event=events.getJSONObject(key)
                require(event.keys().asSequence().all {it in setOf("questionId","at","correct","quality")})
                require(event.opt("questionId") is String && event.getString("questionId").length in 1..256)
                require(event.opt("correct") is Boolean)
                whole(event,"at",MAX_TIMESTAMP);whole(event,"quality",5)
            }
        }
        require(records.toString().toByteArray(Charsets.UTF_8).size<=MAX_BYTES) {"État des compétences trop volumineux."}
    }

    private fun validateAttempt(json:JSONObject) {
        require(json.keys().asSequence().all {it in setOf("id","questionId","at","correct","quality")})
        require(json.opt("id") is String&&attemptId.matches(json.getString("id")))
        require(json.opt("questionId") is String&&json.getString("questionId").length in 1..256)
        require(json.opt("correct") is Boolean)
        whole(json,"at",MAX_TIMESTAMP);whole(json,"quality",5)
    }
    private fun validateFailure(json:JSONObject) {
        require(json.keys().asSequence().all {it in setOf("attempt","ease")})
        val event=json.getJSONObject("attempt");validateAttempt(event)
        require(!event.getBoolean("correct")||event.getInt("quality")<3)
        finite(json,"ease",1.3)
    }
    private fun validateRecall(json:JSONObject) {
        require(json.keys().asSequence().all {it in setOf("attempt","due","interval","ease","repetitions","failure","previous")})
        val event=json.getJSONObject("attempt");validateAttempt(event)
        require(event.getBoolean("correct")&&event.getInt("quality")>=3)
        val at=event.getLong("at")
        val interval=finite(json,"interval",1.0);require(interval<=3650.0)
        finite(json,"ease",1.3)
        val repetitions=whole(json,"repetitions",Int.MAX_VALUE.toLong());require(repetitions>=1)
        require(whole(json,"due",MAX_TIMESTAMP)==due(at,interval))
        listOf("failure","previous").forEach {key->require(!json.has(key)||json.opt(key) is JSONObject)}
        val failure=json.optJSONObject("failure")
        failure?.let {validateFailure(it);require(due(it.getJSONObject("attempt").getLong("at"),0.0)<=at)}
        val previous=json.optJSONObject("previous")
        require((repetitions==1L)==(previous==null))
        previous?.let {
            require(it.keys().asSequence().all {key->key in setOf("at","due")})
            val previousAt=whole(it,"at",MAX_TIMESTAMP);val previousDue=whole(it,"due",MAX_TIMESTAMP)
            require(previousAt<at&&previousDue>=due(previousAt,1.0)&&previousDue<=at)
            failure?.let {f->require(previousAt>=due(f.getJSONObject("attempt").getLong("at"),0.0))}
        }
    }
    private fun finite(json:JSONObject,key:String,min:Double):Double {
        val value=json.get(key)
        require(value is Number&&value.toDouble().isFinite()&&value.toDouble()>=min)
        return value.toDouble()
    }

    private fun whole(obj:JSONObject,key:String,max:Long):Long {
        val number=obj.get(key)
        require(number is Number&&number.toDouble().isFinite()&&number.toDouble()%1.0==0.0&&number.toDouble() in 0.0..max.toDouble()) {
            "Valeur de compétence $key invalide."
        }
        return number.toLong()
    }
}
