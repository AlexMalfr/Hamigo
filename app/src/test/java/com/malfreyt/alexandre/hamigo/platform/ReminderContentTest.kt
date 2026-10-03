package com.malfreyt.alexandre.hamigo.platform

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ReminderContentTest {
    private val today = LocalDate.of(2026, 10, 3)

    @Test
    fun contextReflectsActivityAndGoalWithoutInventingAStreakRepair() {
        assertEquals(ReminderContext.START, build(0, emptySet()).context)
        val continuing = build(0, setOf(today.minusDays(1).toString(), today.minusDays(2).toString()))
        assertEquals(ReminderContext.CONTINUE, continuing.context)
        assertEquals(2, continuing.streak)
        val restarting = build(0, setOf(today.minusDays(2).toString()))
        assertEquals(ReminderContext.RESTART, restarting.context)
        assertEquals(0, restarting.streak)
        assertFalse(restarting.message.lowercase().contains("répar"))
        assertEquals(ReminderContext.IN_PROGRESS, build(12, setOf(today.toString())).context)
        assertEquals(ReminderContext.GOAL_REACHED, build(30, setOf(today.toString())).context)
        assertEquals(ReminderContext.GOAL_REACHED, build(60, setOf(today.toString())).context)
    }

    @Test
    fun nineConsecutiveDaysHaveDifferentMessagesInEveryContext() {
        ReminderContext.entries.forEach { context ->
            val contents = (0L..8L).map { offset ->
                val day = today.plusDays(offset)
                val state = when (context) {
                    ReminderContext.START -> ReminderState(0, 30, emptySet())
                    ReminderContext.CONTINUE -> ReminderState(0, 30, setOf(day.minusDays(1).toString()))
                    ReminderContext.RESTART -> ReminderState(0, 30, setOf(day.minusDays(3).toString()))
                    ReminderContext.IN_PROGRESS -> ReminderState(12, 30, setOf(day.toString()))
                    ReminderContext.GOAL_REACHED -> ReminderState(30, 30, setOf(day.toString()))
                }
                ReminderContent.build(state, day).also { assertEquals(context, it.context) }
            }
            assertEquals(9, contents.map { it.message }.toSet().size)
            assertTrue(contents.map { it.mood }.toSet().size >= 3)
            assertTrue(contents.map { it.pose }.toSet().size >= 3)
        }
    }

    @Test
    fun oneDayIsStableAndInvalidOrFutureActivityDoesNotPretendAPriorSeriesExists() {
        val state = ReminderState(0, 30, setOf("invalid", today.plusDays(1).toString()))
        assertEquals(ReminderContent.build(state, today), ReminderContent.build(state, today))
        assertEquals(ReminderContext.START, ReminderContent.build(state, today).context)
        val invalidGoal = ReminderContent.build(ReminderState(-4, 0, emptySet()), today)
        assertEquals(0, invalidGoal.todayXp)
        assertEquals(1, invalidGoal.goal)
    }

    private fun build(xp: Int, activeDays: Set<String>) =
        ReminderContent.build(ReminderState(xp, 30, activeDays), today)
}
