package com.malfreyt.alexandre.hamigo

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.Normalizer
import kotlin.random.Random
import kotlin.system.measureNanoTime

/** Reads the packaged bank without starting an activity, changing progress or calling a network API. */
@RunWith(AndroidJUnit4::class)
class MixPerformanceInstrumentedTest {
    private fun content(): Content {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.startsWith("sdk_")) { "Run benchmarks on the test emulator" }
        return Content(InstrumentationRegistry.getInstrumentation().targetContext)
    }

    @Test fun packagedThemesRemainIsolatedAndOneThousandQuestionMixesStayUnique() {
        val content = content()
        val index = content.mixIndex
        val technique = index.keys.filterTo(mutableSetOf()) { it.startsWith("technique|") }
        val regulation = index.keys.filterTo(mutableSetOf()) { it.startsWith("regulation|") }
        assertTrue(technique.isNotEmpty())
        assertEquals(content.activeExam.size + content.procedural.size, index.available(index.keys))
        assertEquals(content.activeExam.count { it.section == "regulation" }, index.available(regulation))
        assertEquals(0, index.available(emptySet()))

        val wholeTechnique = index.questions(technique, index.available(technique), Random(7))
        assertTrue("Selecting all Technique must include Morse", wholeTechnique.any { it.topic == "Morse" })
        assertTrue("Selecting all Technique must include decibel variants", wholeTechnique.any { it.topic == "Décibels" })
        assertTrue(wholeTechnique.all { it.section == "technique" })
        assertEquals(wholeTechnique.size, wholeTechnique.map { it.id }.toSet().size)
        assertFalse("Every generated question needs a factual theme", content.procedural.any { it.topic.isBlank() })

        val colorKey = index.keys.single { it.startsWith("technique|") && it.contains("Code des couleurs") }
        val colors = index.questions(setOf(colorKey), index.available(setOf(colorKey)), Random(8))
        assertFalse("Code of resistor colors must not mean digital code", colors.any { it.topic == "Numérique" })
        val generatedIds = content.procedural.map { it.id }.toSet()
        assertTrue("Resistor color selection must not inject unrelated procedural themes", colors.filter { it.id in generatedIds }.all { it.topic == "Code des couleurs" })

        for (selected in listOf(index.keys, technique)) {
            assertTrue(index.available(selected) >= 1000)
            for (count in listOf(1, 10, 20, 40, 80, 150, 1000)) {
                val questions = index.questions(selected, count, Random(count))
                assertEquals("Requested length $count", count, questions.size)
                assertEquals("Distinct identities at length $count", count, questions.map { it.id }.toSet().size)
            }
        }
    }

    @Test fun oneThousandSelectionChangesMeasureIndexAgainstHistoricalRegexScanner() {
        val content = content()
        val index = content.mixIndex
        // The old scanner and the current index intentionally differ for all-Technique and colors.
        // Comparable cases exclude the color key but exercise empty/full sets, both sections and combinations.
        val safeKeys = index.keys.filterNot { it.contains("Code des couleurs") }.sorted()
        val random = Random(901)
        val selections = List(1000) { n ->
            when (n % 16) {
                0 -> emptySet()
                1 -> index.keys
                2 -> index.keys.filterTo(mutableSetOf()) { it.startsWith("regulation|") }
                else -> safeKeys.shuffled(random).take(1 + n % 3).toSet()
            }
        }
        // Warm both paths before measuring; timings are diagnostic and never make the test fail.
        selections.take(16).forEach { selected ->
            assertEquals(historicalAvailable(content, index.keys, selected), index.available(selected))
        }
        val indexTimes = LongArray(selections.size)
        val scanTimes = LongArray(selections.size)
        selections.forEachIndexed { n, selected ->
            var indexed = -1
            var scanned = -1
            if (n % 2 == 0) {
                indexTimes[n] = measureNanoTime { indexed = index.available(selected) }
                scanTimes[n] = measureNanoTime { scanned = historicalAvailable(content, index.keys, selected) }
            } else {
                scanTimes[n] = measureNanoTime { scanned = historicalAvailable(content, index.keys, selected) }
                indexTimes[n] = measureNanoTime { indexed = index.available(selected) }
            }
            assertEquals("Equivalent pool for selection $n", scanned, indexed)
        }
        fun median(values: LongArray): Double {
            val sorted = values.sorted()
            return (sorted[sorted.size / 2 - 1].toDouble() + sorted[sorted.size / 2]) / 2.0
        }
        val indexedMedian = median(indexTimes)
        val scannedMedian = median(scanTimes)
        val report = JSONObject()
            .put("scope", "Packaged Content; availability computation on instrumentation thread; no UI frame-rate claim")
            .put("legacyBaseline", "fa933d65 PracticeHubScreen per-question filter with a newly compiled combining-marks regex per match")
            .put("fixture", "Current packaged questions and corrected nonblank generator topics; colors/all-Technique semantic changes separately tested")
            .put("iterations", selections.size)
            .put("fixedQuestions", content.activeExam.size)
            .put("proceduralQuestions", content.procedural.size)
            .put("indexMedianMicros", indexedMedian / 1000.0)
            .put("scannerMedianMicros", scannedMedian / 1000.0)
            .put("medianRatio", scannedMedian / indexedMedian.coerceAtLeast(1.0))
            .put("indexTotalMillis", indexTimes.sum() / 1_000_000.0)
            .put("scannerTotalMillis", scanTimes.sum() / 1_000_000.0)
            .put("allComparablePoolsEqual", true)
            .put("timingAssertions", false)
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        File(target.getExternalFilesDir(null), "mix-benchmark.json").writeText(report.toString(2))
        Log.i("HamigoMixBenchmark", report.toString())
        println("HamigoMixBenchmark: $report")
    }

    /** Baseline algorithm from 0.8; purposefully retains its per-question regex construction. */
    private fun historicalAvailable(content: Content, keys: Set<String>, selected: Set<String>): Int {
        val pool = content.activeExam.filter { "${it.section}|${it.topic}" in selected }
        val variants = content.procedural.filter { question ->
            selected == keys || selected.any { key -> key.startsWith("technique|") && historicalMatches(question.topic, key.substringAfter('|')) }
        }
        return pool.size + variants.size
    }

    private fun historicalMatches(generated: String, topic: String): Boolean {
        fun normalized(value: String) = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        val target = normalized(topic)
        val terms = when (generated) {
            "Loi d'Ohm", "Grandeurs électriques", "Unités et préfixes" -> listOf("electricite", "continu", "ohm")
            "Associations de résistances" -> listOf("resistance")
            "Associations de condensateurs", "Réactances" -> listOf("condensateur", "bobine", "rlc", "alternatif")
            "Décibels" -> listOf("decibel", "db", "puissance")
            "Longueur d'onde" -> listOf("antenne", "propagation", "frequence")
            "Numérique" -> listOf("numerique", "code", "modulation")
            "Morse" -> listOf("morse", "telegraphie")
            "Modulation" -> listOf("modulation", "signal")
            else -> listOf(normalized(generated))
        }
        return terms.any { it in target }
    }
}
