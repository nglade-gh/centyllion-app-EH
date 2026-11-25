package com.centyllion.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The optional `fill` flag of the shape meta-actions, and the obsolete `AddDisc`/`EraseDisc`
 * spellings: what the formula means, what it does on the grid, and what it refuses.
 */
class ShapeActionsTest {

    // ---- parsing ----

    @Test
    fun testFillDefaultsToTrue() {
        val rect = "AddRect(0, 10, 2, 2, 6, 6)".parseModifierActions().single()
        assertTrue(rect is Action.AddRect)
        assertTrue(rect.fill, "a shape written without fill stays solid")

        val circle = "AddCircle(0, 5, 10, 10, 3)".parseModifierActions().single()
        assertTrue(circle is Action.AddCircle)
        assertTrue(circle.fill)

        val eraseRect = "EraseRect(2, 2, 6, 6)".parseModifierActions().single()
        assertTrue(eraseRect is Action.EraseRectAll)
        assertTrue(eraseRect.fill)

        val eraseCircle = "EraseCircle(10, 10, 3)".parseModifierActions().single()
        assertTrue(eraseCircle is Action.EraseCircleAll)
        assertTrue(eraseCircle.fill)

        val typed = "EraseCircle(0, 10, 10, 3)".parseModifierActions().single()
        assertTrue(typed is Action.EraseCircle)
        assertTrue(typed.fill)

        val typedRect = "EraseRect(0, 2, 2, 6, 6)".parseModifierActions().single()
        assertTrue(typedRect is Action.EraseRect)
        assertTrue(typedRect.fill)
    }

    @Test
    fun testFillFlagIsReadOnEveryShape() {
        val rect = "AddRect(0, 10, 2, 2, 6, 6, false)".parseModifierActions().single()
        assertTrue(rect is Action.AddRect && !rect.fill)

        val circle = "AddCircle(0, 5, 10, 10, 3, false)".parseModifierActions().single()
        assertTrue(circle is Action.AddCircle && !circle.fill)

        val eraseRect = "EraseRect(2, 2, 6, 6, false)".parseModifierActions().single()
        assertTrue(eraseRect is Action.EraseRectAll && !eraseRect.fill)

        val eraseCircle = "EraseCircle(10, 10, 3, false)".parseModifierActions().single()
        assertTrue(eraseCircle is Action.EraseCircleAll && !eraseCircle.fill)

        val typedRect = "EraseRect(0, 2, 2, 6, 6, false)".parseModifierActions().single()
        assertTrue(typedRect is Action.EraseRect && !typedRect.fill)

        val typedCircle = "EraseCircle(0, 10, 10, 3, false)".parseModifierActions().single()
        assertTrue(typedCircle is Action.EraseCircle && !typedCircle.fill)

        // an explicit true is the same as no flag at all
        assertEquals(
            "AddCircle(0, 5, 10, 10, 3)".parseModifierActions(),
            "AddCircle(0, 5, 10, 10, 3, true)".parseModifierActions()
        )
    }

    @Test
    fun testTheTypedEraseRectStaysUnambiguous() {
        // five numbers: the typed form (id, x1, y1, x2, y2), not a rectangle with a flag
        val typed = "EraseRect(1, 0, 0, 9, 9)".parseModifierActions().single()
        assertTrue(typed is Action.EraseRect)
        assertTrue(typed.fill)
        // five arguments ending with a boolean: every agent of the rectangle
        val all = "EraseRect(0, 0, 9, 9, false)".parseModifierActions().single()
        assertTrue(all is Action.EraseRectAll)
        assertFalse(all.fill)
    }

