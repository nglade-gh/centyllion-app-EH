package com.centyllion.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimulationTest {

    @Test
    fun testUpdatePlanSerialization() {
        val model = GrainModel(
            name = "Fourmis",
            description = "Agent bleus et rouges",
            updatePlan = SEQUENTIAL_UPDATE_PLAN
        )
        val simulation = Simulation.empty

        // Sequential is serialized in the model section
        val sequentialJson = Json.encodeToString(ModelAndSimulation.serializer(), ModelAndSimulation(model, simulation))
        assertTrue(sequentialJson.contains("\"updatePlan\":\"Sequential\""), "Sequential plan should be serialized")

        // Parallel (default) is not serialized
        val parallelJson = Json.encodeToString(
            ModelAndSimulation.serializer(),
            ModelAndSimulation(model.copy(updatePlan = PARALLEL_UPDATE_PLAN), simulation)
        )
        assertFalse(parallelJson.contains("updatePlan"), "Parallel plan should not be serialized")

        // Loading applies the plan to the simulation
        val loaded = Json.decodeFromString(ModelAndSimulation.serializer(), sequentialJson).applyUpdatePlan()
        assertTrue(loaded.model.isSequentialUpdate)
        assertFalse(loaded.simulation.parallelUpdate)
    }

    @Test
    fun testBugSimpleOne2OneTransfo() {
        // creates the simulations
        val one2oneModel = GrainModel(
            "one2one", "Test one 2 one behaviour",
            grains = listOf(Grain(0, "Source", movementProbability = 0.0), Grain(1, "Target")),
            behaviours = listOf(Behaviour("one2one", mainReactiveId = 0, mainProductId = 1))
        )

        val one2oneSimulation = createSimulation("Test one 2 one") { if (it % 20 == 0) 0 else -1}

        val simulator = Simulator(one2oneModel, one2oneSimulation)
        simulator.grainsCounts().let { counts ->
            assertEquals(500, counts[0])
            assertEquals(0, counts[1] ?: 0)
        }

        // runs the simulator
        simulator.oneStep()

        simulator.grainsCounts().let { counts ->
            assertEquals(0, counts[0] ?: 0)
            assertEquals(500, counts[1] ?: 0)
        }
    }

    @Test
    fun testGenerationBehaviour() {
        // Test: None -> Agent (spontaneous generation)
        val model = GrainModel(
            "generation", "Test generation behaviour",
            grains = listOf(Grain(0, "AgentA"), Grain(1, "AgentB")),
            behaviours = listOf(
                Behaviour(
                    "generate A",
                    mainReactiveId = -1,  // None
                    mainProductId = 0,    // AgentA
                    agentCountPredicates = listOf(
                        AgentCountPredicate(grainId = 1, op = Operator.GreaterThan, count = 2)
                    )
                )
            )
        )

        // Create a simulation with some AgentB but no AgentA
        val simulation = createSimulation("Test generation", size = 10) { index ->
            // Place AgentB at positions 0, 1, 2 (neighbours of position 5)
            when (index) {
                0, 1, 2 -> 1
                else -> -1
            }
        }

        val simulator = Simulator(model, simulation)
        simulator.grainsCounts().let { counts ->
            assertEquals(0, counts[0] ?: 0)
            assertEquals(3, counts[1] ?: 0)
        }

        // Run the simulator - AgentA should be generated where there are > 2 AgentB neighbours
        simulator.oneStep()

        simulator.grainsCounts().let { counts ->
            // Some AgentA should have been generated
            val agentACount = counts[0] ?: 0
            assertTrue(agentACount > 0, "Expected some AgentA to be generated, but got $agentACount")
        }
    }

    @Test
    fun testConditionExpression() {
        // Test: AgentA -> None only when count(1) > 2 AND count(2) == 1
        val model = GrainModel(
            "condition expression", "Test condition expression",
            grains = listOf(Grain(0, "AgentA"), Grain(1, "AgentB"), Grain(2, "AgentC")),
            behaviours = listOf(
                Behaviour(
                    "A dies near B and C",
                    mainReactiveId = 0,  // AgentA
                    mainProductId = -1,  // None (dies)
                    conditionExpression = "count(1) > 2 && count(2) == 1"
                )
            )
        )

        // AgentA at 0, AgentB at 1,10,11, AgentC at 19 (neighbour LeftDown of 0)
        val simulation = createSimulation("Test condition expression", size = 10) { index ->
            when (index) {
                0 -> 0    // AgentA
                1, 10, 11 -> 1  // AgentB (3 neighbours)
                19 -> 2  // AgentC (1 neighbour: LeftDown)
                else -> -1
            }
        }

        val simulator = Simulator(model, simulation)
        simulator.oneStep()

        simulator.grainsCounts().let { counts ->
            assertEquals(0, counts[0] ?: 0)  // AgentA should be dead
            assertEquals(3, counts[1] ?: 0)  // AgentB unchanged
            assertEquals(1, counts[2] ?: 0)  // AgentC unchanged
        }
    }

    @Test
    fun testConditionExpressionFalse() {
        // Same but condition is false: count(2) == 5 (false)
        val model = GrainModel(
            "condition expression false", "Test condition expression false",
            grains = listOf(Grain(0, "AgentA"), Grain(1, "AgentB"), Grain(2, "AgentC")),
            behaviours = listOf(
                Behaviour(
                    "A dies near many C",
                    mainReactiveId = 0,
                    mainProductId = -1,
                    conditionExpression = "count(2) == 5"
                )
            )
        )

        val simulation = createSimulation("Test condition false", size = 10) { index ->
            when (index) {
                0 -> 0
                1, 10, 11 -> 1
                19 -> 2
                else -> -1
            }
        }

        val simulator = Simulator(model, simulation)
        simulator.oneStep()

        simulator.grainsCounts().let { counts ->
            assertEquals(1, counts[0] ?: 0)  // AgentA should survive (condition false)
        }
    }

    @Test
    fun testParallelUpdate() {
        // B becomes C unconditionally; A becomes C when at least 1 B neighbour is present.
        // Parallel mode: both are computed from the initial state, so A sees B and becomes C.
        // Sequential mode: if B is transformed first, A no longer sees B and stays A.
        val model = GrainModel(
            "parallel", "Test parallel update",
            grains = listOf(Grain(0, "A"), Grain(1, "B"), Grain(2, "C")),
            behaviours = listOf(
                Behaviour(
                    "B becomes C",
                    mainReactiveId = 1,
                    mainProductId = 2
                ),
                Behaviour(
                    "A becomes C near B",
                    mainReactiveId = 0,
                    mainProductId = 2,
                    agentCountPredicates = listOf(
                        AgentCountPredicate(grainId = 1, op = Operator.GreaterThanOrEquals, count = 1)
                    )
                )
            )
        )

        val baseSimulation = createSimulation("Test parallel", size = 10) { index ->
            when (index) {
                0 -> 0  // A
                1 -> 1  // B
                else -> -1
            }
        }

        // Parallel mode: A sees B at the start of the step, so both become C
        val parallelSim = Simulator(model.copy(updatePlan = PARALLEL_UPDATE_PLAN), baseSimulation)
        parallelSim.oneStep()
        assertEquals(2, parallelSim.idAtIndex(0), "A should become C in parallel mode")
        assertEquals(2, parallelSim.idAtIndex(1), "B should become C in parallel mode")

        // Sequential mode: order matters; A may stay A if B is transformed first
        val sequentialSim = Simulator(model.copy(updatePlan = SEQUENTIAL_UPDATE_PLAN), baseSimulation)
        sequentialSim.oneStep()
        val pos0 = sequentialSim.idAtIndex(0)
        val pos1 = sequentialSim.idAtIndex(1)
        val valid = (pos0 == 2 && pos1 == 2) || (pos0 == 0 && pos1 == 2)
        assertTrue(valid, "Sequential mode should give (C,C) or (A,C), got ($pos0,$pos1)")
    }

    @Test
    fun testAgentCountPredicate() {
        // Test: AgentA -> None only when AgentB count > 2
        val model = GrainModel(
            "count predicate", "Test agent count predicate",
            grains = listOf(Grain(0, "AgentA"), Grain(1, "AgentB")),
            behaviours = listOf(
                Behaviour(
                    "A dies near many B",
                    mainReactiveId = 0,  // AgentA
                    mainProductId = -1,  // None (dies)
                    agentCountPredicates = listOf(
                        AgentCountPredicate(grainId = 1, op = Operator.GreaterThan, count = 2)
                    )
                )
            )
        )

        // Create a simulation with AgentA at position 0 and AgentB at its 3 neighbours (indices 1, 10, 11)
        val simulation = createSimulation("Test count predicate", size = 10) { index ->
            when (index) {
                0 -> 0  // AgentA
                1, 10, 11 -> 1  // AgentB (neighbours of 0: Right, Down, RightDown)
                else -> -1
            }
        }

        val simulator = Simulator(model, simulation)
        simulator.grainsCounts().let { counts ->
            assertEquals(1, counts[0] ?: 0)
            assertEquals(3, counts[1] ?: 0)
        }

        // Run the simulator - AgentA should die because it has 3 AgentB neighbours (> 2)
        simulator.oneStep()

        simulator.grainsCounts().let { counts ->
            assertEquals(0, counts[0] ?: 0)  // AgentA should be dead
            assertEquals(3, counts[1] ?: 0)  // AgentB unchanged
        }
    }

}
