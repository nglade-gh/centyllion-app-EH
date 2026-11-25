package com.centyllion.model

import com.centyllion.i18n.French
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The journal of a simulation: bounded buffer, order of the entries, and the text format of the
 * export. Everything here is a pure function of the entries, so the rules the reader relies on
 * (200 lines at most, oldest first, one tab separated line per entry) are tested without a
 * browser and without waiting.
 */
class JournalTest {

    @Test
    fun testTheBufferKeepsTheOldestEntriesOut() {
        val journal = JournalBuffer()
        repeat(JOURNAL_CAPACITY + 50) { index ->
            journal.add(JournalEntry(index, JournalLevel.INFO, "line $index"))
        }
        assertEquals(JOURNAL_CAPACITY, journal.size, "the buffer is bounded to 200 entries")
        assertEquals(JOURNAL_CAPACITY, journal.entries.size)
        assertEquals(50, journal.entries.first().step, "the 50 oldest entries left the buffer")
        assertEquals(JOURNAL_CAPACITY + 49, journal.entries.last().step, "the newest entry is kept")
        // the remaining entries are still in the order they were added
        journal.entries.zipWithNext().forEach { (previous, next) ->
            assertTrue(previous.step < next.step, "entries stay in insertion order")
        }
    }

    @Test
    fun testACustomCapacityIsHonoured() {
        val journal = JournalBuffer(capacity = 3)
        repeat(5) { journal.add(it, JournalLevel.INFO, "$it") }
        assertEquals(listOf(2, 3, 4), journal.entries.map { it.step })
    }

    @Test
    fun testACapacityOfZeroIsRefused() {
        assertFailsWith<IllegalArgumentException> { JournalBuffer(capacity = 0) }
    }

    @Test
    fun testWarningsAreCountedAndRevisionFollowsTheChanges() {
        val journal = JournalBuffer()
        val initial = journal.revision
        journal.add(1, JournalLevel.WARNING, "a")
        journal.add(2, JournalLevel.WARNING, "b")
        journal.add(3, JournalLevel.ERROR, "c")
        assertEquals(2, journal.warningCount, "only the WARNING level is counted")
        assertTrue(journal.revision > initial, "an addition changes the revision")
        val afterAdd = journal.revision
        journal.clear()
        assertTrue(journal.isEmpty)
        assertEquals(0, journal.warningCount)
        assertTrue(journal.revision > afterAdd, "a clear changes the revision too")
    }

    @Test
    fun testTheExportHasAHeaderThenOneTabSeparatedLinePerEntry() {
        val entries = listOf(
            JournalEntry(1, JournalLevel.META, "Pause(2) executed"),
            JournalEntry(4, JournalLevel.INFO, "Capture saved in captures/GoL/GoL__4.jpg."),
            JournalEntry(7, JournalLevel.WARNING, "The model paused the simulation for 2 s (modifier)."),
            JournalEntry(9, JournalLevel.ERROR, "The capture failed.")
        )
        val text = formatJournal(entries, "GoL Modifiers", "2026-11-25 10:30:00")
        val lines = text.split('\n')
        assertEquals("Journal — GoL Modifiers — 2026-11-25 10:30:00", lines[0], "the header carries the date and the model")
        assertEquals(
            listOf(
                "meta\t1\tPause(2) executed",
                "info\t4\tCapture saved in captures/GoL/GoL__4.jpg.",
                "avert\t7\tThe model paused the simulation for 2 s (modifier).",
                "erreur\t9\tThe capture failed."
            ),
            lines.drop(1).dropLast(1),
            "level, step and message are separated by tabulations, in plain words"
        )
        assertTrue(text.endsWith("\n"), "the last entry ends the file with a newline")
    }

    @Test
    fun testTheExportOfAnEmptyJournalIsItsHeaderAlone() {
        val text = formatJournal(emptyList(), "empty", "2026-11-25")
        assertEquals("Journal — empty — 2026-11-25\n", text)
    }

    @Test
    fun testAMessageCannotBreakTheOneLinePerEntryRule() {
        val entries = listOf(JournalEntry(2, JournalLevel.INFO, "first line\nsecond\tline\rthird"))
        val lines = formatJournal(entries, "m", "d").split('\n').dropLast(1)
        assertEquals(2, lines.size, "header plus one line")
        assertEquals("info\t2\tfirst line second line third", lines[1])
    }

    @Test
    fun testTheJournalFileNameReusesTheCleaningRulesOfACapture() {
        assertEquals("GoL_Modifiers__journal.txt", journalFileName("GoL_Modifiers"))
        assertEquals("journal/GoL_Modifiers__journal.txt", journalRelativePath("GoL_Modifiers"))
        // a separator in the model name must never create an unintended directory
        assertEquals("GoL_Modifiers__journal.txt", journalFileName("GoL/Modifiers"))
        assertEquals("journal/GoL_Modifiers__journal.txt", journalRelativePath("GoL/Modifiers"))
        assertEquals("model__journal.txt", journalFileName("   "))
    }

    @Test
    fun testTheLevelTokensAreTheDocumentedWords() {
        assertEquals("meta", journalLevelToken(JournalLevel.META))
        assertEquals("info", journalLevelToken(JournalLevel.INFO))
        assertEquals("avert", journalLevelToken(JournalLevel.WARNING))
        assertEquals("erreur", journalLevelToken(JournalLevel.ERROR))
    }

    private fun modifierModel(actions: String) = GrainModel(
        name = "journal",
        grains = listOf(Grain(id = 0, name = "a", color = "Lime", movementProbability = 0.0)),
        modifiers = listOf(SimulationModifier(name = "m", id = 0, formula = "step == 0", actions = actions))
    )

    private fun simulation() = createSimulation("journal", size = 10) { -1 }

    @Test
    fun testTheCoreFeedsTheJournalWhenAMetaActionRuns() {
        val simulator = Simulator(modifierModel("Pause(2), Capture()"), simulation())
        simulator.oneStep()

        assertEquals(2, simulator.journal.size, "one line per executed meta-action")
        val pause = simulator.journal.entries[0]
        assertEquals(JournalLevel.META, pause.level)
        assertEquals(1, pause.step, "the entry carries the step number the interface displays")
        assertEquals("Pause(2) executed", pause.message)

        val capture = simulator.journal.entries[1]
        assertEquals("Capture() executed", capture.message)
        assertEquals(1, simulator.captureRequestCount, "the execution is unchanged")
    }

    @Test
    fun testTheMessageOfAMetaActionFollowsTheLocaleOfTheSimulator() {
        val simulator = Simulator(modifierModel("Pause(2)"), simulation(), French)
        simulator.oneStep()
        assertEquals("Pause(2) exécutée", simulator.journal.entries.single().message)
    }

    @Test
    fun testAnIgnoredMetaActionLeavesNoLine() {
        val simulator = Simulator(modifierModel("RunNStep(0)"), simulation())
        simulator.oneStep()
        assertTrue(simulator.journal.isEmpty, "RunNStep(0) asks for no step: nothing ran, nothing is logged")
    }

    @Test
    fun testARewindKeepsTheHistoryOfTheSession() {
        val simulator = Simulator(modifierModel("Pause()"), simulation())
        simulator.oneStep()
        assertEquals(1, simulator.journal.size)
        simulator.reset()
        assertEquals(1, simulator.journal.size, "the journal is the history of the session, not a state")
    }
}
