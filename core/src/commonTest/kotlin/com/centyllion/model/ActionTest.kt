package com.centyllion.model

import com.centyllion.i18n.Locales
import kotlinx.serialization.json.Json
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
        assertTrue(validateActionFormula("Add(0)")!!.contains("expects 2 or 3 arguments"))
        assertTrue(validateActionFormula("Move(0, 0, 0, 1)")!!.contains("expects 5 arguments"))
        // non-numeric argument
        assertTrue(validateActionFormula("Add(true, 1, 0)")!!.contains("must be a number"))
        // meta-actions
        assertNull(validateActionFormula("pause()"))
        assertNull(validateActionFormula("Add(0, 10)"))
        assertNull(validateActionFormula("AddRect(0, 10, 1, 2, 5, 6)"))
        assertNull(validateActionFormula("AddDisc(0, 5, 10, 10, 3)"))
        assertNull(validateActionFormula("EraseRect(1, 2, 5, 6)"))
        assertNull(validateActionFormula("EraseRect(0, 1, 2, 5, 6)"))
        assertNull(validateActionFormula("EraseDisc(10, 10, 3)"))
        assertNull(validateActionFormula("EraseDisc(0, 10, 10, 3)"))
        assertNull(validateActionFormula("Erase(0)"))
        assertNull(validateActionFormula("Activate(1), Inactivate(2)"))
        assertTrue(validateActionFormula("AddRect(0, 10, 1, 2)")!!.contains("expects 6 arguments"))
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
        val behaviour = Behaviour(name = "B", mainReactiveId = 0, actions = "Add(0)")
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

    // ---- serialization (saves / .netbiodyn files) ----

    @Test
    fun testActionsSerializationRoundTrip() {
        val model = GrainModel(
            name = "T",
            grains = listOf(Grain(id = 0, name = "A", halfLife = 0), Grain(id = 1, name = "B", halfLife = 0)),
            behaviours = listOf(
                Behaviour(name = "B1", mainReactiveId = 0, mainProductId = 1,
                    actions = "Add(1, 1, 0), Move(0, 0, 0, 0, 1)"),
                Behaviour(name = "B2", mainReactiveId = 0, mainProductId = 1)
            )
        )
        val saved = ModelAndSimulation(model, Simulation.empty)
        val encoded = Json.encodeToString(ModelAndSimulation.serializer(), saved)

        // non-empty actions are written
        assertTrue(encoded.contains("Add(1, 1, 0), Move(0, 0, 0, 0, 1)"))
        // exactly one behaviour has actions (the empty one is omitted)
        assertEquals(1, encoded.split("\"actions\"").size - 1)

        // round trip
        val reloaded = Json.decodeFromString(ModelAndSimulation.serializer(), encoded)
        assertEquals("Add(1, 1, 0), Move(0, 0, 0, 0, 1)", reloaded.model.behaviours[0].actions)
        assertEquals("", reloaded.model.behaviours[1].actions)

        // a file without any "actions" key still loads (old .netbiodyn files)
        val legacy = Json.decodeFromString(ModelAndSimulation.serializer(), """
            {"model": {"name": "L", "grains": [{"name": "A"}],
              "behaviours": [{"name": "B", "mainReactiveId": 0, "mainProductId": 0}]},
             "simulation": {"name": "", "description": "", "agents": [-1]}}
        """.trimIndent())
        assertEquals("", legacy.model.behaviours[0].actions)
    }
}

/**
 * Activate/Inactivate act at run time, and a rewind restores the activation state of the
 * beginning of the run.
 */
class BehaviourActivationTest {

    private fun model() = GrainModel(
        name = "activation",
        grains = listOf(Grain(id = 0, name = "a", color = "Lime", movementProbability = 0.0)),
        behaviours = listOf(
            Behaviour(
                name = "switch off", mainReactiveId = 0, mainProductId = 0,
                id = 0, probability = 1.0, actions = "Inactivate(0)"
            ),
        ),
    )

    @Test
    fun testInactivateChangesTheRunTimeState() {
        val simulator = Simulator(model(), createSimulation(size = 8) { if (it == 0) 0 else -1 })
        assertTrue(simulator.isRuntimeEnabled(0), "a behaviour is active by default")
        simulator.oneStep()
        assertTrue(!simulator.isRuntimeEnabled(0), "Inactivate(0) must switch the behaviour off")
        assertTrue(simulator.activationChanged, "the UI has to be told")
    }

    @Test
    fun testRewindRestoresTheInitialActivation() {
        val simulator = Simulator(model(), createSimulation(size = 8) { if (it == 0) 0 else -1 })
        simulator.oneStep()
        assertTrue(!simulator.isRuntimeEnabled(0))
        simulator.reset()
        assertTrue(simulator.isRuntimeEnabled(0), "a rewind must restore the activation of the start")
    }

    @Test
    fun testModelActivationIsTheInitialState() {
        val disabled = model().let { m ->
            m.copy(behaviours = m.behaviours.map { it.copy(enabled = false) })
        }
        val simulator = Simulator(disabled, createSimulation(size = 8) { if (it == 0) 0 else -1 })
        assertTrue(!simulator.isRuntimeEnabled(0), "an inactive behaviour starts inactive")
    }
}

/**
 * Meta-actions act on the simulation, so a behaviour applicable on many cells must not run
 * them once per cell: `AddRect(id, N, ...)` adds N agents, not N per cell.
 */
class MetaActionOncePerStepTest {

    private fun model(actions: String) = GrainModel(
        name = "zones",
        grains = listOf(Grain(id = 0, name = "a", color = "Lime", movementProbability = 0.0)),
        behaviours = listOf(
            Behaviour(
                name = "seed", mainReactiveId = 0, mainProductId = 0,
                id = 0, probability = 1.0, actions = actions
            ),
        ),
    )

    /** Ten agents in the first column, far from the zone used below. */
    private fun simulation() = createSimulation("zones", size = 10) { index ->
        if (index % 10 == 0) 0 else -1
    }

    @Test
    fun testAddRectAddsExactlyN() {
        val simulator = Simulator(model("AddRect(0, 10, 2, 2, 6, 6)"), simulation())
        val before = simulator.agents.count { it >= 0 }
        simulator.oneStep()
        val after = simulator.agents.count { it >= 0 }
        assertTrue(after - before in 9..10, "AddRect must add about ten agents (got ${after - before}), never one per cell")
    }

    @Test
    fun testAddDiscAddsExactlyN() {
        val simulator = Simulator(model("AddDisc(0, 7, 5, 5, 3)"), simulation())
        val before = simulator.agents.count { it >= 0 }
        simulator.oneStep()
        val after = simulator.agents.count { it >= 0 }
        assertTrue(after - before in 6..7, "AddDisc must add about seven agents (got ${after - before}), never one per cell")
    }

    @Test
    fun testAddManyAddsExactlyN() {
        val simulator = Simulator(model("Add(0, 5)"), simulation())
        val before = simulator.agents.count { it >= 0 }
        simulator.oneStep()
        val after = simulator.agents.count { it >= 0 }
        assertEquals(before + 5, after, "Add(id, N) must add five agents")
    }

    @Test
    fun testTheZoneIsNotFlooded() {
        val simulator = Simulator(model("AddRect(0, 10, 2, 2, 6, 6)"), simulation())
        simulator.oneStep()
        val inZone = (2..6).sumOf { y -> (2..6).count { x -> simulator.agents[y * 10 + x] == 0 } }
        assertTrue(inZone in 1..10, "the zone must receive at most ten agents (got $inZone), not one per cell")
    }
}
