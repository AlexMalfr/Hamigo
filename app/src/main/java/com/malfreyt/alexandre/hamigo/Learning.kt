package com.malfreyt.alexandre.hamigo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

data class PairItem(val left: String, val right: String)
data class Question(
    val id: String, val prompt: String, val choices: List<String>, val answer: Int,
    val explanation: String, val topic: String = "", val section: String = "technique",
    val kind: String = "choice", val image: String? = null, val value: Double? = null,
    val unit: String = "", val tolerance: Double = .01, val pairs: List<PairItem> = emptyList(),
    val source: String = "Hamigo", val bands: List<String> = emptyList()
)
data class Lesson(val id: String, val title: String, val summary: String, val body: List<String>,
    val formula: String, val topic: String, val questions: List<Question>)
data class Chapter(val id: String, val title: String, val subtitle: String, val lessons: List<Lesson>)
data class RefRow(val term: String, val description: String, val extra: String)
data class RefCategory(val id: String, val title: String, val subtitle: String, val rows: List<RefRow>, val flashcards: Boolean)
data class Review(val due: Long = 0, val interval: Double = 0.0, val ease: Double = 2.5,
    val repetitions: Int = 0, val lapses: Int = 0)

/** SM-2 inspired scheduling. Times are milliseconds; incorrect answers return after 10 minutes. */
object SpacedRepetition {
    fun next(old: Review, quality: Int, now: Long): Review {
        require(quality in 0..5)
        val ease = max(1.3, old.ease + .1 - (5 - quality) * (.08 + (5 - quality) * .02))
        if (quality < 3) return Review(now + 600_000, 0.0, ease, 0, old.lapses + 1)
        val interval = when (old.repetitions) { 0 -> 1.0; 1 -> 6.0; else -> old.interval * ease }
        val adjusted = if (quality == 3) max(1.0, interval * .7) else interval
        return Review(now + (adjusted * 86_400_000).toLong(), adjusted, ease, old.repetitions + 1, old.lapses)
    }
}

object LearningRules {
    fun streak(activeDays: Set<String>, today: LocalDate): Int {
        var cursor = if (today.toString() in activeDays) today else today.minusDays(1)
        var count = 0
        while (cursor.toString() in activeDays) { count++; cursor = cursor.minusDays(1) }
        return count
    }
    fun numericCorrect(input: String, expected: Double, tolerance: Double): Boolean {
        val value = input.trim().replace(',', '.').toDoubleOrNull() ?: return false
        return value.isFinite() && kotlin.math.abs(value - expected) <= max(tolerance, 1e-8) + max(1e-10, kotlin.math.abs(expected)*1e-12)
    }
    fun examPassed(regulation: Int, technique: Int) = regulation >= 10 && technique >= 10
}

class Content(private val context: Context) {
    private fun json(path: String) = context.assets.open(path).bufferedReader().use { it.readText() }
    val chapters: List<Chapter> = JSONObject(json("curriculum.json")).getJSONArray("chapters").objects().map { c ->
        Chapter(c.getString("id"), c.getString("title"), c.optString("subtitle"), c.getJSONArray("lessons").objects().map { l ->
            val topic = l.optString("topic")
            Lesson(l.getString("id"), l.getString("title"), l.optString("summary"), l.getJSONArray("body").strings(),
                l.optString("formula"), topic, l.getJSONArray("questions").objects().map { parseQuestion(it, topic) })
        })
    }
    val lessons = chapters.flatMap { it.lessons }
    val references = JSONObject(json("reference.json")).getJSONArray("categories").objects().map { c ->
        RefCategory(c.getString("id"), c.getString("title"), c.optString("subtitle"), c.getJSONArray("rows").objects().map {
            RefRow(it.getString("term"), it.getString("description"), it.optString("extra")) }, c.optBoolean("flashcards", true))
    }
    val exam: List<Question> = run {
        val raw = json("exam1/questions.json").trim()
        val array = if (raw.startsWith("[")) JSONArray(raw) else JSONObject(raw).getJSONArray("questions")
        array.objects().map { parseQuestion(it) }
    }
    val excluded = runCatching { JSONArray(json("exam1/excluded.json")).strings().toSet() }.getOrDefault(emptySet())
    val activeExam get() = exam.filter { it.id !in excluded }
    val flashcards = references.filter { it.flashcards }.flatMap { cat -> cat.rows.mapIndexed { i, row ->
        Question("flash-${cat.id}-$i", row.term, listOf(row.description), 0, row.extra,
            topic = cat.id, kind = "flash", source = cat.title)
    } }
    val procedural = (0..250).flatMap { PracticeGenerator.create(it) }.distinctBy { it.id }
    val allQuestions = (lessons.flatMap { it.questions } + exam + flashcards + procedural).associateBy { it.id }
    val topics = activeExam.groupBy { it.topic }.toSortedMap()
    fun nextLesson(completed: Set<String>) = lessons.firstOrNull { it.id !in completed }
    private fun parseQuestion(q: JSONObject, fallbackTopic: String = ""): Question {
        val pairs = q.optJSONArray("pairs")?.objects()?.map { PairItem(it.getString("left"), it.getString("right")) } ?: emptyList()
        return Question(q.getString("id"), q.getString("prompt"), q.optJSONArray("choices")?.strings() ?: emptyList(),
            q.optInt("answer", 0), q.optString("explanation"), q.optString("topic", fallbackTopic),
            q.optString("section", "technique"), q.optString("kind", "choice"), q.optString("image").takeIf { it.isNotBlank() && it != "null" },
            if (q.has("value") && !q.isNull("value")) q.getDouble("value") else null, q.optString("unit"),
            q.optDouble("tolerance", .01), pairs, q.optString("source", "Hamigo"),
            q.optJSONArray("bands")?.strings() ?: q.optJSONObject("resistor")?.optJSONArray("bands")?.strings() ?: emptyList())
    }
}
fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
fun JSONArray.strings() = (0 until length()).map { getString(it) }

