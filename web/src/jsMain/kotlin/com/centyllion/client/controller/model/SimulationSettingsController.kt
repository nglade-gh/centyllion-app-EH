package com.centyllion.client.controller.model

import bulma.*
import bulma.extension.Switch
import com.centyllion.client.controller.utils.EditableStringController
import com.centyllion.client.controller.utils.editorBox
import com.centyllion.client.page.BulmaPage
import com.centyllion.model.DEFAULT_SIMULATION_SEED
import com.centyllion.model.PARALLEL_UPDATE_PLAN
import com.centyllion.model.SEQUENTIAL_UPDATE_PLAN
import com.centyllion.model.MAX_SIMULATION_SIZE
import com.centyllion.model.MIN_SIMULATION_SIZE
import com.centyllion.model.SIMULATION_SIZE_STEP
import com.centyllion.model.SimulationSettings
import com.centyllion.model.availableSimulationSizes
import com.centyllion.model.clampSimulationSize
import kotlinx.browser.document
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

    private val tabs = Tabs(boxed = true).apply { root.classList.add("editor") }

    override val container = Div(
        tabs,
        TabPages(simulationPage, environmentPage, curvesPage, modelPage, tabs = tabs),
        classes = "editor"
    ).apply { root.style.display = "contents" }

    override fun refresh() {
        colorController.refresh()
        gridTextureUrlController.refresh()
    }
}
