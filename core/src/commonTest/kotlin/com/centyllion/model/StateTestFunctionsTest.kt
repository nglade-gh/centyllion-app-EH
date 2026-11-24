package com.centyllion.model

import com.centyllion.expression.parseExpression
import com.centyllion.i18n.English
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * State tests of a simulation modifier: `IsToricSelectionOn`, `IsOverwritingOn` and `IsVisible`
 *
 * They all read the state of the simulation or of its view, so the condition of a modifier is
 * their only home: an actions formula and the condition of a behaviour refuse them.
 */
class StateTestFunctionsTest {

    // ---- the new actions of the edit bar ----

    @Test
    fun testTheSwitchCommandsAreAcceptedInAModifier() {
        assertNull(validateModifierActions("AllowToricSelection(true)"))
        assertNull(validateModifierActions("AllowToricSelection(false)"))
        assertNull(validateModifierActions("AllowOverwriting(true)"))
        assertNull(validateModifierActions("AllowOverwriting(false)"))
        assertNull(validateModifierActions("Hide(0), AllowOverwriting(true), AllowToricSelection(false)"))
    }

    @Test
    fun testTheSwitchCommandsCheckTheirArity() {
        assertTrue(
            validateModifierActions("AllowToricSelection()")!!
                .contains("AllowToricSelection expects 1 argument, got 0")
        )
        assertTrue(
            validateModifierActions("AllowToricSelection(true, false)")!!
                .contains("AllowToricSelection expects 1 argument, got 2")
        )
        assertTrue(
            validateModifierActions("AllowOverwriting()")!!
                .contains("AllowOverwriting expects 1 argument, got 0")
        )
    }

    @Test
    fun testTheSwitchCommandsTakeABooleanLiteral() {
        // like the paste mode: true or false, never a number nor an expression
        assertTrue(validateModifierActions("AllowToricSelection(1)")!!.contains("must be a boolean"))
        assertTrue(validateModifierActions("AllowToricSelection(0)")!!.contains("must be a boolean"))
        assertTrue(validateModifierActions("AllowOverwriting(step)")!!.contains("must be a boolean"))
        assertTrue(validateModifierActions("AllowOverwriting(true ? 1 : 0)")!!.contains("must be a boolean"))
    }

    @Test
    fun testTheSwitchCommandsAreRefusedInABehaviour() {
        assertTrue(validateActionFormula("AllowToricSelection(true)")!!.contains("meta-action"))
        assertTrue(validateActionFormula("AllowOverwriting(false)")!!.contains("meta-action"))
    }

    @Test
    fun testTheSwitchCommandsResolveTheirBoolean() {
        fun resolve(formula: String) =
            (formula.parseModifierActions().single() as Action.Grid).call.resolve(NetbioDynEvaluator(null))
        assertEquals(GridCommand.AllowToricSelection(true), resolve("AllowToricSelection(true)"))
        assertEquals(GridCommand.AllowToricSelection(false), resolve("AllowToricSelection(false)"))
        assertEquals(GridCommand.AllowOverwriting(true), resolve("AllowOverwriting(true)"))
        assertEquals(GridCommand.AllowOverwriting(false), resolve("AllowOverwriting(false)"))
    }

    // ---- resolution of the switches in the simulator ----

    private fun model(condition: String, actions: String) = GrainModel(
        name = "state",
        grains = listOf(Grain(id = 0, name = "a", color = "Lime", movementProbability = 0.0)),
        modifiers = listOf(
            SimulationModifier(name = "m", id = 0, formula = condition, actions = actions)
        ),
    )

    private fun simulation() = createSimulation("state", size = 8) { -1 }

    @Test
    fun testTheSwitchesStartOffAndFollowTheCommands() {
        val simulator = Simulator(model("", "AllowToricSelection(true), AllowOverwriting(false)"), simulation())
        // safe defaults: nothing changes unless the formula asks for it
        assertFalse(simulator.toricSelectionOn)
        assertFalse(simulator.overwritingOn)
        simulator.oneStep()
        assertTrue(simulator.toricSelectionOn, "AllowToricSelection(true) turned the Toric ? switch on")
        assertFalse(simulator.overwritingOn, "AllowOverwriting(false) left the Overwrite ? switch off")
        // and the view receives the two commands, in order, to flip its widgets
        assertEquals(
            listOf<GridCommand>(GridCommand.AllowToricSelection(true), GridCommand.AllowOverwriting(false)),
            simulator.pendingGridCommands
        )
    }

