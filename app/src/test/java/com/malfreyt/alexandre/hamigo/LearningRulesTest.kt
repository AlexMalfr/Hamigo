package com.malfreyt.alexandre.hamigo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

/** Pure JVM checks for the rules used by lessons, revision, streaks and exam practice. */
class LearningRulesTest {
    @Test fun aPoorInitialAttemptDoesNotPassALessonAfterCorrections() {
        assertFalse(LearningRules.lessonPassed(0,8))
        assertFalse(LearningRules.lessonPassed(6,8))
        assertTrue(LearningRules.lessonPassed(7,8))
        assertTrue(LearningRules.lessonPassed(8,10))
        assertFalse(LearningRules.lessonPassed(0,0))
        val session=Session("Consolider",MutableList(8){i->Question("q$i","?",listOf("A","B"),0,"")},"lesson")
        session.firstCorrect=1;session.correct=8
        assertFalse(session.lessonPassed)
        session.firstCorrect=7;session.unresolved.add("q0")
        assertFalse(session.lessonPassed)
        session.unresolved.clear();assertTrue(session.lessonPassed)
    }
    private val now = 1_800_000_000_000L
    private val dayMillis = 86_400_000L

    @Test
    fun incorrectAnswerReturnsInTenMinutesAndResetsSuccessfulSequence() {
        val mature = Review(due = now, interval = 30.0, ease = 2.5, repetitions = 5, lapses = 2)

        val failed = SpacedRepetition.next(mature, quality = 1, now = now)

        assertEquals(now + 10 * 60_000L, failed.due)
        assertEquals(0.0, failed.interval, 0.0)
        assertEquals(0, failed.repetitions)
        assertEquals(3, failed.lapses)
        assertTrue(failed.ease < mature.ease)
    }

    @Test
    fun successAfterFailureStartsAtOneDayAndPreservesLapseHistory() {
        val failed = SpacedRepetition.next(Review(repetitions = 4, interval = 12.0, lapses = 3), 0, now)

        val recovered = SpacedRepetition.next(failed, 4, failed.due)

        assertEquals(failed.due + dayMillis, recovered.due)
        assertEquals(1.0, recovered.interval, 0.0)
        assertEquals(1, recovered.repetitions)
        assertEquals(4, recovered.lapses)
    }

    @Test
    fun firstTwoSpacedSuccessesScheduleOneThenSixDays() {
        val first = SpacedRepetition.next(Review(), 4, now)
        val second = SpacedRepetition.next(first, 4, first.due)

        assertEquals(now + dayMillis, first.due)
        assertEquals(first.due + 6 * dayMillis, second.due)
        assertEquals(2, second.repetitions)
        assertEquals(0, second.lapses)
    }

    @Test
    fun matureRecallSchedulesHardAnswersBeforeNormalAndEasyAnswers() {
        val mature = Review(due = now, interval = 12.0, ease = 2.5, repetitions = 4)
        val hard = SpacedRepetition.next(mature, 3, now)
        val normal = SpacedRepetition.next(mature, 4, now)
        val easy = SpacedRepetition.next(mature, 5, now)

        assertTrue(hard.due > now)
        assertTrue(hard.due < normal.due)
        assertTrue(normal.due < easy.due)
        assertTrue(hard.ease < normal.ease)
        assertTrue(normal.ease < easy.ease)
        assertEquals(5, hard.repetitions)
    }

    @Test
    fun repeatedLapsesNeverReduceEaseBelowMinimum() {
        var review = Review()
        repeat(30) { review = SpacedRepetition.next(review, 0, now + it * 600_000L) }

        assertEquals(1.3, review.ease, 0.0)
        assertEquals(30, review.lapses)
        assertEquals(0, review.repetitions)
        assertTrue(review.due > now)
    }

    @Test
    fun qualityOutsideZeroThroughFiveIsRejected() {
        for (quality in listOf(Int.MIN_VALUE, -1, 6, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) {
                SpacedRepetition.next(Review(), quality, now)
            }
        }
    }

