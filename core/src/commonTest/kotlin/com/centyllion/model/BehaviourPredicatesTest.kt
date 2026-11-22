package com.centyllion.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Switching between graphical predicates and the predicate formula must not change the
 * meaning of a behaviour: the two forms are ANDed by the simulator, so only one of them
 * may be active at a time.
 */
class BehaviourPredicatesTest {

    private fun behaviour(
        age: List<Predicate<Int>> = emptyList(),
        counts: List<AgentCountPredicate> = emptyList(),
        fields: List<Pair<Int, Predicate<Float>>> = emptyList(),
        mainReactiveId: Int = 0,
    ) = Behaviour(
        name = "test",
        mainReactiveId = mainReactiveId,
        mainProductId = 0,
        agePredicates = age,
        agentCountPredicates = counts,
        fieldPredicates = fields,
    )

    @Test
    fun testNoGraphicalPredicateGivesNoFormula() {
        assertEquals("", BehaviourPredicates.toFormula(behaviour()))
    }

    @Test
    fun testAgeCountAndFieldAreWrittenAsAtoms() {
        val model = behaviour(
            age = listOf(Predicate(Operator.GreaterThan, 4)),
            counts = listOf(AgentCountPredicate(grainId = 0, op = Operator.Equals, count = 3)),
            fields = listOf(7 to Predicate(Operator.GreaterThanOrEquals, 0.5f)),
        )
        assertEquals("age(1) > 4 && count(0) == 3 && field(7) >= 0.5", BehaviourPredicates.toFormula(model))
    }

    @Test
    fun testAGenerationBehaviourUsesTheStep() {
        val model = behaviour(age = listOf(Predicate(Operator.LessThanOrEquals, 10)), mainReactiveId = -1)
        assertEquals("step <= 10", BehaviourPredicates.toFormula(model))
    }

    @Test
    fun testTheLegacyAgePredicateIsKeptWhenItIsNotNeutral() {
        val model = behaviour().copy(agePredicate = Predicate(Operator.GreaterThan, 2))
        assertEquals("age(1) > 2", BehaviourPredicates.toFormula(model))
    }

    @Test
    fun testRoundTripGraphicalToFormulaAndBack() {
        val model = behaviour(
            age = listOf(Predicate(Operator.GreaterThan, 4), Predicate(Operator.LessThan, 12)),
            counts = listOf(
                AgentCountPredicate(grainId = 0, op = Operator.GreaterThanOrEquals, count = 2),
                AgentCountPredicate(grainId = 3, op = Operator.NotEquals, count = 1),
            ),
            fields = listOf(7 to Predicate(Operator.LessThan, 0.25f)),
        )
        val formula = BehaviourPredicates.toFormula(model)
        val back = assertNotNull(BehaviourPredicates.fromFormula(formula))
        assertEquals(model.agePredicates, back.agePredicates)
        assertEquals(model.agentCountPredicates, back.agentCountPredicates)
        assertEquals(model.fieldPredicates, back.fieldPredicates)
        // and the other way round is stable too
        assertEquals(formula, BehaviourPredicates.toFormula(model.copy(
            agePredicates = back.agePredicates,
            agentCountPredicates = back.agentCountPredicates,
            fieldPredicates = back.fieldPredicates,
        )))
    }

    @Test
    fun testAllAgentCountCannotBeConverted() {
        val model = behaviour(counts = listOf(AgentCountPredicate(grainId = -1, op = Operator.Equals, count = 2)))
        assertFalse(BehaviourPredicates.convertible(model), "count over every agent has no formula equivalent")
    }

    @Test
    fun testPerGrainCountCanBeConverted() {
        assertTrue(BehaviourPredicates.convertible(
            behaviour(counts = listOf(AgentCountPredicate(grainId = 2, op = Operator.Equals, count = 2)))
        ))
    }

    @Test
    fun testADisjunctionForbidsTheReturnToGraphicalMode() {
        assertNull(BehaviourPredicates.fromFormula("count(0) == 3 || count(0) == 4"))
        assertNull(BehaviourPredicates.fromFormula("count(0) == 3 && (step > 2 || step < 1)"))
    }

    @Test
    fun testUnsupportedFunctionsForbidTheReturnToGraphicalMode() {
        assertNull(BehaviourPredicates.fromFormula("sin(step) > 0.5"))
        assertNull(BehaviourPredicates.fromFormula("agent(1, 2) == 3"))
        assertNull(BehaviourPredicates.fromFormula("count(0) + 1 == 3"))
        assertNull(BehaviourPredicates.fromFormula("!count(0) == 3"))
    }

    @Test
    fun testAPureConjunctionIsAccepted() {
        val parsed = assertNotNull(BehaviourPredicates.fromFormula("count(0) == 3 && age(1) >= 2 && field(1) > 0.1"))
        assertEquals(1, parsed.agePredicates.size)
        assertEquals(1, parsed.agentCountPredicates.size)
        assertEquals(1, parsed.fieldPredicates.size)
    }

    @Test
    fun testAnEmptyFormulaGivesNoPredicate() {
        val parsed = assertNotNull(BehaviourPredicates.fromFormula("   "))
        assertTrue(parsed.isEmpty)
    }

    @Test
    fun testSpacesAndParenthesesAreTolerated() {
        val parsed = assertNotNull(BehaviourPredicates.fromFormula("( count( 2 ) == 1 )"))
        assertEquals(AgentCountPredicate(2, Operator.Equals, 1), parsed.agentCountPredicates.single())
    }
}
