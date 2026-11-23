package com.centyllion.client.controller.model

import bulma.*
import bulma.Field
import com.centyllion.client.page.BulmaPage
import com.centyllion.model.*
import kotlinx.browser.document
import kotlinx.browser.window
import org.khronos.webgl.Uint8Array
import org.khronos.webgl.get
import org.khronos.webgl.set
import org.w3c.dom.CanvasRenderingContext2D
import org.w3c.dom.Path2D
import org.w3c.dom.HTMLCanvasElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLLabelElement
import org.w3c.dom.ImageData
import org.w3c.dom.Node
import org.w3c.dom.events.Event
import org.w3c.dom.events.EventTarget
import org.w3c.dom.events.KeyboardEvent
import org.w3c.dom.events.MouseEvent
import org.w3c.dom.events.WheelEvent
import org.w3c.files.Blob
import kotlin.js.Promise
import kotlin.math.*
import kotlin.properties.Delegates.observable
import kotlin.random.Random

/**
 * Top-down 2D view of the simulation environment.
 *
 * The grid is rendered as pixels: one pixel per cell in an offscreen buffer
 * (agents + field overlay), scaled to the display canvas with nearest-neighbour
 * interpolation, then the grid and the tool preview are drawn on top. There is
 * no 3D scene, no lighting and no perspective: an agent always has exactly the
 * colour of its grain.
 *
 * Interactions are the same as before (pen, line, square, spray, eraser, move
 * to pan, zoom with the wheel, fullscreen, thumbnails), plus a rectangle
 * selection (shift + drag) that can be copied, cut and pasted with the edit
 * toolbar or Ctrl+C / Ctrl+X / Ctrl+V.
 */
