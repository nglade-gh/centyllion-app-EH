package com.centyllion.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The simulation seed must make a run reproducible: same seed, same trajectory, whatever the
 * order in which the cells are visited (each cell owns its own stream).
 */
class SimulationRandomTest {

    /** Two grains on a 30x30 grid; grain 0 moves randomly, so the trajectory depends on draws. */
    private fun movingModel() = GrainModel(
        name = "moves",
        grains = listOf(Grain(id = 0, name = "walker", movementProbability = 1.0)),
        behaviours = listOf(Behaviour(name = "noop", mainReactiveId = 0, mainProductId = 0))
    )

    private fun run(seed: Long, steps: Int = 20): List<Int> {
        val simulation = createSimulation(size = 30) { if (it == 30 * 15 + 15) 0 else -1 }
            .copy(seed = seed)
        val simulator = Simulator(movingModel(), simulation)
        repeat(steps) { simulator.oneStep() }
        return simulator.agents.toList()
    }

    @Test
    fun testSameSeedGivesTheSameTrajectory() {
        assertEquals(run(42), run(42))
        assertEquals(run(7), run(7))
    }

    @Test
    fun testDifferentSeedsGiveDifferentTrajectories() {
        val trajectories = listOf(1L, 2L, 3L, 12345L).map { run(it) }.toSet()
        assertTrue(trajectories.size > 1, "the seed must actually drive the trajectory")
    }

    @Test
    fun testResetRepeatsTheSameTrajectory() {
        val simulation = createSimulation(size = 30) { if (it == 30 * 15 + 15) 0 else -1 }
        val simulator = Simulator(movingModel(), simulation)
        repeat(20) { simulator.oneStep() }
        val first = simulator.agents.toList()

        simulator.reset()
        repeat(20) { simulator.oneStep() }
        assertEquals(first, simulator.agents.toList(), "reset + run must repeat the run")
    }

    @Test
    fun testAPositionDoesNotDependOnTheOrderOfTheCells() {
        // the stream of a cell is its own: a cell draws the same numbers whatever happens
        // elsewhere on the grid, which is what makes parallel runs reproducible
        val a = SimulationRandom(42, 100)
        val first = (0 until 5).map { a.nextDouble(7) }

        val b = SimulationRandom(42, 100)
        // consume draws on other cells first: they must not disturb cell 7
        repeat(1000) { b.nextDouble(3) }
        repeat(1000) { b.nextDouble(99) }
        val second = (0 until 5).map { b.nextDouble(7) }

        assertEquals(first, second, "cell 7 keeps its own stream")
    }

    @Test
    fun testCellsDifferFromEachOther() {
        val engine = SimulationRandom(42, 10)
        val firsts = (0 until 10).map { engine.nextDouble(it) }
        assertEquals(10, firsts.toSet().size, "each cell starts from a different stream")
    }

    @Test
    fun testDrawsStayInRange() {
        val engine = SimulationRandom(1234, 50)
        repeat(2000) { index ->
            val cell = index % 50
            val d = engine.nextDouble(cell)
            assertTrue(d >= 0.0 && d < 1.0, "double out of range: $d")
            val n = engine.nextInt(cell, 7)
            assertTrue(n in 0..6, "int out of range: $n")
        }
        repeat(500) {
            assertTrue(engine.nextDouble() in 0.0..0.999999999)
            assertTrue(engine.nextInt(3) in 0..2)
        }
    }

    @Test
    fun testDistributionIsRoughlyUniform() {
        val engine = SimulationRandom(42, 1)
        var above = 0
        val total = 20_000
        repeat(total) { if (engine.nextDouble(0) < 0.5) above++ }
        // 3 sigma around 10 000 is about +-212
        assertTrue(above in (9_700..10_300), "unexpected balance: $above / $total")
    }

    @Test
    fun testTheSeedIsSerialized() {
        val simulation = createSimulation(size = 10) { -1 }.copy(seed = 987654321L)
        val text = kotlinx.serialization.json.Json.encodeToString(Simulation.serializer(), simulation)
        assertTrue(text.contains("\"seed\":987654321"), "the seed is written in the file")
        val reloaded = kotlinx.serialization.json.Json.decodeFromString(Simulation.serializer(), text)
        assertEquals(987654321L, reloaded.seed)
    }

    @Test
    fun testTheDefaultSeedIs42() {
        assertEquals(42L, createSimulation(size = 10) { -1 }.seed)
        assertEquals(42L, DEFAULT_SIMULATION_SEED)
    }
}
