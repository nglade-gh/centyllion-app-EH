package com.centyllion.client.controller.model

import bulma.*
import bulma.extension.Switch
import com.centyllion.client.controller.utils.EditableStringController
import com.centyllion.client.controller.utils.editorBox
import com.centyllion.client.page.BulmaPage
import com.centyllion.model.CaptureFormat
import com.centyllion.model.CaptureResolution
import com.centyllion.model.CsvSeparator
import com.centyllion.model.DEFAULT_SIMULATION_SEED
import com.centyllion.model.JPEG_QUALITY_STEP
import com.centyllion.model.MAX_JPEG_QUALITY
import com.centyllion.model.MAX_PIXELS_PER_CELL
import com.centyllion.model.MIN_JPEG_QUALITY
import com.centyllion.model.MIN_PIXELS_PER_CELL
import com.centyllion.model.PARALLEL_UPDATE_PLAN
import com.centyllion.model.RecordingSettings
import com.centyllion.model.SEQUENTIAL_UPDATE_PLAN
import com.centyllion.model.clampJpegQuality
import com.centyllion.model.clampPixelsPerCell
import com.centyllion.model.jpegQualityLabel
import com.centyllion.model.MAX_SIMULATION_SIZE
import com.centyllion.model.MIN_SIMULATION_SIZE
import com.centyllion.model.SIMULATION_SIZE_STEP
import com.centyllion.model.SimulationSettings
import com.centyllion.model.availableSimulationSizes
import com.centyllion.model.clampSimulationSize
import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLLabelElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import kotlin.properties.Delegates.observable

