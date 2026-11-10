package com.centyllion.model

/**
 * Executes a list of contiguous cell blocks in parallel.
 *
 * On JVM, a fixed thread pool is used. On JS (single-threaded), the blocks are
 * executed sequentially.
 */
expect class ParallelExecutor(threads: Int) {
    /** Runs [block] for each block and returns the results in the same order. */
    fun <T> mapBlocks(blocks: List<ParallelBlock>, block: (ParallelBlock) -> T): List<T>

    /** Releases the underlying resources (thread pool). */
    fun close()
}

/** Number of processors available to the current runtime. */
expect fun availableProcessors(): Int

/** Recommended number of worker threads: 80% of the available processors. */
fun recommendedThreads(): Int = maxOf(1, (availableProcessors() * 0.8).toInt())

/** A contiguous range of cell indices. */
data class ParallelBlock(val index: Int, val range: IntRange)

/** Splits [size] indices into [count] contiguous blocks of roughly equal size. */
fun splitIntoBlocks(size: Int, count: Int): List<ParallelBlock> {
    if (count <= 1 || size <= 0) return listOf(ParallelBlock(0, 0 until size))
    val base = size / count
    val remainder = size % count
    val blocks = mutableListOf<ParallelBlock>()
    var start = 0
    for (i in 0 until count) {
        val length = base + if (i < remainder) 1 else 0
        if (length > 0) blocks.add(ParallelBlock(i, start until (start + length)))
        start += length
    }
    return blocks
}
