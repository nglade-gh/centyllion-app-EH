package com.centyllion.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Expert mode is only required for what normal mode cannot edit: raw predicate
 * formulas and raw action lists. Fields, age predicates, field thresholds,
 * neighbour count predicates (with their grain) and the age inheritance are
 * editable in normal mode, so they must not force expert mode.
 */
class ExpertModeTest {

    private fun modelWith(behaviour: Behaviour, fields: List<Field> = emptyList()) = GrainModel(
        grains = listOf(Grain(id = 0, name = "A", halfLife = 0)),
        behaviours = listOf(behaviour),
        fields = fields
    )

    private fun expert(model: GrainModel) = ModelAndSimulation(model, Simulation.empty).expert

    @Test
    fun testFieldsDoNotRequireExpertMode() {
        // creating a field in normal mode must stay in normal mode
        assertFalse(expert(modelWith(Behaviour(name = "b"), fields = listOf(Field(id = 0, name = "f")))))
    }

    @Test
    fun testAgePredicateAndInheritanceDoNotRequireExpertMode() {
        val behaviour = Behaviour(
            name = "b", agePredicate = Predicate(Operator.GreaterThan, 5), sourceReactive = 0,
            reaction = listOf(Reaction(reactiveId = 1, productId = 1, sourceReactive = 1))
        )
        assertFalse(expert(modelWith(behaviour)))
    }

    @Test
    fun testGraphicalPredicatesDoNotRequireExpertMode() {
        val behaviour = Behaviour(
            name = "b",
            fieldPredicates = listOf(0 to Predicate(Operator.GreaterThan, 0.5f)),
            agentCountPredicates = listOf(AgentCountPredicate(grainId = 0, op = Operator.GreaterThan, count = 2))
        )
        assertFalse(expert(modelWith(behaviour, fields = listOf(Field(id = 0, name = "f")))))
    }

    @Test
    fun testPredicateFormulaRequiresExpertMode() {
        assertTrue(expert(modelWith(Behaviour(name = "b", formula = "count(0) > 2"))))
    }

    @Test
    fun testActionsRequireExpertMode() {
        assertTrue(expert(modelWith(Behaviour(name = "b", actions = "Add(0, 1, 0)"))))
    }

    @Test
    fun testFieldFormulasAloneDoNotRequireExpertMode() {
        // a field driven by a formula is a simulation feature, not a behaviour formula:
        // it does not hide anything from the normal mode behaviour editor
        val field = Field(id = 0, name = "f", formula = "(1+sin(step/25))/2")
        assertFalse(expert(modelWith(Behaviour(name = "b"), fields = listOf(field))))
    }
}
