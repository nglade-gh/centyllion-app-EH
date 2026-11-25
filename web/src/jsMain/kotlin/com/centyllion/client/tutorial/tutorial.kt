package com.centyllion.client.tutorial

import bulma.BulmaElement
import com.centyllion.client.page.BulmaPage
import org.w3c.dom.HTMLElement

class TutorialStep(
    val title: String,
    val content: List<BulmaElement>,
    val selector: () -> HTMLElement,
    val validated: () -> Boolean,
    val nextCallback: () -> Unit = { },
    /**
     * Content built again every time the step is shown, instead of [content]: for a text that
     * depends on a state which may change during the tour (the working folder of the captures,
     * for instance). Null when the text is fixed.
     */
    val liveContent: (() -> List<BulmaElement>)? = null
)

interface Tutorial<P: BulmaPage> {
    val page: P

    val name: String

    val introduction: List<BulmaElement>

    fun prepare() { }

    val steps: List<TutorialStep>

    val conclusion: List<BulmaElement>

    val next: Tutorial<P>?

    /**
     * True for a guided tour: the layer shows Previous, Next and Skip buttons and walks the
     * steps by itself, instead of waiting for the visitor to perform the action described by
     * each step. The introduction dialog is skipped as well, so the first step is shown at
     * once — it carries the welcome message.
     */
    val guided: Boolean get() = false

    /**
     * Called once when the layer is closed, whatever the way out: the end of the steps, the
     * conclusion, the Skip button, the close button, or another tutorial taking over. A guided
     * tour uses it to remember that it has been seen.
     */
    fun onLeave() { }

    fun isNotEmpty() = steps.isNotEmpty()

    fun i18n(key: String, vararg parameters: Any): String = page.i18n(key, *parameters)
}

/**
 * Element a step points at: [candidate] when it is really displayed, [fallback] otherwise.
 *
 * A step must never point at a hidden element: its box is then 0 by 0 at the corner of the
 * page, and the message and the arrow are drawn there. A tab that is not selected, a chart
 * hidden because the model has no field, a read-only view without tools: the caller gives the
 * element to point at instead, through [targetOr].
 */
fun targetOr(candidate: HTMLElement?, fallback: HTMLElement): HTMLElement {
    if (candidate == null) return fallback
    val box = candidate.getBoundingClientRect()
    return if (box.width > 0.0 && box.height > 0.0) candidate else fallback
}
