package com.centyllion.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The control meta-actions drive the run loop (pause, one step, N steps, rewind, restart)
 * through flags of the [Simulator], never through the grid command queue. A pause can only be
 * lifted from the interface: `Continue()` does not exist any more.
 *
 * The loop is driven here exactly as the web client does it: one step, then
 * [SimulationLoop.afterStep].
 */
class SimulationLoopTest {

    private fun model(formula: String, actions: String) = GrainModel(
        name = "loop",
        grains = listOf(Grain(id = 0, name = "a", color = "Lime", movementProbability = 0.0)),
        modifiers = listOf(
            SimulationModifier(name = "control", id = 0, formula = formula, actions = actions)
        )
    )

    private fun simulation() = createSimulation("loop", size = 10) { -1 }

    @Test
    fun testRunOneStepRunsOneMoreStepThenPauses() {
        val simulator = Simulator(model("step == 0", "RunOneStep()"), simulation())
        val loop = SimulationLoop(simulator)
        loop.start()

        simulator.oneStep()
        val requested = loop.afterStep()
        assertEquals(1, requested.remainingSteps, "RunOneStep asks for exactly one step")
        assertTrue(loop.running, "the loop keeps running to execute the requested step")
        assertTrue(
            simulator.pendingGridCommands.isEmpty(),
            "a loop request is a flag, not a grid command"
        )

        simulator.oneStep()
        val done = loop.afterStep()
        assertEquals(2, simulator.step, "one more step than the step that asked for it")
        assertEquals(0, done.remainingSteps)
        assertFalse(done.running, "the run is paused after the requested step")
        assertFalse(loop.canStep, "no further step is executed")
    }

    @Test
    fun testRunNStepRunsThreeStepsThenStops() {
        val simulator = Simulator(model("step == 0", "RunNStep(3)"), simulation())
        val loop = SimulationLoop(simulator)
        loop.start()

        simulator.oneStep()
        loop.afterStep()
        assertEquals(3, loop.remainingSteps)

        var executed = 0
        while (loop.canStep && executed < 10) {
            simulator.oneStep()
            loop.afterStep()
            executed += 1
        }
        assertEquals(3, executed, "RunNStep(3) executes three steps")
        assertEquals(4, simulator.step, "one step plus the three requested ones")
        assertFalse(loop.running, "the run stops once the three steps are done")
        assertFalse(loop.canStep)
    }

    @Test
    fun testANonPositiveCountIsIgnored() {
        for (count in listOf(0, -2)) {
            val simulator = Simulator(model("step == 0", "RunNStep($count)"), simulation())
            val loop = SimulationLoop(simulator, running = true)
            simulator.oneStep()
            val update = loop.afterStep()
            assertEquals(0, update.remainingSteps, "RunNStep($count) asks for no step")
            assertTrue(update.running, "RunNStep($count) does not stop the run")
        }
    }

    @Test
    fun testPauseStopsTheRunAndIsReportedAsAskedByTheModel() {
        val simulator = Simulator(model("step == 1", "Pause()"), simulation())
        val loop = SimulationLoop(simulator)
        loop.start()

        // the run goes on until the pause asked at the end of the step numbered 1
        var steps = 0
        while (loop.canStep && steps < 10) {
            simulator.oneStep()
            loop.afterStep()
            steps += 1
        }
        assertEquals(2, simulator.step)
        assertFalse(loop.running, "Pause() stops the run")
        // the loop stopped because a modifier asked it: the view must be able to say so
        assertEquals(1, simulator.modifierPauseCount, "one pause asked by a modifier")

        // the view reads the flag once, and the counter goes back to zero
        assertEquals(1, simulator.consumeModifierPauseRequest(), "the view consumes the warning")
        assertEquals(0, simulator.modifierPauseCount, "the consumption clears the counter")
        assertEquals(0, simulator.consumeModifierPauseRequest(), "a warning is shown once")

        // nothing resumes the run by itself: a pause is lifted only from the interface
        simulator.oneStep()
        loop.afterStep()
        assertFalse(loop.running, "a manual step does not restart the run")
        assertEquals(0, simulator.modifierPauseCount, "no modifier asked a pause again")
    }

    @Test
    fun testARunStoppedByItselfIsNotReportedAsAModifierPause() {
        // RunNStep stops the loop without Pause(): no warning must be shown
        val simulator = Simulator(model("step == 0", "RunNStep(2)"), simulation())
        val loop = SimulationLoop(simulator, running = true)
        var steps = 0
        while (loop.canStep && steps < 10) {
            simulator.oneStep()
            loop.afterStep()
            steps += 1
        }
        assertFalse(loop.running)
        assertEquals(0, simulator.modifierPauseCount, "RunNStep is not a pause asked by the model")
    }

    @Test
    fun testRewindGoesBackToTheInitialStateWithoutRestarting() {
        val simulator = Simulator(model("step == 2", "Rewind()"), simulation())
        val loop = SimulationLoop(simulator)
        loop.start()

        var update = LoopUpdate(running = true, remainingSteps = 0)
        repeat(3) {
            simulator.oneStep()
            update = loop.afterStep()
        }
        assertEquals(0, simulator.step, "the step counter is back to zero")
        assertTrue(update.rewound)
        assertFalse(loop.running, "a rewind does not restart the run")
        assertFalse(loop.canStep)
    }

    @Test
    fun testRestartRewindsThenRuns() {
        val simulator = Simulator(model("step == 2", "Restart()"), simulation())
        val loop = SimulationLoop(simulator)
        loop.start()

        var update = LoopUpdate(running = true, remainingSteps = 0)
        repeat(3) {
            simulator.oneStep()
            update = loop.afterStep()
        }
        assertEquals(0, simulator.step, "the step counter is back to zero")
        assertTrue(update.rewound)
        assertTrue(loop.running, "a restart resumes the run")
    }

    @Test
    fun testTheControlActionsAreMetaActions() {
        // no control action may be parsed as a cell action of a behaviour
        for (actions in listOf("Pause()", "RunOneStep()", "RunNStep(2)", "Rewind()", "Restart()")) {
            assertTrue(
                validateActionFormula(actions)!!.contains("meta-action"),
                "$actions must be refused in a behaviour"
            )
            assertNull(validateModifierActions(actions), "$actions is accepted in a modifier")
        }
        // Continue() was removed and is refused everywhere, with its own message
        assertTrue(validateActionFormula("Continue()")!!.contains("n'existe plus"))
        assertTrue(validateModifierActions("Continue()")!!.contains("n'existe plus"))
    }
}
