package com.centyllion.model

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies that the CPU parallelization of the update step is actually active:
 * with the parallel update plan and a grid of 100x100 or more, the per-cell work
 * runs on the pool worker threads; on smaller grids it stays sequential.
 *
 * The pool threads created by `ParallelExecutor` are named "centyllion-pool-N".
 */
class ParallelUpdateVerificationTest {

    private fun heavyModel(): GrainModel = GrainModel(
        grains = listOf(
            Grain(id = 0, name = "A", halfLife = 1000),
            Grain(id = 1, name = "B", halfLife = 1000)
        ),
        behaviours = (0 until 6).map { i ->
            Behaviour(
                name = "b$i",
                formula = "sin(step) * cos(x) > -1 && count(0) >= 0 && count($i) >= 0",
                mainReactiveId = 0,
                mainProductId = if (i % 2 == 0) 1 else 0,
                probability = 1.0
            )
        },
        fields = listOf(
            Field(id = 0, name = "F", formula = "current * 0.95 + 0.001 * count(0)")
        )
    )

    private fun poolThreadNames(): Set<String> =
        Thread.getAllStackTraces().keys
            .filter { it.name.startsWith("centyllion-pool-") && it.state != Thread.State.TERMINATED }
            .map { it.name }
            .toSet()

    /**
     * Runs [steps] steps on a background thread and samples the thread table.
     * Returns the number of pool threads (outside [known]) ever observed and
     * the maximum number of them observed RUNNABLE at once.
     */
    private fun runStepsSampled(
        simulator: Simulator, steps: Int, known: Set<String> = emptySet()
    ): Pair<Int, Int> {
        var seen = 0
        var maxConcurrent = 0
        val stepThread = Thread {
            repeat(steps) { simulator.oneStep() }
        }
        stepThread.start()
        while (stepThread.isAlive) {
            val live = poolThreadNames() - known
            if (live.isNotEmpty()) seen = maxOf(seen, live.size)
            val running = Thread.getAllStackTraces().keys.count {
                it.name in live && it.state == Thread.State.RUNNABLE
            }
            if (running > maxConcurrent) maxConcurrent = running
            Thread.sleep(1)
        }
        stepThread.join()
        return seen to maxConcurrent
    }

    @Test
    fun `parallel update uses worker threads on large grids`() {
        val known = poolThreadNames()
        val simulation = createSimulation(size = 200) { 0 }
        val simulator = Simulator(heavyModel(), simulation)
        val (seen, maxConcurrent) = runStepsSampled(simulator, 5, known)
        simulator.close()
        println(
            "parallel 200x200: processors=${availableProcessors()} " +
                    "recommendedThreads=${recommendedThreads()} " +
                    "pool threads seen=$seen maxConcurrent=$maxConcurrent"
        )
        assertTrue(seen > 0, "expected pool worker threads to be used on a 200x200 grid")
    }

    @Test
    fun `small grids stay sequential`() {
        val known = poolThreadNames()
        val simulation = createSimulation(size = 25) { 0 }
        val simulator = Simulator(heavyModel(), simulation)
        val (seen, maxConcurrent) = runStepsSampled(simulator, 5, known)
        simulator.close()
        println("sequential 25x25: new pool threads seen=$seen maxConcurrent=$maxConcurrent")
        assertTrue(seen == 0, "expected no pool worker threads on a 25x25 grid (saw $seen)")
        assertTrue(maxConcurrent == 0, "expected no runnable pool threads on a 25x25 grid")
    }

    @Test
    fun `sequential update plan is single threaded on large grids`() {
        val known = poolThreadNames()
        val model = heavyModel().copy(updatePlan = SEQUENTIAL_UPDATE_PLAN)
        val simulation = createSimulation(size = 200) { 0 }
        val simulator = Simulator(model, simulation)
        val (seen, maxConcurrent) = runStepsSampled(simulator, 3, known)
        simulator.close()
        println("sequential plan 200x200: new pool threads seen=$seen maxConcurrent=$maxConcurrent")
        assertTrue(seen == 0, "expected no pool worker threads with the Sequential plan (saw $seen)")
    }
}
