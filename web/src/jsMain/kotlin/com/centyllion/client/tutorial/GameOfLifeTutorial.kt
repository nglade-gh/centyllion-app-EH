package com.centyllion.client.tutorial

import bulma.*
import com.centyllion.client.controller.model.BehaviourEditController
import com.centyllion.client.page.ShowPage
import com.centyllion.client.takeInstance
import com.centyllion.model.*
import kotlinx.html.*

/**
 * Expert tutorial: Conway's Game of Life written with three formulas on `count(0)`.
 *
 * The agents and the three behaviours (survive / die / birth) are created by
 * [prepare] so the tutorial focuses on the expert part: the rules. The recipe is
 * covered by `GameOfLifeTest` in the core module (blinker, block, glider).
 */
class GameOfLifeTutorial(
    override val page: ShowPage
) : Tutorial<ShowPage> {

    override val name = i18n("Conway's Game of Life (expert mode)")

    private val surviveFormula = "count(0) == 2 || count(0) == 3"
    private val dieFormula = "count(0) < 2 || count(0) > 3"
    private val birthFormula = "count(0) == 3"

    override val introduction: List<BulmaElement> = listOf(
        p(i18n("With this tutorial you will write Conway's Game of Life: one agent, three behaviours and three formulas that you type yourself.")),
        p(i18n("The agent and the three behaviours (survive, die, birth) are already created, with their reactants and products. Your job is the rules, written with count(0) — the number of neighbours of the same agent around the cell.")),
        p(i18n("A cell survives with 2 or 3 neighbours, dies with less than 2 or more than 3, and is born in an empty place surrounded by exactly 3 neighbours."))
    )

    override fun prepare() {
        val cell = Grain(id = 0, name = "cell", color = "Lime", icon = "square-full", movementProbability = 0.0)
        val model = GrainModel(
            name = "Game of Life",
            description = "Conway's Game of Life",
            grains = listOf(cell),
            behaviours = listOf(
                Behaviour(
                    "survive", "A cell with 2 or 3 neighbours stays alive",
                    mainReactiveId = 0, mainProductId = 0
                ),
                Behaviour(
                    "die", "A cell with less than 2 or more than 3 neighbours dies",
                    mainReactiveId = 0, mainProductId = -1
                ),
                Behaviour(
                    "birth", "An empty place with exactly 3 neighbours becomes a cell",
                    mainReactiveId = -1, mainProductId = 0
                )
            )
        )
        page.model = ModelAndSimulation(model, createSimulation("Game of Life", size = 100))
        // starts in normal mode: enabling expert mode is the second step
        page.expertMode = false
    }

    private fun behaviour(name: String): Behaviour? = page.model.model.behaviours.find { it.name == name }

    private fun formula(name: String): String = behaviour(name)?.formula ?: ""

    private fun isOpen(name: String): Boolean =
        (page.modelController.editorController as? BehaviourEditController)?.data?.name == name

    private fun behaviourRow(name: String) =
        page.modelController.behavioursController.dataControllers.find { it.data.name == name }?.root
            ?: page.modelController.addBehaviourButton.root

    private fun formulaInput() =
        page.modelController.editorController
            .takeInstance<BehaviourEditController>()
            ?.formulaController?.root
            ?: page.modelController.addBehaviourButton.root

    private fun cells() = page.simulationController.simulator.grainsCounts()[0] ?: 0

    override val steps: List<TutorialStep> = listOf(
        TutorialStep(
            i18n("Open the model page"),
            listOf(p(i18n("The model is created: go to the model page to write the three rules."))),
            { page.modelController.editionItem.title.root },
            { page.modelController.editionTabPages.selectedPage == page.modelController.editionItem }
        ),
        TutorialStep(
            i18n("Enable expert mode"),
            listOf(p(i18n("Formulas are only editable in expert mode: switch on 'Expert Mode'."))),
            { page.expertModeSwitch.root },
            { page.expertMode }
        ),
        TutorialStep(
            i18n("Open the 'survive' behaviour"),
            listOf(p(i18n("Select the behaviour named 'survive': it keeps a cell alive, its product is the cell itself."))),
            { behaviourRow("survive") },
            { isOpen("survive") }
        ),
        TutorialStep(
            i18n("Survival rule"),
            listOf(
                p(i18n("In the predicate formula, write: %0", surviveFormula)),
                p(i18n("count(0) is the number of neighbouring cells of the same agent (the cell itself is not counted)."))
            ),
            { formulaInput() },
            { matchesCountRule(formula("survive"), trueFor = setOf(2, 3), falseFor = setOf(0, 1, 4, 5, 8)) }
        ),
        TutorialStep(
            i18n("Open the 'die' behaviour"),
            listOf(p(i18n("Select the behaviour named 'die': its main product is already 'none', so the cell disappears."))),
            { behaviourRow("die") },
            { isOpen("die") }
        ),
        TutorialStep(
            i18n("Death rule"),
            listOf(p(i18n("In the predicate formula, write: %0", dieFormula))),
            { formulaInput() },
            { matchesCountRule(formula("die"), trueFor = setOf(0, 1, 4, 5, 8), falseFor = setOf(2, 3)) }
        ),
        TutorialStep(
            i18n("Open the 'birth' behaviour"),
            listOf(p(i18n("Select the behaviour named 'birth': its main reactive is 'none' (spontaneous generation), its product is the cell."))),
            { behaviourRow("birth") },
            { isOpen("birth") }
        ),
        TutorialStep(
            i18n("Birth rule"),
            listOf(p(i18n("In the predicate formula, write: %0", birthFormula))),
            { formulaInput() },
            { matchesCountRule(formula("birth"), trueFor = setOf(3), falseFor = setOf(0, 1, 2, 4, 5, 8)) }
        ),
        TutorialStep(
            i18n("Open the simulation page"),
            listOf(p(i18n("Let's draw a pattern and run it."))),
            { page.modelController.simulationItem.title.root },
            { page.modelController.editionTabPages.selectedPage == page.modelController.simulationItem }
        ),
        TutorialStep(
            i18n("Draw a blinker"),
            listOf(p(i18n("Select the cell agent, take the pen and draw three cells in a vertical line (they must touch each other)."))),
            { page.simulationController.simulationViewController.simulationCanvas.root },
            { cells() >= 3 }
        ),
        TutorialStep(
            i18n("Run the simulation"),
            listOf(p(i18n("Watch the line: it must oscillate between vertical and horizontal at every step. This pattern is a blinker."))),
            { page.simulationController.runButton.root },
            { page.simulationController.simulator.step > 20 },
            { page.simulationController.stop() }
        )
    )

    override val conclusion: List<BulmaElement> = listOf(
        SubTitle(i18n("You have just written Conway's Game of Life, well done 👍.")),
        wrap("content") {
            p { +i18n("You can now for instance:") }
            ul {
                li { +i18n("Try the R-pentomino (5 cells): it evolves for more than 1000 steps before stabilising.") }
                li { +i18n("Draw a glider (5 cells) and check that it moves by one cell every four steps.") }
                li { +i18n("Change the rules (for instance a cell survives with 2, 3 or 4 neighbours) and watch the patterns die out.") }
                li { +i18n("Add a second agent with another rule to build a two-species predator/prey world.") }
            }
            p {
                +i18n("Documentation on formulas: ")
                a("https://centyllion.com/fr/documentation.html", "_blank") {
                    span("icon is-primary") { i("fas fa-book") }
                }
                + "."
            }
        }
    )

    override val next: Tutorial<ShowPage> get() = ActionsTutorial(page)
}
