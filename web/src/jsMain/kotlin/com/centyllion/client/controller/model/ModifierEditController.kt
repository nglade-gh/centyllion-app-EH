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

    /**
     * One command of the help: the syntax in the first column, what it does (with an example
     * substituted for `%0`) in the second one.
     */
    private fun helpLine(name: String, description: String, example: String? = null) = TableRow(
        TableCell(name).apply {
            root.classList.add("has-text-weight-bold")
            // some entries document two forms of the same command on two lines
            root.style.whiteSpace = "pre-line"
        },
        TableCell(if (example == null) page.i18n(description) else page.i18n(description, example)),
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
                helpLine("IsBehaviourActive(id) / ", "1 when the behaviour id is active (resp. inactive), 0 otherwise"),
                helpLine("IsModifierActive(id) / ", "1 when the simulation modifier id is active (resp. inactive), 0 otherwise"),
                helpLine("IsToricSelectionOn()", "1 when the Toric ? switch of the edit bar is on, 0 when it is off"),
                helpLine("IsOverwritingOn()", "1 when the Overwrite ? switch of the edit bar is on, 0 when it is off"),
                helpLine("IsVisible(id)", "1 when the agents of type id are visible in the simulation view, 0 when they are hidden"),
            ),
            fullWidth = true, striped = true
        ),
        p(page.i18n("The tests whose name begins with Is are written here, in the condition of the modifier, and nowhere else: an actions formula refuses them, and so does the condition of a behaviour.")),
        p(page.i18n("Coordinates are wrapped around the grid (toroidal). There is no x, y, z variable and no age(n): those depend on the cell of a behaviour."))
    )

    private val modifierActionsHelp = Section(
        p(page.i18n("The actions are executed once per step, after the behaviours, when the condition is true.")),
        p(page.i18n("Only meta-actions are allowed here: they act on the simulation as a whole, not on one cell.")),
        SubTitle(page.i18n("Meta-actions")),
        Table(
            body = listOf(
                helpLine("pause()", "Pauses the simulation at the end of the current step, for instance %0.", "pause()"),
                helpLine("Add(id, N)", "Adds N agents of type id on empty cells drawn at random in the whole environment, for instance %0.", "Add(1, 20)"),
                helpLine("AddRect(id, N, x1, y1, x2, y2)", "Adds N agents of type id on the empty cells of the rectangle (x1, y1)-(x2, y2), for instance %0.", "AddRect(1, 10, 0, 0, 9, 9)"),
                helpLine("AddDisc(id, N, x, y, radius)", "Adds N agents of type id on the empty cells of the disc centred on (x, y), for instance %0.", "AddDisc(1, 10, 50, 50, 5)"),
                helpLine("DrawLine(id, x1, y1, x2, y2)", "Draws a line of agents of type id from (x1, y1) to (x2, y2), on the empty cells only, for instance %0.", "DrawLine(1, 0, 0, 99, 99)"),
                helpLine("EraseRect(x1, y1, x2, y2)\nEraseRect(id, x1, y1, x2, y2)", "Erases every agent of the rectangle, or only the agents of type id, for instance %0.", "EraseRect(0, 0, 9, 9) / EraseRect(1, 0, 0, 9, 9)"),
                helpLine("EraseDisc(x, y, radius)\nEraseDisc(id, x, y, radius)", "Erases every agent of the disc, or only the agents of type id, for instance %0.", "EraseDisc(50, 50, 5) / EraseDisc(1, 50, 50, 5)"),
                helpLine("Erase(id)", "Erases every agent of type id in the whole environment, for instance %0.", "Erase(1)"),
                helpLine("Activate(id)", "Activates the behaviour id, for instance %0.", "Activate(2)"),
                helpLine("Inactivate(id)", "Inactivates the behaviour id, for instance %0.", "Inactivate(2)"),
                helpLine("Use(id)", "Activates the simulation modifier id, for instance %0.", "Use(0)"),
                helpLine("Unuse(id)", "Inactivates the simulation modifier id, for instance %0.", "Unuse(0)"),
            ),
            fullWidth = true, striped = true
        ),
        SubTitle(page.i18n("State tests")),
        p(page.i18n("The tests whose name begins with Is (IsBehaviourActive, IsModifierActive, IsToricSelectionOn, IsVisible…) test the state of the simulation: they are written in the Condition of the modifier, never here. An actions formula that calls one of them is refused.")),
        p(page.i18n("The identifiers are those displayed in the explorer, after the name: name (id). Arguments can be expressions built on the condition functions.")),
        p(page.i18n("AddRect and AddDisc draw their positions among the empty cells of the zone: asking for more agents than there are free cells adds only the free ones.")),
        SubTitle(page.i18n("Grid commands")),
        Table(
            body = listOf(
                helpLine("Show(id)", "Makes the agents of type id visible again in the simulation view, like the Show ? switch of the explorer, for instance %0.", "Show(1)"),
                helpLine("Hide(id)", "Hides the agents of type id in the simulation view, without touching the simulation, for instance %0.", "Hide(1)"),
                helpLine("Select(x1, y1, x2, y2)", "Selects the rectangle between the two corners, in any order, like Shift + drag, for instance %0.", "Select(10, 10, 20, 20)"),
                helpLine("Copy()", "Copies the visible agents of the selection into the clipboard, for instance %0.", "Copy()"),
                helpLine("Cut()", "Copies the visible agents of the selection into the clipboard, then erases them from the grid, for instance %0.", "Cut()"),
                helpLine("Paste(x, y, overwrite)", "Pastes the clipboard with (x, y) as its top-left corner; overwrite is true (replace) or false (keep), for instance %0.", "Paste(10, 10, false)"),
                helpLine("FlushSelection()", "Drops the selection; the clipboard is left untouched, for instance %0.", "FlushSelection()"),
                helpLine("EraseSelection()", "Erases the visible agents of the selection from the grid, then drops the selection, for instance %0.", "EraseSelection()"),
                helpLine("PasteAndFlush(x, y, overwrite)", "Pastes the clipboard with (x, y) as its top-left corner, then drops the selection, for instance %0.", "PasteAndFlush(10, 10, true)"),
                helpLine("Rotate(N)", "Turns the clipboard N quarter turns clockwise (negative: counter-clockwise), for instance %0.", "Rotate(1)"),
                helpLine("FlipX()", "Mirrors the clipboard left/right, for instance %0.", "FlipX()"),
                helpLine("FlipY()", "Mirrors the clipboard top/bottom, for instance %0.", "FlipY()"),
                helpLine("AllowToricSelection(enabled)", "Turns the Toric ? switch of the edit bar on (true) or off (false), like the switch itself, for instance %0.", "AllowToricSelection(true)"),
                helpLine("AllowOverwriting(enabled)", "Turns the Overwrite ? switch of the edit bar on (true) or off (false), like the switch itself, for instance %0.", "AllowOverwriting(true)"),
            ),
            fullWidth = true, striped = true
        ),
        p(page.i18n("The grid commands work on the current selection and on the clipboard of the simulation view, not on a cell. They are applied at the end of the step, in the order of the formula: a selection or a clipboard built by a modifier can then be used by hand. Copy, Cut, Paste, PasteAndFlush, Rotate, FlipX and FlipY are ignored when the selection or the clipboard they need is empty.")),
        p(page.i18n("Paste and PasteAndFlush follow the Toric ? switch of the edit bar: on, the cells that overflow the grid are folded on the opposite side (coordinates wrapped modulo the width and the height), so Paste(-10, 10, true) brings back on the right what comes out on the left; off, the cells outside the grid are lost."))
    )

    /** Problems of the two formulas, refreshed as they are typed. */
    private val problemsLabel = Label("").apply { root.style.color = "#f14668" }

    private fun problems(): String = buildList {
        validateModifierCondition(data.formula)?.let { add("${page.i18n("Condition")}: $it") }
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
