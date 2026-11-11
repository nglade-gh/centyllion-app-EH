package com.centyllion.client.controller.model

import bulma.*
import com.centyllion.client.controller.utils.EditableStringController
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
            formulaController.data = new.formula
            mainReactiveController.data = context.grainForId(new.mainReactiveId)
            mainDirection.context = null to context.grainForId(new.mainReactiveId)?.color
            mainProductController.data = context.grainForId(new.mainProductId)
            sourceReactiveController.data = new.sourceReactive
            sourceReactiveController.context = new to context
            reactionsController.data = new.reaction
            reactionsController.context = new to context
            fieldPredicatesController.data = new.fieldPredicates
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
            fieldInfluencesController.context = context.fields
            fieldInfluencesController.data = context.fields.map { it.id to (data.fieldInfluences[it.id] ?: 0f) }
            refresh()
        }
    }

    var expertMode: Boolean by observable(expertMode) { _, old, new ->
        if (old != new) {
            sourceHeader.hidden = !new
            sourceReactiveController.hidden = !new
            reactionsController.dataControllers
                .filterIsInstance<ReactionEditController>()
                .forEach { it.expertMode = new }
        }
    }

    override var readOnly: Boolean by observable(false) { _, old, new ->
        if (old != new) {
            addReactionButton.invisible = new
            addFieldPredicateButton.invisible = new
            nameController.readOnly = new
            descriptionController.readOnly = new
            probabilityController.readOnly = new
            formulaController.readOnly = new
            mainReactiveController.readOnly = new
            mainProductController.readOnly = new
            sourceReactiveController.readOnly = new
            reactionsController.readOnly = new
            reactionEditTile.hidden = new
            fieldPredicatesController.readOnly = new
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
        placeHolder = page.i18n("Formula (e.g. count(1) > 2 && count(2) == 1)"),
        columns = 40,
        isValid = { value -> if (value.isBlank()) null else validatePredicateFormula(value) },
        onUpdate = { _, new, _ ->
            data = data.copy(formula = new)
        }
    )

    fun helpLine(name: String, description: String) = TableRow(
        TableCell(name).apply { root.classList.add("has-text-weight-bold") },
        TableCell(page.i18n(description)),
    )

    val formulaHelp = Section(
        p(page.i18n("Evaluated for each agent that could apply the behaviour.")),
        p(page.i18n("The formula must be a logical predicate returning true or false. Any other value (e.g. sin(step)) makes the behaviour never applicable.")),
        SubTitle(page.i18n("Formula Parameters")),
        Table(
            body = listOf(
                helpLine("step", "Age of the agent (current step for spontaneous generation)"),
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
                helpLine("step >= 10 && count(1) == 0", "Age at least 10 and no neighbour of agent 1"),
                helpLine("count(0) > 3 && field(1) < 0.5", "At least 4 neighbours of agent 0 and field 1 below 0.5 on the cell"),
            ),
            fullWidth = true, striped = true
        )
    )

    val formulaLine = HorizontalField(
        Label(page.i18n("Formula")),
        BField(Control(Button("", Icon("question-circle"), size = Size.Small, color = ElementColor.Info) {
            page.modalDialog(
                page.i18n("Formula"),
                listOf(formulaHelp),
                Button(page.i18n("Close"), color = ElementColor.Primary) {}
            )
        })),
        formulaController.container
    )


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
    ).apply { hidden = !expertMode }

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
        .apply { hidden = !expertMode }

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
            previous ?: ReactionEditController(reaction, data, context, page, expertMode).also {
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

    fun fieldSeparator() = HtmlWrapper(createHr()).apply {
        hidden = context.fields.isEmpty()
    }

    val fieldsConfiguration = Columns(
        Column(
            fieldSeparator().apply { root.style.marginTop = "0.2rem" },
            size = ColumnSize.Full
        ),
        Column(
            Level(
                left= listOf(Label(page.i18n("Field thresholds"))),
                right = listOf(addFieldPredicateButton)
            ),
            fieldPredicatesController,
            size = ColumnSize.Full
        ),
        Column(
            Label(page.i18n("Field influences")),
            fieldInfluencesController,
            size = ColumnSize.Full
        ),
        multiline = true
    ).apply { hidden = context.fields.isEmpty() }

    override val container = editorBox(page.i18n("Behaviour"), behaviourIcon,
        HorizontalField(Label(page.i18n("Name")), nameController.container),
        HorizontalField(Label(page.i18n("Description")), descriptionController.container),
        HorizontalField(Label(page.i18n("Speed")), probabilityController.container),
        formulaLine,
        HtmlWrapper(createHr()),
        Label(page.i18n("Reactions")), reactionsController,
        fieldsConfiguration,
    )

    override fun refresh() {
        addReactionButton.disabled = data.reaction.size >= 8

        nameController.refresh()
        descriptionController.refresh()
        probabilityController.refresh()
        formulaController.refresh()
        mainReactiveController.refresh()
        mainProductController.refresh()
        reactionsController.refresh()

        fieldPredicatesController.refresh()
        fieldInfluencesController.refresh()
        fieldsConfiguration.hidden = context.fields.isEmpty()
    }

}
