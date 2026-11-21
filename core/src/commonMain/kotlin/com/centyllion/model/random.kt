package com.centyllion.model

/**
 * Deterministic random engine of a simulation.
 *
 * Every cell owns its own 32-bit stream, initialised from the simulation seed and the cell
 * index. A cell therefore draws the same numbers whatever the order in which the cells are
 * processed, and whatever the number of threads: two runs sharing a seed give exactly the
 * same trajectory, on JVM as on JS (32-bit integer arithmetic is exact on both, as long as
 * the generator only uses additions, xor, shifts and Int multiplications).
 *
 * The generator is SplitMix32, iterated twice per double so that a draw keeps 51 bits of
 * precision. Draws that cannot be attached to a cell (concurrency resolution) use a
 * separate stream.
 */
class SimulationRandom(private val seed: Long, private val size: Int) {

    private val states = IntArray(size.coerceAtLeast(1)) { index -> seedFor(seed, index) }

    /** Stream used by the draws that are not attached to a cell. */
    private var global = seedFor(seed, -1)

    /** Uniform double in `[0, 1)` drawn from the stream of [cell]. */
    fun nextDouble(cell: Int): Double {
        val hi = nextBits(cell) ushr 5 // 27 bits
        val lo = nextBits(cell) ushr 6 // 26 bits
        return (hi * 67108864.0 + lo) / 9007199254740992.0
    }

    /** Uniform int in `[0, bound)` drawn from the stream of [cell]. */
    fun nextInt(cell: Int, bound: Int): Int {
        if (bound <= 1) return 0
        return (nextDouble(cell) * bound).toInt().coerceIn(0, bound - 1)
    }

    /** Uniform double in `[0, 1)` drawn from the global stream. */
    fun nextDouble(): Double {
        val hi = nextBits(-1) ushr 5
        val lo = nextBits(-1) ushr 6
        return (hi * 67108864.0 + lo) / 9007199254740992.0
    }

    /** Uniform int in `[0, bound)` drawn from the global stream. */
    fun nextInt(bound: Int): Int {
        if (bound <= 1) return 0
        return (nextDouble() * bound).toInt().coerceIn(0, bound - 1)
    }

    /** Rewinds every stream to its seed state: the next run repeats the same trajectory. */
    fun reset() {
        for (index in states.indices) states[index] = seedFor(seed, index)
        global = seedFor(seed, -1)
    }

    private fun nextBits(cell: Int): Int =
        if (cell < 0) {
            global = splitMix32(global)
            global
        } else {
            val index = if (cell < states.size) cell else cell % states.size
            val next = splitMix32(states[index])
            states[index] = next
            next
        }

    private companion object {
        /** SplitMix32: one 32-bit step, same result on JVM and JS. */
        fun splitMix32(value: Int): Int {
            var z = value + -0x61c88647 // 0x9e3779b9
            z = (z xor (z ushr 16)) * 0x21f0aaad
            z = (z xor (z ushr 15)) * 0x735a2d97
            return z xor (z ushr 15)
        }

        /** Stream seed for a cell: mixes both halves of the simulation seed with the index. */
        fun seedFor(seed: Long, index: Int): Int {
            val low = (seed and 0xffffffffL).toInt()
            val high = (seed ushr 32).toInt()
            var z = splitMix32(low xor (index * 0x9e3779b9.toInt()))
            z = splitMix32(z xor high)
            z = splitMix32(z xor (index * 0x85ebca6b.toInt()))
            return if (z == 0) 0x2545f491 else z
        }
    }
}
