package com.centyllion.client.journal

import bulma.BulmaElement
import bulma.Button
import bulma.Div
import bulma.ElementColor
import bulma.Icon
import bulma.Label
import bulma.iconButton
import bulma.p
import com.centyllion.client.capture.CaptureDestination
import com.centyllion.client.capture.CaptureFallback
import com.centyllion.client.capture.JournalExportResult
import com.centyllion.client.capture.WorkFolder
import com.centyllion.client.page.BulmaPage
import com.centyllion.model.JournalBuffer
import com.centyllion.model.JournalEntry
import com.centyllion.model.JournalLevel
import com.centyllion.model.Simulator
import com.centyllion.model.formatJournal
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLDivElement
import org.w3c.dom.HTMLElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

/** localStorage key: whether the journal panel is folded (`1`) or open (`0`). */
const val JOURNAL_COLLAPSED_KEY = "centyllion-journal-collapsed"

/**
 * The journal panel of the simulation view: what the simulation did on its own, under the grid
 * and under the drawing and edition bars.
 *
 * It replaces the notices that used to float over the grid. Every entry shows its level as a
 * prefix (`META`, `INFO`, `AVERT.`, `ERREUR`) **and** in colour, so the colour is never the only
 * signal; the step number sits between the level and the message. The header folds the list and
 * keeps the number of warnings in view when it is folded; the folded state is remembered in
 * localStorage, so a reload keeps the reader's choice.
 *
 * The panel reads the bounded buffer of the [Simulator] ([JournalBuffer]): it never copies the
 * entries, only repaints when the revision changes. The export button writes the journal beside
 * the captures (`journal/<model>__journal.txt` in the working folder) or downloads the same
 * text; it is disabled while the journal is empty and never empties it.
 */
