package com.centyllion.model

/**
 * What [SimulationLoop.afterStep] changed in the loop, so that the caller can refresh its view.
 */
data class LoopUpdate(
    /** True when the loop must keep running. */
    val running: Boolean,
    /** Steps still to execute before the loop pauses by itself (0 when none). */
    val remainingSteps: Int,
    /** True when the simulation was rewound (initial state restored). */
    val rewound: Boolean = false
)

/**
 * Run/pause state of the simulation loop, driven by the control meta-actions
 * (`Pause`, `RunOneStep`, `RunNStep`, `Rewind`, `Restart`).
 *
 * The meta-actions only set flags on the [Simulator]; this class turns them into the next state
 * of the loop at the end of the step that emitted them. The web client drives it from its
 * animation callback and `:core:jvmTest` drives it directly, so the semantics — one step then
 * pause, N steps then pause, rewind without restart — are the same on both sides and are
 * tested in the core rather than in the DOM code.
 *
 * A pause can never be lifted by the model: `Continue()` does not exist, only the reader
 * starts the run again (`start()`), which is why the arbitration below has no resume branch.
 *
 * Requests of a single step are applied in this order, the last one winning:
 * `Restart`, `Rewind`, `Pause`, `RunOneStep`/`RunNStep`.
 */
class SimulationLoop(val simulator: Simulator, running: Boolean = false) {

    /** True while the loop runs (the run button is on). */
    var running: Boolean = running
        private set

    /** Steps asked by `RunOneStep`/`RunNStep` and not executed yet (0 when none). */
    var remainingSteps: Int = 0
        private set

    /** True while the steps of a request are being executed: the loop pauses when they are done. */
    private var bursting: Boolean = false

    /** True when a step may be executed now. */
    val canStep: Boolean get() = running || remainingSteps > 0

    /** Starts the loop (run button, `Restart`). */
    fun start() {
        running = true
        cancelBurst()
    }

    /** Stops the loop and drops the steps still requested (stop button, `Pause`). */
    fun stop() {
        running = false
        cancelBurst()
    }

    private fun cancelBurst() {
        bursting = false
        remainingSteps = 0
    }

    /**
     * Applies the control requests of the step that has just run. Must be called once per
     * executed step, by the run loop.
     *
     * A `RunOneStep`/`RunNStep` request runs its steps and then pauses by itself: the loop
     * reports `running == false` once the last requested step has been executed. The steps of
     * two requests add up, and a `Pause` cancels what remains to run.
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
        when {
            requests.restart -> {
                simulator.reset()
                rewound = true
                running = true
                cancelBurst()
            }
            requests.rewind -> {
                simulator.reset()
                rewound = true
                running = false
                cancelBurst()
            }
            else -> {
                if (requests.pause) {
                    running = false
                    cancelBurst()
                }
                if (requests.steps > 0) {
                    // RunOneStep is RunNStep(1): run them, then pause
                    running = true
                    remainingSteps += requests.steps
                    bursting = true
                }
            }
        }
        return LoopUpdate(running, remainingSteps, rewound)
    }
}
