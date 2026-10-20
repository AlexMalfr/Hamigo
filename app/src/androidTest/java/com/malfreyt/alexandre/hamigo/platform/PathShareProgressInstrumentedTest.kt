package com.malfreyt.alexandre.hamigo.platform

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PathShareProgressInstrumentedTest {
    @Test fun oldSummaryKeepsItsUnknownPathInsteadOfInventingACataloguePosition() {
        val json="""{"schema":2,"name":"Camille","xp":501,"streak":3,"lessons":7,"updatedAt":"2026-10-18T08:00:00Z"}"""
        val old=ShareProgress.fromJson(json)
        assertNull(old.pathSectionNumber);assertNull(old.pathSectionTitle)
        assertNull(old.pathCompletedLessons);assertNull(old.pathTotalLessons)
        val roundTrip=JSONObject(old.toJson())
        listOf("pathSectionNumber","pathSectionTitle","pathCompletedLessons","pathTotalLessons").forEach {assertFalse(roundTrip.has(it))}
        assertEquals(old,ShareProgress.fromJson(old.toJson()))
    }

    @Test fun currentPathRoundTripsWithoutPublishingLessonIdentities() {
        val profile=ShareProgress("Camille",501,3,7,pathSectionNumber=2,pathSectionTitle="Comprendre les circuits",pathCompletedLessons=7,pathTotalLessons=140)
        val json=profile.toJson()
        assertEquals(profile,ShareProgress.fromJson(json))
        assertFalse(json.contains("completed\""));assertFalse(json.contains("lessonIds"));assertFalse(json.contains("skillIds"))
        // Missing one field remains missing; readers can render a genuinely partial summary.
        val partial=ShareProgress.fromJson(JSONObject(json).apply {remove("pathSectionNumber");remove("pathTotalLessons")}.toString())
        assertNull(partial.pathSectionNumber);assertNull(partial.pathTotalLessons)
        assertEquals("Comprendre les circuits",partial.pathSectionTitle);assertEquals(7,partial.pathCompletedLessons)
    }

    @Test fun malformedPathDataCannotForgeAProgression() {
        val valid=ShareProgress("Camille",501,3,7,pathSectionNumber=0,pathSectionTitle="Introduction",pathCompletedLessons=7,pathTotalLessons=140).toJson()
        assertEquals(0,ShareProgress.fromJson(valid).pathSectionNumber)
        listOf(
            JSONObject(valid).put("pathSectionNumber",-1),
            JSONObject(valid).put("pathSectionNumber",2.5),
            JSONObject(valid).put("pathSectionTitle",42),
            JSONObject(valid).put("pathSectionTitle","  "),
            JSONObject(valid).put("pathSectionTitle","x".repeat(121)),
            JSONObject(valid).put("pathCompletedLessons",-1),
            JSONObject(valid).put("pathCompletedLessons",141),
            JSONObject(valid).put("pathTotalLessons",0)
        ).forEach {bad->assertThrows(Exception::class.java){ShareProgress.fromJson(bad.toString())}}
    }
}
