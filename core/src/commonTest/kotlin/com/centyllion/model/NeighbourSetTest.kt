package com.centyllion.model

import com.centyllion.expression.parseExpression
import com.centyllion.expression.v
import com.centyllion.i18n.English
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The four set predicates over the agent types present in the **eight neighbours** of the cell:
 *
 * - `hasAnyOf({ids})`: at least one listed type is present;
 * - `hasAllOf({ids})`: every listed type is present;
 * - `hasOnly({ids})`: no type outside the list is present (the listed ones may be absent);
 * - `hasExactly({ids})`: exactly the listed types, neither more nor fewer.
 *
 * The list `{0,1,3}` is a syntax for this single argument: the neighbourhood becomes a bit mask
 * of the types present (bit `id` = type `id`), and the four answers are bit tests over two
 * ordinary numbers — no new type in the expression language. Because the mask is a 32-bit
 * integer with bit 31 as the sign bit, at most 30 types (ids 0 to 29) can be represented, and a
 * model that declares more is reported by the diagnostic rather than silently truncated.
 */
class NeighbourSetTest {

    private val size = 10

    /** 10x10 grid, empty but for the given (x, y, grain) cells. */
    private fun simulation(vararg cells: Triple<Int, Int, Int>): Simulation {
        val agents = MutableList(size * size) { -1 }
        for ((x, y, id) in cells) agents[y * size + x] = id
        return createSimulation(size = size) { agents[it] }.copy(seed = 42)
    }

