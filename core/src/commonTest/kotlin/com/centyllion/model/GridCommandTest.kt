package com.centyllion.model

import com.centyllion.expression.v
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Grid meta-actions: parsing of the actions formula of a simulation modifier and queueing in
 * the simulator. The view (selection, clipboard) is out of reach of the core: the commands are
 * only queued there, in order, and applied by the view at the end of the step.
 */
class GridCommandTest {

    // ---- parsing ----

    @Test
    fun testEveryGridCommandIsAcceptedInAModifier() {
        assertNull(validateModifierActions("Show(0)"))
        assertNull(validateModifierActions("Hide(0)"))
        assertNull(validateModifierActions("Select(0, 0, 4, 3)"))
        assertNull(validateModifierActions("Cut()"))
        assertNull(validateModifierActions("Copy()"))
        assertNull(validateModifierActions("EraseSelection()"))
        assertNull(validateModifierActions("Paste(1, 2, true)"))
        assertNull(validateModifierActions("FlushSelection()"))
        assertNull(validateModifierActions("Unselect()"))
        assertNull(validateModifierActions("PasteAndFlush(1, 2, false)"))
        assertNull(validateModifierActions("Rotate(1)"))
        assertNull(validateModifierActions("FlipX()"))
        assertNull(validateModifierActions("FlipY()"))
    }

    @Test
    fun testGridCommandsMixWithTheOtherMetaActions() {
        assertNull(validateModifierActions("Select(0, 0, 4, 3), Copy(), Add(0, 2), Rotate(1), Paste(6, 0, false)"))
    }

    @Test
    fun testTheOrderOfTheFormulaIsKept() {
        val actions = "Add(0, 2), Select(0, 0, 2, 2), Copy()".parseModifierActions()
        assertEquals(3, actions.size)
        assertTrue(actions[0] is Action.AddMany)
        assertEquals("Select", (actions[1] as Action.Grid).call.name)
        assertEquals("Copy", (actions[2] as Action.Grid).call.name)
    }

    @Test
    fun testArityErrors() {
        assertTrue(validateModifierActions("Select(0, 0, 2)")!!.contains("Select expects 4 arguments, got 3"))
        assertTrue(validateModifierActions("Show()")!!.contains("Show expects 1 argument, got 0"))
        assertTrue(validateModifierActions("Paste(1, 2)")!!.contains("Paste expects 3 arguments, got 2"))
        assertTrue(
            validateModifierActions("PasteAndFlush(1, 2, true, 3)")!!
                .contains("PasteAndFlush expects 3 arguments, got 4")
        )
        assertTrue(validateModifierActions("Rotate()")!!.contains("Rotate expects 1 argument, got 0"))
        assertTrue(validateModifierActions("Copy(1)")!!.contains("Copy expects 0 arguments, got 1"))
        assertTrue(validateModifierActions("FlushSelection(0)")!!.contains("expects 0 arguments"))
        assertTrue(validateModifierActions("Unselect(1)")!!.contains("expects 0 arguments"))
        assertTrue(validateModifierActions("FlipX(0)")!!.contains("expects 0 arguments"))
        assertTrue(validateModifierActions("FlipY(0)")!!.contains("expects 0 arguments"))
        assertTrue(validateModifierActions("Cut(0, 1)")!!.contains("expects 0 arguments"))
        assertTrue(validateModifierActions("EraseSelection(0)")!!.contains("EraseSelection expects 0 arguments, got 1"))
    }

    @Test
    fun testTypeErrors() {
        // coordinates must be numbers
        assertTrue(validateModifierActions("Select(true, 0, 2, 2)")!!.contains("must be a number"))
        assertTrue(validateModifierActions("Hide(false)")!!.contains("must be a number"))
        // the paste mode must be a boolean literal
        assertTrue(validateModifierActions("Paste(1, 2, 1)")!!.contains("must be a boolean"))
        assertTrue(validateModifierActions("Paste(1, 2, step)")!!.contains("must be a boolean"))
        assertTrue(validateModifierActions("PasteAndFlush(1, 2, x)")!!.contains("must be a boolean"))
    }

    @Test
    fun testUnknownNamesAreReported() {
        val message = validateModifierActions("Create(0, 1, 0)")!!
        assertTrue(message.contains("Unknown action 'Create'"))
        // the grid commands are listed with the other actions
        assertTrue(message.contains("PasteAndFlush"))
    }

    @Test
    fun testGridCommandsAreRefusedInABehaviour() {
        assertTrue(validateActionFormula("Copy()")!!.contains("meta-action"))
        assertTrue(validateActionFormula("Select(0, 0, 2, 2)")!!.contains("meta-action"))
        assertTrue(validateActionFormula("Paste(1, 2, true)")!!.contains("meta-action"))
        assertTrue(validateActionFormula("EraseSelection()")!!.contains("meta-action"))
        // a cell action is still accepted
        assertNull(validateActionFormula("Add(0, 1, 0)"))
    }

    // ---- resolution of the arguments ----

    private fun evaluator() = NetbioDynEvaluator(null).also { it.step = 3.v }

