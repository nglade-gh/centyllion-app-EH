package com.centyllion.client.tutorial

import bulma.*
import com.centyllion.client.controller.model.StencilDialog
import com.centyllion.client.page.ShowPage
import com.centyllion.model.Behaviour
import com.centyllion.model.BoundaryConditions
import com.centyllion.model.Grain
import com.centyllion.model.GrainModel
import com.centyllion.model.ModelAndSimulation
import com.centyllion.model.createSimulation
import kotlinx.browser.document
import kotlinx.html.*
import org.w3c.dom.HTMLElement

/**
 * Beginner tutorial: load a pattern from a text with the Stencil window, then paste it and
 * watch it live.
 *
 * The tour creates its own model ([prepare]), a single grain of id 0, because that is the model
 * the two binary formats speak of: a `.cells` file and an RLE body made of `b` and `o` only are
 * two-state, so their live cells designate grain 0 (see `parseStencil` in the core). The pattern
 * of the tour is a real binary RLE, the catereron of the Conway Life wiki, 32 x 45: with the old
 * "o is grain 1" reading not a single cell of it would reach the grid.
 *
 * The model already carries the rules of the Game of Life, copied from the `GoL_Engine` model of
 * the gallery — `count(0)==3` gives birth, `count(0)<2 || count(0)>3` kills — so the creature
 * really lives after the paste: the last step runs the simulation, which is what makes the
 * tutorial convincing. The paste itself is untouched by these rules: a stencil is a copy.
 *
 * Like the edition tutorial it is a guided tour: the layer shows Previous, Next and Skip, and no
 * step waits for an action. Opening a window, pasting in it and clicking the grid cannot be told
 * apart from a click that does something else, and the last steps only explain the accepted
 * formats, the two switches of the paste and the run bar, which no gesture can validate.
 */
