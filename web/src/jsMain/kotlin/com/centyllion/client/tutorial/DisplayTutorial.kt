package com.centyllion.client.tutorial

import bulma.*
import com.centyllion.client.page.ShowPage
import kotlinx.browser.document
import kotlinx.html.*

/**
 * Tutorial on the display and activation settings: behaviour identifiers, the On/Off radio,
 * the field opacity and the custom grid size.
 *
 * The steps are explanatory: they are not anchored on a UI element, so any element of the
 * page validates them.
 */
class DisplayTutorial(
    override val page: ShowPage
) : Tutorial<ShowPage> {

    override val name = i18n("Identifiers, activation, opacity and grid size")

    override val introduction: List<BulmaElement> = listOf(
        p(i18n("This tour covers the settings that act on what you see and on which behaviours run: behaviour identifiers, the On/Off radio, the opacity of the fields and the size of the grid."))
    )

    override val steps: List<TutorialStep> = listOf(
        TutorialStep(
            title = i18n("Behaviours: identifier and activation"),
            content = listOf(
                p(i18n("Each behaviour now has an identifier, displayed after its name in the explorer: name (id). It is what Activate(id) and Inactivate(id) target in the actions.")),
                p(i18n("The radio in front of a behaviour switches it on or off by hand: an inactive behaviour is never applied, as if its probability were zero, but it keeps its settings. Clicking the radio again switches it back on."))
            ),
            selector = { document.body!! },
            validated = { true }
        ),
        TutorialStep(
            title = i18n("Field opacity"),
            content = listOf(
                p(i18n("Open a field: below the formula, under a blue separator, an Opacity setting goes from 0 to 100 %.")),
                p(i18n("It only changes the display: the field keeps diffusing with the same values, so the curves and the behaviours that read it are unaffected. At 0 % the field is not drawn at all."))
            ),
            selector = { document.body!! },
            validated = { true }
        ),
        TutorialStep(
            title = i18n("Grid size"),
            content = listOf(
                p(i18n("Settings > Environment lists the square presets up to 500, and a Custom mode with two incrementable values, width and height, by step of ten cells between 10 and 1000.")),
                p(i18n("The two modes are exclusive: choosing a preset disables the custom values, and the Custom radio disables the preset list. Changing the size reinitialises the agents."))
            ),
            selector = { document.body!! },
            validated = { true }
        )
    )

    override val conclusion: List<BulmaElement> = listOf(
        p(i18n("All of these settings are saved with the model, except the behaviours activation state which is also applied at run time by Activate/Inactivate."))
    )

    override val next: Tutorial<ShowPage>? = null
}
