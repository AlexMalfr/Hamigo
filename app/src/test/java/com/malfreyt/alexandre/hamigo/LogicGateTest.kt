package com.malfreyt.alexandre.hamigo

import org.junit.Assert.assertEquals
import org.junit.Test

class LogicGateTest {
    @Test fun sandboxMatchesAllSixTruthTables() {
        val expected = mapOf(
            LogicGate.AND to listOf(false,false,false,true),
            LogicGate.OR to listOf(false,true,true,true),
            LogicGate.NOT to listOf(true,true,false,false),
            LogicGate.NAND to listOf(true,true,true,false),
            LogicGate.NOR to listOf(true,false,false,false),
            LogicGate.XOR to listOf(false,true,true,false)
        )
        for ((gate, values) in expected) for (pair in 0..3)
            assertEquals("${gate.label}, A=${pair/2}, B=${pair%2}", values[pair], gate.output(pair/2==1,pair%2==1))
    }
}
