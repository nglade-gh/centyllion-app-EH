package com.centyllion.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The control meta-actions drive the run loop (pause, one step, N steps, rewind, restart)
 * through flags of the [Simulator], never through the grid command queue. An indefinite pause
 * can only be lifted from the interface; a timed pause (`Pause(t)`) resumes by itself.
 *
 * The loop is driven here exactly as the web client does it: one step, then
 * [SimulationLoop.afterStep]. The timed pauses use a [SimulatedClock]: nothing sleeps, the
 * tests advance time by hand.
 */
class SimulationLoopTest {

    /** Clock of the tests: time only moves when the test says so. */
    private class SimulatedClock(var time: Long = 0L) : Clock {
        override fun nowMillis(): Long = time
    }

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
        // Continue() does not exist: it is refused like any other unknown action
        assertTrue(validateActionFormula("Continue()")!!.contains("Unknown action"))
        assertTrue(validateModifierActions("Continue()")!!.contains("Unknown action"))
    }

    @Test
    fun testAPauseOfTwoSecondsFreezesTheLoopWithoutAStepThenResumesByItself() {
        val clock = SimulatedClock()
        val simulator = Simulator(model("step == 2", "Pause(2)"), simulation())
        val loop = SimulationLoop(simulator, clock = clock)
        loop.start()

        // the run goes on until the step numbered 2, which asks the pause
        var steps = 0
        while (loop.canStep && steps < 10) {
            simulator.oneStep()
            loop.afterStep()
            steps += 1
        }
        assertEquals(3, simulator.step, "the pause is applied at the end of the step that asked it")
        assertTrue(loop.isTimedPaused, "the loop is in a timed pause")
        assertEquals(2000L, loop.pausedUntilMillis, "the deadline is two seconds of the clock away")
        assertEquals(2.0, loop.pauseSeconds)
        assertTrue(loop.running, "a timed pause keeps the loop alive")
        assertFalse(loop.canStep, "no step is executed while the pause lasts")

        // the grid does not move during the pause: the state sequence does not depend on time
        val frozenAgents = simulator.agents.copyOf()
        val frozenStep = simulator.step

        // 1999 ms later, still nothing: no frame executes a step, and the pause holds
        clock.time = 1999L
        assertFalse(loop.resumeIfDue(), "the deadline is not reached")
        assertFalse(loop.canStep)
        assertEquals(frozenStep, simulator.step, "no step during the pause")
        assertTrue(frozenAgents.contentEquals(simulator.agents), "the grid is unchanged")

        // 2000 ms: the loop resumes by itself and the following steps run again
        clock.time = 2000L
        assertTrue(loop.resumeIfDue(), "the loop lifts its own pause")
        assertFalse(loop.isTimedPaused)
        assertEquals(0.0, loop.pauseSeconds)
        assertTrue(loop.running)
        assertTrue(loop.canStep, "the run can go on")
        simulator.oneStep()
        loop.afterStep()
        assertEquals(frozenStep + 1, simulator.step, "the steps follow the pause")
    }

    @Test
    fun testTheTimedPauseIsReportedWithItsDuration() {
        val clock = SimulatedClock()
        val simulator = Simulator(model("step == 0", "Pause(2.5)"), simulation())
        val loop = SimulationLoop(simulator, clock = clock)
        loop.start()
        simulator.oneStep()
        val update = loop.afterStep()
        assertEquals(2.5, update.pauseSeconds, "the view can say how long the pause lasts")
        assertTrue(update.running, "the loop stays alive")
        assertEquals(2500L, loop.pausedUntilMillis)
        assertEquals("2.5", formatPauseSeconds(update.pauseSeconds!!))
    }

    @Test
    fun testTheReaderCanInterruptATimedPause() {
        val clock = SimulatedClock()
        val simulator = Simulator(model("step == 0", "Pause(60)"), simulation())
        val loop = SimulationLoop(simulator, clock = clock)
        loop.start()
        simulator.oneStep()
        loop.afterStep()
        assertTrue(loop.isTimedPaused)
        // the Stop button (or the Run button) drops the pause: the deadline is forgotten
        loop.stop()
        assertFalse(loop.isTimedPaused)
        assertFalse(loop.canStep)
        clock.time = 120_000L
        assertFalse(loop.resumeIfDue(), "a stopped loop does not resume")
        assertFalse(loop.running)
        // the Run button starts it again immediately
        loop.start()
        assertTrue(loop.canStep)
    }

    @Test
    fun testTheIndefinitePauseNeverResumesByItself() {
        val clock = SimulatedClock()
        val simulator = Simulator(model("step == 1", "Pause()"), simulation())
        val loop = SimulationLoop(simulator, clock = clock)
        loop.start()

        var steps = 0
        while (loop.canStep && steps < 10) {
            simulator.oneStep()
            loop.afterStep()
            steps += 1
        }
        assertEquals(2, simulator.step)
        assertFalse(loop.isTimedPaused, "Pause() is not a timed pause")
        assertNull(loop.pausedUntilMillis)
        assertFalse(loop.running)

        // however far the clock goes, nothing resumes the run
        clock.time = 3_600_000L
        assertFalse(loop.resumeIfDue())
        assertFalse(loop.canStep)
        assertFalse(loop.running, "only the reader starts the run again")
    }

    @Test
    fun testADurationOfZeroAtRunTimeAsksForNoPause() {
        // the duration depends on the state, so the parser cannot refuse it: at run time a
        // duration <= 0 is ignored, like RunNStep(N <= 0), and the run is not interrupted
        val clock = SimulatedClock()
        val simulator = Simulator(model("step == 0", "Pause(step - 5)"), simulation())
        val loop = SimulationLoop(simulator, clock = clock)
        loop.start()
        simulator.oneStep()
        val update = loop.afterStep()
        assertFalse(loop.isTimedPaused, "a negative duration asks for no pause")
        assertTrue(update.running, "the run is not interrupted")
        assertNull(update.pauseSeconds)
        assertEquals(0, simulator.modifierPauseCount, "no warning for a duration that asks nothing")
    }
}
