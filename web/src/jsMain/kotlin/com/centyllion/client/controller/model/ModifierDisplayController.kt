package com.centyllion.client.controller.model

import bulma.*
import com.centyllion.client.page.BulmaPage
import com.centyllion.model.*
import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.HTMLLabelElement
import org.w3c.dom.events.Event
import kotlin.properties.Delegates.observable

/**
 * Row of a simulation modifier in the explorer. Same architecture as the grains, the fields
 * and the behaviours: the controller root is a [Column], so the selection mark applied by the
 * editor (`is-selected`) works exactly as it does for the other objects.
 */
class ModifierDisplayController(
    val page: BulmaPage,
    modifier: SimulationModifier,
    model: GrainModel
) : Controller<SimulationModifier, GrainModel, Column> {

    var onDelete: () -> Unit = { }

    var onEnabledChange: (Boolean) -> Unit = { }

    override var data: SimulationModifier by observable(modifier) { _, old, new ->
        if (old != new) {
            titleLabel.text = "${new.name} (${new.id})"
            probabilityButton.text = new.probability.toString()
            enabledInput.checked = new.enabled
            refresh()
        }
    }

    override var context: GrainModel by observable(model) { _, old, new ->
        if (old != new) refresh()
    }

    override var readOnly: Boolean by observable(false) { _, _, new ->
        enabledInput.disabled = new
        deleteButton.disabled = new
    }

    /**
     * On/Off radio of the modifier. Native, because Bulma's Radio cannot be switched back by
     * clicking it, and because the run loop finds these inputs by their data attribute.
     */
    private val enabledInput: HTMLInputElement =
        (document.createElement("input") as HTMLInputElement).apply {
            type = "radio"
            name = "modifier-enabled-${modifier.uuid}"
            checked = modifier.enabled
            setAttribute("data-modifier-id", "${modifier.id}")
        }

    private val enabledToggle = HtmlWrapper(
        (document.createElement("label") as HTMLLabelElement).apply {
            classList.add("radio")
            appendChild(enabledInput)
        }
    )

    val titleLabel = Label("${modifier.name} (${modifier.id})")

    /** Probability, displayed like the behaviour probabilities. */
    val probabilityButton: Button = textButton(
        modifier.probability.toString(), rounded = true,
        color = ElementColor.Success, size = Size.Small
    )

    val deleteButton = iconButton(Icon("times", Size.Small), ElementColor.Danger, true, size = Size.Small) {
        onDelete()
    }

    init {
        enabledInput.addEventListener("click", { event: Event ->
            event.stopPropagation()
            val target = !data.enabled
            enabledInput.checked = target
            onEnabledChange(target)
        })
        deleteButton.root.addEventListener("click", { event: Event -> event.stopPropagation() })
    }

    override val container = Column(
        Box(
            Level(
                left = listOf(enabledToggle, titleLabel),
                right = listOf(probabilityButton, deleteButton),
                mobile = true
            )
        )
    )

    override fun refresh() {
        titleLabel.text = "${data.name} (${data.id})"
        probabilityButton.text = data.probability.toString()
        enabledInput.checked = data.enabled
    }
}
