package com.centyllion.model

import com.centyllion.i18n.English
import com.centyllion.i18n.French
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Reading a stencil is a pure function of a text: every format is recognised on its own (there
 * is no selector), a refused text says why, and the check against the model of the moment is
 * pure too. Nothing here needs a browser, a canvas or a simulator, so the rules the window
 * relies on are tested in `:core:jvmTest`.
 */
class StencilTest {

    /** Parsed stencil of a text that must be accepted. */
    private fun parsed(text: String): StencilParse.Parsed = when (val result = parseStencil(text)) {
        is StencilParse.Parsed -> result
        is StencilParse.Rejected -> fail("expected a pattern, got ${result.problem}")
    }

    private fun rejected(text: String): StencilParse.Rejected = when (val result = parseStencil(text)) {
        is StencilParse.Rejected -> result
        is StencilParse.Parsed -> fail("expected a refusal, got ${result.stencil}")
    }

    /** Rows of a stencil, [grain] for a cell holding a grain and `.` for an empty one. */
    private fun shapeOf(stencil: Stencil, grain: Char = '#'): List<String> =
        (0 until stencil.height).map { row ->
            (0 until stencil.width).map { column ->
                if (stencil.idAt(column, row) == STENCIL_EMPTY) '.' else grain
            }.joinToString("")
        }

    private fun modelOf(vararg ids: Int) = GrainModel(grains = ids.map { Grain(id = it) })

    // --------------------------------------------------------------------------- RLE

    @Test
    fun testASimpleRleGliderIsRead() {
        val result = parsed("x = 3, y = 3\nbob\$2bo\$3o!")
        assertEquals(StencilFormat.RLE, result.format)
        val stencil = result.stencil
        assertEquals(3, stencil.width)
        assertEquals(3, stencil.height)
        // a body with only b and o is two-state: the state number is not written, so a live
        // cell designates the first grain of the model
        assertEquals(listOf(".0.", "..0", "000"), shapeOf(stencil, '0'))
    }

    /**
     * The two binary formats must read alike: the same glider written as `.cells` and as a
     * two-state RLE has to produce the very same pattern, so that a Game of Life pattern
     * loads in a model whose only grain is 0.
     */
    @Test
    fun testABinaryRleAndACellsPatternProduceTheSamePattern() {
        val rle = parsed("x = 3, y = 3\nbob\$2bo\$3o!").stencil
        val cells = parsed(".O.\n..O\nOOO").stencil
        assertEquals(cells.cells, rle.cells, "o and O designate the same grain, id 0")
        assertEquals(listOf(0, 0, 0, 0, 0), rle.cells.filter { it != STENCIL_EMPTY })
        // a one-grain model keeps every cell: nothing is dropped as an unknown id
        val filtering = rle.restrictTo(modelOf(0))
        assertEquals(0, filtering.ignoredCells)
        assertTrue(filtering.unknownIds.isEmpty())
        assertEquals(rle.cells, filtering.stencil.cells)
    }

    @Test
    fun testRleCommentsAndRuleAreIgnored() {
        val text = """
            #N Glider
            #C The classic glider, four generations.
            #r 23b3o${'$'}21bo2bo${'$'}21bobo${'$'}22b2o!
            #c a lowercase comment too
            x = 3, y = 3, rule = B3/S23
            bob${'$'}2bo${'$'}3o!
        """.trimIndent()
        val result = parsed(text)
        assertEquals(StencilFormat.RLE, result.format)
        assertEquals("Glider", result.stencil.name, "the #N comment names the pattern")
        assertEquals(listOf(".0.", "..0", "000"), shapeOf(result.stencil, '0'))
        assertFalse(result.stencil.isEmpty)
    }

    @Test
    fun testRleCountsRepeatSymbolsAndRows() {
        // two live cells, two dead ones, then two more rows
        val result = parsed("x = 4, y = 3\n2o2b\$4b\$bo!")
        val stencil = result.stencil
        assertEquals(4, stencil.width)
        assertEquals(3, stencil.height)
        assertEquals(listOf("00..", "....", ".0.."), shapeOf(stencil, '0'))
    }

    @Test
    fun testRleMultiStateUsesTheStateAsTheGrainId() {
        // "2A" is two cells of state 10, "B" one cell of state 11 and "o" one cell of state 1
        val result = parsed("x = 4, y = 1\n2ABo!")
        assertEquals(StencilFormat.RLE, result.format)
        assertEquals(listOf(10, 10, 11, 1), result.stencil.cells)
        // one uppercase letter is enough: the body leaves the two-state alphabet, so `o` is
        // grain 1 again, as in Golly
        assertEquals(listOf(10, 1, 1), parsed("x = 3, y = 1\nAoo!").stencil.cells)
    }

