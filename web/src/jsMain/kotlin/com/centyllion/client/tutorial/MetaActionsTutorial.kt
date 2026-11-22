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

    override val name = i18n("Meta-actions: act on the whole simulation")

    override val introduction: List<BulmaElement> = listOf(
        p(i18n("The usual actions (Add, Remove, Move, SetField…) work around the cell where the behaviour applies. Meta-actions work on the simulation itself: they add or erase agents in a whole zone, pause the run, or switch another behaviour on and off.")),
        p(i18n("They are written in the same Actions field, separated by commas."))
    )

    override val steps: List<TutorialStep> = listOf(
        TutorialStep(
            title = i18n("Populate and erase"),
            content = listOf(
                p(i18n("Add(id, N) adds N agents of type id anywhere in the environment. AddRect(id, N, x1, y1, x2, y2) and AddDisc(id, N, x, y, radius) restrict the zone.")),
                p(i18n("Add(id, N) — two arguments — is the global form; Add(id, dx, dy) — three arguments — keeps its old meaning: one agent at an offset from the behaviour cell.")),
                p(i18n("EraseRect(x1, y1, x2, y2) erases every agent of the rectangle, EraseDisc(x, y, radius) every agent of the disc, and the five-argument form EraseRect(id, x1, y1, x2, y2) only erases the agents of type id. Erase(id) erases the whole type.")),
                p(i18n("The rectangle accepts its corners in any order, and every coordinate is clamped to the grid."))
            ),
            selector = { document.body!! },
            validated = { true }
        ),
        TutorialStep(
            title = i18n("Pause and activation"),
            content = listOf(
                p(i18n("pause() stops the simulation at the end of the current step: useful to run a scenario until a condition is reached, then look at it quietly.")),
                p(i18n("Activate(id) and Inactivate(id) switch a behaviour on or off during the run. The identifier is the one displayed in the explorer, after the behaviour name: name (id). A behaviour that is already in the requested state is not changed."))
            ),
            selector = { document.body!! },
            validated = { true }
        )
    )

    override val conclusion: List<BulmaElement> = listOf(
        p(i18n("Careful: Add(id, N) uses a random draw over the whole grid, so the agents appear anywhere in the environment. For a reproducible scenario, prefer AddRect or AddDisc with explicit coordinates."))
    )

    override val next: Tutorial<ShowPage>? = null
}
