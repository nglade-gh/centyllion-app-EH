package com.centyllion.client.controller.model

import bulma.*
import bulma.extension.Switch
import com.centyllion.client.controller.utils.DeleteCallbackProperty
import com.centyllion.client.page.BulmaPage
import com.centyllion.model.Grain
import com.centyllion.model.GrainModel
import com.centyllion.model.hasNoError
import kotlin.properties.Delegates.observable

class GrainDisplayController(val page: BulmaPage, grain: Grain, model: GrainModel): Controller<Grain, GrainModel, Box> {

    override var data: Grain by observable(grain) { _, old, new ->
        if (old != new) {
            errorIcon.hidden = data.hasNoError(context, page.appContext.locale)
            refresh()
        }
    }

    override var context: GrainModel by observable(model) { _, old, new ->
        if (old != new) {
            errorIcon.hidden = data.hasNoError(context, page.appContext.locale)
            refresh()
        }
    }

    override var readOnly by observable(false) { _, old, new ->
        if (old != new) deleteCallbackProperty.readOnly = new
    }

    val errorIcon = Icon("exclamation-triangle", color = TextColor.Danger).apply {
        hidden = data.hasNoError(context, page.appContext.locale)
    }

    /** Visibility toggle callback (wired by the ModelController to the 3D view). */
    var onVisibilityChange: ((Boolean) -> Unit)? = null

    /** True when the app context does not hide this grain type. */
    private fun isVisible(): Boolean = data.id !in page.appContext.hiddenGrainIds

    /**
     * Switch left of the grain icon: off hides this grain type in the simulation view.
     *
     * Its state is re-read in [refresh], because the hidden ids also change outside this
     * widget: a Show(id) / Hide(id) meta-action writes page.appContext.hiddenGrainIds directly.
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

    val icon = Icon(grain.icon).apply {
        root.style.color = data.color
    }

    val titleLabel = Label("${grain.name} (${grain.id})")
    val invisibleIcon = Icon("eye-slash", color = TextColor.Primary)
    val movingIcon = Icon("walking", color = TextColor.Primary)
    val deathIcon = Icon("skull-crossbones", color = TextColor.Primary)

    val status = Span(invisibleIcon, movingIcon, deathIcon)

    val body = Level(left=listOf(errorIcon), center = listOf(visibilityControl, icon, titleLabel, status), mobile = true)

    val deleteCallbackProperty = DeleteCallbackProperty(null,this) { old, new ->
        old?.let { body.right -= it }
        new?.let { body.right += it }
    }
    var onDelete by deleteCallbackProperty

    override val container = Box(body)

    override fun refresh() {
        // the visibility can also be changed by a meta-action, not only by this switch
        visibilitySwitch.checked = isVisible()
        icon.icon = data.icon
        icon.root.style.color = data.color
        titleLabel.text = "${data.name} (${data.id})"
        invisibleIcon.hidden = !data.invisible
        movingIcon.hidden = !data.canMove
        deathIcon.hidden = data.halfLife <= 0
        status.hidden = invisibleIcon.hidden && movingIcon.hidden && deathIcon.hidden
        movingIcon.root.style.opacity = "${data.movementProbability.coerceAtLeast(0.3)}"
        deathIcon.root.style.opacity = "${(101 - data.halfLife.coerceAtMost(71)) / 100.0}"
    }

}
