package com.centyllion.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Geometry of the shapes: they enumerate cells by lines (runs of columns), and the outline of
 * a shape is its filled form minus its interior, by construction.
 */
class ShapeRowsTest {

    private fun cellsOf(rows: List<CellRow>, width: Int, height: Int): Set<Pair<Int, Int>> {
        val cells = mutableSetOf<Pair<Int, Int>>()
        rows.forEachCell(width, height) { x, y -> cells.add(x to y) }
        return cells
    }

    /** Reference disc: the definition the runs must reproduce (dx² + dy² <= r²). */
    private fun referenceDisc(centerX: Int, centerY: Int, radius: Int): Set<Pair<Int, Int>> {
        val cells = mutableSetOf<Pair<Int, Int>>()
        for (dy in -radius..radius) for (dx in -radius..radius) {
            if (dx * dx + dy * dy <= radius * radius) cells.add((centerX + dx) to (centerY + dy))
        }
        return cells
    }

    // ---- rectangle ----

    @Test
    fun testFilledRectangleIsOneRunPerRow() {
        val rows = rectangleRows(2, 3, 6, 8)
        assertEquals(6, rows.size, "one run per row")
        rows.forEach { run ->
            assertEquals(2, run.from)
            assertEquals(6, run.to)
        }
        assertEquals((3..8).toList(), rows.map { it.row })
        assertEquals(30, cellsOf(rows, 100, 100).size, "5 columns by 6 rows")
    }

    @Test
    fun testCornersAreInAnyOrder() {
        assertEquals(rectangleRows(2, 3, 6, 8), rectangleRows(6, 8, 2, 3))
        assertEquals(rectangleRows(2, 3, 6, 8), rectangleRows(6, 3, 2, 8))
    }

    @Test
    fun testOutlineRectangleIsTheBorderOnly() {
        val outline = cellsOf(rectangleRows(2, 2, 6, 6, fill = false), 100, 100)
        // a 5x5 rectangle: 25 cells, the 3x3 interior is not part of the outline
        assertEquals(16, outline.size)
        assertTrue((2 to 2) in outline && (6 to 6) in outline)
        assertTrue((2 to 4) in outline && (6 to 4) in outline, "the left and right sides")
        assertTrue((4 to 2) in outline && (4 to 6) in outline, "the top and bottom rows are whole")
        assertTrue((4 to 4) !in outline, "the interior is left out")
    }

    @Test
    fun testThinRectangleOutlines() {
        // one row: the top and the bottom row are the same run, emitted once
        val singleRow = rectangleRows(0, 2, 4, 2, fill = false)
        assertEquals(1, singleRow.size)
        assertEquals(cellsOf(rectangleRows(0, 2, 4, 2), 10, 10), cellsOf(singleRow, 10, 10))
        // one column: the two sides of a row are the same cell, emitted once
        val singleColumn = rectangleRows(3, 1, 3, 5, fill = false)
        assertEquals(5, singleColumn.size, "one run per row, no duplicated cell")
        assertEquals((1..5).map { 3 to it }.toSet(), cellsOf(singleColumn, 10, 10))
        // one cell
        assertEquals(setOf(3 to 3), cellsOf(rectangleRows(3, 3, 3, 3, fill = false), 10, 10))
    }

    @Test
    fun testRectangleOutlineIsTheFilledRectangleMinusItsInterior() {
        val cases = listOf(
            listOf(0, 0, 0, 0), listOf(0, 0, 1, 1), listOf(4, 7, 4, 9),
            listOf(2, 3, 6, 4), listOf(0, 0, 4, 4), listOf(10, 10, 19, 14),
            listOf(-3, -3, 2, 2), listOf(90, 95, 110, 120), listOf(5, 5, 3, 3)
        )
        for ((x1, y1, x2, y2) in cases) {
            val filled = cellsOf(rectangleRows(x1, y1, x2, y2), 100, 100)
            val outline = cellsOf(rectangleRows(x1, y1, x2, y2, fill = false), 100, 100)
            // the interior is the filled rectangle with its border removed: a shape that is
            // two cells wide or high has no interior at all
            val left = minOf(x1, x2) + 1
            val right = maxOf(x1, x2) - 1
            val top = minOf(y1, y2) + 1
            val bottom = maxOf(y1, y2) - 1
            val interior = if (left <= right && top <= bottom) {
                cellsOf(rectangleRows(left, top, right, bottom), 100, 100)
            } else {
                emptySet()
            }
            assertEquals(filled - interior, outline, "rectangle $x1,$y1-$x2,$y2")
            assertTrue(outline.isNotEmpty())
        }
    }

