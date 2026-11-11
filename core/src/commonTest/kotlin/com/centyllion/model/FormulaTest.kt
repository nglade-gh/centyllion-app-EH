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
            Field(0, formula = "field(1, x, y)"),
            Field(1),
            Field(2, formula = "field(1, x, y)"),
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
    fun testPredicateFormulaValidation() {
        // valid logical predicates
        assertNull(validatePredicateFormula("count(0) >= 3"))
        assertNull(validatePredicateFormula("count(0) >= 3 && count(0) < 5"))
        assertNull(validatePredicateFormula("step >= 10"))
        assertNull(validatePredicateFormula("true"))
        assertNull(validatePredicateFormula("x % 2 == 0"))
        assertNull(validatePredicateFormula("!(count(0) > 2)"))
        // non-logical formulas are rejected
        assertTrue(validatePredicateFormula("sin(step)")!!.contains("logical predicate"))
        assertTrue(validatePredicateFormula("count(0)")!!.contains("logical predicate"))
        assertTrue(validatePredicateFormula("step")!!.contains("logical predicate"))
        assertTrue(validatePredicateFormula("current * 0.9")!!.contains("logical predicate"))
        // field/agent are not available in behaviour formulas
        assertTrue(validatePredicateFormula("field(0, x, y) > 0.1")!!.contains("not available"))
        assertTrue(validatePredicateFormula("agent(x, y) == 0")!!.contains("not available"))
        // syntax errors are still reported
        assertEquals("Variable 'toto' doesn't exist", validatePredicateFormula("toto >= 3"))
    }

    @Test
    fun testNumericFormulaValidation() {
        // valid numeric formulas
        assertNull(validateNumericFormula("current * 0.9"))
        assertNull(validateNumericFormula("field(0, x, y)"))
        assertNull(validateNumericFormula("count(0)"))
        assertNull(validateNumericFormula("count(0) > 2 ? current * 0.95 : current"))
        // boolean formulas are rejected
        assertTrue(validateNumericFormula("count(0) >= 3")!!.contains("return a number"))
        assertTrue(validateNumericFormula("current > 0.1")!!.contains("return a number"))
        // syntax errors are still reported
        assertEquals("Variable 'toto' doesn't exist", validateNumericFormula("toto * 2"))
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

    @Test
    fun testCountInFieldFormula() {
        // a field formula using count() sees the neighbour agents
        val model = GrainModel(
            grains = listOf(Grain(id = 0, name = "A")),
            fields = listOf(Field(id = 0, name = "F", formula = "count(0) * 0.1"))
        )
        // 10x10 grid: only cell (x=1, y=1) has an agent 0
        val simulation = createSimulation(size = 10) { if (it == 11) 0 else -1 }
        val simulator = Simulator(model, simulation)
        simulator.oneStep()
        val f = simulator.fields[0]!!
        // the 8 cells around (1,1) see one agent
        listOf(0, 1, 2, 10, 12, 20, 21, 22).forEach { index ->
            assertEquals(0.1f, f[index], 1e-6f)
        }
        // the agent's own cell and far cells see no agent
        assertEquals(0f, f[11], 1e-14f)
        assertEquals(0f, f[50], 1e-14f)
    }

    @Test
    fun testPositionInBehaviourFormula() {
        // a behaviour formula can use x, y of the cell position
        val model = GrainModel(
            grains = listOf(Grain(id = 0, name = "A"), Grain(id = 1, name = "B")),
            behaviours = listOf(
                Behaviour(
                    name = "Transform on even x",
                    formula = "x % 2 == 0",
                    mainReactiveId = 0,
                    mainProductId = 1,
                    probability = 1.0
                )
            )
        )
        // full 10x10 grid of agents 0
        val simulation = createSimulation(size = 10) { 0 }
        val simulator = Simulator(model, simulation)
        simulator.oneStep()
        for (y in 0 until 10) {
            for (x in 0 until 10) {
                val expected = if (x % 2 == 0) 1 else 0
                assertEquals(expected, simulator.agents[y * 10 + x], "cell ($x,$y)")
            }
        }
    }
}
