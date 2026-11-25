package com.centyllion.model

import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Settings of what the application records: the pictures taken by the `Capture()` meta-action and
 * the CSV files exported from the curves.
 *
 * They are interface preferences, not part of a model: they live in the `localStorage` of the
 * browser (see the web client), and never in the `.nbd2` file. Everything the interface needs to
 * turn a stored value into a usable setting — the bounds of the JPEG quality, the number of
 * pixels per cell, the separator of the CSV files, the encoding of the whole object — is a pure
 * function of this file, so `:core:jvmTest` can check the rules without a browser, a canvas or a
 * file system.
 */

/** Format of the pictures written by `Capture()`. */
enum class CaptureFormat(
    /** Name used in the stored settings: short, stable, never translated. */
    val id: String,
    /** MIME type handed to `canvas.toDataURL`. */
    val mimeType: String,
    /** Extension of the file written in the working folder, without the dot. */
    val extension: String
) {
    /** Lossy JPEG: small files, and the format of every capture written before this setting. */
    Jpeg("jpeg", "image/jpeg", "jpg"),

    /** Lossless PNG: much larger files, every cell exactly as drawn. */
    Png("png", "image/png", "png");

    companion object {
        /** [Jpeg] unless [raw] names another known format: an unknown value is never an error. */
        fun fromId(raw: String?): CaptureFormat =
            entries.firstOrNull { it.id == raw?.trim()?.lowercase() } ?: Jpeg
    }
}

/**
 * Where the size of a capture comes from.
 */
enum class CaptureResolution(
    /** Name used in the stored settings. */
    val id: String
) {
    /** The canvas as it is displayed: the picture of what the visitor sees, grid lines included. */
    Screen("screen"),

    /**
     * The grid itself, rendered off screen at [RecordingSettings.pixelsPerCell] pixels per cell:
     * the detail of the picture then follows the grid, not the size of the window.
     */
    Grid("grid");

    companion object {
        /** [Screen] unless [raw] names another known mode. */
        fun fromId(raw: String?): CaptureResolution =
            entries.firstOrNull { it.id == raw?.trim()?.lowercase() } ?: Screen
    }
}

/**
 * Separator written between the columns of the exported CSV files.
 *
 * [Tab] is the default: a tabulation never appears in a model, an agent or a field name, so the
 * header of a file opened in a spreadsheet stays on one line — while a comma is the decimal
 * separator of many locales.
 */
enum class CsvSeparator(
    /** Name used in the stored settings. */
    val id: String,
    /** Character actually written between two columns. */
    val character: Char
) {
    Tab("tab", '\t'),
    Comma("comma", ','),
    Semicolon("semicolon", ';'),
    Space("space", ' ');

    companion object {
        /**
         * [Tab] unless [raw] names another known separator: a stored value written by another
         * version, a corrupt entry or an empty string all fall back on the tabulation.
         */
        fun fromId(raw: String?): CsvSeparator =
            entries.firstOrNull { it.id == raw?.trim()?.lowercase() } ?: Tab
    }
}

/** Lowest JPEG quality offered by the slider: below, the cells are visibly blurred. */
const val MIN_JPEG_QUALITY = 0.5

/** Highest JPEG quality offered by the slider: 1.0 asks the encoder for its best. */
const val MAX_JPEG_QUALITY = 1.0

/** Step of the quality slider: 0.05, so ten steps cover [MIN_JPEG_QUALITY]..[MAX_JPEG_QUALITY]. */
const val JPEG_QUALITY_STEP = 0.05

/**
 * Default JPEG quality: 0.95 rather than the 0.92 of the first captures, whose ringing around the
 * edges of a cell was visible on a grid magnified on screen.
 */
const val DEFAULT_JPEG_QUALITY = 0.95

/** Fewest pixels written per cell in the grid-sized capture: 1 pixel per cell, the raw grid. */
const val MIN_PIXELS_PER_CELL = 1

/** Most pixels written per cell in the grid-sized capture. */
const val MAX_PIXELS_PER_CELL = 8

/** Default pixels per cell: 4, readable without inflating the file beyond a screen. */
const val DEFAULT_PIXELS_PER_CELL = 4

/**
 * [value] brought into [MIN_JPEG_QUALITY]..[MAX_JPEG_QUALITY] and snapped to a step of
 * [JPEG_QUALITY_STEP], so a stored or dragged value is always one the slider can show.
 *
 * `NaN` cannot be compared, so it gives [DEFAULT_JPEG_QUALITY]; an infinite value is simply
 * clamped to the nearest bound.
 */
fun clampJpegQuality(value: Double): Double {
    if (value.isNaN()) return DEFAULT_JPEG_QUALITY
    val clamped = value.coerceIn(MIN_JPEG_QUALITY, MAX_JPEG_QUALITY)
    val steps = ((clamped - MIN_JPEG_QUALITY) / JPEG_QUALITY_STEP).roundToInt()
    val snapped = MIN_JPEG_QUALITY + steps * JPEG_QUALITY_STEP
    // 0.5 + 9 * 0.05 is 0.9500000000000001 in binary floating point: the round keeps the stored
    // value readable (and equal to the one written in the settings)
    return round(snapped.coerceIn(MIN_JPEG_QUALITY, MAX_JPEG_QUALITY) * 100.0) / 100.0
}

/** [value] brought into [MIN_PIXELS_PER_CELL]..[MAX_PIXELS_PER_CELL]. */
fun clampPixelsPerCell(value: Int): Int = value.coerceIn(MIN_PIXELS_PER_CELL, MAX_PIXELS_PER_CELL)

