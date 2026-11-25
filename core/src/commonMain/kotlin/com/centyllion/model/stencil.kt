package com.centyllion.model

import com.centyllion.i18n.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * A pattern pasted into the edition grid: a rectangle of cells, each one empty
 * ([STENCIL_EMPTY]) or holding the id of a grain of the model.
 *
 * The cells are stored row by row (`row * width + column`), exactly like the cells of the
 * clipboard of the simulation view (`SimulatorViewController.clipboard<IntArray>`): a stencil is
 * therefore loaded as is, and the existing rotations, mirrors, anchor and paste rules apply
 * without any conversion.
 *
 * Reading a stencil is a pure function of a text ([parseStencil]): no browser, no DOM, no
 * simulator. Checking it against the model of the moment ([restrictTo]) is pure too, so both
 * are covered by `:core:jvmTest`.
 */
data class Stencil(
    val name: String,
    val width: Int,
    val height: Int,
    val cells: List<Int>
) {

    init {
        require(width >= 0 && height >= 0) { "a stencil has a positive size" }
        require(cells.size == width * height) { "a stencil holds exactly width * height cells" }
    }

    /** True when no cell of the pattern holds a grain: there is nothing to paste. */
    val isEmpty: Boolean get() = cells.all { it == STENCIL_EMPTY }

    /** Number of cells holding a grain. */
    val filledCells: Int get() = cells.count { it != STENCIL_EMPTY }

    /** Grain id of a cell, [STENCIL_EMPTY] outside the pattern. */
    fun idAt(column: Int, row: Int): Int = when {
        column < 0 || column >= width || row < 0 || row >= height -> STENCIL_EMPTY
        else -> cells[row * width + column]
    }

    /**
     * The same pattern reduced to the bounding box of its grains, which is what a saved project
     * needs: a `.nbd2` file stores the whole grid, with its empty border, while a stencil is
     * only interesting where it is populated. An empty pattern is returned unchanged (there is
     * no box to compute).
     */
    fun cropped(): Stencil {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (row in 0 until height) {
            for (column in 0 until width) {
                if (cells[row * width + column] == STENCIL_EMPTY) continue
                if (column < left) left = column
                if (column > right) right = column
                if (row < top) top = row
                if (row > bottom) bottom = row
            }
        }
        if (right < 0 || bottom < 0) return this
        val croppedWidth = right - left + 1
        val croppedHeight = bottom - top + 1
        return Stencil(
            name, croppedWidth, croppedHeight,
            List(croppedWidth * croppedHeight) { index ->
                cells[(top + index / croppedWidth) * width + left + index % croppedWidth]
            }
        )
    }
}

/** Cell of a [Stencil] that stays transparent: nothing is written, nothing is erased. */
const val STENCIL_EMPTY: Int = -1

/** Format a text was recognised as, so the window can tell what it read. */
enum class StencilFormat {
    /** Golly RLE: `x = 3, y = 3` then `bob$2bo$3o!`. */
    RLE,

    /** Plain text `.cells`: lines of `.` and `O`, comments starting with `!`. */
    CELLS,

    /** One character per cell: `.` empty, `0`-`9` then `a`-`f` for the grains. */
    MATRIX,

    /** NetBioDyn project: the populated environment of the grid it stores. */
    PROJECT
}

/** Why a text was refused. Each problem carries one translated sentence, see [message]. */
enum class StencilProblem {
    /** Nothing to read at all (blank text, empty file). */
    EMPTY_CONTENT,

    /** No accepted format: neither RLE, nor `.cells`, nor a matrix, nor a project. */
    UNKNOWN_FORMAT,

    /** The format was recognised but the pattern holds no grain. */
    EMPTY_PATTERN,

    /** A `.nbd2` project whose environment has no agent. */
    EMPTY_PROJECT,

    /** An RLE header followed by a body that cannot be read, [StencilParse.Rejected.position]. */
    MALFORMED_RLE,

    /** A JSON text that is not a readable NetBioDyn project. */
    MALFORMED_PROJECT
}

/**
 * Result of [parseStencil]: either a pattern with the format it was read as, or a refusal with
 * a problem and, for a malformed RLE body, the character where the reading stopped.
 */
sealed interface StencilParse {

    data class Parsed(val format: StencilFormat, val stencil: Stencil) : StencilParse

