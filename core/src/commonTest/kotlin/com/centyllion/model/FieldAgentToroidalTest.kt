package com.centyllion.model

import com.centyllion.expression.parseExpression
import com.centyllion.expression.v
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression test: field(id, dx, dy) and agent(x, y) must be TOROIDAL,
 * consistent with Simulation.moveIndex (used by count() and neighbours()).
 *
 * Previously field() used a raw linear index (y*width + x) with no modulo,
 * so a horizontal offset leaving the grid aliased to the WRONG row (e.g.
 * from (0,1) with dx=-1 it returned the value of cell (9,0) instead of
 * wrapping), while a vertical offset returned 0 — inconsistent behaviour.
 */
class FieldAgentToroidalTest {

    private val width = 10

    /** Evaluator over a 10x10 grid whose field 0 holds its own index
     *  (so value of cell (x,y) = y*width + x) and whose agents also hold
     *  their index. All values are distinct, so any aliasing is detected. */
    private class Harness {
        val model = GrainModel(
            grains = listOf(Grain(id = 0, name = "A")),
            fields = listOf(Field(id = 0, name = "F"))
        )
        val simulator = Simulator(model, createSimulation(size = 10) { it })
        init {
            val f = simulator.fields[0]!!
            for (i in 0 until 100) f[i] = i.toFloat()
        }
        val ev = NetbioDynEvaluator(simulator)

        fun field(x: Int, y: Int, dx: Int, dy: Int): Float {
            ev.x = x.v
            ev.y = y.v
            return ev.evaluate("field(0, $dx, $dy)".parseExpression()).float
        }

        fun field(x: Int, y: Int): Float {
            ev.x = x.v
            ev.y = y.v
            return ev.evaluate("field(0)".parseExpression()).float
        }

        fun agent(x: Int, y: Int): Int {
            ev.x = 0.v
            ev.y = 0.v
            return ev.evaluate("agent($x, $y)".parseExpression()).int
        }
    }

    private fun wrapX(v: Int) = ((v % width) + width) % width
    private fun wrapY(v: Int) = ((v % width) + width) % width

    @Test
    fun fieldInGrid() {
        val h = Harness()
        // value of cell (x,y) = y*width + x
        assertEquals(55f, h.field(5, 5), 1e-6f)
        assertEquals(56f, h.field(5, 5, 1, 0), 1e-6f)   // east
        assertEquals(65f, h.field(5, 5, 0, 1), 1e-6f)   // south
        assertEquals(44f, h.field(5, 5, -1, -1), 1e-6f) // north-west
        assertEquals(0f, h.field(0, 0), 1e-6f)
    }

    @Test
    fun fieldWrapsHorizontally() {
        val h = Harness()
        // left edge, dx=-1 -> wraps to rightmost column (previously aliased!)
        assertEquals(19f, h.field(0, 1, -1, 0), 1e-6f)  // (0,1)+(-1,0) -> (9,1) = 19
        assertEquals(29f, h.field(0, 2, -1, 0), 1e-6f)  // (0,2)+(-1,0) -> (9,2) = 29
        // right edge, dx=+1 -> wraps to leftmost column (previously aliased!)
        assertEquals(10f, h.field(9, 1, 1, 0), 1e-6f)   // (9,1)+(1,0)  -> (0,1) = 10
        assertEquals(50f, h.field(9, 5, 1, 0), 1e-6f)   // (9,5)+(1,0)  -> (0,5) = 50
    }

    @Test
    fun fieldWrapsVertically() {
        val h = Harness()
        // top edge, dy=-1 -> wraps to bottom row
        assertEquals(93f, h.field(3, 0, 0, -1), 1e-6f)  // (3,0)+(0,-1) -> (3,9) = 93
        // bottom edge, dy=+1 -> wraps to top row
        assertEquals(3f, h.field(3, 9, 0, 1), 1e-6f)    // (3,9)+(0,1)  -> (3,0) = 3
    }

    @Test
    fun fieldWrapsAtCornersAndLargeOffsets() {
        val h = Harness()
        // corner: (0,0)+(-1,-1) -> (9,9) = 99
        assertEquals(99f, h.field(0, 0, -1, -1), 1e-6f)
        // large negative offset: (5,5)+(-12,1) -> (3,6) = 63
        assertEquals(63f, h.field(5, 5, -12, 1), 1e-6f)
        // large positive offset: (5,5)+(12,-1) -> (7,4) = 47
        assertEquals(47f, h.field(5, 5, 12, -1), 1e-6f)
    }

    @Test
    fun fieldUnknownIdReturnsZero() {
        val h = Harness()
        ev0(h)
        assertEquals(0f, h.ev.evaluate("field(99, 1, 0)".parseExpression()).float, 1e-6f)
    }

    private fun ev0(h: Harness) { h.ev.x = 5.v; h.ev.y = 5.v }

    @Test
    fun agentWrapsLikeField() {
        val h = Harness()
        // agents hold their index: agent(x,y) = y*width + x (toroidal)
        assertEquals(55, h.agent(5, 5))
        // left edge wraps to rightmost column
        assertEquals(19, h.agent(wrapX(0 - 1), 1))   // agent(-1, 1) -> (9,1) = 19
        assertEquals(19, h.agent(-1, 1))
        // right edge wraps to leftmost column
        assertEquals(10, h.agent(10, 1))              // agent(10,1) -> (0,1) = 10
        // top / bottom wrap
        assertEquals(93, h.agent(3, -1))              // (3,-1) -> (3,9) = 93
        assertEquals(3, h.agent(3, 10))               // (3,10) -> (3,0) = 3
    }
}
