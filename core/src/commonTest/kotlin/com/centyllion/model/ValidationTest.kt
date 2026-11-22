package com.centyllion.model

import com.centyllion.i18n.English
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A behaviour whose reactive and product are both None ("None -> None") cannot produce any
 * agent. Without actions it does nothing at all; with actions it may legitimately act on the
 * simulation (SetField…), so the warning must say what is really wrong.
 */
class ValidationTest {

    private fun noneToNone(actions: String = "") = Behaviour(
        name = "field change",
        mainReactiveId = -1,
        mainProductId = -1,
        actions = actions,
    )

    private fun messages(behaviour: Behaviour): List<String> =
        buildList { behaviour.diagnose(GrainModel(behaviours = listOf(behaviour)), English, this) }
            .map { it.message }

    @Test
    fun testNoneToNoneWithoutActionIsMeaningless() {
        val messages = messages(noneToNone())
        assertTrue(messages.any { it.contains("no effect") }, "unexpected messages: $messages")
    }

    @Test
    fun testNoneToNoneWithAnActionWarnsAboutTheMissingAgent() {
        val messages = messages(noneToNone(actions = "SetField(0, 0, 0, 1)"))
        assertTrue(
            messages.any { it.contains("produces no agent") },
            "an action on a None -> None behaviour must warn that no agent is produced: $messages"
        )
        assertTrue(
            messages.none { it.contains("no effect") },
            "the behaviour is not inert when it has actions: $messages"
        )
    }

    @Test
    fun testTheActionItselfMustBeValid() {
        val messages = messages(noneToNone(actions = "SetField(0, 0, 0, 1), Oops(1, 2)"))
        assertTrue(messages.any { it.startsWith("Actions") || it.contains("Oops") || it.contains("unknown") },
            "an unknown action must be reported: $messages")
    }

    @Test
    fun testABehaviourThatProducesAnAgentIsNotWarned() {
        val model = GrainModel(grains = listOf(Grain(id = 0, name = "A", halfLife = 0)))
        val behaviour = Behaviour(name = "produces", mainReactiveId = 0, mainProductId = 0)
        val messages = buildList { behaviour.diagnose(model, English, this) }.map { it.message }
        assertTrue(messages.isEmpty(), "unexpected messages: $messages")
    }

    @Test
    fun testTheNoneToNoneWarningIsFlaggedAsAWarning() {
        val problems = buildList {
            noneToNone(actions = "SetField(0, 0, 0, 1)").diagnose(GrainModel(), English, this)
        }
        assertTrue(problems.all { it.warning }, "this remark is non-blocking: $problems")

        val errors = buildList { noneToNone().diagnose(GrainModel(), English, this) }
        assertTrue(errors.none { it.warning }, "a behaviour without any effect is an error: $errors")
    }

    @Test
    fun testTheWarningIsTranslated() {
        val french = com.centyllion.i18n.French
        val message = french.i18n(
            "Behaviour produces no agent (None -> None): its actions alone act on the simulation, for example SetField(0, 0, 0, 1), possibly under a predicate on the simulation step."
        )
        assertEquals(
            "Le comportement ne produit aucun agent (None -> None) : seules ses actions agissent sur la simulation, par exemple SetField(0, 0, 0, 1), éventuellement sous condition d'un prédicat sur l'étape de simulation.",
            message
        )
    }
}
