package com.centyllion.client.controller.model

import bulma.*
import bulma.extension.Switch
import com.centyllion.client.controller.utils.EditableStringController
import com.centyllion.client.controller.utils.formulaStringController
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

    val formulaController = formulaStringController(data.formula, page.i18n("Condition")) { _, new, _ ->
        this.data = this.data.copy(formula = new)
    }

    val actionsController = formulaStringController(data.actions, page.i18n("Actions")) { _, new, _ ->
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
        p(page.i18n("Meta-functions and meta-tests start with a capital letter (Pause, RunNStep, IsVisible); the functions used in the conditions and in the arguments stay in lower case (abs, min, max, sin, cos, count, field, age, agent).")),
        p(page.i18n("pause() in lower case is still accepted for the models already recorded, but it is obsolete: write Pause().")),
        SubTitle(page.i18n("The simulation loop")),
        p(page.i18n("These meta-actions drive the run loop, not the grid: they stop it, run a given number of steps, or rewind the simulation to its initial state. A request is applied at the end of the step that emits it.")),
        Table(
            body = listOf(
                helpLine("Pause()", "Pauses the simulation at the end of the current step, for good: only the reader starts it again, for instance %0.", "Pause()"),
                helpLine("Pause(t)", "Pauses the simulation for t seconds of real time (t > 0, for instance 2.5), then resumes it by itself, for instance %0.", "Pause(2.5)"),
                helpLine("RunOneStep()", "Runs exactly one more step, then pauses the run, for instance %0.", "RunOneStep()"),
                helpLine("RunNStep(N)", "Runs N more steps (N >= 1, rounded), then pauses the run; N <= 0 is ignored, for instance %0.", "RunNStep(10)"),
                helpLine("Rewind()", "Goes back to the initial state and pauses the run, like the rewind button, for instance %0.", "Rewind()"),
                helpLine("Restart()", "Goes back to the initial state and starts the run again, for instance %0.", "Restart()"),
            ),
            fullWidth = true, striped = true
        ),
        SubTitle(page.i18n("Working folder and captures")),
        Table(
            body = listOf(
                helpLine(
                    "Capture()",
                    "Takes a JPEG picture of the simulation view at the end of the step (quality 0.92), for instance %0.",
                    "Capture()"
                ),
            ),
            fullWidth = true, striped = true
        ),
        p(page.i18n("The picture is named model__step.jpg: the model name is cleaned for the file system (no slash, backslash, colon, star, question mark, quotation mark, angle bracket or vertical bar; no surrounding space; bounded length) and becomes the sub-folder of the working folder; the number is the step of the picture, that is the counter displayed once the step has run, so a Capture() triggered by step == 5 writes model__6.jpg. Without a working folder, or on a browser without the File System Access API, the picture is downloaded.")),
        p(page.i18n("A timed pause never advances the simulation: no step runs while it lasts, so the sequence of states stays reproducible as soon as the trigger is deterministic (step == 10). Only its duration comes from the clock of the machine: at least t seconds, and longer in a background tab, where the animation frames that notice the end of the pause are rare.")),
        SubTitle(page.i18n("Meta-actions")),
        Table(
            body = listOf(
                helpLine("Add(id, N)", "Adds N agents of type id on empty cells drawn at random in the whole environment, for instance %0.", "Add(1, 20)"),
                helpLine("AddRect(id, N, x1, y1, x2, y2[, fill])", "Adds N agents of type id on the cells of the rectangle (x1, y1)-(x2, y2): fill true (the default) for the whole rectangle, false for its border only, for instance %0.", "AddRect(1, 10, 0, 0, 9, 9) / AddRect(1, 40, 0, 0, 9, 9, false)"),
                helpLine("AddCircle(id, N, x, y, radius[, fill])", "Adds N agents of type id on the cells of the disc centred on (x, y): fill true (the default) for the whole disc, false for its ring only, for instance %0.", "AddCircle(1, 10, 50, 50, 5) / AddCircle(1, 40, 50, 50, 8, false)"),
                helpLine("DrawLine(id, x1, y1, x2, y2)", "Draws a line of agents of type id from (x1, y1) to (x2, y2), on the empty cells only, for instance %0.", "DrawLine(1, 0, 0, 99, 99)"),
                helpLine("EraseRect(x1, y1, x2, y2[, fill])\nEraseRect(id, x1, y1, x2, y2[, fill])", "Erases every agent of the rectangle, or only the agents of type id; fill false erases the border only, for instance %0.", "EraseRect(0, 0, 9, 9) / EraseRect(1, 0, 0, 9, 9, false)"),
                helpLine("EraseCircle(x, y, radius[, fill])\nEraseCircle(id, x, y, radius[, fill])", "Erases every agent of the disc, or only the agents of type id; fill false erases the ring only, for instance %0.", "EraseCircle(50, 50, 5) / EraseCircle(1, 50, 50, 5, false)"),
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
        p(page.i18n("fill is written after the other arguments, as the literal true or false: AddRect(1, 10, 0, 0, 9, 9, false) draws the border of the rectangle and EraseCircle(50, 50, 10, false) erases the ring of the disc. Omitting it means true, the solid shape, so a formula written before the flag existed keeps its meaning.")),
        p(page.i18n("AddRect and AddCircle draw their positions among the empty cells of the zone: asking for more agents than there are free cells adds only the free ones.")),
        p(page.i18n("AddDisc and EraseDisc are the former names of AddCircle and EraseCircle: they are still accepted for the models already recorded, but they are obsolete: write the circle names.")),
        p(page.i18n("The rectangle and the circle are the same shapes as in the drawing bar: the Fill ? switch of the bar traces them solid or outlined, with the same functions as the meta-actions.")),
        SubTitle(page.i18n("Grid commands")),
        Table(
            body = listOf(
                helpLine("Show(id)", "Makes the agents of type id visible again in the simulation view, like the Show ? switch of the explorer, for instance %0.", "Show(1)"),
                helpLine("Hide(id)", "Hides the agents of type id in the simulation view, without touching the simulation, for instance %0.", "Hide(1)"),
                helpLine("Select(x1, y1, x2, y2)", "Selects the rectangle between the two corners, in any order, like Shift + drag, for instance %0.", "Select(10, 10, 20, 20)"),
                helpLine("Copy()", "Copies the visible agents of the selection into the clipboard, for instance %0.", "Copy()"),
                helpLine("Cut()", "Copies the visible agents of the selection into the clipboard, then erases them from the grid, for instance %0.", "Cut()"),
                helpLine("Paste(x, y, overwrite)", "Pastes the clipboard with (x, y) as its top-left corner; overwrite is true (replace) or false (keep), for instance %0.", "Paste(10, 10, false)"),
                helpLine("FlushSelection()", "Empties the memory: the copied or cut selection is forgotten and nothing is left to paste. The grid is not modified, and the selection rectangle is dropped as well, for instance %0.", "FlushSelection()"),
                helpLine("Unselect()", "Drops the selection rectangle without touching the grid or the memory: it does only that, for instance %0.", "Unselect()"),
                helpLine("EraseSelection()", "Erases the visible agents of the selection from the grid, then drops the selection rectangle, for instance %0.", "EraseSelection()"),
                helpLine("PasteAndFlush(x, y, overwrite)", "Pastes the clipboard with (x, y) as its top-left corner, then empties the memory and drops the selection rectangle, for instance %0.", "PasteAndFlush(10, 10, true)"),
                helpLine("Rotate(N)", "Turns the clipboard N quarter turns clockwise (negative: counter-clockwise), for instance %0.", "Rotate(1)"),
                helpLine("FlipX()", "Mirrors the clipboard left/right, for instance %0.", "FlipX()"),
                helpLine("FlipY()", "Mirrors the clipboard top/bottom, for instance %0.", "FlipY()"),
                helpLine("AllowToricSelection(enabled)", "Turns the Toric ? switch of the edit bar on (true) or off (false), like the switch itself, for instance %0.", "AllowToricSelection(true)"),
                helpLine("AllowOverwriting(enabled)", "Turns the Overwrite ? switch of the edit bar on (true) or off (false), like the switch itself, for instance %0.", "AllowOverwriting(true)"),
            ),
            fullWidth = true, striped = true
        ),
        p(page.i18n("The grid commands work on the current selection and on the clipboard of the simulation view, not on a cell. They are applied at the end of the step, in the order of the formula: a selection or a clipboard built by a modifier can then be used by hand. Copy, Cut, Paste, PasteAndFlush, Rotate, FlipX and FlipY are ignored when the selection or the clipboard they need is empty. Four commands end an operation and drop the selection rectangle as well: EraseSelection() destroys the agents of the selected zone, then deselects; FlushSelection() empties the memory, so the copied or cut selection is forgotten, and deselects; PasteAndFlush() pastes, then empties the memory and deselects. Unselect() does only that: it drops the rectangle, without touching the grid or the memory.")),
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
