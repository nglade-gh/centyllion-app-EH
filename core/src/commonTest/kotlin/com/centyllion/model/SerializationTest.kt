package com.centyllion.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Verifies the .netbiodyn serialization contract for the fields whose values
 * must always be explicit in the saved file:
 * - boundary conditions: written with ALL four walls when a grain is not
 *   toric, omitted entirely when toric everywhere;
 * - count predicates: every present predicate writes grainId, op and count.
 *
 * The Json configuration mirrors the web app serializer (encodeDefaults =
 * false is the kotlinx default).
 */
class SerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun toricBoundaryConditionsAreOmitted() {
        val grain = Grain(id = 0, name = "W", boundaryConditions = BoundaryConditions())
        val obj = json.encodeToJsonElement(Grain.serializer(), grain).jsonObject
        assertNull(obj["boundaryConditions"], "an all-toric grain must not write boundaryConditions")
    }

    @Test
    fun nonToricBoundaryConditionsWriteAllWalls() {
        val grain = Grain(id = 0, name = "W", boundaryConditions = BoundaryConditions(left = false))
        val obj = json.encodeToJsonElement(Grain.serializer(), grain).jsonObject
        val bc = obj["boundaryConditions"]!!.jsonObject
        assertEquals(false, bc["left"]!!.jsonPrimitive.boolean)
        assertEquals(true, bc["right"]!!.jsonPrimitive.boolean, "right must be written explicitly")
        assertEquals(true, bc["top"]!!.jsonPrimitive.boolean, "top must be written explicitly")
        assertEquals(true, bc["bottom"]!!.jsonPrimitive.boolean, "bottom must be written explicitly")

        // round trip
        val loaded = json.decodeFromString(Grain.serializer(), obj.toString())
        assertEquals(BoundaryConditions(left = false), loaded.boundaryConditions)
    }

    @Test
    fun legacyPartialBoundaryConditionsStillLoad() {
        // A file written before the ALWAYS-encode change (only the closed
        // walls present) must still load with the other walls defaulting to true.
        val s = """{"id":0,"name":"W","boundaryConditions":{"left":false}}"""
        val grain = json.decodeFromString(Grain.serializer(), s)
        assertEquals(BoundaryConditions(left = false), grain.boundaryConditions)
    }

    @Test
    fun legacyPartialCountPredicatesStillLoad() {
        // A file written before the ALWAYS-encode change (grainId missing)
        // must still load with the "any agent" default (grainId = -1).
        val s = """{"name":"B1","mainReactiveId":0,"agentCountPredicates":[{"op":"GreaterThan","count":3}]}"""
        val behaviour = json.decodeFromString(Behaviour.serializer(), s)
        assertEquals(
            listOf(AgentCountPredicate(grainId = -1, op = Operator.GreaterThan, count = 3)),
            behaviour.agentCountPredicates
        )
    }

    @Test
    fun countPredicatesWriteAllValues() {
        val behaviour = Behaviour(
            name = "B1", probability = 1.0, mainReactiveId = 0,
            agentCountPredicates = listOf(
                AgentCountPredicate(), // all defaults
                AgentCountPredicate(op = Operator.GreaterThan, count = 3)
            )
        )
        val obj = json.encodeToJsonElement(Behaviour.serializer(), behaviour).jsonObject
        val preds = obj["agentCountPredicates"]!!.jsonArray
        assertEquals(2, preds.size)

        val p0 = preds[0].jsonObject
        assertEquals(-1, p0["grainId"]!!.jsonPrimitive.int, "grainId must be written explicitly")
        assertEquals("GreaterThanOrEquals", p0["op"]!!.jsonPrimitive.content, "default op must be written explicitly")
        assertEquals(0, p0["count"]!!.jsonPrimitive.int, "default count must be written explicitly")

        val p1 = preds[1].jsonObject
        assertEquals(-1, p1["grainId"]!!.jsonPrimitive.int)
        assertEquals("GreaterThan", p1["op"]!!.jsonPrimitive.content)
        assertEquals(3, p1["count"]!!.jsonPrimitive.int)
    }

    @Test
    fun emptyCountPredicatesAreOmitted() {
        val behaviour = Behaviour(name = "B1", probability = 1.0, mainReactiveId = 0)
        val obj = json.encodeToJsonElement(Behaviour.serializer(), behaviour).jsonObject
        assertNull(obj["agentCountPredicates"], "an empty predicate list must be omitted")
    }
}
