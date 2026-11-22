package com.centyllion.client.controller.model

import bulma.*
import bulma.extension.Switch
import com.centyllion.client.controller.utils.EditableStringController
import com.centyllion.client.controller.utils.editorBox
import com.centyllion.client.page.BulmaPage
import com.centyllion.model.*
import kotlin.properties.Delegates.observable
import bulma.Field as BField

/**
 * Editor of a simulation modifier: name, identifier, probability, activation, condition and
 * meta-actions. The condition is evaluated once per step, outside any cell, so its functions
 * take explicit coordinates: agent(x, y), age(x, y), count(id, x, y), field(id, x, y).
 */
class ModifierEditController(
    initialData: SimulationModifier,
    initialContext: GrainModel,
    val page: BulmaPage,
    var onUpdate: (old: SimulationModifier, new: SimulationModifier, controller: ModifierEditController) -> Unit = { _, _, _ -> }
) : Controller<SimulationModifier, GrainModel, Div> {

    override var data: SimulationModifier by observable(initialData) { _, old, new ->
        if (old != new) {
            nameController.data = new.name
            descriptionController.data = new.description
            probabilityController.data = "${new.probability}"
            formulaController.data = new.formula
            actionsController.data = new.actions
            enabledSwitch.checked = new.enabled
            onUpdate(old, new, this@ModifierEditController)
        }
        refresh()
    }

    override var context: GrainModel by observable(initialContext) { _, old, new ->
        if (old != new) refresh()
    }

    override var readOnly: Boolean by observable(false) { _, _, new ->
        nameController.readOnly = new
        descriptionController.readOnly = new
        probabilityController.readOnly = new
        formulaController.readOnly = new
        actionsController.readOnly = new
        enabledSwitch.disabled = new
    }

    val nameController = EditableStringController(data.name, page.i18n("Name")) { _, new, _ ->
        this.data = this.data.copy(name = new)
    }

    val descriptionController = EditableStringController(data.description, page.i18n("Description"), columns = 40) { _, new, _ ->
        this.data = this.data.copy(description = new)
    }

    val probabilityController = EditableStringController("${data.probability}", page.i18n("Probability")) { _, new, controller ->
        val value = new.trim().toDoubleOrNull()
        if (value == null || value < 0.0 || value > 1.0) {
            controller.data = "${this.data.probability}"
        } else {
            this.data = this.data.copy(probability = value)
        }
    }

    val enabledSwitch = Switch(
        page.i18n("Active"), checked = data.enabled, rounded = true, outlined = true
    ) { _, checked -> this.data = this.data.copy(enabled = checked) }

    val formulaController = EditableStringController(data.formula, page.i18n("Condition"), columns = 40) { _, new, _ ->
        this.data = this.data.copy(formula = new)
    }

    val actionsController = EditableStringController(data.actions, page.i18n("Actions"), columns = 40) { _, new, _ ->
        this.data = this.data.copy(actions = new)
    }

    /** Problems of the two formulas, refreshed as they are typed. */
    private val problemsLabel = Label("").apply { root.style.color = "#f14668" }

    private fun problems(): String = buildList {
        validateFormula(data.formula)?.let { add("${page.i18n("Condition")}: $it") }
        validateModifierActions(data.actions)?.let { add("${page.i18n("Actions")}: $it") }
    }.joinToString("\n")

    override val container = editorBox(
        page.i18n("Simulation modifier"), "sliders-h",
        HorizontalField(
            Label(page.i18n("Name")),
            nameController.container,
            BField(Control(Label("ID = ${data.id}")))
        ),
        HorizontalField(Label(page.i18n("Description")), descriptionController.container),
        HorizontalField(Label(page.i18n("Probability")), probabilityController.container),
        HorizontalField(Label(page.i18n("Active")), BField(enabledSwitch)),
        HorizontalField(Label(page.i18n("Condition")), formulaController.container),
        HorizontalField(Label(page.i18n("Actions")), actionsController.container),
        p(page.i18n("The condition and the actions are evaluated once per step, outside any cell: use agent(x, y), age(x, y), count(id, x, y) and field(id, x, y).")),
        problemsLabel
    )

    override fun refresh() {
        nameController.refresh()
        descriptionController.refresh()
        probabilityController.refresh()
        formulaController.refresh()
        actionsController.refresh()
        val text = problems()
        problemsLabel.text = text
        problemsLabel.root.style.display = if (text.isEmpty()) "none" else ""
    }
}
