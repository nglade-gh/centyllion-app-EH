package com.centyllion.client.tutorial

import bulma.*
import com.centyllion.client.capture.WorkFolder
import com.centyllion.client.capture.WorkFolderState
import com.centyllion.client.page.ShowPage
import kotlinx.browser.window
import kotlinx.html.*

/**
 * Tour of the first connection: what the screen is, step by step.
 *
 * The tour opens by itself on the first visit (see [ShowPage]) and is then the first entry of
 * the Tutorial menu. It is a guided tour: the layer shows Previous, Next and Skip, and it never
 * waits for an action of the visitor, so nothing can block it. A persistent flag
 * ([SEEN_KEY]) is written when the tour is left — Skip, close button or last step — and the
 * tour is never started by itself again; the Tutorial menu still replays it.
 *
 * It stops at the drawing tools: the modifiers, the behaviours, the actions and the formulas
 * belong to the other tutorials, which the last step announces instead of repeating.
 */
class GettingStartedTour(
    override val page: ShowPage
) : Tutorial<ShowPage> {

    override val name = i18n("Getting started")

    /** Not used: a guided tour starts on its first step, which is the welcome message. */
    override val introduction: List<BulmaElement> = emptyList()

    override val guided = true

    /** The tour speaks of the grid and of the drawing tools: it shows the Simulation tab. */
    override fun prepare() {
        page.modelController.editionTabPages.selectedPage = page.modelController.simulationItem
    }

    /** Written when the layer is closed, whatever the way out. */
    override fun onLeave() = markSeen()

    // ------------------------------------------------------------------ targets

    private val title = { page.titleLabel.root }
    private val workingFolder = { page.workFolderController.control.root }
    private val topBar = { page.tools.root }
    private val grid = {
        targetOr(
            page.simulationController.simulationViewController.simulationCanvas.root,
            page.modelController.editionTabs.root
        )
    }
    private val explorer = { page.modelController.leftColumn.root }
    private val tabs = { page.modelController.editionTabs.root }
    private val curves = {
        targetOr(page.modelController.grainChart.root, page.modelController.chartsHeader.root)
    }
    private val drawingTools = {
        targetOr(
            page.simulationController.simulationViewController.toolbar.root,
            page.modelController.editionTabs.root
        )
    }

    // ------------------------------------------------------------ working folder

    /**
     * Text of the working folder step, built when the step is shown: the visitor may have
     * chosen a folder since the tour started, and the step must then state it instead of
     * offering the picker again.
     */
    private fun folderContent(): List<BulmaElement> {
        val content = mutableListOf<BulmaElement>()
        content += p(
            i18n(
                "Some models take a picture of the view while they run, through the Capture() " +
                    "meta-action. The picture is written in the captures folder of the working " +
                    "folder, in a sub-folder named after the model."
            )
        )
        content += p(page.workFolderController.stateText())
        when (WorkFolder.state) {
            // no picker in this browser: say where the pictures go instead of showing a button
            // that cannot do anything
            WorkFolderState.UNSUPPORTED -> content += p(
                i18n(
                    "This browser has no access to a folder, so there is nothing to choose here: " +
                        "every capture is downloaded, one file per step. Chrome and Edge offer the " +
                        "working folder on HTTPS or on localhost; Firefox and Safari do not."
                )
            )
            // already set: state it, and say what the home button does now
            WorkFolderState.DEFINED -> content += p(
                i18n(
                    "Nothing to do here: the pictures go to this folder. The home button of the " +
                        "bar, under the title, reopens this folder dialog at any time."
                )
            )
            // not defined, or permission to renew: the gesture can be done from this step
            else -> {
                content += p(
                    i18n(
                        "You can set it now, or later with the home button of the bar: it is " +
                            "magenta while no folder is set and blue once one is."
                    )
                )
                content += Button(
                    i18n("Choose a folder…"), Icon("folder-open"), color = ElementColor.Primary
                ) { page.workFolderController.showDialog() }
            }
        }
        return content
    }

    // -------------------------------------------------------------------- steps

    override val steps: List<TutorialStep> = listOf(
        TutorialStep(
            title = i18n("Welcome"),
            content = listOf(
                p(
                    i18n(
                        "NetBioDyn simulates a population of agents on a grid: every agent follows " +
                            "the behaviours of its grain, and fields diffuse around them, cell by cell."
                    )
                ),
                p(
                    i18n(
                        "This tour shows the screen in eight short steps, from the working folder " +
                            "to the drawing tools. It stops there: the agents, the fields, the " +
                            "behaviours and the formulas have their own tutorials."
                    )
                ),
                p(
                    i18n(
                        "Go on with Next, or leave at any time with Skip: the tour then closes and " +
                            "will not open by itself again."
                    )
                )
            ),
            selector = title,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("The working folder"),
            content = emptyList(),
            liveContent = { folderContent() },
            selector = workingFolder,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("The top bar"),
            content = listOf(
                p(
                    i18n(
                        "New Model starts an empty model, Import loads a .nbd2 file from the disk, " +
                            "and Save As writes the model and its simulation to a file."
                    )
                ),
                p(
                    i18n(
                        "Gallery lists the pictures and the snapshots kept in the browser, Tutorial " +
                            "reopens this tour and the others, Help opens the documentation and " +
                            "About gives the version. Undo and Redo are on the right."
                    )
                ),
                p(
                    i18n(
                        "The pink button under the bar, « Model information », edits the name and " +
                            "the description of the model."
                    )
                )
            ),
            selector = topBar,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("The simulation screen"),
            content = listOf(
                p(
                    i18n(
                        "One square of the grid is one cell of the simulation: an agent takes the " +
                            "colour of its grain, an empty cell the background colour."
                    )
                ),
                p(
                    i18n(
                        "The fields are overlaid on the grid as a coloured veil, the more opaque the " +
                            "more concentrated. The wheel zooms, and the pointer names the cell under " +
                            "it with the value of each field there."
                    )
                ),
                p(
                    i18n(
                        "Above the grid, the run bar: Rewind, Run, Step, Stop, the speed in steps per " +
                            "second and the step counter."
                    )
                )
            ),
            selector = grid,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("The explorer"),
            content = listOf(
                p(
                    i18n(
                        "The left column lists what the model contains: Agents (the grains), Fields " +
                            "and Behaviours, then Simulation modifiers in expert mode."
                    )
                ),
                p(
                    i18n(
                        "Each row carries an On/Off radio, a name and its identifier, written " +
                            "name (id): that identifier is what the formulas use. The + of a section " +
                            "adds an element and opens its editor, and the search box above filters " +
                            "the list."
                    )
                )
            ),
            selector = explorer,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("The tabs"),
            content = listOf(
                p(
                    i18n(
                        "Simulation holds the grid, the run bar and the edition tools. Model opens " +
                            "the editor of the element selected in the explorer, and Settings holds " +
                            "the size of the grid, the seed, the update plan and the curves."
                    )
                )
            ),
            selector = tabs,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("The curves"),
            content = listOf(
                p(
                    i18n(
                        "The right column draws one curve per agent: how many there are at each " +
                            "step, in the colour of its grain. The Fields chart below does the same " +
                            "for the amount of each field, and appears as soon as the model has one."
                    )
                ),
                p(
                    i18n(
                        "Both start again at a rewind. The CSV button exports the history, and " +
                            "Settings can show a sliding window instead of the whole run."
                    )
                )
            ),
            selector = curves,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Drawing tools and edit bar"),
            content = listOf(
                p(
                    i18n(
                        "The bar under the grid holds the drawing tools — pen, line, square, circle, " +
                            "spray, eraser and move — with their size, the Fill switch of the square " +
                            "and the circle, the random add and the button that clears the grid."
                    )
                ),
                p(
                    i18n(
                        "The bar under it copies, cuts, pastes, erases, turns and mirrors a " +
                            "selection of cells; a shift + drag selects a rectangle."
                    )
                ),
                p(
                    i18n(
                        "The tutorial « Cut, rotate, paste » takes those gestures one by one. This " +
                            "tour stops here."
                    )
                )
            ),
            selector = drawingTools,
            validated = { false },
            // the tour is over: remember it even if the conclusion is closed without Ok
            nextCallback = { markSeen() }
        )
    )

    override val conclusion: List<BulmaElement> = listOf(
        p(
            i18n(
                "That is the whole screen. The other tutorials go further: « Cut, rotate, paste » " +
                    "for the edition, the beginner ones for the agents and the fields, the expert " +
                    "ones for the behaviours, the actions, the modifiers and the formulas."
            )
        )
    )

    override val next: Tutorial<ShowPage> = EditSelectionTutorial(page)

    companion object {

        /** localStorage flag: set once the tour has been seen, never read to hide the menu entry. */
        const val SEEN_KEY = "gettingStartedTourSeen"

        /** True when the tour was already closed once in this browser. */
        fun seen(): Boolean = try {
            window.localStorage.getItem(SEEN_KEY) != null
        } catch (t: Throwable) {
            // a browser refusing localStorage shows the tour again: it is harmless
            false
        }

        /** Remembers that the tour was seen. */
        fun markSeen() {
            try {
                window.localStorage.setItem(SEEN_KEY, "true")
            } catch (t: Throwable) {
                // nothing to do: the tour will be proposed again
            }
        }
    }
}
