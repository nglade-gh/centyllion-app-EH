package com.centyllion.model

import com.centyllion.i18n.Locales
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

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
        // meta-actions are accepted by the parser but refused in a behaviour
        assertNull(validateModifierActions("pause()"))
        assertNull(validateModifierActions("Add(0, 10)"))
        assertNull(validateModifierActions("AddRect(0, 10, 1, 2, 5, 6)"))
        assertNull(validateModifierActions("AddDisc(0, 5, 10, 10, 3)"))
        assertNull(validateModifierActions("EraseRect(1, 2, 5, 6)"))
        assertNull(validateModifierActions("EraseRect(0, 1, 2, 5, 6)"))
        assertNull(validateModifierActions("EraseDisc(10, 10, 3)"))
        assertNull(validateModifierActions("EraseDisc(0, 10, 10, 3)"))
        assertNull(validateModifierActions("Erase(0)"))
        assertNull(validateModifierActions("Activate(1), Inactivate(2)"))
        assertTrue(validateActionFormula("pause()")!!.contains("meta-action"))
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

/**
 * Simulation modifiers: evaluated once per step, without any current cell, and the only place
 * where the meta-actions are allowed.
 */
class SimulationModifierTest {

    private fun model(
        formula: String = "",
        actions: String = "AddRect(0, 5, 2, 2, 4, 4)",
        probability: Double = 1.0,
        enabled: Boolean = true
    ) = GrainModel(
        name = "modifiers",
        grains = listOf(Grain(id = 0, name = "a", color = "Lime", movementProbability = 0.0)),
        modifiers = listOf(
            SimulationModifier(name = "seed", id = 0, formula = formula, actions = actions,
                probability = probability, enabled = enabled)
        ),
    )

    private fun simulation() = createSimulation("modifiers", size = 10) { -1 }

    @Test
    fun testTheModifierRunsOncePerStep() {
        val simulator = Simulator(model(), simulation())
        simulator.oneStep()
        assertEquals(5, simulator.agents.count { it >= 0 }, "five agents, once only")
        simulator.oneStep()
        // the zone holds nine cells: five then four more, then nothing
        assertEquals(9, simulator.agents.count { it >= 0 }, "the zone fills up, once per step")
    }

    @Test
    fun testAnInactiveModifierDoesNothing() {
        val simulator = Simulator(model(enabled = false), simulation())
        simulator.oneStep()
        assertEquals(0, simulator.agents.count { it >= 0 })
    }

    @Test
    fun testAZeroProbabilityDoesNothing() {
        val simulator = Simulator(model(probability = 0.0), simulation())
        simulator.oneStep()
        assertEquals(0, simulator.agents.count { it >= 0 })
    }

    @Test
    fun testTheConditionIsEvaluatedWithoutACell() {
        // the modifier sees the number of the step that has just been computed, like the
        // behaviours: 0 during the first step, 1 during the second
        val simulator = Simulator(model(formula = "step >= 1"), simulation())
        simulator.oneStep()
        assertEquals(0, simulator.agents.count { it >= 0 }, "step is 0 during the first step")
        simulator.oneStep()
        assertEquals(5, simulator.agents.count { it >= 0 }, "step is 1 now")
    }

    @Test
    fun testCountAndFieldTakeCoordinates() {
        assertNull(validateFormula("count(0, 5, 5) >= 3 && step > 2"))
        assertNull(validateFormula("agent(0, 0) >= 0"))
        assertNull(validateFormula("age(3, 4) > 2"))
    }

    @Test
    fun testMetaActionsAreRefusedInABehaviour() {
        assertTrue(validateActionFormula("AddRect(0, 5, 2, 2, 4, 4)")!!.contains("meta-action"))
        assertTrue(validateActionFormula("Add(0, 5)")!!.contains("meta-action"))
        assertTrue(validateActionFormula("pause()")!!.contains("meta-action"))
        assertTrue(validateActionFormula("Activate(1)")!!.contains("meta-action"))
        assertNull(validateActionFormula("Add(0, 1, 0)"), "cell actions stay allowed")
    }

    @Test
    fun testOnlyMetaActionsAreAllowedInAModifier() {
        assertNull(validateModifierActions("AddRect(0, 5, 2, 2, 4, 4), pause()"))
        assertTrue(validateModifierActions("Add(0, 1, 0)")!!.contains("meta-actions"))
    }
}

/** Use(id) and Unuse(id) switch the simulation modifiers on and off at run time. */
class ModifierUseTest {

    private fun model() = GrainModel(
        name = "use",
        grains = listOf(Grain(id = 0, name = "a", color = "Lime", movementProbability = 0.0)),
        modifiers = listOf(
            SimulationModifier(name = "switcher", id = 0, actions = "Unuse(1)"),
            SimulationModifier(name = "seeder", id = 1, actions = "AddRect(0, 4, 1, 1, 4, 4)"),
        ),
    )

    @Test
    fun testUnuseStopsAModifier() {
        val simulator = Simulator(model(), createSimulation("use", size = 10) { -1 })
        simulator.oneStep()
        // the first modifier ran and switched the second one off, so nothing was seeded
        assertEquals(0, simulator.agents.count { it >= 0 })
        assertFalse(simulator.isModifierUsed(1), "Unuse(1) must switch the modifier off")
    }

    @Test
    fun testUseReactivatesIt() {
        val simulator = Simulator(model(), createSimulation("use", size = 10) { -1 })
        simulator.oneStep()
        assertFalse(simulator.isModifierUsed(1))
        val reseeded = model().let { m ->
            m.copy(modifiers = m.modifiers.map { if (it.id == 0) it.copy(actions = "Use(1)") else it })
        }
        val second = Simulator(reseeded, createSimulation("use", size = 10) { -1 })
        second.oneStep()
        assertTrue(second.isModifierUsed(1))
    }

