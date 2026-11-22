package com.centyllion.client.controller.model

import bulma.*
import com.centyllion.i18n.Locale
import com.centyllion.model.AgentCountPredicate
import com.centyllion.model.COUNT_ALL_AGENTS
import com.centyllion.model.COUNT_EMPTY_CELLS
import com.centyllion.model.Grain
import com.centyllion.model.Operator
import kotlinx.browser.window
import org.w3c.dom.events.Event
import kotlin.properties.Delegates.observable

/**
 * One "number of neighbours" predicate line: [Nb] [grain] [relation] [value 0..8] [delete].
 *
 * The predicate counts neighbour cells (8 directions). The grain drop-down selects
 * which agents are counted: "all agents" ([AgentCountPredicate.grainId] < 0) or one
 * grain only (the equivalent of `count(grainId)` in a predicate formula).
 * The value is bounded between 0 and 8.
 */
class CountPredicateController(
    private val locale: Locale, value: AgentCountPredicate, grains: List<Grain>,
    var onUpdate: (old: AgentCountPredicate, new: AgentCountPredicate, controller: CountPredicateController) -> Unit =
        { _, _, _ -> },
    var onDelete: (AgentCountPredicate, controller: CountPredicateController) -> Unit = { _, _ -> }
) : Controller<AgentCountPredicate, List<Grain>, Column> {

    /** label used when every grain is counted (grainId = -1) */
    private val allAgentsLabel: String = locale.i18n("All agents")

    /** label used when the empty neighbour cells are counted (grainId = -2) */
    private val noAgentLabel: String = locale.i18n("None")

    override var data: AgentCountPredicate by observable(value) { _, old, new ->
        if (old != new) {
            onUpdate(old, new, this@CountPredicateController)
            refresh()
        }
    }

    override var context: List<Grain> by observable(grains) { _, old, new ->
        if (old != new) {
            grainDropdown.items = grainItems()
            refresh()
        }
    }

    override var readOnly: Boolean by observable(false) { _, old, new ->
        if (old != new) {
            grainDropdown.disabled = new
            opSelect.disabled = new
            valueSelect.disabled = new
            delete.invisible = new
        }
    }

    val label = Label(locale.i18n("Nb"))

    val grainIcon = Icon(grainIconName(data.grainId)).apply {
        root.style.color = grainColor(data.grainId)
    }

    val grainDropdown: Dropdown = Dropdown(
        text = grainLabel(data.grainId), icon = grainIcon, rounded = true
    ).apply { items = grainItems() }

    val opSelect = Select(Operator.entries.map { Option(it.label, it.name) }, rounded = true) { _: Event, value: List<Option> ->
        value.firstOrNull()?.value?.let {
            data = data.copy(op = Operator.valueOf(it))
        }
    }

    val valueSelect = Select((0..8).map { Option("$it", "$it") }, rounded = true) { _: Event, value: List<Option> ->
        value.firstOrNull()?.value?.toIntOrNull()?.let {
            data = data.copy(count = it)
        }
    }

    val delete = iconButton(Icon("times", Size.Small), ElementColor.Danger, true, size = Size.Small)
    {
        onDelete(this.data, this@CountPredicateController)
    }

    override val container = Column(
        Level(
            left = listOf(label),
            center = listOf(Field(Control(grainDropdown), Control(opSelect), Control(valueSelect), addons = true)),
            right = listOf(delete)
        ),
        size = ColumnSize.Full
    )

    private fun grainFor(grainId: Int): Grain? = context.find { it.id == grainId }

    private fun grainLabel(grainId: Int): String = when {
        grainId == COUNT_EMPTY_CELLS -> noAgentLabel
        grainId < 0 -> allAgentsLabel
        else -> grainFor(grainId)?.label() ?: allAgentsLabel
    }

    private fun grainIconName(grainId: Int): String = when {
        grainId == COUNT_EMPTY_CELLS -> "times-circle"
        grainId < 0 -> "users"
        else -> grainFor(grainId)?.icon ?: "users"
    }

    private fun grainColor(grainId: Int): String =
        if (grainId < 0) "" else grainFor(grainId)?.color ?: ""

    private fun item(grainId: Int, label: String, icon: String, color: String): DropdownSimpleItem {
        val dropdownIcon = Icon(icon)
        dropdownIcon.root.style.color = color
        return DropdownSimpleItem(label, dropdownIcon) {
            this.data = data.copy(grainId = grainId)
            this.grainDropdown.toggleDropdown()
        }
    }

    private fun grainItems(): List<DropdownSimpleItem> =
        listOf(
            item(COUNT_ALL_AGENTS, allAgentsLabel, "users", ""),
            item(COUNT_EMPTY_CELLS, noAgentLabel, "times-circle", ""),
        ) + context.map { item(it.id, it.label(), it.icon, it.color) }

    init {
        refresh()
    }

    override fun refresh() {
        grainDropdown.text = grainLabel(data.grainId)
        grainIcon.icon = grainIconName(data.grainId)
        grainIcon.root.style.color = grainColor(data.grainId)
        window.setTimeout({
            opSelect.selectedIndex = Operator.entries.indexOf(data.op)
            valueSelect.selectedIndex = data.count.coerceIn(0, 8)
        }, 0)
    }

}
