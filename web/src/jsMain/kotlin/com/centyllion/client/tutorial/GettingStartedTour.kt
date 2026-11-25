package com.centyllion.client.tutorial

import bulma.*
import com.centyllion.client.capture.WorkFolder
import com.centyllion.client.page.ShowPage
import kotlinx.browser.window
import kotlinx.html.*
import org.w3c.dom.HTMLElement

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
    /** The home button itself: the frame must surround the house, not the row it sits in. */
    private val workingFolder = { page.workFolderController.buttonRoot }
    /** The model information button, beside the home button. */
    private val modelInfo = { page.modelInfoButton.root }

    /**
     * The button drawn inside a control, or the control itself when it holds no button (the
     * import control is a file label): every step of the bar frames one button, never the row.
     */
    private fun buttonOf(control: Control): HTMLElement =
        control.root.querySelector("button") as? HTMLElement ?: control.root

    private val newModel = { buttonOf(page.newControl) }
    private val importButton = { page.fileInput.root }
    private val saveAs = { buttonOf(page.saveAsControl) }
    private val gallery = { buttonOf(page.galleryControl) }
    private val tutorial = { buttonOf(page.tutorialControl) }
    private val help = { buttonOf(page.helpControl) }
    private val about = { buttonOf(page.aboutControl) }
    /** Undo and redo are disabled — hence drawn at low opacity — while the history is empty. */
    private val undo = { targetOr(page.undoButton.root, page.tools.root) }
    private val redo = { targetOr(page.redoButton.root, page.tools.root) }

    private val grid = {
        targetOr(
            page.simulationController.simulationViewController.simulationCanvas.root,
            page.modelController.editionTabs.root
        )
    }
    /** The run bar above the grid, with its own step. */
    private val runBar = { page.simulationController.runBar.root }
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
     * Text of the working folder step. The sentence is fixed; only the download fallback depends
     * on the browser, so the content is still built when the step is shown.
     */
    private fun folderContent(): List<BulmaElement> {
        val content = mutableListOf<BulmaElement>()
        content += p(
            i18n(
                "One of the first things to do the first time you use NetBioDyn is to set your " +
                    "home. This folder is where your models are saved by default, and it is also " +
                    "where the model gallery and the captures (in the « captures » sub-folder) " +
                    "are saved."
            )
        )
        if (WorkFolder.isSupported()) {
            content += Button(
                i18n("Choose a folder…"), Icon("folder-open"), color = ElementColor.Primary
            ) { page.workFolderController.showDialog() }
        } else {
            // short secondary line: this browser can never open the picker
            content += p(
                i18n("Captures are downloaded: this browser has no access to a folder.")
            ).apply {
                root.classList.add("is-size-7")
                root.classList.add("has-text-grey")
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
                        "This tour shows the screen in eighteen short steps, from the working " +
                            "folder to the drawing tools. It stops there: the agents, the fields, " +
                            "the behaviours and the formulas have their own tutorials."
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
        // one step per button of the top bar, in the order the bar draws them: the visitor reads
        // what each icon does while the frame surrounds that icon alone
        TutorialStep(
            title = i18n("New Model"),
            content = listOf(
                p(i18n("In the top bar you will find the following icons:")),
                p(i18n("New Model, which starts a new empty model"))
            ),
            selector = newModel,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Import"),
            content = listOf(
                p(i18n("Import loads a .nbd2 file from the disk"))
            ),
            selector = importButton,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Save As"),
            content = listOf(
                p(i18n("Save As writes the model and its simulation to a .nbd2 file."))
            ),
            selector = saveAs,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Gallery"),
            content = listOf(
                p(i18n("Gallery lets you bookmark models and open them directly"))
            ),
            selector = gallery,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Tutorial"),
            content = listOf(
                p(i18n("Tutorial shows a series of tutorials, from beginner to expert level"))
            ),
            selector = tutorial,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Help"),
            content = listOf(
                p(i18n("Help gives you access to a detailed documentation of NetBioDyn"))
            ),
            selector = help,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("About"),
            content = listOf(
                p(i18n("About tells you about the current version of NetBioDyn."))
            ),
            selector = about,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Undo"),
            content = listOf(
                p(i18n("The left circular arrow button is Undo"))
            ),
            selector = undo,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Redo"),
            content = listOf(
                p(i18n("The right circular arrow button is Redo"))
            ),
            selector = redo,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Model information"),
            content = listOf(
                p(
                    i18n(
                        "In « Model information » you can enter a name and a description for your " +
                            "model."
                    )
                )
            ),
            selector = modelInfo,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("The simulation screen"),
            content = listOf(
                p(i18n("A square of the grid may hold one agent of the simulation, or none.")),
                p(i18n("Fields are overlaid on the grid as a coloured veil, the more opaque the more concentrated. The visibility of the agents and of the fields can be controlled both by a Show ? button and, for the fields, by a control of their transparency.")),
                p(i18n("The mouse wheel zooms on the grid. The pointer gives information about the values met in the grid."))
            ),
            selector = grid,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("The run bar"),
            content = listOf(
                p(i18n("Above the grid, the run bar: Rewind, Run, Step, Stop, the speed in steps per second and the step counter."))
            ),
            selector = runBar,
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
