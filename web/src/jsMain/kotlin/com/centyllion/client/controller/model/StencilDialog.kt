package com.centyllion.client.controller.model

import bulma.BulmaElement
import bulma.Button
import bulma.Control
import bulma.ElementColor
import bulma.Field
import bulma.FileInput
import bulma.Icon
import bulma.Label
import bulma.ModalCard
import bulma.Section
import bulma.SubTitle
import bulma.Table
import bulma.TableCell
import bulma.TableRow
import bulma.TextArea
import bulma.p
import com.centyllion.client.page.BulmaPage
import com.centyllion.model.Stencil
import com.centyllion.model.StencilParse
import com.centyllion.model.message
import com.centyllion.model.parseStencil
import org.w3c.dom.HTMLInputElement
import org.w3c.files.File
import org.w3c.files.FileList
import org.w3c.files.FileReader
import org.w3c.files.get

/**
 * The window of the "Stencil" button of the edit bar: it reads a pattern from a text or from a
 * file and hands it to the simulation view, which loads it into its clipboard.
 *
 * The format is detected by the core ([parseStencil]): this window has no selector and knows
 * nothing about RLE, `.cells`, matrices or projects. It only shows what the reader answered —
 * the refusal message in the window, the pattern in the clipboard — so a text that no format
 * recognises loads nothing at all.
 *
 * The dialogue stays open on a refusal: the reader can fix the text and validate again, or
 * choose another file. The two buttons that end the dialogue are "Valider", which closes it
 * only once a pattern was read, and "Fermer", which closes it without touching the clipboard.
 */
