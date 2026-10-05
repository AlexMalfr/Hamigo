package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class PracticeMixIndexTest {
    private fun question(id: String, topic: String, section: String = "technique") = Question(id, id, listOf("Oui"), 0, "", topic = topic, section = section)
    @Test fun overlappingThemesIncludeEachVariantOnlyOnceAndKeepRegulationSeparate() {
        val index = PracticeMixIndex(listOf(question("a", "Électricité"), question("b", "Courant continu"), question("c", "Règles", "regulation"),question("d","Antennes")), listOf(question("v1", "Loi d'Ohm"), question("v2", "Décibels")))
        val selected = setOf("technique|Électricité", "technique|Courant continu")
        assertEquals(3, index.available(selected))
        assertEquals(setOf("a", "b", "v1"), index.questions(selected, 3, Random(1)).map { it.id }.toSet())
        assertEquals(1, index.available(setOf("regulation|Règles")))
        assertEquals(0, index.available(emptySet()))
        assertEquals(6, index.available(index.keys))
        assertEquals(5, index.available(index.keys.filterTo(mutableSetOf()){it.startsWith("technique|")}))
        assertThrows(IllegalArgumentException::class.java) { index.questions(selected, 4) }
    }
    @Test fun largeMixFillsFromVariantsWhenTheFixedBankIsSmall() {
        val fixed = List(2) { question("f$it", "Électricité") }
        val variants = List(1000) { question("v$it", "Loi d'Ohm") }
        val index = PracticeMixIndex(fixed, variants)
        val questions = index.questions(index.keys, 1000, Random(1))
        assertEquals(1000, questions.size)
        assertEquals(1000, questions.map { it.id }.toSet().size)
        assertEquals(2, questions.count { it.id.startsWith("f") })
    }
}
