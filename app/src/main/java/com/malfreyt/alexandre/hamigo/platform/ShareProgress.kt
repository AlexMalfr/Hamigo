package com.malfreyt.alexandre.hamigo.platform

import org.json.JSONObject
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate

data class DailyPoint(val day: String, val xp: Int)

/** Publicly shareable statistics, deliberately separated from the complete learning backup. */
data class ShareProgress(
    val name: String,
    val xp: Int,
    val streak: Int,
    val lessons: Int,
    val weeklyXp: Int = 0,
    val updatedAt: String = Instant.now().toString(),
    val dailyXp: List<DailyPoint> = emptyList()
) {
    fun toJson(): String = JSONObject()
        .put("schema", 2)
        .put("name", name.trim().take(48))
        .put("xp", xp.coerceAtLeast(0))
        .put("streak", streak.coerceAtLeast(0))
        .put("lessons", lessons.coerceAtLeast(0))
        .put("weeklyXp", weeklyXp.coerceAtLeast(0))
        .put("updatedAt", updatedAt)
        .put("dailyXp", JSONArray(dailyXp.takeLast(30).map {
            JSONObject().put("day", it.day).put("xp", it.xp.coerceAtLeast(0))
        }))
        .toString(2)

    companion object {
        fun fromJson(json: String): ShareProgress {
            require(json.length <= 32_768) { "Le fichier de progression est trop volumineux." }
            val data = try { JSONObject(json) } catch (_: Exception) {
                throw IllegalArgumentException("Le fichier de progression n'est pas un JSON valide.")
            }
            val schema = data.opt("schema")
            require(schema is Number && schema.toDouble() == 2.0) { "Version du fichier de progression non reconnue." }
            val suppliedName = data.opt("name")
            require(suppliedName is String) { "Pseudo absent ou invalide." }
            val name = suppliedName.trim()
            require(name.isNotEmpty() && name.length <= 48) { "Pseudo absent ou trop long." }
            fun number(key: String, optional: Boolean = false): Int {
                if (optional && !data.has(key)) return 0
                val value = data.opt(key)
                require(value is Number && value.toDouble() % 1.0 == 0.0 &&
                    value.toDouble() in 0.0..Int.MAX_VALUE.toDouble()) { "Valeur $key invalide." }
                return value.toInt()
            }
            val date = data.optString("updatedAt")
            try { Instant.parse(date) } catch (_: Exception) {
                throw IllegalArgumentException("Date de progression invalide.")
            }
            val points = data.optJSONArray("dailyXp")?.let { array ->
                require(array.length() <= 30) { "Historique de progression trop long." }
                (0 until array.length()).map { index ->
                    val point = array.getJSONObject(index)
                    val day = point.getString("day")
                    try { require(day.length == 10); LocalDate.parse(day) } catch (_: Exception) {
                        throw IllegalArgumentException("Jour de progression invalide.")
                    }
                    val amount = point.opt("xp")
                    require(amount is Number && amount.toDouble() % 1.0 == 0.0 && amount.toDouble() in 0.0..Int.MAX_VALUE.toDouble()) {
                        "XP journaliers invalides."
                    }
                    DailyPoint(day, amount.toInt())
                }.also { require(it.map { point -> point.day }.distinct().size == it.size) { "Jour de progression répété." } }.sortedBy { it.day }
            } ?: emptyList()
            return ShareProgress(name, number("xp"), number("streak"), number("lessons"),
                number("weeklyXp", optional = true), date, points)
        }
    }
}