    @Test
    fun testRleCountsBeforeUppercaseLettersStayMultiState() {
        // a count before anything other than b, o or $ is a state: "3C" is three cells of
        // grain 12, and the whole body follows the multi-state rule
        assertEquals(listOf(12, 12, 12, 1), parsed("x = 4, y = 1\n3Co!").stencil.cells)
    }

    /**
     * A real binary RLE file: 50P92.1 (catereron), from the Conway Life wiki. The reference
     * shape was decoded independently — 32 x 45, 66 live cells — and the pattern must load as
     * is in a model whose grains are 0: with `o` meaning grain 1 not a single cell would
     * survive the check against that model.
     */
    @Test
    fun testALargeBinaryRleLoadsInAOneGrainModel() {
        val text = """
            #N catereron50p92.1.rle
            #C https://conwaylife.com/wiki/50P92.1
            #C https://www.conwaylife.com/patterns/catereron50p92.1.rle
            x = 32, y = 45, rule = B3/S23
            2bo${'$'}bobo2${'$'}3bo${'$'}2b2o${'$'}2b5o${'$'}2b2ob2o${'$'}3bo4${'$'}13b3o14b2o${'$'}13bo3bo12b2o${'$'}13bo4bo${'$'}14bo3bo
            2${'$'}14bo3bo${'$'}13bo4bo${'$'}13bo3bo12b2o${'$'}13b3o14b2o6${'$'}b3o3b3o${'$'}o2bo3bo2bo${'$'}2obo3bob2o16${'$'}b
            2o5b2o${'$'}b2o5b2o!
        """.trimIndent()
        val result = parsed(text)
        assertEquals(StencilFormat.RLE, result.format)
        val stencil = result.stencil
        assertEquals("catereron50p92.1.rle", stencil.name)
        assertEquals(32, stencil.width)
        assertEquals(45, stencil.height)
        assertEquals(66, stencil.filledCells)
        assertTrue(stencil.cells.all { it == 0 || it == STENCIL_EMPTY }, "every live cell is grain 0")
        // the reference shape, read independently: row 0 is "..#", row 11 ends with "##"
        assertEquals("..#.............................", shapeOf(stencil).first())
        assertEquals(".............###..............##", shapeOf(stencil)[11])
        assertEquals(".##.....##......................", shapeOf(stencil).last())
        val filtering = stencil.restrictTo(modelOf(0))
        assertEquals(0, filtering.ignoredCells, "a one-grain model accepts the whole pattern")
        assertEquals(66, filtering.stencil.filledCells)
    }

    @Test
    fun testRleBodyErrorsAreReported() {
        // a row longer than the declared width
        val overflow = rejected("x = 2, y = 1\n3o!")
        assertEquals(StencilProblem.MALFORMED_RLE, overflow.problem)
        assertTrue(overflow.position >= 0, "the character that broke the reading is reported")

        // an unknown symbol
        assertEquals(StencilProblem.MALFORMED_RLE, rejected("x = 3, y = 1\nbo%!").problem)

        // more rows than declared
        assertEquals(StencilProblem.MALFORMED_RLE, rejected("x = 1, y = 1\no\$o!").problem)
    }

    @Test
    fun testAnRleWithoutAnyLivingCellIsRefused() {
        assertEquals(StencilProblem.EMPTY_PATTERN, rejected("x = 3, y = 3\n3b\$3b\$3b!").problem)
    }

    // ------------------------------------------------------------------------ .cells

    @Test
    fun testPlainCellsWithANameIsRead() {
        val text = """
            !Name: Glider
            !A comment that is ignored
            .O.
            ..O
            OOO
        """.trimIndent()
        val result = parsed(text)
        assertEquals(StencilFormat.CELLS, result.format)
        assertEquals("Glider", result.stencil.name)
        assertEquals(3, result.stencil.width)
        assertEquals(3, result.stencil.height)
        // the two-state format carries no id: a live cell designates the first grain of the model
        assertEquals(listOf(".0.", "..0", "000"), shapeOf(result.stencil, '0'))
    }

    @Test
    fun testPlainCellsAcceptLowercaseAndRaggedLines() {
        val result = parsed("o.o\n..\no")
        assertEquals(StencilFormat.CELLS, result.format)
        // shorter lines are padded with empty cells: the width is the widest line
        assertEquals(3, result.stencil.width)
        assertEquals(3, result.stencil.height)
        assertEquals(listOf("0.0", "...", "0.."), shapeOf(result.stencil, '0'))
        assertEquals(listOf(0, STENCIL_EMPTY, 0), result.stencil.cells.take(3))
    }

    // ------------------------------------------------------------------------ matrix

