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
                // a read / write / read cycle: the file must not lose anything essential
                val reencoded = json.encodeToString(ModelAndSimulation.serializer(), project.forSerialization())
                val again = json.decodeFromString(ModelAndSimulation.serializer(), reencoded).migrated()
                if (again.model.grains.size != model.grains.size ||
                    again.model.behaviours.size != model.behaviours.size ||
                    again.model.fields.size != model.fields.size
                ) {
                    failures.add("${file.name}: round trip differs")
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