    data class Rejected(val problem: StencilProblem, val position: Int = -1) : StencilParse
}

/** Translated sentence of a refusal: what is accepted, or where the reading stopped. */
fun StencilProblem.message(locale: Locale, position: Int = -1): String = when (this) {
    StencilProblem.EMPTY_CONTENT -> locale.i18n(
        "The content is empty: paste a pattern or choose a file."
    )

    StencilProblem.UNKNOWN_FORMAT -> locale.i18n(
        "Unrecognized format. Accepted: RLE (header x = 3, y = 3), plain text .cells (lines of . and O), " +
            "a character matrix (. for an empty cell, 0-9 then a-f for the grains) and a .nbd2 project."
    )

    StencilProblem.EMPTY_PATTERN -> locale.i18n(
        "The pattern is empty: there is nothing to paste."
    )

    StencilProblem.EMPTY_PROJECT -> locale.i18n(
        "This project has no populated environment: there is nothing to copy."
    )

    StencilProblem.MALFORMED_RLE -> if (position < 0) {
        locale.i18n("The RLE pattern cannot be read.")
    } else {
        locale.i18n("The RLE pattern cannot be read at character %0.", position)
    }

    StencilProblem.MALFORMED_PROJECT -> locale.i18n(
        "This file is not a readable NetBioDyn project (.nbd2)."
    )
}

/**
 * Reads a stencil from a text, detecting the format on its own — there is no selector to
 * choose it — in this order:
 *
 * 1. a text starting with `{` is a NetBioDyn project (`.nbd2`), read as the populated
 *    environment of the grid it stores, cropped on its bounding box;
 * 2. an RLE header `x = <width>, y = <height>` (optionally followed by `, rule = B3/S23`,
 *    preceded by `#` comments) makes the text an RLE pattern — a body without any uppercase
 *    letter is two-state, so its `o` designates grain 0 like the `O` of a `.cells` file,
 *    while the first uppercase letter brings back the multi-state rule (state = grain id);
 * 3. lines of `.`, `O`/`o` and `!` comments make it a plain text `.cells` pattern;
 * 4. one character per cell — `.` empty, `0`-`9` then `a`-`f` (or `A`-`F`) for the grains,
 *    with commas, spaces and tabs ignored — makes it a multi-agent matrix;
 * 5. everything else is refused with [StencilProblem.UNKNOWN_FORMAT].
 *
 * A pattern without a single grain is refused ([StencilProblem.EMPTY_PATTERN]): loading it
 * would only offer a transparent frame, and pasting it over the agents already there would
 * erase them when the "Écraser" switch is on.
 */
fun parseStencil(text: String): StencilParse {
    val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
    if (normalized.isBlank()) return StencilParse.Rejected(StencilProblem.EMPTY_CONTENT)
    val trimmed = normalized.trim()
    // a project is the only accepted format that is not read line by line
    if (trimmed.startsWith("{")) return parseProject(trimmed)
    val lines = normalized.split('\n')
    rleHeader(lines)?.let { header -> return parseRle(lines, header) }
    parseCells(lines)?.let { return it }
    parseMatrix(lines)?.let { return it }
    return StencilParse.Rejected(StencilProblem.UNKNOWN_FORMAT)
}

// ------------------------------------------------------------------------------- RLE

/** Position of an RLE size header in the text, with the size it declares. */
private data class RleHeader(val lineIndex: Int, val width: Int, val height: Int)

private val rleHeaderPattern = Regex(
    """^\s*x\s*=\s*(\d+)\s*,\s*y\s*=\s*(\d+)\s*(?:,\s*rule\s*=\s*\S+\s*)?$""",
    RegexOption.IGNORE_CASE
)

private val rleNamePattern = Regex("""^#\s*[Nn]\s+(.+)$""")

/**
 * The RLE header of the text, or null when the text is not an RLE file. Comments (`#`) and
 * blank lines may precede it; the first line that is neither must be the header, otherwise the
 * text is not an RLE pattern at all.
 */
private fun rleHeader(lines: List<String>): RleHeader? {
    for ((index, line) in lines.withIndex()) {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
        val match = rleHeaderPattern.matchEntire(trimmed) ?: return null
        val width = match.groupValues[1].toIntOrNull() ?: return null
        val height = match.groupValues[2].toIntOrNull() ?: return null
        return RleHeader(index, width, height)
    }
    return null
}

