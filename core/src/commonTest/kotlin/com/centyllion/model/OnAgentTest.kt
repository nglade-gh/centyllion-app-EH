package com.centyllion.model

import com.centyllion.expression.parseExpression
import com.centyllion.expression.v
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `onAgent(id)` asks whether one of the **eight neighbours** of the cell holds that grain (the
 * cell itself is not counted, like `count`), and `onAgent(id, dx, dy)` asks it about a single
 * cell at an offset in [-1, 1] — the cell itself included for (0, 0). Both return a boolean, so
 * they compose with `&&`, `||` and `!`, and both wrap around the grid.
 *
 * The tests run real one-step simulations: the behaviour turns a grain 0 into a grain 1, so the
 * result of the formula is read on the grid (`grain 1` where it fired) rather than on a mock.
 */
class OnAgentTest {

    private val size = 10

    /** 10x10 grid, empty but for the given (x, y, grain) cells. */
    private fun simulation(vararg cells: Triple<Int, Int, Int>): Simulation {
        val agents = MutableList(size * size) { -1 }
        for ((x, y, id) in cells) agents[y * size + x] = id
        return createSimulation(size = size) { agents[it] }.copy(seed = 42)
    }

    /** One behaviour: a grain 0 becomes a grain 1 when its formula holds. */
    private fun model(formula: String) = GrainModel(
        name = "onAgent",
        grains = listOf(
            Grain(id = 0, name = "a", color = "Lime", movementProbability = 0.0),
            Grain(id = 1, name = "b", color = "Cyan", movementProbability = 0.0),
            Grain(id = 2, name = "c", color = "Orange", movementProbability = 0.0),
        ),
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

    // --- onAgent(id): the eight neighbours -------------------------------------------------

    @Test
    fun onAgentSeesANeighbourOfTheGivenGrain() {
        // the grain 0 at (1,1) has a grain 1 at (2,2): the rule fires for it only
        val counts = step("onAgent(1)", Triple(1, 1, 0), Triple(2, 2, 1), Triple(8, 8, 0))
        assertEquals(2, counts[1] ?: 0, "the 0 next to the grain 1 must become a 1: $counts")
        assertEquals(1, counts[0] ?: 0, "the isolated 0 must stay a 0: $counts")
    }

    @Test
    fun onAgentIgnoresAnotherGrain() {
        // grain 2 is not grain 1: no neighbour of the asked type, nothing fires
        val counts = step("onAgent(1)", Triple(1, 1, 0), Triple(2, 2, 2), Triple(8, 8, 0))
        assertEquals(0, counts[1] ?: 0, "a grain 2 must not answer onAgent(1): $counts")
        assertEquals(2, counts[0] ?: 0, "no cell may be converted: $counts")
    }

    @Test
    fun onAgentDoesNotCountTheCentralCell() {
        // a lone grain 0 is its own neighbour only if the cell itself were counted
        val counts = step("onAgent(0)", Triple(5, 5, 0))
        assertEquals(1, counts[0] ?: 0, "the cell itself must not be counted: $counts")
        assertEquals(0, counts[1] ?: 0, "nothing may be converted: $counts")
    }

    @Test
    fun onAgentCountsTheNeighboursAcrossTheEdges() {
        // (0,0) and (9,9) are diagonal neighbours on a torus: both see each other
        val counts = step("onAgent(0)", Triple(0, 0, 0), Triple(9, 9, 0))
        assertEquals(2, counts[1] ?: 0, "the edges of the grid are toric: $counts")
        assertEquals(0, counts[0] ?: 0, "both cells must be converted: $counts")
    }

    // --- onAgent(id, dx, dy): one cell, the centre included ---------------------------------

    @Test
    fun onAgentAtTheCentreSeesTheCellItself() {
        // the three-argument form includes (0, 0): the lone grain 0 answers for itself
        val counts = step("onAgent(0, 0, 0)", Triple(5, 5, 0))
        assertEquals(1, counts[1] ?: 0, "onAgent(0, 0, 0) must see the cell itself: $counts")
        assertEquals(0, counts[0] ?: 0, "the rule must have fired: $counts")
    }

    @Test
    fun onAgentAtTheCentreIgnoresAnotherGrain() {
        val counts = step("onAgent(1, 0, 0)", Triple(5, 5, 0))
        assertEquals(0, counts[1] ?: 0, "the cell holds a grain 0, not a grain 1: $counts")
        assertEquals(1, counts[0] ?: 0, "nothing may be converted: $counts")
    }

    @Test
    fun onAgentOffsetsWrapAroundTheGrid() {
        // (0,3) reads its west neighbour: (9,3), the last column of the grid
        val west = step("onAgent(1, -1, 0)", Triple(0, 3, 0), Triple(9, 3, 1))
        assertEquals(2, west[1] ?: 0, "the offset -1 on the left edge must read the last column: $west")
        assertEquals(0, west[0] ?: 0, "the grain 0 must have been converted: $west")
        // the same grid with the east offset reads an empty cell: the wrap is what made the
        // difference, not the presence of a grain 1 somewhere in the grid
        val east = step("onAgent(1, 1, 0)", Triple(0, 3, 0), Triple(9, 3, 1))
        assertEquals(1, east[1] ?: 0, "the east neighbour of (0,3) is empty: $east")
        assertEquals(1, east[0] ?: 0, "nothing may be converted: $east")
    }

    @Test
    fun onAgentReadsTheDiagonalOffsetsToo() {
        val counts = step("onAgent(1, -1, 1)", Triple(0, 0, 0), Triple(9, 1, 1))
        assertEquals(2, counts[1] ?: 0, "the south-west neighbour of (0,0) is (9,1): $counts")
        assertEquals(0, counts[0] ?: 0, "the grain 0 must have been converted: $counts")
    }

    // --- typing: the two forms are predicates ----------------------------------------------

    @Test
    fun onAgentIsAcceptedAsAPredicate() {
        assertNull(validatePredicateFormula("onAgent(0)"), "onAgent(id) alone is a predicate")
        assertNull(validatePredicateFormula("onAgent(0, 1, 0)"), "onAgent(id, dx, dy) alone is a predicate")
        assertNull(validateFormula("onAgent(0)"), "the generic validation must accept it")
    }

    @Test
    fun onAgentComposesWithLogicalOperators() {
        assertNull(
            validatePredicateFormula("onAgent(0) && !onAgent(1)"),
            "onAgent must be typed boolean, otherwise && is refused"
        )
        assertNull(validatePredicateFormula("onAgent(0) || count(0) > 3"))
        assertNull(validatePredicateFormula("onAgent(0, 0, 1) ? count(1) > 0 : true"))
        // and the composition runs: a grain 0 touching another 0, and one alone
        val counts = step("onAgent(0) && !onAgent(1)", Triple(1, 1, 0), Triple(2, 1, 0), Triple(8, 8, 0))
        assertEquals(1, counts[0] ?: 0, "the isolated 0 has no 0 neighbour: $counts")
        assertEquals(2, counts[1] ?: 0, "both grains 0 that touch each other convert: $counts")
    }

    @Test
    fun onAgentIsUsableInAFieldFormula() {
        // a field formula that uses onAgent must ask the simulator for the neighbour counts
        val evaluator = FormulaFieldEvaluator(
            Field(id = 0, name = "F"), "onAgent(0) ? 1 : current", NetbioDynEvaluator(null)
        )
        assertTrue(evaluator.usesCount, "onAgent(id) reads the neighbour counts")
        assertNull(validateNumericFormula("onAgent(0) ? 1 : current"))
    }

    @Test
    fun onAgentDrivesAFieldFormula() {
        // the field paints the cells that touch a grain 0: without the counts published for the
        // field formulas (usesCount), every cell would answer false and stay at 0
        val model = GrainModel(
            name = "field",
            grains = listOf(Grain(id = 0, name = "a", color = "Lime", movementProbability = 0.0)),
            fields = listOf(
                Field(id = 0, name = "F", speed = 0.0f, halfLife = 0, formula = "onAgent(0) ? 1 : current")
            ),
        )
        val simulator = Simulator(model, simulation(Triple(5, 5, 0)))
        simulator.oneStep()
        val values = simulator.fields[0]!!
        assertEquals(1f, values[5 * size + 4], 1e-6f, "the west neighbour of the agent must be painted")
        assertEquals(1f, values[4 * size + 5], 1e-6f, "the north neighbour of the agent must be painted")
        assertEquals(0f, values[5 * size + 5], 1e-6f, "the cell of the agent itself is not a neighbour")
        assertEquals(0f, values[8 * size + 8], 1e-6f, "a cell far from the agent keeps its value")
    }

    // --- offsets outside [-1, 1] ------------------------------------------------------------

    @Test
    fun onAgentRefusesAnOffsetOutsideTheNeighbourhood() {
        val error = validatePredicateFormula("onAgent(0, 5, 5)")
        assertNotNull(error, "an offset of 5 must be refused by the validator")
        assertTrue(error.contains("-1, 0 or 1"), "the message must give the bounds, got: $error")
        assertNotNull(validateFormula("onAgent(0, 2, 0)"), "dx = 2 is out of bounds")
        assertNotNull(validateFormula("onAgent(0, 0, -2)"), "dy = -2 is out of bounds")
        assertNull(
            validateNumericFormula("onAgent(0, 1, 0) ? 1 : current"),
            "a valid offset must still be accepted in a field formula"
        )
        assertNotNull(validateFormula("onAgent(0, 0.5, 0)"), "a fractional offset is not a cell offset")
    }

    @Test
    fun onAgentRefusesAWrongNumberOfArguments() {
        assertNotNull(validateFormula("onAgent()"), "the id is required")
        assertNotNull(validateFormula("onAgent(0, 0)"), "two arguments are not 1 nor 3")
        assertNotNull(validateFormula("onAgent(0, 1, 0, 1)"), "four arguments are not 1 nor 3")
    }

    @Test
    fun theEvaluatorRefusesAComputedOffsetAtRunTime() {
        // a computed offset escapes the static check: the evaluator still refuses it, it does
        // not read an arbitrary cell
        val evaluator = NetbioDynEvaluator(null).also { it.step = 5.v }
        val result = runCatching { evaluator.evaluate("onAgent(0, step, 0)".parseExpression()) }
        assertTrue(result.isFailure, "an offset of 5 must fail: ${result.getOrNull()}")
        assertTrue(
            result.exceptionOrNull()?.message?.contains("-1, 0 or 1") == true,
            "the message must give the bounds, got: ${result.exceptionOrNull()?.message}"
        )
    }
}
