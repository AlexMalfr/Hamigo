package com.malfreyt.alexandre.hamigo

import com.malfreyt.alexandre.hamigo.platform.ShareProgress
import org.junit.Assert.*
import org.junit.Test

class FriendPathProgressTest {
    private val old=ShareProgress("Camille",501,3,7)
    @Test fun absentAndPartialPathDataNeverBorrowTotalsFromTheLocalCatalogue() {
        assertNull(friendPathSectionLabel(old));assertNull(friendPathLessonLabel(old))
        assertEquals("Section 3",friendPathSectionLabel(old.copy(pathSectionNumber=3)))
        assertEquals("Les premières ondes",friendPathSectionLabel(old.copy(pathSectionTitle="Les premières ondes")))
        assertNull(friendPathLessonLabel(old.copy(pathTotalLessons=140)))
        assertEquals("7 cours validés",friendPathLessonLabel(old.copy(pathCompletedLessons=7)))
    }
    @Test fun thePublishedSectionAndCountAreRenderedTogether() {
        val profile=old.copy(pathSectionNumber=3,pathSectionTitle="Les premières ondes",pathCompletedLessons=7,pathTotalLessons=140)
        assertEquals("Section 3 · Les premières ondes",friendPathSectionLabel(profile))
        assertEquals("7 / 140 cours validés",friendPathLessonLabel(profile))
    }
}
