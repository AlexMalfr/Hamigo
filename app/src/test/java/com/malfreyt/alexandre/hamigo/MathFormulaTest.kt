package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test

class MathFormulaTest {
    @Test fun recognisedExamAnswersKeepTheirStructureWithoutCorrectingDistractors() {
        val source=listOf("Imax = Ieff / √ 2","P = √ (R x I)","P = U² / R","W = R x I x t")
        val formulas=source.map {requireNotNull(MathFormula.parse(it))}
        val tex=formulas.map(MathFormula::latex)
        assertTrue(tex[0].contains("_{\\mathrm{max}}"));assertTrue(tex[0].contains("\\frac"));assertTrue(tex[0].contains("\\sqrt"))
        assertTrue(tex[1].contains("\\sqrt{R \\times I}"))
        assertTrue(tex[2].contains("\\frac{{U}^{\\mathrm{2}}}{R}"))
        assertTrue(tex[3].contains("R \\times I \\times t"))
        assertEquals(Formula.Join(Formula.Atom("P"),"=",Formula.Fraction(Formula.Power(Formula.Atom("U"),Formula.Atom("2",false)),Formula.Atom("R"))),formulas[2])
    }
    @Test fun groupingAndImplicitProductsPreserveWhereTheExponentApplies() {
        val formula=requireNotNull(MathFormula.parse("W = RI²t")) as Formula.Join
        assertEquals(Formula.Join(Formula.Join(Formula.Atom("R"),"",Formula.Power(Formula.Atom("I"),Formula.Atom("2",false))),"",Formula.Atom("t")),formula.right)
        assertNotNull(MathFormula.parse("f1 = (18 - 10) / 2"))
    }
    @Test fun unknownNotationAndProseStayUntouched() {
        listOf("Cette réponse = une formule inconnue","300 MHz","P =","P = sin(U)","U/R","P = U²/R = RI²").forEach {assertNull(it,MathFormula.parse(it))}
    }
    @Test fun equationsInsideAPromptKeepAllTheSurroundingText() {
        val source="Pour R = 10 Ω et I = 2 A, calcule U. Puis utilise P = U² / R."
        val fragments=MathFormula.fragments(source)
        assertEquals(source,fragments.joinToString(""){it.source})
        assertEquals(listOf("R = 10 Ω","I = 2 A","P = U² / R"),fragments.filter {it.formula!=null}.map {it.source})
        assertTrue(MathFormula.fragments("La formule P = sin(U) est inconnue ici.").none {it.formula!=null})
    }
}
