package com.centyllion.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * `count({0,1,3})`: the number of neighbours whose grain belongs to the list.
 *
 * It completes the `has…` predicates: those answer a truth value over the *set* of types
 * present, while `count({…})` sums the per-type counts, because multiplicity matters —
 * three neighbours of type 0 and one of type 1 make four, not two.
 */
class CountSetTest {

    private val size = 10

    /** 10x10 grid, empty but for the given (x, y, grain) cells. */
    private fun simulation(vararg cells: Triple<Int, Int, Int>): Simulation {
        val agents = MutableList(size * size) { -1 }
        for ((x, y, id) in cells) agents[y * size + x] = id
        return createSimulation(size = size) { agents[it] }.copy(seed = 42)
    }

    /** One behaviour: a grain 0 becomes a grain 1 when its formula holds. */
    private fun model(formula: String, grains: Int = 3) = GrainModel(
        name = "count set",
        grains = (0 until grains).map {
            Grain(id = it, name = "g$it", color = "Lime", movementProbability = 0.0)
        },
        behaviours = listOf(
            Behaviour(name = "rule", mainReactiveId = 0, mainProductId = 1, formula = formula)
        ),
    )

    /** Runs one step and returns the grain counts of the resulting grid. */
    private fun step(formula: String, vararg cells: Triple<Int, Int, Int>): Map<Int, Int> {
        val simulator = Simulator(model(formula), simulation(*cells))
        simulator.oneStep()
        return simulator.grainsCounts()
    }

    @Test
    fun testCountSetSumsMultiplicities() {
        // the cell in the middle is a 0; it has two 0 and one 1 among its neighbours
        val cells = arrayOf(
            Triple(5, 5, 0), Triple(4, 5, 0), Triple(6, 5, 0), Triple(5, 4, 1),
        )
        // 3 neighbours in {0,1}: the rule fires and the middle 0 becomes a 1
        val fired = step("count({0,1}) == 3", *cells)
        assertEquals(2, fired[0], "two 0 left: the rule must have fired")
        assertEquals(2, fired[1], "the middle 0 became a 1")

        // 4 would be the answer of a set-based test, not of a sum: the rule must not fire
        val notFired = step("count({0,1}) == 4", *cells)
        assertEquals(3, notFired[0], "three 0 left: the rule must not fire")
        assertEquals(1, notFired[1])
    }

    @Test
    fun testCountSetAgreesWithCountForOneId() {
        val cells = arrayOf(
            Triple(5, 5, 0), Triple(4, 5, 0), Triple(6, 5, 0), Triple(5, 4, 1),
        )
        assertEquals(
            step("count(0) == 2", *cells),
            step("count({0}) == 2", *cells),
            "count({0}) must mean exactly count(0)",
        )
    }

    @Test
    fun testCountSetDeduplicatesTheList() {
        val cells = arrayOf(Triple(5, 5, 0), Triple(4, 5, 0), Triple(6, 5, 0))
        // the middle cell has two neighbours of type 0: {0,0} must count them twice, not four times
        val counts = step("count({0,0}) == 2", *cells)
        // the middle cell sees two 0 among its neighbours and fires; the two ends see one and stay
        assertEquals(2, counts[0], "the two end cells must remain 0")
        assertEquals(1, counts[1], "the middle cell became 1")
    }

    @Test
    fun testCountSetOfAbsentTypeIsZero() {
        val counts = step("count({2}) == 0", Triple(5, 5, 0))
        assertEquals(null, counts[0], "the rule must fire: count({2}) is 0")
        assertEquals(1, counts[1], "the cell became 1")
    }

    @Test
    fun testCountSetValidatesItsList() {
        assertNull(validatePredicateFormula("count({0,1}) >= 2"), "a grain list of ids is valid")
        assertNull(validatePredicateFormula("count({30}) >= 1"), "no 30-type limit for count({…})")
        assertNotNull(validatePredicateFormula("count({}) >= 1"), "the empty list is refused")
        assertNotNull(validatePredicateFormula("count({-1}) >= 1"), "the sentinel -1 belongs to count(-1)")
        assertNotNull(validatePredicateFormula("count({0,0.5}) >= 1"), "ids are whole numbers")
    }
}
