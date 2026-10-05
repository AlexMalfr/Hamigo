package com.malfreyt.alexandre.hamigo.platform

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/** A mergeable backup: historical totals form a baseline, subsequent attempts are immutable events. */
object CloudProgress {
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val MAX_EVENTS = 100_000
    private val eventId = Regex("[a-fA-F0-9-]{36}")
    const val MAX_FRIENDS = 30
    private const val MAX_FRIEND_RECORDS = 2_000
    private val friendId = Regex("[a-f0-9]{5,64}")

    /** Relationships are independent registers. Cache updates never modify relationship clocks. */
    fun localFriends(active: JSONArray, tombstones: JSONObject): JSONObject {
        val result = JSONObject()
        for (index in 0 until minOf(active.length(), MAX_FRIENDS)) {
            runCatching {
                val entry = active.getJSONObject(index)
                val id = GitHubSync.gistId(entry.getString("gist"))
                val record = JSONObject().put("modifiedAt", entry.optLong("modifiedAt", 1L).coerceAtLeast(1L))
                    .put("deleted", false).put("progress", JSONObject(ShareProgress.fromJson(entry.getJSONObject("progress").toString()).toJson()))
                result.put(id, result.optJSONObject(id)?.let { chooseFriend(it, record) } ?: record)
            }
        }
        tombstones.keys().forEach { id ->
            if (friendId.matches(id) && tombstones.optLong(id) > 0) {
                val record = JSONObject().put("modifiedAt", tombstones.getLong(id)).put("deleted", true)
                result.put(id, result.optJSONObject(id)?.let { chooseFriend(it, record) } ?: record)
            }
        }
        checkFriends(result)
        return sortedFriends(result)
    }

    fun activeFriends(records: JSONObject): JSONArray = JSONArray(records.keys().asSequence().sorted().mapNotNull { id ->
        val record = records.getJSONObject(id)
        if (record.getBoolean("deleted")) null else JSONObject().put("gist", "https://gist.github.com/$id")
            .put("modifiedAt", record.getLong("modifiedAt")).put("progress", copy(record.getJSONObject("progress")))
    }.toList())

    fun friendTombstones(records: JSONObject): JSONObject = JSONObject().also { result ->
        records.keys().asSequence().sorted().forEach { id ->
            val record = records.getJSONObject(id)
            if (record.getBoolean("deleted")) result.put(id, record.getLong("modifiedAt"))
        }
    }

    private fun chooseFriend(a: JSONObject, b: JSONObject): JSONObject {
        val timeA = a.getLong("modifiedAt"); val timeB = b.getLong("modifiedAt")
        val winner = when {
            timeA > timeB -> a
            timeB > timeA -> b
            a.getBoolean("deleted") != b.getBoolean("deleted") -> if (a.getBoolean("deleted")) a else b
            canonical(a) >= canonical(b) -> a
            else -> b
        }
        val result = copy(winner)
        // Compare cache dates within the same relationship version. A deliberate re-add starts
        // a new version; an older pre-deletion cache must not leak across that boundary.
        if (timeA == timeB && !winner.getBoolean("deleted") && !a.getBoolean("deleted") && !b.getBoolean("deleted")) {
            val left = a.getJSONObject("progress"); val right = b.getJSONObject("progress")
            val cache = when {
                java.time.Instant.parse(left.getString("updatedAt")) > java.time.Instant.parse(right.getString("updatedAt")) -> left
                java.time.Instant.parse(left.getString("updatedAt")) < java.time.Instant.parse(right.getString("updatedAt")) -> right
                canonical(left) >= canonical(right) -> left
                else -> right
            }
            result.put("progress", copy(cache))
        }
        return result
    }

