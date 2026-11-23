package com.centyllion.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The model description is part of the file: written when filled, absent when empty. */
class ModelDescriptionTest {

    private val json = Json { encodeDefaults = false }

    @Test
    fun testEmptyDescriptionIsOmitted() {
        val project = ModelAndSimulation(GrainModel(name = "m"), createSimulation("m", size = 20))
        val text = json.encodeToString(project)
        val modelNode = Json.parseToJsonElement(text).jsonObject["model"]!!.jsonObject
        assertFalse(modelNode.containsKey("description"), "an empty description must not be written")
    }

    @Test
    fun testDescriptionIsWritten() {
        val model = GrainModel(name = "m", description = "un modèle de test")
        val text = json.encodeToString(ModelAndSimulation(model, createSimulation("m", size = 20)))
        val modelNode = Json.parseToJsonElement(text).jsonObject["model"]!!.jsonObject
        assertTrue(modelNode.containsKey("description"), "a filled description must be written")
    }

    @Test
    fun testDescriptionSurvivesARoundTrip() {
        val model = GrainModel(name = "m", description = "aller-retour")
        val text = json.encodeToString(ModelAndSimulation(model, createSimulation("m", size = 20)))
        val back = json.decodeFromString<ModelAndSimulation>(text)
        assertTrue(back.model.description == "aller-retour", "the description must be read back")
    }
}
