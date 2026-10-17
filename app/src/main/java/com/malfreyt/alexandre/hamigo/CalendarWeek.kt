package com.malfreyt.alexandre.hamigo

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

internal object CalendarWeek {
    fun days(date:LocalDate,locale:Locale=Locale.getDefault()):List<LocalDate> {
        val start=date.with(TemporalAdjusters.previousOrSame(WeekFields.of(locale).firstDayOfWeek))
        return (0L..6L).map(start::plusDays)
    }
}