class Progress(private val context: Context) {
    val prefs = context.getSharedPreferences("hamigo", Context.MODE_PRIVATE)
    private var root = runCatching { JSONObject(prefs.getString("progress", "{}")!!) }.getOrElse { JSONObject() }
    var name: String
        get() = prefs.getString("name", "Pilote des ondes")!!
        set(value) { prefs.edit().putString("name", value.take(40).ifBlank { "Pilote des ondes" }).apply() }
    val completed: Set<String> get() = (root.optJSONArray("completed") ?: JSONArray()).strings().toSet()
    val reviews: Map<String, Review> get() {
        val obj = root.optJSONObject("reviews") ?: JSONObject()
        return obj.keys().asSequence().associateWith { id -> val r = obj.getJSONObject(id)
            Review(r.optLong("due"), r.optDouble("interval"), r.optDouble("ease", 2.5), r.optInt("repetitions"), r.optInt("lapses")) }
    }
    val activeDays: Set<String> get() = (root.optJSONObject("dailyXp") ?: JSONObject()).keys().asSequence().toSet()
    val xp get() = root.optInt("xp")
    val streak get() = LearningRules.streak(activeDays, LocalDate.now())
    val todayXp get() = dayXp(LocalDate.now())
    val weeklyXp get() = (0L..6L).sumOf { dayXp(LocalDate.now().minusDays(it)) }
    val totalAnswers get() = root.optInt("answers")
    val totalCorrect get() = root.optInt("correct")
    val dailyGoal get() = prefs.getInt("dailyGoal", 30)
    fun dayXp(day: LocalDate) = root.optJSONObject("dailyXp")?.optInt(day.toString()) ?: 0
    fun due(content: Content) = reviews.filter { (id, r) -> r.due <= System.currentTimeMillis() && id in content.allQuestions }
        .toList().sortedBy { it.second.due }.mapNotNull { content.allQuestions[it.first] }
    fun answer(id: String, correct: Boolean, quality: Int = if (correct) 4 else 1): Int {
        val now = System.currentTimeMillis()
        val previous = reviews[id] ?: Review()
        // Early successful rereading isn't a spaced recall. Failed items may be corrected immediately.
        val review = if(correct && previous.repetitions>0 && previous.due>now) previous else SpacedRepetition.next(previous, quality, now)
        val obj = root.optJSONObject("reviews") ?: JSONObject().also { root.put("reviews", it) }
        obj.put(id, JSONObject().put("due", review.due).put("interval", review.interval).put("ease", review.ease)
            .put("repetitions", review.repetitions).put("lapses", review.lapses))
        // Revisiting a card early is allowed, but can't farm XP on the same item in the same day.
        val awarded = root.optJSONObject("awarded") ?: JSONObject().also { root.put("awarded", it) }
        val day = LocalDate.now().toString()
        val gain = if (awarded.optString(id) == day) 0 else if (correct) 10 else 2
        awarded.put(id, day)
        root.put("answers", totalAnswers + 1).put("correct", totalCorrect + if (correct) 1 else 0)
        addXp(gain); save(); return gain
    }
    fun complete(id: String): Int {
        if (id !in completed) {
            root.put("completed", JSONArray((completed + id).toList())); addXp(20); save();return 20
        }
        return 0
    }
    private fun addXp(gain: Int) {
        root.put("xp", xp + gain)
        if (gain > 0) {
            val days = root.optJSONObject("dailyXp") ?: JSONObject().also { root.put("dailyXp", it) }
            val day = LocalDate.now().toString(); days.put(day, days.optInt(day) + gain)
        }
    }
    private fun save() { root.put("schema", 1); prefs.edit().putString("progress", root.toString()).apply() }
    fun export() = JSONObject().put("app", "hamigo").put("schema", 1).put("name", name).put("progress", root).toString(2)
    fun import(json: String) {
        val candidate = JSONObject(json)
        require(candidate.optString("app") == "hamigo" && candidate.optInt("schema") == 1) { "Ce fichier n'est pas une sauvegarde Hamigo." }
        val state = candidate.getJSONObject("progress")
        require(state.optInt("xp") >= 0 && state.optInt("answers") >= 0) { "Sauvegarde invalide." }
        val importedReviews = state.optJSONObject("reviews") ?: JSONObject()
        require(importedReviews.length() <= 20_000) { "Sauvegarde trop volumineuse." }
        importedReviews.keys().forEach { key ->
            val r = importedReviews.getJSONObject(key)
            require(r.getDouble("ease").isFinite() && r.getDouble("ease") >= 1.3 && r.getDouble("interval").isFinite() && r.getDouble("interval") >= 0 && r.getLong("due") >= 0)
        }
        root = state; name = candidate.optString("name", name); save()
    }
    fun snapshot() = com.malfreyt.alexandre.hamigo.platform.ShareProgress(name, xp, streak, completed.size, weeklyXp)
}

