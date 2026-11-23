package com.centyllion.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behaviour boundary conditions in a real project file: the model is written into a .nbd2
 * file of a temporary directory and read back, so the check is not limited to an in-memory
 * round trip.
 *
 * Contract, identical to the grains: a behaviour whose four walls are toric does not write
 * the key at all, and a behaviour with at least one closed wall writes all four walls
 * explicitly, so the value read back is the value written.
 */
class BehaviourBoundaryFilePersistenceTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val closed = BoundaryConditions(left = false, right = true, top = true, bottom = false)

    private fun project() = ModelAndSimulation(
        GrainModel(
            name = "walls",
            grains = listOf(Grain(id = 0, name = "A", color = "Red")),
            behaviours = listOf(
                Behaviour(name = "toric", mainReactiveId = 0, mainProductId = 0, id = 0),
                Behaviour(name = "closed", mainReactiveId = 0, mainProductId = 0, id = 1, boundaryConditions = closed),
            ),
        ),
        createSimulation(name = "sim", size = 20)
    )

    @Test
    fun aBehaviourWritesItsWallsToARealProjectFile() {
        val directory = File(System.getProperty("java.io.tmpdir"), "centyllion-boundary-test")
        assertTrue(directory.exists() || directory.mkdirs(), "cannot create ${directory.absolutePath}")
        val file = File(directory, "behaviour-boundaries.nbd2")
        try {
            file.writeText(json.encodeToString(ModelAndSimulation.serializer(), project()))
            assertTrue(file.length() > 0, "the project file must not be empty")

            val behaviours = Json.parseToJsonElement(file.readText())
                .jsonObject["model"]!!.jsonObject["behaviours"]!!.jsonArray
            assertEquals(2, behaviours.size)

            assertNull(
                behaviours[0].jsonObject["boundaryConditions"],
                "a behaviour with four toric walls must not write the key"
            )
            val walls = assertNotNull(
                behaviours[1].jsonObject["boundaryConditions"]?.jsonObject,
                "a behaviour with a closed wall must write its four walls"
            )
            assertEquals(setOf("left", "right", "top", "bottom"), walls.keys)
            assertFalse(walls.getValue("left").jsonPrimitive.boolean)
            assertTrue(walls.getValue("right").jsonPrimitive.boolean)
            assertTrue(walls.getValue("top").jsonPrimitive.boolean)
            assertFalse(walls.getValue("bottom").jsonPrimitive.boolean)

            val loaded = json.decodeFromString(ModelAndSimulation.serializer(), file.readText())
            val toric = loaded.model.behaviours.first { it.name == "toric" }
            val withWalls = loaded.model.behaviours.first { it.name == "closed" }
            assertEquals(BoundaryConditions(), toric.boundaryConditions, "a behaviour without the key is toric everywhere")
            assertFalse(toric.hasClosedBoundary)
            assertEquals(closed, withWalls.boundaryConditions, "the four walls must come back identical")
            assertTrue(withWalls.hasClosedBoundary)
            // the whole project survives the file: re-writing it gives the same content
            assertEquals(
                json.encodeToString(ModelAndSimulation.serializer(), loaded),
                file.readText(),
                "the file must be a fixed point of read / write"
            )
        } finally {
            file.delete()
            directory.delete()
        }
    }
}