    /** One behaviour: a grain 0 becomes a grain 1 when its formula holds. */
    private fun model(formula: String, grains: Int = 3) = GrainModel(
        name = "neighbour set",
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

    /**
     * Same, with a rule `2 -> 1`: it probes whether a grain 2 sees itself, since a rule `0 -> 1`
     * can only show what the neighbours of the grain 0 hold.
     */
    private fun stepOn2(formula: String, vararg cells: Triple<Int, Int, Int>): Map<Int, Int> {
        val behaviour = Behaviour(name = "rule", mainReactiveId = 2, mainProductId = 1, formula = formula)
        val simulator = Simulator(model(formula).copy(behaviours = listOf(behaviour)), simulation(*cells))
        simulator.oneStep()
        return simulator.grainsCounts()
    }

    /**
     * Evaluates a formula against a hand-made neighbourhood: the mask is the types present among
     * the eight neighbours, published by the simulator under [NetbioDynEvaluator.agentCounts].
     */
    private fun evaluate(formula: String, neighbours: Map<Int, Int>): Boolean {
        val evaluator = NetbioDynEvaluator(null)
        evaluator.agentCounts = neighbours
        return evaluator.evaluate(formula.parseExpression()).boolean
    }

    // --- truth table of the four predicates ------------------------------------------------

    @Test
    fun hasAnyOfIsTrueAsSoonAsOneListedTypeIsPresent() {
        val around01 = mapOf(0 to 1, 2 to 3)
        assertTrue(evaluate("hasAnyOf({0})", around01), "type 0 is there")
        assertTrue(evaluate("hasAnyOf({1,2})", around01), "type 2 is there")
        assertFalse(evaluate("hasAnyOf({1})", around01), "type 1 is absent")
        // an empty neighbourhood answers false
        assertFalse(evaluate("hasAnyOf({0})", emptyMap()))
        // the sentinels of count() are not types: they are ignored
        assertFalse(evaluate("hasAnyOf({0})", mapOf(-1 to 8, -2 to 0)))
    }

    @Test
    fun hasAllOfRequiresEveryListedType() {
        val around01 = mapOf(0 to 1, 1 to 2, 2 to 1)
        assertTrue(evaluate("hasAllOf({0,1})", around01), "0 and 1 are both there")
        assertTrue(evaluate("hasAllOf({0})", around01), "a single listed type present")
        assertFalse(evaluate("hasAllOf({0,3})", around01), "3 is absent")
        assertFalse(evaluate("hasAllOf({0,1})", mapOf(0 to 1)), "1 is absent")
    }

    @Test
    fun hasOnlyForbidsEveryTypeOutsideTheList() {
        val around01 = mapOf(0 to 1, 1 to 2)
        assertTrue(evaluate("hasOnly({0,1})", around01), "the types present are all listed")
        assertTrue(evaluate("hasOnly({0,1,2})", around01), "a bigger list is still a superset")
        // absence is allowed, presence of a foreign type is not: {2,3} listed while 0 is there
        assertFalse(evaluate("hasOnly({2,3})", mapOf(0 to 1)), "0 is present and not listed")
        assertTrue(evaluate("hasOnly({0,2,3})", mapOf(0 to 1)), "0 is listed, 2 and 3 may be absent")
        assertTrue(evaluate("hasOnly({2})", emptyMap()), "an empty neighbourhood has no foreign type")
        assertFalse(evaluate("hasOnly({0})", around01), "type 1 is present and not listed")
    }

    @Test
    fun hasExactlyRequiresBothDirections() {
        val around01 = mapOf(0 to 1, 1 to 2)
        assertTrue(evaluate("hasExactly({0,1})", around01), "exactly 0 and 1")
        assertTrue(evaluate("hasExactly({1,0})", around01), "order and repetitions do not matter")
        assertTrue(evaluate("hasExactly({1,0,1})", around01), "a repeated id is the same bit")
        assertFalse(evaluate("hasExactly({0})", around01), "1 is present and not listed")
        assertFalse(evaluate("hasExactly({0,1,2})", around01), "2 is listed but absent")
        assertFalse(evaluate("hasExactly({0})", emptyMap()), "the empty neighbourhood is not {0}")
    }

    @Test
    fun theFourPredicatesReadTheSameNeighbourhood() {
        // same neighbourhood, four different questions
        val around = mapOf(0 to 1, 1 to 1, 2 to 6)
        assertTrue(evaluate("hasAnyOf({0,1,2})", around))
        assertTrue(evaluate("hasAllOf({0,1,2})", around))
        assertTrue(evaluate("hasOnly({0,1,2})", around))
        assertTrue(evaluate("hasExactly({0,1,2})", around))
        assertTrue(evaluate("hasAnyOf({0}) && hasAllOf({0}) && hasOnly({0,1,2})", around))
        assertFalse(evaluate("hasExactly({0,1}) && hasAnyOf({2})", around))
    }

    // --- the mask follows the eight neighbours of a real grid ------------------------------

    @Test
    fun thePredicatesFireOnTheNeighboursOfARealSimulation() {
        // the grain 0 at (1,1) touches a 1 at (2,2): the rule fires for it alone
        val counts = step("hasAnyOf({1})", Triple(1, 1, 0), Triple(2, 2, 1), Triple(8, 8, 0))
        assertEquals(2, counts[1] ?: 0, "the 0 next to the grain 1 must become a 1: $counts")
        assertEquals(1, counts[0] ?: 0, "the isolated 0 must stay a 0: $counts")
    }

    @Test
    fun theCentralCellIsNotOneOfTheEightNeighbours() {
        // a lone grain 2 under hasAnyOf({2}): the only type present is the cell itself
        val lone = stepOn2("hasAnyOf({2})", Triple(5, 5, 2))
        assertEquals(1, lone[2] ?: 0, "the cell itself must not enter the mask: $lone")
        assertEquals(0, lone[1] ?: 0, "nothing may be converted: $lone")
        // two adjacent 2s see each other: the mask does hold a type of the neighbourhood
        val pair = stepOn2("hasAnyOf({2})", Triple(5, 5, 2), Triple(6, 5, 2))
        assertEquals(2, pair[1] ?: 0, "two 2s next to each other are not alone: $pair")
        assertEquals(0, pair[2] ?: 0, "both cells must be converted: $pair")
    }

    @Test
    fun aTypePresentOnlyAtTheCentreDoesNotEnterTheMask() {
        // the 0 next to a 2 sees {2} and is refused by hasOnly({0}); the isolated 0 sees nothing
        // (an empty neighbourhood has no foreign type) and converts
        val counts = step("hasOnly({0})", Triple(5, 5, 2), Triple(4, 5, 0), Triple(9, 9, 0))
        assertEquals(1, counts[0] ?: 0, "the 0 touching the 2 must stay a 0: $counts")
        assertEquals(1, counts[1] ?: 0, "the isolated 0 converts: $counts")
    }

    @Test
    fun theMaskWrapsAroundTheEdges() {
        // (0,0) and (9,9) are diagonal neighbours on a torus: each one sees the other
        val counts = step("hasAnyOf({0}) && hasExactly({0})", Triple(0, 0, 0), Triple(9, 9, 0))
        assertEquals(2, counts[1] ?: 0, "the edges of the grid are toric: $counts")
        assertEquals(0, counts[0] ?: 0, "both cells must be converted: $counts")
    }

    // --- composition with the other predicates ---------------------------------------------

    @Test
    fun thePredicatesComposeWithTheOtherPredicates() {
        assertNull(
            validatePredicateFormula("hasAnyOf({0,1}) && step > 3"),
            "the four functions are typed boolean, otherwise && is refused"
        )
        assertNull(validatePredicateFormula("hasAnyOf({0}) || count(2) > 3"))
        assertNull(validatePredicateFormula("!hasAllOf({0,1}) ? true : hasExactly({2})"))
        // and the composition runs: the grain 0 converts only from step 4 on (the first step is 0)
        val simulator = Simulator(
            model("hasAnyOf({1}) && step > 3"),
            simulation(Triple(1, 1, 0), Triple(2, 1, 1))
        )
        repeat(4) { simulator.oneStep() }
        assertEquals(1, simulator.grainsCounts()[0] ?: 0, "up to step 3 the rule must not fire")
        simulator.oneStep()
        assertEquals(2, simulator.grainsCounts()[1] ?: 0, "from step 4 on the rule fires")
    }

    // --- the list is refused everywhere else ----------------------------------------------

    @Test
    fun anEmptyListIsRefused() {
        for (name in listOf("hasAnyOf", "hasAllOf", "hasOnly", "hasExactly")) {
            val error = validatePredicateFormula("$name({})")
            assertNotNull(error, "$name({{}}) must be refused")
            assertTrue(error.contains("empty list"), "the message must explain the refusal, got: $error")
        }
    }

    @Test
    fun aListIsRefusedAsAValue() {
        for (formula in listOf(
            "{0,1}",
            "count(0) + {1}",
            "{0} == {0}",
            "field({0})",
            // count({0}) is no longer a list used as a value: it sums the counts (see CountSetTest)
            "hasAnyOf({0}) ? {1} : false",
        )) {
            val error = validatePredicateFormula(formula)
            assertNotNull(error, "$formula must be refused: a list is only an argument")
            assertTrue(
                error.contains("may only be the argument"),
                "the message must name the rule, got: $error for $formula"
            )
        }
    }

    @Test
    fun aListIsRefusedInAFieldFormulaToo() {
        assertNotNull(validateNumericFormula("{0,1}"), "a list is not a number")
        assertNull(
            validateNumericFormula("hasAnyOf({0,1}) ? 1 : current"),
            "the four predicates are usable in a field formula"
        )
    }

    @Test
    fun anUnknownOrInvalidIdIsRefused() {
        // no model given: the syntax is checked, the id's existence is the model's business
        assertNotNull(validatePredicateFormula("hasAnyOf({30})"), "the mask cannot hold id 30")
        assertNotNull(validatePredicateFormula("hasAnyOf({-1})"), "count() sentinels are not types")
        assertNotNull(validatePredicateFormula("hasAnyOf({0.5})"), "an id is a whole number")
        assertNotNull(validatePredicateFormula("hasAnyOf({step})"), "a grain list is constant")
        assertNotNull(validatePredicateFormula("hasAnyOf(0)"), "a bare id is not a list")
        assertNotNull(validatePredicateFormula("hasAnyOf({0},{1})"), "one list only")
        // with the model: an id that no grain carries is refused
        val error = validatePredicateFormula("hasAnyOf({0,7})", setOf(0, 1, 2))
        assertNotNull(error, "id 7 does not exist in a three-grain model")
        assertTrue(error.contains("doesn't exist"), "the message must say so, got: $error")
        assertNull(validatePredicateFormula("hasAnyOf({0,2})", setOf(0, 1, 2)))
    }

    @Test
    fun aMalformedCallFailsAtRunTimeToo() {
        // a formula that escaped the validation must not answer a wrong result
        val evaluator = NetbioDynEvaluator(null).also { it.agentCounts = mapOf(1 to 1) }
        for (formula in listOf("hasAnyOf(0)", "hasAnyOf({})", "hasAnyOf({31})", "hasAnyOf({step})")) {
            val result = runCatching { evaluator.evaluate(formula.parseExpression()) }
            assertTrue(result.isFailure, "$formula must fail at run time: ${result.getOrNull()}")
        }
        // a list used as a value is refused by the base evaluator as well
        val result = runCatching { evaluator.evaluate("count(0) + {1}".parseExpression()) }
        assertTrue(result.isFailure, "a list has no value: ${result.getOrNull()}")
    }

    @Test
    fun aModifierConditionRefusesTheNeighbourhoodPredicates() {
        // a modifier runs outside any cell: the counts are not filled there
        val error = validateModifierCondition("hasAnyOf({0})")
        assertNotNull(error, "the set predicates belong to a behaviour or a field formula")
        assertTrue(error.contains("eight neighbours"), "the message must say why, got: $error")
        assertNull(validateModifierCondition("step % 10 == 0"))
    }

    // --- the diagnostic of the 30-type limit ------------------------------------------------

    @Test
    fun aModelWithMoreThanThirtyTypesIsReported() {
        val formula = "hasAnyOf({0,29})"
        // 30 types: the mask holds every one of them
        assertNull(
            model(formula, grains = NEIGHBOUR_SET_MAX_TYPES).diagnose(English)
                .firstOrNull { it.message.contains("at most") },
            "a 30-type model must not be reported"
        )
        // 31 types: the mask cannot describe them all, and the diagnostic says so
        val problems = model(formula, grains = NEIGHBOUR_SET_MAX_TYPES + 1).diagnose(English)
        val limit = problems.firstOrNull { it.property == "Predicate" && it.message.contains("at most") }
        assertNotNull(limit, "a 31-type model must be reported on the formula: $problems")
        assertTrue(
            limit.message.contains("${NEIGHBOUR_SET_MAX_TYPES + 1}"),
            "the message must give the number of types, got: ${limit.message}"
        )
        assertTrue(limit.message.contains("count()"), "the message must suggest an alternative")
    }

    @Test
    fun aThirtyOneTypeModelIsFineWithoutTheSetPredicates() {
        // the limit only concerns the four predicates: a count() formula on 31 types is valid
        val problems = model("count(0) > 2", grains = NEIGHBOUR_SET_MAX_TYPES + 1).diagnose(English)
        assertTrue(
            problems.none { it.message.contains("at most") },
            "count() has no mask limit: $problems"
        )
    }

    @Test
    fun theFormulaOfAFieldIsDiagnosedToo() {
        val model = GrainModel(
            name = "field limit",
            grains = (0..NEIGHBOUR_SET_MAX_TYPES).map {
                Grain(id = it, name = "g$it", color = "Lime", movementProbability = 0.0)
            },
            fields = listOf(
                Field(id = 0, name = "F", formula = "hasAnyOf({0}) ? 1 : current")
            ),
        )
        val limit = model.diagnose(English).firstOrNull { it.property == "Formula" && it.message.contains("at most") }
        assertNotNull(limit, "the field formula must be reported: ${model.diagnose(English)}")
    }

    // --- the mask itself --------------------------------------------------------------------

    @Test
    fun theNeighbourhoodMaskHasOneBitPerType() {
        val evaluator = NetbioDynEvaluator(null)
        evaluator.agentCounts = mapOf(0 to 1, 2 to 3, 29 to 1, -1 to 5, -2 to 3)
        assertEquals((1 shl 0) or (1 shl 2) or (1 shl 29), evaluator.neighbourTypesMask)
        assertTrue(evaluator.neighbourTypesMask > 0, "bit 29 must remain a positive number")
        // a type with a zero count is not present
        evaluator.agentCounts = mapOf(3 to 0)
        assertEquals(0, evaluator.neighbourTypesMask)
    }

    @Test
    fun theEvaluationIsAvailableToFieldFormulas() {
        val field = FormulaFieldEvaluator(
            Field(id = 0, name = "F"), "hasAnyOf({0}) ? 1 : current", NetbioDynEvaluator(null)
        )
        assertTrue(field.usesCount, "the set predicates read the neighbour counts")
    }

    @Test
    fun thePredicatesDriveAFieldFormula() {
        // the field paints the cells that touch a grain 0
        val model = GrainModel(
            name = "field set",
            grains = listOf(Grain(id = 0, name = "a", color = "Lime", movementProbability = 0.0)),
            fields = listOf(
                Field(id = 0, name = "F", speed = 0.0f, halfLife = 0, formula = "hasAnyOf({0}) ? 1 : current")
            ),
        )
        val simulator = Simulator(model, simulation(Triple(5, 5, 0)))
        simulator.oneStep()
        val values = simulator.fields[0]!!
        assertEquals(1f, values[5 * size + 4], 1e-6f, "the west neighbour of the agent must be painted")
        assertEquals(0f, values[5 * size + 5], 1e-6f, "the cell of the agent itself is not a neighbour")
        assertEquals(0f, values[8 * size + 8], 1e-6f, "a cell far from the agent keeps its value")
    }

    // --- the syntax keeps the source text -----------------------------------------------

    @Test
    fun theListKeepsItsSourceText() {
        val formula = "hasAnyOf({0, 1,3}) && step > 2"
        assertEquals(formula, formula.parseExpression().text(), "the list must be printed back as written")
    }
}
