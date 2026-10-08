package com.malfreyt.alexandre.hamigo

import kotlin.math.ceil
import kotlin.random.Random

/** Stable course identities: revising a due item uses the ordinary answer/SRS path. */
object CourseRevisionBuilder {
    fun eligible(lessons: List<Lesson>, completed: Set<String>, reviews: Map<String,Review>): List<Question> =
        lessons.flatMap { lesson ->
            val studied=lesson.id in completed || lesson.questions.any { it.id in reviews }
            if(!studied) emptyList() else lesson.questions.filter { lesson.id in completed || it.id in reviews } +
                ExtendedPracticeGenerator.lessonExtraPool(lesson).filter { it.id in reviews }
        }.distinctBy { it.id }

    fun create(pool: List<Question>, reviews: Map<String,Review>, count: Int=10,
               now: Long=System.currentTimeMillis(), seed: Int=Random.nextInt()): List<Question> {
        if(count<=0) return emptyList()
        val random=Random(seed)
        val unique=pool.distinctBy { it.id }
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
}