object PracticeGenerator {
    fun create(seed: Int = Random.nextInt()): List<Question> {
        val random = Random(seed)
        return (0 until 8).map { n ->
            val resistance = listOf(10, 22, 47, 100, 220, 470, 1000).random(random)
            val current = listOf(.01, .02, .05, .1).random(random)
            val voltage = resistance * current
            when (n % 4) {
                0 -> Question("proc-ohm-$resistance-$current", "Une résistance de $resistance Ω est parcourue par ${formatNumber(current * 1000)} mA. Quelle tension à ses bornes ?",
                    emptyList(), 0, "U = R × I. ${resistance} × ${formatNumber(current)} = ${formatNumber(voltage)} V. Convertis les mA en A avant le calcul.", kind="number", value=voltage, unit="V", tolerance=.02)
                1 -> Question("proc-power-$resistance-$current", "Avec U = ${formatNumber(voltage)} V et I = ${formatNumber(current)} A, quelle puissance est dissipée ?",
                    emptyList(), 0, "P = U × I = ${formatNumber(voltage)} × ${formatNumber(current)} = ${formatNumber(voltage * current)} W.", kind="number", value=voltage * current, unit="W", tolerance=max(.000001,voltage * current * .01))
                2 -> { val f = listOf(3, 7, 14, 28, 50, 100, 150, 300).random(random)
                    Question("proc-wave-$f", "Un signal de $f MHz a quelle longueur d'onde approximative dans le vide ?", emptyList(), 0,
                        "λ ≈ 300 / f(MHz) = ${formatNumber(300.0 / f)} m.", kind="number", value=300.0/f, unit="m", tolerance=.1) }
                else -> { val f = listOf(145.0, 146.0, 144.0, 147.0).random(random)
                    Question("proc-band-$f", "Place le curseur sur $f MHz : entraîne-toi à lire une fréquence VHF.", emptyList(), 0,
                        "144 à 146 MHz est la bande amateur dite « 2 mètres » en France métropolitaine. La fréquence $f MHz ${if (f < 146 && f >= 144) "se situe à l'intérieur" else "est une limite ou hors de l'intérieur"} de cette bande.", kind="frequency", value=f, unit="MHz", tolerance=.05) }
            }
        }
    }
}
fun formatNumber(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString()
    else "%.6f".format(java.util.Locale.FRANCE, value).trimEnd('0').trimEnd(',')
