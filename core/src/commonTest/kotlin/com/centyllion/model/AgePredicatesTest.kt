package com.centyllion.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * "Simulation age" graphical predicate: a behaviour can carry several age
 * predicate lines (normal mode), and all of them must hold at the same time
 * (AND), like the field thresholds and the neighbour count predicates.
 *
 * The legacy single `agePredicate` is still honoured, and is folded into the
 * list by the editor (`migrateLegacyAgePredicate`).
 */
class AgePredicatesTest {

    private fun conversionModel(behaviour: Behaviour) = GrainModel(
        grains = listOf(Grain(id = 0, name = "A", halfLife = 0), Grain(id = 1, name = "B", halfLife = 0)),
        behaviours = listOf(behaviour)
    )

    @Test
    fun testSeveralAgePredicatesAreAnded() {
        // two lines that are only both true for age == 3: with an OR the
        // conversion would already happen at age 1 and 2
        val behaviour = Behaviour(
            name = "A -> B when age is 3",
            mainReactiveId = 0, mainProductId = 1, probability = 1.0,
            agePredicates = listOf(
                Predicate(Operator.GreaterThanOrEquals, 3),
                Predicate(Operator.LessThanOrEquals, 3)
            )
        )
        val simulator = Simulator(conversionModel(behaviour), createSimulation(size = 10) { if (it == 0) 0 else -1 })

        simulator.oneStep() // read age 1
        assertEquals(0, simulator.agents[0], "age 1 is outside the window")
        simulator.oneStep() // read age 2
        assertEquals(0, simulator.agents[0], "age 2 is outside the window (the second line must be honoured)")
        simulator.oneStep() // read age 3
        assertEquals(1, simulator.agents[0], "A becomes B at age 3: both lines hold")
    }

    @Test
    fun testLegacySingleAgePredicateStillApplies() {
        val behaviour = Behaviour(
            name = "A -> B when age >= 2",
            mainReactiveId = 0, mainProductId = 1, probability = 1.0,
            agePredicate = Predicate(Operator.GreaterThanOrEquals, 2)
        )
        val simulator = Simulator(conversionModel(behaviour), createSimulation(size = 10) { if (it == 0) 0 else -1 })

        simulator.oneStep() // read age 1
        assertEquals(0, simulator.agents[0])
        simulator.oneStep() // read age 2
        assertEquals(1, simulator.agents[0], "the legacy single predicate is still evaluated")
    }

    @Test
    fun testLegacyAndListAreAnded() {
        // a legacy predicate together with a list: all must hold
        val behaviour = Behaviour(
            name = "A -> B",
            mainReactiveId = 0, mainProductId = 1, probability = 1.0,
            agePredicate = Predicate(Operator.GreaterThanOrEquals, 1),
            agePredicates = listOf(Predicate(Operator.GreaterThanOrEquals, 4))
        )
        val simulator = Simulator(conversionModel(behaviour), createSimulation(size = 10) { if (it == 0) 0 else -1 })

        repeat(3) { simulator.oneStep() } // read ages 1..3
        assertEquals(0, simulator.agents[0], "the list line (age >= 4) is not satisfied yet")
        simulator.oneStep() // read age 4
        assertEquals(1, simulator.agents[0])
    }

    @Test
    fun testMigrationOfTheLegacyPredicate() {
        val legacy = Behaviour(name = "b", agePredicate = Predicate(Operator.GreaterThan, 5))
        val migrated = legacy.migrateLegacyAgePredicate()

        assertEquals(listOf(Predicate(Operator.GreaterThan, 5)), migrated.agePredicates)
        assertTrue(migrated.hasDefaultAgePredicate, "the legacy field is reset to the neutral default")
        assertEquals(migrated, migrated.migrateLegacyAgePredicate(), "migration must be idempotent")

        val untouched = Behaviour(name = "b")
        assertEquals(untouched, untouched.migrateLegacyAgePredicate(), "a default predicate is left untouched")

        // a legacy predicate is prepended to the existing lines
        val both = Behaviour(
            name = "b", agePredicate = Predicate(Operator.GreaterThan, 5),
            agePredicates = listOf(Predicate(Operator.LessThan, 10))
        ).migrateLegacyAgePredicate()
        assertEquals(
            listOf(Predicate(Operator.GreaterThan, 5), Predicate(Operator.LessThan, 10)),
            both.agePredicates
        )
    }
}
