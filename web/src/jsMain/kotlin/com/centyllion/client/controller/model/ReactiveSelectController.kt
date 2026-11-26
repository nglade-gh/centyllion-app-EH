package com.centyllion.client.controller.model

import bulma.*
import com.centyllion.client.page.BulmaPage
import com.centyllion.model.ANY_AGENT
import com.centyllion.model.Grain
import kotlin.properties.Delegates.observable

/**
 * Dropdown of the reactive of a reaction line, which is an id and not a grain reference.
 *
 * The three kinds of reactive are offered in this order: no agent (an empty neighbour cell,
 * where the product is then created), any agent ([ANY_AGENT], any occupied neighbour whatever
 * its grain, which the product replaces in place), then the grains of the model.
 *
 * A [GrainSelectController] cannot express this column: its value is a `Grain?`, so "any agent"
 * and "empty cell" would collapse into the same `null`. The product column keeps using
 * [GrainSelectController] on purpose — a product always names one precise grain, so "any agent"
 * is never offered there.
 */
class ReactiveSelectController(
    reactiveId: Int, grains: List<Grain>, val page: BulmaPage,
    var onUpdate: (old: Int, new: Int, controller: ReactiveSelectController) -> Unit = { _, _, _ -> }
) : Controller<Int, List<Grain>, Field> {

    override var data: Int by observable(reactiveId) { _, old, new ->
        if (old != new) {
            onUpdate(old, new, this@ReactiveSelectController)
            refresh()
        }
    }

    override var context: List<Grain> by observable(grains) { _, old, new ->
        if (old != new) {
            dropdown.items = items()
            this@ReactiveSelectController.refresh()
        }
    }

    override var readOnly: Boolean by observable(false) { _, old, new ->
        if (old != new) {
            dropdown.disabled = readOnly
        }
    }

    private fun grain(id: Int): Grain? = if (id < 0) null else context.firstOrNull { it.id == id }

    fun label(id: Int) = when (id) {
        ANY_AGENT -> page.i18n("Any agent")
        else -> grain(id)?.label() ?: page.i18n("none")
    }

    private fun iconName(id: Int) = when (id) {
        ANY_AGENT -> "asterisk"
        else -> grain(id)?.icon ?: "times-circle"
    }

    private fun iconColor(id: Int) = when {
        id == ANY_AGENT -> "#7a7a7a"
        else -> grain(id)?.color ?: ""
    }

    val icon = Icon(iconName(data)).apply { root.style.color = iconColor(data) }

    val dropdown: Dropdown = Dropdown(text = label(data), icon = icon, rounded = true).apply { items = items() }

    override val container: Field = Field(Control(dropdown))

    private fun item(id: Int): DropdownSimpleItem {
        val itemIcon = Icon(iconName(id))
        itemIcon.root.style.color = iconColor(id)
        return DropdownSimpleItem(label(id), itemIcon) {
            this.data = id
            this.dropdown.toggleDropdown()
        }
    }

    /** No agent, any agent, then the grains: the order requested for the reactive column. */
    fun items(): List<DropdownSimpleItem> = listOf(item(-1), item(ANY_AGENT)) + context.map { item(it.id) }

    override fun refresh() {
        dropdown.text = label(data)
        icon.icon = iconName(data)
        icon.root.style.color = iconColor(data)
    }
}