    @Test
    fun testRewindRestoresTheModifiers() {
        val simulator = Simulator(model(), createSimulation("use", size = 10) { -1 })
        simulator.oneStep()
        assertFalse(simulator.isModifierUsed(1))
        simulator.reset()
        assertTrue(simulator.isModifierUsed(1), "a rewind restores the modifiers of the start")
    }

    @Test
    fun testUseAndUnuseAreMetaActions() {
        assertTrue(validateActionFormula("Use(1)")!!.contains("meta-action"))
        assertNull(validateModifierActions("Use(1), Unuse(0)"))
    }
}

/** DrawLine traces a line of agents between two cells. */
class DrawLineTest {

    private fun model(actions: String) = GrainModel(
        name = "line",
        grains = listOf(Grain(id = 0, name = "a", color = "Lime", movementProbability = 0.0)),
        modifiers = listOf(SimulationModifier(name = "line", id = 0, actions = actions)),
    )

    private fun simulation() = createSimulation("line", size = 10) { -1 }

    @Test
    fun testTheLineJoinsBothEnds() {
        val simulator = Simulator(model("DrawLine(0, 1, 1, 5, 5)"), simulation())
        simulator.oneStep()
        val agents = simulator.agents
        // both ends and the diagonal between them
        assertTrue(agents[1 * 10 + 1] == 0, "the start must be drawn")
        assertTrue(agents[5 * 10 + 5] == 0, "the end must be drawn")
        assertTrue(agents[3 * 10 + 3] == 0, "the diagonal must be continuous")
        assertEquals(5, agents.count { it >= 0 }, "a diagonal of five cells")
    }

    @Test
    fun testTheLineIsHorizontalOrVerticalToo() {
        val horizontal = Simulator(model("DrawLine(0, 0, 4, 9, 4)"), simulation())
        horizontal.oneStep()
        assertEquals(10, horizontal.agents.count { it >= 0 })
        val vertical = Simulator(model("DrawLine(0, 7, 0, 7, 9)"), simulation())
        vertical.oneStep()
        assertEquals(10, vertical.agents.count { it >= 0 })
    }

    @Test
    fun testTheLineIsAMetaAction() {
        assertTrue(validateActionFormula("DrawLine(0, 1, 1, 5, 5)")!!.contains("meta-action"))
        assertNull(validateModifierActions("DrawLine(0, 1, 1, 5, 5)"))
    }
}

/** Activation tests: state tests, accepted in the condition of a simulation modifier only. */
class ActivationTestFunctionsTest {

    @Test
    fun testTheyAreValidConditions() {
        assertNull(validateModifierCondition("IsActive(0) && IsNotInUse(1)"))
        assertNull(validateModifierCondition("IsInactive(2)"))
        assertNull(validateModifierCondition("IsInUse(0)"))
        // outside the condition of a modifier they are refused, with an explicit message
        assertTrue(validateFormula("IsActive(0) && IsNotInUse(1)")!!.contains("state test"))
        assertTrue(validateFormula("IsInUse(0)")!!.contains("state test"))
        assertTrue(validatePredicateFormula("IsInactive(2)")!!.contains("state test"))
    }

    @Test
    fun testAModifierSeesItsOwnState() {
        // the modifier switches itself off at the first step: IsInUse(0) is true, then false
        val model = GrainModel(
            name = "tests",
            grains = listOf(Grain(id = 0, name = "a", color = "Lime", movementProbability = 0.0)),
            modifiers = listOf(
                SimulationModifier(name = "self", id = 0, formula = "IsInUse(0)", actions = "Unuse(0)"),
                SimulationModifier(name = "seed", id = 1, formula = "IsNotInUse(0)", actions = "DrawLine(0, 0, 0, 3, 0)"),
            ),
        )
        val simulator = Simulator(model, createSimulation("tests", size = 8) { -1 })
        simulator.oneStep()
        // the modifiers run in order inside the step: the second one sees the first one off
        assertFalse(simulator.isModifierUsed(0), "the first modifier switched itself off")
        assertEquals(4, simulator.agents.count { it >= 0 }, "the second modifier drew its line")
    }
}

/**
 * The modifier probabilities are independent: two modifiers at probability 1 both run in the
 * same step, they are never normalised between each other.
 */
class ModifierProbabilityTest {

    private fun model(first: Double, second: Double) = GrainModel(
        name = "probas",
        grains = listOf(Grain(id = 0, name = "a", color = "Lime", movementProbability = 0.0)),
        modifiers = listOf(
            SimulationModifier(name = "top", id = 0, probability = first, actions = "DrawLine(0, 0, 0, 3, 0)"),
            SimulationModifier(name = "bottom", id = 1, probability = second, actions = "DrawLine(0, 0, 7, 3, 7)"),
        ),
    )

    private fun simulation() = createSimulation("probas", size = 8) { -1 }

    @Test
    fun testTwoModifiersAtFullProbabilityBothRun() {
        val simulator = Simulator(model(1.0, 1.0), simulation())
        simulator.oneStep()
        assertEquals(8, simulator.agents.count { it >= 0 }, "both modifiers ran, none was scaled down")
    }

    @Test
    fun testAZeroProbabilityModifierNeverRuns() {
        val simulator = Simulator(model(1.0, 0.0), simulation())
        simulator.oneStep()
        assertEquals(4, simulator.agents.count { it >= 0 }, "only the first modifier ran")
    }
}