    @Test
    fun testTheSwitchesAreReadBackByTheNextModifierOfTheSameStep() {
        val model = GrainModel(
            name = "state",
            grains = listOf(Grain(id = 0, name = "a", color = "Lime", movementProbability = 0.0)),
            modifiers = listOf(
                // first modifier turns the switch on...
                SimulationModifier(name = "on", id = 0, formula = "", actions = "AllowOverwriting(true)"),
                // ...the second one only draws when it sees it on
                SimulationModifier(name = "draw", id = 1, formula = "IsOverwritingOn()", actions = "DrawLine(0, 0, 0, 3, 0)"),
            ),
        )
        val simulator = Simulator(model, simulation())
        simulator.oneStep()
        assertEquals(4, simulator.agents.count { it >= 0 }, "the second modifier saw the switch on")
    }

    // ---- visibility ----

    @Test
    fun testEverythingIsVisibleByDefaultAndHiddenByHide() {
        val simulator = Simulator(model("", "Hide(0)"), simulation())
        assertTrue(simulator.isGrainVisible(0), "nothing is hidden by default")
        assertTrue(simulator.hiddenGrains.isEmpty())
        simulator.oneStep()
        assertFalse(simulator.isGrainVisible(0), "Hide(0) hid the grain")
        assertEquals(setOf(0), simulator.hiddenGrains)
    }

    @Test
    fun testShowMakesTheGrainVisibleAgain() {
        val simulator = Simulator(model("", "Hide(0), Show(0)"), simulation())
        simulator.oneStep()
        assertTrue(simulator.isGrainVisible(0), "the last command wins")
        assertTrue(simulator.pendingGridCommands.contains(GridCommand.Hide(0)))
        assertTrue(simulator.pendingGridCommands.contains(GridCommand.Show(0)))
    }

    @Test
    fun testHidingAnUnknownGrainIsIgnored() {
        // the view silently ignores an id the model does not have: the core mirror does the same
        val simulator = Simulator(model("", "Hide(7)"), simulation())
        simulator.oneStep()
        assertTrue(simulator.isGrainVisible(7))
        assertTrue(simulator.hiddenGrains.isEmpty())
        // the command is still queued for the view, which decides
        assertEquals(listOf<GridCommand>(GridCommand.Hide(7)), simulator.pendingGridCommands)
    }

    @Test
    fun testIsVisibleAnswersInACondition() {
        val model = GrainModel(
            name = "state",
            grains = listOf(Grain(id = 0, name = "a", color = "Lime", movementProbability = 0.0)),
            modifiers = listOf(
                // the first modifier hides the grain...
                SimulationModifier(name = "hide", id = 0, formula = "", actions = "Hide(0)"),
                // ...so the second one, evaluated later in the same step, does not draw
                SimulationModifier(name = "draw", id = 1, formula = "IsVisible(0)", actions = "DrawLine(0, 0, 0, 3, 0)"),
            ),
        )
        val hidden = Simulator(model, simulation())
        hidden.oneStep()
        assertEquals(0, hidden.agents.count { it >= 0 }, "IsVisible(0) was false: nothing drawn")

        // with nothing hidden the same modifier draws: IsVisible(0) is true by default
        val visible = Simulator(
            model("IsVisible(0)", "DrawLine(0, 0, 0, 3, 0)"),
            simulation()
        )
        visible.oneStep()
        assertEquals(4, visible.agents.count { it >= 0 }, "IsVisible(0) is true by default")
    }

    @Test
    fun testTheEvaluatorAnswersTheVisibility() {
        val simulator = Simulator(model("", ""), simulation())
        val evaluator = createEvaluator(simulator).also { it.stateTestsAllowed = true }
        assertTrue(evaluator.evaluate("IsVisible(0)".parseExpression()).boolean)
        simulator.setGrainHidden(0, true)
        assertFalse(evaluator.evaluate("IsVisible(0)".parseExpression()).boolean)
        simulator.setGrainHidden(0, false)
        assertTrue(evaluator.evaluate("IsVisible(0)".parseExpression()).boolean)
    }

