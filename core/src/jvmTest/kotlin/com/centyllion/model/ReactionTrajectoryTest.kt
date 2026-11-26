package com.centyllion.model

import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Non-regression of the existing models: a project that does not use the "any agent" reactive
 * must place exactly the same agents as before the wildcard was introduced.
 *
 * The reaction engine pairs each line with a chosen neighbour; the historical pairing sorts both
 * lists on the grain id, and it is kept as is for every behaviour without a wildcard. The
 * signatures below were recorded on the code **before** the wildcard was added (FNV-1a 64 over
 * the agent ids, after 3, 10 and 25 steps) and must never move.
 *
 * Only the agent ids are signed, not the ages: when two concurrent behaviours reach the same cell
 * in the same step, the age kept by that cell depends on the order in which the parallel path
 * applies them, which it does not guarantee (pre-existing: the *ids* are stable, the ages are
 * not, and `patee_fourmis.netbiodyn` flips between two values from one run to the next). The
 * inheritance of the age itself is locked by `AnyReactiveTest` on deterministic models.
 */
class ReactionTrajectoryTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
        coerceInputValues = true
    }

    /** file -> "agents after 3 steps, after 10 steps, after 25 steps". */
    private val expected = mapOf(
        "coeur.netbiodyn" to
                "1574456154417814293 -4561149467673841835 -5420932653446749552",
        "Dyctie.netbiodyn" to
                "-8196840978637887003 -3811077967061411099 -1279085965954127387",
        "flux2.netbiodyn" to
                "-8068449818507140402 3828611981042524078 -1139990811438617457",
        "cellules_excitables.netbiodyn" to
                "-60917564783359531 4184177252594261381 1395406493166820342",
        "classe_covid_particules.netbiodyn" to
                "4401621452539813960 5117013010380228680 -8402543266420512381",
        "patee_fourmis.netbiodyn" to
                "6325339754560300606 1143717251328311767 9114991840912859163",
        "neurone_ntA_ntI_2.netbiodyn" to
                "-2993894717052397351 1352818905373959322 1328936281295477786",
        "modele_complique_zombies.netbiodyn" to
                "-3971113154746041444 1156280455289971478 5181550485209671702",
    )

    private fun modelsFolder(): File? {
        val candidates = listOf(
            "../../NetBioDyn_Models",
            "../../../NetBioDyn_Models",
            System.getenv("NETBIODYN_MODELS"),
        )
        return candidates.filterNotNull().map { File(it) }.firstOrNull { it.isDirectory }
    }

    /** FNV-1a 64 over the little-endian bytes of the values, so the signature is platform free. */
    private fun fnv(values: IntArray): Long {
        var hash = -3750763034362895579L // FNV-1a 64 offset basis
        for (v in values) {
            var x = v.toLong()
            for (i in 0 until 8) {
                hash = hash xor (x and 0xff)
                hash *= 1099511628211L
                x = x shr 8
            }
        }
        return hash
    }

    private fun signature(file: File): String {
        val project = json.decodeFromString(ModelAndSimulation.serializer(), file.readText()).migrated()
        val simulator = Simulator(project.model, project.simulation)
        try {
            val parts = mutableListOf<String>()
            var done = 0
            for (target in intArrayOf(3, 10, 25)) {
                while (done < target) {
                    simulator.oneStep()
                    done++
                }
                parts.add(fnv(simulator.agents).toString())
            }
            return parts.joinToString(" ")
        } finally {
            simulator.close()
        }
    }

    @Test
    fun theReferenceModelsKeepTheSameAgents() {
        val folder = modelsFolder() ?: return // reference folder absent: nothing to compare
        println("trajectory: ${expected.size} reference models read from ${folder.absolutePath}")
        val failures = mutableListOf<String>()
        for ((name, expectedSignature) in expected) {
            val file = File(folder, name)
            if (!file.isFile) {
                failures.add("$name: missing from ${folder.absolutePath}")
                continue
            }
            val actual = signature(file)
            if (actual != expectedSignature) {
                failures.add("$name: trajectory changed\n  expected $expectedSignature\n  actual   $actual")
            }
        }
        assertTrue(failures.isEmpty(), "reaction engine regression:\n" + failures.joinToString("\n"))
    }
}
