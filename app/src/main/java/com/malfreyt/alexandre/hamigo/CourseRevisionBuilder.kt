package com.malfreyt.alexandre.hamigo

import kotlin.math.ceil
import kotlin.random.Random

/** Stable course identities: revising a due item uses the ordinary answer/SRS path. */
object CourseRevisionBuilder {
    fun eligible(content:Content,completed:Set<String>,reviews:Map<String,Review>,
                 skillReviews:Map<String,SkillReview>,due:List<Question> = emptyList()):List<Question> =
        eligible(content.lessons,completed,reviews,skillReviews,content::skillsFor,
            due.filter(content::isReviewEligible)).filter(content::isReviewEligible)

    /** A known concept permits a fresh variant, never a question from an untouched lesson. */
    fun eligible(lessons:List<Lesson>,completed:Set<String>,reviews:Map<String,Review>,
                 skillReviews:Map<String,SkillReview>,skillsFor:(Question)->List<String>,
                 due:List<Question> = emptyList()):List<Question> {
        val learned=lessons.flatMap {lesson->
            if(lesson.id in completed)lesson.skillIds+lesson.questions.flatMap(skillsFor)
            else lesson.questions.filter {it.id in reviews}.flatMap(skillsFor)
        }.toSet()+skillReviews.keys
        val knownCharacters=(lessons.filter {lesson->lesson.id in completed||lesson.questions.any {it.id in reviews}}
            .flatMap {it.knownCharacters.uppercase().toList()}+
            (lessons.flatMap {it.questions}+due).filter {it.id in reviews}.flatMap {q->morseTarget(q).orEmpty().toList()}).toSet()
        return (lessons.flatMap {lesson->
            val done=lesson.id in completed
            val seen=lesson.questions.any {it.id in reviews}
            if(!done&&!seen)emptyList() else {
                val native=lesson.questions.filter {q->done||q.id in reviews||
                    (skillsFor(q).isNotEmpty()&&skillsFor(q).all {it in learned})}
                val extras=ExtendedPracticeGenerator.lessonExtraPool(lesson).filter {q->q.id in reviews||
                    (done&&skillsFor(q).isNotEmpty()&&skillsFor(q).all {it in learned})}
                native+extras
            }
        }+due.filter {it.id in reviews}).distinctBy {it.id}.filter {q->
            val target=morseTarget(q)
            skillsFor(q).all(SkillLearning::isSrsSkill)&&(target==null||(target.all {it.isWhitespace()||it in knownCharacters}&&
                (q.kind!="morseListen"||q.morseComprehension||q.choices.all {MorseReference.answerWithinKnownCharacters(it,knownCharacters)})))
        }
    }

    private fun morseTarget(question:Question):String? {
        if(question.kind !in setOf("morseListen","morseEncode")&&question.skillIds.none {it.startsWith("morse-")})return null
        val code=question.bands.firstOrNull()?.takeIf {value->value.any {it in ".-•●━"}} ?: return null
        val translated=MorseReference.decode(normalizedMorse(code))
        return translated.output.uppercase().takeIf {translated.unsupported.isEmpty()} ?: "\uFFFD"
    }

    fun eligible(lessons: List<Lesson>, completed: Set<String>, reviews: Map<String,Review>, due: List<Question> = emptyList()): List<Question> =
        (lessons.flatMap { lesson ->
            val studied=lesson.id in completed || lesson.questions.any { it.id in reviews }
            if(!studied) emptyList() else lesson.questions.filter { lesson.id in completed || it.id in reviews } +
                ExtendedPracticeGenerator.lessonExtraPool(lesson).filter { it.id in reviews }
        } + due).distinctBy { it.id }.filter {it.skillIds.all(SkillLearning::isSrsSkill)}

    fun create(pool: List<Question>, reviews: Map<String,Review>, count: Int=10,
               now: Long=System.currentTimeMillis(), seed: Int=Random.nextInt()): List<Question> {
        if(count<=0) return emptyList()
        val random=Random(seed)
        val unique=pool.distinctBy { it.id }.filter {it.skillIds.all(SkillLearning::isSrsSkill)}
        val size=minOf(count,unique.size)
        val due=unique.filter { reviews[it.id]?.due?.let { date->date<=now }==true }
            .shuffled(random).sortedBy { reviews.getValue(it.id).due }
        val others=unique.filter { reviews[it.id]?.due?.let { date->date<=now }!=true }.shuffled(random)
        val dueCount=when {
            others.isEmpty()->minOf(size,due.size)
            due.isEmpty()->0
            else->minOf(due.size,maxOf(ceil(size*.65).toInt(),size-others.size))
        }
        val selectedDue=due.take(dueCount).shuffled(random)
        val selectedOther=others.take(size-dueCount)
        if(selectedDue.isEmpty()) return LessonSessionBuilder.shuffleAnswers(selectedOther,random)
        if(selectedOther.isEmpty()) return LessonSessionBuilder.shuffleAnswers(selectedDue,random)
        // Spread the minority through the whole session, rather than a due-first block.
        val minorityIsDue=selectedDue.size<=selectedOther.size
        val minority=if(minorityIsDue)selectedDue else selectedOther
        val majority=if(minorityIsDue)selectedOther else selectedDue
        val positions=minority.indices.map { ((it+.5)*size/minority.size).toInt() }.toSet()
        var a=0;var b=0
        return LessonSessionBuilder.shuffleAnswers((0 until size).map { if(it in positions) minority[a++] else majority[b++] },random)
    }