class JournalPanel(
    private val page: BulmaPage,
    /** Current simulator: the controller replaces it when the project changes. */
    private val simulator: () -> Simulator
) {

    /** True when the list is folded. Read once from localStorage, written on every toggle. */
    private var collapsed: Boolean = readCollapsed()

    private val chevron = Icon("chevron-down")

    private val titleLabel = Label().apply {
        root.classList.add("journal-title")
    }

    /** Number of warnings, shown in the header while the list is folded. */
    private val warningLabel = Label().apply {
        root.classList.add("journal-warnings")
    }

    /** Header: the clickable area that folds and unfolds the list. */
    private val header = Div(chevron, titleLabel, warningLabel, classes = "journal-header").apply {
        root.setAttribute("role", "button")
        root.setAttribute("tabindex", "0")
        root.addEventListener("click", { toggle() })
        root.addEventListener("keydown", { event ->
            val key = (event as? KeyboardEvent)?.key
            if (key == "Enter" || key == " ") {
                event.preventDefault()
                toggle()
            }
        })
    }

    val exportButton: Button = iconButton(Icon("download"), ElementColor.Primary, rounded = true) {
        exportJournal()
    }.apply {
        root.setAttribute("title", page.i18n("Export the journal"))
        root.setAttribute("aria-label", page.i18n("Export the journal"))
    }

    private val helpButton: Button = iconButton(Icon("question-circle"), ElementColor.Primary, rounded = true) {
        showHelp()
    }.apply {
        root.setAttribute("title", page.i18n("Journal help"))
        root.setAttribute("aria-label", page.i18n("Journal help"))
    }

    private val actions = Div(exportButton, helpButton, classes = "journal-actions")

    private val bar = Div(header, actions, classes = "journal-bar")

    /** Scrolling list of the entries. */
    private val entriesBox = Div(classes = "journal-entries")

    val container: BulmaElement = Div(bar, entriesBox, classes = "journal-panel")

    /** Simulator whose journal is currently displayed, to notice a replacement of the project. */
    private var shownJournal: JournalBuffer? = null

    /** Last revision painted, so an unchanged journal costs nothing per frame. */
    private var shownRevision: Int = -1

    /**
     * Repaints the panel when the journal changed since the last call (or when [force] is true).
     * Called on every animation frame by the run controller: the check is one integer compare.
     */
    fun refresh(force: Boolean = false) {
        val journal = simulator().journal
        if (!force && journal === shownJournal && journal.revision == shownRevision) return
        shownJournal = journal
        shownRevision = journal.revision
        render()
    }

    /** Appends a line to the journal of the current simulation and repaints the panel. */
    fun append(level: JournalLevel, message: String) {
        val current = simulator()
        current.journal.add(current.step, level, message)
        refresh(force = true)
    }

    // ------------------------------------------------------------------ rendering

    private fun render() {
        val journal = simulator().journal
        val scroll = entriesBox.root
        // follow the newest line when the reader is already at the bottom, stay put otherwise
        val stickToBottom = scroll.scrollHeight - scroll.scrollTop - scroll.clientHeight < 24

        titleLabel.text = page.i18n("Journal (%0)", journal.size)
        warningLabel.text = page.i18n("%0 warning(s)", journal.warningCount)
        exportButton.disabled = journal.isEmpty
        renderEntries(journal.entries)

        val folded = collapsed
        container.root.classList.toggle("is-collapsed", folded)
        chevron.root.style.transform = if (folded) "rotate(-90deg)" else ""
        // the number of warnings is what the folded header announces
        warningLabel.root.style.display = if (folded && journal.warningCount > 0) "" else "none"
        header.root.setAttribute("aria-expanded", (!folded).toString())
        header.root.setAttribute("title", page.i18n("Journal of the simulation"))
        header.root.setAttribute("aria-label", page.i18n("Journal of the simulation"))

        if (stickToBottom) scroll.scrollTop = scroll.scrollHeight.toDouble()
    }

    private fun renderEntries(entries: List<JournalEntry>) {
        val box = entriesBox.root
        while (box.firstChild != null) box.removeChild(box.firstChild!!)
        if (entries.isEmpty()) {
            val empty = document.createElement("p") as HTMLElement
            empty.className = "journal-empty"
            empty.textContent = page.i18n("The journal is empty.")
            box.appendChild(empty)
            return
        }
        for (entry in entries) {
            box.appendChild(row(entry))
        }
    }

    /** One line: level prefix, step number and message, colour carried by the level class. */
    private fun row(entry: JournalEntry): HTMLDivElement {
        val line = document.createElement("div") as HTMLDivElement
        line.className = "journal-entry is-${levelClass(entry.level)}"
        val level = document.createElement("span") as HTMLElement
        level.className = "journal-level"
        // the prefix keeps the meaning readable without the colour
        level.textContent = "• ${levelLabel(entry.level)}"
        val step = document.createElement("span") as HTMLElement
        step.className = "journal-step"
        step.textContent = "#${entry.step}"
        val message = document.createElement("span") as HTMLElement
        message.className = "journal-message"
        // textContent, never innerHTML: a message is data, not markup
        message.textContent = entry.message
        line.appendChild(level)
        line.appendChild(step)
        line.appendChild(message)
        return line
    }

    private fun levelClass(level: JournalLevel): String = when (level) {
        JournalLevel.META -> "meta"
        JournalLevel.INFO -> "info"
        JournalLevel.WARNING -> "warning"
        JournalLevel.ERROR -> "error"
    }

    private fun levelLabel(level: JournalLevel): String = when (level) {
        JournalLevel.META -> page.i18n("Meta")
        JournalLevel.INFO -> page.i18n("Info")
        JournalLevel.WARNING -> page.i18n("Warn.")
        JournalLevel.ERROR -> page.i18n("Error")
    }

    // --------------------------------------------------------------------- folding

    private fun toggle() {
        collapsed = !collapsed
        writeCollapsed(collapsed)
        render()
    }

    private fun readCollapsed(): Boolean = try {
        window.localStorage.getItem(JOURNAL_COLLAPSED_KEY) == "1"
    } catch (t: Throwable) {
        // storage refused: the panel opens by default
        false
    }

    private fun writeCollapsed(value: Boolean) {
        try {
            window.localStorage.setItem(JOURNAL_COLLAPSED_KEY, if (value) "1" else "0")
        } catch (t: Throwable) {
            console.warn("Journal: cannot remember the folded state.", t)
        }
    }

    // ---------------------------------------------------------------------- export

    /**
     * Exports the journal as plain text: the header (date and model name) and one tabulated line
     * per entry. Written in `journal/` of the working folder when it is usable, downloaded
     * otherwise — the outcome is then a journal line itself, so the reader learns where the file
     * is without a floating notice. The journal is not emptied.
     */
    private fun exportJournal() {
        val current = simulator()
        val entries = current.journal.entries.toList()
        if (entries.isEmpty()) return
        val text = formatJournal(entries, current.model.name, nowText())
        WorkFolder.saveJournal(current.model.name, text) { result -> report(result) }
    }

    /** Local date and time of the export, written in the header of the file. */
    private fun nowText(): String = js("new Date().toLocaleString()") as String

    /** Says where the export went: the folder, or a download and why. */
    private fun report(result: JournalExportResult) {
        when (result.destination) {
            CaptureDestination.FOLDER -> append(
                JournalLevel.INFO,
                page.i18n("Journal exported to %0.", result.path ?: result.fileName)
            )
            CaptureDestination.DOWNLOAD -> append(
                fallbackLevel(result.fallback),
                page.i18n(fallbackMessage(result.fallback), result.fileName)
            )
        }
    }

    /** A download is normal without a folder, a warning when it replaces a refused folder. */
    private fun fallbackLevel(fallback: CaptureFallback): JournalLevel = when (fallback) {
        CaptureFallback.PERMISSION -> JournalLevel.WARNING
        CaptureFallback.WRITE_ERROR -> JournalLevel.ERROR
        else -> JournalLevel.INFO
    }

    private fun fallbackMessage(fallback: CaptureFallback): String = when (fallback) {
        CaptureFallback.UNSUPPORTED ->
            "Journal downloaded: %0 (this browser has no access to a folder)."
        CaptureFallback.PERMISSION ->
            "Journal downloaded: %0 (the permission of the working folder must be renewed)."
        CaptureFallback.WRITE_ERROR ->
            "Journal downloaded: %0 (the working folder could not be written)."
        else -> "Journal downloaded: %0 (no working folder)."
    }

    // ------------------------------------------------------------------------ help

    /** Contextual help: the levels, their colours, the folding and the export. */
    private fun showHelp() {
        page.modalDialog(
            page.i18n("Journal of the simulation"),
            listOf(
                p(
                    page.i18n(
                        "The journal lists what the simulation does by itself: the meta-actions it executes, " +
                            "the captures it takes, the pauses it asks for, and what happens to the files it " +
                            "writes. It replaces the notices that used to cover the top of the grid."
                    )
                ),
                p(
                    page.i18n(
                        "Four levels: META (blue) for an executed meta-action, INFO (green) for a normal event, " +
                            "WARNING (orange) for something to check, ERROR (red) for a failure. The level is " +
                            "written at the beginning of every line, so the colour is never the only signal."
                    )
                ),
                p(
                    page.i18n(
                        "The header folds the panel. Folded, it keeps the number of warnings in view; the state " +
                            "is remembered in this browser. The export button writes journal/MODEL__journal.txt " +
                            "in the working folder, or downloads the same text: one line per entry, level, step " +
                            "and message separated by tabulations. The export never empties the journal."
                    )
                )
            ),
            Button(page.i18n("Close"), color = ElementColor.Primary) {}
        )
    }
}