    @Test
    fun testANonBooleanFillIsRefused() {
        val rect = validateModifierActions("AddRect(0, 10, 2, 2, 6, 6, 1)")
        assertNotNull(rect)
        assertTrue(rect.contains("fill"), "the message must name the flag: $rect")
        assertTrue(rect.contains("true or false"), "the message must say what to write: $rect")

        val circle = validateModifierActions("AddCircle(0, 5, 10, 10, 3, 2)")
        assertNotNull(circle)
        assertTrue(circle.contains("fill") && circle.contains("true or false"), circle)

        val erased = validateModifierActions("EraseCircle(0, 10, 10, 3, 1)")
        assertNotNull(erased)
        assertTrue(erased.contains("fill") && erased.contains("true or false"), erased)

        val erasedRect = validateModifierActions("EraseRect(0, 2, 2, 6, 6, 1)")
        assertNotNull(erasedRect)
        assertTrue(erasedRect.contains("fill") && erasedRect.contains("true or false"), erasedRect)

        // a boolean is only read as the flag: a number where a rectangle is expected is refused
        val number = validateModifierActions("AddRect(0, 10, 2, 2, 6)")
        assertNotNull(number)
        assertTrue(number.contains("expects 6 arguments"), number)
    }

    @Test
    fun testTheObsoleteDiscNamesAreReadAsCircles() {
        assertEquals(
            "AddCircle(0, 5, 10, 10, 3)".parseModifierActions(),
            "AddDisc(0, 5, 10, 10, 3)".parseModifierActions()
        )
        assertEquals(
            "EraseCircle(10, 10, 3)".parseModifierActions(),
            "EraseDisc(10, 10, 3)".parseModifierActions()
        )
        assertEquals(
            "EraseCircle(0, 10, 10, 3)".parseModifierActions(),
            "EraseDisc(0, 10, 10, 3)".parseModifierActions()
        )
        // the flag is read on the old spelling too, like on pause()
        assertEquals(
            "AddCircle(0, 5, 10, 10, 3, false)".parseModifierActions(),
            "AddDisc(0, 5, 10, 10, 3, false)".parseModifierActions()
        )
        assertEquals("AddCircle", deprecatedActionNames["AddDisc"])
        assertEquals("EraseCircle", deprecatedActionNames["EraseDisc"])
        assertEquals("Pause", deprecatedActionNames["pause"])
    }

    @Test
    fun testTheNewNamesAreMetaActions() {
        val circle = "AddCircle(0, 5, 10, 10, 3)".parseModifierActions().single()
        assertTrue(isMetaAction(circle))
        val erased = "EraseCircle(10, 10, 3)".parseModifierActions().single()
        assertTrue(isMetaAction(erased))
        // and they are refused in a behaviour, like every other meta-action
        assertTrue(validateActionFormula("AddCircle(0, 5, 10, 10, 3)")!!.contains("meta-action"))
        assertTrue(validateActionFormula("EraseCircle(10, 10, 3)")!!.contains("meta-action"))
    }

    // ---- runtime ----

    private fun model(actions: String) = GrainModel(
        name = "shapes",
        grains = listOf(Grain(id = 0, name = "a", color = "Lime", movementProbability = 0.0)),
        modifiers = listOf(
            SimulationModifier(name = "shapes", id = 0, formula = "", actions = actions, probability = 1.0)
        ),
    )

    /** A 10x10 grid entirely covered with agents of type 0. */
    private fun fullGrid() = createSimulation("shapes", size = 10) { 0 }

    private fun emptyGrid() = createSimulation("shapes", size = 10) { -1 }

    @Test
    fun testTheFilledRectangleErasesEveryAgentOfTheZone() {
        val simulator = Simulator(model("EraseRect(2, 2, 6, 6)"), fullGrid())
        simulator.oneStep()
        assertEquals(100 - 25, simulator.agents.count { it >= 0 }, "a solid rectangle written without fill")
    }