class StencilDialog(
    private val page: BulmaPage,
    /** Receives the pattern the reader just accepted, with the format it was read as. */
    private val onStencil: (Stencil) -> Unit
) {

    /** The open dialogue, null when it was closed: a second click on the button opens a new one. */
    private var dialog: ModalCard? = null

    /** Opens the window (one at a time: an already open dialogue is brought back, not doubled). */
    fun show() {
        val existing = dialog
        if (existing != null) {
            existing.active = true
            return
        }

        val textInput = TextArea(
            "", placeholder = page.i18n("Paste an RLE pattern, a .cells pattern, a character matrix or a .nbd2 project."),
            rows = "8"
        ).apply {
            // one character per cell: the pattern must be readable as a grid in the zone
            root.style.fontFamily = "monospace"
            root.style.fontSize = "12px"
            // stable id: the tutorial "Load a stencil" points at this zone, which only exists
            // while the window is open
            root.id = TEXT_ID
        }

        val errorLine = p(classes = "help has-text-danger")
        errorLine.hidden = true

        /** Explanation of what will happen to the accepted pattern: always visible. */
        val hint = p(
            page.i18n(
                "The pattern fills the clipboard and floats under the mouse: click the grid to anchor it, " +
                    "then paste. The Overwrite ? and Toric ? switches of the edit bar keep their usual meaning."
            )
        ).apply { root.classList.add("help") }

        fun fail(message: String) {
            errorLine.text = message
            errorLine.hidden = false
        }

        /** Reads the zone; a refused text says why in the window and loads nothing. */
        fun validate() {
            when (val result = parseStencil(textInput.value)) {
                is StencilParse.Rejected -> fail(result.problem.message(page.appContext.locale, result.position))
                is StencilParse.Parsed -> {
                    errorLine.hidden = true
                    dialog?.active = false
                    onStencil(result.stencil)
                }
            }
        }

        val fileInput = FileInput(
            label = page.i18n("Choose a file"), icon = "upload", color = ElementColor.Primary
        )
        val fileElement = fileInput.root.querySelector("input[type=file]") as? HTMLInputElement
        // the accept attribute is only a hint: the content is read and checked anyway
        fileElement?.accept = ".rle,.cells,.txt,.nbd2,.netbiodyn"
        fileInput.onChange = { _, files: FileList? ->
            val file = files?.get(0)
            if (file != null) readText(
                file,
                onText = { text ->
                    // the file lands in the zone, so that Valider always reads the same place
                    textInput.value = text
                    errorLine.hidden = true
                    hint.text = page.i18n("File loaded: %0. Press Validate to read the pattern.", file.name)
                },
                onError = {
                    fail(page.i18n("The selected file could not be read."))
                }
            )
            // lets the visitor choose the same file again
            fileElement?.value = ""
        }

        val helpButton = Button(page.i18n("Help"), Icon("question-circle"), color = ElementColor.Info) {
            showHelp()
        }
        val cancel = Button(page.i18n("Close")) { dialog?.active = false }
        val validateButton = Button(page.i18n("Validate"), Icon("check"), color = ElementColor.Primary) { validate() }
        // stable id, as for the zone: the tutorial points at this button only while it exists
        validateButton.root.id = VALIDATE_ID

        val body = listOf<BulmaElement>(
            Field(Label(page.i18n("Pattern")), Control(textInput)),
            Field(Control(fileInput)),
            errorLine,
            hint
        )

        val card = ModalCard(page.i18n("Load a stencil"), body, listOf(helpButton, cancel, validateButton)) { closed ->
            closed.root.parentNode?.removeChild(closed.root)
            if (dialog === closed) dialog = null
        }
        card.root.classList.add(CARD_CLASS)
        dialog = card
        page.root.appendChild(card.root)
        card.active = true
    }

    // ------------------------------------------------------------------------ help

    /**
     * Contextual help of the window and of the edit bar: the four accepted formats, what
     * happens to the ids the model does not have, and the paste rules the stencil follows.
     *
     * Public because the edit bar has its own "?" button: the same text explains the formats of
     * the window and the switches the paste of a stencil obeys.
     */
    fun showHelp() {
        val formats = Section(
            p(page.i18n("The format is detected on its own, without a selector. Four formats are accepted; anything else is refused and nothing is loaded.")),
            Table(
                body = listOf(
                    helpLine("RLE", page.i18n("RLE (Golly): a line x = width, y = height, optionally followed by rule = B3/S23, then the cells on the following lines. Lines starting with # are comments, #N naming the pattern. b (or .) is an empty cell and o a live one; a number repeats the symbol that follows, $ ends a row and ! ends the pattern. A body made of b and o only is two-state: o designates the first grain of the model, id 0, exactly like O in a .cells file, so a Game of Life pattern loads in a one-grain model. An uppercase letter introduces a state of its own (A = 10, B = 11 ... Z = 35): the body is then multi-state, 2A is two cells of grain 10, and o is grain 1 again.")),
                    helpLine(".cells", page.i18n("Plain text .cells: one line per row, . for an empty cell and O (or o) for a live one; ! starts a comment and !Name: gives the name of the pattern. The format has two states only, so a live cell designates the first grain of the model, id 0.")),
                    helpLine(page.i18n("Matrix"), page.i18n("Character matrix: one character per cell, . for an empty cell and 0-9 then a-f (or A-F) for the grains of id 0 to 15; commas, spaces and tabs are ignored, ! and # lines are comments.")),
                    helpLine(".nbd2", page.i18n(".nbd2 project: the populated environment stored in the file is extracted, with the grain of every cell, and cropped on the bounding box of the agents; the empty border of the grid is dropped."))
                ),
                fullWidth = true, striped = true
            )
        )

        val grainIds = Section(
            SubTitle(page.i18n("Grains the model does not have")),
            p(page.i18n("A cell designating a grain the model does not have becomes empty, since there is nothing to paste there. The journal reports the number of cells ignored and the ids in cause, once per load; a pattern that loses every cell is not loaded at all."))
        )

        val paste = Section(
            SubTitle(page.i18n("Paste and the edit bar")),
            p(page.i18n("The stencil arrives in the clipboard like a copy: it floats under the mouse, is anchored with a left click, turned with Ctrl+R and mirrored with Ctrl+F or Ctrl+Shift+F. The Overwrite ? switch decides whether the paste replaces the agents already there or only fills the empty cells of the pattern; Toric ? decides whether a pattern that overflows the grid wraps on the opposite side. These are the rules of the manual paste, unchanged."))
        )

        page.modalDialog(
            page.i18n("Stencil — accepted formats"),
            listOf<BulmaElement>(formats, grainIds, paste),
            Button(page.i18n("Close"), color = ElementColor.Primary) {}
        )
    }

    /** One line of the table of formats: its name on the left, what it accepts on the right. */
    private fun helpLine(name: String, description: String) = TableRow(
        TableCell(name).apply { root.classList.add("has-text-weight-bold") },
        TableCell(description)
    )

    companion object {

        /**
         * Ids of the two controls the tutorial "Load a stencil" points at. They carry no style:
         * the window is rebuilt on every opening, so the tutorial cannot hold a reference to its
         * elements and looks them up by id instead — an id, unlike a CSS shape, says exactly
         * which control is meant.
         */
        const val TEXT_ID = "centy-stencil-text"
        const val VALIDATE_ID = "centy-stencil-validate"

        /** Class of the whole window, used by the tutorial as a fallback target. */
        const val CARD_CLASS = "centy-stencil-dialog"
    }

    // ----------------------------------------------------------------------- files

    /** Reads a file as text, in the language of the browser's decoder (UTF-8 by default). */
    private fun readText(file: File, onText: (String) -> Unit, onError: () -> Unit) {
        val reader = FileReader()
        reader.onload = {
            when (val result = reader.result) {
                is String -> onText(result)
                else -> onError()
            }
        }
        reader.onerror = { onError() }
        reader.readAsText(file)
    }
}
