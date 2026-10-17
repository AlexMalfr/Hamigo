package com.malfreyt.alexandre.hamigo.platform

import org.json.JSONObject
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DailyPoint(val day: String, val xp: Int)
data class HourlyPoint(val hour: Int, val xp: Int)

/** Publicly shareable statistics, deliberately separated from the complete learning backup. */
data class ShareProgress(
    val name: String,
    val xp: Int,
    val streak: Int,
    val lessons: Int,
    val weeklyXp: Int = 0,
    val updatedAt: String = Instant.now().toString(),
    val dailyXp: List<DailyPoint> = emptyList(),
    val dailyGoal: Int? = null,
    val hourlyDay: String? = null,
    val hourlyXp: List<HourlyPoint> = emptyList(),
    val hourlyComplete: Boolean = false,
    val zoneId: String? = null,
    val activityHistory:PublicActivityHistory? = null,
    val appVersion:String?=null,
    val appVersionCode:Int?=null,
    val dayStartHour:Int=0
) {
    fun toJson(): String {
        val json=JSONObject()
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
        .put("dailyGoal", dailyGoal)
        .put("hourlyDay", hourlyDay)
        .put("hourlyXp", JSONArray(hourlyXp.map {JSONObject().put("hour",it.hour).put("xp",it.xp)}))
        .put("hourlyComplete",hourlyComplete)
        .put("zoneId",zoneId)
        .put("appVersion",appVersion)
        .put("appVersionCode",appVersionCode)
        .put("dayStartHour",dayStartHour)
        var history=activityHistory?.forPublication()
        if(history!=null)json.put("activityHistory",history.toJson())
        var result=json.toString(2)
        while(result.length>30_000&&history!=null&&history.months.isNotEmpty()) {
            history=history.trimOldestMonth();json.put("activityHistory",history.toJson());result=json.toString(2)
        }
        return result
    }

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
            val goal=if(data.has("dailyGoal"))number("dailyGoal").also {require(it in 1..1000)} else null
            val hourlyDay=if(data.has("hourlyDay"))data.getString("hourlyDay").also {require(it.length==10);LocalDate.parse(it)} else null
            val hours=data.optJSONArray("hourlyXp")?.let {array->
                require(array.length()<=24)
                (0 until array.length()).map {i->val point=array.getJSONObject(i)
                    val h=point.opt("hour");val x=point.opt("xp")
                    require(h is Number&&h.toDouble()%1.0==0.0&&h.toDouble() in 0.0..23.0)
                    require(x is Number&&x.toDouble()%1.0==0.0&&x.toDouble() in 0.0..Int.MAX_VALUE.toDouble())
                    HourlyPoint(h.toInt(),x.toInt())
                }.also {require(it.map {p->p.hour}.distinct().size==it.size)}.sortedBy {it.hour}
            }.orEmpty()
            val complete=if(data.has("hourlyComplete"))data.get("hourlyComplete").also {require(it is Boolean)} as Boolean else false
            val zone=if(data.has("zoneId"))data.getString("zoneId").also {require(it.length<=64);ZoneId.of(it)} else null
            val history=if(data.has("activityHistory"))PublicActivityHistory.read(data.getJSONObject("activityHistory")) else null
            val appVersion=if(data.has("appVersion"))data.get("appVersion").also {value->
                require(value is String&&value.length in 3..64&&value.matches(Regex("[0-9]+(?:\\.[0-9]+){1,3}(?:[-+][A-Za-z0-9.-]+)*")))
            } as String else null
            val appVersionCode=if(data.has("appVersionCode"))number("appVersionCode").also {require(it>0)} else null
            val dayStartHour=if(data.has("dayStartHour"))number("dayStartHour").also {require(it==0||it==3)} else 0
            points.forEach {p->history?.xp(LocalDate.parse(p.day))?.let {require(it==p.xp)}}
            require(hours.isEmpty()||hourlyDay!=null)
            if(complete) {
                require(hourlyDay!=null)
                require(points.firstOrNull {it.day==hourlyDay}?.xp?.toLong()==hours.sumOf {it.xp.toLong()})
            }
            return ShareProgress(name, number("xp"), number("streak"), number("lessons"),
                number("weeklyXp", optional = true), date, points,goal,hourlyDay,hours,complete,zone,history,appVersion,appVersionCode,dayStartHour)
        }
    }
}
