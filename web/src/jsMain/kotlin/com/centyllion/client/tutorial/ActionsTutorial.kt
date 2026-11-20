package com.centyllion.client.tutorial

import bulma.*
import com.centyllion.client.controller.model.BehaviourEditController
import com.centyllion.client.page.ShowPage
import com.centyllion.client.takeInstance
import com.centyllion.model.*
import kotlinx.html.*

/**
 * Expert tutorial: actions and age predicates.
 *
 * A "runner" agent moves and, at every step, leaves a "crumb" behind it
 * (`Add(1, 0, 1)`); the crumb disappears after six steps (`age(1) > 5` with the
 * main product set to none). The two formulas are typed by the user.
 */
class ActionsTutorial(
    override val page: ShowPage
) : Tutorial<ShowPage> {

    override val name = i18n("Actions: a trail that fades (expert mode)")

    private val addAction = "Add(1, 0, 1)"
    private val fadeFormula = "age(1) > 5"

    override val introduction: List<BulmaElement> = listOf(
        p(i18n("With this tutorial you will use actions to make an agent leave a trail of crumbs behind it, and an age predicate to make the trail fade.")),
        p(i18n("Actions are a comma separated list of calls executed when the behaviour is applied: Add(agent, dx, dy), Remove(agent, dx, dy), Move(agent, dx1, dy1, dx2, dy2), SetField(field, dx, dy, value), AddField(field, dx, dy, delta) and ClearField(field, dx, dy).")),
        p(i18n("dx and dy are offsets in cells around the behaviour cell, and can be formulas (x, y, step, count(id), age(n)…)."))
    )

    override fun prepare() {
        val runner = Grain(
            id = 0, name = "runner", color = "DodgerBlue", icon = "square-full",
            movementProbability = 1.0
        )
        val crumb = Grain(
            id = 1, name = "crumb", color = "Orange", icon = "square", movementProbability = 0.0
        )
        val model = GrainModel(
            name = "Trail",
            description = "An agent that leaves a fading trail",
            grains = listOf(runner, crumb),
            behaviours = listOf(
                Behaviour(
                    "run", "The runner moves and leaves a crumb behind it",
                    mainReactiveId = 0, mainProductId = 0
                ),
                Behaviour(
                    "fade", "A crumb disappears after six steps",
                    mainReactiveId = 1, mainProductId = -1
                )
            )
        )
        page.model = ModelAndSimulation(model, createSimulation("Trail", size = 100))
        // starts in normal mode: enabling expert mode is the second step
        page.expertMode = false
    }

    private fun behaviour(name: String): Behaviour? = page.model.model.behaviours.find { it.name == name }

    private fun isOpen(name: String): Boolean =
        (page.modelController.editorController as? BehaviourEditController)?.data?.name == name

    private fun behaviourRow(name: String) =
        page.modelController.behavioursController.dataControllers.find { it.data.name == name }?.root
            ?: page.modelController.addBehaviourButton.root

    private fun actionsInput() =
        page.modelController.editorController
            .takeInstance<BehaviourEditController>()
            ?.actionsController?.root
            ?: page.modelController.addBehaviourButton.root

    private fun formulaInput() =
        page.modelController.editorController
            .takeInstance<BehaviourEditController>()
            ?.formulaController?.root
            ?: page.modelController.addBehaviourButton.root

    override val steps: List<TutorialStep> = listOf(
        TutorialStep(
            i18n("Open the model page"),
            listOf(p(i18n("The model is created: go to the model page to write the action and the age predicate."))),
            { page.modelController.editionItem.title.root },
            { page.modelController.editionTabPages.selectedPage == page.modelController.editionItem }
        ),
        TutorialStep(
            i18n("Enable expert mode"),
            listOf(p(i18n("Actions are only editable in expert mode: switch on 'Expert Mode'."))),
            { page.expertModeSwitch.root },
            { page.expertMode }
        ),
        TutorialStep(
            i18n("Open the 'run' behaviour"),
            listOf(p(i18n("Select the behaviour named 'run': it applies to the runner and keeps it alive."))),
            { behaviourRow("run") },
            { isOpen("run") }
        ),
        TutorialStep(
            i18n("Leave a crumb"),
            listOf(
                p(i18n("In the actions field, write: %0", addAction)),
                p(i18n("Add(1, 0, 1) creates an agent of type 1 (the crumb) one cell below the runner, if that cell is empty."))
            ),
            { actionsInput() },
            { actionAddsGrain(behaviour("run")?.actions ?: "", 1) }
        ),
        TutorialStep(
            i18n("Open the 'fade' behaviour"),
            listOf(p(i18n("Select the behaviour named 'fade': it applies to the crumb, whose main product is already 'none' (the crumb disappears)."))),
            { behaviourRow("fade") },
            { isOpen("fade") }
        ),
        TutorialStep(
            i18n("Fade after six steps"),
            listOf(
                p(i18n("In the predicate formula, write: %0", fadeFormula)),
                p(i18n("age(1) is the age of the main agent: the crumb is removed as soon as it is older than five steps."))
            ),
            { formulaInput() },
            { matchesAgeRule(behaviour("fade")?.formula ?: "", aliveAge = 6, deadAge = 2) }
        ),
        TutorialStep(
            i18n("Open the simulation page"),
            listOf(p(i18n("Let's draw some runners and watch the trail."))),
            { page.modelController.simulationItem.title.root },
            { page.modelController.editionTabPages.selectedPage == page.modelController.simulationItem }
        ),
        TutorialStep(
            i18n("Draw some runners"),
            listOf(p(i18n("Select the runner agent and spray a few agents (the spray can) on the grid."))),
            { page.simulationController.simulationViewController.randomAddButton.root },
            { (page.simulationController.simulator.grainsCounts()[0] ?: 0) >= 5 }
        ),
        TutorialStep(
            i18n("Run the simulation"),
            listOf(p(i18n("Watch the runners move: each of them leaves a trail of crumbs that disappears after a few steps."))),
            { page.simulationController.runButton.root },
            { page.simulationController.simulator.step > 20 },
            { page.simulationController.stop() }
        )
    )

    override val conclusion: List<BulmaElement> = listOf(
        SubTitle(i18n("You know how to use actions, well done 👍.")),
        wrap("content") {
            p { +i18n("You can now for instance:") }
            ul {
                li { +i18n("Eat a crumb above the runner with Remove(1, 0, -1).") }
                li { +i18n("Make the crumbs fall with Move(1, 0, 0, 0, 1) in a behaviour applied to the crumb.") }
                li { +i18n("Paint a field where the runner passes with AddField(0, 0, 0, 0.2) (a field must exist).") }
                li { +i18n("Use a formula in an action: Add(1, 0, min(3, count(0))) spreads the crumbs further in crowded places.") }
            }
        }
    )

    override val next: Tutorial<ShowPage> get() = FieldFormulaTutorial(page)
}
