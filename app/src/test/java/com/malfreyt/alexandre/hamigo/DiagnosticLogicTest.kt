package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test

class DiagnosticLogicTest {
    @Test fun accessIsAnExactTypedCodeNotAnEquivalentCalculation() {
        assertTrue(DiagnosticAccess.matches("7388 7388 2026",2026))
        assertTrue(DiagnosticAccess.matches(" 738873882026 ",2026))
        listOf("738873882026.0","738873882025+1","7.38873882026E11","7388738820260","").forEach {assertFalse(DiagnosticAccess.matches(it,2026))}
    }
    @Test fun accessFollowsTheDeviceCalendarYearWithoutRetainingThePreviousCode() {
        assertEquals("738873882026",DiagnosticAccess.codeForYear(2026))
        assertEquals("738873882027",DiagnosticAccess.codeForYear(2027))
        assertTrue(DiagnosticAccess.matches("7388 7388 2027",2027))
        assertFalse(DiagnosticAccess.matches("738873882026",2027))
        assertFalse(DiagnosticAccess.matches("738873882027",2026))
        assertEquals(DiagnosticAccess.codeForYear(java.time.LocalDate.now().year),DiagnosticAccess.CODE)
    }
    @Test fun sliderDetentsFollowTheScaleWithoutChatteringAtTheSameValue() {
        assertNull(sliderCue(143f,143f,143f,148f,100))
        assertNull(sliderCue(145f,145.001f,143f,148f,100))
        assertEquals(FeedbackCue.SLIDER_TICK,sliderCue(145f,145.05f,143f,148f,100))
        assertEquals(FeedbackCue.SLIDER_MARK,sliderCue(145.45f,145.5f,143f,148f,100))
        assertEquals(FeedbackCue.SLIDER_EDGE,sliderCue(147.95f,148f,143f,148f,100))
        assertNull(sliderCue(1f,2f,0f,0f,100))
        val throttle=FeedbackThrottle()
        assertTrue(throttle.allow(FeedbackCue.SLIDER_TICK,1000));assertFalse(throttle.allow(FeedbackCue.SLIDER_TICK,1020))
        assertTrue(throttle.allow(FeedbackCue.SLIDER_MARK,1040));assertTrue(throttle.allow(FeedbackCue.SLIDER_EDGE,1042))
        assertTrue(throttle.allow(FeedbackCue.SLIDER_RELEASE,1043));assertTrue(throttle.allow(FeedbackCue.ERROR,1043))
        assertNull(FeedbackDesign.sound(FeedbackCue.SLIDER_TICK))
    }
    @Test fun scratchPreferencesHaveTheirOwnStoreAndDefensiveSetCopies() {
        val a=MemoryPreferences();val b=MemoryPreferences();val original=mutableSetOf("a")
        a.edit().putStringSet("set",original).putString("name","Demo").commit();original.add("b")
        assertEquals(setOf("a"),a.getStringSet("set",null));assertNull(b.getString("name",null))
        a.getStringSet("set",null)!!.add("c");assertEquals(setOf("a"),a.getStringSet("set",null))
        a.edit().clear().putInt("xp",12).commit();assertFalse(a.contains("name"));assertEquals(12,a.getInt("xp",0))
    }
    @Test fun catalogSearchKeepsStableIdsAndCanFindAnExamNumberOrType() {
        val q=Question("exam1-20081","Une antenne",listOf("A"),0,"",topic="Propagation")
        val other=q.copy(id="course-1",kind="number",prompt="Calculer une puissance")
        assertEquals(listOf(q),DiagnosticData.search(listOf(q,other),"20081"))
        assertEquals(listOf(q),DiagnosticData.search(listOf(q,other),"antenne propagation"))
        assertEquals(listOf(other),DiagnosticData.search(listOf(q,other),"","number"))
        assertEquals("exam1-20081",q.id)
    }
    @Test fun catalogSearchIncludesEveryQuestionFieldAndCombinesWordsAcrossFields() {
        val q=Question("q-opaque","Question sans indice",listOf("Mégahertz","Kilohertz"),0,"Une correction utile",topic="Radio",
            section="regulation",kind="match",image="diagram.png",value=147.0,unit="MHz",tolerance=.05,
            pairs=listOf(PairItem("Lampe","Récepteur")),source="Une source",bands=listOf("jaune"),visual="logic:and")
        for(query in listOf("kilohertz correction","récepteur radio","diagram.png","147.0","0.05","MHz","logic:and","jaune","regulation match"))
            assertEquals(query,listOf(q),DiagnosticData.search(listOf(q),query))
        assertTrue(DiagnosticData.search(listOf(q),"kilohertz","number").isEmpty())
    }
}
