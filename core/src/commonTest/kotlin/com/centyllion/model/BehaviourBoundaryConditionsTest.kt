package com.centyllion.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Boundary conditions of a behaviour restrict the neighbourhood it can see.
 *
 * A behaviour whose boundary has a closed wall must not see the agents nor the field values
 * lying beyond that wall: they are treated as absent (no wrap-around, no read). The all-toric
 * default (the shape of every file written before this feature) must keep the exact previous
 * trajectory and cost, which is what the golden arrays below guard.
 */
class BehaviourBoundaryConditionsTest {

    private fun model(boundary: BoundaryConditions = BoundaryConditions()) = GrainModel(
        name = "optimization",
        grains = listOf(
            Grain(id = 0, name = "a", color = "Lime", movementProbability = 0.2),
        ),
        behaviours = listOf(
            Behaviour(
                name = "birth",
                mainReactiveId = -1,
                mainProductId = 0,
                agentCountPredicates = listOf(
                    AgentCountPredicate(grainId = COUNT_ALL_AGENTS, op = Operator.Equals, count = 3)
                ),
                boundaryConditions = boundary,
            ),
        ),
    )

    private fun simulation() = createSimulation(size = 12) { if (it % 7 == 0) 0 else -1 }.copy(seed = 7)

    /** Agents after 15 steps, recorded with the all-toric engine before behaviours had walls. */
    private val goldenAgents = intArrayOf(
        0, 0, -1, 0, -1, 0, 0, -1, 0, 0, 0, -1,
        -1, 0, 0, 0, -1, 0, 0, -1, 0, -1, 0, 0,
        0, 0, -1, 0, -1, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, -1, -1, -1, 0, 0, -1,
        0, 0, 0, 0, -1, 0, 0, 0, 0, -1, 0, -1,
        0, 0, 0, -1, 0, -1, 0, 0, 0, 0, -1, 0,
        -1, -1, 0, -1, 0, 0, 0, 0, 0, -1, 0, 0,
        0, 0, -1, -1, 0, 0, -1, -1, -1, -1, 0, 0,
        0, -1, 0, 0, 0, 0, 0, 0, -1, 0, 0, 0,
        -1, 0, 0, 0, 0, -1, 0, 0, 0, -1, 0, -1,
        0, -1, 0, 0, 0, -1, -1, 0, 0, -1, 0, 0,
        0, 0, -1, 0, 0, -1, 0, 0, -1, 0, 0, 0,
    )

    /** Ages after 15 steps, recorded at the same time as [goldenAgents]. */
    private val goldenAges = intArrayOf(
        15, 15, 0, 9, 0, 9, 12, 0, 13, 13, 12, 0,
        0, 13, 15, 10, 0, 10, 14, 0, 12, 0, 12, 13,
        15, 12, 0, 10, 0, 15, 15, 4, 14, 4, 12, 11,
        12, 11, 11, 9, 9, 15, 0, 0, 0, 15, 15, 0,
        9, 9, 10, 8, 0, 11, 11, 4, 11, 0, 10, 0,
        10, 10, 9, 0, 15, 0, 15, 9, 15, 8, 0, 15,
        0, 0, 11, 0, 10, 9, 15, 10, 15, 0, 9, 9,
        15, 12, 0, 0, 9, 10, 0, 0, 0, 0, 9, 9,
        12, 0, 13, 11, 10, 15, 11, 11, 0, 8, 11, 10,
        0, 12, 15, 10, 10, 0, 12, 13, 15, 0, 15, 0,
        12, 0, 14, 10, 11, 0, 0, 15, 12, 0, 11, 11,
        12, 13, 0, 11, 10, 0, 12, 12, 0, 12, 11, 11,
    )

