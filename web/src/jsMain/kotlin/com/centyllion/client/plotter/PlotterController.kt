package com.centyllion.client.plotter

import bulma.*
import com.centyllion.client.toFixed
import io.data2viz.geom.Size
import io.data2viz.geom.size
import org.w3c.dom.HTMLElement
import kotlin.math.roundToInt
import kotlin.properties.Delegates.observable

class PlotterController(
    title: String, val stepName: String,
    plot: List<Plot>, size: Size = size(800.0, 400.0),
    val roundPoints: Boolean = false
): Controller<List<Plot>, Size, Div> {

    // Each plotter generation has its own canvas element. When the plotter is
    // recreated (model change, size change), the previous canvas is detached
    // from the DOM: the data2viz renderer still bound to it (event listeners,
    // timers) can no longer paint on the visible chart, which used to leave
    // stale, doubled axis labels behind.
    private val canvasHolder = Div()
    private var canvas = canvas { }

    init {
        canvasHolder.root.appendChild(canvas.root)
    }

    override var data: List<Plot> by observable(plot) { _, old, new ->
        if (old != new) {
            // recreate the plotter (new canvas)
            plotter = createPlotter(new)
            legend.columns = createColumns(new)
        } else {
            // the chart is the same but the model was updated, a reset is required
            reset()
        }
    }

    var size get() = context; set(value) { context = value }

    /** Sliding time window width in steps applied to the underlying plotter (0 = full history). */
    var window: Int = 0
        set(value) {
            field = value
            plotter.window = value
        }

    override var context: Size by observable(size) { _, old, new ->
        if (old != new) {
            // new canvas with the new size; the old canvas (and its renderer) is detached
            plotter = createPlotter(data)
        }
    }

    override var readOnly: Boolean = false

    private var plotter: LinePlotter = createPlotter(data)

    val title = SubTitle(title).apply { root.classList.add("has-text-centered") }

    private val stepValueSpan = Label("$stepName -")
    private val legendValueSpans = mutableListOf<HtmlWrapper<HTMLElement>>()

    val legend = Columns(multiline = true).apply { columns = createColumns(data) }

    override val container: Div = Div(this.title, canvasHolder, legend)

    override fun refresh() { renderRequest() }

    private fun createPlotter(plots: List<Plot>): LinePlotter {
        val newCanvas = canvas { }
        newCanvas.root.width = context.width.toInt()
        newCanvas.root.height = context.height.toInt()
        // detach the previous canvas (and the renderer bound to it)
        canvasHolder.root.replaceChild(newCanvas.root, canvas.root)
        canvas = newCanvas
        return LinePlotter(newCanvas.root, plots, context) { label ->
        val pointsForLabel = plotter.pointsForLabel(label)
        stepValueSpan.text = "$stepName $label"
        legendValueSpans.forEachIndexed { index, valueSpan ->
            val get = pointsForLabel?.get(index)?.let {
                if (roundPoints) it.roundToInt() else it.toFixed(3)
            }
            valueSpan.text = "${get ?: "-"}"
        }
    }.apply {
        xTick = 50
        window = this@PlotterController.window
    }
    }

    private fun colorIcon(color: String) = Icon("").apply {
        root.style.backgroundColor = color
        root.style.borderRadius = "50%"
        root.style.color = color
        root.style.marginRight = "0.5rem"
    }

    private fun createColumns(plots: List<Plot>): List<Column> {
        legendValueSpans.clear()
        return listOf(Column(stepValueSpan, narrow = true)) +
            plots.map { plot ->
                val valueSpan = span("0", "is-italic")
                legendValueSpans.add(valueSpan)
                Column(
                    Level(
                        center = listOf(colorIcon(plot.stroke.rgbHex), span(plot.label), span(":"), valueSpan),
                        mobile = true
                    ),
                    narrow = true
                ).apply {
                    root.style.textDecorationLine = if (plotter.isHidden(plot)) "line-through" else ""
                    root.onclick = {
                        val hidden = plotter.toggleHiddenPlot(plot)
                        root.style.textDecorationLine = if (hidden) "line-through" else ""
                        Unit
                    }
                }
            }
    }

    fun reset() {
        plotter.clear()
    }

    fun push(x: Int, ys: List<Double>) {
        plotter.push(x, ys)
    }

    fun renderRequest() {
        plotter.renderRequest()
    }
}
