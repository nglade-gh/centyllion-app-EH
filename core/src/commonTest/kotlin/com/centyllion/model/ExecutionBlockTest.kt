package com.centyllion.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Step 1a of the execution blocks: data model, serialization and migration.
 *
 * Nothing is executed differently at this stage: the simulator still reads
 * [GrainModel.behaviours], and these tests prove that a file written before blocks
 * existed reloads into exactly the same behaviour list, wrapped in a single block.
 */
class ExecutionBlockTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun project(model: GrainModel) =
        ModelAndSimulation(model, createSimulation(size = 10) { -1 })

    @Test
    fun testAnEmptyModelGetsOneEmptyBlock() {
        val migrated = GrainModel().migrated()
        assertEquals(1, migrated.executionBlocks.size)
        val block = migrated.executionBlocks.single()
        assertTrue(block.behaviours.isEmpty(), "a fresh model has no behaviour in its block")
        assertTrue(block.enabled)
        assertEquals(1.0, block.probability)
        assertTrue(block.executable)
    }

    @Test
    fun testAnOldFileIsMigratedIntoASingleBlock() {
        val model = dendriteModel()
        val text = json.encodeToString(ModelAndSimulation.serializer(), project(model))
        // files written by the previous version carry no executionBlocks key
        assertFalse(text.contains("executionBlocks"), "no block is written for a model without blocks")

        val loaded = json.decodeFromString(ModelAndSimulation.serializer(), text)
        assertEquals(0, loaded.model.executionBlocks.size, "decoding alone does not invent blocks")

        val migrated = loaded.migrated()
        assertEquals(1, migrated.model.executionBlocks.size)
        val block = migrated.model.executionBlocks.single()
        // uuid is transient: a reloaded behaviour gets a new one, so compare its content
        assertEquals(model.behaviours.map { it.name }, block.behaviours.map { it.name },
            "the block holds every behaviour, in order")
        assertEquals(model.behaviours.map { it.name }, migrated.model.behaviours.map { it.name },
            "the reference list is untouched")
        assertEquals(model.grains.map { it.name }, migrated.model.grains.map { it.name })
        assertTrue(block.enabled)
        assertEquals(1.0, block.probability)
    }

    @Test
    fun testMigrationIsIdempotent() {
        val once = dendriteModel().migrated()
        val twice = once.migrated()
        assertEquals(once.executionBlocks, twice.executionBlocks)
        assertEquals(once.behaviours, twice.behaviours)

        val emptyOnce = GrainModel().migrated()
        assertEquals(emptyOnce.executionBlocks, emptyOnce.migrated().executionBlocks)
    }

    @Test
    fun testMigrationKeepsTheBehaviourListIdentical() {
        // the simulator computes from model.behaviours only: an identical list, in the same
        // order, guarantees an identical computation
        val model = dendriteModel()
        val migrated = model.migrated()
        assertEquals(model.behaviours, migrated.behaviours)
        assertEquals(model.behaviours.map { it.uuid }, migrated.behaviours.map { it.uuid })
    }

    @Test
    fun testBlocksAreSerializedAndReloaded() {
        val behaviour = dendriteModel().behaviours.single()
        val model = GrainModel(
            name = "Deux blocs",
            grains = dendriteModel().grains,
            behaviours = listOf(behaviour),
            executionBlocks = listOf(
                ExecutionBlock(
                    name = "Croissance",
                    description = "premier bloc",
                    probability = 0.8,
                    agePredicates = listOf(Predicate(Operator.GreaterThanOrEquals, 2)),
                    behaviours = listOf(behaviour)
                ),
                ExecutionBlock(
                    name = "Division",
                    enabled = false,
                    probability = 0.0,
                    formula = "count(0) >= 3",
                    behaviours = emptyList()
                )
            )
        )

        val text = json.encodeToString(ModelAndSimulation.serializer(), project(model))
        assertTrue(text.contains("executionBlocks"), "blocks are written")
        assertTrue(text.contains("Croissance"))

        val reloaded = json.decodeFromString(ModelAndSimulation.serializer(), text).migrated()
        // block parameters, then the behaviour names each block owns (uuid is transient)
        val strip: (ExecutionBlock) -> ExecutionBlock = { it.copy(behaviours = emptyList()) }
        assertEquals(model.executionBlocks.map(strip), reloaded.model.executionBlocks.map(strip))
        assertEquals(
            model.executionBlocks.map { block -> block.behaviours.map { it.name } },
            reloaded.model.executionBlocks.map { block -> block.behaviours.map { it.name } }
        )
        assertEquals(model.behaviours.map { it.name }, reloaded.model.behaviours.map { it.name })
    }

    @Test
    fun testExecutableFlagFollowsEnabledAndProbability() {
        assertTrue(ExecutionBlock().executable)
        assertTrue(ExecutionBlock(probability = 0.5).executable)
        assertFalse(ExecutionBlock(probability = 0.0).executable, "probability 0 means never")
        assertFalse(ExecutionBlock(enabled = false).executable, "unchecked means never")
        assertFalse(ExecutionBlock(enabled = false, probability = 1.0).executable)
    }
}
