package com.centyllion.client.controller.model

import bulma.*
import com.centyllion.client.controller.utils.EditableStringController
import com.centyllion.client.controller.utils.blockTitle
import com.centyllion.client.controller.utils.createHr
import com.centyllion.client.controller.utils.editableDoubleController
import com.centyllion.client.controller.utils.editorBox
import com.centyllion.client.page.BulmaPage
import com.centyllion.model.*
import com.centyllion.model.Field
import kotlin.properties.Delegates.observable
import bulma.Field as BField

class BehaviourEditController(
    initialData: Behaviour, model: GrainModel, val page: BulmaPage, expertMode: Boolean,
    var onUpdate: (old: Behaviour, new: Behaviour, controller: BehaviourEditController) -> Unit =
        { _, _, _ -> }
) : Controller<Behaviour, GrainModel, Div> {

    override var data: Behaviour by observable(initialData) { _, old, new ->
        if (old != new) {
            nameController.data = new.name
            descriptionController.data = new.description
            probabilityController.data = "${new.probability}"
            agePredicatesController.data = new.agePredicates
            formulaController.data = new.formula
            actionsController.data = new.actions
            mainReactiveController.data = context.grainForId(new.mainReactiveId)
            mainDirection.context = null to context.grainForId(new.mainReactiveId)?.color
            mainProductController.data = context.grainForId(new.mainProductId)
            sourceReactiveController.data = new.sourceReactive
            sourceReactiveController.context = new to context
            reactionsController.data = new.reaction
            reactionsController.context = new to context
            fieldPredicatesController.data = new.fieldPredicates
            countPredicatesController.data = new.agentCountPredicates
            fieldInfluencesController.data = context.fields.map { it.id to (new.fieldInfluences[it.id] ?: 0f) }
            onUpdate(old, new, this@BehaviourEditController)
            refresh()
        }
    }

    override var context: GrainModel by observable(model) { _, old, new ->
        if (old != new) {
            mainReactiveController.data = context.grainForId(data.mainReactiveId)
            mainReactiveController.context = new.grains
            mainProductController.data = context.grainForId(data.mainProductId)
            mainProductController.context = new.grains
            sourceReactiveController.context = data to context
            reactionsController.context = data to context
            fieldPredicatesController.context = context.fields
            countPredicatesController.context = context.grains
            fieldInfluencesController.context = context.fields
            fieldInfluencesController.data = context.fields.map { it.id to (data.fieldInfluences[it.id] ?: 0f) }
            refresh()
        }
    }

    /** True when at least one graphical predicate is configured on this behaviour. */
    fun hasGraphicalPredicates(): Boolean =
        data.agePredicates.isNotEmpty() || data.agentCountPredicates.isNotEmpty() ||
                data.fieldPredicates.isNotEmpty() || !data.hasDefaultAgePredicate

    /** True when those predicates have an exact formula equivalent. */
    fun canConvertPredicates(): Boolean = BehaviourPredicates.convertible(data)

    /** Drops the graphical predicates; an already written formula is left untouched. */
    fun clearGraphicalPredicates() {
        data = data.copy(
            agePredicate = Predicate(Operator.GreaterThanOrEquals, 0),
            agePredicates = emptyList(),
            agentCountPredicates = emptyList(),
            fieldPredicates = emptyList(),
        )
    }

    /** Rewrites the graphical predicates as a formula (ANDed with an existing one), then drops them. */
    fun convertPredicatesToFormula() {
        if (!canConvertPredicates()) return
        val formula = BehaviourPredicates.toFormula(data)
        if (formula.isBlank()) return
        data = data.copy(
            formula = listOf(data.formula.trim(), formula).filter { it.isNotBlank() }.joinToString(" && "),
            agePredicate = Predicate(Operator.GreaterThanOrEquals, 0),
            agePredicates = emptyList(),
            agentCountPredicates = emptyList(),
            fieldPredicates = emptyList(),
        )
    }

    var expertMode: Boolean by observable(expertMode) { _, old, new ->
        if (old != new) {
            refresh()
        }
    }

    override var readOnly: Boolean by observable(false) { _, old, new ->
        if (old != new) {
            addReactionButton.invisible = new
            addAgePredicateButton.invisible = new
            addFieldPredicateButton.invisible = new
            addCountPredicateButton.invisible = new
            nameController.readOnly = new
            descriptionController.readOnly = new
            probabilityController.readOnly = new
            agePredicatesController.readOnly = new
            formulaController.readOnly = new
            actionsController.readOnly = new
            mainReactiveController.readOnly = new
            mainProductController.readOnly = new
            sourceReactiveController.readOnly = new
            reactionsController.readOnly = new
            reactionEditTile.hidden = new
            fieldPredicatesController.readOnly = new
            countPredicatesController.readOnly = new
            fieldInfluencesController.readOnly = new
        }
    }

    val nameController = EditableStringController(data.name, page.i18n("Name"))
    { _, new, _ ->
        this.data = this.data.copy(name = new)
    }

    val descriptionController = EditableStringController(data.description, page.i18n("Description"))
    { _, new, _ ->
        this.data = this.data.copy(description = new)
    }

    val probabilityController = editableDoubleController(
        locale = page.appContext.locale,
        initialData = data.probability,
        placeHolder = page.i18n("Speed"),
        minValue = 0.0,
        maxValue = 1.0,
        onUpdate = { _, new, _ ->
            this.data = this.data.copy(probability = new)
        }
    )

    val formulaController = EditableStringController(
        initialData = data.formula,
        placeHolder = page.i18n("Predicate (e.g. count(1) > 2 && count(2) == 1)"),
        columns = 40,
        isValid = { value -> if (value.isBlank()) null else validatePredicateFormula(value) },
        onUpdate = { _, new, _ ->
            data = data.copy(formula = new)
        }
    )

    val actionsController = EditableStringController(
        initialData = data.actions,
        placeHolder = page.i18n("Actions (e.g. Add(0, 1, 0), Remove(1, 0, 0))"),
        columns = 40,
        isValid = { value -> if (value.isBlank()) null else validateActionFormula(value) },
        onUpdate = { _, new, _ ->
            data = data.copy(actions = new)
        }
    )

    // ---- contextual help of the three graphical predicates (normal mode) ----

    private fun helpButton(title: String, sections: List<BulmaElement>) = Button(
        page.i18n("Help"), size = Size.Small, color = ElementColor.Info
    ) {
        page.modalDialog(page.i18n(title), sections, Button(page.i18n("Close"), color = ElementColor.Primary) {})
    }

    val agePredicateHelp = Section(
        p(page.i18n("Each line is a condition on the age of the agent at the current step: 1 at the agent's first step, 0 for a spontaneous generation (there is no agent yet).")),
        p(page.i18n("All the lines must be true at the same time (AND). They are also ANDed with the field thresholds, with the neighbour counts and, in expert mode, with the predicate formula.")),
        SubTitle(page.i18n("Examples")),
        Table(
            body = listOf(
                helpLine(">= 10", "The agent must have existed for at least 10 steps"),
                helpLine(">= 10 then <= 20", "The behaviour applies only between the 10th and the 20th step of the agent"),
                helpLine("!= 5", "Every age except 5"),
            ),
            fullWidth = true, striped = true
        )
    )

    val fieldThresholdsHelp = Section(
        p(page.i18n("Each line compares the value of a field on the behaviour cell (between 0 and 1, read at the start of the step) with a threshold.")),
        p(page.i18n("All the lines must be true at the same time (AND). They are also ANDed with the simulation age lines, with the neighbour counts and, in expert mode, with the predicate formula.")),
        SubTitle(page.i18n("Example")),
        Table(
            body = listOf(
                helpLine("sugar > 0.3", "The behaviour applies only if the sugar field on the cell is above 0.3"),
            ),
            fullWidth = true, striped = true
        )
    )

    val countPredicatesHelp = Section(
        p(page.i18n("Each line counts the neighbours of the chosen type among the 8 adjacent cells (0 to 8) and compares that count with the value.")),
        p(page.i18n("'All agents' counts every occupied neighbour cell; a chosen agent type counts only that type (same as count(grainId) in a formula).")),
        p(page.i18n("All the lines must be true at the same time (AND), like the simulation age lines and the field thresholds.")),
        SubTitle(page.i18n("Example")),
        Table(
            body = listOf(
                helpLine("Nb A >= 3", "At least 3 neighbours of type A"),
                helpLine("Nb all agents == 0", "No occupied neighbour cell at all"),
            ),
            fullWidth = true, striped = true
        )
    )

    val addAgePredicateButton = iconButton(Icon("plus", Size.Small), ElementColor.Primary, true, size = Size.Small) {
        val predicate = Predicate(Operator.GreaterThanOrEquals, 0)
        this.data = data.copy(agePredicates = data.agePredicates + predicate)
    }

    val agePredicatesController = noContextColumnsController(data.agePredicates) { predicate, previous ->
        previous ?: AgePredicateLineController(
            page.appContext.locale, predicate,
            { old, new, _ -> if (old != new) this.data = data.updateAgePredicate(old, new) },
            { delete, _ -> this.data = data.dropAgePredicate(delete) }
        )
    }

    val agePredicatesGroup = Column(
        Level(
            left = listOf(Label(page.i18n("Simulation age"))),
            right = listOf(helpButton("Simulation age", listOf(agePredicateHelp)), addAgePredicateButton)
        ),
        agePredicatesController,
        size = ColumnSize.Full
    ).apply { hidden = expertMode }

    fun helpLine(name: String, description: String) = TableRow(
        TableCell(name).apply { root.classList.add("has-text-weight-bold") },
        TableCell(page.i18n(description)),
    )

    val formulaHelp = Section(
        p(page.i18n("Evaluated for each agent that could apply the behaviour.")),
        p(page.i18n("The formula must be a logical predicate returning true or false. Any other value (e.g. sin(step)) makes the behaviour never applicable.")),
        p(page.i18n("The graphical predicates entered in normal mode (simulation age lines, field thresholds and neighbour counts) are also evaluated: the behaviour applies only if they are all true AND the formula is true. Remove the graphical lines to let the formula alone decide.")),
        SubTitle(page.i18n("Formula Parameters")),
        Table(
            body = listOf(
                helpLine("step", "Current simulation step"),
                helpLine("age(n)", "Age of reactive n (1 = main agent, 2 to N+1 = neighbours of the reactions, same numbering as age inheritance)"),
                helpLine("x, y, z", "Position of the cell"),
            ),
            fullWidth = true, striped = true
        ),
        SubTitle(page.i18n("Model functions")),
        Table(
            body = listOf(
                helpLine("count(grainId)", "Number of neighbour agents with the given id (8 adjacent cells)"),
                helpLine("field(id)", "Value of field id at the current cell"),
                helpLine("field(id, dx, dy)", "Value of field id at offset (dx, dy) from the current cell (e.g. 1, 0 = east neighbour)"),
                helpLine("agent(x, y)", "Agent id at position (x, y), -1 if none"),
            ),
            fullWidth = true, striped = true
        ),
        SubTitle(page.i18n("Operators and functions")),
        Table(
            body = listOf(
                helpLine("+, -, *, /", "Mathematical operators"),
                helpLine("%", "Modulo: returns the remainder of a division, after one number is divided by another"),
                helpLine("^", "Exponentiation: a^b means a raised to the power of b"),
                helpLine("&&, ||, !", "Logical 'and', 'or', 'not' operators"),
                helpLine("==, !=", "Equality operators"),
                helpLine("<, >, <=, >=", "Comparison operators"),
                helpLine("a ? b : c", "If else ternary operator"),
                helpLine("pi, e", "PI and E constants"),
                helpLine("abs(x)", "Absolute value"),
                helpLine("cos(x), sin(x), tan(x)", "Trigonometry functions (also asin, acos, atan)"),
                helpLine("floor(x), ceil(x), round(x)", "Floor, ceil and round functions"),
                helpLine("ln(x), log(x, base)", "Logarithmic functions"),
                helpLine("min(a1, ..., an), max(a1, ..., an)", "Min and max functions"),
            ),
            fullWidth = true, striped = true
        ),
        SubTitle(page.i18n("Examples")),
        Table(
            body = listOf(
                helpLine("count(0) >= 3", "At least 3 neighbours of agent 0"),
                helpLine("count(0) >= 3 && count(0) < 5", "Between 3 and 4 neighbours of agent 0"),
                helpLine("age(1) >= 10 && count(1) == 0", "Age at least 10 and no neighbour of agent 1"),
                helpLine("count(0) > 3 && field(1) < 0.5", "At least 4 neighbours of agent 0 and field 1 below 0.5 on the cell"),
            ),
            fullWidth = true, striped = true
        )
    )

    val formulaLine = HorizontalField(
        Label(page.i18n("Predicate")),
        BField(Control(Button(page.i18n("Help"), size = Size.Small, color = ElementColor.Info) {
            page.modalDialog(
                page.i18n("Predicate"),
                listOf(formulaHelp),
                Button(page.i18n("Close"), color = ElementColor.Primary) {}
            )
        })),
        formulaController.container
    ).apply { hidden = !expertMode }

    val actionsHelp = Section(
        p(page.i18n("Actions are executed when the behaviour is applied, after the reactions.")),
        p(page.i18n("Offsets are relative to the behaviour cell and wrap around the grid (toroidal).")),
        SubTitle(page.i18n("Actions")),
        Table(
            body = listOf(
                helpLine("Add(id, dx, dy)", "Adds an agent of type id at offset (dx, dy) from the behaviour cell (only if the target cell is empty)"),
                helpLine("Remove(id, dx, dy)", "Removes the agent of type id at offset (dx, dy) from the behaviour cell"),
                helpLine("Move(id, dx1, dy1, dx2, dy2)", "Moves the agent of type id from offset (dx1, dy1) to offset (dx2, dy2) of the behaviour cell (empty target or same type)"),
                helpLine("SetField(id, dx, dy, value)", "Sets the value of field id at offset (dx, dy) to value (clamped between 0 and 1)"),
                helpLine("AddField(id, dx, dy, delta)", "Adds delta to the value of field id at offset (dx, dy) (result clamped between 0 and 1)"),
                helpLine("ClearField(id, dx, dy)", "Sets the value of field id at offset (dx, dy) to 0"),
            ),
            fullWidth = true, striped = true
        ),
        SubTitle(page.i18n("Arguments")),
        Table(
            body = listOf(
                helpLine("step", "Current simulation step"),
                helpLine("age(n)", "Age of reactive n (1 = main agent, 2 to N+1 = neighbours of the reactions, same numbering as age inheritance)"),
                helpLine("x, y, z", "Position of the behaviour cell"),
            ),
            fullWidth = true, striped = true
        ),
        p(page.i18n("Variables, count(grainId) and field(id, dx, dy) can also be used in the arguments.")),
        SubTitle(page.i18n("Order of reactions and actions")).apply {
            // the modal CSS cancels the subtitle's top margin: restore the line break
            root.style.marginTop = "1.5rem"
        },
        p(page.i18n("Within a single behaviour, the reactions are applied first, then the actions. Within the actions, the order matters: successive actions are applied one after the other, each seeing the effect of the previous ones. Note that count(id) is evaluated on the state at the beginning of the step, before the reactions and the actions are applied.")),
        p(page.i18n("Example: behaviour with an empty central cell and a B neighbour to the left at (-1,0):")),
        Table(
            body = listOf(
                helpLine("None -> A", page.i18n("main reaction, on the empty central cell")),
                helpLine("B -> B", page.i18n("reaction, B allowed only to the left at (-1,0)")),
                helpLine("Remove(0,0,0), Remove(1,-1,0), Add(0,0,0)", page.i18n("actions, in this order")),
            ),
            fullWidth = true, striped = true
        ),
        p(page.i18n("Application (if the behaviour is selected):")),
        Table(
            body = listOf(
                helpLine("1. None -> A", page.i18n("centre becomes A (main reaction)")),
                helpLine("2. B -> B", page.i18n("B at (-1,0) stays B (identity)")),
                helpLine("3. Remove(0,0,0)", page.i18n("removes the A from the centre (placed by the main reaction) -> empty cell")),
                helpLine("4. Remove(1,-1,0)", page.i18n("removes the B at (-1,0) -> empty")),
                helpLine("5. Add(0,0,0)", page.i18n("places A in the centre (empty) -> A")),
            ),
            fullWidth = true, striped = true
        ),
        p(page.i18n("Net result: centre = A, B on the left removed. Remove(0,0,0) has an effect only because it runs after the main reaction (which places the A) and before the Add: if the Add had come before the Remove, the centre would end up empty.")),
    )

    val actionsLine = HorizontalField(
        Label(page.i18n("Action list")),
        BField(Control(Button(page.i18n("Help"), size = Size.Small, color = ElementColor.Info) {
            page.modalDialog(
                page.i18n("Actions"),
                listOf(actionsHelp),
                Button(page.i18n("Close"), color = ElementColor.Primary) {}
            )
        })),
        actionsController.container
    ).apply { hidden = !expertMode }


    val mainReactiveController = GrainSelectController(
        context.grainForId(data.mainReactiveId), context.grains, page, allowNone = true
    ) { _, new, _ -> this.data = this.data.copy(mainReactiveId = new?.id ?: -1) }

    val mainProductController = GrainSelectController(context.grainForId(data.mainProductId), context.grains, page)
    { _, new, _ ->
        this.data = this.data.copy(mainProductId = new?.id ?: -1)
    }

    val sourceReactiveController = SourceReactiveSelectController(
        index = data.sourceReactive,
        behaviour = data,
        model = context,
        page = page,
        onUpdate = { _, new, _ ->
            this.data = this.data.copy(sourceReactive = new)
        }
    )

    val addReactionButton = iconButton(Icon("plus", Size.Small), ElementColor.Primary, true, size = Size.Small) {
        val newReaction = Reaction()
        this.data = data.copy(reaction = data.reaction + newReaction)
    }

    val mainDirection = DirectionController(
        initial = emptySet(),
        initialContext = null to context.grainForId(data.mainReactiveId)?.color,
        readOnly = true
    )

    val sourceHeader = Column(Help(page.i18n("Sources")), size = ColumnSize.S2)

    val reactionHeader = listOf(
        // Header
        Column(
            Columns(
                Column(p(page.i18n("Reactives")), size = ColumnSize.S4),
                Column(p(page.i18n("Directions")), size = ColumnSize.S1).apply { root.classList.add("has-text-centered") },
                Column(p(page.i18n("Products")), size = ColumnSize.S4),
                sourceHeader,
                Column(size = ColumnSize.S1)
            ),
            size = ColumnSize.Full
        ),
        // Main reactive
        Column(
            Columns(
                Column(mainReactiveController, size = ColumnSize.S4),
                Column(mainDirection, size = ColumnSize.S1).apply { root.classList.add("has-text-centered") },
                Column(mainProductController, size = ColumnSize.S4),
                Column(sourceReactiveController, size = ColumnSize.S2),
                Column(addReactionButton, size = ColumnSize.S1).apply { root.classList.add("has-text-right") }
            ),
            size = ColumnSize.Full
        )
    )

    val reactionEditTile = TileParent(size = TileSize.S1, vertical = true)


    val reactionsController = columnsController(
        initialList = data.reaction,
        initialContext = data to context,
        header = reactionHeader,
        footer = emptyList(),
        controllerBuilder = { reaction, previous ->
            previous ?: ReactionEditController(reaction, data, context, page).also {
                it.onUpdate = { old, new, _ ->
                    data = data.updateReaction(old, new)
                }
                it.onDelete = { delete, _ ->
                    data = data.dropReaction(delete)
                }
            }
        },
    )
    /*
    val reactionsController = MultipleController<Reaction, Pair<Behaviour, GrainModel>, TileAncestor, TileParent, ReactionEditController>(
        initialList = data.reaction,
        initialContext = data to context,
        header = reactionHeader,
        footer = emptyList(),
        container = TileAncestor( *Array(5) { if (it == 4) reactionEditTile else TileParent(vertical = true) } ),
        onClick = null,
        controllerBuilder = { reaction, previous ->
            previous ?: ReactionEditController(reaction, data, context, page).also {
                it.onUpdate = { old, new, _ ->
                    data = data.updateReaction(old, new)
                }
                it.onDelete = { delete, _ ->
                    data = data.dropReaction(delete)
                }
            }
        },
        updateParent = { parent, items ->
            parent.body.forEachIndexed { index, tileInner ->
                (tileInner as TileParent).body = items.map { it.body[index] }
            }
        }
    )
    */

    val addFieldPredicateButton = iconButton(Icon("plus", Size.Small), ElementColor.Primary, true, size = Size.Small) {
        val predicate = context.fields.first().id to Predicate(Operator.GreaterThan, 0f)
        this.data = data.copy(fieldPredicates = data.fieldPredicates + predicate)
    }

    val fieldPredicatesController: MultipleController<
            Pair<Int, Predicate<Float>>, List<Field>, Columns, Column, FieldPredicateController
    > = columnsController(data.fieldPredicates, context.fields) { pair, previous ->
            previous ?: FieldPredicateController(page.appContext.locale, pair, context.fields,
            { old, new, _ -> if (old != new) this.data = data.updateFieldPredicate(old, new) },
            { delete, _ -> this.data = data.dropFieldPredicate(delete) })
        }

    val fieldInfluencesController =
        columnsController(context.fields.map { it.id to (data.fieldInfluences[it.id] ?: 0f) }, context.fields) { pair, previous ->
            previous ?: FieldChangeController(pair, context.fields) { old, new, _ ->
                if (old != new) {
                    this.data = data.updateFieldInfluence(new.first, new.second)
                }
            }
        }

    val addCountPredicateButton = iconButton(Icon("plus", Size.Small), ElementColor.Primary, true, size = Size.Small) {
        val predicate = AgentCountPredicate(grainId = -1, op = Operator.GreaterThan, count = 0)
        this.data = data.copy(agentCountPredicates = data.agentCountPredicates + predicate)
    }

    val countPredicatesController: MultipleController<AgentCountPredicate, List<Grain>, Columns, Column, CountPredicateController> =
        columnsController(data.agentCountPredicates, context.grains) { predicate, previous ->
            previous ?: CountPredicateController(
                page.appContext.locale, predicate, context.grains,
                { old, new, _ -> if (old != new) this.data = data.updateAgentCountPredicate(old, new) },
                { delete, _ -> this.data = data.dropAgentCountPredicate(delete) }
            )
        }

    val countPredicatesGroup = Column(
        Level(
            left = listOf(Label(page.i18n("Nb Neighbours"))),
            right = listOf(helpButton("Nb Neighbours", listOf(countPredicatesHelp)), addCountPredicateButton)
        ),
        countPredicatesController,
        size = ColumnSize.Full
    ).apply { hidden = expertMode }

    val fieldThresholdsGroup = Column(
        Level(
            left = listOf(Label(page.i18n("Field thresholds"))),
            right = listOf(helpButton("Field thresholds", listOf(fieldThresholdsHelp)), addFieldPredicateButton)
        ),
        fieldPredicatesController,
        size = ColumnSize.Full
    ).apply { hidden = expertMode || context.fields.isEmpty() }

    val fieldInfluencesGroup = Column(
        Label(page.i18n("Field influences")),
        fieldInfluencesController,
        size = ColumnSize.Full
    ).apply { hidden = context.fields.isEmpty() }

    override val container = editorBox(page.i18n("Behaviour"), behaviourIcon,
        // top block: information
        blockTitle(page.i18n("Misc")),
        HorizontalField(Label(page.i18n("Name")), nameController.container),
        HorizontalField(Label(page.i18n("Description")), descriptionController.container),
        // middle block: predicates (graphical in normal mode, formula in expert mode)
        HtmlWrapper(createHr().also { it.classList.add("centy-block-separator") }),
        blockTitle(page.i18n("Predicates")),
        agePredicatesGroup,
        fieldThresholdsGroup,
        countPredicatesGroup,
        formulaLine,
        // bottom block: speed, field influences, reactions and actions (expert mode)
        HtmlWrapper(createHr().also { it.classList.add("centy-block-separator") }),
        blockTitle("Reactions"),
        HorizontalField(Label(page.i18n("Speed")), probabilityController.container),
        fieldInfluencesGroup,
        Label(page.i18n("Reactions")), reactionsController,
        actionsLine
    )

    init {
        // A single age predicate written by an older version becomes the first
        // line of the "simulation age" list, so every line is stored and edited
        // the same way. Idempotent: nothing happens once migrated.
        val migrated = data.migrateLegacyAgePredicate()
        if (migrated != data) this.data = migrated
    }

    override fun refresh() {
        addReactionButton.disabled = data.reaction.size >= 8

        nameController.refresh()
        descriptionController.refresh()
        probabilityController.refresh()
        agePredicatesController.refresh()
        formulaController.refresh()
        actionsController.refresh()
        mainReactiveController.refresh()
        mainProductController.refresh()
        reactionsController.refresh()

        fieldPredicatesController.refresh()
        fieldInfluencesController.refresh()
        countPredicatesController.refresh()

        // mode-dependent visibility
        agePredicatesGroup.hidden = expertMode
        formulaLine.hidden = !expertMode
        actionsLine.hidden = !expertMode
        countPredicatesGroup.hidden = expertMode
        fieldThresholdsGroup.hidden = expertMode || context.fields.isEmpty()
        fieldInfluencesGroup.hidden = context.fields.isEmpty()
    }

}
