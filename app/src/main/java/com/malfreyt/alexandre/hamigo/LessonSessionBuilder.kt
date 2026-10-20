package com.malfreyt.alexandre.hamigo

import kotlin.random.Random

/** Keep every taught concept; renew extra exercises and the order of answer positions. */
object LessonSessionBuilder {
    fun create(lesson:Lesson,seed:Int=Random.nextInt()):List<Question> {
        val random=Random(seed)
        val taught=(lesson.skillIds+lesson.questions.flatMap {it.skillIds}).toSet()
        val characters=lesson.knownCharacters.uppercase().toSet()
        // Keywords only find candidates. A prerequisite label alone is not evidence that it was learned.
        val extras=ExtendedPracticeGenerator.lessonExtraPool(lesson).filter {question->
            val skills=question.skillIds.ifEmpty {SkillLearning.proceduralSkillIds(question)}
            skills.isNotEmpty()&&skills.all {it in taught}&&morseWithin(question,characters)
        }.shuffled(Random(seed.toLong())).take(2)
        return shuffleAnswers(lesson.questions+extras,random).shuffled(random)
    }
    private fun morseWithin(question:Question,characters:Set<Char>):Boolean {
        if(question.kind !in setOf("morseListen","morseEncode"))return true
        val code=question.bands.firstOrNull() ?: return false
        val decoded=MorseReference.decode(normalizedMorse(code))
        fun known(text:String)=text.uppercase().all {it.isWhitespace()||it in characters}
        return decoded.unsupported.isEmpty()&&known(decoded.output)&&
            (question.kind!="morseListen"||question.morseComprehension||question.choices.all {MorseReference.answerWithinKnownCharacters(it,characters)})
    }
    fun shuffleAnswers(questions:List<Question>,random:Random):List<Question> = questions.map {q ->
            if(q.kind !in setOf("choice","truefalse","cloze","multiselect","resistor","morseListen","waveform") || q.choices.size<2) q
            else {
                val indices=q.choices.indices.shuffled(random)
                val bands=when(q.kind) {
                    "multiselect" -> q.bands.mapNotNull {it.toIntOrNull()}.map {indices.indexOf(it).toString()}
                    "waveform" -> indices.map {q.bands.getOrElse(it){"sine"}}
                    else ->q.bands
                }
                val choices=if(q.kind=="waveform")indices.indices.map {"Tracé ${'A'+it}"} else indices.map {q.choices[it]}
                q.copy(choices=choices,answer=indices.indexOf(q.answer),bands=bands)
            }
        }
}
