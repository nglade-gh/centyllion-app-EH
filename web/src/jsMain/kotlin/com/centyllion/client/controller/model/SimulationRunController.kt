package com.centyllion.client.controller.model

import bulma.*
import kotlinx.browser.document
import org.w3c.dom.HTMLInputElement
import bulma.extension.Slider
import com.centyllion.client.capture.CaptureDestination
import com.centyllion.client.capture.CaptureFallback
import com.centyllion.client.capture.CaptureResult
import com.centyllion.client.capture.WorkFolder
import com.centyllion.client.page.BulmaPage
import com.centyllion.client.page.notification
import com.centyllion.client.toggleElementToFullScreen
import com.centyllion.model.GrainModel
import com.centyllion.model.PARALLEL_UPDATE_PLAN
import com.centyllion.model.SEQUENTIAL_UPDATE_PLAN
import com.centyllion.model.Simulation
import com.centyllion.model.SimulationLoop
import com.centyllion.model.Simulator
import com.centyllion.model.formatPauseSeconds
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
     * Run/pause state of the loop: the control meta-actions (Pause, Pause(t), RunOneStep,
     * RunNStep, Rewind, Restart) change it at the end of the step that emitted them. An
     * indefinite pause is lifted only from the interface; a timed pause resumes by itself.
     */
    private var loop = SimulationLoop(currentSimulator)

    val simulator get() = currentSimulator

    private var running: Boolean by observable(false) { _, old, new ->
        if (old != new) {
            if (new) {
                // the loop is started only when it does not already run: mirroring the state of
                // the loop (a timed pause keeps `running` true) must not lift its own pause
                if (!loop.running) loop.start()
            } else {
                loop.stop()
                lastStepTimestamp = 0.0
                refresh()
            }
            simulationViewController.running = new
            if (new) {
                refreshButtons()
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

    /**
     * The run bar above the grid: Rewind, Run, Step, Stop, the speed and the step counter. A
     * property so that the tour of the first connection can frame the bar itself.
     */
    val runBar: Level = Level(
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
    )

    val simulationColumns: Columns = Columns(
        Column(
            runBar, size = ColumnSize.Full
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
            // a timed pause (Pause(t)) keeps the loop alive without executing any step: the
            // deadline is polled on every frame and the loop lifts the pause by itself (see
            // SimulationLoop.resumeIfDue), which is why the frames must keep coming
            if (loop.isTimedPaused) {
                if (loop.resumeIfDue()) {
                    // the run starts again by itself: the warning is obsolete
                    hideModifierPauseNotification()
                    lastStepTimestamp = 0.0
                }
            }
            if (loop.canStep) {
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
        // the reader takes over: the warning about a pause asked by the model is obsolete
        hideModifierPauseNotification()
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
     * run: Pause, Pause(t), RunOneStep, RunNStep, Rewind, Restart. They drive the loop, never
     * the grid.
     */
    private fun applyControlRequests() {
        val update = loop.afterStep()
        // a modifier can ask the pause: the reader must see it. An indefinite pause is lifted
        // only by the reader; a timed pause resumes by itself and the warning then disappears.
        if (simulator.consumeModifierPauseRequest() > 0) {
            showModifierPauseNotification(update.pauseSeconds ?: 0.0)
        }
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
     *
     * [seconds] is the duration of a timed pause (`Pause(t)`), 0 for the indefinite `Pause()`:
     * the message then says how long the run stays frozen, and that it starts again by itself.
     */
    private fun showModifierPauseNotification(seconds: Double = 0.0) {
        if (modifierPauseNotification != null) return
        val text = if (seconds > 0.0) {
            page.i18n(
                "The model paused the simulation for %0 s (modifier). The run resumes by itself at the end of the pause.",
                formatPauseSeconds(seconds)
            )
        } else {
            page.i18n("The model paused the simulation (modifier). Resume it with the Run button or step by step.")
        }
        modifierPauseNotification = Notification(
            Content { +text },
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
        val painted = applied.isNotEmpty() || dead.isNotEmpty() ||
            simulator.modifiersChanged || simulator.model.fields.isNotEmpty()
        if (painted) {
            simulationViewController.oneStep(applied, dead)
        }
        // the image of the step is complete (oneStep ends with a render): a Capture() asked by
        // this step is taken now, before the next step erases it
        takeCaptures(painted)
        stepLabel.text = "${currentSimulator.step}"
        onStep(simulator)
    }

    /**
     * Takes the captures the step has just asked for (the `Capture()` meta-action).
     *
     * The core only counts the requests: the picture is encoded here, from the simulation canvas
     * as it stands at the end of the step, then written in the working folder when one is defined —
     * or downloaded. [reportCapture] says which of the two happened: a capture never falls back to
     * a download silently.
     *
     * [painted] is false when the step changed nothing that is drawn: the canvas already shows the
     * state of the step, since the grid is only repainted when it changes.
     */
    private fun takeCaptures(painted: Boolean) {
        val count = simulator.consumeCaptureRequest()
        if (count <= 0) return
        if (!painted) simulationViewController.render()
        val modelName = simulator.model.name
        // the name carries the step of the picture: the counter shown once the step is done
        val step = simulator.step
        repeat(count) {
            val picture = simulationViewController.captureJpeg()
            if (picture == null) {
                page.error("The simulation view could not be captured.")
            } else {
                WorkFolder.saveCapture(modelName, step, picture) { result -> reportCapture(result) }
            }
        }
    }

    /** Tells the reader where the capture went: the working folder, or a download and why. */
    private fun reportCapture(result: CaptureResult) {
        when (result.destination) {
            CaptureDestination.FOLDER -> notification(
                page.i18n("Capture saved in %0.", result.path ?: result.fileName),
                ElementColor.Success, 6000
            )
            CaptureDestination.DOWNLOAD -> notification(
                page.i18n(captureFallbackMessage(result.fallback), result.fileName),
                ElementColor.Warning, 7000
            )
        }
    }

    /** Message of a downloaded capture: the reason is part of the sentence, never silent. */
    private fun captureFallbackMessage(fallback: CaptureFallback): String = when (fallback) {
        CaptureFallback.UNSUPPORTED -> "Capture downloaded: %0 (this browser has no access to a folder)."
        CaptureFallback.PERMISSION -> "Capture downloaded: %0 (the permission of the working folder must be renewed)."
        CaptureFallback.WRITE_ERROR -> "Capture downloaded: %0 (the working folder could not be written)."
        else -> "Capture downloaded: %0 (no working folder)."
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