class SimulationSettingsController (
    initialData: SimulationSettings, val page: BulmaPage,
    initialUpdatePlan: String = PARALLEL_UPDATE_PLAN,
    var onUpdatePlan: (String) -> Unit = { },
    initialSeed: Long = DEFAULT_SIMULATION_SEED,
    var onUpdateSeed: (Long) -> Unit = { },
    initialValueInfo: Boolean = true,
    var onValueInfo: (Boolean) -> Unit = { },
    initialWarnings: Boolean = true,
    var onWarnings: (Boolean) -> Unit = { },
    initialSlidingWindow: Boolean = false,
    initialWindowSize: Int = 100,
    var onSlidingWindow: (Boolean, Int) -> Unit = { _, _ -> },
    initialRecording: RecordingSettings = RecordingSettings(),
    var onRecording: (RecordingSettings) -> Unit = { },
    var onUpdate: (old: SimulationSettings, new: SimulationSettings, controller: SimulationSettingsController) -> Unit = { _, _, _ -> }
) : NoContextController<SimulationSettings, Div>() {

    override var data: SimulationSettings by observable(initialData) { _, old, new ->
        if (old != new) {
            sizeController.selectedIndex = availableSimulationSizes.indexOf(new.size)
            setRadioChecked(new.isCustomSize)
            customSize = new.isCustomSize
            widthStepper.show(new.effectiveWidth)
            heightStepper.show(new.effectiveHeight)
            colorController.data = new.backgroundColor ?: "Grey"
            showGridSwitch.checked = data.showGrid
            gridTextureUrlController.data = data.gridTextureUrl ?: ""
            onUpdate(old, new, this@SimulationSettingsController)
        }
        refresh()
    }

    override var readOnly: Boolean by observable(false) { _, old, new ->
        if (old != new) {
            sizeController.disabled = new || customSize
            widthStepper.enabled(!new && customSize)
            heightStepper.enabled(!new && customSize)
            colorController.readOnly = new
            showGridSwitch.disabled = new
            gridTextureUrlController.readOnly = new
            updatePlanSelect.disabled = new
            seedInput.disabled = new
            valueInfoSwitch.disabled = new
            slidingWindowCheckbox.disabled = new
            warningsCheckbox.disabled = new
            windowSizeSelect.disabled = new
            showRecording()
        }
    }

    val updatePlanSelect = Select(
        options = listOf(Option(page.i18n("Parallel")), Option(page.i18n("Sequential"))),
        rounded = true,
        selectedIndex = if (initialUpdatePlan == SEQUENTIAL_UPDATE_PLAN) 1 else 0
    ) { _, value: List<Option> ->
        val newPlan = if (value.firstOrNull()?.value == page.i18n("Sequential")) SEQUENTIAL_UPDATE_PLAN else PARALLEL_UPDATE_PLAN
        onUpdatePlan(newPlan)
    }

    /** Seed of the simulation: two runs sharing a seed follow the same trajectory. */
    val seedInput = Input(initialSeed.toString(), rounded = true) { _, value ->
        value.trim().toLongOrNull()?.let { seed -> onUpdateSeed(seed) }
    }

    /** Called when another project is loaded: shows its seed. */
    fun refreshSeed(seed: Long) {
        if (seedInput.value != seed.toString()) seedInput.value = seed.toString()
    }

    /**
     * A checkbox is rendered inside a `.help` span (12 px). Dropping that class gives it
     * the body text size of the tab, like the field labels around it.
     */
    private fun <T : bulma.Checkbox> T.alignText(): T {
        root.classList.remove("help")
        root.style.fontWeight = "700"
        return this
    }

    /** A little breathing room between the fields of a page: Bulma packs them tightly. */
    private fun <T : bulma.Box> T.spaced(): T {
        val fields = root.querySelectorAll(".field")
        for (index in 0 until fields.length) {
            (fields.item(index) as? HTMLElement)?.style?.marginBottom = "1rem"
        }
        return this
    }

    /** Hovering a cell shows the field concentrations (on by default). */
    val valueInfoSwitch = Checkbox(page.i18n("Value info"), initialValueInfo) { _, checked ->
        onValueInfo(checked)
    }.alignText()

    /** Shows the non-blocking warnings of the model in the problems table. */
    val warningsCheckbox = Checkbox(page.i18n("Display warnings"), initialWarnings) { _, checked ->
        onWarnings(checked)
    }.alignText()

    private val windowValues = (10..500 step 10).toList()
    private var windowSize = initialWindowSize

    val slidingWindowCheckbox = Checkbox(page.i18n("Sliding window"), initialSlidingWindow) { _, checked ->
        onSlidingWindow(checked, windowSize)
    }.alignText()

    val windowSizeSelect = Select(
        options = windowValues.map { Option("$it") },
        rounded = true,
        selectedIndex = windowValues.indexOf(initialWindowSize).coerceAtLeast(0)
    ) { _: Event, value: List<Option> ->
        value.firstOrNull()?.value?.toIntOrNull()?.let { size ->
            windowSize = size
            onSlidingWindow(slidingWindowCheckbox.checked, size)
        }
    }

    /** True when the custom width and height drive the grid instead of the square preset. */
    private var customSize: Boolean = initialData.isCustomSize

    val sizeController = Select(
        options = availableSimulationSizes.map { Option("$it") }, rounded = true,
        selectedIndex = availableSimulationSizes.indexOf(initialData.size)
    ) { _: Event, value: List<Option> ->
        value.firstOrNull()?.value?.let {
            // a preset always wins over the custom size
            data = data.withPreset(it.toInt())
        }
    }

    /** Incrementable grid dimension: minus, value, plus, by step of ten cells. */
    private inner class SizeStepper(initial: Int) {
        var value: Int = clampSimulationSize(initial)
            private set
        private var silent = false
        private val field = EditableStringController("$value", "") { _, new, controller ->
            if (silent) return@EditableStringController
            val parsed = clampSimulationSize(new.trim().toIntOrNull() ?: value)
            if ("$parsed" != new.trim()) controller.data = "$parsed"
            set(parsed)
        }
        private val minus = Button("-") { set(value - SIMULATION_SIZE_STEP) }
        private val plus = Button("+") { set(value + SIMULATION_SIZE_STEP) }
        val container = Div(minus, field.container, plus).apply { root.classList.add("field", "has-addons") }

        fun set(newValue: Int) {
            value = clampSimulationSize(newValue)
            show(value)
            applyCustomSize()
        }

        /** Displays a value without notifying the model (used when the data changes). */
        fun show(newValue: Int) {
            value = clampSimulationSize(newValue)
            silent = true
            field.data = "$value"
            silent = false
        }

        fun enabled(enabled: Boolean) {
            minus.disabled = !enabled
            plus.disabled = !enabled
            field.readOnly = !enabled
        }
    }

    private val widthStepper = SizeStepper(
        if (initialData.isCustomSize) initialData.customWidth else initialData.effectiveWidth
    )
    private val heightStepper = SizeStepper(
        if (initialData.isCustomSize) initialData.customHeight else initialData.effectiveHeight
    )

    private fun applyCustomSize() {
        if (!customSize) return
        data = data.withCustomSize(widthStepper.value, heightStepper.value)
    }

    /**
     * Radios of the grid size mode, built by hand: Bulma's Radio API is not usable here.
     * A radio cannot be unchecked by clicking it, so the preset mode has its own button.
     */
    private fun radioInput(mode: String, checked: Boolean): HTMLInputElement =
        (document.createElement("input") as HTMLInputElement).apply {
            type = "radio"
            name = "centy-grid-size"
            value = mode
            this.checked = checked
        }

    private val customRadioInput = radioInput("custom", initialData.isCustomSize)
    private val presetRadioInput = radioInput("preset", !initialData.isCustomSize)

    private fun radioLabel(input: HTMLInputElement, text: String) = HtmlWrapper(
        (document.createElement("label") as HTMLLabelElement).apply {
            classList.add("radio")
            appendChild(input)
            appendChild(document.createTextNode(" " + text))
        }
    )

    val customRadio = radioLabel(customRadioInput, page.i18n("Custom"))
    val presetRadio = radioLabel(presetRadioInput, page.i18n("Preset"))

    private fun setRadioChecked(checked: Boolean) {
        customRadioInput.checked = checked
        presetRadioInput.checked = !checked
    }

    init {
        val apply = {
            val checked = customRadioInput.checked
            customSize = checked
            widthStepper.enabled(checked)
            heightStepper.enabled(checked)
            sizeController.disabled = checked || readOnly
            data = if (checked) data.withCustomSize(widthStepper.value, heightStepper.value)
            else data.withPreset(data.size)
        }
        customRadioInput.addEventListener("change", { apply() })
        presetRadioInput.addEventListener("change", { apply() })
        // initial state: the steppers only matter in custom mode
        widthStepper.enabled(customSize)
        heightStepper.enabled(customSize)
        sizeController.disabled = customSize
    }


    val colorController = ColorSelectController(data.backgroundColor ?: "Grey") { _, new, _ ->
        data = data.copy(backgroundColor = if (new.isBlank()) null else new.trim())
    }

    val showGridSwitch = Switch(
        page.i18n("Show"), checked = data.showGrid, rounded = true, outlined = true
    ) { _, c -> data = data.copy(showGrid = c) }

    val gridTextureUrlController = EditableStringController(data.gridTextureUrl ?: "", "") { _, new, _ ->
        data = data.copy(gridTextureUrl = if (new.isBlank()) null else new.trim())
    }

    private val simulationPage = TabPage(
        TabItem(page.i18n("Simulation"), "play"),
        Box(
            Field(
                Label(page.i18n("Random seed")),
                Control(seedInput)
            ),
            Field(
                Label(page.i18n("Update Plan")),
                Control(updatePlanSelect)
            )
        ).spaced()
    )

    private val environmentPage = TabPage(
        TabItem(page.i18n("Environment"), "cogs"),
        Box(
            Field(
                Label(page.i18n("Size")),
                Control(sizeController)
            ),
            Div(presetRadio, customRadio).apply { root.classList.add("field") },
            Div(Label(page.i18n("Width")), widthStepper.container).apply { root.classList.add("field") },
            Div(Label(page.i18n("Height")), heightStepper.container).apply { root.classList.add("field") },
            Field(
                Label(page.i18n("Background Color")),
                Control(colorController.container)
            ),
            Field(
                Label(page.i18n("Grid")),
                showGridSwitch
            ),
            Field(Control(valueInfoSwitch)),
            Field(
                Label(page.i18n("Image URL")),
                gridTextureUrlController.container
            )
        ).spaced()
    )

    private val curvesPage = TabPage(
        TabItem(page.i18n("Curves"), "chart-line"),
        Box(
            Field(Control(slidingWindowCheckbox)),
            Field(
                Label(page.i18n("Window size")),
                Control(windowSizeSelect)
            )
        ).spaced()
    )

    private val modelPage = TabPage(
        TabItem(page.i18n("Model"), "boxes"),
        Box(
            Field(Control(warningsCheckbox))
        ).spaced()
    )

    // ------------------------------------------------------------- recordings

    /** The settings of the Records tab: what the widgets below show and what the view applies. */
    private var recording: RecordingSettings = initialRecording.normalized()

    /** The quality slider, from [MIN_JPEG_QUALITY] to [MAX_JPEG_QUALITY] by [JPEG_QUALITY_STEP]. */
    private val qualityInput = (document.createElement("input") as HTMLInputElement).apply {
        type = "range"
        min = "$MIN_JPEG_QUALITY"
        max = "$MAX_JPEG_QUALITY"
        step = "$JPEG_QUALITY_STEP"
        value = jpegQualityLabel(recording.jpegQuality)
        style.width = "100%"
    }

    /** The quality written next to its slider, two decimals whatever the locale of the browser. */
    private val qualityValue = HtmlWrapper(
        (document.createElement("span") as HTMLElement).apply { classList.add("help") }
    )

    val formatSelect = Select(
        options = listOf(Option(page.i18n("JPEG")), Option(page.i18n("PNG"))),
        rounded = true,
        selectedIndex = if (recording.captureFormat == CaptureFormat.Png) 1 else 0
    ) { _: Event, value: List<Option> ->
        val format = if (value.firstOrNull()?.value == page.i18n("PNG")) CaptureFormat.Png else CaptureFormat.Jpeg
        recording = recording.copy(captureFormat = format)
        applyRecording()
    }

    /** A radio of the capture resolution: the two modes are explicit, never a single checkbox. */
    private fun resolutionInput(mode: String, checked: Boolean): HTMLInputElement =
        (document.createElement("input") as HTMLInputElement).apply {
            type = "radio"
            name = "centy-capture-resolution"
            value = mode
            this.checked = checked
        }

    private val screenResolutionInput = resolutionInput(
        "screen", recording.captureResolution == CaptureResolution.Screen
    )
    private val gridResolutionInput = resolutionInput(
        "grid", recording.captureResolution == CaptureResolution.Grid
    )

    val screenResolutionRadio = radioLabel(screenResolutionInput, page.i18n("Screen size"))
    val gridResolutionRadio = radioLabel(gridResolutionInput, page.i18n("Grid size"))

    private val pixelValues = (MIN_PIXELS_PER_CELL..MAX_PIXELS_PER_CELL).toList()

    val pixelsSelect = Select(
        options = pixelValues.map { Option("$it") },
        rounded = true,
        selectedIndex = pixelValues.indexOf(recording.pixelsPerCell).coerceAtLeast(0)
    ) { _: Event, value: List<Option> ->
        value.firstOrNull()?.value?.toIntOrNull()?.let { pixels ->
            recording = recording.copy(pixelsPerCell = clampPixelsPerCell(pixels))
            applyRecording()
        }
    }

    /** Label of a separator in the language of the page. */
    private fun separatorLabel(separator: CsvSeparator): String = when (separator) {
        CsvSeparator.Tab -> page.i18n("Tabulation")
        CsvSeparator.Comma -> page.i18n("Comma")
        CsvSeparator.Semicolon -> page.i18n("Semicolon")
        CsvSeparator.Space -> page.i18n("Space")
    }

    private val separators = CsvSeparator.entries.toList()

    val separatorSelect = Select(
        options = separators.map { Option(separatorLabel(it)) },
        rounded = true,
        selectedIndex = separators.indexOf(recording.csvSeparator)
    ) { _: Event, value: List<Option> ->
        val chosen = value.firstOrNull()?.value
        separators.firstOrNull { separatorLabel(it) == chosen }?.let { separator ->
            recording = recording.copy(csvSeparator = separator)
            applyRecording()
        }
    }

    /**
     * Contextual help of the tab: where the settings are kept, and the section of the
     * documentation that describes the captures and the CSV separator.
     */
    private val recordsHelp = HtmlWrapper(
        (document.createElement("p") as HTMLElement).apply {
            classList.add("help")
            textContent = page.i18n("These settings are kept by this browser, not in the project file.")
            appendChild(document.createElement("br"))
            appendChild((document.createElement("a") as HTMLAnchorElement).apply {
                href = "help.html#records"
                target = "_blank"
                textContent = page.i18n("Open the help")
            })
        }
    )

    /**
     * Shows the settings in the widgets without telling anybody: the quality only matters for a
     * JPEG, the pixels per cell only for a grid-sized capture, and a read-only page freezes all
     * of them.
     */
    private fun showRecording() {
        qualityInput.value = jpegQualityLabel(recording.jpegQuality)
        qualityInput.disabled = !recording.usesQuality || readOnly
        qualityValue.root.textContent = jpegQualityLabel(recording.jpegQuality)
        formatSelect.disabled = readOnly
        screenResolutionInput.disabled = readOnly
        gridResolutionInput.disabled = readOnly
        pixelsSelect.disabled = recording.captureResolution != CaptureResolution.Grid || readOnly
        separatorSelect.disabled = readOnly
    }

    /** Applies what the visitor has just chosen: shows it, remembers it, tells the view. */
    private fun applyRecording() {
        recording = recording.normalized()
        showRecording()
        onRecording(recording)
    }

    private val recordsPage = TabPage(
        TabItem(page.i18n("Records"), "database"),
        Box(
            SubTitle(page.i18n("Captures")),
            Field(
                Label(page.i18n("Picture format")),
                Control(formatSelect)
            ),
            Div(
                Label(page.i18n("JPEG quality")),
                Div(HtmlWrapper(qualityInput)).apply { root.classList.add("control") },
                qualityValue
            ).apply { root.classList.add("field") },
            Div(
                Label(page.i18n("Resolution")),
                Div(screenResolutionRadio, gridResolutionRadio)
            ).apply { root.classList.add("field") },
            Field(
                Label(page.i18n("Pixels per cell")),
                Control(pixelsSelect)
            ),
            SubTitle(page.i18n("Curves")),
            Field(
                Label(page.i18n("CSV column separator")),
                Control(separatorSelect)
            ),
            recordsHelp
        ).spaced()
    )

    init {
        qualityInput.addEventListener("input", {
            // the slider only gives values between its bounds, but a stored or scripted one does not
            recording = recording.copy(
                jpegQuality = clampJpegQuality(qualityInput.value.toDoubleOrNull() ?: recording.jpegQuality)
            )
            applyRecording()
        })
        screenResolutionInput.addEventListener("change", {
            if (screenResolutionInput.checked) {
                recording = recording.copy(captureResolution = CaptureResolution.Screen)
                applyRecording()
            }
        })
        gridResolutionInput.addEventListener("change", {
            if (gridResolutionInput.checked) {
                recording = recording.copy(captureResolution = CaptureResolution.Grid)
                applyRecording()
            }
        })
        // the initial state is shown and remembered: an absent or corrupt entry becomes explicit
        applyRecording()
    }

    private val tabs = Tabs(boxed = true).apply { root.classList.add("editor") }

    override val container = Div(
        tabs,
        TabPages(simulationPage, environmentPage, curvesPage, modelPage, recordsPage, tabs = tabs),
        classes = "editor"
    ).apply { root.style.display = "contents" }

    override fun refresh() {
        colorController.refresh()
        gridTextureUrlController.refresh()
        // the two inputs only matter in custom mode, and a preset cannot be picked then
        widthStepper.enabled(customSize && !readOnly)
        heightStepper.enabled(customSize && !readOnly)
        sizeController.disabled = customSize || readOnly
        showRecording()
    }
}