/**
 * Body of an RLE pattern, following the Golly encoding:
 *
 * - `b` (or `.`) is an empty cell, `o` a live one;
 * - an uppercase letter is a state of its own: `A` is state 10, `B` 11 … `Z` 35, so that
 *   `2A` is two cells of grain 10 — the multi-state rule of Golly;
 * - a number before a symbol repeats it, `$` ends a row (`3$` ends three rows) and `!` ends
 *   the pattern.
 *
 * How a live cell is numbered depends on the body, because RLE serves two very different
 * files. As long as the body uses no uppercase letter it is a **two-state** pattern (`b`/`o`
 * only), and the two binary formats then read alike: `o` designates the **first grain** of the
 * model (id 0), exactly like `O` in a `.cells` file, so that a Game of Life pattern drops into
 * a one-grain model whose only grain is 0. The first uppercase letter makes the pattern
 * multi-state, and the Golly rule comes back: the state number *is* the grain id, so `o` is
 * then grain 1 — a pattern mixing `o` and `A` has to number its cells as it did before, and
 * the unknown ids are reported by [restrictTo].
 */
private fun parseRle(lines: List<String>, header: RleHeader): StencilParse {
    val width = header.width
    val height = header.height
    if (width <= 0 || height <= 0) return StencilParse.Rejected(StencilProblem.EMPTY_PATTERN)
    val name = lines.take(header.lineIndex)
        .firstNotNullOfOrNull { rleNamePattern.matchEntire(it.trim())?.groupValues?.get(1)?.trim() }
        ?: ""
    val body = buildString {
        for (index in header.lineIndex + 1 until lines.size) {
            val line = lines[index]
            // a comment may interrupt the body, exactly like a comment before the header
            if (line.trimStart().startsWith("#")) continue
            append(line)
            append('\n')
        }
    }

    val cells = IntArray(width * height) { STENCIL_EMPTY }
    // an uppercase letter is the only way an RLE body leaves the two-state alphabet: its
    // absence means a binary pattern, whose live cells designate the first grain of the model
    val twoState = body.none { it in 'A'..'Z' }
    val liveId = if (twoState) 0 else 1
    var row = 0
    var column = 0
    var run = 0
    var hasRun = false

    /** Writes [id] over the pending run of cells, or reports where the body overflows. */
    fun put(id: Int, position: Int): StencilParse.Rejected? {
        val length = if (hasRun) run else 1
        if (length <= 0) return StencilParse.Rejected(StencilProblem.MALFORMED_RLE, position)
        if (row >= height || column + length > width) {
            return StencilParse.Rejected(StencilProblem.MALFORMED_RLE, position)
        }
        repeat(length) { cells[row * width + column++] = id }
        run = 0
        hasRun = false
        return null
    }

    for (position in body.indices) {
        val symbol = body[position]
        when {
            symbol.isWhitespace() -> Unit
            symbol in '0'..'9' -> {
                // a count is only meaningful before a symbol: "0o" is refused by put
                run = run * 10 + (symbol - '0')
                hasRun = true
            }

            symbol == 'b' || symbol == '.' -> put(STENCIL_EMPTY, position)?.let { return it }
            symbol == 'o' -> put(liveId, position)?.let { return it }
            symbol in 'A'..'Z' -> put(10 + (symbol - 'A'), position)?.let { return it }
            symbol == '$' -> {
                val length = if (hasRun) run else 1
                if (length <= 0) return StencilParse.Rejected(StencilProblem.MALFORMED_RLE, position)
                row += length
                column = 0
                run = 0
                hasRun = false
                if (row > height) return StencilParse.Rejected(StencilProblem.MALFORMED_RLE, position)
            }

            symbol == '!' -> break
            else -> return StencilParse.Rejected(StencilProblem.MALFORMED_RLE, position)
        }
    }
    val stencil = Stencil(name, width, height, cells.toList())
    return if (stencil.isEmpty) {
        StencilParse.Rejected(StencilProblem.EMPTY_PATTERN)
    } else {
        StencilParse.Parsed(StencilFormat.RLE, stencil)
    }
}

// ---------------------------------------------------------------------------- .cells

