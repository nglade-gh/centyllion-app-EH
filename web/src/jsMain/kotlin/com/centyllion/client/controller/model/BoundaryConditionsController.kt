package com.centyllion.client.controller.model

import bulma.*
import bulma.extension.Switch
import com.centyllion.client.page.BulmaPage
import com.centyllion.model.BoundaryConditions
import kotlin.properties.Delegates.observable
import bulma.Field as BField

/**
 * The four per-wall switches of a [BoundaryConditions] value: one row for the vertical walls
 * (left / right) and one for the horizontal walls (top / bottom), each pair grouped.
 *
 * Shared by the grain editor and the behaviour editor so that both offer exactly the same
 * control, with the same labels: a checked switch authorises crossing that wall (toric
 * wrap-around), all four checked (the default) means toric everywhere.
 */
class BoundaryConditionsController(
    initialData: BoundaryConditions,
    val page: BulmaPage,
    var onUpdate: (old: BoundaryConditions, new: BoundaryConditions) -> Unit = { _, _ -> }
) {

    /** The value shown by the switches. Setting it refreshes the switches and notifies [onUpdate]. */
    var data: BoundaryConditions by observable(initialData) { _, old, new ->
        if (old != new) {
            leftSwitch.checked = new.left
            rightSwitch.checked = new.right
            topSwitch.checked = new.top
            bottomSwitch.checked = new.bottom
            onUpdate(old, new)
        }
    }

    val leftSwitch = Switch(
        page.i18n("Left"), rounded = true, outlined = true,
        size = Size.Small, color = ElementColor.Info, checked = data.left
    ) { _, new -> data = data.copy(left = new) }

    val rightSwitch = Switch(
        page.i18n("Right"), rounded = true, outlined = true,
        size = Size.Small, color = ElementColor.Info, checked = data.right
    ) { _, new -> data = data.copy(right = new) }

    val topSwitch = Switch(
        page.i18n("Top"), rounded = true, outlined = true,
        size = Size.Small, color = ElementColor.Info, checked = data.top
    ) { _, new -> data = data.copy(top = new) }

    val bottomSwitch = Switch(
        page.i18n("Bottom"), rounded = true, outlined = true,
        size = Size.Small, color = ElementColor.Info, checked = data.bottom
    ) { _, new -> data = data.copy(bottom = new) }

    var readOnly: Boolean
        get() = leftSwitch.disabled
        set(value) {
            leftSwitch.disabled = value
            rightSwitch.disabled = value
            topSwitch.disabled = value
            bottomSwitch.disabled = value
        }

    val container = Column(
        Label(page.i18n("Boundary conditions")),
        BField(leftSwitch, rightSwitch, grouped = true),
        BField(topSwitch, bottomSwitch, grouped = true),
        size = ColumnSize.Full
    )
}
