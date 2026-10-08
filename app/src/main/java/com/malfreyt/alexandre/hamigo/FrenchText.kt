package com.malfreyt.alexandre.hamigo

/** French counts use the singular for zero and one. Shared with Canvas artwork and RemoteViews. */
fun dayUnit(count: Int) = if (count > 1) "jours" else "jour"
fun dayCount(count: Int) = "$count ${dayUnit(count)}"
