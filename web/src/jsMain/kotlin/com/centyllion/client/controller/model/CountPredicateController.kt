package com.centyllion.client.controller.model

import bulma.*
import com.centyllion.i18n.Locale
import com.centyllion.model.AgentCountPredicate
import com.centyllion.model.Operator
import kotlinx.browser.window
import org.w3c.dom.events.Event
import kotlin.properties.Delegates.observable

/**
 * One "number of neighbours" predicate line: [Nb] [relation] [value 0..8] [delete].
 * The predicate counts occupied neighbour cells (any agent) among the 8 directions,
 * so the value is bounded between 0 and 8.
 */
class CountPredicateController(
    locale: Locale, value: AgentCountPredicate,
    var onUpdate: (old: AgentCountPredicate, new: AgentCountPredicate, controller: CountPredicateController) -> Unit =
        { _, _, _ -> },
    var onDelete: (AgentCountPredicate, controller: CountPredicateController) -> Unit = { _, _ -> }
) : NoContextController<AgentCountPredicate, Column>() {

    override var data by observable(value) { _, old, new ->
        if (old != new) {
            onUpdate(old, new, this@CountPredicateController)
            refresh()
        }
    }

    override var readOnly: Boolean by observable(false) { _, old, new ->
        if (old != new) {
            opSelect.disabled = new
            valueSelect.disabled = new
            delete.invisible = new
        }
    }

    val label = Label(locale.i18n("Nb"))

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
            center = listOf(Field(Control(opSelect), Control(valueSelect), addons = true)),
            right = listOf(delete)
        ),
        size = ColumnSize.Full
    )

    init {
        refresh()
    }

    override fun refresh() {
        window.setTimeout({
            opSelect.selectedIndex = Operator.entries.indexOf(data.op)
            valueSelect.selectedIndex = data.count.coerceIn(0, 8)
        }, 0)
    }

}
