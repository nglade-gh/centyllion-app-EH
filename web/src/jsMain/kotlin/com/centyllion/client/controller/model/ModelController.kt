package com.centyllion.client.controller.model

import bulma.*
import com.centyllion.client.controller.utils.SearchController
import com.centyllion.client.controller.utils.filtered
import com.centyllion.client.download
import com.centyllion.client.page.BulmaPage
import com.centyllion.client.plotter.Plot
import com.centyllion.client.plotter.PlotterController
import com.centyllion.client.plotter.toRGB
import com.centyllion.client.stringHref
import com.centyllion.model.*
import com.centyllion.model.Field
import io.data2viz.geom.size
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import kotlin.properties.Delegates.observable

/** True when [element] (by uuid) is part of this model. */
private fun GrainModel.holds(element: ModelElement): Boolean = when (element) {
    is Grain -> grains.any { it.uuid == element.uuid }
    is Behaviour -> behaviours.any { it.uuid == element.uuid }
    is Field -> fields.any { it.uuid == element.uuid }
    else -> false
}

class ModelController(
    val page: BulmaPage, model: ModelAndSimulation,
    val onUpdate: (old: ModelAndSimulation, new: ModelAndSimulation, controller: ModelController) -> Unit = { _, _, _ -> }
) : NoContextController<ModelAndSimulation, Columns>() {

    override var data: ModelAndSimulation by observable(model) { _, old, new ->
        if (old != new) {
            if (old.model != new.model) {
                fieldsController.data = data.model.fields.filtered(searchController.data)
                grainsController.context = data.model
                grainsController.data = data.model.grains.filtered(searchController.data)
                behavioursController.context = data.model
                behavioursController.data = data.model.behaviours.filtered(searchController.data)
                simulationController.context = data.model

                grainChart.data = createGrainPlots()
                fieldChart.data = createFieldPlots()
                refreshFieldPanels()

                // handles problems display
                val problems = new.model.diagnose(page.appContext.locale)

                // problems box is visible is there are problems and was already open
                problemsMessage.hidden = problems.isEmpty()
                problemsTable.body = problems.map { it.toBulma() }

                // the selection can belong to a previous model (New Model, load,
                // import): drop it so the edition panel does not keep showing it
                selected?.let { selection ->
                    if (!new.model.holds(selection)) selected = null
                }
            }

            if (old.simulation != new.simulation) {
                simulationController.data = data.simulation
                settingsController.data = data.simulation.settings
                settingsController.refreshSeed(data.simulation.seed)
            }

            onUpdate(old, new, this@ModelController)
            refresh()
        }
    }

    var expertMode: Boolean by observable(false) { _, old, new ->
        if (old != new) {
            refreshFieldPanels()
            when (val c = editorController) {
                is BehaviourEditController -> c.expertMode = new
                is GrainEditController -> c.expertMode = new
                else -> {}
            }
        }
    }

    /**
     * Field chart and field CSV export are available in both modes: they only
     * depend on the model having fields. The grain chart keeps the remaining
     * height when the field chart is shown.
     */
    private fun refreshFieldPanels() {
        val visible = data.model.fields.isNotEmpty()
        fieldsChartsHeader.hidden = !visible
        exportFieldCsvButton.hidden = !visible
        fieldChart.hidden = !visible

        // both charts share the available height when the field chart is shown;
        // the grain chart keeps the whole height otherwise. The parent width is
        // not reliable before the first layout, so size from the window here and
        // let resizeCharts() refine it on later layouts.
        val ratio = if (visible) 0.4 else 0.8
        val width = window.innerWidth / 4.0
        grainChart.size = size(width, window.innerHeight * ratio)
        fieldChart.size = size(width, window.innerHeight * ratio)
        grainChart.renderRequest()
        fieldChart.renderRequest()

        resizeCharts()
    }

    override var readOnly: Boolean
        get() = false
        set(_) {}

    private fun MultipleController<*, *, *, *, *>.updateSelection(value: Any?) {
        val found =
            this.dataControllers.find { it.data === value } ?:
            this.dataControllers.find { it.data == value }

        this.dataControllers.forEach {
            it.root.classList.toggle("is-selected", found == it)
        }
    }

    var selected: ModelElement? by observable(null) { _, oldSelection, newSelection ->
        if (oldSelection != newSelection) {
            editorController = when (newSelection) {
                is Field -> FieldEditController(newSelection, data.model, page) { old, new, _ ->
                    data = data.updateField(old, new)
                }

                is Grain -> GrainEditController(newSelection, data.model, page, expertMode) { old, new, _ ->
                    data = data.updateGrain(old, new)
                }

                is Behaviour -> BehaviourEditController(newSelection, data.model, page, expertMode) { old, new, _ ->
                    data = data.updateBehaviour(old, new)
                }

                else -> null
            }
            editorController?.root?.classList?.add("animated", "fadeIn", "faster")
            editorController?.readOnly = this.readOnly
            editorColumn.body = listOf(editorController ?: emptyEditor)
            fieldsController.updateSelection(newSelection)
            grainsController.updateSelection(newSelection)
            behavioursController.updateSelection(newSelection)

            // update simulation selected grain
            if (newSelection is Grain) {
                simulationController.simulationViewController.selectedGrain = newSelection
            } else {
                simulationController.simulationViewController.selectedGrain = null
            }

        }
    }

    fun scrollToSelected() {
        // TODO
    }

    val problemsTable = Table(
        head = listOf(TableHeaderRow(
            TableHeaderCell(page.i18n("Source")), TableHeaderCell(page.i18n("Message"))
        )),
        fullWidth = true, hoverable = true
    ).apply {
        root.style.backgroundColor = "transparent"
    }

    val problemsMessage = Message(body = listOf(problemsTable), color = ElementColor.Danger)
        .apply {
            // Problems column is always hidden when starting
            hidden = true
        }

    private val ModelElement.icon get() = when (this) {
        is Field -> fieldIcon
        is Grain -> grainIcon
        is Behaviour -> behaviourIcon
        else -> ""
    }

    private fun Problem.toBulma() = TableRow(
        TableCell(body = arrayOf(Icon(source.icon), span(source.name))), TableCell(message)
    ).also {
        it.root.onclick = {
            selected = this.source
            scrollToSelected()
        }
    }

    val searchController: SearchController = SearchController(page) { _, filter ->
        fieldsController.data = data.model.fields.filtered(filter)
        grainsController.data = data.model.grains.filtered(filter)
        behavioursController.data = data.model.behaviours.filtered(filter)
    }

    val addFieldButton = iconButton(Icon("plus"), ElementColor.Primary, true, size = Size.Small) {
        val field = data.model.newField(page.i18n("Field"))
        this.data = data.addField(field)
        selected = field
        editionTabPages.selectedPage = editionItem
    }

    val addGrainButton = iconButton(Icon("plus"), ElementColor.Primary, true, size = Size.Small) {
        val grain = data.model.newGrain(page.i18n("Grain"))
        this.data = data.addGrain(grain)
        selected = grain
        editionTabPages.selectedPage = editionItem
    }

    val addBehaviourButton = iconButton(Icon("plus"), ElementColor.Primary, true, size = Size.Small) {
        val behaviour = data.model.newBehaviour(page.i18n("Behaviour"))
        this.data = data.addBehaviour(behaviour)
        selected = behaviour
        editionTabPages.selectedPage = editionItem
    }

    val grainsController: MultipleController<Grain, GrainModel, Columns, Column, Controller<Grain, GrainModel, Column>> =
        columnsController(
            initialList = data.model.grains,
            initialContext = data.model,
            onClick = { grain, _ -> selected = grain }
        ) { grain, previous ->
            previous ?: GrainDisplayController(page, grain, data.model).wrap { controller ->
                controller.onDelete = {
                    data = data.dropGrain(controller.data)
                    selected = null
                }
                controller.onVisibilityChange = { visible ->
                    val hidden = page.appContext.hiddenGrainIds
                    if (visible) hidden.remove(controller.data.id) else hidden.add(controller.data.id)
                    simulationController.simulationViewController.refreshVisibility()
                }
                Column(controller, size = ColumnSize.Full)
            }
        }

    val behavioursController: MultipleController<Behaviour, GrainModel, Columns, Column, Controller<Behaviour, GrainModel, Column>> =
        columnsController(data.model.behaviours, data.model, onClick = { behaviour, _ -> selected = behaviour })
        { behaviour, previous ->
            previous ?: BehaviourDisplayController(page, behaviour, data.model).wrap { controller ->
                controller.onDelete = {
                    data = data.dropBehaviour(controller.data)
                    selected = null
                }
                Column(controller.container, size = ColumnSize.Full)
            }
        }

    val fieldsController: MultipleController<Field, Unit, Columns, Column, Controller<Field, Unit, Column>> =
        noContextColumnsController(
            initialList = data.model.fields,
            onClick = { field, _ -> selected = field },
            controllerBuilder = { field, previous ->
                previous ?: FieldDisplayController(field).wrap { controller ->
                    controller.onDelete = {
                        data = data.dropField(controller.data)
                        selected = null
                    }
                    controller.onVisibilityChange = { visible ->
                        val hidden = page.appContext.hiddenFieldIds
                        if (visible) hidden.remove(controller.data.id) else hidden.add(controller.data.id)
                        simulationController.simulationViewController.refreshVisibility()
                    }
                    Column(controller.container, size = ColumnSize.Full)
                }
            }
        )

    /**
     * Section header of the editor (agents, behaviours, fields) with a fold
     * chevron. The three sections fold independently, so dozens of behaviours
     * don't force long scrolls to reach the agents or the fields. Adding an
     * element unfolds the section so the new row stays visible.
     */
    private fun sectionHeader(icon: String, title: String, content: BulmaElement, add: Button): Level {
        val chevron = Icon("chevron-up", Size.Small)

        fun apply(collapsed: Boolean) {
            content.hidden = collapsed
            // only swap the direction class: keep Bulma's size classes (fa-sm…)
            chevron.root.querySelector("i")?.classList?.let { classes ->
                classes.remove(if (collapsed) "fa-chevron-up" else "fa-chevron-down")
                classes.add(if (collapsed) "fa-chevron-down" else "fa-chevron-up")
            }
        }

        val toggle = iconButton(chevron, ElementColor.None, rounded = true, size = Size.Small) {
            apply(!content.hidden)
        }

        // the section is unfolded when a new element is added
        val addAction = add.onClick
        add.onClick = { button ->
            addAction?.invoke(button)
            apply(false)
        }

        return Level(
            left = listOf(Icon(icon), Title(page.i18n(title), TextSize.S4)),
            right = listOf(toggle, add),
            mobile = true
        )
    }

    val grainsHeader = sectionHeader(grainIcon, "Grains", grainsController, addGrainButton)
    val behavioursHeader = sectionHeader(behaviourIcon, "Behaviours", behavioursController, addBehaviourButton)
    val fieldsHeader = sectionHeader(fieldIcon, "Fields", fieldsController, addFieldButton)

    val leftColumn = Column(
        searchController,
        grainsHeader,
        grainsController,
        behavioursHeader,
        behavioursController,
        fieldsHeader,
        fieldsController,
        problemsMessage,
        size = ColumnSize.S3
    ).apply {
        root.style.height = "80vh"
        root.style.overflowY = "auto"
    }

    val simulationController = SimulationRunController(
        model.simulation, model.model, page, false,
        onStep = { simulator: Simulator ->
            val grainCounts = simulator.lastGrainsCount()
            grainChart.push(
                simulator.step,
                grainCounts.values.map { it.toDouble() },
            )
            grainChart.renderRequest()

            fieldChart.push(
                simulator.step,
                simulator.lastFieldAmount().values.map { it.toDouble() },
            )
            fieldChart.renderRequest()

            refreshCounts()
        },
        onReset = {  simulator: Simulator ->
            // resets grain chart
            grainChart.reset()
            val grainCounts = simulator.lastGrainsCount()
            grainChart.push(0, grainCounts.values.map { it.toDouble() })
            grainChart.renderRequest()

            // resets field chart
            fieldChart.reset()
            fieldChart.push(0, simulator.lastFieldAmount().values.map { it.toDouble() })
            fieldChart.renderRequest()
        },
        onUpdate = { old, new, _ -> if (old != new) data = data.copy(simulation = new) }
    )


    val emptyEditor = SubTitle(page.i18n("Select a element to edit it"))
        .also { it.root.classList.add("has-text-centered") }

    val editorColumn = Column(emptyEditor, size = ColumnSize.Full)

    var editorController: Controller<*, dynamic, dynamic>? = null

    val settingsController = SimulationSettingsController(
        model.simulation.settings, page,
        initialUpdatePlan = model.model.updatePlan,
        onUpdatePlan = { newPlan ->
            data = data.copy(model = data.model.copy(updatePlan = newPlan))
        },
        initialSeed = model.simulation.seed,
        onUpdateSeed = { newSeed ->
            data = data.copy(simulation = data.simulation.copy(seed = newSeed))
        }
    ) { _, new, _ ->
        data = data.copy(simulation = data.simulation.updateSettings(new))
    }

    val modelSearchController: ModelSearchController = ModelSearchController(page) {
        loaded -> data = loaded
        editionTabPages.selectedPage = simulationItem
    }

    val editionItem: TabPage = TabPage(TabItem(page.i18n("Model"), "boxes"), editorColumn)
    val simulationItem: TabPage = TabPage(TabItem(page.i18n("Simulation"), "play"), simulationController)
    val environmentItem: TabPage = TabPage(TabItem(page.i18n("Settings"), "cogs"), settingsController)

    val editionTabs = Tabs(fullWidth = true, boxed = true)
    val editionTabPages = TabPages(simulationItem, editionItem, environmentItem, tabs = editionTabs) {
        if (it == simulationItem) simulationController.resize()
        refresh()
    }

    val centerColumn = Column(
        editionTabPages,
        size = ColumnSize.S6
    )

    var exportGrainCsvButton = iconButton(Icon("file-csv"), ElementColor.Info, true) {
        val header = "step,${data.model.grains.map { it.name.ifBlank { it.id } }.joinToString(",")}"
        val content = (0 until simulationController.simulator.step).joinToString("\n") { step ->
            val counts = data.model.grains.map { simulationController.simulator.grainCountHistory[it]?.get(step) ?: 0 }.joinToString(",")
            "$step,$counts"
        }
        download("agents.csv", stringHref("$header\n$content"))
    }

    val fieldsChartsHeader = Label(page.i18n("Fields")).apply {
        hidden = true
    }

    var exportFieldCsvButton = iconButton(Icon("file-csv"), ElementColor.Info, true) {
        val header = "step,${data.model.fields.map { it.name.ifBlank { it.id } }.joinToString(",")}"
        val content = (0 until simulationController.simulator.step).joinToString("\n") { step ->
            val counts = data.model.fields.map { simulationController.simulator.fieldAmountHistory[it]?.get(step) ?: 0f }.joinToString(",")
            "$step,$counts"
        }
        download("fields.csv", stringHref("$header\n$content"))
    }.apply {
        hidden = true
    }

    val chartsHeader = Level(
        center = listOf(
            Label(page.i18n("Grains")), exportGrainCsvButton,
            fieldsChartsHeader, exportFieldCsvButton
        ),
        mobile = true
    )

    private fun createGrainPlots(): List<Plot> = data.model.grains.map {
        Plot(
            label = it.label(true),
            stroke = it.color.toRGB(),
            startHidden = !data.model.doesGrainCountCanChange(it)
        )
    }

    private fun createFieldPlots(): List<Plot> = data.model.fields.map { field ->
        Plot(label = field.label(true), stroke = field.color.toRGB() )
    }

    val grainChart: PlotterController = PlotterController(
        page.i18n("Grains"), page.i18n("Step"),
        createGrainPlots(), size(window.innerWidth/4.0, window.innerHeight * if (data.model.fields.isEmpty()) 0.8 else 0.4),
        roundPoints = true,
    ).apply {
        push(0, simulationController.simulator.grainsCounts().values.map { it.toDouble() })
    }

    val fieldChart: PlotterController = PlotterController(
        page.i18n("Fields"), page.i18n("Step"),
        createFieldPlots(), size(window.innerWidth/4.0, window.innerHeight/3.0)
    ).apply { hidden = this@ModelController.data.model.fields.isEmpty() }

    // shared display mode for both charts: full history (step 0 -> current) or sliding window
    private val windowValues = (10..500 step 10).toList()
    private var plotWindowMode = false
    private var plotWindowSize = 100

    private fun applyPlotWindow(checked: Boolean, sizeValue: Int) {
        plotWindowMode = checked
        plotWindowSize = sizeValue
        val w = if (checked) sizeValue else 0
        grainChart.window = w
        fieldChart.window = w
        grainChart.renderRequest()
        fieldChart.renderRequest()
    }

    val windowCheckbox = Checkbox(page.i18n("Sliding window"), false) { _, checked ->
        applyPlotWindow(checked, plotWindowSize)
    }

    val windowSelect = Select(
        options = windowValues.map { Option("$it") },
        rounded = true,
        selectedIndex = windowValues.indexOf(100)
    ) { _: Event, value: List<Option> ->
        value.firstOrNull()?.value?.toIntOrNull()?.let { applyPlotWindow(plotWindowMode, it) }
    }

    val plotModeHeader = Level(
        center = listOf(windowCheckbox, Label(page.i18n("Window")), windowSelect),
        mobile = true
    )

    val rightColumn = Column(
        plotModeHeader, chartsHeader, grainChart, fieldChart,
        size = ColumnSize.S3
    )

    override val container = Columns(
        leftColumn, centerColumn, rightColumn,
        multiline = true,
    )

    fun refreshCounts() {
        // refreshes grain counts
        /* TODO
        val counts = simulationController.simulator.lastGrainsCount().values
        grainsController.dataControllers.zip(counts) { controller, count ->
            val source = controller.source
            if (source is GrainRunController) source.count = count
        }

        // refreshes field amounts
        val amounts = simulationController.simulator.lastFieldAmount().values
        fieldsController.dataControllers.zip(amounts) { controller, amount ->
            val source = controller.source
            if (source is FieldRunController) source.amount = amount
        }
         */
    }

    override fun refresh() {
        // TODO refresh
        resizeCharts()
    }

    fun dispose() {
        // stop simulation
        simulationController.dispose()
    }

    fun resizeCharts() {
        val grainParent = grainChart.root.parentElement
        if (grainParent is HTMLElement) {
            val ratio = if (fieldChart.hidden) 0.8 else 0.4
            // prevent resizing if size is < 0
            val x = grainParent.offsetWidth - 30.0
            if (x > 0) grainChart.size = size(x, window.innerHeight * ratio)
        }

        val fieldParent = fieldChart.root.parentElement
        if (fieldParent is HTMLElement) {
            val ratio = 0.4
            // prevent resizing if size is < 0
            val x = fieldParent.offsetWidth - 30.0
            if (x > 0) fieldChart.size = size(x, window.innerHeight * ratio)
        }
    }

}
