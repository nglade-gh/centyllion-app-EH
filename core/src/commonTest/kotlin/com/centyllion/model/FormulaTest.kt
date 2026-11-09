package com.centyllion.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FormulaTest {

    val model = GrainModel(
        grains = listOf(
            Grain(
                id = 0,
                name = "Grain",
                movementProbability = 0.0,
                fieldProductions = mapOf(1 to 1f)
            )
        ),
        fields = listOf(
            Field(0, formula = "field1(x,y)"),
            Field(1),
            Field(2, formula = "field1(x,y)"),
            Field(3, formula = "agent(x,y) == 0 ? 1 : 0")
        ),
    )

    @Test
    fun testFormulaCheck() {
        assertNull(validateFormula("field(0, x, y)"))
        assertNull(validateFormula("agent(x, y)"))
        assertNull(validateFormula( "step"))
        assertNull(validateFormula( "count(0)"))
        assertEquals("Variable 'toto' doesn't exist", validateFormula("toto"))
        assertEquals("Variable 'toto' doesn't exist", validateFormula("0.0 + toto"))
        assertTrue(validateFormula("agent +")!!.contains("Expecting"))
        assertTrue(validateFormula("agent )")!!.contains("Expecting"))
    }


    @Test
    fun testSimulation() {
        val simulation = createSimulation(size = 10) { if (it == 0) 0 else -1 }
        val simulator = Simulator(model, simulation)
        repeat(2) {
            simulator.oneStep()
            assertEquals(simulator.fields[0]?.toList(), simulator.fields[2]?.toList())
            assertEquals(1f, simulator.fields[3]?.get(0))

            println("----")
            simulator.fields.entries.sortedBy { it.key }.forEach { (_, f) ->
                println(f.toList())
            }
        }
    }
}