/**
 * Plain text `.cells` pattern: one line per row, `.` for an empty cell and `O` (or `o`) for a
 * live one, `!` starting a comment, the first `!Name:` line giving the name of the pattern.
 *
 * The format is two-state and carries no id: a live cell designates the **first grain** of the
 * model (id 0), the only grain a `.cells` file can mean.
 *
 * Returns null when the text is not a `.cells` pattern, or when it holds no live cell (an empty
 * pattern is not this format's business: the caller refuses it with
 * [StencilProblem.EMPTY_PATTERN]).
 */
private fun parseCells(lines: List<String>): StencilParse? {
    var name = ""
    val rows = mutableListOf<String>()
    for (raw in lines) {
        val line = raw.trimEnd()
        if (line.isBlank()) continue
        if (line.startsWith("!")) {
            if (name.isEmpty()) {
                val comment = line.substring(1).trim()
                if (comment.startsWith("Name:", ignoreCase = true)) {
                    name = comment.substring("Name:".length).trim()
                }
            }
            continue
        }
        if (line.any { it != '.' && it != 'O' && it != 'o' }) return null
        rows.add(line)
    }
    if (rows.isEmpty()) return null
    if (rows.none { row -> row.any { it == 'O' || it == 'o' } }) return null
    val width = rows.maxOf { it.length }
    val height = rows.size
    val cells = ArrayList<Int>(width * height)
    for (row in rows) {
        for (column in 0 until width) {
            val symbol = row.getOrNull(column)
            cells.add(if (symbol == 'O' || symbol == 'o') 0 else STENCIL_EMPTY)
        }
    }
    return StencilParse.Parsed(StencilFormat.CELLS, Stencil(name, width, height, cells))
}

// --------------------------------------------------------------------------- matrix

/**
 * Multi-agent matrix: one character per cell, `.` for an empty cell, `0`-`9` then `a`-`f` (or
 * `A`-`F`) for the grains of id 0 to 15. Commas, spaces and tabs separate the cells and are
 * ignored, `!` and `#` start a comment line, blank lines are ignored. A line shorter than the
 * widest one is padded with empty cells, so a trailing `.` may be omitted.
 *
 * Returns null when a character is outside this alphabet: the text is then not a matrix, and
 * the caller refuses it as an unknown format.
 */
private fun parseMatrix(lines: List<String>): StencilParse? {
    val rows = mutableListOf<List<Int>>()
    for (raw in lines) {
        val line = raw.trim()
        if (line.isEmpty()) continue
        if (line.startsWith("!") || line.startsWith("#")) continue
        val cells = mutableListOf<Int>()
        for (symbol in line) {
            when {
                symbol == '.' -> cells.add(STENCIL_EMPTY)
                symbol == ',' || symbol == ' ' || symbol == '\t' -> Unit
                symbol in '0'..'9' -> cells.add(symbol - '0')
                symbol in 'a'..'f' -> cells.add(10 + (symbol - 'a'))
                symbol in 'A'..'F' -> cells.add(10 + (symbol - 'A'))
                else -> return null
            }
        }
        if (cells.isNotEmpty()) rows.add(cells)
    }
    if (rows.isEmpty()) return null
    val width = rows.maxOf { it.size }
    val height = rows.size
    val cells = ArrayList<Int>(width * height)
    for (row in rows) {
        for (column in 0 until width) {
            cells.add(row.getOrNull(column) ?: STENCIL_EMPTY)
        }
    }
    if (cells.all { it == STENCIL_EMPTY }) return StencilParse.Rejected(StencilProblem.EMPTY_PATTERN)
    return StencilParse.Parsed(StencilFormat.MATRIX, Stencil("", width, height, cells))
}

// -------------------------------------------------------------------- .nbd2 project

/**
 * Populated environment of a NetBioDyn project: the `simulation.agents` list of the `.nbd2`
 * file, read as a row-major grid of grain ids (`-1` for an empty cell), then cropped on the
 * bounding box of its grains.
 *
 * The size follows the file: `simulation.width`/`height` when they are written (older files),
 * otherwise the custom size of the settings, otherwise their square preset (`100` by default).
 * A project storing no agent, or whose whole environment is empty, is refused
 * ([StencilProblem.EMPTY_PROJECT]).
 */