    @Test
    fun testMatrixAcceptsEverySeparator() {
        val expected = listOf(0, 1, 2, STENCIL_EMPTY, 3, STENCIL_EMPTY)
        val samples = mapOf(
            "comma" to "0,1,2\n.,3,.",
            "space" to "0 1 2\n. 3 .",
            "tab" to "0\t1\t2\n.\t3\t.",
            "mixed" to "0 , 1 ,2\n . ,3, ."
        )
        for ((label, text) in samples) {
            val result = parsed(text)
            assertEquals(StencilFormat.MATRIX, result.format, "$label: the format is a matrix")
            assertEquals(3, result.stencil.width, label)
            assertEquals(2, result.stencil.height, label)
            assertEquals(expected, result.stencil.cells, "$label: the separators are ignored")
        }
    }

    @Test
    fun testMatrixReadsHexadecimalIds() {
        val result = parsed("aF0f")
        assertEquals(StencilFormat.MATRIX, result.format)
        assertEquals(listOf(10, 15, 0, 15), result.stencil.cells)
        // the uppercase and the lowercase spelling of the same id are the same grain
        assertEquals(parsed("AF0F").stencil.cells, result.stencil.cells)
    }

    @Test
    fun testMatrixCommentLinesAreIgnored() {
        assertEquals(listOf(0, 1, 2, 3), parsed("! a comment\n# another one\n0 1\n2 3").stencil.cells)
    }

    @Test
    fun testMatrixOfOnlyEmptyCellsIsRefused() {
        assertEquals(StencilProblem.EMPTY_PATTERN, rejected("...\n...").problem)
    }

    @Test
    fun testUnknownGrainIdsBecomeEmptyCellsAndAreReported() {
        // the model only knows grains 0 and 1
        val filtering = parsed("0123\n4567").stencil.restrictTo(modelOf(0, 1))
        assertEquals(6, filtering.ignoredCells, "the six cells out of 0..1 lost their grain")
        assertEquals(listOf(2, 3, 4, 5, 6, 7), filtering.unknownIds, "the ids are listed once, sorted")
        assertTrue(filtering.hasIgnoredCells)
        assertEquals(
            listOf(0, 1) + List(6) { STENCIL_EMPTY },
            filtering.stencil.cells,
            "an unknown id leaves an empty cell"
        )
        assertEquals(4, filtering.stencil.width, "the size of the pattern does not change")
        assertEquals(2, filtering.stencil.height)
    }

    @Test
    fun testAKnownModelKeepsEveryCell() {
        val filtering = parsed("0123").stencil.restrictTo(modelOf(0, 1, 2, 3, 4))
        assertEquals(0, filtering.ignoredCells)
        assertTrue(filtering.unknownIds.isEmpty())
        assertFalse(filtering.hasIgnoredCells)
    }

    @Test
    fun testAPatternOfUnknownIdsOnlyBecomesEmpty() {
        val filtering = parsed("23").stencil.restrictTo(modelOf(0, 1))
        assertTrue(filtering.stencil.isEmpty)
        assertEquals(2, filtering.ignoredCells)
        assertEquals(listOf(2, 3), filtering.unknownIds)
    }

    // ---------------------------------------------------------------------- refusals

    @Test
    fun testArbitraryTextIsRefused() {
        assertEquals(
            StencilProblem.UNKNOWN_FORMAT,
            rejected("Bonjour, ceci n'est pas un motif.\nDeuxième ligne.").problem
        )
    }

    @Test
    fun testAnEmptyTextIsRefused() {
        assertEquals(StencilProblem.EMPTY_CONTENT, rejected("").problem)
        assertEquals(StencilProblem.EMPTY_CONTENT, rejected("   \n\t\n").problem)
    }

    @Test
    fun testTheRefusalMessagesNameTheAcceptedFormats() {
        for (locale in listOf(English, French)) {
            val message = StencilProblem.UNKNOWN_FORMAT.message(locale)
            assertTrue(message.contains("RLE"), "${locale.name}: the RLE format is named")
            assertTrue(message.contains(".cells"), "${locale.name}: the .cells format is named")
            assertTrue(message.contains(".nbd2"), "${locale.name}: the project format is named")
        }
        // the French sentence is a real translation, not the English key echoed back
        assertTrue(
            StencilProblem.UNKNOWN_FORMAT.message(French) != StencilProblem.UNKNOWN_FORMAT.message(English)
        )
    }

    @Test
    fun testTheRleRefusalReportsTheCharacter() {
        val result = rejected("x = 3, y = 1\nbo%!")
        assertTrue(result.position >= 0)
        val message = result.problem.message(French, result.position)
        assertTrue(message.contains(result.position.toString()), "the message carries the position: $message")
    }

