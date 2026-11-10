package com.centyllion.model

actual class ParallelExecutor actual constructor(threads: Int) {
    actual fun <T> mapBlocks(blocks: List<ParallelBlock>, block: (ParallelBlock) -> T): List<T> {
        // Kotlin/JS is single-threaded: execute sequentially.
        return blocks.map { block(it) }
    }

    actual fun close() {
        // Nothing to release.
    }
}

actual fun availableProcessors(): Int = 1