private fun parseProject(text: String): StencilParse {
    val root = try {
        Json.parseToJsonElement(text)
    } catch (error: Throwable) {
        return StencilParse.Rejected(StencilProblem.MALFORMED_PROJECT)
    }
    val project = root as? JsonObject ?: return StencilParse.Rejected(StencilProblem.MALFORMED_PROJECT)
    val simulation = project["simulation"] as? JsonObject
        ?: return StencilParse.Rejected(StencilProblem.MALFORMED_PROJECT)
    val settings = simulation["settings"] as? JsonObject
    val preset = settings?.get("size").asIntOrNull() ?: 100
    val customWidth = settings?.get("customWidth").asIntOrNull() ?: 0
    val customHeight = settings?.get("customHeight").asIntOrNull() ?: 0
    val square = preset.coerceAtLeast(1)
    val width = simulation["width"].asIntOrNull()
        ?: if (customWidth > 0 && customHeight > 0) customWidth else square
    val height = simulation["height"].asIntOrNull()
        ?: if (customWidth > 0 && customHeight > 0) customHeight else square
    if (width <= 0 || height <= 0) return StencilParse.Rejected(StencilProblem.EMPTY_PROJECT)
    val agents = simulation["agents"] as? JsonArray ?: JsonArray(emptyList())
    val cells = List(width * height) { index ->
        agents.getOrNull(index).asIntOrNull() ?: STENCIL_EMPTY
    }
    val name = (project["model"] as? JsonObject)?.get("name").asTextOrNull()
        ?: simulation["name"].asTextOrNull()
        ?: ""
    val cropped = Stencil(name, width, height, cells).cropped()
    return if (cropped.isEmpty) {
        StencilParse.Rejected(StencilProblem.EMPTY_PROJECT)
    } else {
        StencilParse.Parsed(StencilFormat.PROJECT, cropped)
    }
}

private fun kotlinx.serialization.json.JsonElement?.asIntOrNull(): Int? =
    (this as? JsonPrimitive)?.intOrNull

private fun kotlinx.serialization.json.JsonElement?.asTextOrNull(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }

// ------------------------------------------------------------------------ model check

/**
 * A stencil checked against the model of the moment: the cells designating a grain that does
 * not exist became empty, and the load reports how many cells and which ids were dropped
 * ([ignoredCells], [unknownIds]).
 */
data class StencilFiltering(
    val stencil: Stencil,
    val ignoredCells: Int,
    val unknownIds: List<Int>
) {
    /** True when at least one cell designated a grain the model does not have. */
    val hasIgnoredCells: Boolean get() = ignoredCells > 0
}

/**
 * Drops the cells that designate a grain the model does not have: a pattern may come from
 * another project, whose ids do not mean anything here. Nothing else changes — the size, the
 * place of the cells and the known ids stay as they were read.
 */
fun Stencil.restrictTo(model: GrainModel): StencilFiltering {
    val known = model.grains.mapTo(mutableSetOf()) { it.id }
    var ignored = 0
    val unknown = mutableSetOf<Int>()
    val restricted = cells.map { id ->
        when {
            id == STENCIL_EMPTY || id in known -> id
            else -> {
                ignored += 1
                unknown.add(id)
                STENCIL_EMPTY
            }
        }
    }
    return StencilFiltering(copy(cells = restricted), ignored, unknown.sorted())
}

/** Journal line reporting the cells a load had to ignore: `%0` cells, `%1` the ids. */
const val STENCIL_IGNORED_KEY = "Stencil: %0 cell(s) ignored, unknown grain id(s) %1."

/** Journal line reporting a loaded stencil: `%0` its width, `%1` its height. */
const val STENCIL_LOADED_KEY = "Stencil loaded: %0 x %1 cells, in the clipboard. Click the grid to anchor it, then paste."

/** Journal line of a stencil that designates no grain of the model: nothing was loaded. */
const val STENCIL_ALL_UNKNOWN_KEY =
    "Stencil: every cell designates an unknown grain, nothing was loaded."

/** Text of [STENCIL_IGNORED_KEY] for [filtering], in the language of [locale]. */
fun stencilIgnoredMessage(locale: Locale, filtering: StencilFiltering): String =
    locale.i18n(STENCIL_IGNORED_KEY, filtering.ignoredCells, filtering.unknownIds.joinToString(", "))
