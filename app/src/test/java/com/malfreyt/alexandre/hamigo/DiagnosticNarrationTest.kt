package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test

class DiagnosticNarrationTest {
    private val energy=Lesson("energy-stable","Énergie stockée","Condensateurs et bobines",listOf("E = ½LI²"),"E = ½LI²","Électricité",emptyList(),
        narration=listOf("L’énergie vaut un demi fois l’inductance fois l’intensité au carré."))
    @Test fun introductionSearchIncludesOriginalFormulaSpokenVersionLocationAndStableIdentity() {
        val entries=NarrationAudit.entries(listOf(Chapter("chapter-stable","Circuits","",listOf(energy))))
        for(query in listOf("½LI²","inductance","energie circuits","energy-stable"))assertEquals(query,energy, NarrationAudit.search(entries,query).single().lesson)
        assertTrue(NarrationAudit.search(entries,"introuvable").isEmpty())
        assertEquals(1,NarrationAudit.search(entries,"","formula").size)
        assertTrue(NarrationAudit.search(entries,"","warning").isEmpty())
    }
    @Test fun auditDistinguishesRawRisksFromActuallyPreparedTextWithoutInventingErrors() {
        assertTrue(NarrationAudit.warnings("E = ½LI²","E égale LI au carré").isNotEmpty())
        assertTrue(NarrationAudit.warnings("E = ½LI²",FrenchSpeech.prepare("E = ½LI²")).isEmpty())
        assertTrue(NarrationAudit.warnings("Γ = (RL−Z0)/(RL+Z0)","gamma pause entre mots").any {it.contains("Morse")})
        assertTrue(NarrationAudit.warnings("51 volts","51 volts").isEmpty())
    }
}