/**
 * The quality as the slider displays it: two decimals, a dot whatever the locale of the browser
 * (a comma would be read as a column separator by a spreadsheet), for example `0.95` or `1.00`.
 *
 * Written by hand rather than with a format string: the interface must show the same text on
 * every platform, and the core has no `String.format`.
 */
fun jpegQualityLabel(value: Double): String {
    val hundredths = (clampJpegQuality(value) * 100).roundToInt()
    val fraction = hundredths % 100
    return "${hundredths / 100}.${if (fraction < 10) "0$fraction" else "$fraction"}"
}

/**
 * What the interface records, as one object.
 *
 * The defaults are the safe ones: a JPEG at [DEFAULT_JPEG_QUALITY], the picture of the screen,
 * four pixels per cell and tabulations between the columns of the CSV files.
 */
data class RecordingSettings(
    val captureFormat: CaptureFormat = CaptureFormat.Jpeg,
    val jpegQuality: Double = DEFAULT_JPEG_QUALITY,
    val captureResolution: CaptureResolution = CaptureResolution.Screen,
    val pixelsPerCell: Int = DEFAULT_PIXELS_PER_CELL,
    val csvSeparator: CsvSeparator = CsvSeparator.Tab
) {
    /** The same settings with each number back inside its bounds. */
    fun normalized(): RecordingSettings = copy(
        jpegQuality = clampJpegQuality(jpegQuality),
        pixelsPerCell = clampPixelsPerCell(pixelsPerCell)
    )

    /** True when the quality matters: a lossless PNG ignores it, so the slider is disabled. */
    val usesQuality: Boolean get() = captureFormat == CaptureFormat.Jpeg
}

/** Key of a stored setting, as written by [encodeRecordingSettings]. */
private const val KEY_FORMAT = "format"
private const val KEY_QUALITY = "quality"
private const val KEY_RESOLUTION = "resolution"
private const val KEY_PIXELS = "pixels"
private const val KEY_SEPARATOR = "separator"

/**
 * Encodes [settings] as the plain text kept in `localStorage`: one `key=value` line per setting,
 * using the untranslated identifiers of the enumerations.
 *
 * The format is deliberately dumb — no JSON parser is needed to read it, an added key is ignored
 * by an older version and a missing key takes its default — and it is written and read by the two
 * pure functions of this file, so a stored object is checked by `:core:jvmTest`.
 */
fun encodeRecordingSettings(settings: RecordingSettings): String {
    val normalized = settings.normalized()
    return listOf(
        "$KEY_FORMAT=${normalized.captureFormat.id}",
        "$KEY_QUALITY=${normalized.jpegQuality}",
        "$KEY_RESOLUTION=${normalized.captureResolution.id}",
        "$KEY_PIXELS=${normalized.pixelsPerCell}",
        "$KEY_SEPARATOR=${normalized.csvSeparator.id}"
    ).joinToString("\n")
}

/**
 * Reads back what [encodeRecordingSettings] wrote.
 *
 * Every field falls back on its own default when it is absent, unreadable or out of range, so a
 * truncated, hand-edited or corrupt entry never prevents the application from recording anything:
 * an empty string, a random sentence or a value of another type all give [RecordingSettings].
 * Unknown keys and unknown enumeration values are ignored rather than rejected.
 */
fun decodeRecordingSettings(raw: String?): RecordingSettings {
    if (raw.isNullOrBlank()) return RecordingSettings()
    var settings = RecordingSettings()
    // a stored blob is short and line oriented; a huge entry is not one of ours, but reading it
    // line by line costs nothing and stops at the end of the text
    for (line in raw.lineSequence()) {
        val separator = line.indexOf('=')
        if (separator <= 0) continue
        val key = line.substring(0, separator).trim().lowercase()
        val value = line.substring(separator + 1).trim()
        settings = when (key) {
            KEY_FORMAT -> settings.copy(captureFormat = CaptureFormat.fromId(value))
            KEY_QUALITY -> settings.copy(jpegQuality = value.toDoubleOrNull()?.let(::clampJpegQuality) ?: settings.jpegQuality)
            KEY_RESOLUTION -> settings.copy(captureResolution = CaptureResolution.fromId(value))
            KEY_PIXELS -> settings.copy(pixelsPerCell = value.toIntOrNull()?.let(::clampPixelsPerCell) ?: settings.pixelsPerCell)
            KEY_SEPARATOR -> settings.copy(csvSeparator = CsvSeparator.fromId(value))
            else -> settings
        }
    }
    return settings.normalized()
}

/**
 * One column of a CSV file: [value] as it is, or quoted when it would otherwise break the line —
 * when it holds the separator, a double quote or a line break.
 *
 * Quoting follows the usual rule (the field is wrapped in double quotes, and each inner double
 * quote is doubled), so a model, an agent or a field named after the separator — `Membrane, interne`
 * with a comma — still produces a header of the right number of columns.
 */
fun csvField(value: String, separator: CsvSeparator): String {
    val needsQuotes = value.any {
        it == separator.character || it == '"' || it == '\n' || it == '\r'
    }
    return if (needsQuotes) "\"${value.replace("\"", "\"\"")}\"" else value
}

/** One line of a CSV file: [values] joined by the character of [separator], each one escaped. */
fun csvLine(values: List<String>, separator: CsvSeparator): String =
    values.joinToString(separator.character.toString()) { csvField(it, separator) }
