package com.centyllion.model

import com.centyllion.i18n.Locales
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ActionTest {

    // ---- parsing & validation ----

    @Test
    fun testActionFormulaValidation() {
        // valid action lists
        assertNull(validateActionFormula("Add(0, 1, 0)"))
        assertNull(validateActionFormula("Add(0, 1, 0), Remove(1, 0, 0)"))
        assertNull(validateActionFormula("Move(0, 0, 0, 1, 0)"))
        assertNull(validateActionFormula("SetField(1, 0, 0, 0.5), ClearField(1, 1, 0)"))
        assertNull(validateActionFormula("AddField(1, 0, 0, 0.1)"))
        assertNull(validateActionFormula(""))
        // arguments can be expressions (variables, nested calls)
        assertNull(validateActionFormula("Add(0, x % 2, 0)"))
        assertNull(validateActionFormula("Add(0, min(1, 2), 0)"))
        assertNull(validateActionFormula("SetField(1, 0, 0, 0.5 + 0.25)"))
        // unknown action
        assertTrue(validateActionFormula("Create(0, 1, 0)")!!.contains("Unknown action"))
        // wrong argument count
        assertTrue(validateActionFormula("Add(0, 1)")!!.contains("expects 3 arguments"))
        assertTrue(validateActionFormula("Move(0, 0, 0, 1)")!!.contains("expects 5 arguments"))
        // non-numeric argument
        assertTrue(validateActionFormula("Add(true, 1, 0)")!!.contains("must be a number"))
        // not an action call
        assertTrue(validateActionFormula("toto")!!.contains("Expected a list of actions"))
    }

    @Test
    fun testActionParse() {
        val actions = "Add(0, 1, 0), Move(1, 0, 0, 0, 1), ClearField(2, 0, 0)".parseActionFormula()
        assertEquals(3, actions.size)
        assertTrue(actions[0] is Action.Add)
        assertTrue(actions[1] is Action.Move)
        assertTrue(actions[2] is Action.ClearField)
    }

    @Test
    fun testActionDiagnose() {
        // invalid actions formula produces a model problem on the "Actions" property
        val model = GrainModel(grains = listOf(Grain(id = 0, name = "A")))
        val behaviour = Behaviour(name = "B", mainReactiveId = 0, actions = "Add(0, 1)")
        val problems = buildList { behaviour.diagnose(model, Locales.default, this) }
        assertTrue(problems.any { it.property == "Actions" })
    }

    @Test
    fun testPredicateProblemProperty() {
        // invalid condition formula produces a model problem on the "Predicate" property
        val model = GrainModel(grains = listOf(Grain(id = 0, name = "A")))
        val behaviour = Behaviour(name = "B", mainReactiveId = 0, formula = "sin(step)")
        val problems = buildList { behaviour.diagnose(model, Locales.default, this) }
        assertTrue(problems.any { it.property == "Predicate" })
    }

    // ---- runtime ----

    @Test
    fun testAddAction() {
        val model = GrainModel(
            grains = listOf(Grain(id = 0, name = "A", halfLife = 0), Grain(id = 1, name = "B", halfLife = 0)),
            behaviours = listOf(
                Behaviour(
                    name = "Add B east",
                    mainReactiveId = 0,
                    mainProductId = 0,
                    probability = 1.0,
                    actions = "Add(1, 1, 0)"
                )
            )
        )
        // a single A at (0,0) on a 10x10 grid
        val simulation = createSimulation(size = 10) { if (it == 0) 0 else -1 }
        val simulator = Simulator(model, simulation)
        simulator.oneStep()
        assertEquals(0, simulator.agents[0])
        assertEquals(1, simulator.agents[1])
        // an occupied target cell is not overwritten
        val model2 = GrainModel(
            grains = listOf(Grain(id = 0, name = "A", halfLife = 0), Grain(id = 1, name = "B", halfLife = 0)),
            behaviours = listOf(
                Behaviour(
                    name = "Add B east",
                    mainReactiveId = 0,
                    mainProductId = 0,
                    probability = 1.0,
                    actions = "Add(1, 0, 1)"
                )
            )
        )
        // A at (0,0), B at (0,1)
        val simulation2 = createSimulation(size = 10) { if (it == 0) 0 else if (it == 10) 1 else -1 }
        val simulator2 = Simulator(model2, simulation2)
        simulator2.oneStep()
        assertEquals(1, simulator2.agents[10])
    }

    @Test
    fun testAddActionToroidal() {
        val model = GrainModel(
            grains = listOf(Grain(id = 0, name = "A", halfLife = 0), Grain(id = 1, name = "B", halfLife = 0)),
            behaviours = listOf(
                Behaviour(
                    name = "Add B east",
                    mainReactiveId = 0,
                    mainProductId = 0,
                    probability = 1.0,
                    actions = "Add(1, 1, 0)"
                )
            )
        )
        // A at (9,0): the east offset wraps to (0,0)
        val simulation = createSimulation(size = 10) { if (it == 9) 0 else -1 }
        val simulator = Simulator(model, simulation)
        simulator.oneStep()
        assertEquals(0, simulator.agents[9])
        assertEquals(1, simulator.agents[0])
    }

    @Test
    fun testRemoveAction() {
        val model = GrainModel(
            grains = listOf(Grain(id = 0, name = "A", halfLife = 0)),
            behaviours = listOf(
                Behaviour(
                    name = "Remove east neighbour",
                    formula = "x == 0 && y == 0",
                    mainReactiveId = 0,
                    mainProductId = 0,
                    probability = 1.0,
                    actions = "Remove(0, 1, 0)"
                )
            )
        )
        // A at (0,0) and (1,0); only (0,0) applies the behaviour
        val simulation = createSimulation(size = 10) { if (it == 0 || it == 1) 0 else -1 }
        val simulator = Simulator(model, simulation)
        simulator.oneStep()
        assertEquals(0, simulator.agents[0])
        assertEquals(-1, simulator.agents[1])
    }

    @Test
    fun testMoveAction() {
        val model = GrainModel(
            grains = listOf(Grain(id = 0, name = "A", halfLife = 0)),
            behaviours = listOf(
                Behaviour(
                    name = "Move east",
                    formula = "x == 0 && y == 0",
                    mainReactiveId = 0,
                    mainProductId = 0,
                    probability = 1.0,
                    actions = "Move(0, 0, 0, 1, 0)"
                )
            )
        )
        // A at (0,0) and (1,0); (0,0) moves to the occupied-by-A cell (1,0): swap
        val simulation = createSimulation(size = 10) { if (it == 0 || it == 1) 0 else -1 }
        val simulator = Simulator(model, simulation)
        simulator.oneStep()
        assertEquals(0, simulator.agents[0])
        assertEquals(0, simulator.agents[1])
    }

    @Test
    fun testMoveActionToEmptyCell() {
        val model = GrainModel(
            grains = listOf(Grain(id = 0, name = "A", halfLife = 0)),
            behaviours = listOf(
                Behaviour(
                    name = "Move east",
                    formula = "x == 0 && y == 0",
                    mainReactiveId = 0,
                    mainProductId = 0,
                    probability = 1.0,
                    actions = "Move(0, 0, 0, 1, 0)"
                )
            )
        )
        // A at (0,0) only: moves to (1,0)
        val simulation = createSimulation(size = 10) { if (it == 0) 0 else -1 }
        val simulator = Simulator(model, simulation)
        simulator.oneStep()
        assertEquals(-1, simulator.agents[0])
        assertEquals(0, simulator.agents[1])
    }

    @Test
    fun testFieldActions() {
        val model = GrainModel(
            grains = listOf(Grain(id = 0, name = "A", halfLife = 0)),
            fields = listOf(Field(id = 0, name = "F")),
            behaviours = listOf(
                Behaviour(
                    name = "Set field east",
                    formula = "x == 0 && y == 0",
                    mainReactiveId = 0,
                    mainProductId = 0,
                    probability = 1.0,
                    actions = "SetField(0, 1, 0, 0.5 + 0.25), AddField(0, 2, 0, 0.3)"
                )
            )
        )
        val simulation = createSimulation(size = 10) { if (it == 0) 0 else -1 }
        val simulator = Simulator(model, simulation)
        simulator.oneStep()
        // SetField with an expression argument (0.5 + 0.25)
        assertEquals(0.75f, simulator.fields[0]!![1], 1e-6f)
        // AddField on a cell at its initial value (~0)
        assertEquals(0.3f, simulator.fields[0]!![2], 1e-4f)
    }

    @Test
    fun testClearFieldAction() {
        val model = GrainModel(
            grains = listOf(Grain(id = 0, name = "A", halfLife = 0)),
            fields = listOf(Field(id = 0, name = "F", formula = "1.0")),
            behaviours = listOf(
                Behaviour(
                    name = "Clear field east",
                    formula = "x == 0 && y == 0",
                    mainReactiveId = 0,
                    mainProductId = 0,
                    probability = 1.0,
                    actions = "ClearField(0, 1, 0)"
                )
            )
        )
        val simulation = createSimulation(size = 10) { if (it == 0) 0 else -1 }
        val simulator = Simulator(model, simulation)
        simulator.oneStep()
        // the field formula puts 1.0 everywhere, but the action cleared (1,0)
        assertEquals(1f, simulator.fields[0]!![0], 1e-6f)
        assertEquals(0f, simulator.fields[0]!![1], 1e-6f)
    }

    @Test
    fun testCountInActionArguments() {
        val model = GrainModel(
            grains = listOf(
                Grain(id = 0, name = "A", halfLife = 0),
                Grain(id = 1, name = "B", halfLife = 0),
                Grain(id = 2, name = "C", halfLife = 0)
            ),
            behaviours = listOf(
                Behaviour(
                    name = "Add C at distance count(B)",
                    formula = "count(1) > 0",
                    mainReactiveId = 0,
                    mainProductId = 0,
                    probability = 1.0,
                    actions = "Add(2, 0, count(1))"
                )
            )
        )
        // A at (0,0), B at (1,0): count(1) == 1 around the behaviour cell
        val simulation = createSimulation(size = 10) { if (it == 0) 0 else if (it == 1) 1 else -1 }
        val simulator = Simulator(model, simulation)
        simulator.oneStep()
        // C must be created at (0,1): if count() returned 0, Add would target
        // the occupied behaviour cell and nothing would happen
        assertEquals(2, simulator.agents[10])
        assertEquals(0, simulator.agents[0])
    }

    @Test
    fun testFieldActionToroidal() {
        val model = GrainModel(
            grains = listOf(Grain(id = 0, name = "A", halfLife = 0)),
            fields = listOf(Field(id = 0, name = "F")),
            behaviours = listOf(
                Behaviour(
                    name = "Set field west",
                    formula = "x == 0 && y == 0",
                    mainReactiveId = 0,
                    mainProductId = 0,
                    probability = 1.0,
                    actions = "SetField(0, -1, 0, 0.6)"
                )
            )
        )
        val simulation = createSimulation(size = 10) { if (it == 0) 0 else -1 }
        val simulator = Simulator(model, simulation)
        simulator.oneStep()
        // the west offset from (0,0) wraps to (9,0)
        assertEquals(0.6f, simulator.fields[0]!![9], 1e-6f)
    }
}
