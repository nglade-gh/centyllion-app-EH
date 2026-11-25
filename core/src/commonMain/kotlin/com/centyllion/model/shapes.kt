package com.centyllion.model

import kotlin.math.sqrt

/**
 * One row of an area of the grid: the cells between [from] and [to], both included.
 *
 * The coordinates are plain grid coordinates, in the order the shape builds them
 * (`from <= to`): a row may start before column 0 or end past the last column of a grid,
 * which is why the callers clamp or fold it when they walk it.
 *
 * An area is a list of these runs, one or two per row, row after row: the shapes below
 * enumerate the cells **by lines** (a run of columns) instead of testing every cell of the
 * bounding box, so a filled shape costs one run per row and its outline costs two.
 */
data class CellRow(val row: Int, val from: Int, val to: Int)

/**
 * Runs of the rectangle spanned by the two corners, in any order and both included.
 *
 * With [fill] the area is solid: one run per row, from the left column to the right one.
 * Without [fill] only the border is emitted: the top and the bottom rows are emitted whole,
 * and each row in between keeps two cells, its left and its right one. The outline is
 * therefore, by construction, the filled rectangle of the same corners minus the filled
 * rectangle (left + 1, top + 1)-(right - 1, bottom - 1): there is no second definition of
 * the shape.
 */
fun rectangleRows(x1: Int, y1: Int, x2: Int, y2: Int, fill: Boolean = true): List<CellRow> {
    val left = minOf(x1, x2)
    val right = maxOf(x1, x2)
    val top = minOf(y1, y2)
    val bottom = maxOf(y1, y2)
    if (fill) return (top..bottom).map { y -> CellRow(y, left, right) }
    // one row only: the top and the bottom row are the same run
    if (top == bottom) return listOf(CellRow(top, left, right))
    val rows = ArrayList<CellRow>(2 * (bottom - top) + 2)
    rows.add(CellRow(top, left, right))
    if (left == right) {
        // one column only: the two sides of a row are the very same cell
        for (y in top + 1 until bottom) rows.add(CellRow(y, left, left))
    } else {
        for (y in top + 1 until bottom) {
            rows.add(CellRow(y, left, left))
            rows.add(CellRow(y, right, right))
        }
    }
    rows.add(CellRow(bottom, left, right))
    return rows
}

/**
 * Runs of the disc centred on (centerX, centerY).
 *
 * With [fill] every row of the disc is emitted whole. Without [fill] a row is the outer run
 * minus the inner run of the disc of radius `radius - 1`: the two border runs of that row.
 * The outline is therefore, by construction, the filled disc of the same radius minus its
 * interior: there is no second definition of the disc. A radius of 0 is a single cell, whose
 * outline is that cell (the disc of radius -1 is empty).
 *
 * A negative radius is read as 0.
 */
fun circleRows(centerX: Int, centerY: Int, radius: Int, fill: Boolean = true): List<CellRow> {
    val r = maxOf(0, radius)
    val rows = ArrayList<CellRow>(2 * r + 1)
    for (dy in -r..r) {
        val y = centerY + dy
        val outer = halfWidthAt(r, dy)
        // no interior at all (or a filled shape): the whole run of the row
        val inner = if (fill || r == 0) -1 else halfWidthAt(r - 1, dy)
        if (inner < 0) {
            rows.add(CellRow(y, centerX - outer, centerX + outer))
        } else {
            if (centerX - outer <= centerX - inner - 1) {
                rows.add(CellRow(y, centerX - outer, centerX - inner - 1))
            }
            if (centerX + inner + 1 <= centerX + outer) {
                rows.add(CellRow(y, centerX + inner + 1, centerX + outer))
            }
        }
    }
    return rows
}

/** Whole cells between the axis of a disc of [radius] and its border on the row at [dy], -1 outside. */
private fun halfWidthAt(radius: Int, dy: Int): Int {
    if (radius < 0) return -1
    val squared = radius.toLong() * radius - dy.toLong() * dy
    if (squared < 0) return -1
    return integerSquareRoot(squared)
}

/**
 * Largest integer whose square is at most [value] (0 when [value] is negative).
 *
 * Integer arithmetic, so that the same radius always gives the same cells: a floating point
 * square root could round a perfect square below its root and open a hole in a row.
 */
internal fun integerSquareRoot(value: Long): Int {
    if (value <= 0L) return 0
    var root = sqrt(value.toDouble()).toInt()
    while (root > 0 && root.toLong() * root > value) root -= 1
    while ((root + 1).toLong() * (root + 1) <= value) root += 1
    return root
}

/**
 * Calls [block] with the coordinates of every cell of these runs inside a grid of
 * [width] x [height].
 *
 * The cells outside the grid are simply skipped: nothing is folded here, the caller decides
 * (the paste of the view wraps them when the Toric switch is on, and drops them otherwise).
 */
inline fun List<CellRow>.forEachCell(width: Int, height: Int, block: (x: Int, y: Int) -> Unit) {
    for (run in this) {
        if (run.row < 0 || run.row >= height) continue
        val from = maxOf(run.from, 0)
        val to = minOf(run.to, width - 1)
        for (x in from..to) block(x, run.row)
    }
}