class StencilTutorial(
    override val page: ShowPage
) : Tutorial<ShowPage> {

    override val name = i18n("Load a stencil")

    /** Not used: a guided tutorial starts on its first step. */
    override val introduction: List<BulmaElement> = emptyList()

    override val guided = true

    /** Birth of the Game of Life: an empty cell with exactly three neighbours becomes a cell. */
    private val birthFormula = "count(0)==3"

    /** Death of the Game of Life: a cell with less than two or more than three neighbours dies. */
    private val deathFormula = "count(0)<2 || count(0)>3"

    /**
     * The tour needs a one-grain model of its own: the binary RLE it pastes designates grain 0,
     * and a model whose grains start at another id would drop every cell of the pattern.
     *
     * The two behaviours are those of the `GoL_Engine` model of the gallery, so the creature of
     * the tutorial is not a still picture: it follows the Game of Life from the first step. The
     * grid stays empty, the pattern is the only thing to see.
     */
    override fun prepare() {
        val cell = Grain(
            id = 0, name = "Cell", color = "Lime", icon = "square-full", movementProbability = 0.0,
            // the walls of GoL_Engine: the creature does not come back on the other side
            boundaryConditions = BoundaryConditions(left = false, right = false, top = false, bottom = false)
        )
        val model = GrainModel(
            name = "Stencil",
            description = "One grain and the Game of Life rules, to load a binary RLE pattern",
            grains = listOf(cell),
            behaviours = listOf(
                Behaviour(
                    name = "Birth", description = "An empty cell with exactly 3 neighbours is born",
                    formula = birthFormula, mainProductId = 0, sourceReactive = 0
                ),
                Behaviour(
                    name = "Death", description = "A cell with less than 2 or more than 3 neighbours dies",
                    formula = deathFormula, mainReactiveId = 0, sourceReactive = 0, id = 1
                )
            )
        )
        page.model = ModelAndSimulation(model, createSimulation("Stencil", size = 100))
        // the window and the paste belong to the simulation tab; set after the model, which
        // replaces the editors of the page
        page.modelController.editionTabPages.selectedPage = page.modelController.simulationItem
    }

    // ------------------------------------------------------------------ the pattern

    /**
     * The RLE of 50P92.1 (« catereron »), exactly as published on the Conway Life wiki. The
     * line breaks of an RLE body carry no meaning, the file happens to be cut every 70
     * characters: the three body lines below are the ones of the published file.
     */
    private val rleText: String = listOf(
        "#N catereron50p92.1.rle",
        "#C https://conwaylife.com/wiki/50P92.1",
        "#C https://www.conwaylife.com/patterns/catereron50p92.1.rle",
        "x = 32, y = 45, rule = B3/S23",
        "2bo\$bobo2\$3bo\$2b2o\$2b5o\$2b2ob2o\$3bo4\$13b3o14b2o\$13bo3bo12b2o\$13bo4bo\$14bo3bo",
        "2\$14bo3bo\$13bo4bo\$13bo3bo12b2o\$13b3o14b2o6\$b3o3b3o\$o2bo3bo2bo\$2obo3bob2o16\$b",
        "2o5b2o\$b2o5b2o!"
    ).joinToString("\n")

    /**
     * The pattern as the visitor reads it: a monospace box that scrolls on its own, so that the
     * message of the tour keeps its size. The header `x = 32, y = 45` and the first lines are
     * therefore visible at once, and the whole text can be selected and copied from the box.
     */
    private fun patternBlock(): BulmaElement = wrap("content") {
        pre(classes = "centy-tutorial-rle") { +rleText }
    }.apply {
        (root.querySelector(".centy-tutorial-rle") as? HTMLElement)?.let { block ->
            block.style.fontSize = "0.7rem"
            block.style.lineHeight = "1.25"
            block.style.maxHeight = "11rem"
            block.style.setProperty("overflow", "auto")
            block.style.whiteSpace = "pre"
        }
    }

    // ------------------------------------------------------------------ targets

    /** Element looked up by id: the window is rebuilt on every opening, so no reference lasts. */
    private fun byId(id: String): HTMLElement? = document.getElementById(id) as? HTMLElement

    private val editBar = {
        targetOr(
            page.simulationController.simulationViewController.editToolbar.root,
            page.modelController.editionTabs.root
        )
    }

    private val grid = {
        targetOr(
            page.simulationController.simulationViewController.simulationCanvas.root,
            page.modelController.editionTabs.root
        )
    }

    /** The Stencil button of the edit bar, and the frame it falls back to once the window opens. */
    private val stencilButton = {
        targetOr(page.simulationController.simulationViewController.stencilButton.root, editBar())
    }

    /** The window itself, whatever it holds: the frame can always fall back to it. */
    private val stencilWindow = {
        document.querySelector(".${StencilDialog.CARD_CLASS}") as? HTMLElement
    }

    /** The text zone of the window; the window or the Stencil button while it is closed. */
    private val patternZone = { targetOr(byId(StencilDialog.TEXT_ID) ?: stencilWindow(), stencilButton()) }

    /** The Validate button of the window, framed only while the window is open. */
    private val validateButton = { targetOr(byId(StencilDialog.VALIDATE_ID), patternZone()) }

    private val pasteButton = {
        targetOr(page.simulationController.simulationViewController.pasteButton.root, editBar())
    }

    /** The "?" beside Stencil: the formats and the rules of the paste, in the same window. */
    private val helpButton = {
        targetOr(page.simulationController.simulationViewController.stencilHelpButton.root, editBar())
    }

    // -------------------------------------------------------------------- steps

    override val steps: List<TutorialStep> = listOf(
        TutorialStep(
            title = i18n("Open the Stencil window"),
            content = listOf(
                p(
                    i18n(
                        "The Stencil button, with the stamp icon, sits in the edit bar just after " +
                            "Paste. It opens a window that reads a pattern from a text or from a " +
                            "file and drops it in the clipboard as a floating selection: nothing " +
                            "is written on the grid yet."
                    )
                ),
                p(
                    i18n(
                        "This tutorial created a one-grain model for itself: a single grain " +
                            "« Cell » of id 0, which already carries the rules of the Game of " +
                            "Life — Birth at exactly 3 neighbours, Death under 2 or over 3. That " +
                            "is the model the two binary formats speak of: a .cells file, and an " +
                            "RLE made of b and o only, designate the first grain."
                    )
                )
            ),
            selector = stencilButton,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Paste the pattern in the zone"),
            content = listOf(
                p(
                    i18n(
                        "Select the pattern below, copy it (Ctrl+C), click the text zone of the " +
                            "window and paste it there (Ctrl+V)."
                    )
                ),
                patternBlock(),
                p(
                    i18n(
                        "The lines are long and the message is narrow: the box scrolls on its " +
                            "own. The very same pattern is written in full in the help, §7.5 " +
                            "« Load a stencil », and it is the catereron of the Conway Life " +
                            "wiki, 50P92.1."
                    )
                )
            ),
            selector = patternZone,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Validate"),
            content = listOf(
                p(
                    i18n(
                        "Validate reads the zone and closes the window. A text that no format " +
                            "recognises leaves the window open, with a red message saying what is " +
                            "accepted, and loads nothing."
                    )
                ),
                p(
                    i18n(
                        "Here the header announces x = 32, y = 45 and the body uses nothing but " +
                            "b and o: this is a two-state RLE, so each o becomes grain 0, the only " +
                            "grain of the model. The journal then writes « Stencil loaded: 32 x " +
                            "45 cells » — a size or a grain id that does not fit would be " +
                            "reported there as ignored cells."
                    )
                )
            ),
            selector = validateButton,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Anchor the floating selection"),
            content = listOf(
                p(
                    i18n(
                        "The pattern now floats under the pointer: a thin cool frame with the " +
                            "ghost of its 66 agents, showing exactly what a paste would write on " +
                            "the cell below."
                    )
                ),
                p(
                    i18n(
                        "A left click anchors it on that cell: the frame becomes thick and " +
                            "yellow and stops following the pointer. A right click releases it, " +
                            "Ctrl+R turns it, Ctrl+F and Ctrl+Shift+F mirror it."
                    )
                )
            ),
            selector = grid,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Paste (Ctrl+V)"),
            content = listOf(
                p(
                    i18n(
                        "Ctrl+V, or the Paste button of the edit bar, writes the 66 agents of the " +
                            "pattern where the anchor is. Without an anchor, the paste lands " +
                            "under the pointer or on the corner of the selection."
                    )
                ),
                p(
                    i18n(
                        "Only the agents of the pattern are written: a cell the pattern leaves " +
                            "empty leaves its target as it is, unless Écraser ? is on — see the " +
                            "next step."
                    )
                )
            ),
            selector = pasteButton,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Accepted formats, Écraser ? and Torique ?"),
            content = listOf(
                p(
                    i18n(
                        "The format is detected on its own, there is no selector. Four are " +
                            "accepted: RLE of Golly (a header x = …, y = … then the cells), plain " +
                            "text .cells (lines of . and O), a character matrix (one character per " +
                            "cell, 0-9 then a-f for the ids) and a .nbd2 project, cropped on the " +
                            "box of its agents."
                    )
                ),
                p(
                    i18n(
                        "How a cell is numbered: a two-state pattern — RLE with b and o only, or " +
                            ".cells — designates grain 0. As soon as an RLE body carries an " +
                            "uppercase state (A = 10, B = 11 … Z = 35), the state number is the " +
                            "grain id, so o is grain 1 again and 2A is two cells of grain 10. A " +
                            "cell whose grain the model does not have becomes empty, and the " +
                            "journal counts it once per load."
                    )
                ),
                p(
                    i18n(
                        "Écraser ? decides what happens over the agents already there: off, the " +
                            "paste only fills the empty cells of its target; on, the agents of the " +
                            "pattern replace the ones in place, cell by cell. Torique ? decides " +
                            "at the borders: off, the cells that overflow the grid are lost; on, " +
                            "they fold on the opposite side. A stencil follows both, exactly like " +
                            "a manual paste."
                    )
                ),
                p(
                    i18n(
                        "The ? button beside Stencil opens the same explanations, and the help " +
                            "(§7.5) holds the table of formats and the complete text of this " +
                            "pattern."
                    )
                )
            ),
            selector = helpButton,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Run it — the creature lives"),
            content = listOf(
                p(
                    i18n(
                        "The 66 cells pasted on the grid are agents of the model, and the two " +
                            "behaviours it already had apply to them: Birth where exactly three " +
                            "neighbours surround an empty cell, Death where there are less than " +
                            "two or more than three. Press the play button of the run bar, or the " +
                            "step-forward arrow to go one step at a time."
                    )
                ),
                p(
                    i18n(
                        "This pattern is 50P92.1, an oscillator of the Game of Life: it does not " +
                            "stay still, it comes back to its shape every 92 steps. Watch the " +
                            "counter and the curve of the grain: the number of living cells " +
                            "changes from one step to the next."
                    )
                ),
                p(
                    i18n(
                        "A grid that would not move is the sign of a pattern loaded with a grain " +
                            "the model does not have: nothing was pasted. Rewind brings the model " +
                            "and the grid back to their initial state, and the clipboard is left " +
                            "alone, so the pattern can be pasted again."
                    )
                )
            ),
            selector = { targetOr(page.simulationController.runButton.root, editBar()) },
            validated = { false }
        )
    )

    override val conclusion: List<BulmaElement> = listOf(
        p(
            i18n(
                "A stencil is a copy that comes from a text: Stencil, paste the pattern, " +
                    "Validate, click the grid to anchor it, Ctrl+V, then press play. Before " +
                    "pasting, check Écraser ? and Torique ? — the stencil obeys them like any " +
                    "other paste, and with Écraser ? on its empty cells erase the ones they cover."
            )
        )
    )

    override val next: Tutorial<ShowPage> = BacteriasTutorial(page)
}
