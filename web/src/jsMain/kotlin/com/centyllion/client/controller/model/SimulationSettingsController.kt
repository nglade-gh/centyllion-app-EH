package com.centyllion.client.controller.model

import bulma.*
import bulma.extension.Switch
import com.centyllion.client.controller.utils.EditableStringController
import com.centyllion.client.controller.utils.editorBox
import com.centyllion.client.page.BulmaPage
import com.centyllion.model.DEFAULT_SIMULATION_SEED
import com.centyllion.model.PARALLEL_UPDATE_PLAN
import com.centyllion.model.SEQUENTIAL_UPDATE_PLAN
import com.centyllion.model.SimulationSettings
import com.centyllion.model.availableSimulationSizes
import org.w3c.dom.events.Event
import kotlin.properties.Delegates.observable

class SimulationSettingsController (
    initialData: SimulationSettings, val page: BulmaPage,
    initialUpdatePlan: String = PARALLEL_UPDATE_PLAN,
    var onUpdatePlan: (String) -> Unit = { },
    initialSeed: Long = DEFAULT_SIMULATION_SEED,
    var onUpdateSeed: (Long) -> Unit = { },
    var onUpdate: (old: SimulationSettings, new: SimulationSettings, controller: SimulationSettingsController) -> Unit = { _, _, _ -> }
) : NoContextController<SimulationSettings, Div>() {

    override var data: SimulationSettings by observable(initialData) { _, old, new ->
        if (old != new) {
            sizeController.selectedIndex = availableSimulationSizes.indexOf(new.size)
            colorController.data = new.backgroundColor ?: "Grey"
            showGridSwitch.checked = data.showGrid
            gridTextureUrlController.data = data.gridTextureUrl ?: ""
            onUpdate(old, new, this@SimulationSettingsController)
        }
        refresh()
    }

    override var readOnly: Boolean by observable(false) { _, old, new ->
        if (old != new) {
            sizeController.disabled = new
            colorController.readOnly = new
            showGridSwitch.disabled = new
            gridTextureUrlController.readOnly = new
            updatePlanSelect.disabled = new
            seedInput.disabled = new
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

    val sizeController = Select(
        options = availableSimulationSizes.map { Option("$it") }, rounded = true,
        selectedIndex = availableSimulationSizes.indexOf(initialData.size)
    ) { _: Event, value: List<Option> ->
        value.firstOrNull()?.value?.let {
            data = data.copy(size = it.toInt())
        }
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
        )
    )

    private val environmentPage = TabPage(
        TabItem(page.i18n("Environment"), "cogs"),
        Box(
            Field(
                Label(page.i18n("Size")),
                Control(sizeController)
            ),
            Field(
                Label(page.i18n("Background Color")),
                Control(colorController.container)
            ),
            Field(
                Label(page.i18n("Grid")),
                showGridSwitch
            ),
            Field(
                Label(page.i18n("Image URL")),
                gridTextureUrlController.container
            )
        )
    )

    private val tabs = Tabs(boxed = true).apply { root.classList.add("editor") }

    override val container = Div(
        tabs,
        TabPages(simulationPage, environmentPage, tabs = tabs),
        classes = "editor"
    ).apply { root.style.display = "contents" }

    override fun refresh() {
        colorController.refresh()
        gridTextureUrlController.refresh()
    }
}
