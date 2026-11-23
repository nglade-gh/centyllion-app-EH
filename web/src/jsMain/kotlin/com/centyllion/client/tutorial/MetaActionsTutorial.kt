package com.centyllion.client.tutorial

import bulma.*
import com.centyllion.client.page.ShowPage
import com.centyllion.model.*
import kotlinx.html.*

/**
 * Tutorial on the simulation modifiers, the only place where the meta-actions may be used.
 *
 * Each step waits for a real action of the user on the model: it advances when the model
 * really changed, never by itself (a step whose condition is always true would be skipped and
 * the tutorial would jump to its conclusion).
 */
class MetaActionsTutorial(
    override val page: ShowPage
) : Tutorial<ShowPage> {

    override val name = i18n("Simulation modifiers: meta-actions")

    override val introduction: List<BulmaElement> = listOf(
        p(i18n("Actions such as Add, Move or SetField act around the cell where a behaviour applies. Meta-actions act on the simulation itself: they add or erase agents in a zone, draw a line, pause the run, or switch a behaviour or another modifier on and off.")),
        p(i18n("They are no longer written in a behaviour: the behaviour editor refuses them. They belong to a simulation modifier, in the section « Simulation modifiers », which only appears in expert mode. This tutorial puts you in expert mode and builds a small model to work on."))
    )

    /** A single grain with one behaviour, so there is something to modify. */
    override fun prepare() {
        val grain = Grain(id = 0, name = "cell", color = "Lime", icon = "square-full", movementProbability = 0.0)
        val model = GrainModel(
            name = "Modifiers",
            description = "A grain and a behaviour, ready for a modifier",
            grains = listOf(grain),
            behaviours = listOf(
                Behaviour(name = "stay", mainReactiveId = 0, mainProductId = 0, id = 0)
            )
        )
        page.model = ModelAndSimulation(model, createSimulation("Modifiers", size = 100) { if (it % 97 == 0) 0 else -1 })
        page.expertMode = true
    }

    private val modifiers = { page.modelController.modifiersController.root }

    private fun modifierWithActions(): SimulationModifier? =
        page.model.model.modifiers.firstOrNull { it.actions.isNotBlank() && validateModifierActions(it.actions) == null }

    override val steps: List<TutorialStep> = listOf(
        TutorialStep(
            title = i18n("Create a modifier"),
            content = listOf(
                p(i18n("In the explorer, find the section « Simulation modifiers » under the behaviours, then click its + button.")),
                p(i18n("A row appears, like the ones of the grains, the fields and the behaviours: On/Off radio, name, identifier, probability."))
            ),
            selector = modifiers,
            validated = { page.model.model.modifiers.isNotEmpty() }
        ),
        TutorialStep(
            title = i18n("Write its meta-actions"),
            content = listOf(
                p(i18n("Select the modifier (a click anywhere in its white box), then write its Actions, for example:")),
                code2("AddRect(0, 5, 10, 10, 20, 20)"),
                p(i18n("This fills up to five cells of type 0 inside the rectangle (10, 10)-(20, 20). The positions are drawn among the empty cells of the zone.")),
                p(i18n("The help button in front of the Actions field lists all the meta-actions, DrawLine and Use/Unuse included."))
            ),
            selector = modifiers,
            validated = { modifierWithActions() != null }
        ),
        TutorialStep(
            title = i18n("Run it and switch it off"),
            content = listOf(
                p(i18n("Run the simulation: the zone fills up once, at the first step, and nothing more happens afterwards, since the zone is full.")),
                p(i18n("Click the On/Off radio of the modifier to switch it off. A modifier can also be switched by another one, with Unuse(id), and switched back with Use(id)."))
            ),
            selector = modifiers,
            validated = { page.model.model.modifiers.any { !it.enabled } }
        )
    )

    override val conclusion: List<BulmaElement> = listOf(
        p(i18n("A modifier is evaluated once per step, after the behaviours. Its condition is evaluated outside any cell: use step, agent(x, y), age(x, y), count(id, x, y), field(id, x, y), and IsActive/IsInactive/IsInUse/IsNotInUse to test the activation state."))
    )

    override val next: Tutorial<ShowPage>? = null

    private fun code2(text: String): BulmaElement = p(text)
}
