package com.centyllion.model

import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * What [SimulationLoop.afterStep] changed in the loop, so that the caller can refresh its view.
 */
data class LoopUpdate(
    /** True when the loop must keep running. */
    val running: Boolean,
    /** Steps still to execute before the loop pauses by itself (0 when none). */
    val remainingSteps: Int,
    /** True when the simulation was rewound (initial state restored). */
    val rewound: Boolean = false,
    /**
     * Duration in seconds of the pause that this step asked for: `null` when the step asked no
     * pause, `0.0` for the indefinite `Pause()`, strictly positive for `Pause(t)`.
     *
     * The view reads it to say how long the pause lasts, and whether it will resume by itself.
     */
    val pauseSeconds: Double? = null
)

/**
 * Run/pause state of the simulation loop, driven by the control meta-actions
 * (`Pause`, `Pause(t)`, `RunOneStep`, `RunNStep`, `Rewind`, `Restart`).
 *
 * The meta-actions only set flags on the [Simulator]; this class turns them into the next state
 * of the loop at the end of the step that emitted them. The web client drives it from its
 * animation callback and `:core:jvmTest` drives it directly, so the semantics — one step then
 * pause, N steps then pause, rewind without restart, timed pause — are the same on both sides
 * and are tested in the core rather than in the DOM code.
 *
 * Two kinds of pause, deliberately different:
 *
 * - `Pause()` stops the loop (`running == false`) and only the reader can start it again
 *   ([start]): a pause is never lifted by the model itself, which is why the arbitration below
 *   has no resume branch.
 * - `Pause(t)` keeps the loop **alive** but frozen: `running` stays true, [canStep] is false and
 *   [pausedUntilMillis] holds the deadline. No step runs, so the grid and the step counter do not
 *   move; [resumeIfDue], called on every animation frame, lifts the pause when the deadline is
 *   reached. The resume therefore belongs to the loop, not to the view: it is testable without a
 *   browser and without waiting.
 *
 * The clock is injected ([clock]): the core never reads the system clock itself, so a test of
 * `Pause(2)` runs instantly by advancing a simulated clock.
 *
 * Requests of a single step are applied in this order, the last one winning:
 * `Restart`, `Rewind`, `Pause`/`Pause(t)`, `RunOneStep`/`RunNStep`.
 */
class SimulationLoop(
    val simulator: Simulator,
    running: Boolean = false,
    /** Clock of the timed pauses: the wall clock of the machine, or a simulated one in tests. */
    val clock: Clock = systemClock()
) {

    /** True while the loop runs (the run button is on). A timed pause keeps it true. */
    var running: Boolean = running
        private set

    /** Steps asked by `RunOneStep`/`RunNStep` and not executed yet (0 when none). */
    var remainingSteps: Int = 0
        private set

    /** True while the steps of a request are being executed: the loop pauses when they are done. */
    private var bursting: Boolean = false

    /**
     * Deadline of the timed pause in progress, in [clock] milliseconds, or null when the loop is
     * not in a timed pause. Set by `Pause(t)`, cleared by [resumeIfDue], [start] and [stop].
     */
    var pausedUntilMillis: Long? = null
        private set

    /** Duration in seconds of the timed pause in progress (0 when none): what the view shows. */
    var pauseSeconds: Double = 0.0
        private set

    /** True while a timed pause holds the loop: no step runs, but the loop is still alive. */
    val isTimedPaused: Boolean get() = pausedUntilMillis != null

    /**
     * True when the view must keep asking for animation frames: a run in progress, or a timed
     * pause waiting for its deadline (the frames are what lets the loop resume).
     */
    val isAlive: Boolean get() = running || isTimedPaused

    /** True when a step may be executed now. */
    val canStep: Boolean get() = !isTimedPaused && (running || remainingSteps > 0)

    /** Starts the loop (run button, `Restart`). The reader takes over: any timed pause is lifted. */
    fun start() {
        running = true
        cancelBurst()
        clearTimedPause()
    }

    /** Stops the loop and drops the steps still requested (stop button, `Pause`). */
    fun stop() {
        running = false
        cancelBurst()
        clearTimedPause()
    }

    private fun cancelBurst() {
        bursting = false
        remainingSteps = 0
    }

    private fun clearTimedPause() {
        pausedUntilMillis = null
        pauseSeconds = 0.0
    }

    /**
     * Lifts the timed pause when its deadline has been reached, and says whether this call
     * resumed the run. Does nothing (and returns false) when no timed pause is in progress.
     *
     * Called by the run loop on **every** frame, including the frames where no step is executed,
     * since no step runs while the pause lasts: this is the only place where a `Pause(t)` ends.
     * The test is `>=`, so a pause of t seconds lasts at least t milliseconds of [clock].
     */
    fun resumeIfDue(): Boolean {
        val deadline = pausedUntilMillis ?: return false
        if (clock.nowMillis() < deadline) return false
        clearTimedPause()
        // the run goes on by itself, as if the reader had pressed Run
        running = true
        cancelBurst()
        return true
    }

    /**
     * Applies the control requests of the step that has just run. Must be called once per
     * executed step, by the run loop.
     *
     * A `RunOneStep`/`RunNStep` request runs its steps and then pauses by itself: the loop
     * reports `running == false` once the last requested step has been executed. The steps of
     * two requests add up, and a `Pause` cancels what remains to run. A `Pause(t)` does not stop
     * the loop: it freezes it until [resumeIfDue] sees its deadline.
     */
    fun afterStep(): LoopUpdate {
        // the step that has just run counts towards a burst requested at an earlier step
        if (bursting) {
            remainingSteps -= 1
            if (remainingSteps <= 0) {
                cancelBurst()
                running = false
            }
        }

        val requests = simulator.consumeControlRequests()
        var rewound = false
        var pausedFor: Double? = null
        when {
            requests.restart -> {
                simulator.reset()
                rewound = true
                running = true
                cancelBurst()
                clearTimedPause()
            }
            requests.rewind -> {
                simulator.reset()
                rewound = true
                running = false
                cancelBurst()
                clearTimedPause()
            }
            else -> {
                if (requests.pause) {
                    running = false
                    cancelBurst()
                    if (requests.pauseSeconds > 0.0) {
                        // Pause(t): the loop stays alive, frozen until the deadline. A pause is
                        // rounded up to the millisecond so that it never lasts less than asked.
                        running = true
                        pauseSeconds = requests.pauseSeconds
                        val millis = ceil(requests.pauseSeconds * 1000.0).roundToLong().coerceAtLeast(1L)
                        pausedUntilMillis = clock.nowMillis() + millis
                        pausedFor = requests.pauseSeconds
                    } else {
                        clearTimedPause()
                        pausedFor = 0.0
                    }
                }
                if (requests.steps > 0) {
                    // RunOneStep is RunNStep(1): run them, then pause. The steps win over a
                    // pause asked by the same step, exactly like the indefinite Pause().
                    running = true
                    clearTimedPause()
                    pausedFor = null
                    remainingSteps += requests.steps
                    bursting = true
                }
            }
        }
        return LoopUpdate(running, remainingSteps, rewound, pausedFor)
    }
}

/**
 * How a pause duration is written to the reader: `2` for two seconds, `2.5` for two and a half,
 * `0.25` for a quarter of a second (millisecond precision, no trailing zeros).
 */
fun formatPauseSeconds(seconds: Double): String {
    val millis = (seconds * 1000.0).roundToLong()
    val value = millis / 1000.0
    val text = value.toString()
    return if (text.endsWith(".0")) text.dropLast(2) else text
}
