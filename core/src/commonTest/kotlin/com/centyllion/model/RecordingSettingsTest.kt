package com.centyllion.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The settings of the Records tab: bounds of the JPEG quality, number of pixels per cell,
 * separator of the CSV files, and the round trip through the text kept in `localStorage`.
 *
 * Nothing here needs a browser: the settings are one data class and the functions of
 * `recording.kt` are pure, so the rules the interface relies on are checked in `:core:jvmTest`.
 */
class RecordingSettingsTest {

    // ---------------------------------------------------------------- defaults

    @Test
    fun testTheDefaultsAreTheSafeOnes() {
        val settings = RecordingSettings()
        assertEquals(CaptureFormat.Jpeg, settings.captureFormat)
        assertEquals(0.95, settings.jpegQuality)
        assertEquals(CaptureResolution.Screen, settings.captureResolution)
        assertEquals(4, settings.pixelsPerCell)
        assertEquals(CsvSeparator.Tab, settings.csvSeparator)
        // the quality only matters for a lossy format
        assertTrue(settings.usesQuality)
        assertFalse(settings.copy(captureFormat = CaptureFormat.Png).usesQuality)
    }

    @Test
    fun testTheQualityIsClampedAndSnappedToItsStep() {
        // bounds: nothing below 0.5, nothing above 1.0
        assertEquals(0.5, clampJpegQuality(-3.0))
        assertEquals(0.5, clampJpegQuality(0.0))
        assertEquals(1.0, clampJpegQuality(1.7))
        assertEquals(1.0, clampJpegQuality(Double.POSITIVE_INFINITY))
        assertEquals(0.5, clampJpegQuality(Double.NEGATIVE_INFINITY))
        // a NaN cannot be compared: it gives the default instead of an unusable value
        assertEquals(DEFAULT_JPEG_QUALITY, clampJpegQuality(Double.NaN))
        // the slider has ten steps of 0.05: a dragged or stored value lands on one of them
        assertEquals(0.5, clampJpegQuality(0.51))
        assertEquals(0.5, clampJpegQuality(0.5))
        assertEquals(0.95, clampJpegQuality(0.96))
        assertEquals(1.0, clampJpegQuality(0.98))
        assertEquals(0.75, clampJpegQuality(0.75))
        // the binary representation must not leak into the stored text (0.9500000000000001)
        assertEquals("0.95", clampJpegQuality(0.95).toString())
    }

    @Test
    fun testThePixelsPerCellStayBetweenOneAndEight() {
        assertEquals(1, clampPixelsPerCell(0))
        assertEquals(1, clampPixelsPerCell(-12))
        assertEquals(1, clampPixelsPerCell(MIN_PIXELS_PER_CELL))
        assertEquals(8, clampPixelsPerCell(9))
        assertEquals(8, clampPixelsPerCell(Int.MAX_VALUE))
        assertEquals(4, clampPixelsPerCell(4))
        assertEquals(8, clampPixelsPerCell(MAX_PIXELS_PER_CELL))
    }

    @Test
    fun testTheQualityIsDisplayedWithTwoDecimals() {
        // the text of the slider: a dot, never a comma of the locale
        assertEquals("0.50", jpegQualityLabel(0.5))
        assertEquals("0.95", jpegQualityLabel(0.95))
        assertEquals("0.70", jpegQualityLabel(0.7))
        assertEquals("1.00", jpegQualityLabel(1.0))
        // out of bounds values are displayed as the slider shows them, not as stored
        assertEquals("1.00", jpegQualityLabel(3.0))
        assertEquals("0.50", jpegQualityLabel(0.0))
    }

    // -------------------------------------------------------------- decoding

    @Test
    fun testAnAbsentValueGivesTheDefaults() {
        assertEquals(RecordingSettings(), decodeRecordingSettings(null))
        assertEquals(RecordingSettings(), decodeRecordingSettings(""))
        assertEquals(RecordingSettings(), decodeRecordingSettings("   \n  "))
    }

    @Test
    fun testACorruptValueGivesTheDefaults() {
        // a random sentence, a truncated entry, a value of another type
        assertEquals(RecordingSettings(), decodeRecordingSettings("hello world"))
        assertEquals(RecordingSettings(), decodeRecordingSettings("{"))
        assertEquals(RecordingSettings(), decodeRecordingSettings("format"))
        // a readable key with an unreadable value keeps the default of that field only
        assertEquals(
            RecordingSettings(captureFormat = CaptureFormat.Png),
            decodeRecordingSettings("format=png\nquality=lots2")
        )
    }

    @Test
    fun testAnUnknownSeparatorFallsBackOnTheTabulation() {
        assertEquals(CsvSeparator.Tab, CsvSeparator.fromId(null))
        assertEquals(CsvSeparator.Tab, CsvSeparator.fromId(""))
        assertEquals(CsvSeparator.Tab, CsvSeparator.fromId("pipe"))
        assertEquals(CsvSeparator.Tab, CsvSeparator.fromId("TABULATION"))
        assertEquals(CsvSeparator.Tab, decodeRecordingSettings("separator=pipe").csvSeparator)
        // the known ones are read, case and blanks included
        assertEquals(CsvSeparator.Comma, CsvSeparator.fromId(" comma "))
        assertEquals(CsvSeparator.Semicolon, CsvSeparator.fromId("Semicolon"))
        assertEquals(CsvSeparator.Space, CsvSeparator.fromId("space"))
        assertEquals(CsvSeparator.Tab, CsvSeparator.fromId("tab"))
    }