    /** One representative per due skill is selected before applying the ordinary mixed session. */
    fun create(pool:List<Question>,reviews:Map<String,Review>,skillReviews:Map<String,SkillReview>,
               skillsFor:(Question)->List<String>,count:Int=10,now:Long=System.currentTimeMillis(),
               seed:Int=Random.nextInt()):List<Question> {
        if(count<=0)return emptyList()
        val unique=pool.distinctBy {it.id}.filter {skillsFor(it).all(SkillLearning::isSrsSkill)}
        val scheduled=scheduledReviews(unique,reviews,skillReviews,skillsFor,now,seed)
        val ordinary=create(unique,scheduled,count,now,seed)
        return diversifyDifficultFamilies(ordinary,unique,scheduled,skillReviews,skillsFor,now,seed)
    }

    /** Borrow a little from studied siblings; no due question or learning evidence is replaced. */
    private fun diversifyDifficultFamilies(selected:List<Question>,pool:List<Question>,scheduled:Map<String,Review>,
        skillReviews:Map<String,SkillReview>,skillsFor:(Question)->List<String>,now:Long,seed:Int):List<Question> {
        val budget=selected.size/5
        if(budget==0)return selected
        val families=SkillHierarchy.difficultFamilies(skillReviews)
        if(families.isEmpty())return selected
        val random=Random(seed xor 0x25A7)
        val result=selected.toMutableList()
        val slots=result.indices.filter {index->scheduled[result[index].id]?.due?.let {it<=now}!=true}.shuffled(random)
        var used=0
        for(family in families) {
            if(used>=minOf(budget,slots.size))break
            val practiced=family.skillIds.filter {SkillLearning.isSrsSkill(it)&&skillReviews[it]?.due?.let {due->due>now}==true}.toSet()
            val candidate=pool.filter {q->q.id !in result.map {it.id}&&
                scheduled[q.id]?.due?.let {it<=now}!=true&&skillsFor(q).any {it in practiced}&&
                skillsFor(q).none {skillReviews[it]?.due?.let {due->due<=now}==true}}
                // Prefer the sibling with actual difficulty; shuffling still renews equally weak choices.
                .shuffled(random).sortedByDescending {q->skillsFor(q).sumOf {skillReviews[it]?.let(SkillHierarchy::difficulty) ?: 0}}
                .firstOrNull() ?: continue
            result[slots[used++]]=LessonSessionBuilder.shuffleAnswers(listOf(candidate),random).single()
        }
        return result
    }

    fun due(pool:List<Question>,reviews:Map<String,Review>,skillReviews:Map<String,SkillReview>,
            skillsFor:(Question)->List<String>,now:Long=System.currentTimeMillis()):List<Question> {
        val unique=pool.distinctBy {it.id}.filter {skillsFor(it).all(SkillLearning::isSrsSkill)}
        val scheduled=scheduledReviews(unique,reviews,skillReviews,skillsFor,now,0)
        return unique.filter {q->scheduled[q.id]?.due?.let {it<=now}==true}.sortedBy {scheduled.getValue(it.id).due}
    }

    private fun scheduledReviews(unique:List<Question>,reviews:Map<String,Review>,skillReviews:Map<String,SkillReview>,
                                 skillsFor:(Question)->List<String>,now:Long,seed:Int):Map<String,Review> {
        val random=Random(seed)
        val mapping=unique.associate {it.id to skillsFor(it)}
        val scheduled=linkedMapOf<String,Review>()
        skillReviews.entries.filter {it.value.due<=now}.sortedBy {it.value.due}.forEach {(skill,review)->
            val alternatives=unique.filter {skill in mapping.getValue(it.id)}.shuffled(random)
                .sortedWith(compareBy<Question> {it.id in scheduled}.thenBy {it.id==review.lastQuestionId})
            alternatives.firstOrNull()?.let {q->
                val previous=scheduled[q.id]
                if(previous==null||review.due<previous.due)scheduled[q.id]=review.review
            }
        }
        unique.forEach {q->
            // UUID SRS remains the fallback for cards, Exam1 and concepts not mapped yet.
            if(mapping.getValue(q.id).none {it in skillReviews})reviews[q.id]?.let {scheduled[q.id]=it}
        }
        return scheduled
    }
}