    @Test
    fun todayActivityCountsConsecutiveDaysThroughToday() {
        val today = LocalDate.of(2026, 10, 3)
        val days = setOf("2026-10-03", "2026-10-02", "2026-10-01", "2026-09-29")

        assertEquals(3, LearningRules.streak(days, today))
    }

    @Test
    fun yesterdayActivityKeepsStreakAliveBeforeTodaysLesson() {
        val today = LocalDate.of(2026, 10, 3)
        val days = setOf("2026-10-02", "2026-10-01", "2026-09-30")

        assertEquals(3, LearningRules.streak(days, today))
    }

    @Test
    fun aMissedYesterdayBreaksStreakEvenWithOlderOrFutureActivity() {
        val today = LocalDate.of(2026, 10, 3)

        assertEquals(0, LearningRules.streak(setOf("2026-10-01", "2026-09-30", "2026-10-04"), today))
        assertEquals(0, LearningRules.streak(emptySet(), today))
    }

    @Test
    fun parisDaylightSavingTransitionsDoNotBreakCalendarDayStreaks() {
        val paris = ZoneId.of("Europe/Paris")
        val spring = LocalDate.of(2026, 3, 29)
        val autumn = LocalDate.of(2026, 10, 25)
        // These real calendar days contain 23 and 25 hours; neither is a missing study day.
        assertEquals(23L, Duration.between(spring.atStartOfDay(paris), spring.plusDays(1).atStartOfDay(paris)).toHours())
        assertEquals(25L, Duration.between(autumn.atStartOfDay(paris), autumn.plusDays(1).atStartOfDay(paris)).toHours())

        for (transition in listOf(spring, autumn)) {
            val days = (-1L..1L).map { transition.plusDays(it).toString() }.toSet()
            assertEquals(3, LearningRules.streak(days, transition.plusDays(1)))
            assertEquals(3, LearningRules.streak(days, transition.plusDays(2)))
        }
    }

    @Test
    fun numericAnswersAcceptFrenchDecimalCommaWhitespaceAndScientificNotation() {
        assertTrue(LearningRules.numericCorrect("  1,25  ", 1.25, .001))
        assertTrue(LearningRules.numericCorrect("1.25", 1.25, .001))
        assertTrue(LearningRules.numericCorrect("2,5e-3", .0025, .00001))
        assertTrue(LearningRules.numericCorrect("-12,5", -12.5, .001))
    }

    @Test
    fun malformedAndNonFiniteNumericAnswersAreRejected() {
        for (input in listOf("", " ", "abc", "1,2,3", "12 V", "NaN", "Infinity", "+Infinity", "-Infinity", "1e999")) {
            assertFalse("Invalid numeric answer accepted: $input", LearningRules.numericCorrect(input, 12.0, .1))
        }
    }

    @Test
    fun numericToleranceIncludesBoundaryAndRejectsValuesJustOutside() {
        // Binary-exact values avoid making a boundary assertion depend on decimal rounding.
        assertTrue(LearningRules.numericCorrect("8.125", 8.0, .125))
        assertTrue(LearningRules.numericCorrect("7.875", 8.0, .125))
        assertFalse(LearningRules.numericCorrect("8.12501", 8.0, .125))
        assertFalse(LearningRules.numericCorrect("7.87499", 8.0, .125))
    }

    @Test
    fun zeroToleranceStillAccommodatesFloatingPointNoise() {
        assertTrue(LearningRules.numericCorrect("1.000000001", 1.0, 0.0))
        assertFalse(LearningRules.numericCorrect("1.00000002", 1.0, 0.0))
    }

    @Test
    fun examRequiresTenCorrectAnswersInEachSectionNotOnlyACombinedTotal() {
        assertTrue(LearningRules.examPassed(10, 10))
        assertTrue(LearningRules.examPassed(20, 20))
        assertFalse(LearningRules.examPassed(9, 20))
        assertFalse(LearningRules.examPassed(20, 9))
        assertFalse(LearningRules.examPassed(9, 11))
        assertFalse(LearningRules.examPassed(0, 0))
    }
}
