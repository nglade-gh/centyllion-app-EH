package com.centyllion.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A project saved with a custom grid must keep it when it is loaded. */
class CustomGridPersistenceTest {

    @Test
    fun testLoadingKeepsTheCustomGrid() {
        val simulation = createSimulation("wide", size = 100).copy(width = 1000, height = 100)
        val project = ModelAndSimulation(GrainModel(name = "wide"), simulation).migrated()
        assertEquals(1000, project.simulation.settings.effectiveWidth, "the width must come from the file")
        assertEquals(100, project.simulation.settings.effectiveHeight, "the height must come from the file")
        assertTrue(project.simulation.settings.isCustomSize, "the grid is a custom one now")
    }

    @Test
    fun testApplyingTheSettingsKeepsTheGrid() {
        val simulation = createSimulation("wide", size = 100).copy(width = 1000, height = 100)
        val project = ModelAndSimulation(GrainModel(name = "wide"), simulation).migrated()
        val afterSettings = project.simulation.updateSettings(project.simulation.settings)
        assertEquals(1000, afterSettings.width, "applying the settings must not shrink the grid")
        assertEquals(100, afterSettings.height)
    }
}
