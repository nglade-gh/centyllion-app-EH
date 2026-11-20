package com.centyllion.client.controller.model

import bulma.*
import com.centyllion.i18n.Locale
import com.centyllion.model.Predicate
import kotlin.properties.Delegates.observable

/**
 * One "simulation age" predicate line: [relation] [value] [delete].
 *
 * All the lines of a behaviour are ANDed, like the field thresholds and the
 * neighbour count predicates (see `Behaviour.applicable()`).
 */
class AgePredicateLineController(
    locale: Locale, value: Predicate<Int>,
    var onUpdate: (old: Predicate<Int>, new: Predicate<Int>, controller: AgePredicateLineController) -> Unit =
        { _, _, _ -> },
    var onDelete: (Predicate<Int>, controller: AgePredicateLineController) -> Unit = { _, _ -> }
) : NoContextController<Predicate<Int>, Column>() {

    override var data by observable(value) { _, old, new ->
        if (old != new) {
            predicateController.data = new
            onUpdate(old, new, this@AgePredicateLineController)
            refresh()
        }
    }

    override var readOnly: Boolean by observable(false) { _, old, new ->
        if (old != new) {
            predicateController.readOnly = new
            delete.invisible = new
        }
    }

    val predicateController: IntPredicateController = IntPredicateController(
        locale = locale, predicate = data, placeHolder = locale.i18n("Age"), minValue = 0
    ) { _, new, _ ->
        data = new
    }

    val delete = iconButton(Icon("times", Size.Small), ElementColor.Danger, true, size = Size.Small) {
        onDelete(this.data, this@AgePredicateLineController)
    }

    override val container = Column(
        Level(
            center = listOf(predicateController.container),
            right = listOf(delete)
        ),
        size = ColumnSize.Full
    )

    override fun refresh() {
        predicateController.refresh()
    }

}
