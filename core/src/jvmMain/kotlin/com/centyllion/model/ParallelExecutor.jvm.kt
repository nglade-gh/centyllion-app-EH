package com.centyllion.model

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

actual class ParallelExecutor actual constructor(threads: Int) {

    private val executor = Executors.newFixedThreadPool(threads) { r ->
        Thread(r).apply {
            isDaemon = true
            name = "centyllion-pool-${poolThreadCounter.incrementAndGet()}"
        }
    }

    companion object {
        private val poolThreadCounter = AtomicInteger(0)
    }

    actual fun <T> mapBlocks(blocks: List<ParallelBlock>, block: (ParallelBlock) -> T): List<T> {
        return blocks.map { b -> executor.submit(Callable { block(b) }) }.map { it.get() }
    }

    actual fun close() {
        executor.shutdown()
    }
}

actual fun availableProcessors(): Int =
    System.getProperty("centyllion.processors")?.toIntOrNull()
        ?: Runtime.getRuntime().availableProcessors()