    private fun mergeFriends(a: JSONObject, b: JSONObject): JSONObject {
        val result = mergeObjects(a, b, ::chooseFriend)
        require(result.length() <= MAX_FRIEND_RECORDS) { "L'historique des équipiers est trop volumineux." }
        // Concurrent additions can exceed the UI limit. Keep the newest thirty deterministically;
        // retain deletion records for the overflow, so a stale device cannot put it back.
        val active = result.keys().asSequence().filter { !result.getJSONObject(it).getBoolean("deleted") }
            .sortedWith(compareByDescending<String> { result.getJSONObject(it).getLong("modifiedAt") }.thenBy { it }).toList()
        active.drop(MAX_FRIENDS).forEach { id ->
            result.put(id, JSONObject().put("modifiedAt", result.getJSONObject(id).getLong("modifiedAt")).put("deleted", true))
        }
        return sortedFriends(result)
    }

    private fun sortedFriends(records: JSONObject): JSONObject = JSONObject().also { result ->
        records.keys().asSequence().sorted().forEach { result.put(it, copy(records.getJSONObject(it))) }
    }

    private fun checkFriends(records: JSONObject) {
        require(records.length() <= MAX_FRIEND_RECORDS) { "Trop de relations dans la sauvegarde." }
        var active = 0
        records.keys().forEach { id ->
            require(friendId.matches(id)) { "Identifiant d'équipier invalide." }
            val record = records.getJSONObject(id)
            require(record.keys().asSequence().all { it in setOf("modifiedAt", "deleted", "progress") }) { "Relation de sauvegarde invalide." }
            val modified = record.opt("modifiedAt")
            require(modified is Number && modified.toDouble().isFinite() && modified.toDouble() % 1.0 == 0.0 && modified.toDouble() in 1.0..9_007_199_254_740_991.0) { "Date de relation invalide." }
            require(record.opt("deleted") is Boolean) { "État de relation invalide." }
            if (record.getBoolean("deleted")) {
                require(!record.has("progress")) { "Une relation supprimée ne doit pas contenir de profil." }
            } else {
                active++
                record.put("progress", JSONObject(ShareProgress.fromJson(record.getJSONObject("progress").toString()).toJson()))
            }
        }
        require(active <= MAX_FRIENDS) { "La sauvegarde dépasse la limite de trente équipiers." }
    }

    /** Call before the first local counter mutation, so that existing XP isn't counted twice. */
    fun ensureLedger(state: JSONObject) {
        if (!state.has("syncBase")) {
            state.put("syncBase", JSONObject()
                .put("xp", state.optInt("xp"))
                .put("answers", state.optInt("answers"))
                .put("correct", state.optInt("correct"))
                .put("dailyXp", copy(state.optJSONObject("dailyXp") ?: JSONObject()))
                .put("awarded", copy(state.optJSONObject("awarded") ?: JSONObject()))
                .put("completed", JSONArray(state.optJSONArray("completed")?.toString() ?: "[]")))
        }
        if (!state.has("syncEvents")) state.put("syncEvents", JSONObject())
    }

    /** Records an attempt, independently of its XP award. Same question/day awards merge by maximum. */
    fun recordEvent(state: JSONObject, day: String, xp: Int, answers: Int = 0, correct: Int = 0,
        completed: String? = null, awardKey: String? = null) {
        ensureLedger(state)
        require(xp >= 0 && answers in 0..1 && correct in 0..answers)
        LocalDate.parse(day)
        val event = JSONObject().put("day", day).put("xp", xp)
            .put("answers", answers).put("correct", correct).put("at", System.currentTimeMillis())
        completed?.let { event.put("completed", it) }
        awardKey?.let { event.put("awardKey", it) }
        state.getJSONObject("syncEvents").put(UUID.randomUUID().toString(), event)
    }

