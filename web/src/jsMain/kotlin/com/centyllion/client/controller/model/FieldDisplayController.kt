package com.centyllion.client.controller.model

import bulma.*
import bulma.extension.Switch
import com.centyllion.client.controller.utils.DeleteCallbackProperty
import com.centyllion.client.page.BulmaPage
import com.centyllion.model.Field
import kotlin.properties.Delegates.observable

open class FieldDisplayController(field: Field, val page: BulmaPage): NoContextController<Field, Box>() {

    override var data: Field by observable(field) { _, old, new ->
        if (old != new) refresh()
    }

    override var readOnly by observable(false) { _, old, new ->
        if (old != new) deleteCallbackProperty.readOnly = new
    }

    /** Visibility toggle callback (wired by the ModelController to the 3D view). */
    var onVisibilityChange: ((Boolean) -> Unit)? = null

    /** True when the app context does not hide this field. */
    private fun isVisible(): Boolean = data.id !in page.appContext.hiddenFieldIds

    /**
     * Switch left of the field icon: off hides this field in the simulation view.
     *
     * Its state is re-read in [refresh]: the hidden ids are a plain mutable set, not an
     * observable, so a change made elsewhere (the visibility callback of the ModelController)
     * would otherwise leave this switch on a stale value.
     */
    val visibilitySwitch = Switch(
        text = "", checked = isVisible(), size = Size.Small
    ) { _, value -> onVisibilityChange?.invoke(value) }

    /** Caption left of the switch: "Show ?" in both languages. */
    private val visibilityLabel = Label(page.i18n("Show ?")).apply {
        root.classList.add("is-size-7")
        root.style.marginBottom = "0"
        root.style.marginRight = "0.4rem"
    }

    /** Caption and switch on one line, so the row stays compact. */
    val visibilityControl = Div(visibilityLabel, visibilitySwitch).apply {
        root.classList.add("is-flex", "is-align-items-center")
        visibilitySwitch.root.style.marginBottom = "0"
        // clicking the caption toggles the switch, like a native <label for>
        visibilitySwitch.root.querySelector("input")?.let { input ->
            visibilityLabel.root.setAttribute("for", input.id)
        }
    }

    val icon = Icon("square-full").apply {
        root.style.color = data.color
    }

    val titleLabel = Label("${field.name} (${field.id})")
    val invisibleIcon = Icon("eye-slash", color = TextColor.Primary)
    val movingIcon = Icon("walking", color = TextColor.Primary)
    val deathIcon = Icon("skull-crossbones", color = TextColor.Primary)

    val status = Span(invisibleIcon, movingIcon, deathIcon)

    val body = Level(center = listOf(visibilityControl, icon, titleLabel, status), mobile = true)

    val deleteCallbackProperty = DeleteCallbackProperty(null, this) { old, new ->
        old?.let { body.right -= it }
        new?.let { body.right += it }
    }
    var onDelete by deleteCallbackProperty

    override val container = Box(body)

    override fun refresh() {
        // the visibility can also be changed by a meta-action, not only by this switch
        visibilitySwitch.checked = isVisible()
        icon.root.style.color = data.color
        titleLabel.text = "${data.name} (${data.id})"
        invisibleIcon.hidden = !data.invisible
        movingIcon.hidden = data.speed <= 0.0
        deathIcon.hidden = data.halfLife <= 0
        status.hidden = invisibleIcon.hidden && movingIcon.hidden && deathIcon.hidden
        movingIcon.root.style.opacity = "${data.speed.coerceAtLeast(0.3f)}"
        deathIcon.root.style.opacity = "${(101 - data.halfLife.coerceAtMost(71)) / 100.0}"
    }

}
