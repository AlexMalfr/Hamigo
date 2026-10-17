package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class CalendarAndStudyDayTest {
    private val paris=ZoneId.of("Europe/Paris")
    @Test fun studyDayChangesAtThreeWithoutLosingAnHourToDaylightSaving() {
        listOf("2026-03-29","2026-10-25","2026-10-17").map(LocalDate::parse).forEach {day->
            assertEquals(day.minusDays(1),StudyDay.date(day.atTime(1,59).atZone(paris).toInstant(),paris))
            assertEquals(day,StudyDay.date(day.atTime(3,0).atZone(paris).toInstant(),paris))
            assertEquals(day,StudyDay.date(day.atTime(3,30).atZone(paris).toInstant(),paris))
        }
        val day=LocalDate.of(2026,10,17)
        assertEquals(day.minusDays(1),StudyDay.date(day.atTime(2,59,59).atZone(paris).toInstant(),paris))
    }
    @Test fun weekFollowsSystemConventionAndCrossesMonthAndYearBoundaries() {
        val sunday=LocalDate.of(2027,1,3)
        val french=CalendarWeek.days(sunday,Locale.FRANCE)
        assertEquals(LocalDate.of(2026,12,28),french.first());assertEquals(sunday,french.last())
        assertEquals(sunday,CalendarWeek.days(sunday,Locale.US).first())
        assertEquals(7,french.distinct().size)
        assertEquals(CalendarWeek.days(sunday.minusDays(2),Locale.FRANCE),french)
    }
}
