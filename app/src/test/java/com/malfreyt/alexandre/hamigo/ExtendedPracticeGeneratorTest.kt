package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test

class ExtendedPracticeGeneratorTest {
    @Test fun everyVariantIsRecoverableAndAllAnswerContractsAreValid() {
        val catalog=ExtendedPracticeGenerator.catalog()
        assertEquals(5120,catalog.size)
        assertEquals(catalog.size,catalog.map{it.id}.toSet().size)
        catalog.forEach{q ->
            assertEquals(q,ExtendedPracticeGenerator.resolve(q.id))
            when(q.kind) {
                "number","estimate" -> {assertTrue(q.value!!.isFinite());assertTrue(q.tolerance>0)}
                "morseEncode" -> assertTrue(q.bands.single().all{it=='.'||it=='-'})
                "binary" -> assertTrue(q.value!! in 0.0..15.0)
                "multiselect" -> assertTrue(q.bands.map{it.toInt()}.all{it in q.choices.indices})
                "match" -> assertTrue(q.pairs.isNotEmpty())
                "order" -> assertEquals(q.choices.size,q.choices.toSet().size)
                "waveform" -> {assertEquals(q.choices.size,q.bands.size);assertTrue(q.answer in q.choices.indices)}
                else -> assertTrue(q.answer in q.choices.indices)
            }
        }
        assertNull(ExtendedPracticeGenerator.resolve("extra--1-2"))
        assertNull(ExtendedPracticeGenerator.resolve("extra-256-0"))
    }

    @Test fun longMixHasNoDuplicateIdsAndASeedReproducesTheSession() {
        val first=ExtendedPracticeGenerator.create(125L,150)
        assertEquals(150,first.size)
        assertEquals(150,first.map{it.id}.toSet().size)
        assertEquals(first,ExtendedPracticeGenerator.create(125L,150))
        assertNotEquals(first,ExtendedPracticeGenerator.create(126L,150))
    }

    @Test fun generatedAssociationsFollowElectricalRelationships() {
        ExtendedPracticeGenerator.catalog().filter{it.id.endsWith("-0")||it.id.endsWith("-1")}.forEach { q ->
            val values=Regex("(\\d+) Ω").findAll(q.prompt).map{it.groupValues[1].toDouble()}.toList()
            assertEquals(2,values.size)
            val expected=if(q.id.endsWith("-0"))values.sum()else values[0]*values[1]/values.sum()
            assertEquals(expected,q.value!!,1e-10)
        }
    }

    @Test fun morseLessonExtrasNeverTestAnUntaughtSymbol() {
        val lesson=Lesson("morse-test","Les premiers signes morse","",emptyList(),"","Morse",listOf(
            Question("morse-q","Relie",emptyList(),0,"",kind="match",pairs=listOf(PairItem("E","."),PairItem("T","-"),PairItem("A",".-"),PairItem("N","-.")))
        ))
        val taught=setOf(".","-",".-","-.")
        repeat(20){seed ->
            val extras=ExtendedPracticeGenerator.lessonExtras(lesson,seed.toLong())
            assertEquals(2,extras.size)
            assertTrue(extras.all{it.kind in setOf("morseListen","morseEncode")&&it.bands.first() in taught})
        }
        assertTrue(ExtendedPracticeGenerator.lessonExtras(lesson.copy(topic="",title="Introduction",questions=emptyList()),0).isEmpty())
    }
}