    @Test
    fun allToricBehavioursKeepTheExactTrajectory() {
        val simulator = Simulator(model(), simulation())
        repeat(15) { simulator.oneStep() }
        assertFalse(simulator.model.behaviours.first().hasClosedBoundary)
        assertEquals(goldenAgents.toList(), simulator.agents.toList(), "same seed, same agents as before the feature")
        assertEquals(goldenAges.toList(), simulator.ages.toList(), "same seed, same ages as before the feature")
    }

    @Test
    fun anExplicitAllToricBoundaryIsTheDefaultOne() {
        val explicit = Simulator(model(BoundaryConditions(true, true, true, true)), simulation())
        repeat(15) { explicit.oneStep() }
        assertEquals(goldenAgents.toList(), explicit.agents.toList(), "writing the four toric walls changes nothing")
        assertEquals(goldenAges.toList(), explicit.ages.toList(), "writing the four toric walls changes nothing")
    }

    // ---- the walls of a behaviour's visible neighbourhood ----

    private fun generationModel(boundary: BoundaryConditions) = GrainModel(
        grains = listOf(Grain(id = 0, name = "A", color = "Lime", movementProbability = 0.0)),
        behaviours = listOf(
            Behaviour(
                name = "birth",
                mainReactiveId = -1,
                mainProductId = 0,
                agentCountPredicates = listOf(
                    // exactly one occupied neighbour: the cell (0, 5) only matches when the
                    // agent stored in the last column is reached through the left wall
                    AgentCountPredicate(grainId = COUNT_ALL_AGENTS, op = Operator.Equals, count = 1)
                ),
                boundaryConditions = boundary,
            ),
        ),
    )

    /** A 10x10 grid holding a single agent of grain 0 at (9, 5). */
    private fun singleAgentAtRightEdge() = createSimulation(size = 10) { if (it == 59) 0 else -1 }

    private val leftEdgeOfRow5 = 5 * 10 + 0
    private val insideNeighbourOfRow5 = 5 * 10 + 8

    @Test
    fun aClosedLeftWallHidesTheNeighbourAcrossIt() {
        val toric = Simulator(generationModel(BoundaryConditions()), singleAgentAtRightEdge())
        toric.oneStep()
        assertEquals(
            0, toric.agents[leftEdgeOfRow5],
            "a toric behaviour sees the agent wrapped from the last column"
        )

        val closed = Simulator(generationModel(BoundaryConditions(left = false)), singleAgentAtRightEdge())
        closed.oneStep()
        assertEquals(
            -1, closed.agents[leftEdgeOfRow5],
            "the left wall is closed: no agent beyond it, so no generation at the left edge"
        )
        assertEquals(
            0, closed.agents[insideNeighbourOfRow5],
            "the behaviour still applies where the neighbour is inside the grid"
        )
        assertEquals(0, toric.agents[insideNeighbourOfRow5], "and the toric behaviour too")
    }

    private fun reactionModel(boundary: BoundaryConditions) = GrainModel(
        grains = listOf(
            Grain(id = 0, name = "A", movementProbability = 0.0),
            Grain(id = 1, name = "B", movementProbability = 0.0),
            Grain(id = 2, name = "C", movementProbability = 0.0),
        ),
        behaviours = listOf(
            Behaviour(
                name = "meet",
                mainReactiveId = 0,
                mainProductId = 0,
                // the B neighbour on the left becomes C
                reaction = listOf(
                    Reaction(reactiveId = 1, productId = 2, sourceReactive = 0, allowedDirection = setOf(Direction.Left))
                ),
                boundaryConditions = boundary,
            ),
        ),
    )

    /** A at (0, 5) and B at (9, 5), i.e. its left neighbour only through the toric wall. */
    private fun agentAtLeftEdgeAndRightEdge() = createSimulation(size = 10) {
        when (it) {
            50 -> 0
            59 -> 1
            else -> -1
        }
    }

