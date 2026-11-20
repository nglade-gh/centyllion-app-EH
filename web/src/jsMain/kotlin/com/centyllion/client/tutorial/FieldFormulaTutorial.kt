package com.centyllion.client.tutorial

import bulma.*
import com.centyllion.client.controller.model.FieldEditController
import com.centyllion.client.page.ShowPage
import com.centyllion.client.takeInstance
import com.centyllion.model.*
import com.centyllion.model.Field
import kotlinx.html.*

/**
 * Expert tutorial: a field driven by a formula, giving a gradient that moves in
 * space and in time.
 *
 * The field formula replaces the value computed by diffusion and production
 * (`current`), and is evaluated at every step for every cell with `x`, `y`,
 * `step`, `current`, `count(id)` and `field(id)` available.
 */
class FieldFormulaTutorial(
    override val page: ShowPage
) : Tutorial<ShowPage> {

    override val name = i18n("Field formula: a moving gradient (expert mode)")

    private val formula = "0.5 + 0.5 * sin(x / 10 + step / 5)"

    override val introduction: List<BulmaElement> = listOf(
        p(i18n("With this tutorial you will drive a field with a formula: instead of diffusing and decaying, the field is computed at every step for every cell.")),
        p(i18n("The formula can use x and y (the cell coordinates), step (the simulation step), current (the value after diffusion and production), count(id) (the number of agents of type id around the cell) and field(id).")),
        p(i18n("The model already contains an agent and a field: you only have to write the formula and run the simulation."))
    )

    override fun prepare() {
        val seed = Grain(id = 0, name = "seed", color = "Lime", icon = "square-full", movementProbability = 0.0)
        val wave = Field(id = 0, name = "wave", color = "SkyBlue", speed = 0.2f, halfLife = 10)
        val model = GrainModel(
            name = "Moving gradient",
            description = "A field computed by a formula",
            grains = listOf(seed),
            fields = listOf(wave)
        )
        page.model = ModelAndSimulation(model, createSimulation("Moving gradient", size = 100))
    }

    private fun field(): Field? = page.model.model.fields.firstOrNull()

    private fun isFieldOpen(): Boolean = page.modelController.editorController is FieldEditController

    private fun fieldRow() =
        page.modelController.fieldsController.dataControllers.firstOrNull()?.root
            ?: page.modelController.addFieldButton.root

    private fun formulaInput() =
        page.modelController.editorController
            .takeInstance<FieldEditController>()
            ?.formulaController?.root
            ?: page.modelController.addFieldButton.root

    override val steps: List<TutorialStep> = listOf(
        TutorialStep(
            i18n("Open the model page"),
            listOf(p(i18n("The model is created: go to the model page to write the field formula."))),
            { page.modelController.editionItem.title.root },
            { page.modelController.editionTabPages.selectedPage == page.modelController.editionItem }
        ),
        TutorialStep(
            i18n("Open the field"),
            listOf(p(i18n("Select the field named 'wave' to edit it."))),
            { fieldRow() },
            { isFieldOpen() }
        ),
        TutorialStep(
            i18n("Write the formula"),
            listOf(
                p(i18n("In the formula field, write: %0", formula)),
                p(i18n("x is the column, step the simulation step: the field becomes a wave that travels from left to right.")),
                p(i18n("Leave the speed and the half-life as they are: the formula replaces their result."))
            ),
            { formulaInput() },
            { isMovingGradient(field()?.formula ?: "") }
        ),
        TutorialStep(
            i18n("Open the simulation page"),
            listOf(p(i18n("Let's see the field moving."))),
            { page.modelController.simulationItem.title.root },
            { page.modelController.editionTabPages.selectedPage == page.modelController.simulationItem }
        ),
        TutorialStep(
            i18n("Run the simulation"),
            listOf(p(i18n("The field is now a gradient coloured by the agent's field: watch the bands move as the simulation runs."))),
            { page.simulationController.runButton.root },
            { page.simulationController.simulator.step > 20 },
            { page.simulationController.stop() }
        )
    )

    override val conclusion: List<BulmaElement> = listOf(
        SubTitle(i18n("You can now drive a field with a formula, well done 👍.")),
        wrap("content") {
            p { +i18n("You can now for instance:") }
            ul {
                li { +i18n("Write x / 100 for a static gradient from left to right.") }
                li { +i18n("Write current * 0.98 for a field that slowly fades without moving.") }
                li { +i18n("Write min(1, count(0) / 8) to paint the field where the agents are dense.") }
                li { +i18n("Write 0.5 + 0.5 * sin((x - y) / 8 - step / 4) for a diagonal wave.") }
            }
        }
    )

    override val next: Tutorial<ShowPage>? = null
}