    @Test
    fun testArgumentsAreExpressions() {
        val call = "Select(1 + 1, 0, step, 2)".parseModifierActions().single() as Action.Grid
        assertEquals(GridCommand.Select(2, 0, 3, 2), call.call.resolve(evaluator()))
    }

    @Test
    fun testRotateIsNormalisedModuloFour() {
        fun turns(formula: String): Int {
            val call = formula.parseModifierActions().single() as Action.Grid
            return (call.call.resolve(evaluator()) as GridCommand.Rotate).quarterTurns
        }
        assertEquals(0, turns("Rotate(0)"))
        assertEquals(1, turns("Rotate(1)"))
        assertEquals(3, turns("Rotate(3)"))
        // a negative count turns counter-clockwise: -1 is three clockwise turns
        assertEquals(3, turns("Rotate(-1)"))
        assertEquals(1, turns("Rotate(-3)"))
        assertEquals(0, turns("Rotate(-4)"))
        assertEquals(1, turns("Rotate(5)"))
        assertEquals(2, turns("Rotate(2 + 4)"))
    }

    @Test
    fun testOverwriteIsABoolean() {
        fun overwrite(formula: String): Boolean {
            val call = formula.parseModifierActions().single() as Action.Grid
            return when (val command = call.call.resolve(evaluator())) {
                is GridCommand.Paste -> command.overwrite
                is GridCommand.PasteAndFlush -> command.overwrite
                else -> error("not a paste: $command")
            }
        }
        assertTrue(overwrite("Paste(1, 2, true)"))
        assertFalse(overwrite("Paste(1, 2, false)"))
        assertTrue(overwrite("PasteAndFlush(1, 2, true)"))
        assertFalse(overwrite("PasteAndFlush(1, 2, false)"))
    }

    @Test
    fun testResolvedValues() {
        fun resolve(formula: String) = (formula.parseModifierActions().single() as Action.Grid).call.resolve(evaluator())
        assertEquals(GridCommand.Select(1, 2, 3, 4), resolve("Select(1, 2, 3, 4)"))
        assertEquals(GridCommand.Copy, resolve("Copy()"))
        assertEquals(GridCommand.Cut, resolve("Cut()"))
        assertEquals(GridCommand.EraseSelection, resolve("EraseSelection()"))
        assertEquals(GridCommand.FlushSelection, resolve("FlushSelection()"))
        assertEquals(GridCommand.Unselect, resolve("Unselect()"))
        assertEquals(GridCommand.Paste(5, 6, true), resolve("Paste(5, 6, true)"))
        assertEquals(GridCommand.PasteAndFlush(5, 6, false), resolve("PasteAndFlush(5, 6, false)"))
        assertEquals(GridCommand.FlipX, resolve("FlipX()"))
        assertEquals(GridCommand.FlipY, resolve("FlipY()"))
        assertEquals(GridCommand.Show(2), resolve("Show(2)"))
        assertEquals(GridCommand.Hide(2), resolve("Hide(2)"))
    }

    // ---- end of an operation: the selection rectangle goes away ----

    /**
     * The view drops the selection rectangle for every command whose [GridCommand.dropsSelection]
     * is true, right after the work of that command (`applyGridCommands`). Pinning the flag here
     * keeps the two effects of a command together: `FlushSelection()` empties the memory *and*
     * deselects, `PasteAndFlush()` pastes, then empties the memory *and* deselects, exactly like
     * `Unselect()`, which does nothing else.
     */
    @Test
    fun testTheCommandsThatEndAnOperationDropTheSelection() {
        assertTrue(GridCommand.Unselect.dropsSelection, "Unselect() only drops the rectangle")
        assertTrue(GridCommand.EraseSelection.dropsSelection, "the erased zone is deselected")
        assertTrue(GridCommand.FlushSelection.dropsSelection, "flushing ends the operation")
        assertTrue(GridCommand.PasteAndFlush(1, 2, false).dropsSelection, "paste then flush ends it too")
        // the flag survives the resolution of the formula, which is what the view receives
        fun resolved(formula: String) =
            ((formula.parseModifierActions().single() as Action.Grid).call.resolve(evaluator()))
        assertTrue(resolved("Unselect()").dropsSelection)
        assertTrue(resolved("EraseSelection()").dropsSelection)
        assertTrue(resolved("FlushSelection()").dropsSelection)
        assertTrue(resolved("PasteAndFlush(1, 2, true)").dropsSelection)
    }

    @Test
    fun testTheOtherCommandsKeepTheSelection() {
        // a command that only reads, fills or transforms the clipboard leaves the rectangle alone
        val keeping = listOf(
            GridCommand.Select(0, 0, 2, 2), GridCommand.Copy, GridCommand.Cut,
            GridCommand.Paste(1, 2, true), GridCommand.Rotate(1), GridCommand.FlipX,
            GridCommand.FlipY, GridCommand.Show(0), GridCommand.Hide(0),
            GridCommand.AllowToricSelection(true), GridCommand.AllowOverwriting(false)
        )
        keeping.forEach { command ->
            assertFalse(command.dropsSelection, "$command must keep the selection")
        }
    }

