package com.centyllion.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The `Capture()` meta-action and the name of the pictures it asks for.
 *
 * Nothing here needs a browser: the action only counts the request in the [Simulator] (the view
 * takes and serves it), and the naming rules are the pure functions of `capture.kt`.
 */
class CaptureTest {

    // ------------------------------------------------------------ file name

    @Test
    fun testTheCaptureNameIsTheModelThenTwoUnderscoresThenTheStep() {
        assertEquals("GoL_Modifiers__12.jpg", captureFileName("GoL_Modifiers", 12))
        assertEquals("cell__0.jpg", captureFileName("cell", 0))
        assertEquals("_CON__3.jpg", captureFileName("CON", 3))
    }

    @Test
    fun testTheModelNameIsTheSubDirectoryNameToo() {
        // the same cleaning gives the sub-directory of the working folder and the file name
        assertEquals("GoL_Modifiers", sanitizeCaptureName("GoL_Modifiers"))
        assertEquals(sanitizeCaptureName("a/b"), captureFileName("a/b", 1).substringBefore("__1.jpg"))
    }

    @Test
    fun testTheForbiddenCharactersBecomeUnderscores() {
        // a slash would create another directory, a backslash and the others are refused by
        // Windows: the picture must always land in the sub-directory of the working folder
        assertEquals("a_b", sanitizeCaptureName("a/b"))
        assertEquals("a_b", sanitizeCaptureName("a\\b"))
        assertEquals("a_b_c_d_e_f_g_h_i", sanitizeCaptureName("a:b*c?d\"e<f>g|h/i"))
        // a path-looking name keeps a single directory level only
        assertEquals("_etc_passwd", sanitizeCaptureName("/etc/passwd"))
    }

    @Test
    fun testTheControlCharactersBecomeUnderscores() {
        assertEquals("a_b", sanitizeCaptureName("a\nb"))
        assertEquals("a_b", sanitizeCaptureName("a\tb"))
        assertEquals("a_b", sanitizeCaptureName("a\u0000b"))
    }

    @Test
    fun testTheSurroundingSpacesAndDotsAreDropped() {
        assertEquals("GoL", sanitizeCaptureName("  GoL  "))
        assertEquals("Game of Life", sanitizeCaptureName("Game of Life"), "inner spaces are kept")
        assertEquals("v1", sanitizeCaptureName("v1."), "Windows drops a trailing dot")
        assertEquals("hidden", sanitizeCaptureName(".hidden"))
    }

    @Test
    fun testAccentsAreKept() {
        // the name stays the one the author wrote: no ASCII folding
        assertEquals("Bactéries", sanitizeCaptureName("Bactéries"))
        assertEquals("Évolution — modèle 2", sanitizeCaptureName("Évolution — modèle 2"))
        assertEquals("Évolution — modèle 2__7.jpg", captureFileName("Évolution — modèle 2", 7))
    }

    @Test
    fun testAnEmptyNameFallsBackOnAnExplicitName() {
        assertEquals(CAPTURE_NAME_FALLBACK, sanitizeCaptureName(""))
        assertEquals(CAPTURE_NAME_FALLBACK, sanitizeCaptureName("   "))
        assertEquals(CAPTURE_NAME_FALLBACK, sanitizeCaptureName("."))
        assertEquals(CAPTURE_NAME_FALLBACK, sanitizeCaptureName(".."))
        assertEquals("model__4.jpg", captureFileName("", 4), "the fallback keeps a usable name")
        assertEquals("model__4.jpg", captureFileName("  ..  ", 4))
    }

    @Test
    fun testTheModelPartIsBounded() {
        val long = "x".repeat(200)
        val name = sanitizeCaptureName(long)
        assertEquals(CAPTURE_NAME_MAX_LENGTH, name.length)
        // the cut never leaves a trailing space: the 64th character here is a space
        assertEquals(
            CAPTURE_NAME_MAX_LENGTH - 1,
            sanitizeCaptureName("y".repeat(CAPTURE_NAME_MAX_LENGTH - 1) + " " + "z".repeat(20)).length
        )
        // and the whole file name keeps its suffix
        assertTrue(captureFileName(long, 42).endsWith("__42.jpg"))
    }

    @Test
    fun testAWindowsDeviceNameGetsAnUnderscore() {
        for (device in listOf("CON", "con", "PRN", "AUX", "NUL", "COM1", "com9", "LPT1", "LPT9")) {
            assertEquals("_$device", sanitizeCaptureName(device), "$device is a device name")
        }
        // a name that only starts with a device name is fine
        assertEquals("console", sanitizeCaptureName("console"))
        assertEquals("CONSOLE", sanitizeCaptureName("CONSOLE"))
    }

    // --------------------------------------------------------- the action

