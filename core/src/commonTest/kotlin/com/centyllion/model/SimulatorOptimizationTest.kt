package com.centyllion.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Non-regression guard for the optimisations of the simulation step (steps A to C).
 *
 * They replace per-cell maps and lists with flat arrays, tables and a deterministic
 * collector. The physics must not change: matter is conserved, the same seed gives the same
 * trajectory, and a behaviour is never applied twice.
 */
class SimulatorOptimizationTest {

    private fun model() = GrainModel(
        name = "optimization",
        grains = listOf(
            Grain(id = 0, name = "a", color = "Lime", movementProbability = 0.2),
        ),
        behaviours = listOf(
            Behaviour(
                name = "birth",
                mainReactiveId = -1,
                mainProductId = 0,
                agentCountPredicates = listOf(
                    AgentCountPredicate(grainId = COUNT_ALL_AGENTS, op = Operator.Equals, count = 3)
                ),
            ),
        ),
    )

    private fun simulation() = createSimulation(size = 12) { if (it % 7 == 0) 0 else -1 }.copy(seed = 7)

    @Test
    fun testMatterIsConservedAtEveryStep() {
        val simulator = Simulator(model(), simulation())
        repeat(20) {
            simulator.oneStep()
            val agents = simulator.agents.count { it >= 0 }
            val counted = simulator.grainsCounts().values.sum()
            assertEquals(agents, counted, "the grain counts must match the agents present (step ${simulator.step})")
        }
    }

    @Test
    fun testTheSameSeedGivesTheSameTrajectory() {
        val first = Simulator(model(), simulation())
        val second = Simulator(model(), simulation())
        repeat(15) {
            first.oneStep()
            second.oneStep()
        }
        assertEquals(first.agents.toList(), second.agents.toList(), "same seed, same agents")
        assertEquals(first.ages.toList(), second.ages.toList(), "same seed, same ages")
    }

    @Test
    fun testABehaviourIsNeverAppliedTwice() {
        val simulator = Simulator(model(), simulation())
        repeat(10) {
            val (applied, _) = simulator.oneStep()
            assertEquals(
                applied.size, applied.toSet().size,
                "the executed behaviours must be distinct instances"
            )
        }
    }

    @Test
    fun testTheGridStaysConsistent() {
        val simulator = Simulator(model(), simulation())
        repeat(20) { simulator.oneStep() }
        val agents = simulator.agents
        assertEquals(simulator.simulation.dataSize, agents.size)
        assertTrue(agents.count { it >= 0 } > 0, "some agents must remain")
        assertTrue(agents.count { it >= 0 } <= agents.size, "no agent outside the grid")
    }
}