    @Test
    fun testRectangleAtTheGridEdges() {
        // half of the rectangle is outside the grid: the cells outside are simply skipped
        val clipped = cellsOf(rectangleRows(-3, -3, 2, 2), 10, 10)
        assertEquals(9, clipped.size)
        assertTrue((0 to 0) in clipped && (2 to 2) in clipped)
        // entirely outside: nothing
        assertTrue(cellsOf(rectangleRows(-9, -9, -1, -1), 10, 10).isEmpty())
        assertTrue(cellsOf(rectangleRows(10, 0, 20, 5), 10, 10).isEmpty())
        // the outline of a clipped rectangle keeps the border cells that are inside
        val outline = cellsOf(rectangleRows(-2, -2, 3, 3, fill = false), 10, 10)
        assertTrue((0 to 3) in outline, "the bottom row is inside the grid")
        assertTrue((3 to 0) in outline, "the right side is inside the grid")
        assertTrue((1 to 1) !in outline, "the interior stays out, even when the border is clipped")
        assertTrue(outline.all { (x, y) -> x in 0..3 && y in 0..3 })
        assertTrue(cellsOf(rectangleRows(-2, -2, 3, 3), 10, 10).containsAll(outline))
    }

    // ---- circle ----

    @Test
    fun testFilledCircleMatchesTheReferenceDisc() {
        for (radius in listOf(0, 1, 2, 5, 12, 40)) {
            val rows = circleRows(50, 50, radius)
            assertEquals(2 * radius + 1, rows.size, "one run per row, radius $radius")
            assertEquals(referenceDisc(50, 50, radius), cellsOf(rows, 100, 100), "radius $radius")
        }
    }

    @Test
    fun testCircleRadiusZeroIsOneCell() {
        assertEquals(setOf(5 to 5), cellsOf(circleRows(5, 5, 0), 10, 10))
        assertEquals(setOf(5 to 5), cellsOf(circleRows(5, 5, 0, fill = false), 10, 10))
        assertEquals(setOf(5 to 5), cellsOf(circleRows(5, 5, -3), 10, 10), "a negative radius is read as 0")
    }

    @Test
    fun testCircleOutlineIsTheFilledCircleMinusTheInterior() {
        for (radius in listOf(0, 1, 2, 5, 12, 40)) {
            val filled = cellsOf(circleRows(50, 50, radius), 100, 100)
            val outline = cellsOf(circleRows(50, 50, radius, fill = false), 100, 100)
            val interior = referenceDisc(50, 50, radius - 1)
            assertEquals(filled - interior, outline, "radius $radius")
            assertEquals(referenceDisc(50, 50, radius) - interior, outline, "radius $radius")
        }
    }

    @Test
    fun testCircleOutlinesOfSmallRadii() {
        // radius 0: the cell itself
        assertEquals(1, cellsOf(circleRows(5, 5, 0, fill = false), 10, 10).size)
        // radius 1: the four neighbours, not the centre
        val one = cellsOf(circleRows(5, 5, 1, fill = false), 10, 10)
        assertEquals(setOf(5 to 4, 5 to 6, 4 to 5, 6 to 5), one)
        // radius 5: the ring, the centre is not on it
        val five = cellsOf(circleRows(50, 50, 5, fill = false), 100, 100)
        assertTrue((50 to 50) !in five)
        assertTrue((50 to 45) in five && (45 to 50) in five && (55 to 50) in five && (50 to 55) in five)
        assertTrue(five.size < cellsOf(circleRows(50, 50, 5), 100, 100).size)
    }

    @Test
    fun testCircleAtTheGridEdges() {
        // the quarter of the disc that falls in the grid
        val corner = cellsOf(circleRows(0, 0, 2), 10, 10)
        assertEquals(setOf(0 to 0, 1 to 0, 2 to 0, 0 to 1, 1 to 1, 0 to 2), corner)
        // its outline: the border cells of that quarter, (1,1) is on the ring, (0,0) is interior
        val outline = cellsOf(circleRows(0, 0, 2, fill = false), 10, 10)
        assertEquals(setOf(2 to 0, 1 to 1, 0 to 2), outline)
        // entirely outside
        assertTrue(cellsOf(circleRows(-20, -20, 5), 10, 10).isEmpty())
        // a large radius is clipped, not wrapped
        val large = cellsOf(circleRows(5, 5, 30), 10, 10)
        assertEquals(100, large.size, "a disc larger than the grid covers all of it")
    }

    @Test
    fun testIntegerSquareRoot() {
        assertEquals(0, integerSquareRoot(0))
        assertEquals(1, integerSquareRoot(1))
        assertEquals(3, integerSquareRoot(15))
        assertEquals(4, integerSquareRoot(16))
        assertEquals(4, integerSquareRoot(24))
        assertEquals(5, integerSquareRoot(25))
        assertEquals(1_000_000, integerSquareRoot(1_000_000_000_000L))
    }

    @Test
    fun testRowsAreRowMajorAndOrdered() {
        val rows = circleRows(10, 10, 4, fill = false)
        assertEquals(rows.map { it.row }, rows.map { it.row }.sorted(), "rows are emitted top to bottom")
        assertTrue(rows.all { it.from <= it.to }, "a run never goes backwards")
    }
}