    @Test
    fun testAnUnknownFormatOrResolutionFallsBackOnTheDefault() {
        assertEquals(CaptureFormat.Jpeg, CaptureFormat.fromId("webp"))
        assertEquals(CaptureFormat.Png, CaptureFormat.fromId("PNG"))
        assertEquals(CaptureResolution.Screen, CaptureResolution.fromId("window"))
        assertEquals(CaptureResolution.Grid, CaptureResolution.fromId(" grid "))
    }

    @Test
    fun testOutOfRangeStoredNumbersAreBroughtBackIntoTheirBounds() {
        // a hand-edited entry cannot freeze the interface with an unusable value
        assertEquals(0.5, decodeRecordingSettings("quality=0.1").jpegQuality)
        assertEquals(1.0, decodeRecordingSettings("quality=5").jpegQuality)
        assertEquals(1, decodeRecordingSettings("pixels=0").pixelsPerCell)
        assertEquals(8, decodeRecordingSettings("pixels=1000").pixelsPerCell)
    }

    @Test
    fun testTheSettingsSurviveTheRoundTrip() {
        val settings = RecordingSettings(
            captureFormat = CaptureFormat.Png,
            jpegQuality = 0.65,
            captureResolution = CaptureResolution.Grid,
            pixelsPerCell = 6,
            csvSeparator = CsvSeparator.Semicolon
        )
        assertEquals(settings, decodeRecordingSettings(encodeRecordingSettings(settings)))
        assertEquals(RecordingSettings(), decodeRecordingSettings(encodeRecordingSettings(RecordingSettings())))
        // the encoding is one line per setting, with untranslated identifiers
        val text = encodeRecordingSettings(settings)
        assertEquals(5, text.lines().size)
        assertTrue(text.contains("format=png"))
        assertTrue(text.contains("separator=semicolon"))
        // a value out of bounds is stored inside its bounds, not as the caller wrote it
        val bounded = encodeRecordingSettings(RecordingSettings(jpegQuality = 2.0, pixelsPerCell = 0))
        assertTrue(bounded.contains("quality=1.0"))
        assertTrue(bounded.contains("pixels=1"))
    }

    // ------------------------------------------------------------------- CSV

    @Test
    fun testTheSeparatorIsWrittenBetweenTheColumns() {
        val values = listOf("step", "A", "B")
        assertEquals("step\tA\tB", csvLine(values, CsvSeparator.Tab))
        assertEquals("step,A,B", csvLine(values, CsvSeparator.Comma))
        assertEquals("step;A;B", csvLine(values, CsvSeparator.Semicolon))
        assertEquals("step A B", csvLine(values, CsvSeparator.Space))
    }

    @Test
    fun testANameHoldingTheSeparatorIsQuoted() {
        // a comma inside a model name would add a column to the header without the quotes
        assertEquals("\"Membrane, interne\"", csvField("Membrane, interne", CsvSeparator.Comma))
        assertEquals("\"Membrane; interne\"", csvField("Membrane; interne", CsvSeparator.Semicolon))
        assertEquals("\"a\tb\"", csvField("a\tb", CsvSeparator.Tab))
        assertEquals("\"a b\"", csvField("a b", CsvSeparator.Space))
        // another separator is harmless
        assertEquals("Membrane, interne", csvField("Membrane, interne", CsvSeparator.Tab))
        assertEquals("Membrane, interne", csvField("Membrane, interne", CsvSeparator.Semicolon))
        // an empty name and a plain one need no quotes
        assertEquals("", csvField("", CsvSeparator.Comma))
        assertEquals("A", csvField("A", CsvSeparator.Comma))
    }

    @Test
    fun testADoubleQuoteAndALineBreakAreQuotedAndDoubled() {
        assertEquals("\"a\"\"b\"", csvField("a\"b", CsvSeparator.Comma))
        assertEquals("\"a\nb\"", csvField("a\nb", CsvSeparator.Comma))
        assertEquals("\"a\rb\"", csvField("a\rb", CsvSeparator.Comma))
        // the header of a model named after the separator keeps its number of columns
        val header = csvLine(listOf("step", "A,B", "C;B", "D E"), CsvSeparator.Comma)
        assertEquals("step,\"A,B\",C;B,D E", header)
    }

    // ---------------------------------------------------------------- capture

    @Test
    fun testTheCaptureNameFollowsTheChosenFormat() {
        assertEquals("GoL__3.jpg", captureFileName("GoL", 3))
        assertEquals("GoL__3.jpg", captureFileName("GoL", 3, CaptureFormat.Jpeg.extension))
        assertEquals("GoL__3.png", captureFileName("GoL", 3, CaptureFormat.Png.extension))
        assertEquals("captures/GoL/GoL__3.png", captureRelativePath("GoL", 3, CaptureFormat.Png.extension))
        // the model part is still cleaned: the format changes the end of the name only
        assertEquals("a_b__2.png", captureFileName("a/b", 2, "png"))
    }
}
