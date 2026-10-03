package com.malfreyt.alexandre.hamigo.platform

import org.json.JSONObject
import java.time.Instant

/** The only data transmitted to a social Gist. It contains no answers, review history, or credentials. */
data class ShareProgress(
    val name: String,
    val xp: Int,
    val streak: Int,
    val lessons: Int,
    val weeklyXp: Int = 0,
    val updatedAt: String = Instant.now().toString()
) {
    fun toJson(): String = JSONObject()
        .put("schema", 1)
        .put("name", name.trim().take(48))
        .put("xp", xp.coerceAtLeast(0))
        .put("streak", streak.coerceAtLeast(0))
        .put("lessons", lessons.coerceAtLeast(0))
        .put("weeklyXp", weeklyXp.coerceAtLeast(0))
        .put("updatedAt", updatedAt)
        .toString(2)

    companion object {
        fun fromJson(json: String): ShareProgress {
            require(json.length <= 32_768) { "Le fichier de progression est trop volumineux." }
            val data = try { JSONObject(json) } catch (_: Exception) {
                throw IllegalArgumentException("Le fichier de progression n'est pas un JSON valide.")
            }
            val schema = data.opt("schema")
            require(schema is Number && schema.toDouble() == 1.0) { "Version du fichier de progression non reconnue." }
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
            return ShareProgress(name, number("xp"), number("streak"), number("lessons"),
                number("weeklyXp", optional = true), date)
        }
    }
}