    // ---- queueing in the simulator ----

    private fun model(actions: String) = GrainModel(
        name = "grid",
        grains = listOf(Grain(id = 0, name = "a", color = "Lime", movementProbability = 0.0)),
        modifiers = listOf(SimulationModifier(name = "grid", id = 0, actions = actions)),
    )

    private fun simulation() = createSimulation("grid", size = 10) { -1 }

    @Test
    fun testTheCommandsAreQueuedInOrder() {
        val simulator = Simulator(
            model("Select(0, 0, 2, 2), Copy(), Rotate(1), Paste(4, 4, true), FlushSelection(), Hide(0)"),
            simulation()
        )
        assertTrue(simulator.pendingGridCommands.isEmpty(), "nothing is queued before the first step")
        simulator.oneStep()
        val queued = simulator.pendingGridCommands
        assertEquals(6, queued.size, "one command per call, in the formula order")
        assertEquals(GridCommand.Select(0, 0, 2, 2), queued[0])
        assertEquals(GridCommand.Copy, queued[1])
        assertEquals(GridCommand.Rotate(1), queued[2])
        assertEquals(GridCommand.Paste(4, 4, true), queued[3])
        assertEquals(GridCommand.FlushSelection, queued[4])
        assertEquals(GridCommand.Hide(0), queued[5])
        // the view must redraw (it is the only one that can apply the commands)
        assertTrue(simulator.modifiersChanged)
    }

    @Test
    fun testEraseSelectionIsQueuedAfterItsSelection() {
        val simulator = Simulator(model("Select(0, 0, 2, 2), EraseSelection()"), simulation())
        simulator.oneStep()
        assertEquals(
            listOf<GridCommand>(GridCommand.Select(0, 0, 2, 2), GridCommand.EraseSelection),
            simulator.pendingGridCommands
        )
    }

    @Test
    fun testTheQueueIsDrained() {
        val simulator = Simulator(model("Select(0, 0, 2, 2)"), simulation())
        simulator.oneStep()
        assertEquals(1, simulator.drainGridCommands().size)
        assertTrue(simulator.pendingGridCommands.isEmpty(), "the view takes the commands once")
        assertEquals(0, simulator.drainGridCommands().size)
    }

    @Test
    fun testAStepReplacesTheCommandsOfThePreviousOne() {
        val simulator = Simulator(model("Rotate(1)"), simulation())
        simulator.oneStep()
        simulator.oneStep()
        // the view did not drain: a command only belongs to its step, so nothing accumulates
        assertEquals(1, simulator.pendingGridCommands.size)
        assertEquals(GridCommand.Rotate(1), simulator.pendingGridCommands.single())
    }

    @Test
    fun testRewindDropsThePendingCommands() {
        val simulator = Simulator(model("Copy()"), simulation())
        simulator.oneStep()
        simulator.reset()
        assertTrue(simulator.pendingGridCommands.isEmpty(), "a rewound step leaves no command behind")
    }

    @Test
    fun testACommandWithoutSelectionIsHarmless() {
        // no selection, no clipboard: the commands are queued anyway and simply ignored by the view
        val simulator = Simulator(model("Copy(), Cut(), Paste(0, 0, true), Rotate(-1), FlipX()"), simulation())
        simulator.oneStep()
        assertEquals(5, simulator.pendingGridCommands.size)
        // and the grid itself is untouched
        assertEquals(0, simulator.agents.count { it >= 0 })
    }

    // ---- toric paste ----

    @Test
    fun testWrapCoordinateStaysInsideTheGrid() {
        assertEquals(0, wrapCoordinate(0, 100))
        assertEquals(99, wrapCoordinate(99, 100))
        assertEquals(0, wrapCoordinate(100, 100))
        assertEquals(3, wrapCoordinate(103, 100))
        // a negative coordinate comes back from the opposite edge
        assertEquals(99, wrapCoordinate(-1, 100))
        assertEquals(90, wrapCoordinate(-10, 100))
        assertEquals(0, wrapCoordinate(-100, 100))
        // degenerate grid: never divide by zero
        assertEquals(0, wrapCoordinate(7, 0))
    }

    @Test
    fun testWrapCoordinateMatchesTheEdgeWrappingOfThePaste() {
        // Paste(-10, 10, true) on a 100x100 grid with a 50 wide extraction: the ten left
        // columns reappear at 90..99, the 40 others stay at 0..39
        val columns = (0 until 50).map { wrapCoordinate(-10 + it, 100) }
        assertEquals((90 until 100).toList() + (0 until 40).toList(), columns)
        // overflow on the right: 95 + 10 wraps to 5
        assertEquals(listOf(95, 96, 97, 98, 99, 0, 1, 2, 3, 4), (0 until 10).map { wrapCoordinate(95 + it, 100) })
        // and the same vertically: 80 rows, a paste starting at y = 90 wraps to 10
        assertEquals(20, wrapCoordinate(100, 80))
        assertEquals(0, wrapCoordinate(80, 80))
    }
}