    @Test
    fun aReactiveBeyondAClosedWallIsNotVisible() {
        val toric = Simulator(reactionModel(BoundaryConditions()), agentAtLeftEdgeAndRightEdge())
        toric.oneStep()
        assertEquals(0, toric.agents[50], "the main reaction still applies")
        assertEquals(2, toric.agents[59], "the wrapped B on the left is a visible reactive")

        val closed = Simulator(reactionModel(BoundaryConditions(left = false)), agentAtLeftEdgeAndRightEdge())
        closed.oneStep()
        assertEquals(0, closed.agents[50], "the main reaction still applies")
        assertEquals(1, closed.agents[59], "the closed left wall hides the reactive: its reaction never applies")
    }

    // ---- persistence of the new key: same JSON shape as the grains ----

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun project(boundary: BoundaryConditions) = ModelAndSimulation(
        GrainModel(
            name = "walls",
            grains = listOf(Grain(id = 0, name = "A")),
            behaviours = listOf(
                Behaviour(name = "toric", mainReactiveId = 0, mainProductId = 0),
                Behaviour(name = "closed", mainReactiveId = 0, mainProductId = 0, boundaryConditions = boundary),
            ),
        ),
        createSimulation(size = 10)
    )

    @Test
    fun anAllToricBehaviourDoesNotWriteTheKey() {
        val text = json.encodeToString(ModelAndSimulation.serializer(), project(BoundaryConditions()))
        val behaviours = Json.parseToJsonElement(text).jsonObject["model"]!!.jsonObject["behaviours"]!!.jsonArray
        assertEquals(2, behaviours.size)
        behaviours.forEach {
            assertNull(it.jsonObject["boundaryConditions"], "a toric behaviour must not write boundaryConditions")
        }
    }

    @Test
    fun aClosedWallSurvivesARoundTripWithAllFourWalls() {
        val closed = BoundaryConditions(left = false, right = true, top = true, bottom = false)
        val text = json.encodeToString(ModelAndSimulation.serializer(), project(closed))
        val behaviourNode = Json.parseToJsonElement(text).jsonObject["model"]!!.jsonObject["behaviours"]!!
            .jsonArray[1].jsonObject
        val walls = assertNotNull(behaviourNode["boundaryConditions"]?.jsonObject, "all four walls must be written")
        assertEquals(setOf("left", "right", "top", "bottom"), walls.keys)
        assertFalse(walls.getValue("left").jsonPrimitive.boolean)
        assertTrue(walls.getValue("right").jsonPrimitive.boolean)
        assertTrue(walls.getValue("top").jsonPrimitive.boolean)
        assertFalse(walls.getValue("bottom").jsonPrimitive.boolean)

        val loaded = json.decodeFromString(ModelAndSimulation.serializer(), text)
        assertEquals(closed, loaded.model.behaviours[1].boundaryConditions, "the four walls must be read back")
        assertEquals(
            BoundaryConditions(), loaded.model.behaviours[0].boundaryConditions,
            "a behaviour without the key is toric"
        )
        assertEquals(text, json.encodeToString(ModelAndSimulation.serializer(), loaded), "re-encoding is identical")
    }

    @Test
    fun aBehaviourWithoutTheKeyIsToricEverywhere() {
        val legacy = """
            {
              "model": {
                "grains": [{"id": 0, "name": "A", "color": "Red"}],
                "behaviours": [{
                  "name": "b", "probability": 1,
                  "agePredicate": {"op": "GreaterThanOrEquals", "constant": 0},
                  "mainReactiveId": 0, "mainProductId": 0
                }]
              },
              "simulation": {"name": "old", "description": "", "width": 20, "height": 20}
            }
        """.trimIndent()
        val loaded = json.decodeFromString(ModelAndSimulation.serializer(), legacy)
        val behaviour = loaded.model.behaviours.first()
        assertEquals(BoundaryConditions(), behaviour.boundaryConditions, "missing boundary conditions means toric")
        assertFalse(behaviour.hasClosedBoundary)
    }
}
