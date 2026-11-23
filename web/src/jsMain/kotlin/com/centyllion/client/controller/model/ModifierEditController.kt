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

    private fun helpLine(name: String, description: String) = TableRow(
        TableCell(name).apply { root.classList.add("has-text-weight-bold") },
        TableCell(page.i18n(description)),
    )

    private fun helpButton(title: String, sections: List<BulmaElement>) = Button(
        page.i18n("Help"), size = Size.Small, color = ElementColor.Info
    ) {
        page.modalDialog(page.i18n(title), sections, Button(page.i18n("Close"), color = ElementColor.Primary) {})
    }

    private val conditionHelp = Section(
        p(page.i18n("Evaluated once per step, outside any cell. The variables and functions that depend on a cell therefore take explicit coordinates.")),
        p(page.i18n("The formula must be a logical predicate returning true or false; the mathematical operators and functions (abs, min, max, sin, cos, log…) are available.")),
        SubTitle(page.i18n("Variables and functions")),
        Table(
            body = listOf(
                helpLine("step", "Number of the current step"),
                helpLine("agent(x, y)", "Type of the agent at the cell (x, y), -1 when the cell is empty"),
                helpLine("age(x, y)", "Age of the agent at the cell (x, y), 0 when the cell is empty"),
                helpLine("count(id, x, y)", "Number of neighbours of type id around the cell (x, y)"),
                helpLine("field(id, x, y)", "Value of the field id at the cell (x, y)"),
                helpLine("IsActive(id) / IsInactive(id)", "1 when the behaviour id is active (resp. inactive), 0 otherwise"),
                helpLine("IsInUse(id) / IsNotInUse(id)", "1 when the simulation modifier id is active (resp. inactive), 0 otherwise"),
            ),
            fullWidth = true, striped = true
        ),
        p(page.i18n("Coordinates are wrapped around the grid (toroidal). There is no x, y, z variable and no age(n): those depend on the cell of a behaviour."))
    )

    private val modifierActionsHelp = Section(
        p(page.i18n("The actions are executed once per step, after the behaviours, when the condition is true.")),
        p(page.i18n("Only meta-actions are allowed here: they act on the simulation, not on one cell.")),
        SubTitle(page.i18n("Meta-actions")),
        Table(
            body = listOf(
                helpLine("pause()", "Pauses the simulation at the end of the current step"),
                helpLine("Add(id, N)", "Adds N agents of type id anywhere in the environment"),
                helpLine("AddRect(id, N, x1, y1, x2, y2)", "Adds N agents of type id inside the rectangle (x1, y1)-(x2, y2)"),
                helpLine("AddDisc(id, N, x, y, radius)", "Adds N agents of type id inside the disc centred on (x, y)"),
                helpLine("EraseRect(x1, y1, x2, y2)", "Erases every agent of the rectangle, whatever its type"),
                helpLine("EraseDisc(x, y, radius)", "Erases every agent of the disc, whatever its type"),
                helpLine("EraseRect(id, x1, y1, x2, y2)", "Erases the agents of type id inside the rectangle"),
                helpLine("EraseDisc(id, x, y, radius)", "Erases the agents of type id inside the disc"),
                helpLine("Erase(id)", "Erases every agent of type id in the whole environment"),
                helpLine("DrawLine(id, x1, y1, x2, y2)", "Draws a line of agents of type id from (x1, y1) to (x2, y2), on the empty cells only"),
                helpLine("Activate(id)", "Activates the behaviour id (no effect when already active)"),
                helpLine("Inactivate(id)", "Inactivates the behaviour id (no effect when already inactive)"),
                helpLine("Use(id)", "Activates the simulation modifier id"),
                helpLine("Unuse(id)", "Inactivates the simulation modifier id"),
            ),
            fullWidth = true, striped = true
        ),
        p(page.i18n("The identifiers are those displayed in the explorer, after the name: name (id). Arguments can be expressions built on the condition functions.")),
        p(page.i18n("AddRect and AddDisc draw their positions among the empty cells of the zone: asking for more agents than there are free cells adds only the free ones."))
    )

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
        HorizontalField(
            Label(page.i18n("Condition")),
            BField(Control(helpButton("Condition of a modifier", listOf(conditionHelp)))),
            formulaController.container
        ),
        HorizontalField(
            Label(page.i18n("Actions")),
            BField(Control(helpButton("Meta-actions", listOf(modifierActionsHelp)))),
            actionsController.container
        ),
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
