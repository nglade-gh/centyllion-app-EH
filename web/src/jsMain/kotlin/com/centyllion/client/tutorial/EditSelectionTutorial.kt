package com.centyllion.client.tutorial

import bulma.*
import com.centyllion.client.page.ShowPage
import kotlinx.html.*

/**
 * Tutorial on the edition of the grid: select, copy or cut, anchor, turn, mirror, paste and
 * erase — with the two switches that decide what a paste does at the borders of the grid and
 * over the agents already there.
 *
 * Like the tour of the first connection it is a guided tutorial: the visitor reads the gesture
 * of each step, does it on the grid, then goes on with Next. A step that would wait for the
 * action (a selection, a cut, a paste) would block the reader as soon as the gesture fails, and
 * the last steps only explain the two switches, which no action can validate.
 */
class EditSelectionTutorial(
    override val page: ShowPage
) : Tutorial<ShowPage> {

    override val name = i18n("Cut, rotate, paste")

    /** Not used: a guided tutorial starts on its first step. */
    override val introduction: List<BulmaElement> = emptyList()

    override val guided = true

    /** The gestures happen on the grid and on the edit bar, both in the Simulation tab. */
    override fun prepare() {
        page.modelController.editionTabPages.selectedPage = page.modelController.simulationItem
    }

    // ------------------------------------------------------------------ targets

    private val grid = {
        targetOr(
            page.simulationController.simulationViewController.simulationCanvas.root,
            page.modelController.editionTabs.root
        )
    }

    private val editBar = {
        targetOr(
            page.simulationController.simulationViewController.editToolbar.root,
            page.modelController.editionTabs.root
        )
    }

    /** The grid is empty at the first connection: say how to fill it before selecting. */
    private fun selectionContent(): List<BulmaElement> {
        val content = mutableListOf<BulmaElement>()
        content += p(
            i18n(
                "Hold Shift and drag on the grid: a rectangle follows the pointer and stays " +
                    "selected when the button is released, with a dark veil and a frame on its " +
                    "cells. That selection is what Copy, Cut and Erase work on."
            )
        )
        content += p(
            i18n(
                "A plain drag draws with the tool of the bar instead, so Shift is the way to " +
                    "select without drawing."
            )
        )
        if (page.model.simulation.agents.none { it >= 0 }) {
            content += p(
                i18n(
                    "This grid is empty: draw a few agents with the pen before selecting them, " +
                        "otherwise the copy has nothing to carry."
                )
            )
        }
        return content
    }

    // -------------------------------------------------------------------- steps

    override val steps: List<TutorialStep> = listOf(
        TutorialStep(
            title = i18n("Select a rectangle"),
            content = emptyList(),
            liveContent = { selectionContent() },
            selector = grid,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Copy (Ctrl+C) or cut (Ctrl+X)"),
            content = listOf(
                p(
                    i18n(
                        "Copy keeps the cells in place and memorises their agents; Cut memorises " +
                            "them and empties the selected cells. Both read the visible agents only: " +
                            "an agent hidden by a Show(id)/Hide(id) is not carried away."
                    )
                ),
                p(
                    i18n(
                        "The two buttons of the edit bar do the same as the shortcuts. Nothing is " +
                            "written in the grid by Copy, and a cut can be undone with Undo."
                    )
                )
            ),
            selector = editBar,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Anchor the floating selection"),
            content = listOf(
                p(
                    i18n(
                        "Once copied or cut, the extraction floats under the pointer: a thin cool " +
                            "frame with the ghost of its agents, which shows exactly what a paste " +
                            "would write on the cell below."
                    )
                ),
                p(
                    i18n(
                        "A left click anchors it on that cell: the frame becomes thick and yellow, " +
                            "it no longer follows the pointer, and the cell under it is the origin " +
                            "of the next paste."
                    )
                )
            ),
            selector = grid,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Release it (right click)"),
            content = listOf(
                p(
                    i18n(
                        "A right click detaches the anchor: the extraction floats again under the " +
                            "pointer, ready to be anchored somewhere else. Escape drops the preview " +
                            "without emptying the clipboard."
                    )
                ),
                p(
                    i18n(
                        "A right click inside the selection, when nothing is anchored, drops the " +
                            "selection itself; Ctrl+Shift+S and the Deselect button do the same."
                    )
                )
            ),
            selector = grid,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Turn it (Ctrl+R)"),
            content = listOf(
                p(
                    i18n(
                        "Ctrl+R, or the circular arrow of the edit bar, gives the extraction a " +
                            "quarter turn clockwise: the frame and its ghost agents turn at once, " +
                            "and a rectangle that is not square swaps its width and its height."
                    )
                ),
                p(
                    i18n(
                        "Only the clipboard turns, never the grid: four turns bring the extraction " +
                            "back to its first position, and nothing has been written yet."
                    )
                )
            ),
            selector = editBar,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Mirror it (Ctrl+F, Ctrl+Shift+F)"),
            content = listOf(
                p(
                    i18n(
                        "Ctrl+F mirrors the extraction left to right, Ctrl+Shift+F top to bottom; " +
                            "the two arrow buttons of the edit bar do the same. The two mirrors can " +
                            "be combined with a turn."
                    )
                )
            ),
            selector = editBar,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Paste (Ctrl+V or the button)"),
            content = listOf(
                p(
                    i18n(
                        "Ctrl+V or the Paste button writes the extraction where the anchor is; " +
                            "without an anchor, it lands under the pointer, or on the corner of the " +
                            "selection when the pointer is outside the grid."
                    )
                ),
                p(
                    i18n(
                        "Only the agents of the extraction are written: a cell of the copy that " +
                            "was empty leaves the target cell as it is, unless Overwrite is on — see " +
                            "the switch below. A second Ctrl+V pastes the same extraction again, " +
                            "without the floating preview."
                    )
                )
            ),
            selector = editBar,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Toric ? — the borders of the grid"),
            content = listOf(
                p(
                    i18n(
                        "Off, a paste that overflows the grid loses the cells that fall outside: " +
                            "they are simply not written."
                    )
                ),
                p(
                    i18n(
                        "On, those cells fold on the opposite side of the grid, as if the edges " +
                            "were glued: a paste astride the right edge continues on the left. The " +
                            "preview already shows the folded cells."
                    )
                ),
                p(
                    i18n(
                        "The switch also drives the Paste(x, y, overwrite) and PasteAndFlush(x, y, " +
                            "overwrite) meta-actions, and the drawing tools that overflow the grid."
                    )
                )
            ),
            selector = editBar,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Overwrite ? — the agents already there"),
            content = listOf(
                p(
                    i18n(
                        "Off, a paste only writes the empty cells of its target: the agents already " +
                            "present stay where they are, and the paste looks partial over a " +
                            "populated zone."
                    )
                ),
                p(
                    i18n(
                        "On, the agents of the extraction replace the ones in place, cell by cell, " +
                            "visible agents only. The frame of the preview turns warm when the " +
                            "switch is on."
                    )
                )
            ),
            selector = editBar,
            validated = { false }
        ),
        TutorialStep(
            title = i18n("Erase the selection (Ctrl+Delete)"),
            content = listOf(
                p(
                    i18n(
                        "Ctrl+Delete, or the red Erase button, empties the visible agents of the " +
                            "selection and drops the selection. The clipboard is not touched: what " +
                            "was copied or cut can still be pasted."
                    )
                )
            ),
            selector = editBar,
            validated = { false }
        )
    )

    override val conclusion: List<BulmaElement> = listOf(
        p(
            i18n(
                "The whole edition rests on one rectangle: Shift + drag to select it, Copy or Cut " +
                    "to fill the clipboard, a left click to anchor the paste, a right click to " +
                    "release it, Ctrl+R and Ctrl+F to turn it, Ctrl+V to write it, Ctrl+Delete to " +
                    "empty it. Before a paste, check the Toric and Overwrite switches: they decide " +
                    "what happens at the borders and over the agents already there."
            )
        )
    )

    override val next: Tutorial<ShowPage>? = null
}
