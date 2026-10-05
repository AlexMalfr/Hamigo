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

    @Test fun editablePowerStagesCalculateMeasurementsAndCascadeWithoutIntermediateRounding() {
        assertEquals(3.0102999566, ResourceMath.powerGain(5.0, 10.0)!!, 1e-8)
        assertEquals(-3.0102999566, ResourceMath.powerGain(10.0, 5.0)!!, 1e-8)
        assertEquals(20.0, ResourceMath.powerAfterGain(10.0, ResourceMath.powerGain(10.0, 20.0)!!)!!, 1e-8)
        val chain = ResourceMath.powerChain(10.0, 6.0, 3.0)!!
        assertEquals(39.8107170553, chain.afterAmplifier, 1e-8)
        assertEquals(19.9526231497, chain.output, 1e-8)
        assertEquals(3.0, chain.totalDb, 1e-9)
        assertEquals(10.0, ResourceMath.powerChain(10.0, 0.0, 0.0)!!.output, 1e-8)
        assertEquals(1.0, ResourceMath.powerChain(10.0, -3.0, 7.0)!!.output, 1e-8)
    }

    @Test fun logarithmicPowerCalculationsRejectInvalidsAndRetainExtremeFiniteRatios() {
        assertEquals(4000.0, ResourceMath.powerGain(1e-200, 1e200)!!, 1e-8)
        assertEquals(1e100, ResourceMath.powerAfterGain(1e-200, 3000.0)!!, 1e86)
        assertNull(ResourceMath.powerGain(0.0, 10.0))
        assertNull(ResourceMath.powerGain(10.0, -1.0))
        assertNull(ResourceMath.powerGain(Double.NaN, 10.0))
        assertNull(ResourceMath.powerAfterGain(Double.POSITIVE_INFINITY, 3.0))
        assertNull(ResourceMath.powerAfterGain(1.0, Double.NaN))
        assertNull(ResourceMath.powerAfterGain(1.0, 4000.0))
        assertNull(ResourceMath.powerAfterGain(1.0, -4000.0))
        assertNull(ResourceMath.powerChain(10.0, 3.0, -1.0))
        assertNull(ResourceMath.powerChain(10.0, 3.0, Double.NaN))
    }

    @Test fun morseTranslatorPreservesWordAndLetterSeparationBothWays() {
        val encoded = MorseReference.encode("CQ HAMIGO 73")
        assertTrue(encoded.unsupported.isEmpty())
        assertEquals("CQ HAMIGO 73", MorseReference.decode(encoded.output).output)
        assertEquals("SOS", MorseReference.decode("••• ——— •••").output)
        assertEquals("ECOLE", MorseReference.decode(MorseReference.encode("école").output).output)
        assertEquals("É", MorseReference.decode("..-..").output)
        assertEquals("\"CQ\"",MorseReference.decode(MorseReference.encode("\"CQ\"").output).output)
        assertEquals(listOf("💡"), MorseReference.encode("💡").unsupported)
        assertEquals(listOf("......."), MorseReference.decode(".......").unsupported)
    }
}