    @Test
    fun testCaptureIsAMetaActionWrittenWithACapital() {
        assertTrue(isMetaAction(Action.Capture))
        assertNull(validateModifierActions("Capture()"))
        assertEquals(Action.Capture, "Capture()".parseModifierActions().single())

        // the name is case sensitive: the lowercase form is not an alias of Capture()
        val message = validateModifierActions("capture()")!!
        assertTrue(message.contains("Unknown action"), message)
        assertTrue(message.contains("Capture"), "the message lists the available actions: $message")
    }

    @Test
    fun testCaptureIsRefusedInTheActionsOfABehaviour() {
        // like every meta-action: it acts on the simulation, not on the cell of a behaviour
        val message = validateActionFormula("Capture()")!!
        assertTrue(message.contains("meta-action"), message)

        var thrown: Throwable? = null
        try {
            "Capture()".parseActionFormula(allowMeta = false)
        } catch (t: Throwable) {
            thrown = t
        }
        assertTrue(thrown is IllegalArgumentException, "a behaviour formula must refuse Capture()")

        // and it is not mixed with the cell actions: one meta-action is enough to refuse the list
        assertTrue(validateActionFormula("Add(0, 1, 0), Capture()")!!.contains("meta-action"))
    }

    @Test
    fun testCaptureTakesNoArgument() {
        val message = validateModifierActions("Capture(1)")!!
        assertTrue(message.contains("expects 0 arguments"), message)
        assertTrue(validateModifierActions("Capture(1, 2)")!!.contains("expects 0 arguments"))
    }

    // -------------------------------------------------------- the counter

    private fun model(formula: String, actions: String) = GrainModel(
        name = "capture",
        grains = listOf(Grain(id = 0, name = "a", color = "Lime", movementProbability = 0.0)),
        modifiers = listOf(
            SimulationModifier(name = "shots", id = 0, formula = formula, actions = actions)
        )
    )

    private fun simulation() = createSimulation("capture", size = 10) { -1 }

    @Test
    fun testTheCounterCountsTheCapturesAndTheViewConsumesThem() {
        val simulator = Simulator(model("step == 2", "Capture()"), simulation())
        assertEquals(0, simulator.captureRequestCount, "nothing is asked before the first step")

        // the counter belongs to the step: a step that does not ask for a capture leaves it empty
        simulator.oneStep()
        assertEquals(0, simulator.captureRequestCount, "the condition is false at the first step")
        simulator.oneStep()
        assertEquals(0, simulator.captureRequestCount)

        simulator.oneStep()
        assertEquals(3, simulator.step)
        assertEquals(1, simulator.captureRequestCount, "one Capture() asked by the step where step == 2")

        // the view reads the counter once, then the request is served
        assertEquals(1, simulator.consumeCaptureRequest())
        assertEquals(0, simulator.captureRequestCount, "the consumption clears the counter")
        assertEquals(0, simulator.consumeCaptureRequest(), "a capture is taken once")

        simulator.oneStep()
        assertEquals(0, simulator.captureRequestCount, "the condition is false again")
    }

    @Test
    fun testTwoCapturesInTheSameStepAreCountedTwice() {
        val simulator = Simulator(model("step == 0", "Capture(), Capture()"), simulation())
        simulator.oneStep()
        assertEquals(2, simulator.captureRequestCount)
        assertEquals(2, simulator.consumeCaptureRequest())
        assertEquals(0, simulator.captureRequestCount)
    }

    @Test
    fun testCaptureIsNotAControlRequestAndDoesNotTouchTheGrid() {
        val simulator = Simulator(model("step == 0", "Capture()"), simulation())
        simulator.oneStep()
        // the loop must not see it: a capture changes neither the run nor the grid
        val requests = simulator.consumeControlRequests()
        assertTrue(requests.isEmpty, "a capture is not a loop control request")
        assertTrue(simulator.pendingGridCommands.isEmpty(), "a capture is not a grid command")
        assertFalse(simulator.modifiersChanged, "a capture does not repaint the grid by itself")
        assertEquals(1, simulator.captureRequestCount, "the counter survives the control requests")
    }

    @Test
    fun testARewindDropsTheCapturesThatWereNotTaken() {
        val simulator = Simulator(model("step == 0", "Capture(), Rewind()"), simulation())
        val loop = SimulationLoop(simulator, running = true)
        simulator.oneStep()
        assertEquals(1, simulator.captureRequestCount, "the step asked for a capture before the rewind")

        // the rewind is applied at the end of the step: the image the capture would show is gone
        val update = loop.afterStep()
        assertTrue(update.rewound)
        assertEquals(0, simulator.step)
        assertEquals(0, simulator.captureRequestCount, "a rewind clears the pending captures")

        // the same goes for a reset asked by the interface
        val other = Simulator(model("step == 0", "Capture()"), simulation())
        other.oneStep()
        assertEquals(1, other.captureRequestCount)
        other.reset()
        assertEquals(0, other.captureRequestCount)
    }
}
