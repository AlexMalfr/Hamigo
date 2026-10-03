package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test

class ResourceMathTest {
    @Test fun frenchDecimalsAcceptFiniteValuesAndRejectInvalidInputs() {
        assertEquals(4.7, ResourceMath.number("4,7")!!, 1e-9)
        assertNull(ResourceMath.number("NaN"))
        assertNull(ResourceMath.number("Infinity"))
        assertNull(ResourceMath.positive("0"))
        assertNull(ResourceMath.positive("-2"))
    }

    @Test fun resistanceRoundTripRetainsFourAndFiveBandValues() {
        val four = ResourceMath.encodeResistance(4700.0, 2)!!
        assertEquals(listOf(4, 7), four.digits)
        assertEquals(2, four.exponent)
        assertEquals(4700.0, four.value, 1e-9)
        val five = ResourceMath.encodeResistance(1230.0, 3)!!
        assertEquals(listOf(1, 2, 3), five.digits)
        assertEquals(1, five.exponent)
        assertEquals(1230.0, five.value, 1e-9)
        assertNull(ResourceMath.encodeResistance(.0001, 2))
        assertNull(ResourceMath.encodeResistance(Double.POSITIVE_INFINITY, 2))
    }

    @Test fun resistanceRoundingCarriesIntoTheNextMultiplier() {
        val code = ResourceMath.encodeResistance(999.9, 2)!!
        assertEquals(listOf(1, 0), code.digits)
        assertEquals(2, code.exponent)
        assertEquals(1000.0, code.value, 1e-9)
    }

    @Test fun decibelPowerAndVoltageUseTheirOwnFactorsAndInvert() {
        assertEquals(3.0102999566, ResourceMath.decibels(2.0), 1e-8)
        assertEquals(6.0205999133, ResourceMath.decibels(2.0, true), 1e-8)
        listOf(-30.0, -3.0, 0.0, 3.0, 20.0).forEach { db ->
            assertEquals(db, ResourceMath.decibels(ResourceMath.ratio(db)), 1e-8)
            assertEquals(db, ResourceMath.decibels(ResourceMath.ratio(db, true), true), 1e-8)
        }
    }

    @Test fun parallelResistanceRemainsBelowEachBranch() {
        assertEquals(50.0, ResourceMath.parallel(listOf(100.0, 100.0)), 1e-9)
        assertEquals(60.0, ResourceMath.parallel(listOf(100.0, 150.0)), 1e-9)
    }

    @Test fun morseTranslatorPreservesWordAndLetterSeparationBothWays() {
        val encoded = MorseReference.encode("CQ HAMIGO 73")
        assertTrue(encoded.unsupported.isEmpty())
        assertEquals("CQ HAMIGO 73", MorseReference.decode(encoded.output).output)
        assertEquals("SOS", MorseReference.decode("••• ——— •••").output)
        assertEquals("ECOLE", MorseReference.decode(MorseReference.encode("école").output).output)
        assertEquals(listOf("💡"), MorseReference.encode("💡").unsupported)
        assertEquals(listOf("......."), MorseReference.decode(".......").unsupported)
    }
}
