package com.centyllion.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Round-trip contract of the saved file (.netbiodyn).
 *
 * Everything the editor can produce — in normal mode (age predicate, field
 * thresholds, neighbour counts with their grain, field influences, age
 * inheritance, reactions) or in expert mode (predicate formula, action list) —
 * must survive a save/load cycle unchanged. The serialization is model-based,
 * so the mode used to edit has no influence on what is written.
 *
 * Backwards compatibility: a file written by an older version (without the
 * keys added later: formula, actions, fieldPredicates, agentCountPredicates,
 * boundaryConditions, toric, ...) must still load, with the documented defaults.
 */
class PersistenceTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun fullSimulation(): ModelAndSimulation {
        val grainA = Grain(
            id = 0, name = "A", color = "Red", icon = "circle", invisible = true, size = 1.5,
            description = "agent A", halfLife = 4, movementProbability = 0.25,
            allowedDirection = setOf(Direction.Left, Direction.Right),
            fieldProductions = mapOf(0 to 0.5f),
            fieldInfluences = mapOf(1 to -0.25f),
            fieldPermeable = mapOf(0 to 1f),
            boundaryConditions = BoundaryConditions(left = false, bottom = false)
        )
        val grainB = Grain(id = 1, name = "B", color = "Blue")

        val physicalField = Field(
            id = 0, name = "f0", color = "Lime", invisible = true, description = "sugar",
            speed = 0.3f, halfLife = 7, allowedDirection = setOf(Direction.Up), toric = false
        )
        val formulaField = Field(
            id = 1, name = "f1", color = "SkyBlue",
            formula = "count(0) > 2 ? current * 0.95 : current"
        )

        val full = Behaviour(
            name = "b1", description = "complete behaviour", probability = 0.75,
            agePredicate = Predicate(Operator.GreaterThan, 7),
            agePredicates = listOf(
                Predicate(Operator.LessThanOrEquals, 30),
                Predicate(Operator.NotEquals, 12)
            ),
            formula = "step > 3 && count(1) >= 2 && age(1) > 1",
            actions = "Add(1, 1, 0), SetField(0, 0, 0, 0.5)",
            fieldPredicates = listOf(
                0 to Predicate(Operator.GreaterThan, 0.3f),
                1 to Predicate(Operator.GreaterThanOrEquals, 0f) // all-default predicate: must stay explicit
            ),
            agentCountPredicates = listOf(
                AgentCountPredicate(grainId = 0, op = Operator.GreaterThanOrEquals, count = 2),
                AgentCountPredicate(grainId = -1, op = Operator.LessThan, count = 8)
            ),
            mainReactiveId = 0, mainProductId = 1, sourceReactive = 0,
            fieldInfluences = mapOf(0 to 0.5f),
            reaction = listOf(
                Reaction(
                    reactiveId = 1, productId = 0, sourceReactive = 0,
                    allowedDirection = setOf(Direction.Right)
                )
            )
        )
        val generation = Behaviour(
            name = "gen", probability = 0.05,
            mainReactiveId = -1, mainProductId = 0,
            agentCountPredicates = listOf(AgentCountPredicate(grainId = -1, op = Operator.Equals, count = 0))
        )

