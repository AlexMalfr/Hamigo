package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test

class DailyGoalsTest {
    @Test fun fourExistingChoicesKeepTheirOrderAndStableStoredKeys() {
        assertEquals(listOf(20,30,60,100),DailyGoals.keys)
        assertEquals(listOf(30,60,120,240),DailyGoals.keys.map(DailyGoals::xp))
        assertEquals(DailyGoals.keys,DailyGoals.values.map(DailyGoals::key))
    }
    @Test fun nonPresetBackupValuesRemainNumericRatherThanBeingAssignedToAnotherTier() {
        assertEquals(45,DailyGoals.xp(45));assertEquals(75,DailyGoals.xp(75))
    }
}
