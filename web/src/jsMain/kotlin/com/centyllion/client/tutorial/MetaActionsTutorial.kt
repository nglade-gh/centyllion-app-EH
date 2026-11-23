package com.centyllion.client.tutorial

import bulma.*
import com.centyllion.client.page.ShowPage
import kotlinx.browser.document
import kotlinx.html.*

/**
 * Tutorial on the meta-actions: the actions that act on the simulation itself instead of on
 * the cell of the behaviour (spawning, erasing, pausing, activating).
 *
 * The steps are explanatory: they are not anchored on a UI element, so any element of the
 * page validates them.
 */
class MetaActionsTutorial(
    override val page: ShowPage
) : Tutorial<ShowPage> {

    override val name = i18n("Simulation modifiers: meta-actions")

    /** The modifiers only exist in expert mode. */
    override fun prepare() {
        page.expertMode = true
    }

    private val modifiersSection = { page.modelController.modifiersController.root }

    override val introduction: List<BulmaElement> = listOf(
        p(i18n("The usual actions (Add, Remove, Move, SetField…) work around the cell where the behaviour applies, and are written in the Actions field of a behaviour.")),
        p(i18n("Meta-actions work on the simulation itself: they add or erase agents in a zone, draw a line, pause the run, or switch a behaviour or another modifier on and off.")),
        p(i18n("They are NOT written in a behaviour any more: the behaviour editor refuses them. They belong to a simulation modifier, in the section « Simulation modifiers », which is only visible in expert mode."))
    )

    override val steps: List<TutorialStep> = listOf(
        TutorialStep(
            title = i18n("Populate and erase"),
            content = listOf(
                p(i18n("Create a modifier with the + of the section, then write its Actions. Add(id, N) adds N agents of type id anywhere; AddRect(id, N, x1, y1, x2, y2), AddDisc(id, N, x, y, radius) and DrawLine(id, x1, y1, x2, y2) restrict the zone or the shape.")),
                p(i18n("EraseRect(x1, y1, x2, y2) erases every agent of the rectangle, EraseDisc(x, y, radius) every agent of the disc, the five-argument forms EraseRect(id, …) and EraseDisc(id, …) only erase the agents of type id, and Erase(id) erases the whole type.")),
                p(i18n("The rectangle accepts its corners in any order, every coordinate is clamped to the grid, and the positions are drawn among the empty cells: asking for more agents than there are free cells adds only the free ones.")),
                p(i18n("A modifier runs once per step, after the behaviours. Its own probability decides at each step, and Activate/Inactivate (behaviours) and Use/Unuse (modifiers) switch the others on and off."))
            ),
            selector = modifiersSection,
            validated = { true }
        ),
        TutorialStep(
            title = i18n("Pause and activation"),
            content = listOf(
                p(i18n("pause() stops the simulation at the end of the current step: useful to run a scenario until a condition is reached, then look at it quietly.")),
                p(i18n("Activate(id) and Inactivate(id) switch a behaviour on or off during the run; Use(id) and Unuse(id) do the same for a modifier. The identifier is the one displayed in the explorer, after the name: name (id).")),
                p(i18n("The condition of a modifier is evaluated once per step, outside any cell: step, agent(x, y), age(x, y), count(id, x, y) and field(id, x, y) are available, and IsActive/IsInactive/IsInUse/IsNotInUse test the activation state."))
            ),
            selector = modifiersSection,
            validated = { true }
        )
    )

    override val conclusion: List<BulmaElement> = listOf(
        p(i18n("Careful: Add(id, N) uses a random draw over the whole grid, so the agents appear anywhere in the environment. For a reproducible scenario, prefer AddRect or AddDisc with explicit coordinates."))
    )

    override val next: Tutorial<ShowPage>? = null
}
