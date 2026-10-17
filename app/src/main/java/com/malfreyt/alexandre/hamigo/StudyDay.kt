package com.malfreyt.alexandre.hamigo

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The learning day closes at 03:00 local time, including across daylight-saving changes. */
internal object StudyDay {
    fun date(at:Instant=Instant.now(),zone:ZoneId=ZoneId.systemDefault()):LocalDate {
        val local=at.atZone(zone)
        return local.toLocalDate().minusDays(if(local.hour<3)1 else 0)
    }
    fun nextBoundary(at:Instant=Instant.now(),zone:ZoneId=ZoneId.systemDefault()):Instant =
        date(at,zone).plusDays(1).atTime(3,0).atZone(zone).toInstant()
}