    /** Idempotent and commutative; neither device can erase the other's completed lessons or events. */
    fun merge(localJson: String, remoteJson: String): String {
        val local = checked(localJson)
        val remote = checked(remoteJson)
        val left = local.getJSONObject("progress")
        val right = remote.getJSONObject("progress")
        ensureLedger(left); ensureLedger(right)
        val base = mergeBase(left.getJSONObject("syncBase"), right.getJSONObject("syncBase"))
        val events = mergeObjects(left.getJSONObject("syncEvents"), right.getJSONObject("syncEvents")) { a, b ->
            if (canonical(a) >= canonical(b)) a else b
        }
        require(events.length() <= MAX_EVENTS) { "L'historique de synchronisation est trop volumineux." }
        val days = copy(base.getJSONObject("dailyXp"))
        val completed = strings(base.optJSONArray("completed")).toMutableSet()
        completed += strings(left.optJSONArray("completed"))
        completed += strings(right.optJSONArray("completed"))
        var answers = base.optInt("answers").toLong()
        var correct = base.optInt("correct").toLong()
        val awards = linkedMapOf<String, Pair<String, Int>>()
        val historicalAwards=base.optJSONObject("awarded") ?: JSONObject()
        events.keys().asSequence().sorted().forEach { id ->
            val event = events.getJSONObject(id)
            answers += event.getInt("answers")
            correct += event.getInt("correct")
            val lesson = event.optString("completed")
            if (lesson.isNotBlank()) completed += lesson
            val earned = event.getInt("xp")
            if (earned > 0 && (lesson.isBlank() || lesson !in strings(base.optJSONArray("completed")))) {
                val key = if (lesson.isNotBlank()) "lesson:$lesson" else event.optString("awardKey").ifBlank { "event:$id" }
                // A pre-ledger device already paid these question/day awards in its baseline.
                val questionId=key.takeIf { it.startsWith("answer:") && it.endsWith(":${event.getString("day")}") }
                    ?.removePrefix("answer:")?.removeSuffix(":${event.getString("day")}")
                if(questionId!=null && historicalAwards.optString(questionId)==event.getString("day")) return@forEach
                val candidate = event.getString("day") to earned
                val previous = awards[key]
                if (previous == null || candidate.second > previous.second ||
                    (candidate.second == previous.second && candidate.first < previous.first)) awards[key] = candidate
            }
        }
        var xp = base.optInt("xp").toLong()
        awards.values.forEach { (day, earned) ->
            xp += earned
            days.put(day, (days.optLong(day) + earned).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
        val reviews = mergeObjects(left.optJSONObject("reviews") ?: JSONObject(), right.optJSONObject("reviews") ?: JSONObject()) { a, b ->
            val timeA = a.optLong("updatedAt")
            val timeB = b.optLong("updatedAt")
            when { timeA > timeB -> a; timeB > timeA -> b; canonical(a) >= canonical(b) -> a; else -> b }
        }
        val awarded = JSONObject()
        listOf(left, right).forEach { side ->
            val entries = side.optJSONObject("awarded") ?: JSONObject()
            entries.keys().forEach { key ->
                val day = entries.getString(key)
                if (day > awarded.optString(key)) awarded.put(key, day)
            }
        }
        val state = JSONObject().put("schema", 1).put("xp", capped(xp))
            .put("answers", capped(answers)).put("correct", capped(correct))
            .put("dailyXp", days).put("completed", JSONArray(completed.sorted()))
            .put("reviews", reviews).put("awarded", awarded).put("syncBase", base).put("syncEvents", events)
        val profile = newer(local, remote, "profileUpdatedAt", "name")
        val preferences = newer(local, remote, "preferencesUpdatedAt", "preferences")
        val result = JSONObject().put("app", "hamigo").put("schema", 2).put("name", profile.optString("name", "Pilote des ondes"))
            .put("profileUpdatedAt", profile.optLong("profileUpdatedAt"))
            .put("preferences", preferences.optJSONObject("preferences") ?: JSONObject())
            .put("preferencesUpdatedAt", preferences.optLong("preferencesUpdatedAt"))
            .put("progress", state)
        if (local.has("friends") || remote.has("friends")) result.put("friends", mergeFriends(
            local.optJSONObject("friends") ?: JSONObject(), remote.optJSONObject("friends") ?: JSONObject()))
        return result.toString()
    }

    fun validate(json: String): String = checked(json).toString()

    private fun checked(json: String): JSONObject {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Sauvegarde GitHub trop volumineuse." }
        val wrapper = JSONObject(json)
        require(wrapper.optString("app") == "hamigo" && wrapper.optInt("schema") in 1..2) { "Ce Gist n'est pas une sauvegarde Hamigo." }
        val name = wrapper.opt("name")
        require(name is String && name.trim().isNotEmpty() && name.length <= 48) { "Pseudo de sauvegarde invalide." }
        if (wrapper.has("friends")) {
            require(wrapper.opt("friends") is JSONObject) { "Liste d'équipiers invalide." }
            checkFriends(wrapper.getJSONObject("friends"))
        }
        val state = wrapper.getJSONObject("progress")
        checkCollectionTypes(state)
        checkCounters(state)
        checkDays(state.optJSONObject("dailyXp") ?: JSONObject())
        checkCompleted(state.optJSONArray("completed"))
        val base = state.optJSONObject("syncBase")
        if (base != null) {
            checkCollectionTypes(base); checkCounters(base); checkDays(base.optJSONObject("dailyXp") ?: JSONObject())
            checkCompleted(base.optJSONArray("completed")); checkAwarded(base.optJSONObject("awarded") ?: JSONObject())
        }
        val events = state.optJSONObject("syncEvents") ?: JSONObject()
        require(events.length() <= MAX_EVENTS)
        events.keys().forEach { id ->
            require(eventId.matches(id)) { "Identifiant de réponse synchronisée invalide." }
            val event = events.getJSONObject(id)
            date(event.getString("day")); int(event, "xp"); int(event, "answers"); int(event, "correct")
            require(event.getInt("answers") in 0..1 && event.getInt("correct") in 0..event.getInt("answers"))
            require(event.optLong("at") >= 0)
            require(event.optString("completed").length <= 128 && event.optString("awardKey").length <= 512)
        }
        val reviews = state.optJSONObject("reviews") ?: JSONObject()
        require(reviews.length() <= 20_000)
        reviews.keys().forEach { id ->
            require(id.length in 1..256)
            val review = reviews.getJSONObject(id)
            require(review.getDouble("ease").isFinite() && review.getDouble("ease") >= 1.3)
            require(review.getDouble("interval").isFinite() && review.getDouble("interval") >= 0)
            require(review.getLong("due") >= 0 && review.optLong("updatedAt") >= 0)
            int(review, "repetitions"); int(review, "lapses")
        }
        checkAwarded(state.optJSONObject("awarded") ?: JSONObject())
        require(wrapper.optLong("profileUpdatedAt") >= 0 && wrapper.optLong("preferencesUpdatedAt") >= 0)
        require(!wrapper.has("preferences") || wrapper.opt("preferences") is JSONObject) { "Paramètres de sauvegarde invalides." }
        val preferences = wrapper.optJSONObject("preferences") ?: JSONObject()
        require(preferences.keys().asSequence().all { it in setOf("dailyGoal", "reminderEnabled", "reminderHour", "reminderMinute") }) {
            "La sauvegarde contient des paramètres non autorisés."
        }
        if (preferences.has("dailyGoal")) require(int(preferences, "dailyGoal") in 1..1000)
        if (preferences.has("reminderHour")) require(int(preferences, "reminderHour") in 0..23)
        if (preferences.has("reminderMinute")) require(int(preferences, "reminderMinute") in 0..59)
        if (preferences.has("reminderEnabled")) require(preferences.opt("reminderEnabled") is Boolean)
        return wrapper
    }

    private fun mergeBase(a: JSONObject, b: JSONObject): JSONObject {
        val days = JSONObject()
        val awarded = JSONObject()
        listOf(a, b).forEach { side ->
            val source = side.optJSONObject("dailyXp") ?: JSONObject()
            source.keys().forEach { day -> days.put(day, maxOf(days.optInt(day), source.getInt(day))) }
            val paid=side.optJSONObject("awarded") ?: JSONObject()
            paid.keys().forEach { id -> if(paid.getString(id)>awarded.optString(id)) awarded.put(id,paid.getString(id)) }
        }
        return JSONObject().put("xp", maxOf(a.optInt("xp"), b.optInt("xp")))
            .put("answers", maxOf(a.optInt("answers"), b.optInt("answers")))
            .put("correct", maxOf(a.optInt("correct"), b.optInt("correct")))
            .put("dailyXp", days).put("awarded", awarded)
            .put("completed", JSONArray((strings(a.optJSONArray("completed")) + strings(b.optJSONArray("completed"))).sorted()))
    }

    private fun mergeObjects(a: JSONObject, b: JSONObject, choose: (JSONObject, JSONObject) -> JSONObject): JSONObject {
        val result = JSONObject()
        (a.keys().asSequence().toSet() + b.keys().asSequence().toSet()).sorted().forEach { key ->
            val av = a.optJSONObject(key); val bv = b.optJSONObject(key)
            result.put(key, copy(when { av == null -> bv!!; bv == null -> av; else -> choose(av, bv) }))
        }
        return result
    }
    private fun newer(a: JSONObject, b: JSONObject, time: String, value: String): JSONObject = when {
        a.optLong(time) > b.optLong(time) -> a
        b.optLong(time) > a.optLong(time) -> b
        canonicalValue(a.opt(value)) >= canonicalValue(b.opt(value)) -> a
        else -> b
    }
    private fun checkCounters(obj: JSONObject) { listOf("xp", "answers", "correct").forEach { int(obj, it) }; require(obj.optInt("correct") <= obj.optInt("answers")) }
    private fun checkCollectionTypes(state: JSONObject) {
        listOf("dailyXp", "reviews", "awarded", "syncBase", "syncEvents").forEach { key ->
            require(!state.has(key) || state.opt(key) is JSONObject) { "État de sauvegarde $key invalide." }
        }
        require(!state.has("completed") || state.opt("completed") is JSONArray) { "Leçons de sauvegarde invalides." }
    }
    private fun checkAwarded(awarded: JSONObject) {
        require(awarded.length() <= 20_000)
        awarded.keys().forEach { id -> require(id.length in 1..256); date(awarded.getString(id)) }
    }
    private fun checkDays(days: JSONObject) { require(days.length() <= 10_000); days.keys().forEach { date(it); int(days, it) } }
    private fun checkCompleted(array: JSONArray?) { if (array != null) { require(array.length() <= 10_000); for (i in 0 until array.length()) require(array.get(i) is String && array.getString(i).length in 1..128) } }
    private fun date(value: String) { require(value.length == 10); LocalDate.parse(value) }
    private fun int(obj: JSONObject, key: String): Int {
        if (!obj.has(key)) return 0
        val value = obj.get(key)
        require(value is Number && value.toDouble().isFinite() && value.toDouble() % 1.0 == 0.0 && value.toDouble() in 0.0..Int.MAX_VALUE.toDouble()) { "Compteur $key invalide." }
        return value.toInt()
    }
    private fun strings(array: JSONArray?): Set<String> = if (array == null) emptySet() else (0 until array.length()).map { array.getString(it) }.toSet()
    private fun capped(value: Long) = value.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
    private fun copy(value: JSONObject) = JSONObject(value.toString())
    private fun canonical(value: JSONObject): String = value.keys().asSequence().sorted().joinToString(prefix = "{", postfix = "}") { "$it:${canonicalValue(value.opt(it))}" }
    private fun canonicalValue(value: Any?): String = when (value) { is JSONObject -> canonical(value); is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]") { canonicalValue(value.opt(it)) }; else -> value.toString() }
}
