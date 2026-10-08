package com.malfreyt.alexandre.hamigo

/** These keys identify the four existing positions; changing XP never rewrites a stored choice. */
object DailyGoals {
    val keys = listOf(20,30,60,100)
    val values = listOf(30,60,120,240)
    fun xp(key: Int): Int = keys.indexOf(key).takeIf { it>=0 }?.let(values::get) ?: key
    fun key(xp: Int): Int = values.indexOf(xp).takeIf { it>=0 }?.let(keys::get) ?: xp
}
