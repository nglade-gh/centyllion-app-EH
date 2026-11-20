package com.centyllion.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Conway's Game of Life expressed with the model primitives that the expert
 * tutorial asks the user to write:
 *
 * - a "cell" grain that does not move,
 * - three behaviours on that grain, all driven by `count(0)`:
 *   survival `count(0) == 2 || count(0) == 3` (main product: the cell itself),
 *   death    `count(0) < 2 || count(0) > 3`  (main product: none, i.e. -1),
 *   birth    `count(0) == 3` on an empty cell (main reactive: none), main product: the cell.
 *
 * The reference states below were computed with a plain Python implementation
 * of the same rules on a toroidal 20x20 grid, so the test also checks that the
 * update is simultaneous (double buffering) and that `count(id)` ignores the
 * cell itself.
 */
class GameOfLifeTest {

    private fun model() = GrainModel(
        name = "game of life", description = "Conway's Game of Life",
        grains = listOf(Grain(id = 0, name = "cell", color = "Lime", movementProbability = 0.0)),
        behaviours = listOf(
            Behaviour("survive", mainReactiveId = 0, mainProductId = 0, formula = "count(0) == 2 || count(0) == 3"),
            Behaviour("die", mainReactiveId = 0, mainProductId = -1, formula = "count(0) < 2 || count(0) > 3"),
            Behaviour("birth", mainReactiveId = -1, mainProductId = 0, formula = "count(0) == 3")
        )
    )

    private fun simulation(size: Int, cells: Set<Pair<Int, Int>>): Simulation =
        createSimulation("game of life", size = size) { index ->
            val position = Pair(index % size, index / size)
            if (cells.contains(position)) 0 else -1
        }

    private fun alive(simulator: Simulator, size: Int): Set<Pair<Int, Int>> =
        (0 until simulator.simulation.dataSize)
            .filter { simulator.idAtIndex(it) == 0 }
            .map { Pair(it % size, it / size) }
            .toSet()

    private fun step(simulator: Simulator) = simulator.oneStep()

    @Test
    fun testBlinkerHasPeriodTwo() {
        val size = 20
        val vertical = setOf(Pair(5, 4), Pair(5, 5), Pair(5, 6))
        val horizontal = setOf(Pair(4, 5), Pair(5, 5), Pair(6, 5))
        val simulator = Simulator(model(), simulation(size, vertical))

        assertEquals(vertical, alive(simulator, size), "initial pattern")

        step(simulator)
        assertEquals(horizontal, alive(simulator, size), "blinker after one step")

        step(simulator)
        assertEquals(vertical, alive(simulator, size), "blinker after two steps")
    }

    @Test
    fun testBlockIsStableAndLonelyCellDies() {
        val size = 20
        val block = setOf(Pair(2, 2), Pair(3, 2), Pair(2, 3), Pair(3, 3))
        var simulator = Simulator(model(), simulation(size, block))
        repeat(3) { step(simulator) }
        assertEquals(block, alive(simulator, size), "block (still life) must not change")

        simulator = Simulator(model(), simulation(size, setOf(Pair(10, 10))))
        step(simulator)
        assertTrue(alive(simulator, size).isEmpty(), "a lonely cell dies of underpopulation")
    }

    @Test
    fun testGliderMovesOneCellPerFourSteps() {
        val size = 20
        val glider = setOf(Pair(5, 5), Pair(6, 6), Pair(4, 7), Pair(5, 7), Pair(6, 7))
        val simulator = Simulator(model(), simulation(size, glider))

        val expected = listOf(
            setOf(Pair(4, 6), Pair(5, 7), Pair(5, 8), Pair(6, 6), Pair(6, 7)),
            setOf(Pair(4, 7), Pair(5, 8), Pair(6, 6), Pair(6, 7), Pair(6, 8)),
            setOf(Pair(5, 6), Pair(5, 8), Pair(6, 7), Pair(6, 8), Pair(7, 7)),
            setOf(Pair(5, 8), Pair(6, 6), Pair(6, 8), Pair(7, 7), Pair(7, 8))
        )

        expected.forEachIndexed { index, cells ->
            step(simulator)
            assertEquals(cells, alive(simulator, size), "glider after ${index + 1} step(s)")
        }

        // after one period the glider is translated by (1, 1)
        assertEquals(glider.map { Pair(it.first + 1, it.second + 1) }.toSet(), alive(simulator, size))
    }

    /** The three formulas must be accepted by the predicate validator (they are what the user types). */
    @Test
    fun testFormulasAreValid() {
        listOf(
            "count(0) == 2 || count(0) == 3",
            "count(0) < 2 || count(0) > 3",
            "count(0) == 3"
        ).forEach { formula ->
            assertEquals(null, validatePredicateFormula(formula), "formula must be valid: $formula")
        }
    }

    /** Without the birth behaviour, only deaths happen: the glider vanishes. */
    @Test
    fun testWithoutBirthBehaviourThePatternDies() {
        val size = 20
        val glider = setOf(Pair(5, 5), Pair(6, 6), Pair(4, 7), Pair(5, 7), Pair(6, 7))
        val twoBehaviours = model().copy(behaviours = model().behaviours.filter { it.name != "birth" })
        val simulator = Simulator(twoBehaviours, simulation(size, glider))

        repeat(4) { step(simulator) }

        assertNotEquals(glider.map { Pair(it.first + 1, it.second + 1) }.toSet(), alive(simulator, size))
        assertTrue(alive(simulator, size).size < glider.size, "without births the population can only shrink")
    }
}
