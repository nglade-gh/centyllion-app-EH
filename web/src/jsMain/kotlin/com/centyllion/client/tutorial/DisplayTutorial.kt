package com.centyllion.client.tutorial

import bulma.*
import com.centyllion.client.page.ShowPage
import com.centyllion.model.*
import kotlinx.html.*

/**
 * Tutorial on the settings that act on what you see and on which behaviours run: behaviour
 * identifiers and activation, field opacity, custom grid size.
 *
 * Every step waits for a real change of the model: a step validating by itself would be
 * skipped immediately and the tutorial would jump to its conclusion.
 */
class DisplayTutorial(
    override val page: ShowPage
) : Tutorial<ShowPage> {

    override val name = i18n("Identifiers, activation, opacity and grid size")

    override val introduction: List<BulmaElement> = listOf(
        p(i18n("This tour covers the settings that act on what you see and on which behaviours run: behaviour identifiers, the On/Off radio, the opacity of the fields and the size of the grid.")),
        p(i18n("A small model is created for you: one grain, one field and one behaviour."))
    )

    override fun prepare() {
        val grain = Grain(id = 0, name = "cell", color = "Lime", icon = "square-full", movementProbability = 1.0)
        // qualified: bulma.Field is imported by the UI package
        val field = com.centyllion.model.Field(id = 0, name = "signal", color = "SkyBlue")
        val model = GrainModel(
            name = "Display",
            description = "Settings tour",
            grains = listOf(grain),
            fields = listOf(field),
            behaviours = listOf(
                Behaviour(
                    name = "grow", mainReactiveId = 0, mainProductId = 0, id = 0,
                    actions = "AddField(0, 0, 0, 0.2)"
                )
            )
        )
        page.model = ModelAndSimulation(model, createSimulation("Display", size = 100) { if (it % 53 == 0) 0 else -1 })
        page.expertMode = true
    }

    private val settings = { page.modelController.settingsController.container.root }
    private val behaviours = { page.modelController.behavioursController.root }

    override val steps: List<TutorialStep> = listOf(
        TutorialStep(
            title = i18n("Behaviours: identifier and activation"),
            content = listOf(
                p(i18n("Each behaviour has an identifier, displayed after its name in the explorer: name (id). That identifier is what Activate(id) and Inactivate(id) target.")),
                p(i18n("Click the On/Off radio in front of the behaviour: it switches the behaviour off. An inactive behaviour is never applied, but keeps all its settings."))
            ),
            selector = behaviours,
            validated = { page.model.model.behaviours.any { !it.enabled } }
        ),
        TutorialStep(
            title = i18n("Field opacity"),
            content = listOf(
                p(i18n("Open the field « signal »: below the formula, under a blue separator, an Opacity setting goes from 0 to 100 %.")),
                p(i18n("Set it to a value below 100. It only changes the display: the field keeps diffusing with the same values, so the curves and the behaviours that read it are unaffected."))
            ),
            selector = settings,
            validated = { page.model.model.fields.any { it.opacity != 100 } }
        ),
        TutorialStep(
            title = i18n("Grid size"),
            content = listOf(
                p(i18n("Settings > Environment lists the square presets up to 500, and a Custom mode with two incrementable values, width and height, by step of ten cells between 10 and 1000.")),
                p(i18n("Pick the Custom radio, then change one of the two values. The two modes are exclusive: a preset disables the custom values, and the Custom radio disables the preset list."))
            ),
            selector = settings,
            validated = { page.model.simulation.settings.isCustomSize }
        )
    )

    override val conclusion: List<BulmaElement> = listOf(
        p(i18n("All of these settings are saved with the model. The activation state is also changed at run time by Activate/Inactivate (behaviours) and Use/Unuse (modifiers), and a rewind restores it as it was at the start of the run."))
    )

    override val next: Tutorial<ShowPage>? = null
}