class SimulatorViewController(
    simulator: Simulator, val page: BulmaPage, readOnly: Boolean = true,
    initialSelectedGrain: Grain? = null,
    val onPointerMove: (x: Int, y: Int) -> Unit = { _, _ -> },
    var onUpdate: (ended: Boolean, new: Simulator, SimulatorViewController) -> Unit = { _, _, _ -> }
) : NoContextController<Simulator, BulmaElement>() {

    // simulation content edition
    private var selectedTool: EditTool = EditTool.Pen
    private var selectedSize: ToolSize = ToolSize.Fine

    var selectedGrain: Grain? by observable(initialSelectedGrain) { _, old, new ->
        if (old != new) render()
    }

    override var data: Simulator by observable(simulator) { _, old, new ->
        if (old != new) {
            onUpdate(true, new, this)
            if (old.model.grains != new.model.grains || old.model.fields != new.model.fields) {
                invalidateColors()
            }
            // the drawing area depends on the grid: a new simulation (other project, other
            // tutorial, other grid size) must recompute it, otherwise the canvas keeps the
            // size of the previous one and the cells are drawn inside a wrong area
            if (old.simulation.width != new.simulation.width || old.simulation.height != new.simulation.height) {
                zoom = 1.0
                invalidateColors()
                layout()
                // the container has not been reflowed yet when the data arrives: measure again
                // once the browser has applied the new sizes
                window.setTimeout({ layout() }, 0)
                window.setTimeout({ layout() }, 120)
            }
            render()
        }
    }

    /** Simulation playback state: only used to keep the cursor style consistent. */
    var running: Boolean = false
        set(value) {
            field = value
            display.style.cursor = when {
                value -> "default"
                selectedTool == EditTool.Move -> "grab"
                else -> "crosshair"
            }
        }

    override var readOnly: Boolean by observable(readOnly) { _, old, new ->
        if (old != new) {
            toolbar.hidden = new
            editToolbar.hidden = new
        }
    }

    // ---------------------------------------------------------------- canvases

    /** Display canvas: CSS-sized, painted from the pixel buffer. */
    val simulationCanvas: HtmlWrapper<HTMLCanvasElement> = canvas("cent-simulation").also { wrapper ->
        wrapper.root.style.setProperty("image-rendering", "pixelated")
        wrapper.root.style.cursor = "crosshair"
        wrapper.root.style.setProperty("touch-action", "none")
        wrapper.root.style.setProperty("max-width", "100%")
        wrapper.root.style.display = "block"
        wrapper.root.style.margin = "0 auto"
    }

    private val display: HTMLCanvasElement get() = simulationCanvas.root
    private val displayCtx: CanvasRenderingContext2D?
        get() = display.getContext("2d") as? CanvasRenderingContext2D

    /** One pixel per cell: agents, then the field overlay. */
    private val buffer: HTMLCanvasElement = (document.createElement("canvas") as HTMLCanvasElement)
    private val bufferCtx: CanvasRenderingContext2D?
        get() = buffer.getContext("2d") as? CanvasRenderingContext2D

    private var image: ImageData? = null

    private val newPixelView = js("function(img) { return new Uint8Array(img.data.buffer); }")

    /** Text selected in the page (empty when none): the shortcuts then belong to the browser. */
    private val textSelection = js(
        "function() { return window.getSelection ? window.getSelection().toString() : ''; }"
    )

    /** grain id -> packed RGBA (little endian: r, g, b, a in memory order). */
    private var grainColors: IntArray = IntArray(0)
    private var grainVisible: BooleanArray = BooleanArray(0)
    private var background: Int = 0xff808080.toInt()

    private var colorsValid = false

    private fun invalidateColors() {
        colorsValid = false
    }

    private fun rgbOf(name: String): Triple<Int, Int, Int>? = colorNames[name]

    private fun packed(name: String, default: Triple<Int, Int, Int>): Int {
        val (r, g, b) = rgbOf(name) ?: default
        return (0xff shl 24) or (b shl 16) or (g shl 8) or r
    }

    private fun rebuildColors() {
        val model = data.model
        val maxId = model.grains.maxOfOrNull { it.id } ?: -1
        val colors = IntArray(maxId + 1) { -1 }
        model.grains.forEach { grain ->
            if (grain.id in colors.indices) colors[grain.id] = packed(grain.color, Triple(0, 128, 0))
        }
        grainColors = colors
        val hidden = page.appContext.hiddenGrainIds
        grainVisible = BooleanArray(colors.size) { index ->
            model.grains.any { it.id == index && !it.invisible && index !in hidden }
        }
        background = packed(data.simulation.settings.backgroundColor ?: "Grey", Triple(128, 128, 128))
        colorsValid = true
    }

    // ------------------------------------------------------------------ layout

    /** Zoom applied inside the view: 1 = whole grid visible, above = magnified then panned. */
    private var zoom = 1.0

    /** Pixels per cell of the fitted view (zoom 1): depends on the available box only. */
    private var fitCell = 1.0

    /** Pixels per cell actually painted (fitCell * zoom). */
    private var cellScale = 1.0

    /** Offset of the grid inside the canvas, in CSS pixels (0 or negative when panned). */
    private var offsetX = 0.0
    private var offsetY = 0.0

    private fun availableSize(): Pair<Int, Int> {
        val parent = container.root.parentElement as? HTMLElement
        val width = (parent?.offsetWidth ?: (window.innerWidth - 40)).coerceAtLeast(200) - 12
        val height = ((0.80 * window.innerHeight).toInt() - 120).coerceAtLeast(200)
        return width to height
    }

    fun resize() = layout()

    fun resizeToFullscreen() = layout(window.innerWidth - 40, window.innerHeight - 140)

    /** Refits the grid to the window (the former "reset camera"). */
    fun resetCamera() {
        zoom = 1.0
        offsetX = 0.0
        offsetY = 0.0
        layout()
    }

    private var cssWidth = 0.0
    private var cssHeight = 0.0

    private fun layout(
        availableWidth: Int = availableSize().first,
        availableHeight: Int = availableSize().second
    ) {
        val simulation = data.simulation
        val cellsW = simulation.width.coerceAtLeast(1)
        val cellsH = simulation.height.coerceAtLeast(1)
        // the canvas keeps the fitted size: zoom magnifies inside it, so a square grid
        // stays square and never grows beyond the available box
        fitCell = max(0.2, min(availableWidth.toDouble() / cellsW, availableHeight.toDouble() / cellsH))
        cellScale = fitCell * zoom
        cssWidth = max(1.0, cellsW * fitCell)
        cssHeight = max(1.0, cellsH * fitCell)

        display.style.width = "${cssWidth.roundToInt()}px"
        display.style.height = "${cssHeight.roundToInt()}px"

        // backing store in device pixels: the canvas is then displayed 1:1, no resampling
        val ratio = window.devicePixelRatio.takeIf { it > 0.0 } ?: 1.0
        display.width = max(1, (cssWidth * ratio).roundToInt())
        display.height = max(1, (cssHeight * ratio).roundToInt())

        if (buffer.width != cellsW || buffer.height != cellsH) {
            buffer.width = cellsW
            buffer.height = cellsH
            image = null
        }
        clampOffset()
        render()
    }

    /** Keeps the magnified grid covering the canvas: no blank band while zoomed in. */
    private fun clampOffset() {
        val simulation = data.simulation
        if (zoom <= 1.0) {
            offsetX = 0.0
            offsetY = 0.0
            return
        }
        // how far the magnified grid overflows the canvas: the offset can slide in [-overflow, 0]
        val overflowX = max(0.0, simulation.width * cellScale - cssWidth)
        val overflowY = max(0.0, simulation.height * cellScale - cssHeight)
        offsetX = offsetX.coerceIn(-overflowX, 0.0)
        offsetY = offsetY.coerceIn(-overflowY, 0.0)
    }

    // ----------------------------------------------------------------- drawing

    /** Builds the pixel buffer: background, agents, then the visible fields. */
    private fun renderBuffer() {
        if (!colorsValid) rebuildColors()

        val simulation = data.simulation
        val cellsW = simulation.width
        val cellsH = simulation.height
        val ctx = bufferCtx ?: return
        var img = image
        if (img == null || img.width != cellsW || img.height != cellsH) {
            img = ctx.createImageData(cellsW.toDouble(), cellsH.toDouble())
            image = img
        }

        // byte view over the ImageData buffer: typed-array writes are far cheaper than per-pixel calls
        val pixels = newPixelView(img) as Uint8Array
        val agents = data.currentAgents
        val count = min(cellsW * cellsH, agents.size)

        // agents (empty cells take the background colour)
        for (i in 0 until count) {
            val id = agents[i]
            val visible = id >= 0 && id < grainColors.size && grainVisible[id]
            val color = if (visible) grainColors[id] else background
            val p = i * 4
            pixels[p] = (color and 0xff).toByte()
            pixels[p + 1] = ((color ushr 8) and 0xff).toByte()
            pixels[p + 2] = ((color ushr 16) and 0xff).toByte()
            pixels[p + 3] = 0xff.toByte()
        }

        ctx.putImageData(img, 0.0, 0.0)
    }

    /** One canvas per field, at half resolution: fields are smooth, so the upscale is free. */
    private val fieldLayers = HashMap<Int, HTMLCanvasElement>()

    /**
     * Builds the layer of one field: its colour, with the alpha of each value (opacity
     * included). Written at half resolution, then upscaled by [render] with smoothing, so the
     * per-pixel CPU work is divided by four and the blend is done by the compositor.
     */
    private fun fieldLayer(field: com.centyllion.model.Field): HTMLCanvasElement? {
        val values = data.field(field.id)
        if (values.isEmpty()) return null
        val (fr, fg, fb) = rgbOf(field.color) ?: return null
        val simulation = data.simulation
        val layerWidth = (simulation.width + 1) / 2
        val layerHeight = (simulation.height + 1) / 2
        var canvas = fieldLayers[field.id]
        if (canvas == null || canvas.width != layerWidth || canvas.height != layerHeight) {
            canvas = (document.createElement("canvas") as HTMLCanvasElement).apply {
                width = layerWidth
                height = layerHeight
            }
            fieldLayers[field.id] = canvas
        }
        val layerCtx = canvas.getContext("2d") as CanvasRenderingContext2D
        val img = layerCtx.createImageData(layerWidth.toDouble(), layerHeight.toDouble())
        val pixels = newPixelView(img) as Uint8Array
        val lut = opacityLut(field)
        for (y in 0 until layerHeight) {
            val sourceY = min(y * 2, simulation.height - 1)
            val row = sourceY * simulation.width
            for (x in 0 until layerWidth) {
                val sourceX = min(x * 2, simulation.width - 1)
                val alpha = lut[fieldAlpha(values[row + sourceX])]
                if (alpha <= 0) continue
                val p = (y * layerWidth + x) * 4
                pixels[p] = fr.toByte()
                pixels[p + 1] = fg.toByte()
                pixels[p + 2] = fb.toByte()
                pixels[p + 3] = alpha.toByte()
            }
        }
        layerCtx.putImageData(img, 0.0, 0.0)
        return canvas
    }

    /** Base alpha of a field value, before the field opacity. */
    private val opacityLuts = HashMap<Int, Pair<Int, IntArray>>()

    /**
     * Alpha table of a field: the 256 base alphas scaled by its opacity, so the per-pixel
     * cost stays a single table read. Rebuilt only when the opacity changes.
     */
    private fun opacityLut(field: com.centyllion.model.Field): IntArray {
        val cached = opacityLuts[field.id]
        if (cached != null && cached.first == field.opacity) return cached.second
        val opacity = field.opacity.coerceIn(0, 100)
        val lut = IntArray(256) { (it * opacity) / 100 }
        opacityLuts[field.id] = opacity to lut
        return lut
    }

    /** Opacity of a field value (same mapping as the former 3D alpha texture). */
    private fun fieldAlpha(level: Float): Int = when {
        level <= minFieldLevel -> 0
        level >= 1f -> 200
        level >= 0.1f -> (111 * level + 89f).toInt()
        else -> (-100f / log10(level)).toInt()
    }.coerceIn(0, 255)

    fun render() {
        val ctx = displayCtx ?: return
        if (display.width == 0 || display.height == 0 || cssWidth <= 0.0) return
        renderBuffer()

        val ratio = display.width / cssWidth
        ctx.setTransform(ratio, 0.0, 0.0, ratio, 0.0, 0.0)
        ctx.clearRect(0.0, 0.0, cssWidth, cssHeight)
        ctx.imageSmoothingEnabled = false
        val simulation = data.simulation
        ctx.drawImage(buffer, offsetX, offsetY, simulation.width * cellScale, simulation.height * cellScale)

        // fields: one layer each, blended by the compositor (no per-pixel CPU blend)
        ctx.imageSmoothingEnabled = true
        data.model.fields.forEach { field ->
            if (field.invisible || page.appContext.hiddenFieldIds.contains(field.id)) return@forEach
            if (field.opacity <= 0) return@forEach
            val layer = fieldLayer(field) ?: return@forEach
            ctx.drawImage(
                layer, offsetX, offsetY,
                simulation.width * cellScale, simulation.height * cellScale
            )
        }
        ctx.imageSmoothingEnabled = false

        drawGrid(ctx)
        drawSelection(ctx)
        drawPastePreview(ctx)
        drawPointer(ctx)
        display.classList.toggle("is-danger", data.step > 0)
    }

    private var gridPath: Path2D? = null
    private var gridKey: String = ""

    private fun drawGrid(ctx: CanvasRenderingContext2D) {
        val simulation = data.simulation
        if (!simulation.settings.showGrid) return
        val cellPx = cellScale
        if (cellPx < 4.0) return

        // only the cells currently visible are stroked: a zoomed grid can be far larger
        val left = max(0.0, offsetX)
        val top = max(0.0, offsetY)
        val right = min(cssWidth, offsetX + simulation.width * cellPx)
        val bottom = min(cssHeight, offsetY + simulation.height * cellPx)
        val firstX = max(1, ceil((left - offsetX) / cellPx).toInt())
        val lastX = min(simulation.width - 1, floor((right - offsetX) / cellPx).toInt())
        val firstY = max(1, ceil((top - offsetY) / cellPx).toInt())
        val lastY = min(simulation.height - 1, floor((bottom - offsetY) / cellPx).toInt())

        // the path only changes when the view changes (zoom, pan, size), not every frame
        val key = "$firstX:$lastX:$firstY:$lastY:$cellPx:$offsetX:$offsetY:$left:$top:$right:$bottom"
        if (key != gridKey || gridPath == null) {
            val path = Path2D()
            for (x in firstX..lastX) {
                val px = offsetX + x * cellPx
                path.moveTo(px, top)
                path.lineTo(px, bottom)
            }
            for (y in firstY..lastY) {
                val py = offsetY + y * cellPx
                path.moveTo(left, py)
                path.lineTo(right, py)
            }
            gridPath = path
            gridKey = key
        }

        ctx.save()
        ctx.strokeStyle = "rgba(255, 255, 255, 0.22)"
        ctx.lineWidth = 1.0
        ctx.stroke(gridPath!!)
        ctx.restore()
    }

    /** Brush preview under the mouse: the cells that a click would fill. */
    private fun drawPointer(ctx: CanvasRenderingContext2D) {
        if (hoverX < 0 || hoverY < 0) return
        val simulation = data.simulation
        val cellPx = cellScale
        val size = selectedTool.actualSize(selectedSize)
        val half = (size / 2).coerceAtLeast(1)

        ctx.save()
        ctx.globalAlpha = when (selectedTool) {
            EditTool.Eraser -> 0.35
            EditTool.Spray -> 0.4
            else -> 0.55
        }
        ctx.fillStyle = when (selectedTool) {
            EditTool.Eraser -> "rgba(0, 0, 0, 0.9)"
            else -> selectedGrain?.color?.let { colorHex(it) } ?: "#338033"
        }
        if (half <= 1) {
            ctx.fillRect(offsetX + hoverX * cellPx, offsetY + hoverY * cellPx, cellPx, cellPx)
        } else {
            ctx.beginPath()
            ctx.ellipse(
                offsetX + (hoverX + 0.5) * cellPx, offsetY + (hoverY + 0.5) * cellPx,
                half * cellPx, half * cellPx, 0.0, 0.0, 2 * PI
            )
            ctx.fill()
        }
        ctx.restore()
    }

    private fun colorHex(name: String): String {
        val (r, g, b) = rgbOf(name) ?: Triple(51, 128, 51)
        return "rgb($r, $g, $b)"
    }

    // -------------------------------------------------------------- interaction

    private var hoverX = -1
    private var hoverY = -1
    private var simulationX = -1
    private var simulationY = -1
    private var simulationSourceX = -1
    private var simulationSourceY = -1
    private var drawStep = -1
    private var panning = false
    private var panX = 0.0
    private var panY = 0.0

    /** Cell under a mouse event, or null outside the grid. */
    private fun cellAt(evt: MouseEvent): Pair<Int, Int>? {
        val rect = display.getBoundingClientRect()
        if (rect.width <= 0.0 || rect.height <= 0.0) return null
        val x = floor((evt.clientX - rect.left - offsetX) / cellScale).toInt()
        val y = floor((evt.clientY - rect.top - offsetY) / cellScale).toInt()
        return if (data.simulation.positionInside(x, y)) x to y else null
    }

    private fun panBy(evt: MouseEvent) {
        offsetX += evt.clientX - panX
        offsetY += evt.clientY - panY
        panX = evt.clientX.toDouble()
        panY = evt.clientY.toDouble()
        clampOffset()
        render()
    }

    private fun installListeners() {
        display.addEventListener("pointerdown", { event ->
            val evt = event as MouseEvent
            evt.preventDefault()
            // Shift + left button: rectangle selection, never a drawing action
            if (evt.shiftKey && evt.button.toInt() == 0) {
                beginSelection(evt)
            } else if (selectedTool == EditTool.Move) {
                panning = true
                panX = evt.clientX.toDouble()
                panY = evt.clientY.toDouble()
                display.style.cursor = "grabbing"
            } else {
                cellAt(evt)?.let { (x, y) ->
                    drawStep = 0
                    simulationX = x
                    simulationY = y
                    simulationSourceX = x
                    simulationSourceY = y
                    drawOnSimulation()
                    drawStep += 1
                }
            }
        })

        display.addEventListener("pointermove", { event ->
            val evt = event as MouseEvent
            if (selecting) {
                dragSelection(evt)
                return@addEventListener
            }
            if (panning) {
                hideTooltip()
                panBy(evt)
                return@addEventListener
            }
            val cell = cellAt(evt)
            updateTooltip(evt, cell)
            if (cell == null) {
                hoverX = -1
                hoverY = -1
                drawStep = -1
                onPointerMove(-1, -1)
                render()
            } else {
                hoverX = cell.first
                hoverY = cell.second
                onPointerMove(hoverX, hoverY)
                if (selectedTool != EditTool.Move && drawStep > 0) {
                    simulationX = hoverX
                    simulationY = hoverY
                    drawOnSimulation()
                    drawStep += 1
                } else {
                    render()
                }
            }
        })

        display.addEventListener("pointerup", { event ->
            val evt = event as MouseEvent
            if (selecting) {
                finishSelection(evt)
            } else if (panning) {
                panning = false
                display.style.cursor = "crosshair"
            } else if (drawStep >= 0) {
                // line and square tools are applied on release
                drawStep = -1
                drawOnSimulation()
                simulationSourceX = -1
                simulationSourceY = -1
                simulationX = -1
                simulationY = -1
                render()
            }
            evt.preventDefault()
        })

        display.addEventListener("pointerleave", {
            hoverX = -1
            hoverY = -1
            drawStep = -1
            hideTooltip()
            render()
        })

        display.addEventListener("wheel", { event ->
            val evt = event as WheelEvent
            evt.preventDefault()
            val factor = if (evt.deltaY < 0) 1.2 else 1.0 / 1.2
            val newZoom = (zoom * factor).coerceIn(1.0, 24.0)
            if (newZoom == zoom) return@addEventListener
            // the cell under the pointer stays under the pointer
            val rect = display.getBoundingClientRect()
            val mouseX = evt.clientX - rect.left
            val mouseY = evt.clientY - rect.top
            val anchorX = (mouseX - offsetX) / cellScale
            val anchorY = (mouseY - offsetY) / cellScale
            zoom = newZoom
            cellScale = fitCell * zoom
            offsetX = mouseX - anchorX * cellScale
            offsetY = mouseY - anchorY * cellScale
            clampOffset()
            render()
        })

        // right click inside the selection drops it; the canvas has no native menu anyway
        display.addEventListener("contextmenu", { event ->
            val evt = event as MouseEvent
            val rect = selection
            if (rect != null) {
                cellAt(evt)?.let { (x, y) -> if (rect.contains(x, y)) clearSelection() }
            }
            evt.preventDefault()
        })
    }

    fun writeGrain(i: Int, j: Int, id: Int) = data.simulation.toIndex(i, j).let { index ->
        if (data.idAtIndex(index) < 0) data.setIdAtIndex(index, id)
    }

    fun circle(x: Int, y: Int, block: (i: Int, j: Int) -> Unit) {
        val size = selectedTool.actualSize(selectedSize)
        val halfSize = (size / 2).coerceAtLeast(1)
        if (halfSize == 1) {
            block(x, y)
        } else {
            for (i in x - halfSize until x + halfSize) {
                for (j in y - halfSize until y + halfSize) {
                    val inCircle = (x - i) * (x - i) + (y - j - 1) * (y - j) + 1 < halfSize * halfSize
                    val inSimulation = data.simulation.positionInside(i, j)
                    if (inCircle && inSimulation) block(i, j)
                }
            }
        }
    }

    fun line(sourceX: Int, sourceY: Int, x: Int, y: Int, block: (i: Int, j: Int) -> Unit) {
        val dx = x - sourceX
        val dy = y - sourceY
        if (abs(dx) > abs(dy)) {
            if (sourceX < x) {
                for (i in sourceX until x) block(i, sourceY + dy * (i - sourceX) / dx)
            } else {
                for (i in x until sourceX) block(i, y + dy * (i - x) / dx)
            }
        } else {
            if (sourceY < y) {
                for (j in sourceY until y) block(sourceX + dx * (j - sourceY) / dy, j)
            } else {
                for (j in y until sourceY) block(x + dx * (j - y) / dy, j)
            }
        }
    }

    fun square(sourceX: Int, sourceY: Int, x: Int, y: Int, block: (i: Int, j: Int) -> Unit) {
        if (selectedSize == ToolSize.Fine) {
            for (i in min(sourceX, x)..max(sourceX, x)) {
                block(i, sourceY)
                block(i, y)
            }
            for (j in min(sourceY, y)..max(sourceY, y)) {
                block(sourceX, j)
                block(x, j)
            }
        } else {
            for (i in min(sourceX, x)..max(sourceX, x)) {
                for (j in min(sourceY, y)..max(sourceY, y)) {
                    block(i, j)
                }
            }
        }
    }

    fun drawOnSimulation() {
        when (selectedTool) {
            EditTool.Pen -> if (drawStep >= 0) {
                selectedGrain?.id?.let { id -> circle(simulationX, simulationY) { i, j -> writeGrain(i, j, id) } }
            }

            EditTool.Line -> selectedGrain?.id?.let { id ->
                if (drawStep == -1) {
                    line(simulationSourceX, simulationSourceY, simulationX, simulationY) { i, j -> writeGrain(i, j, id) }
                }
            }

            EditTool.Square -> selectedGrain?.id?.let { id ->
                if (drawStep == -1) {
                    square(simulationSourceX, simulationSourceY, simulationX, simulationY) { i, j -> writeGrain(i, j, id) }
                }
            }

            EditTool.Spray -> if (drawStep >= 0) {
                selectedGrain?.id?.let { id ->
                    val sprayDensity = 0.005
                    circle(simulationX, simulationY) { i, j ->
                        if (Random.nextDouble() < sprayDensity) writeGrain(i, j, id)
                    }
                }
            }

            EditTool.Eraser -> circle(simulationX, simulationY) { i, j ->
                data.resetIdAtIndex(data.simulation.toIndex(i, j))
            }

            EditTool.Move -> {}
        }

        onUpdate(drawStep == -1, data, this)
        render()
    }

    // ---------------------------------------------------- selection & clipboard

    /**
     * Rectangle in cells. A selection only remembers its coordinates: the agents are read
     * again at copy time, so it stays valid while the simulation runs.
     */
    private data class CellRect(val x: Int, val y: Int, val width: Int, val height: Int) {
        fun contains(column: Int, row: Int) =
            column >= x && column < x + width && row >= y && row < y + height
    }

    /** Frozen selection, null when nothing is selected. Survives the simulation steps. */
    private var selection: CellRect? = null

    /** True while Shift + left button drags a new rectangle. */
    private var selecting = false
    private var selectAnchorX = 0
    private var selectAnchorY = 0
    private var selectCurrentX = 0
    private var selectCurrentY = 0

    /** Last extraction: a visible grain id per cell, or -1 for an empty (or masked) cell. */
    private var clipboard: IntArray? = null
    private var clipboardWidth = 0
    private var clipboardHeight = 0

    /** True when the "Écraser" radio is active: a paste then replaces the visible agents. */
    var overwriteOnPaste: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            refreshEditButtons()
        }

    private fun ensureColors() {
        if (!colorsValid) rebuildColors()
    }

    /** Id of the agent at [index] when it is visible in the view, -1 otherwise. */
    private fun visibleIdAt(index: Int): Int {
        ensureColors()
        val agents = data.currentAgents
        if (index < 0 || index >= agents.size) return -1
        val id = agents[index]
        return if (id >= 0 && id < grainVisible.size && grainVisible[id]) id else -1
    }

    /** Rectangle spanned by two cells, both included. */
    private fun rectOf(ax: Int, ay: Int, bx: Int, by: Int): CellRect {
        val left = min(ax, bx)
        val top = min(ay, by)
        return CellRect(left, top, abs(ax - bx) + 1, abs(ay - by) + 1)
    }

    /** Cell under a mouse event, clamped to the grid (a drag can leave the canvas). */
    private fun clampedCellAt(evt: MouseEvent): Pair<Int, Int>? {
        val rect = display.getBoundingClientRect()
        if (rect.width <= 0.0 || rect.height <= 0.0 || cellScale <= 0.0) return null
        val simulation = data.simulation
        // same mapping as cellAt: pixel -> cell, rounded down, offset and scale included
        val column = floor((evt.clientX - rect.left - offsetX) / cellScale).toInt()
        val row = floor((evt.clientY - rect.top - offsetY) / cellScale).toInt()
        return column.coerceIn(0, simulation.width - 1) to row.coerceIn(0, simulation.height - 1)
    }

    private fun beginSelection(evt: MouseEvent) {
        val cell = clampedCellAt(evt) ?: return
        selecting = true
        selectAnchorX = cell.first
        selectAnchorY = cell.second
        selectCurrentX = cell.first
        selectCurrentY = cell.second
        hoverX = cell.first
        hoverY = cell.second
        hideTooltip()
        render()
    }

    private fun dragSelection(evt: MouseEvent) {
        val cell = clampedCellAt(evt) ?: return
        selectCurrentX = cell.first
        selectCurrentY = cell.second
        hoverX = cell.first
        hoverY = cell.second
        render()
    }

    /** Freezes the dragged rectangle: only an explicit action can change the selection. */
    private fun finishSelection(evt: MouseEvent?) {
        if (!selecting) return
        selecting = false
        if (evt != null) {
            clampedCellAt(evt)?.let { cell ->
                selectCurrentX = cell.first
                selectCurrentY = cell.second
            }
        }
        selection = rectOf(selectAnchorX, selectAnchorY, selectCurrentX, selectCurrentY)
        refreshEditButtons()
        render()
    }

    /** Drops the selection (the "Désélectionner" button and the right click inside it). */
    fun clearSelection() {
        if (selection == null) return
        selection = null
        refreshEditButtons()
        render()
    }

    /** Dark veil and frame over the selected cells, or over the rectangle being dragged. */
    private fun drawSelection(ctx: CanvasRenderingContext2D) {
        val rect = if (selecting) {
            rectOf(selectAnchorX, selectAnchorY, selectCurrentX, selectCurrentY)
        } else {
            selection ?: return
        }
        val left = offsetX + rect.x * cellScale
        val top = offsetY + rect.y * cellScale
        val width = rect.width * cellScale
        val height = rect.height * cellScale
        ctx.save()
        ctx.fillStyle = "rgba(0, 0, 0, 0.30)"
        ctx.fillRect(left, top, width, height)
        // dark then light outline: the frame stays readable on any grain colour
        ctx.strokeStyle = "rgba(0, 0, 0, 0.85)"
        ctx.lineWidth = 3.0
        ctx.strokeRect(left, top, width, height)
        ctx.strokeStyle = "rgba(255, 255, 255, 0.95)"
        ctx.lineWidth = 1.0
        ctx.strokeRect(left + 1.5, top + 1.5, max(0.0, width - 3.0), max(0.0, height - 3.0))
        ctx.restore()
    }

    /**
     * Frame of the paste under the mouse: same top-left cell, same size in cells and same
     * pixel -> cell mapping as [pasteSelection], so it shows exactly what a paste would write.
     * Its style follows the paste mode, and it is not the selection frame (no dark veil).
     * It is drawn by [render], so it follows the mouse, never flickers and adds no listener.
     */
    private fun drawPastePreview(ctx: CanvasRenderingContext2D) {
        if (clipboard == null || readOnly || selecting) return
        // hoverX/hoverY are -1 as soon as the pointer leaves the canvas: the frame disappears
        if (hoverX < 0 || hoverY < 0) return
        if (!data.simulation.positionInside(hoverX, hoverY)) return
        val left = offsetX + hoverX * cellScale
        val top = offsetY + hoverY * cellScale
        val width = clipboardWidth * cellScale
        val height = clipboardHeight * cellScale
        ctx.save()
        if (overwriteOnPaste) {
            // "Écraser": warm frame and veil, the pasted content will replace what is there
            ctx.fillStyle = "rgba(255, 160, 0, 0.22)"
            ctx.fillRect(left, top, width, height)
            ctx.strokeStyle = "rgba(255, 160, 0, 0.95)"
        } else {
            // "Conserver": thin cool frame, only the empty target cells will be written
            ctx.strokeStyle = "rgba(120, 220, 255, 0.95)"
        }
        ctx.lineWidth = 2.0
        ctx.strokeRect(left + 1.0, top + 1.0, max(0.0, width - 2.0), max(0.0, height - 2.0))
        ctx.restore()
    }

    /** Copies the selection: visible agent ids, -1 for empty and for masked agents. */
    fun copySelection(): Boolean {
        val rect = selection ?: return false
        ensureColors()
        val simulation = data.simulation
        val cells = IntArray(rect.width * rect.height) { -1 }
        for (row in 0 until rect.height) {
            val y = rect.y + row
            if (y < 0 || y >= simulation.height) continue
            for (column in 0 until rect.width) {
                val x = rect.x + column
                if (x < 0 || x >= simulation.width) continue
                cells[row * rect.width + column] = visibleIdAt(simulation.toIndex(x, y))
            }
        }
        clipboard = cells
        clipboardWidth = rect.width
        clipboardHeight = rect.height
        refreshEditButtons()
        // the paste preview must show up without waiting for the next mouse move
        render()
        return true
    }

    /** Copies then clears the visible agents of the selection; masked agents stay in place. */
    fun cutSelection(): Boolean {
        if (readOnly) return false
        val rect = selection ?: return false
        if (!copySelection()) return false
        val simulation = data.simulation
        var changed = false
        for (row in 0 until rect.height) {
            val y = rect.y + row
            if (y < 0 || y >= simulation.height) continue
            for (column in 0 until rect.width) {
                val x = rect.x + column
                if (x < 0 || x >= simulation.width) continue
                val index = simulation.toIndex(x, y)
                if (visibleIdAt(index) >= 0) {
                    data.resetIdAtIndex(index)
                    changed = true
                }
            }
        }
        if (changed) updateAfterEdit()
        return true
    }

    /** Top-left cell of a paste: under the mouse, or the selection corner (button bar). */
    private fun pasteOrigin(): Pair<Int, Int>? {
        val simulation = data.simulation
        if (simulation.positionInside(hoverX, hoverY)) return hoverX to hoverY
        val rect = selection ?: return null
        return rect.x to rect.y
    }

    /**
     * Pastes the extraction with its top-left corner under the mouse, ignoring the cells
     * outside the grid. A masked agent in place is never replaced: it is invisible for the
     * tool, so its cell counts as empty.
     */
    fun pasteSelection(): Boolean {
        if (readOnly) return false
        val cells = clipboard ?: return false
        val origin = pasteOrigin() ?: return false
        val simulation = data.simulation
        var changed = false
        for (row in 0 until clipboardHeight) {
            val y = origin.second + row
            if (y < 0 || y >= simulation.height) continue
            for (column in 0 until clipboardWidth) {
                val x = origin.first + column
                if (x < 0 || x >= simulation.width) continue
                val index = simulation.toIndex(x, y)
                val incoming = cells[row * clipboardWidth + column]
                val inPlace = data.idAtIndex(index)
                // masked agent in place: nothing to see, nothing to overwrite
                if (inPlace >= 0 && visibleIdAt(index) < 0) continue
                if (incoming >= 0) {
                    // a visible agent in place wins unless "Écraser" is active
                    if (inPlace >= 0 && !overwriteOnPaste) continue
                    if (inPlace == incoming) continue
                    data.setIdAtIndex(index, incoming)
                    changed = true
                } else if (inPlace >= 0 && overwriteOnPaste) {
                    // an empty pasted cell erases the visible agent in place
                    data.resetIdAtIndex(index)
                    changed = true
                }
            }
        }
        if (changed) updateAfterEdit()
        return true
    }

    /** Same path as the drawing tools: the model records the change (undo/redo) and repaints. */
    private fun updateAfterEdit() {
        onUpdate(true, data, this)
        render()
    }

    /** True for the text fields and the editors: the shortcuts must not steal their keys. */
    private fun isEditableTarget(target: EventTarget?): Boolean {
        var node = target as? Node ?: return false
        while (true) {
            val element = node as? HTMLElement ?: return false
            when (element.tagName.lowercase()) {
                "input", "textarea", "select" -> return true
            }
            if (element.isContentEditable) return true
            val editable = element.getAttribute("contenteditable")
            if (editable != null && editable != "false") return true
            node = element.parentNode ?: return false
        }
    }

    /** True when text is selected: then Ctrl+C / Ctrl+X belong to the browser. */
    private fun hasTextSelection(): Boolean {
        val text = textSelection() as? String ?: return false
        return text.isNotEmpty()
    }

    /** Ctrl+C / Ctrl+X / Ctrl+V (Cmd on macOS) on the simulation clipboard. */
    private fun handleShortcut(event: Event) {
        if (readOnly) return
        val evt = event as? KeyboardEvent ?: return
        if (!evt.ctrlKey && !evt.metaKey) return
        if (evt.altKey) return
        if (isEditableTarget(evt.target)) return
        when (evt.key.lowercase()) {
            "c" -> if (!hasTextSelection() && copySelection()) evt.preventDefault()
            "x" -> if (!hasTextSelection() && cutSelection()) evt.preventDefault()
            "v" -> if (pasteSelection()) evt.preventDefault()
        }
    }

    // ------------------------------------------------------------------ toolbar

    val tool get() = selectedTool
    val size get() = selectedSize

    val sizeDropdown = Dropdown(text = page.i18n(selectedSize.name), rounded = true).apply {
        items = ToolSize.entries.map { toolSize ->
            DropdownSimpleItem(page.i18n(toolSize.name)) {
                this.text = page.i18n(toolSize.name)
                selectedSize = toolSize
                render()
                this.toggleDropdown()
            }
        }
    }

    val toolButtons = EditTool.entries.map { tool ->
        iconButton(
            Icon(tool.icon), ElementColor.Primary,
            rounded = true, outlined = tool.ordinal == selectedTool.ordinal
        ) { selectTool(tool) }
    }

    val toolsField = Field(addons = true).apply { body = toolButtons.map { Control(it) } }

    val randomAddCountInput: Input = Input("20", rounded = true, columns = 3) { _, v ->
        val isNotPositiveInt = v.toIntOrNull()?.let { it <= 0 } ?: true
        randomAddButton.disabled = isNotPositiveInt
        randomAddButton.color = if (isNotPositiveInt) ElementColor.Danger else ElementColor.Primary
    }

    val randomAddButton = iconButton(Icon("spray-can"), ElementColor.Primary, rounded = true) {
        val grainId = selectedGrain?.id
        val count = randomAddCountInput.value.toIntOrNull()
        if (count != null && count > 0 && grainId != null) {
            repeat(count) {
                // try at most 5 times to find a free place
                for (i in 0 until 5) {
                    val index = Random.nextInt(data.simulation.dataSize)
                    if (data.idAtIndex(index) < 0) {
                        data.setIdAtIndex(index, grainId)
                        break
                    }
                }
            }
            onUpdate(true, data, this)
            render()
        }
    }

    val randomAddField = Field(
        Control(randomAddCountInput),
        Control(randomAddButton),
        addons = true
    )

    val clearAllButton = iconButton(Icon("trash"), ElementColor.Danger, true) {
        (0 until data.simulation.dataSize).forEach { data.resetIdAtIndex(it) }
        onUpdate(true, data, this)
        render()
    }

    /** "Value info" mode: hovering a cell shows the field concentrations at that point.
     *  On by default; the switch lives in the Settings tab. */
    private var valueInfo = true

    private val tooltip: Div = Div(classes = "cent-value-info").also { div ->
        with(div.root.style) {
            position = "absolute"
            setProperty("pointer-events", "none")
            background = "rgba(20, 20, 20, 0.82)"
            color = "#ffffff"
            padding = "3px 7px"
            borderRadius = "4px"
            fontSize = "12px"
            lineHeight = "1.35"
            whiteSpace = "pre"
            zIndex = "40"
            display = "none"
        }
    }

    val toolbar = Level(
        center = listOf(toolsField, sizeDropdown, randomAddField, clearAllButton)
    )

    // ------------------------------------------------------------- edit toolbar

    val copyButton = Button(
        page.i18n("Copy"), Icon("copy"), color = ElementColor.Primary, rounded = true
    ) { copySelection() }

    val cutButton = Button(
        page.i18n("Cut"), Icon("cut"), color = ElementColor.Primary, rounded = true
    ) { cutSelection() }

    val pasteButton = Button(
        page.i18n("Paste"), Icon("paste"), color = ElementColor.Primary, rounded = true
    ) { pasteSelection() }

    val deselectButton = Button(
        page.i18n("Deselect"), Icon("times"), color = ElementColor.Warning, rounded = true
    ) { clearSelection() }

    private fun radioInput(mode: String, checked: Boolean): HTMLInputElement =
        (document.createElement("input") as HTMLInputElement).apply {
            type = "radio"
            name = "centy-paste-mode"
            value = mode
            this.checked = checked
        }

    /** Radios of the paste mode, built by hand like the grid size ones (Bulma's API is not usable here). */
    private val overwriteRadioInput = radioInput("overwrite", false)
    private val keepRadioInput = radioInput("keep", true)

    private fun radioLabel(input: HTMLInputElement, text: String) = HtmlWrapper(
        (document.createElement("label") as HTMLLabelElement).apply {
            classList.add("radio")
            appendChild(input)
            appendChild(document.createTextNode(" " + text))
        }
    )

    private val overwriteRadio = radioLabel(overwriteRadioInput, page.i18n("Overwrite"))
    private val keepRadio = radioLabel(keepRadioInput, page.i18n("Keep"))

    private val editButtonsField = Field(addons = true).apply {
        body = listOf(Control(copyButton), Control(cutButton), Control(pasteButton), Control(deselectButton))
    }

    private val pasteModeField = Div(overwriteRadio, keepRadio).apply {
        root.classList.add("control")
        root.style.paddingTop = "6px"
    }

    /** Bar under the drawing tools: copy, cut, paste, deselect and the paste mode. */
    val editToolbar = Level(center = listOf(editButtonsField, pasteModeField))

    private fun refreshEditButtons() {
        val selected = selection != null
        copyButton.disabled = !selected
        cutButton.disabled = !selected || readOnly
        pasteButton.disabled = clipboard == null || readOnly
        deselectButton.disabled = !selected
    }

    /** Cell of the grid under the mouse, with the field concentrations when value info is on. */
    private fun updateTooltip(evt: MouseEvent, cell: Pair<Int, Int>?) {
        if (!valueInfo || cell == null) {
            hideTooltip()
            return
        }
        val (x, y) = cell
        val index = data.simulation.toIndex(x, y)
        val lines = mutableListOf("($x, $y)")
        val fields = data.model.fields
        if (fields.isEmpty()) lines.add(page.i18n("No field"))
        fields.forEach { field ->
            val level = data.field(field.id).getOrElse(index) { 0f }
            val name = field.name.ifBlank { "field ${field.id}" }
            lines.add("$name : ${formatLevel(level)}")
        }
        tooltip.root.textContent = lines.joinToString("\n")
        tooltip.root.style.display = "block"

        val viewportRect = viewport.root.getBoundingClientRect()
        val width = tooltip.root.offsetWidth.toDouble()
        val height = tooltip.root.offsetHeight.toDouble()
        val left = evt.clientX - viewportRect.left + 14.0
        val top = evt.clientY - viewportRect.top + 14.0
        val maxLeft = max(0.0, viewportRect.width - width - 4.0)
        val maxTop = max(0.0, viewportRect.height - height - 4.0)
        tooltip.root.style.left = "${left.coerceIn(0.0, maxLeft).roundToInt()}px"
        tooltip.root.style.top = "${top.coerceIn(0.0, maxTop).roundToInt()}px"
    }

    private fun hideTooltip() {
        tooltip.root.style.display = "none"
    }

    /** Field values are in 0..1: three decimals, scientific notation for the very small ones. */
    private fun formatLevel(value: Float): String {
        val sign = if (value < 0f) "-" else ""
        val magnitude = abs(value)
        // fields are concentrations in 0..1: below this, the value is numerically nothing
        if (value == 0f || magnitude < 1e-9f) return "0"
        return if (magnitude >= 0.001f) {
            sign + (round(magnitude * 1000f) / 1000f).toString()
        } else {
            var mantissa = magnitude
            var exponent = 0
            while (mantissa < 1f && exponent > -12) {
                mantissa *= 10f
                exponent -= 1
            }
            sign + (round(mantissa * 100f) / 100f).toString() + "e" + exponent
        }
    }

    /** Turns the value tooltip on or off (driven by the switch of the Settings tab). */
    fun setValueInfo(enabled: Boolean) {
        if (valueInfo == enabled) return
        valueInfo = enabled
        if (!enabled) hideTooltip()
        render()
    }

    /** Area holding the canvas: the grid is magnified inside it and panned with the Move tool. */
    private val viewport = Div(simulationCanvas, tooltip, classes = "has-text-centered").apply {
        root.style.setProperty("overflow", "hidden")
        root.style.setProperty("position", "relative")
    }

    override val container = Div(viewport, toolbar)

    private val resizeCallback: (Event) -> Unit = { layout() }

    /** A selection released outside the canvas must still be frozen. */
    private val selectionPointerUp: (Event) -> Unit = { if (selecting) finishSelection(null) }

    private val shortcutCallback: (Event) -> Unit = { handleShortcut(it) }

    init {
        installListeners()
        // first layout once the container is attached to the document
        window.setTimeout({ layout() }, 0)
        window.addEventListener("resize", resizeCallback)
        window.addEventListener("pointerup", selectionPointerUp)
        // document level, so the shortcuts also work right after a click on the canvas
        document.addEventListener("keydown", shortcutCallback)
        overwriteRadioInput.addEventListener("change", { overwriteOnPaste = true })
        keepRadioInput.addEventListener("change", { overwriteOnPaste = false })
        editToolbar.hidden = readOnly
        refreshEditButtons()
    }

    fun selectTool(tool: EditTool) {
        toolButtons.forEach { it.outlined = false }
        toolButtons[tool.ordinal].outlined = true
        selectedTool = tool
        display.style.cursor = if (tool == EditTool.Move) "grab" else "crosshair"
        render()
    }

    // --------------------------------------------------------------- simulation

    /** Nothing per-agent to update: the arrays are the source of truth, repaint. */
    fun oneStep(applied: Collection<ApplicableBehavior>, dead: Collection<Int>) {
        render()
    }

    /** Re-applies the visibility toggles from the side panel (hidden grain types and fields). */
    fun refreshVisibility() {
        invalidateColors()
        render()
    }

    override fun refresh() = render()

    @Suppress("UNUSED_PARAMETER")
    fun refresh(force: Boolean) = render()

    fun dispose() {
        window.removeEventListener("resize", resizeCallback)
        window.removeEventListener("pointerup", selectionPointerUp)
        document.removeEventListener("keydown", shortcutCallback)
    }

    // ------------------------------------------------------------- thumbnails

    fun screenshotURL(): String = display.toDataURL("image/webp")

    fun screenshot(width: Int, height: Int): Promise<Blob> = Promise { resolve, reject ->
        display.toBlob({ blob ->
            if (blob != null) resolve(blob) else reject(IllegalStateException("empty screenshot"))
        }, "image/webp")
    }

    fun thumbnail(): Promise<Blob> = screenshot(400, 267)
}