    @Test
    fun testTheEvaluatorAnswersTheSwitches() {
        val simulator = Simulator(model("", ""), simulation())
        val evaluator = createEvaluator(simulator).also { it.stateTestsAllowed = true }
        assertFalse(evaluator.evaluate("IsToricSelectionOn()".parseExpression()).boolean)
        assertFalse(evaluator.evaluate("IsOverwritingOn()".parseExpression()).boolean)
        simulator.toricSelectionOn = true
        assertTrue(evaluator.evaluate("IsToricSelectionOn()".parseExpression()).boolean)
        assertFalse(evaluator.evaluate("IsOverwritingOn()".parseExpression()).boolean)
    }

    // ---- the tests only live in the condition of a modifier ----

    @Test
    fun testTheStateTestsAreAcceptedInTheCondition() {
        assertNull(validateModifierCondition("IsToricSelectionOn()"))
        assertNull(validateModifierCondition("IsOverwritingOn()"))
        assertNull(validateModifierCondition("IsVisible(0)"))
        assertNull(validateModifierCondition("IsVisible(0) && IsToricSelectionOn()"))
        assertNull(validateModifierCondition("step > 3 && !IsVisible(1)"))
    }

    @Test
    fun testTheStateTestsAreRefusedInAnActionFormula() {
        val message = validateModifierActions("IsBehaviourActive(0)")!!
        assertTrue(message.contains("state test"), "clear message expected, got: $message")
        assertTrue(message.contains("condition of a simulation modifier"))
        assertTrue(validateModifierActions("IsVisible(0)")!!.contains("state test"))
        assertTrue(validateModifierActions("Add(0, 2), IsToricSelectionOn()")!!.contains("state test"))
        // an action argument may not contain one either
        assertTrue(validateModifierActions("Add(0, IsBehaviourActive(0), 1)")!!.contains("state test"))
        // a behaviour action list refuses them the same way
        assertTrue(validateActionFormula("IsBehaviourActive(0)")!!.contains("state test"))
        assertTrue(validateActionFormula("DrawLine(0, 0, 0, IsVisible(0), 1)")!!.contains("state test"))
    }

    @Test
    fun testTheStateTestsAreRefusedInABehaviourCondition() {
        val message = validatePredicateFormula("IsVisible(0)")!!
        assertTrue(message.contains("state test"), "clear message expected, got: $message")
        assertTrue(message.contains("condition of a behaviour"))
        assertTrue(validatePredicateFormula("IsBehaviourActive(0) && count(0) > 2")!!.contains("state test"))
        assertTrue(validatePredicateFormula("IsToricSelectionOn()")!!.contains("state test"))
        // a plain predicate is still accepted
        assertNull(validatePredicateFormula("count(0) > 2 && step < 20"))
    }

    @Test
    fun testTheStateTestsAreRefusedInAFieldFormula() {
        val message = validateFormula("IsVisible(0) ? 1 : 0")!!
        assertTrue(message.contains("state test"), "clear message expected, got: $message")
    }

    @Test
    fun testArityAndTypeOfTheNewTests() {
        assertTrue(
            validateModifierCondition("IsVisible()")!!
                .contains("IsVisible expects 1 argument, got 0")
        )
        assertTrue(
            validateModifierCondition("IsVisible(0, 1)")!!
                .contains("IsVisible expects 1 argument, got 2")
        )
        assertTrue(
            validateModifierCondition("IsVisible(true)")!!
                .contains("IsVisible expects an integer id")
        )
        assertTrue(
            validateModifierCondition("IsToricSelectionOn(0)")!!
                .contains("IsToricSelectionOn expects 0 arguments, got 1")
        )
        assertTrue(
            validateModifierCondition("IsOverwritingOn(1)")!!
                .contains("IsOverwritingOn expects 0 arguments, got 1")
        )
    }

    @Test
    fun testTheModifierIsDiagnosed() {
        val model = GrainModel(
            name = "state",
            grains = listOf(Grain(id = 0, name = "a", color = "Lime")),
            modifiers = listOf(
                SimulationModifier(name = "bad", id = 0, formula = "", actions = "IsBehaviourActive(0)"),
                SimulationModifier(name = "good", id = 1, formula = "IsVisible(0)", actions = "Hide(0)"),
            ),
        )
        val problems = model.diagnose(English)
        assertEquals(1, problems.size, "only the modifier with a state test in its actions is wrong")
        assertTrue(problems.single().message.contains("state test"))
        assertEquals("Actions", problems.single().property)
    }
}