        val model = GrainModel(
            name = "full", description = "all features",
            grains = listOf(grainA, grainB),
            behaviours = listOf(full, generation),
            fields = listOf(physicalField, formulaField),
            updatePlan = SEQUENTIAL_UPDATE_PLAN
        )
        val simulation = Simulation(
            name = "sim", description = "test",
            settings = SimulationSettings(size = 42, showGrid = false)
        )
        return ModelAndSimulation(model, simulation)
    }

    @Test
    fun everythingSurvivesARoundTrip() {
        val saved = json.encodeToString(ModelAndSimulation.serializer(), fullSimulation())
        val loaded = json.decodeFromString(ModelAndSimulation.serializer(), saved)

        // nothing silently dropped or added: re-encoding is byte-identical
        assertEquals(saved, json.encodeToString(ModelAndSimulation.serializer(), loaded))

        val model = loaded.model
        assertEquals(SEQUENTIAL_UPDATE_PLAN, model.updatePlan)

        val a = model.grains.first { it.name == "A" }
        assertTrue(a.invisible)
        assertEquals(1.5, a.size)
        assertEquals("agent A", a.description)
        assertEquals(4, a.halfLife)
        assertEquals(0.25, a.movementProbability)
        assertEquals(setOf(Direction.Left, Direction.Right), a.allowedDirection)
        assertEquals(mapOf(0 to 0.5f), a.fieldProductions)
        assertEquals(mapOf(1 to -0.25f), a.fieldInfluences)
        assertEquals(mapOf(0 to 1f), a.fieldPermeable)
        assertEquals(BoundaryConditions(left = false, bottom = false), a.boundaryConditions)

        val f0 = model.fields.first { it.name == "f0" }
        assertFalse(f0.toric, "a non-toric field must keep toric = false")
        assertTrue(f0.invisible)
        assertEquals("sugar", f0.description)
        assertEquals(0.3f, f0.speed)
        assertEquals(7, f0.halfLife)
        assertEquals(setOf(Direction.Up), f0.allowedDirection)
        assertEquals(
            "count(0) > 2 ? current * 0.95 : current",
            model.fields.first { it.name == "f1" }.formula
        )

        val b = model.behaviours.first { it.name == "b1" }
        assertEquals(Predicate(Operator.GreaterThan, 7), b.agePredicate)
        assertEquals(
            listOf(Predicate(Operator.LessThanOrEquals, 30), Predicate(Operator.NotEquals, 12)),
            b.agePredicates,
            "every simulation age line must be kept"
        )
        assertEquals("step > 3 && count(1) >= 2 && age(1) > 1", b.formula)
        assertEquals("Add(1, 1, 0), SetField(0, 0, 0, 0.5)", b.actions)
        assertEquals(0.75, b.probability)
        assertEquals("complete behaviour", b.description)
        assertEquals(
            listOf(0 to Predicate(Operator.GreaterThan, 0.3f), 1 to Predicate(Operator.GreaterThanOrEquals, 0f)),
            b.fieldPredicates
        )
        assertEquals(
            listOf(
                AgentCountPredicate(grainId = 0, op = Operator.GreaterThanOrEquals, count = 2),
                AgentCountPredicate(grainId = -1, op = Operator.LessThan, count = 8)
            ),
            b.agentCountPredicates
        )
        assertEquals(0, b.mainReactiveId)
        assertEquals(1, b.mainProductId)
        assertEquals(0, b.sourceReactive, "sourceReactive = 0 is meaningful and must be kept")
        assertEquals(mapOf(0 to 0.5f), b.fieldInfluences)
        assertEquals(1, b.reaction.size)
        assertEquals(0, b.reaction.first().sourceReactive, "per-reaction age inheritance must be kept")
        assertEquals(setOf(Direction.Right), b.reaction.first().allowedDirection)
        assertEquals(1, b.reaction.first().reactiveId)
        assertEquals(0, b.reaction.first().productId)

        val gen = model.behaviours.first { it.name == "gen" }
        assertEquals(-1, gen.mainReactiveId, "spontaneous generation: main reactive = none")
        assertEquals(listOf(AgentCountPredicate(grainId = -1, op = Operator.Equals, count = 0)), gen.agentCountPredicates)

        assertEquals(42, loaded.simulation.settings.size)
        assertFalse(loaded.simulation.settings.showGrid)
        assertEquals("sim", loaded.simulation.name)
    }

    @Test
    fun legacyFileWithoutRecentKeysStillLoads() {
        // A minimal file written before formula/actions/fieldPredicates/
        // agentCountPredicates/boundaryConditions/toric existed.
        val legacy = """
            {
              "model": {
                "grains": [{"id": 0, "name": "A", "color": "Red", "halfLife": 0}],
                "behaviours": [{
                  "name": "b", "probability": 1,
                  "agePredicate": {"op": "GreaterThanOrEquals", "constant": 0},
                  "mainReactiveId": 0, "mainProductId": 0,
                  "reaction": [{"reactiveId": -1, "productId": 0, "allowedDirection": ["Right"]}]
                }],
                "fields": [{"id": 0, "name": "f", "color": "Blue", "speed": 0.8, "halfLife": 3}]
              },
              "simulation": {"name": "old", "description": "", "width": 50, "height": 50}
            }
        """.trimIndent()

        val loaded = json.decodeFromString(ModelAndSimulation.serializer(), legacy)
        val grain = loaded.model.grains.first()
        assertFalse(grain.invisible)
        assertEquals(BoundaryConditions(), grain.boundaryConditions, "missing boundary conditions means toric everywhere")

        val field = loaded.model.fields.first()
        assertTrue(field.toric, "missing toric means toric")
        assertEquals("", field.formula)
        assertFalse(field.invisible)

        val behaviour = loaded.model.behaviours.first()
        assertEquals("", behaviour.formula)
        assertEquals(emptyList<Predicate<Int>>(), behaviour.agePredicates, "no extra age line in a legacy file")
        assertEquals("", behaviour.actions)
        assertEquals(emptyList(), behaviour.fieldPredicates)
        assertEquals(emptyList(), behaviour.agentCountPredicates)
        assertEquals(-1, behaviour.reaction.first().sourceReactive, "missing per-reaction source means no inheritance")
        assertEquals(PARALLEL_UPDATE_PLAN, loaded.model.updatePlan)

        // and it can be saved again by this version
        val saved = json.encodeToString(ModelAndSimulation.serializer(), loaded)
        assertEquals(
            json.encodeToString(ModelAndSimulation.serializer(), json.decodeFromString(ModelAndSimulation.serializer(), saved)),
            saved
        )
    }
}
