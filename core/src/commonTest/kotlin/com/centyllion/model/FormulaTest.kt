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
            Field(0, formula = "field(1)"),
            Field(1),
            Field(2, formula = "field(1)"),
            Field(3, formula = "agent(x,y) == 0 ? 1 : 0")
        ),
    )

    @Test
    fun testFormulaCheck() {
        assertNull(validateFormula("field(0)"))
        assertNull(validateFormula("field(0, 1, 0)"))
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
        // field/agent are usable in behaviour formulas (simulator state is available)
        assertNull(validatePredicateFormula("field(0) > 0.1"))
        assertNull(validatePredicateFormula("agent(x, y) == 0"))
        assertNull(validatePredicateFormula("count(0) > 3 && field(1) < 0.5"))
        // syntax errors are still reported
        assertEquals("Variable 'toto' doesn't exist", validatePredicateFormula("toto >= 3"))
    }

    @Test
    fun testNumericFormulaValidation() {
        // valid numeric formulas
        assertNull(validateNumericFormula("current * 0.9"))
        assertNull(validateNumericFormula("field(0)"))
        assertNull(validateNumericFormula("count(0)"))
        assertNull(validateNumericFormula("count(0) > 2 ? current * 0.95 : current"))
        // boolean formulas are rejected
        assertTrue(validateNumericFormula("count(0) >= 3")!!.contains("return a number"))
        assertTrue(validateNumericFormula("current > 0.1")!!.contains("return a number"))
        // syntax errors are still reported
        assertEquals("Variable 'toto' doesn't exist", validateNumericFormula("toto * 2"))
    }

    @Test
    fun testTernaryBranchTypeValidation() {
        // same-type branches are accepted
        assertNull(validateNumericFormula("step > 0 ? current * 0.9 : current"))
        assertNull(validatePredicateFormula("step > 0 ? true : false"))
        assertNull(validatePredicateFormula("count(0) > 2 ? true : count(0) < 5"))
        // mixed branches are rejected
        assertTrue(validateNumericFormula("step > 0 ? current * 0.9 : false")!!.contains("same type"))
        assertTrue(validatePredicateFormula("step > 0 ? 1 : false")!!.contains("same type"))
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

    @Test
    fun testAgentFunctionInBehaviourFormula() {
        // agent(x, y) in a behaviour formula: convert to B only when the south
        // neighbour (y+1) holds an agent 0
        val model = GrainModel(
            grains = listOf(Grain(id = 0, name = "A", halfLife = 0), Grain(id = 1, name = "B", halfLife = 0)),
            behaviours = listOf(
                Behaviour(
                    name = "Convert if south neighbour is A",
                    formula = "agent(x, y+1) == 0",
                    mainReactiveId = 0,
                    mainProductId = 1,
                    probability = 1.0
                )
            )
        )
        // agents 0 at (0,0) and (0,1) on a 10x10 grid
        val simulation = createSimulation(size = 10) { if (it == 0 || it == 10) 0 else -1 }
        val simulator = Simulator(model, simulation)
        simulator.oneStep()
        // (0,0): south neighbour (0,1) is A -> converted to B
        assertEquals(1, simulator.agents[0])
        // (0,1): south neighbour (0,2) is empty -> stays A
        assertEquals(0, simulator.agents[10])
        // an empty cell never holds a converted agent
        assertEquals(-1, simulator.agents[20])
    }

    @Test
    fun testFieldFunctionInBehaviourFormula() {
        // field(id) in a behaviour formula: convert when the field on the cell
        // is above 0.5; the field reaches 1.0 after one step (formula "1.0")
        val model = GrainModel(
            grains = listOf(Grain(id = 0, name = "A", halfLife = 0), Grain(id = 1, name = "B", halfLife = 0)),
            fields = listOf(Field(id = 0, name = "F", formula = "1.0")),
            behaviours = listOf(
                Behaviour(
                    name = "Convert above field threshold",
                    formula = "field(0) > 0.5",
                    mainReactiveId = 0,
                    mainProductId = 1,
                    probability = 1.0
                )
            )
        )
        val simulation = createSimulation(size = 10) { if (it == 0) 0 else -1 }
        val simulator = Simulator(model, simulation)
        // step 1: the field is still at its initial value -> no conversion
        simulator.oneStep()
        assertEquals(0, simulator.agents[0])
        // step 2: the field has reached 1.0 -> conversion
        simulator.oneStep()
        assertEquals(1, simulator.agents[0])
    }

    @Test
    fun testFieldOffsetInBehaviourFormula() {
        // field(id, dx, dy) in a behaviour formula: offset from the current cell
        val model = GrainModel(
            grains = listOf(Grain(id = 0, name = "A", halfLife = 0), Grain(id = 1, name = "B", halfLife = 0)),
            fields = listOf(
                Field(id = 0, name = "East", formula = "agent(x+1, y) == 0 ? 1 : 0"),
                Field(id = 1, name = "West", formula = "agent(x-1, y) == 0 ? 1 : 0")
            ),
            behaviours = listOf(
                Behaviour(
                    name = "Convert facing east",
                    formula = "field(0) > 0.5 && field(1) < 0.5",
                    mainReactiveId = 0,
                    mainProductId = 1,
                    probability = 1.0
                )
            )
        )
        // agents 0 at (1,0) and (2,0) on a 10x10 grid
        val simulation = createSimulation(size = 10) { if (it == 1 || it == 2) 0 else -1 }
        val simulator = Simulator(model, simulation)
        // step 1: fields still at their initial values -> no conversion
        simulator.oneStep()
        assertEquals(0, simulator.agents[1])
        assertEquals(0, simulator.agents[2])
        // step 2: field 0 is 1 only on cells with an agent to the east,
        // field 1 only on cells with an agent to the west.
        // (1,0): east neighbour (2,0) is A and no west neighbour -> converts
        // (2,0): west neighbour (1,0) is A and no east neighbour -> does not convert
        simulator.oneStep()
        assertEquals(1, simulator.agents[1])
        assertEquals(0, simulator.agents[2])
    }
}
