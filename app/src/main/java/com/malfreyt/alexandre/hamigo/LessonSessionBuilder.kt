package com.malfreyt.alexandre.hamigo

import kotlin.random.Random

/** Keep every taught concept; renew extra exercises and the order of answer positions. */
object LessonSessionBuilder {
    fun create(lesson:Lesson,seed:Int=Random.nextInt()):List<Question> {
        val random=Random(seed)
        return (lesson.questions + ExtendedPracticeGenerator.lessonExtras(lesson,seed.toLong())).map {q ->
            if(q.kind !in setOf("choice","truefalse","cloze","multiselect","morseListen","waveform") || q.choices.size<2) q
            else {
                val indices=q.choices.indices.shuffled(random)
                val bands=when(q.kind) {
                    "multiselect" -> q.bands.mapNotNull {it.toIntOrNull()}.map {indices.indexOf(it).toString()}
                    "waveform" -> indices.map {q.bands.getOrElse(it){"sine"}}
                    else ->q.bands
                }
                q.copy(choices=indices.map {q.choices[it]},answer=indices.indexOf(q.answer),bands=bands)
            }
        }.shuffled(random)
    }
}
