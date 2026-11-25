package com.centyllion.client.tutorial

import bulma.*
import com.centyllion.client.page.BulmaPage
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement

class TutorialLayer<P: BulmaPage>(
    initial: Tutorial<P>, val onEnd: (TutorialLayer<P>) -> Unit = {}
 ) {

    var tutorial = initial

    var started = false
    var currentStep = 0

    /** Element carrying the highlight of the current step, null when none is marked. */
    private var highlighted: HTMLElement? = null

    /** Computed style of an element (the margins are not in `offsetHeight`). */
    private fun computedStyle(element: HTMLElement): dynamic =
        js("function(el) { return window.getComputedStyle(el); }")(element)

    /** Length in pixels of a computed CSS value ("16px"), 0 when it is missing. */
    private fun pixels(value: String?): Double =
        value?.removeSuffix("px")?.trim()?.toDoubleOrNull() ?: 0.0

    /**
     * Height the message really occupies, margins included.
     *
     * Not read from the placed box: the theme animates `top` and `left` (1.5 s), so a rect read
     * just after a move is the position in the middle of the slide, not the target position.
     */
    private fun messageHeight(): Double {
        val style = computedStyle(container.root)
        return container.root.offsetHeight.toDouble() +
            pixels(style.marginTop as? String) + pixels(style.marginBottom as? String)
    }

    private fun placeStep(target: HTMLElement) {
        // the target may be outside the window (a chart at the bottom of a tall page, a bar
        // under the fold): it is brought in view first, then measured, otherwise the message
        // and the arrow are placed outside the screen
        val before = target.getBoundingClientRect()
        if (before.top < 0.0 || before.bottom > window.innerHeight) {
            window.scrollBy(0.0, before.top - (window.innerHeight - before.height) / 2.0)
        }

        val bodyBox = document.body?.getBoundingClientRect()
        val elementBox = target.getBoundingClientRect()

        // places help content below the target, and keeps it inside the window: a target taller
        // than the screen leaves no room under it, so the message slides up instead of leaving
        val marginTop = pixels(computedStyle(container.root).marginTop as? String)
        val top = (elementBox.top - (bodyBox?.top ?: 0.0) + elementBox.height * 1.5)
        val windowBottom = (window.innerHeight - (bodyBox?.top ?: 0.0)) - 8.0
        val maxTop = (windowBottom - marginTop - messageHeight()).coerceAtLeast(0.0)
        container.root.style.top = "${top.coerceIn(0.0, maxTop)}px"

        val left = elementBox.left - (bodyBox?.left ?: 0.0) + (target.clientWidth - container.root.clientWidth)/2
        val maximumLeft = ((bodyBox?.width ?: Double.MAX_VALUE) - container.root.clientWidth).coerceAtLeast(0.0)
        val coercedLeft = left.coerceIn(bodyBox?.left ?: 0.0, maximumLeft)
        container.root.style.left = "${coercedLeft}px"

        arrow.root.style.top = "${elementBox.top - (bodyBox?.top ?: 0.0) + elementBox.height * 1.5}px"
        arrow.root.style.left = "${elementBox.left - (bodyBox?.left ?: 0.0) + elementBox.width/2.0}px"
    }

    /**
     * Frames the element the step points at (see `.tutorial-target` in the theme): the arrow
     * alone is thin, the frame says at a glance where to look. The class is moved at each
     * step and dropped when the layer closes, so the page is left as it was.
     */
    private fun highlight(target: HTMLElement) {
        highlighted?.classList?.remove(HIGHLIGHT_CLASS)
        target.classList.add(HIGHLIGHT_CLASS)
        highlighted = target
    }

    private fun clearHighlight() {
        highlighted?.classList?.remove(HIGHLIGHT_CLASS)
        highlighted = null
    }

    private fun setStep() {
        val step = tutorial.steps[currentStep]
        val target = step.selector()
        title.text = step.title
        // a guided step may build its text each time (the working folder state, for instance)
        content.body = step.liveContent?.invoke() ?: step.content
        progression.value = currentStep
        if (tutorial.guided) refreshNavigation()
        placeStep(target)
        highlight(target)
    }

    /** Keeps the three buttons of a guided tour in step with the position in the tutorial. */
    private fun refreshNavigation() {
        previousButton.disabled = currentStep <= 0
        nextButton.root.textContent = i18n(
            if (currentStep >= tutorial.steps.size - 1) "Finish" else "Next"
        )
        nextButton.color = if (currentStep >= tutorial.steps.size - 1) ElementColor.Success else ElementColor.Primary
    }

    private fun i18n(key: String) = tutorial.i18n(key)

    private fun checking() {
        if (currentStep < tutorial.steps.size) {
            val step = tutorial.steps[currentStep]
            if (step.validated()) next()
        }
        if (started) window.setTimeout(this::checking, 250)
    }

    fun start() {
        // a layer can be restarted (conclusion -> next tutorial): drop its own
        // previous widgets so they don't accumulate in the page
        started = false
        document.body?.let { body ->
            if (body.contains(arrow.root)) body.removeChild(arrow.root)
            if (body.contains(container.root)) body.removeChild(container.root)
        }
        clearHighlight()

        // a guided tour walks its steps itself: no introduction dialog, the first step is the
        // welcome message, and the visitor goes on with Next or leaves with Skip
        if (tutorial.guided) {
            tutorial.prepare()
            startSteps()
            return
        }

        // creates a modal dialog to present the tutorial
        tutorial.page.modalDialog(
            tutorial.i18n("Tutorial '%0'", tutorial.i18n(tutorial.name)), tutorial.introduction,
            textButton(tutorial.i18n("Start tutorial"), color = ElementColor.Success) {
                tutorial.prepare()
                startSteps()
            },
            textButton(tutorial.i18n("Ok but later")) { /* nothing to do */ },
        )
    }

    private fun startSteps() {
        started = true
        window.setTimeout(this::checking, 1000)

        document.body?.appendChild(arrow.root)
        document.body?.appendChild(container.root)

        arrow.root.classList.add("fadeIn")
        container.root.classList.add("fadeIn")
        setStep()
    }

    fun next() {
        tutorial.steps[currentStep].nextCallback()
        currentStep += 1
        when {
            currentStep < tutorial.steps.size -> setStep()
            currentStep == tutorial.steps.size -> lastStep()
        }
    }

    /** Goes back one step (guided tours only): the previous callback is not undone. */
    fun previous() {
        if (currentStep <= 0) return
        currentStep -= 1
        setStep()
    }

    /** Leaves the tutorial at once (the Skip button of a guided tour). */
    fun skip() = stop()

    fun lastStep() {
        // opens the conclusion if there is one and tutorial was done all the way
        if (tutorial.conclusion.isNotEmpty()) {
            val buttons = listOfNotNull(
                textButton(tutorial.i18n("Ok"), color = ElementColor.Success) { stop() },
                tutorial.next?.let { next ->
                    textButton(tutorial.i18n(next.name), color = ElementColor.Primary) {
                        tutorial = next
                        currentStep = 0
                        start()
                    }
                }
            ).toTypedArray()

            tutorial.page.modalDialog(tutorial.i18n(tutorial.name), tutorial.conclusion, *buttons)
        } else {
            stop()
        }
    }


    fun stop() {
        started = false

        arrow.root.classList.remove("fadeIn")
        container.root.classList.remove("fadeIn")

        arrow.root.classList.add("fadeOut")
        container.root.classList.add("fadeOut")

        clearHighlight()

        window.setTimeout( {
            document.body?.let { body ->
                if (body.contains(arrow.root)) body.removeChild(arrow.root)
                if (body.contains(container.root)) body.removeChild(container.root)
            }
        }, 1000)

        currentStep = 0
        tutorial.onLeave()
        onEnd(this)
    }

    val arrow = span(classes = "tutorial tutorial-arrow animated")
    val title = p("")
    val delete = Delete { stop() }

    val content = Div().apply {
        root.style.marginBottom = "1rem"
    }

    val progression = ProgressBar().apply {
        color = ElementColor.Success
        min = 0
        value = currentStep
        max = tutorial.steps.size - 1
    }

    // Previous / Next / Skip of a guided tour: hidden (inline, the theme classes of Bulma
    // carry !important) for every other tutorial, which advances on the actions of the visitor
    val previousButton = Button(i18n("Previous"), size = Size.Small) { previous() }

    val nextButton = Button(i18n("Next"), color = ElementColor.Primary, size = Size.Small) { next() }

    val skipButton = Button(i18n("Skip"), size = Size.Small) { skip() }

    val navigation = Div(previousButton, nextButton, skipButton).apply {
        root.style.display = if (tutorial.guided) "flex" else "none"
        root.style.justifyContent = "flex-end"
        root.style.setProperty("gap", "0.5rem")
        root.style.marginTop = "0.75rem"
    }

    val container = Message(
        header = listOf(title, delete),
        body = listOf(content, progression, navigation),
        color = ElementColor.Success
    ).apply {
        root.classList.add("tutorial")
        root.classList.add("tutorial-content")
        root.classList.add("animated")
    }

    companion object {
        /** Class of the frame drawn around the element a step points at. */
        const val HIGHLIGHT_CLASS = "tutorial-target"
    }
}
