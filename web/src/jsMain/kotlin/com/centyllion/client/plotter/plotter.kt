package com.centyllion.client.plotter

import com.centyllion.model.colorNames
import io.data2viz.axis.Orient
import io.data2viz.axis.axis
import io.data2viz.color.Color
import io.data2viz.color.Colors
import io.data2viz.geom.Size
import io.data2viz.math.pct
import io.data2viz.scale.Scales
import io.data2viz.viz.*
import org.w3c.dom.HTMLCanvasElement
import kotlin.math.roundToInt
import kotlin.properties.Delegates.observable

data class Range(val min: Double, val max: Double)

fun range(min: Double, max: Double) = Range(min, max)

fun String.toRGB(): Color = colorNames[this]?.let { Colors.rgb(it.first, it.second, it.third, 100.pct) } ?: Colors.Web.red

data class Plot(
    val label: String,
    val stroke: Color = Colors.Web.steelblue,
    val strokeWidth: Double = 1.0,
    val startHidden: Boolean = false
)

private data class PlotPoint(
    val step: Int, val points: List<Double>
)

class LinePlotter(
    val canvas: HTMLCanvasElement,
    val plots: List<Plot>, size: Size,
    val onStepMove: (Int) -> Unit = { }
) {
    var size: Size by observable(size) { _, old, new ->
        if (old != new) {
            visual.size = new
            xScale = newXScale()
            yScale = newYScale()
            rebuild()
        }
    }

    private val points: MutableList<PlotPoint> = mutableListOf()

    // TODO make margins and size mutable
    // TODO checks that margins aren't bigger than size
    val margins = Margins(40.5, 30.5, 50.5, 50.5)

    val chartWidth get() = size.width - margins.hMargins
    val chartHeight get() = size.height - margins.vMargins

    var xTick: Int = 1

    /** Sliding time window width (in steps): 0 plots from step 0 to the current
     *  step, otherwise the chart shows the last [window] steps. */
    var window: Int = 0
        set(value) {
            field = value.coerceAtLeast(0)
            xScale = newXScale()
            rebuild()
            invalidate()
        }

    private fun xMin(): Int = if (window > 0) (xMax - window).coerceAtLeast(0) else 0

    var xMax: Int by observable(0) { _, old, new ->
        if (old != new) {
            xScale = newXScale()
            rebuild()
        }
    }

    var yTick: Double = 1.0

    var yRange: Range by observable(computeYRange()) { _, old, new ->
        if (old != new) {
            yScale = newYScale()
            rebuild()
        }
    }

    private fun computeYRange() = range(
        points.flatMap { it.points.filterIndexed { index, d -> !hiddenPlots[index] } }.minOrNull() ?: 0.0,
        points.flatMap { it.points.filterIndexed { index, d -> !hiddenPlots[index] } }.maxOrNull() ?: 0.0,
    )

    // linear scale for x
    private var xScale = newXScale()

    private fun newXScale() = Scales.Continuous.linearRound {
        domain = listOf(xMin().toDouble(), xMax.toDouble())
        range = listOf(0.0, chartWidth)
    }

    // linear scale for y
    private var yScale = newYScale()

    private fun newYScale() = Scales.Continuous.linear {
        domain = listOf(yRange.min, yRange.max)
        range = listOf(chartHeight, 0.0) // <- y is mapped in the reverse order (in SVG, javafx (0,0) is top left.
    }

    private fun GroupNode.xAxis() = group {
        transform { translate(y = chartHeight + 10) }
        axis(Orient.BOTTOM, xScale)
    }

    private fun GroupNode.yAxis() = group {
        transform { translate(x = -10.0) }
        axis(Orient.LEFT, yScale)
    }

    private val hiddenPlots = Array(plots.size) { plots[it].startHidden }

    private var plotPaths = mutableListOf<PathNode>()

    private var redraw = false

    private var lastBuildStep = -1

    private fun Viz.build() {
        clear()
        lastBuildStep = -1
        group {
            transform { translate(x = margins.left, y = margins.top) }
            group { yAxis() }
            group { xAxis() }

            group {
                plotPaths.clear()
                // in window mode only the points inside the current window are drawn
                val visiblePoints = if (window > 0) points.filter { it.step >= xMin() } else points
                if (visiblePoints.isNotEmpty()) {
                    lastBuildStep = visiblePoints.last().step
                    for (plotIndex in plots.indices) {
                        group {
                            plotPaths.add(path {
                                fill = null
                                val element = plots[plotIndex]
                                strokeColor = if (!hiddenPlots[plotIndex]) element.stroke else null
                                strokeWidth = element.strokeWidth

                                moveTo(xScale(visiblePoints[0].step), yScale(visiblePoints[0].points[plotIndex]))
                                for (i in 1 until visiblePoints.size) {
                                    lineTo(xScale(visiblePoints[i].step), yScale(visiblePoints[i].points[plotIndex]))
                                }
                            })
                        }
                    }
                }
            }
        }
    }

    private var currentStep = -1

    val visual: Viz = viz {
        this.size = this@LinePlotter.size
        build()

        on(KPointerEvents.move) {
            // TODO there is a DPI problem with the position, it needs to be solved
            val step = xScale.invert(it.pos.x-margins.left).roundToInt().coerceIn(0, xMax)
            if (step != currentStep) onStepMove(step)
            currentStep =  step
            EventPropagation.Stop
        }

        bindRendererOn(canvas)
    }

    private fun findNewXMax(current: Int, pushed: Int, tick: Int): Int {
        var newMax = current
        while (newMax < pushed) newMax += tick
        return newMax
    }

    private fun findNewMax(current: Double, pushed: Double, tick: Double): Double {
        var newMax = current
        while (newMax < pushed) newMax += tick
        return newMax
    }

    private fun findNewMin(current: Double, pushed: Double, tick: Double): Double {
        var newMin = current
        while (newMin > pushed) newMin -= tick
        return newMin
    }

    fun push(x: Int, ys: List<Double>) {
        // register point
        points.add(PlotPoint(x, ys))

        var newMin = yRange.min
        var newMax = yRange.max
        for (i in plots.indices) {
            // updates min and max if not hidden
            if (!hiddenPlots[i]) {
                if (ys[i] < newMin) newMin = ys[i]
                if (ys[i] > newMax) newMax = ys[i]
            }
        }

        // updates ranges (a scale change triggers a rebuild that already includes the new point)
        xMax = findNewXMax(xMax, x, xTick)
        yRange = range(findNewMin(yRange.min, newMin, yTick), findNewMax(yRange.max, newMax, yTick))

        // update paths — skip when the rebuild above already contains the new point
        if (x != lastBuildStep) {
            for (i in plotPaths.indices) {
                if (!hiddenPlots[i]) plotPaths[i].lineTo(xScale(x), yScale(ys[i]))
            }
        }
        invalidate()
    }

    fun invalidate() {
        redraw = true
    }

    fun renderRequest() {
        if (redraw) {
            // clear the canvas so stale traces from the previous x scale don't remain
            (canvas.getContext("2d") as? org.w3c.dom.CanvasRenderingContext2D)
                ?.clearRect(0.0, 0.0, canvas.width.toDouble(), canvas.height.toDouble())
            visual.render()
            redraw = false
        }
    }

    fun rebuild() {
        visual.build()
        invalidate()
    }

    fun clear() {
        points.clear()
        xMax = 0
        yRange = range(0.0, 0.0)
        rebuild()
    }

    fun pointsForLabel(step: Int) = points.find { it.step == step }?.points

    fun isHidden(plot: Plot) = plots.indexOf(plot).let {
        if (it >= 0) hiddenPlots[it] else false
    }
    
    fun toggleHiddenPlot(plot: Plot): Boolean {
        val index = plots.indexOf(plot)
        return if (index >= 0) {
            val new = !hiddenPlots[index]
            hiddenPlots[index] = new
            yRange = computeYRange()
            rebuild()
            renderRequest()
            new
        } else false
    }
}

