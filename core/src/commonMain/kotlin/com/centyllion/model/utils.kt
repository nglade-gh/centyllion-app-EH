package com.centyllion.model

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@OptIn(ExperimentalContracts::class)
fun <T>Boolean.orNull(block: () -> T): T? {
    contract { callsInPlace(block, InvocationKind.AT_MOST_ONCE) }
    return if (this) block() else null
}

fun <T> List<List<T>>.allCombinations(): List<List<T>> {
    var count = 1
    for (element in this) count *= element.size
    val result = ArrayList<List<T>>(count)
    combine(emptyList(), result)
    return result
}

internal fun <T> List<List<T>>.combine(prefix: List<T>, result: ArrayList<List<T>>) {
    when {
        isEmpty() -> result.add(prefix)
        else -> {
            val tail: List<List<T>> = drop(1)
            first().forEach { tail.combine(prefix + it, result) }
        }
    }
}

/** Creates a new id excluding [existing] */
fun availableId(existing: Collection<Int>) = existing.toSet().let {
    for (i in existing.indices) {
        if (!it.contains(i)) return i
    }
    existing.size
}

/** Creates a new name excluding [existing] */
fun availableName(existing: Collection<String>, prefix: String): String = existing.toSet().let {
    if (!it.contains(prefix)) return prefix
    for (i in 1..existing.size) {
        "$prefix $i".let { name -> if (!it.contains(name)) return name }
    }
    return "$prefix ${existing.size + 1}"
}

/**
 * Vivid, well separated colours for new agents and fields.
 *
 * Picking randomly among all the CSS names gave dull or dark colours too often
 * (Black, DimGrey, MidnightBlue, DarkGreen…): the grid looked muddy. These
 * names all exist in [colorNames] so they can be edited and rendered.
 */
val vividColorNames = listOf(
    "Red", "Orange", "Gold", "Chartreuse", "Lime", "SpringGreen",
    "Cyan", "DeepSkyBlue", "DodgerBlue", "RoyalBlue", "BlueViolet", "DarkViolet",
    "Magenta", "DeepPink", "Crimson", "Chocolate", "Violet"
)

/** Finds a random colour excluding the already [used] ones (vivid colours first). */
fun availableColor(used: Collection<String>): String {
    val usedColors = used.toSet()
    val vivid = vividColorNames.filterNot { it in usedColors }
    if (vivid.isNotEmpty()) return vivid.random()
    val others = colorNames.keys.filterNot { it in usedColors }
    return others.randomOrNull() ?: "red"
}

fun Float.flatten(threshold: Float, line: Float = 0f) = if (this <= threshold) line else this

/**
 * Read-only view of the cartesian product of [lists], without materialising it.
 *
 * Combination `c` is read in mixed-radix order: the last list varies fastest. The former
 * `allCombinations()` built a list of lists for every cell, which dominated the allocation
 * profile of a step; the order of enumeration is unchanged, so the drawn combination and
 * the probability law stay identical.
 */
class CombinationView<T>(private val lists: List<List<T>>) {
    /** Number of combinations (0 when one of the lists is empty). */
    val size: Int = lists.fold(1) { acc, list -> acc * list.size }

    /** Element of the combination [combination] coming from the list [listIndex]. */
    fun get(combination: Int, listIndex: Int): T {
        var radix = 1
        for (k in listIndex + 1 until lists.size) radix *= lists[k].size
        val digit = if (radix == 0) 0 else (combination / radix) % lists[listIndex].size
        return lists[listIndex][digit]
    }

    /** All the elements of [combination], in list order. */
    fun elements(combination: Int): List<T> = List(lists.size) { get(combination, it) }
}
