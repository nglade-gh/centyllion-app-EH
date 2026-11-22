package com.centyllion.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Neighbour counts can target every agent (-1), one grain type, or the **empty** cells (-2),
 * which the formula language spells `countEmpty()`.
 *
 * The last test runs the same one-step simulation twice, once with the graphical predicate
 * and once with the formula, and requires the same result: the two forms are equivalent.
 */
class EmptyCellsCountTest {

    private fun model(count: AgentCountPredicate? = null, formula: String = "") = GrainModel(
        name = "empty cells",
        grains = listOf(Grain(id = 0, name = "cell", color = "Lime", movementProbability = 0.0)),
        behaviours = listOf(
            Behaviour(
                name = if (count != null) "birth (graphical)" else "birth (formula)",
                mainReactiveId = -1,          // empty cell
                mainProductId = 0,            // becomes a cell
                formula = formula,
                agentCountPredicates = listOfNotNull(count),
            )
        ),
    )

    /** A 10x10 grid holding a single cell at the first index. */
    private fun simulation() = createSimulation(size = 10) { if (it == 0) 0 else -1 }.copy(seed = 42)

    private fun step(model: GrainModel): Map<Int, Int> {
        val simulator = Simulator(model, simulation())
        simulator.oneStep()
        return simulator.grainsCounts()
    }

    @Test
    fun testEmptyCellsAreCountedInGraphicalMode() {
        // eight empty neighbours around the cell: the birth fires eight times
        val counts = step(model(AgentCountPredicate(grainId = COUNT_EMPTY_CELLS, op = Operator.Equals, count = 8)))
        assertTrue((counts[0] ?: 0) >= 2, "empty neighbours must be countable: $counts")
    }

    @Test
    fun testTheCountDistinguishesEmptyFromOccupied() {
        // no cell has eight empty neighbours unless it is isolated: asking for 9 never fires
        val counts = step(model(AgentCountPredicate(grainId = COUNT_EMPTY_CELLS, op = Operator.Equals, count = 9)))
        assertEquals(1, counts[0] ?: 0, "no birth may happen with an impossible count: $counts")
    }

    @Test
    fun testCountEmptyIsTheFormulaEquivalent() {
        val graphical = step(model(AgentCountPredicate(grainId = COUNT_EMPTY_CELLS, op = Operator.Equals, count = 8)))
        val formula = step(model(formula = "countEmpty() == 8"))
        assertEquals(graphical, formula, "the formula must give the same result as the graphical predicate")
    }

    @Test
    fun testTheFormulaIsValid() {
        assertEquals(null, validateFormula("countEmpty() == 8"))
        assertEquals(null, validateFormula("countEmpty() >= 2 && countEmpty() <= 6"))
        // arity is not enforced for the dynamic functions (same as count()), the argument
        // is simply ignored: only the name has to be known
        assertEquals(null, validateFormula("countEmpty() >= 1"))
    }

    @Test
    fun testCountOfAllAgentsIsTheFormulaEquivalent() {
        // a cell next to the only agent has exactly one occupied neighbour
        val graphical = step(model(AgentCountPredicate(grainId = COUNT_ALL_AGENTS, op = Operator.Equals, count = 1)))
        val formula = step(model(formula = "count(-1) == 1"))
        assertEquals(graphical, formula, "count(-1) must count every agent, like the graphical predicate")
        assertTrue((graphical[0] ?: 0) > 1, "the neighbours of the lone cell must be born: $graphical")
    }

    @Test
    fun testCountOfAllAgentsIsConvertedBothWays() {
        val behaviour = Behaviour(
            name = "all agents",
            mainReactiveId = -1, mainProductId = 0,
            agentCountPredicates = listOf(
                AgentCountPredicate(grainId = COUNT_ALL_AGENTS, op = Operator.GreaterThanOrEquals, count = 2)
            ),
        )
        assertTrue(BehaviourPredicates.convertible(behaviour))
        val formula = BehaviourPredicates.toFormula(behaviour)
        assertEquals("count(-1) >= 2", formula)
        val back = BehaviourPredicates.fromFormula(formula)
        assertTrue(back != null && back.agentCountPredicates.single().grainId == COUNT_ALL_AGENTS,
            "count(-1) must be read back as an all-agents count: $back")
    }

    @Test
    fun testCountEmptyIsConvertedBothWays() {
        val behaviour = Behaviour(
            name = "empty",
            mainReactiveId = -1, mainProductId = 0,
            agentCountPredicates = listOf(
                AgentCountPredicate(grainId = COUNT_EMPTY_CELLS, op = Operator.GreaterThanOrEquals, count = 3)
            ),
        )
        assertTrue(BehaviourPredicates.convertible(behaviour), "empty cells now have a formula equivalent")
        val formula = BehaviourPredicates.toFormula(behaviour)
        assertEquals("countEmpty() >= 3", formula)
        val back = BehaviourPredicates.fromFormula(formula)
        assertTrue(back != null && back.agentCountPredicates.single().grainId == COUNT_EMPTY_CELLS,
            "the atom must be read back as an empty-cells count: $back")
    }
}