    @Test
    fun testTheRectangleOutlineErasesOnlyTheBorder() {
        val simulator = Simulator(model("EraseRect(2, 2, 6, 6, false)"), fullGrid())
        simulator.oneStep()
        assertEquals(100 - 16, simulator.agents.count { it >= 0 })
        // the inside of the zone is untouched
        assertEquals(0, simulator.agents[4 * 10 + 4], "the centre of the zone keeps its agent")
        assertEquals(0, simulator.agents[3 * 10 + 5])
        // the border is gone
        assertEquals(-1, simulator.agents[2 * 10 + 2])
        assertEquals(-1, simulator.agents[6 * 10 + 6])
        assertEquals(-1, simulator.agents[2 * 10 + 4], "the top row is whole")
        assertEquals(-1, simulator.agents[4 * 10 + 2], "the left side")
    }

    @Test
    fun testTheTypedRectangleOutlineErasesOnlyThatGrain() {
        val simulator = Simulator(model("EraseRect(0, 2, 2, 6, 6, false)"), fullGrid())
        simulator.oneStep()
        assertEquals(100 - 16, simulator.agents.count { it >= 0 })
        assertEquals(0, simulator.agents[4 * 10 + 4])
    }

    @Test
    fun testTheCircleOutlineErasesOnlyTheRing() {
        val simulator = Simulator(model("EraseCircle(5, 5, 2, false)"), fullGrid())
        simulator.oneStep()
        // disc of radius 2: 13 cells; interior (radius 1): 5 cells; the ring is 8 cells
        assertEquals(100 - 8, simulator.agents.count { it >= 0 })
        assertEquals(0, simulator.agents[5 * 10 + 5], "the centre keeps its agent")
        assertEquals(-1, simulator.agents[3 * 10 + 5], "the top of the ring")
        assertEquals(-1, simulator.agents[5 * 10 + 3], "the left of the ring")
        assertEquals(0, simulator.agents[4 * 10 + 5], "the row just above the centre is interior")
    }

    @Test
    fun testTheFilledCircleErasesTheWholeDisc() {
        val simulator = Simulator(model("EraseCircle(5, 5, 2)"), fullGrid())
        simulator.oneStep()
        assertEquals(100 - 13, simulator.agents.count { it >= 0 })
        assertEquals(-1, simulator.agents[5 * 10 + 5])
    }

    @Test
    fun testTheCircleIsTheObsoleteDisc() {
        val disc = Simulator(model("EraseDisc(5, 5, 2)"), fullGrid())
        val circle = Simulator(model("EraseCircle(5, 5, 2)"), fullGrid())
        disc.oneStep()
        circle.oneStep()
        assertEquals(circle.agents.toList(), disc.agents.toList(), "AddDisc/EraseDisc mean the same as Circle")
    }

    @Test
    fun testAddingOnTheOutlineOnlyFillsTheRing() {
        val simulator = Simulator(model("AddCircle(0, 40, 5, 5, 2, false)"), emptyGrid())
        simulator.oneStep()
        assertEquals(8, simulator.agents.count { it >= 0 }, "the eight cells of the ring, not the thirteen of the disc")
        assertEquals(-1, simulator.agents[5 * 10 + 5], "the centre is not on the ring")
    }

    @Test
    fun testAddingOnTheRectangleOutlineOnlyFillsTheBorder() {
        val simulator = Simulator(model("AddRect(0, 40, 2, 2, 6, 6, false)"), emptyGrid())
        simulator.oneStep()
        assertEquals(16, simulator.agents.count { it >= 0 })
        assertEquals(-1, simulator.agents[4 * 10 + 4])
    }

    @Test
    fun testADefaultFillKeepsEveryEarlierModelRunning() {
        // the formula of a model saved before the flag existed: no flag, solid shape
        val rect = Simulator(model("AddRect(0, 9, 2, 2, 4, 4)"), emptyGrid())
        rect.oneStep()
        assertEquals(9, rect.agents.count { it >= 0 })

        val circle = Simulator(model("AddDisc(0, 9, 5, 5, 2)"), emptyGrid())
        circle.oneStep()
        assertEquals(9, circle.agents.count { it >= 0 }, "the old disc spelling adds on the whole disc")
    }
}
