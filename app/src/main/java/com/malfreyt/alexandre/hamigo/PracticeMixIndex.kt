package com.malfreyt.alexandre.hamigo

import java.text.Normalizer
import kotlin.random.Random

/** Built with Content on the loader thread. Selecting a theme never scans the question bank. */
class PracticeMixIndex(exam: List<Question>, procedural: List<Question>) {
    val groups = exam.groupBy { it.section }.mapValues { (_, questions) -> questions.groupBy { it.topic }.toSortedMap() }
    private val fixed = exam.groupBy { "${it.section}|${it.topic}" }
    val keys = fixed.keys.toSet()
    private val techniqueKeys = keys.filterTo(mutableSetOf()) { it.startsWith("technique|") }
    private val generated = procedural.groupBy { it.topic }
    private val matchingKeys = generated.keys.associateWith { topic ->
        keys.filterTo(mutableSetOf()) { it.startsWith("technique|") && generatedMatchesTopic(topic, it.substringAfter('|')) }
    }
    private fun generatedTopics(selected: Set<String>) = if (techniqueKeys.isNotEmpty() && selected.containsAll(techniqueKeys)) generated.keys else
        matchingKeys.filterValues { candidates -> candidates.any { it in selected } }.keys
    fun available(selected: Set<String>) = selected.sumOf { fixed[it]?.size ?: 0 } + generatedTopics(selected).sumOf { generated[it]?.size ?: 0 }
    fun questions(selected: Set<String>, count: Int, random: Random = Random.Default): List<Question> {
        require(count in 1..available(selected))
        val pool = selected.flatMap { fixed[it].orEmpty() }
        val variants = generatedTopics(selected).flatMap { generated[it].orEmpty() }
        val generatedCount = if (variants.isEmpty()) 0 else minOf((count * .3).toInt().coerceAtLeast(1), variants.size)
        val fixedCount = minOf(count - generatedCount, pool.size)
        return (pool.shuffled(random).take(fixedCount) + variants.shuffled(random).take(count - fixedCount)).shuffled(random)
    }
}

private val combiningMarks = Regex("\\p{M}+")
private fun normalizedTopic(value: String) = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD).replace(combiningMarks, "")
private fun generatedMatchesTopic(generated: String, topic: String): Boolean {
    if (generated.isBlank()) return false
    val target = normalizedTopic(topic)
    val terms = when (generated) {
        "Loi d'Ohm", "Grandeurs électriques", "Unités et préfixes" -> listOf("electricite", "continu", "ohm")
        "Associations de résistances" -> listOf("groupement")
        "Associations de condensateurs", "Réactances" -> listOf("condensateur", "bobine", "rlc", "alternatif")
        "Décibels" -> listOf("decibel", "db", "puissance")
        "Longueur d'onde" -> listOf("antenne", "propagation", "frequence")
        "Numérique" -> listOf("numerique", "modulation")
        "Morse" -> listOf("morse", "telegraphie")
        "Modulation" -> listOf("modulation", "signal")
        else -> listOf(normalizedTopic(generated))
    }
    return terms.any { it in target }
}
