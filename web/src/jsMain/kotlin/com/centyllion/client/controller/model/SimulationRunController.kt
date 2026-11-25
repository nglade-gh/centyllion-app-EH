package com.centyllion.client.controller.model

import bulma.*
import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement
import bulma.extension.Slider
import com.centyllion.client.page.BulmaPage
import com.centyllion.client.toggleElementToFullScreen
import com.centyllion.model.GrainModel
import com.centyllion.model.PARALLEL_UPDATE_PLAN
import com.centyllion.model.SEQUENTIAL_UPDATE_PLAN
import com.centyllion.model.Simulation
import com.centyllion.model.SimulationLoop
import com.centyllion.model.Simulator
import kotlinx.browser.window
import org.w3c.dom.MutationObserver
import org.w3c.dom.MutationObserverInit
import kotlin.properties.Delegates.observable
import bulma.Field as BField

class SimulationRunController(
    simulation: Simulation, model: GrainModel,
    val page: BulmaPage, readOnly: Boolean = false,
    val onStep: (simulator: Simulator) -> Unit = {},
    val onReset: (simulator: Simulator) -> Unit = {},
    val onUpdate: (old: Simulation, new: Simulation, controller: SimulationRunController) -> Unit = { _, _, _ -> }
) : Controller<Simulation, GrainModel, BulmaElement> {

    override var data: Simulation by observable(simulation) { _, old, new ->
        if (old != new) {
            currentSimulator = Simulator(context, new)
            loop = SimulationLoop(currentSimulator)
            simulationViewController.data = currentSimulator
            hideModifierPauseNotification()
            running = false
            onUpdate(old, new, this@SimulationRunController)
            refresh()
        }
    }

    override var context: GrainModel by observable(model) { _, old, new ->
        if (old != new) {
            data = data.cleaned(new)
            currentSimulator = Simulator(new, data)
            loop = SimulationLoop(currentSimulator)
            simulationViewController.data = currentSimulator
            hideModifierPauseNotification()
            running = false
            refresh()
        }
    }

    override var readOnly: Boolean by observable(readOnly) { _, old, new ->
        if (old != new) {
            simulationViewController.readOnly = new
        }
    }

    private var currentSimulator = Simulator(context, data)

    /**
     * Run/pause state of the loop: the control meta-actions (Pause, RunOneStep, RunNStep,
     * Rewind, Restart) change it at the end of the step that emitted them. A pause is lifted
     * only from the interface: there is no `Continue()`.
     */
    private var loop = SimulationLoop(currentSimulator)

    val simulator get() = currentSimulator

    private var running: Boolean by observable(false) { _, old, new ->
        if (old != new) {
            if (new) loop.start() else loop.stop()
            simulationViewController.running = new
            if (new) {
                refreshButtons()
            } else {
                lastStepTimestamp = 0.0
                refresh()
            }
        }
    }

    val isRunning get() = running

    private var disposed = false

    private var fps = 50.0
    private var lastStepTimestamp = 0.0
    private var lastRequestSkipped = true
    private var lastFpsColorRefresh = 0

    // simulation execution controls
    val rewindButton = iconButton(Icon("fast-backward"), ElementColor.Danger, rounded = true) { reset() }
    val runButton = iconButton(Icon("play"), ElementColor.Primary, rounded = true) { run() }
    val stepButton = iconButton(Icon("step-forward"), ElementColor.Primary, rounded = true) { step() }
    val stopButton = iconButton(Icon("stop"), ElementColor.Warning, rounded = true) { stop() }
    val parallelButton = iconButton(
        Icon(if (context.isSequentialUpdate) "list" else "layer-group"),
        ElementColor.Primary, rounded = true
    ) {
        val newPlan = if (context.isSequentialUpdate) PARALLEL_UPDATE_PLAN else SEQUENTIAL_UPDATE_PLAN
        context = context.copy(updatePlan = newPlan)
    }

    val fpsSlider = Slider(
        fps.toString(), "1", "200", "1", color = ElementColor.Info, circle = true
    ) { _, value ->
        fps = value.toDouble()
        setFpsColor(false)
        fpsLabel.text = if (fps >= 200) "max" else "$value fps"
    }.apply {
        root.style.width = "100px"
    }

    val stepLabel = Label()

    val fpsLabel = Button("$fps fps", rounded = true, color = ElementColor.Info)

    val resetOrbitButton = iconButton(Icon("compress-arrows-alt"), rounded = true) {
        simulationViewController.resetCamera()
    }

    val fullscreenButton = iconButton(Icon("expand-arrows-alt"), rounded = true) {
        val fullscreen = toggleElementToFullScreen(simulationColumns.root)
        if (fullscreen) {
            simulationViewController.resizeToFullscreen()
        } else {
            // uses a timer to wait for the canvas to be connected to it's parent
            window.setTimeout( { simulationViewController.resize() }, 1000)
        }
    }

    val simulationViewController = SimulatorViewController(
        currentSimulator, page, readOnly,
        onPointerMove = ::pickValuesAt,
        onUpdate = { ended, new, _ -> updatedSimulatorFromView(ended, new) }
    )

    // the edit bar (copy / cut / paste / deselect) sits right under the drawing tools
    val simulationView = Column(
        simulationViewController.container,
        simulationViewController.editToolbar,
        size = ColumnSize.Full
    )

    /**
     * Warning shown when a simulation modifier pauses the run.
     *
     * A `Pause()` written in a modifier stops the simulation behind the reader's back: the
     * warning says who stopped it and how to go on. It is a plain Bulma `notification is-warning`
     * (non blocking, closable with its cross) placed at the top of the simulation zone, and it
     * is removed as soon as the simulation starts again.
     */
    private var modifierPauseNotification: Notification? by
        bulma(null, simulationView.root, Position.AfterBegin)

    val simulationColumns: Columns = Columns(
        Column(
            Level(
                center = listOf(
                    BField(
                        Control(rewindButton), Control(runButton), Control(stepButton), Control(stopButton),
                        Control(parallelButton),
                        addons = true
                    ),
                    Control(stepLabel),
                    BField(Control(fpsSlider), Control(fpsLabel), grouped = true)
                ),
                right = listOf(
                    BField(Control(resetOrbitButton), Control(fullscreenButton), grouped = true)
                )
            ), size = ColumnSize.Full
        ),
        simulationView,
        multiline = true
    )

    override val container = simulationColumns

    /** This observable is here to compute the correct size for canvas */
    private val sizeObservable = MutationObserver { _, o ->
        resize()
        o.disconnect()
    }

    init {
        sizeObservable.observe(
            simulationColumns.root,
            MutationObserverInit(childList = true, subtree = true, attributes = true)
        )
        window.setTimeout({ animationCallback(0.0) }, 250)
    }

    fun setFpsColor(slowed: Boolean) {
        val color = when {
            fps >= 200.0 -> ElementColor.Success
            slowed -> ElementColor.Warning
            else -> ElementColor.Info
        }
        fpsLabel.color = color
        fpsSlider.color = color
    }

    fun run() {
        // the reader takes over: the warning about a pause asked by the model is obsolete
        hideModifierPauseNotification()
        running = true
    }

    fun animationCallback(timestamp: Double) {
        if (running) {
            val time = 1000 / fps
            val delta = timestamp - lastStepTimestamp

            lastFpsColorRefresh += 1
            if (lastFpsColorRefresh > fps) {
                setFpsColor(delta > time && lastRequestSkipped)
            }

            val refresh = fps >= 200 || lastStepTimestamp == 0.0 || delta >= time ||
                // the steps asked by RunOneStep/RunNStep run without waiting for the fps limit
                loop.remainingSteps > 0

            if (refresh) {
                lastStepTimestamp = timestamp
                executeStep()
            }

            lastRequestSkipped = refresh
        }

        if (!disposed) window.requestAnimationFrame(this::animationCallback)
    }

    fun step() {
        if (!running) {
            hideModifierPauseNotification()
            executeStep()
            simulationViewController.render()
            refreshButtons()
        }
    }

    fun stop() {
        // creates thumbnail with last stop click for better thumbnails.
        running = false
    }

    /** Copies the run time activation of each behaviour into its On/Off radio. */
    private fun syncActivationRadios(simulator: Simulator) {
        val inputs = document.querySelectorAll("input[data-behaviour-id]")
        for (index in 0 until inputs.length) {
            val input = inputs.item(index) as? HTMLInputElement ?: continue
            val id = input.getAttribute("data-behaviour-id")?.toIntOrNull() ?: continue
            input.checked = simulator.isRuntimeEnabled(id)
        }
        val modifiers = document.querySelectorAll("input[data-modifier-id]")
        for (index in 0 until modifiers.length) {
            val input = modifiers.item(index) as? HTMLInputElement ?: continue
            val id = input.getAttribute("data-modifier-id")?.toIntOrNull() ?: continue
            input.checked = simulator.isModifierUsed(id)
        }
    }

    fun reset() {
        if (!running) {
            loop.stop()
            hideModifierPauseNotification()
            currentSimulator.reset()
            // a rewind restores the activation state of the beginning of the run
            syncActivationRadios(currentSimulator)
            refresh()
            onReset(currentSimulator)
            stepLabel.text = "${currentSimulator.step}"
        }
    }

    /**
     * Applies the control requests emitted by the meta-actions during the step that has just
     * run: Pause, RunOneStep, RunNStep, Rewind, Restart. They drive the loop, never the grid.
     */
    private fun applyControlRequests() {
        val update = loop.afterStep()
        // a modifier can ask the pause: the reader must see it, the Run button is the only way
        // back (there is no Continue() any more)
        if (simulator.consumeModifierPauseRequest() > 0) showModifierPauseNotification()
        if (update.rewound) {
            // the simulation is back to its initial state: the radios follow the restored state
            syncActivationRadios(currentSimulator)
            refresh()
            onReset(currentSimulator)
            stepLabel.text = "${currentSimulator.step}"
        }
        if (running != update.running) running = update.running
    }

    /**
     * Shows the warning that says the run was paused by a simulation modifier. The notification
     * is created once and kept until the run starts again: showing it twice would stack two
     * boxes in the simulation zone.
     */
    private fun showModifierPauseNotification() {
        if (modifierPauseNotification != null) return
        modifierPauseNotification = Notification(
            Content { +page.i18n("The model paused the simulation (modifier). Resume it with the Run button or step by step.") },
            color = ElementColor.Warning,
            onDelete = { hideModifierPauseNotification() }
        )
    }

    /** Removes the warning about a pause asked by the model (the reader took over). */
    private fun hideModifierPauseNotification() {
        modifierPauseNotification = null
    }

    private fun executeStep() {
        val (applied, dead) = simulator.oneStep()
        // Activate/Inactivate change the run time state: the radios must follow
        if (simulator.activationChanged) {
            simulator.clearActivationChanged()
            syncActivationRadios(simulator)
        }
        // a control meta-action stops, resumes or rewinds the loop at the end of the step
        applyControlRequests()
        // without fields, nothing can change if no behaviour was applied and nothing died
        // a modifier can change the state even when no behaviour was applied
        if (applied.isNotEmpty() || dead.isNotEmpty() || simulator.modifiersChanged || simulator.model.fields.isNotEmpty()) {
            simulationViewController.oneStep(applied, dead)
        }
        stepLabel.text = "${currentSimulator.step}"
        onStep(simulator)
    }


    private fun updatedSimulatorFromView(ended: Boolean, new: Simulator) {
        // only update simulation if initial simulation was modified
        if (currentSimulator.step == 0 && ended) {
            currentSimulator.resetCount()
            data = data.copy(agents = new.initialAgents.toList())
        }
    }

    fun refreshButtons() {
        rewindButton.disabled = running
        rewindButton.color = if (currentSimulator.step == 0) ElementColor.Primary else ElementColor.Danger
        runButton.loading = running
        runButton.disabled = running
        stepButton.disabled = running
        stopButton.disabled = !running
    }

    fun pickValuesAt(x: Int, y: Int) {
        // TODO
    }

    override fun refresh() {
        refreshButtons()
        simulationViewController.refresh()
    }

    fun resize() {
        simulationViewController.resize()
    }

    fun dispose() {
        disposed = true
        stop()
        simulationViewController.dispose()
    }
}
