package com.centyllion.model

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reference implementations of the two other expert tutorials:
 *
 * - "Actions: a trail that fades": a moving agent leaves a crumb behind it
 *   (`Add(1, 0, 1)`) and the crumb disappears after six steps (`age(1) > 5`),
 * - "Field formula: a moving gradient": the field value is computed by
 *   `0.5 + 0.5 * sin(x / 10 + step / 5)`.
 *
 * The formulas are the exact strings the tutorials ask the user to type.
 */
class ExpertRecipesTest {

    // ---------------------------------------------------------------- actions

    private fun trailModel() = GrainModel(
        name = "Trail", description = "ant trail",
        grains = listOf(
            Grain(id = 0, name = "runner", movementProbability = 1.0),
            Grain(id = 1, name = "crumb", movementProbability = 0.0)
        ),
        behaviours = listOf(
            Behaviour(
                "run", mainReactiveId = 0, mainProductId = 0,
                actions = "Add(1, 0, 1)"
            ),
            Behaviour(
                "fade", mainReactiveId = 1, mainProductId = -1,
                formula = "age(1) > 5"
            )
        )
    )

    private fun counts(simulator: Simulator) = simulator.grainsCounts()

    @Test
    fun testTrailIsCreatedAndFades() {
        val size = 30
        // a single runner in the middle of the grid
        val simulation = createSimulation("trail", size = size) { index ->
            if (index == size * size / 2 + size / 2) 0 else -1
        }
        val simulator = Simulator(trailModel(), simulation)

        assertEquals(0, counts(simulator)[1] ?: 0, "no crumb at the beginning")

        var maximum = 0
        repeat(12) {
            simulator.oneStep()
            maximum = maxOf(maximum, counts(simulator)[1] ?: 0)
        }

        assertTrue(maximum >= 2, "the runner must leave a trail (maximum crumbs = $maximum)")

        // the crumbs fade: the trail cannot grow forever (6 steps of life, one per step)
        val crumbs = counts(simulator)[1] ?: 0
        assertTrue(crumbs in 1..8, "the trail must stay short (crumbs = $crumbs)")

        // the runner is still there
        assertEquals(1, counts(simulator)[0] ?: 0, "the runner must survive")
    }

    /** The `age(1) > 5` rule alone: a crumb lives six steps and then disappears. */
    @Test
    fun testCrumbDisappearsAfterSixSteps() {
        val size = 10
        val model = GrainModel(
            name = "crumb", description = "fading crumb",
            grains = listOf(Grain(id = 0, name = "crumb", movementProbability = 0.0)),
            behaviours = listOf(
                Behaviour("fade", mainReactiveId = 0, mainProductId = -1, formula = "age(1) > 5")
            )
        )
        val simulation = createSimulation("crumb", size = size) { index ->
            if (index == size * size / 2 + size / 2) 0 else -1
        }
        val simulator = Simulator(model, simulation)

        repeat(5) { simulator.oneStep() }
        assertEquals(1, simulator.grainsCounts()[0] ?: 0, "the crumb is still there before its sixth step")

        repeat(3) { simulator.oneStep() }
        assertEquals(0, simulator.grainsCounts()[0] ?: 0, "the crumb disappears after six steps")
    }

    // ----------------------------------------------------------- field formula

    private fun waveModel(formula: String) = GrainModel(
        name = "Moving gradient", description = "field formula",
        grains = listOf(Grain(id = 0, name = "seed", movementProbability = 0.0)),
        fields = listOf(Field(id = 0, name = "wave", formula = formula))
    )

    private fun fieldValue(simulator: Simulator, x: Int, y: Int): Float {
        val simulation = simulator.simulation
        return simulator.field(0)[simulation.toIndex(x, y)]
    }

    @Test
    fun testFieldFormulaVariesInSpaceAndTime() {
        val size = 40
        val simulator = Simulator(
            waveModel("0.5 + 0.5 * sin(x / 10 + step / 5)"),
            createSimulation("wave", size = size)
        )

        simulator.oneStep()
        val left = fieldValue(simulator, 0, 0)
        val right = fieldValue(simulator, 20, 0)
        assertTrue(abs(left - right) > 0.2f, "the field must vary in space (left=$left, right=$right)")

        val before = (0 until size step 5).map { fieldValue(simulator, it, 10) }
        repeat(8) { simulator.oneStep() }
        val after = (0 until size step 5).map { fieldValue(simulator, it, 10) }
        val change = before.indices.maxOf { abs(before[it] - after[it]) }
        assertTrue(change > 0.2f, "the field must vary in time (max change = $change)")
    }

    @Test
    fun testWithoutFormulaTheFieldDecays() {
        val size = 20
        val simulator = Simulator(waveModel(""), createSimulation("wave", size = size))
        simulator.setInitialField(0, simulator.simulation.toIndex(10, 10), 1f)

        repeat(10) { simulator.oneStep() }

        assertTrue(fieldValue(simulator, 10, 10) < 0.5f, "without a formula the field diffuses and decays")
    }

    /** The formulas proposed in the tutorial conclusions must be valid (parse + type check). */
    @Test
    fun testTutorialFormulasAreValid() {
        listOf(
            "0.5 + 0.5 * sin(x / 10 + step / 5)",
            "x / 100",
            "current * 0.98",
            "min(1, count(0) / 8)",
            "0.5 + 0.5 * sin((x - y) / 8 - step / 4)"
        ).forEach { formula ->
            assertEquals(null, validateNumericFormula(formula), "field formula must be valid: $formula")
        }

        listOf("Add(1, 0, 1)", "Remove(1, 0, -1)", "Move(1, 0, 0, 0, 1)", "AddField(0, 0, 0, 0.2)")
            .forEach { actions ->
                assertEquals(null, validateActionFormula(actions), "actions must be valid: $actions")
            }
    }
}
