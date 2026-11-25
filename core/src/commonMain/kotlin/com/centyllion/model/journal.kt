package com.centyllion.model

import com.centyllion.i18n.Locale

/**
 * The journal of a simulation: what the simulation did on its own, in the order it happened.
 *
 * It replaces the floating notices that used to cover the top of the grid (a modifier pausing
 * the run, a capture written or downloaded): the reader now finds those messages under the
 * simulation, with their date-free step number and a level that says how serious they are.
 *
 * The buffer lives in the core ([Simulator.journal]) and the meta-actions feed it **as they
 * execute**, so a line is a fact of the run, not a message the view invented afterwards. It is
 * bounded: once [capacity] entries are kept, the oldest one leaves room for the new one. Memory
 * use is therefore constant whatever the duration of the run.
 *
 * Display and export are pure functions of the entries ([formatJournal], [journalFileName],
 * [journalRelativePath]): the level tokens, the column order and the file name are all
 * testable in `:core:jvmTest`, without a browser.
 */

/** Severity of a journal entry, from the most neutral to the most serious. */
enum class JournalLevel {
    /** A meta-action of a simulation modifier executed (`Pause(2) executed`). */
    META,

    /** A normal event the reader may want to know (a capture written, a model imported). */
    INFO,

    /** Something to check: a pause asked by the model, a permission to renew, a download. */
    WARNING,

    /** A failure: the capture could not be taken, the working folder could not be written. */
    ERROR
}

/**
 * One line of the journal: the step it belongs to, its level and its message.
 *
 * [step] is the step number the interface displays once the step is over, exactly like the
 * number carried by a capture name: the meta-actions run before the counter is incremented.
 */
data class JournalEntry(val step: Int, val level: JournalLevel, val message: String)

/** Number of entries kept by a [JournalBuffer]: enough to read a session, bounded in memory. */
const val JOURNAL_CAPACITY = 200

/** Folder, directly inside the working folder, that receives the exported journals. */
const val JOURNAL_FOLDER = "journal"

/** End of the name of an exported journal: `<model>__journal.txt`. */
const val JOURNAL_FILE_SUFFIX = "__journal.txt"

/**
 * Bounded list of [JournalEntry], oldest first.
 *
 * Adding an entry beyond [capacity] drops the oldest one; [revision] is incremented on every
 * change so the view can repaint only when something happened, without comparing the whole
 * list. A rewind ([Simulator.reset]) does not clear it: the journal is the history of the
 * session, not a part of the simulation state.
 */
class JournalBuffer(val capacity: Int = JOURNAL_CAPACITY) {

    init {
        require(capacity > 0) { "a journal keeps at least one entry" }
    }

    private val items = mutableListOf<JournalEntry>()

    /** Entries, oldest first (read-only view). */
    val entries: List<JournalEntry> get() = items

    val size: Int get() = items.size

    val isEmpty: Boolean get() = items.isEmpty()

    /** Number of [JournalLevel.WARNING] entries: what the folded header announces. */
    val warningCount: Int get() = items.count { it.level == JournalLevel.WARNING }

    /** Incremented on every [add] and [clear]: the view repaints when it changes. */
    var revision: Int = 0
        private set

    /** Appends an entry, dropping the oldest ones when the buffer is full. */
    fun add(entry: JournalEntry) {
        items.add(entry)
        while (items.size > capacity) items.removeAt(0)
        revision += 1
    }

    /** Appends an entry built from its parts. */
    fun add(step: Int, level: JournalLevel, message: String) = add(JournalEntry(step, level, message))

    /** Empties the journal (only the reader does it, from the panel of the view). */
    fun clear() {
        if (items.isEmpty()) return
        items.clear()
        revision += 1
    }
}

/**
 * Call of a meta-action as the journal writes it: the name and the arguments that were
 * evaluated at run time, for instance `Pause(2)` or `Add(0, 5)`.
 */
fun metaActionCall(name: String, arguments: List<String> = emptyList()): String =
    if (arguments.isEmpty()) "$name()" else "$name(${arguments.joinToString(", ")})"

/**
 * Message of an executed meta-action, translated with [locale]: "Pause(2) executed" /
 * "Pause(2) exécutée". [call] is the text built by [metaActionCall].
 */
fun journalMetaAction(locale: Locale, call: String): String = locale.i18n("%0 executed", call)

/**
 * Level token of the export, in plain text and independent of the language of the interface:
 * `meta`, `info`, `avert` (warning) and `erreur` (error).
 *
 * The export is a data file that must stay readable by the same tools whatever the interface
 * language is, so these four words are fixed.
 */
fun journalLevelToken(level: JournalLevel): String = when (level) {
    JournalLevel.META -> "meta"
    JournalLevel.INFO -> "info"
    JournalLevel.WARNING -> "avert"
    JournalLevel.ERROR -> "erreur"
}

/** Replaces the characters that would break the one-line-per-entry rule of the export. */
fun sanitizeJournalText(text: String): String =
    text.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ').trim()

/**
 * Text of the export: one header line carrying the date and the model name, then one line per
 * entry — level token, step and message, separated by tabulations.
 *
 * The header and the messages are cleaned by [sanitizeJournalText], so a message written on
 * several lines in the interface stays on its own line in the file. The function is pure: the
 * date is passed by the caller (the view reads the browser clock), therefore the format is
 * tested without a browser.
 */
fun formatJournal(entries: List<JournalEntry>, modelName: String, date: String): String = buildString {
    append("Journal — ").append(sanitizeJournalText(modelName)).append(" — ").append(sanitizeJournalText(date))
    append('\n')
    for (entry in entries) {
        append(journalLevelToken(entry.level)).append('\t')
        append(entry.step).append('\t')
        append(sanitizeJournalText(entry.message)).append('\n')
    }
}

/** File name of the export of the journal of [modelName]: `<model>__journal.txt`. */
fun journalFileName(modelName: String): String = "${sanitizeCaptureName(modelName)}$JOURNAL_FILE_SUFFIX"

/**
 * Path of the export relative to the working folder:
 * `journal/GoL_Modifiers__journal.txt`. The model part is cleaned exactly like a capture name,
 * so the file never creates an unintended directory.
 */
fun journalRelativePath(modelName: String): String = "$JOURNAL_FOLDER/${journalFileName(modelName)}"
