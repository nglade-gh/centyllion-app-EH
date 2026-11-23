package com.centyllion.model

import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Compatibility audit: every project file of the reference folder must still be readable by
 * the current format, and must survive a read / migrate / write / read cycle.
 *
 * The test is skipped (no assertion) when the reference folder is absent, so it stays usable
 * outside this machine.
 */
class LegacyModelsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
        coerceInputValues = true
    }

    private fun modelsFolder(): File? {
        val candidates = listOf(
            "../../NetBioDyn_Models",
            "../../../NetBioDyn_Models",
            System.getenv("NETBIODYN_MODELS"),
        )
        return candidates.filterNotNull().map { File(it) }.firstOrNull { it.isDirectory }
    }

    @Test
    fun testEveryReferenceModelCanBeRead() {
        val folder = modelsFolder() ?: return
        val files = folder.listFiles { file -> file.name.endsWith(".netbiodyn") }?.sortedBy { it.name } ?: return
        assertTrue(files.isNotEmpty(), "no project found in ${folder.absolutePath}")

        val failures = mutableListOf<String>()
        val report = mutableListOf<String>()
        for (file in files) {
            try {
                val project = json.decodeFromString(ModelAndSimulation.serializer(), file.readText()).migrated()
                val model = project.model
                // read / write / read: what we write must be re-readable identically, so no
                // information is lost at each save (the writer must be a fixed point)
                val first = json.encodeToString(ModelAndSimulation.serializer(), project.forSerialization())
                val again = json.decodeFromString(ModelAndSimulation.serializer(), first).migrated()
                val second = json.encodeToString(ModelAndSimulation.serializer(), again.forSerialization())
                if (first != second) {
                    failures.add("${file.name}: the written project is not stable (read/write is not a fixed point)")
                    continue
                }
                if (again.simulation.agents != project.simulation.agents) {
                    failures.add("${file.name}: the agents were altered (${project.simulation.agents.size} -> ${again.simulation.agents.size})")
                    continue
                }
                if (again.simulation.width != project.simulation.width ||
                    again.simulation.height != project.simulation.height ||
                    again.simulation.settings != project.simulation.settings
                ) {
                    failures.add("${file.name}: the grid or its settings were altered")
                    continue
                }
                if (again.model.behaviours.map { it.name to it.id } != model.behaviours.map { it.name to it.id }) {
                    failures.add("${file.name}: the behaviours were altered")
                    continue
                }
                report.add("${file.name}: ${model.grains.size} grains, ${model.behaviours.size} behaviours, ${model.fields.size} fields")
            } catch (e: Exception) {
                failures.add("${file.name}: ${e::class.simpleName}: ${e.message?.take(160)}")
            }
        }
        assertTrue(failures.isEmpty(), "incompatible projects:\n" + failures.joinToString("\n"))
        println("compatibilité : ${report.size} fichiers lus et réécrits")
    }
}
