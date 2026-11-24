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
        p(i18n("Actions such as Add, Move or SetField act around the cell where a behaviour applies. Meta-actions act on the simulation itself: they add or erase agents in a zone, draw a line, or switch a behaviour or another modifier on and off.")),
        p(i18n("A second family drives the run loop instead of the grid: Pause() stops the run, Continue() resumes it, RunOneStep() and RunNStep(N) run one or N steps then pause, Rewind() goes back to the initial state without restarting and Restart() goes back and starts again. The loop meta-actions and the state tests start with a capital letter; the functions of the formulas stay in lower case (abs, min, count, field, agent).")),
        p(i18n("They are no longer written in a behaviour: the behaviour editor refuses them. They belong to a simulation modifier, in the section « Simulation modifiers », which only appears in expert mode. This tutorial puts you in expert mode and builds a small model to work on."))
    )

    /** The example project: two grains, two behaviours, ready for a modifier. */
    override fun prepare() {
        val cell = Grain(id = 0, name = "cell", color = "Lime", icon = "square-full", movementProbability = 0.0)
        val virus = Grain(id = 1, name = "Virus", color = "Crimson", icon = "square-full", movementProbability = 1.0)
        val model = GrainModel(
            name = "Modifiers",
            description = "A grain and a behaviour, ready for a modifier",
            grains = listOf(cell, virus),
            behaviours = listOf(
                Behaviour(name = "stay", mainReactiveId = 0, mainProductId = 0, id = 0),
                Behaviour(
                    name = "Kill", mainReactiveId = 0, mainProductId = 1, id = 1,
                    reaction = listOf(Reaction(reactiveId = 1, productId = 1, sourceReactive = 1))
                )
            )
        )
        page.model = ModelAndSimulation(model, createSimulation("Modifiers", size = 100) { if (it % 97 == 0) 0 else -1 })
        page.expertMode = true
    }

    private val modifiers = { page.modelController.modifiersController.root }

    private fun modifierWithPredicate(): SimulationModifier? =
        page.model.model.modifiers.firstOrNull { it.formula.isNotBlank() }

    private fun modifierReady(): SimulationModifier? =
        page.model.model.modifiers.firstOrNull {
            validateModifierActions(it.actions) == null && it.actions.contains("Unuse")
        }

    override val steps: List<TutorialStep> = listOf(
        TutorialStep(
            title = i18n("1. Create a modifier"),
            content = listOf(
                p(i18n("In the explorer, find the section « Simulation modifiers » under the behaviours, then click its + button.")),
                p(i18n("The model holds two grains — cell (0) and Virus (1) — and two behaviours: stay (0), which does nothing, and Kill (1), which turns a cell into a virus when it is next to a virus.")),
                p(i18n("A row appears, like the ones of the grains, the fields and the behaviours: On/Off radio, name, identifier, probability. This one gets the identifier 0."))
            ),
            selector = modifiers,
            validated = { page.model.model.modifiers.isNotEmpty() }
        ),
        TutorialStep(
            title = i18n("2. Its condition: step == 10"),
            content = listOf(
                p(i18n("Select the modifier (a click anywhere in its white box), then write its Condition:")),
                code2("step == 10"),
                p(i18n("A modifier is evaluated once per step, after the behaviours. With this condition it does nothing at steps 0 to 9, acts at step 10, and does nothing afterwards.")),
                p(i18n("That is the whole point of the condition: without it, the modifier would run at every step, and its actions would be repeated indefinitely.")),
                p(i18n("The condition is evaluated outside any cell: step, agent(x, y), age(x, y), count(id, x, y), field(id, x, y), and the state tests IsBehaviourActive/IsModifierActive/IsToricSelectionOn/IsOverwritingOn/IsVisible, which are written here and nowhere else — an actions formula and the condition of a behaviour refuse them."))
            ),
            selector = modifiers,
            validated = { modifierWithPredicate() != null }
        ),
        TutorialStep(
            title = i18n("3. Its actions: fill a zone, then switch itself off"),
            content = listOf(
                p(i18n("In the Actions field of the same modifier, write the two meta-actions, separated by a comma:")),
                code2("AddRect(1, 50, 10, 10, 20, 20), Unuse(0)"),
                p(i18n("AddRect(1, 50, 10, 10, 20, 20) adds up to fifty viruses — grain 1 — inside the rectangle (10, 10)-(20, 20). The positions are drawn among the empty cells of the zone.")),
                p(i18n("Unuse(0) switches off the modifier 0 — this one — for the rest of the run. Unuse targets a modifier, where Inactivate targets a behaviour: Use(id) and Unuse(id) are the On/Off radio of a modifier, written in a formula.")),
                p(i18n("Chained after the condition step == 10, the whole thing therefore fires exactly once: at step 10 fifty viruses appear in the rectangle, and the modifier switches itself off so that it can never fire again, whatever the condition becomes."))
            ),
            selector = modifiers,
            validated = { modifierReady() != null }
        ),
        TutorialStep(
            title = i18n("4. Run the simulation"),
            content = listOf(
                p(i18n("Go to the Simulation tab and start the run. Watch the step counter: nothing appears in the rectangle before step 10.")),
                p(i18n("At step 10 fifty viruses appear in the rectangle. They then spread from cell to cell through the behaviour Kill, and the radio of the modifier is off: it has switched itself off, exactly once.")),
                p(i18n("Rewind: the modifier is active again, as it was at the start of the run, and the scenario replays identically."))
            ),
            selector = modifiers,
            validated = { page.model.simulation.agents.any { it == 1 } }
        )
    )

    override val conclusion: List<BulmaElement> = listOf(
        p(i18n("Run it: at step 10 the rectangle fills with viruses, then they spread cell by cell through the behaviour Kill. The modifier itself is off from that moment on.")),
        p(i18n("A modifier is evaluated once per step, after the behaviours. Its condition is evaluated outside any cell: use step, agent(x, y), age(x, y), count(id, x, y), field(id, x, y), and IsBehaviourActive/IsModifierActive/IsToricSelectionOn/IsOverwritingOn/IsVisible to test the state of the simulation; their negation is written with !."))
    )

    override val next: Tutorial<ShowPage>? = null

    private fun code2(text: String): BulmaElement = p(text)
}