    // ----------------------------------------------------------------------- project

    /** A `.nbd2` project: a [width] x [height] grid, [agents] being the row-major ids. */
    private fun projectOf(
        width: Int, height: Int, agents: List<Int>, modelName: String = "Test"
    ): String = """{"model":{"name":"$modelName","grains":[]},"simulation":{"name":"",""" +
        """"description":"","width":$width,"height":$height,"agents":[""" +
        agents.joinToString(",") + "]}}"

    @Test
    fun testAProjectIsCroppedOnItsBoundingBox() {
        // 5 x 5, with a 2 x 3 block of grains at columns 1..2 and rows 1..3
        val agents = MutableList(25) { -1 }
        val block = mapOf(
            (1 to 1) to 0, (2 to 1) to 1,
            (1 to 2) to 2,
            (1 to 3) to 0, (2 to 3) to 2
        )
        block.forEach { (cell, id) -> agents[cell.second * 5 + cell.first] = id }
        val result = parsed(projectOf(5, 5, agents, modelName = "GoL"))
        assertEquals(StencilFormat.PROJECT, result.format)
        val stencil = result.stencil
        assertEquals(2, stencil.width, "the empty border is cropped away")
        assertEquals(3, stencil.height)
        assertEquals(listOf(0, 1, 2, STENCIL_EMPTY, 0, 2), stencil.cells)
        assertEquals("GoL", stencil.name)
    }

    @Test
    fun testAProjectReadsTheSquarePresetOfItsSettings() {
        // the settings carry the size, the width and height keys are absent (a real .nbd2 file)
        val agents = MutableList(25) { -1 }
        agents[0] = 3
        val text = """{"model":{"name":"Sized"},"simulation":{"name":"","description":"",""" +
            """"settings":{"size":5},"agents":[""" + agents.joinToString(",") + "]}}"
        val stencil = parsed(text).stencil
        assertEquals(1, stencil.width)
        assertEquals(1, stencil.height, "cropped on the single populated cell")
        assertEquals(listOf(3), stencil.cells)
    }

    @Test
    fun testAProjectWithoutPopulatedEnvironmentIsRefused() {
        assertEquals(StencilProblem.EMPTY_PROJECT, rejected(projectOf(3, 3, List(9) { -1 })).problem)
        val noAgents = """{"model":{"name":"Empty"},"simulation":{"name":"","description":""}}"""
        assertEquals(StencilProblem.EMPTY_PROJECT, rejected(noAgents).problem)
    }

    @Test
    fun testABrokenProjectIsRefused() {
        assertEquals(StencilProblem.MALFORMED_PROJECT, rejected("{ not json").problem)
        assertEquals(StencilProblem.MALFORMED_PROJECT, rejected("""{"models":[]}""").problem)
    }

    // ------------------------------------------------------------------ cropped/utility

    @Test
    fun testCroppingAnEmptyPatternChangesNothing() {
        val stencil = Stencil("empty", 3, 2, List(6) { STENCIL_EMPTY })
        assertEquals(stencil, stencil.cropped())
        assertTrue(stencil.isEmpty)
        assertEquals(0, stencil.filledCells)
    }

    @Test
    fun testAStencilKeepsItsSize() {
        assertFailsWith<IllegalArgumentException> { Stencil("bad", 2, 2, listOf(0)) }
        assertFailsWith<IllegalArgumentException> { Stencil("bad", -1, 2, emptyList()) }
    }

    @Test
    fun testTheCellsAreReadRowByRow() {
        // the flat order of the cells is the one of the clipboard of the view: row by row
        val stencil = parsed("0 1 2\n3 4 5").stencil
        assertEquals(listOf(0, 1, 2, 3, 4, 5), stencil.cells)
        assertEquals(4, stencil.idAt(1, 1))
        assertEquals(2, stencil.idAt(2, 0))
        assertEquals(STENCIL_EMPTY, stencil.idAt(-1, 0), "outside the pattern, a cell is empty")
        assertEquals(STENCIL_EMPTY, stencil.idAt(0, 2), "outside the pattern, a cell is empty")
    }

    @Test
    fun testTheJournalMessageExistsInBothLanguages() {
        val filtering = parsed("09").stencil.restrictTo(modelOf(0))
        val french = stencilIgnoredMessage(French, filtering)
        val english = stencilIgnoredMessage(English, filtering)
        assertTrue(french.contains("1"), "the number of ignored cells is in the message: $french")
        assertTrue(french.contains("9"), "the unknown id is in the message: $french")
        assertTrue(english.contains("1") && english.contains("9"), english)
        assertTrue(french != english, "the message is translated")
    }
}
