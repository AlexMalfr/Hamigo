package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test

class RadioReferenceContentTest {
    private val shared = RefRow("Statut C", "Secondaire", "")
    private val region1 = RefRow("40 m", "7,000–7,200 MHz", "", region = "1")
    private val region2 = RefRow("40 m · région 2", "7,000–7,300 MHz", "", region = "2")
    private val region3 = RefRow("40 m · région 3", "7,000–7,200 MHz", "", region = "3")
    private val rows = listOf(shared, region1, region2, region3)

    @Test fun regionSelectionKeepsSharedFactsAndOnlyItsOwnAllocations() {
        assertEquals(listOf(shared, region1), bandRowsForRegion(rows, "1"))
        assertEquals(listOf(shared, region2), bandRowsForRegion(rows, "2"))
        assertEquals(listOf(shared, region3), bandRowsForRegion(rows, "3"))
    }

    @Test fun invalidRegionFallsBackToRegionOneRatherThanMixingAllBoundaries() {
        assertEquals(listOf(shared, region1), bandRowsForRegion(rows, "CQ 14"))
    }

    @Test fun onlyIndexedRadioDiagramsAreRecognized() {
        assertTrue(hasRadioReferenceDiagram(shared.copy(visual = "report-r")))
        assertTrue(hasRadioReferenceDiagram(shared.copy(visual = "emission-code")))
        assertFalse(hasRadioReferenceDiagram(shared.copy(visual = "unknown")))
    }

    @Test fun mapShortcutDoesNotConfuseAdministrativeRulesWithGeographicPrefixes() {
        assertEquals("Guyane française", radioCallsignMapQuery(RefRow("FY", "Guyane française", "")))
        assertEquals("Croatie", radioCallsignMapQuery(RefRow("9A", "Croatie", "", group = "Préfixes internationaux du cours")))
        assertNull(radioCallsignMapQuery(RefRow("FX", "Satellite français", "", group = "Préfixes français")))
        assertNull(radioCallsignMapQuery(RefRow("T/R 61-01", "Licence CEPT", "", group = "Suffixes et structure")))
    }
}
