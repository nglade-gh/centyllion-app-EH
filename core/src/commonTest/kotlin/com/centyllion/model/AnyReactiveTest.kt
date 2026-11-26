package com.centyllion.model

import com.centyllion.i18n.English
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * "Any agent" reactive: a reaction line whose reactive is [ANY_AGENT] accepts any occupied
 * neighbour cell, whatever its grain. The sentinel is available on the reactive side only.
 *
 * The three id spaces are locked here as well: grain ids are >= 0, the count predicates own -1
 * ("all agents") and -2 ("empty cells"), a reaction line uses -1 for an empty neighbour cell, so
 * the wildcard takes -3 and nothing else.
 */
class AnyReactiveTest {

    private val size = 10
    private val centre = 55 // (5, 5) on a 10x10 grid
    private val left = 54
    private val right = 56

    private fun model(reaction: Reaction, mainReactiveId: Int = 0, mainProductId: Int = 0) = GrainModel(
        name = "any",
        grains = listOf(
            Grain(id = 0, name = "A", movementProbability = 0.0),
            Grain(id = 1, name = "B", movementProbability = 0.0),
            Grain(id = 2, name = "C", movementProbability = 0.0),
            Grain(id = 3, name = "D", movementProbability = 0.0),
        ),
        behaviours = listOf(
            Behaviour(
                name = "meet",
                mainReactiveId = mainReactiveId,
                mainProductId = mainProductId,
                reaction = listOf(reaction),
            )
        ),
    )

    private fun messages(behaviour: Behaviour, model: GrainModel): List<String> =
        buildList { behaviour.diagnose(model, English, this) }.map { it.message }

    // ---- the sentinel itself ----

    @Test
    fun theSentinelCannotBeConfusedWithTheOtherNegativeIds() {
        assertEquals(-3, ANY_AGENT, "the wildcard reactive must keep its documented value")
        assertNotEquals(COUNT_ALL_AGENTS, ANY_AGENT, "-1 counts every agent in a count predicate")
        assertNotEquals(COUNT_EMPTY_CELLS, ANY_AGENT, "-2 counts empty cells in a count predicate")
        assertNotEquals(-1, ANY_AGENT, "-1 is an empty neighbour cell in a reaction line")
        assertTrue(ANY_AGENT < 0, "grain ids are >= 0: the wildcard must stay out of that range")
    }

    @Test
    fun onlyAnOccupiedNeighbourSatisfiesTheWildcard() {
        val any = Reaction(reactiveId = ANY_AGENT, productId = 3)
        assertTrue(any.acceptsNeighbour(0))
        assertTrue(any.acceptsNeighbour(7))
        assertTrue(!any.acceptsNeighbour(-1), "an empty cell is not 'any agent'")

        val empty = Reaction(reactiveId = -1, productId = 3)
        assertTrue(empty.acceptsNeighbour(-1))
        assertTrue(!empty.acceptsNeighbour(1))

        val precise = Reaction(reactiveId = 1, productId = 3)
        assertTrue(precise.acceptsNeighbour(1))
        assertTrue(!precise.acceptsNeighbour(2))
        assertTrue(!precise.acceptsNeighbour(-1))
    }

    // ---- validation ----

    @Test
    fun theWildcardIsAcceptedAsAReactive() {
        val model = model(Reaction(reactiveId = ANY_AGENT, productId = 3, sourceReactive = 0))
        val messages = messages(model.behaviours.first(), model)
        assertTrue(messages.isEmpty(), "a wildcard reactive is valid: $messages")
    }

    @Test
    fun theWildcardIsRefusedAsAProduct() {
        val model = model(Reaction(reactiveId = 1, productId = ANY_AGENT, sourceReactive = 0))
        val messages = messages(model.behaviours.first(), model)
        assertTrue(
            messages.any { it.contains("never its product") },
            "a wildcard product must be refused explicitly: $messages"
        )
    }

    @Test
    fun theWildcardIsRefusedAsTheMainReactive() {
        val model = model(Reaction(reactiveId = 1, productId = 1), mainReactiveId = ANY_AGENT, mainProductId = 0)
        val messages = messages(model.behaviours.first(), model)
        assertTrue(
            messages.any { it.contains("never for its main reactive") },
            "any agent is a reaction-line reactive only: $messages"
        )
    }

    @Test
    fun anUnknownNegativeReactiveIdIsRefused() {
        val model = model(Reaction(reactiveId = -2, productId = 3, sourceReactive = 0))
        val messages = messages(model.behaviours.first(), model)
        assertTrue(
            messages.any { it.contains("Invalid id -2") },
            "the -2 count sentinel must not be read as a reactive: $messages"
        )
    }

    // ---- simulation ----

    /** A at the centre, [neighbour] just left of it: the wildcard line must transform it. */
    private fun simulatorWith(neighbour: Int, reaction: Reaction): Simulator {
        val simulation = createSimulation(size = size) {
            when (it) {
                centre -> 0
                left -> neighbour
                else -> -1
            }
        }
        return Simulator(model(reaction), simulation)
    }

    @Test
    fun aWildcardReactiveTransformsAFirstSpecies() {
        val simulator = simulatorWith(1, Reaction(reactiveId = ANY_AGENT, productId = 3, sourceReactive = 0))
        simulator.oneStep()
        assertEquals(0, simulator.agents[centre], "the main reaction still applies")
        assertEquals(3, simulator.agents[left], "the B neighbour is transformed by the wildcard line")
    }

    @Test
    fun aWildcardReactiveTransformsAnotherSpecies() {
        val simulator = simulatorWith(2, Reaction(reactiveId = ANY_AGENT, productId = 3, sourceReactive = 0))
        simulator.oneStep()
        assertEquals(0, simulator.agents[centre], "the main reaction still applies")
        assertEquals(3, simulator.agents[left], "a C neighbour is transformed too: the line is species-blind")
    }

    @Test
    fun aWildcardReactivePicksOneNeighbourWhateverTheSpecies() {
        val simulation = createSimulation(size = size) {
            when (it) {
                centre -> 0
                left -> 1 // B
                right -> 2 // C
                else -> -1
            }
        }
        val simulator = Simulator(
            model(Reaction(reactiveId = ANY_AGENT, productId = 3, sourceReactive = 0)),
            simulation
        )
        simulator.oneStep()
        val transformed = listOf(simulator.agents[left], simulator.agents[right]).count { it == 3 }
        assertEquals(1, transformed, "a reaction line names one neighbour cell, not all of them")
        assertEquals(0, simulator.agents[centre])
    }

    @Test
    fun aPreciseReactiveIgnoresTheOtherSpecies() {
        // non-regression of the sorted pairing: a line naming B never touches a C neighbour
        val withC = simulatorWith(2, Reaction(reactiveId = 1, productId = 3, sourceReactive = 0))
        withC.oneStep()
        assertEquals(2, withC.agents[left], "a B reactive must not transform a C neighbour")
        assertEquals(0, withC.agents[centre], "and the behaviour is not applicable at all")

        val withB = simulatorWith(1, Reaction(reactiveId = 1, productId = 3, sourceReactive = 0))
        withB.oneStep()
        assertEquals(3, withB.agents[left], "a B neighbour is still transformed by a B reactive")
    }

    /**
     * The age of the product comes from the reaction line's `sourceReactive` (0 = main agent,
     * k = neighbour of the k-th line), never from the species reached by the wildcard: the A
     * agent is born at step 4, its neighbour exists from the start, so the two ages differ and
     * the product must inherit the neighbour's one.
     */
    private fun ageInheritance(sourceReactive: Int, reactiveId: Int = ANY_AGENT): Simulator {
        val birth = Behaviour(
            name = "birth",
            mainReactiveId = -1,
            mainProductId = 0,
            formula = "x == 5 && y == 5 && step == 3 && count(1) > 0",
        )
        val meet = Behaviour(
            name = "meet",
            mainReactiveId = 0,
            mainProductId = 0,
            reaction = listOf(Reaction(reactiveId = reactiveId, productId = 3, sourceReactive = sourceReactive)),
        )
        val project = GrainModel(
            grains = listOf(
                Grain(id = 0, name = "A", movementProbability = 0.0),
                Grain(id = 1, name = "B", movementProbability = 0.0),
                Grain(id = 3, name = "D", movementProbability = 0.0),
            ),
            behaviours = listOf(birth, meet),
        )
        val simulation = createSimulation(size = size) {
            when (it) {
                left -> 1
                else -> -1
            }
        }
        return Simulator(project, simulation)
    }

    @Test
    fun theWildcardLineKeepsTheAgeOfTheNeighbourItMatched() {
        val simulator = ageInheritance(sourceReactive = 1)
        repeat(4) { simulator.oneStep() }
        assertEquals(0, simulator.agents[centre], "A was generated at step 4")
        val mainAge = simulator.ages[centre]
        val neighbourAge = simulator.ages[left]
        assertTrue(neighbourAge > mainAge, "the neighbour is older: $neighbourAge vs $mainAge")

        simulator.oneStep()
        assertEquals(3, simulator.agents[left], "the wildcard line transforms the older neighbour")
        assertEquals(
            neighbourAge + 1, simulator.ages[left],
            "the product inherits the age of the matched neighbour (sourceReactive = 1), not the main agent's"
        )
    }

    @Test
    fun theWildcardLineCanStillInheritTheMainAgentAge() {
        val simulator = ageInheritance(sourceReactive = 0)
        repeat(4) { simulator.oneStep() }
        val mainAge = simulator.ages[centre]
        simulator.oneStep()
        assertEquals(3, simulator.agents[left])
        assertEquals(mainAge + 1, simulator.ages[left], "sourceReactive = 0 keeps the main agent's age")
    }

    @Test
    fun aPreciseReactiveLineKeepsTheSameAgeInheritance() {
        // same model, but the line names B: the sorted pairing is used, and the inherited age
        // must be exactly the same one (this is the non-regression of the historical pairer)
        val precise = ageInheritance(sourceReactive = 1, reactiveId = 1)
        val wildcard = ageInheritance(sourceReactive = 1)
        repeat(5) { precise.oneStep(); wildcard.oneStep() }
        assertEquals(3, precise.agents[left], "the B line transforms its neighbour")
        assertEquals(wildcard.agents[left], precise.agents[left], "same agent on both paths")
        assertEquals(wildcard.ages[left], precise.ages[left], "and the same inherited age")
    }

    // ---- persistence ----

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun theWildcardSurvivesASaveAndLoadCycle() {
        val project = ModelAndSimulation(
            model(reaction = Reaction(reactiveId = ANY_AGENT, productId = 3, sourceReactive = 1)),
            createSimulation(size = size)
        )
        val saved = json.encodeToString(ModelAndSimulation.serializer(), project)
        assertTrue("\"reactiveId\":-3" in saved, "the sentinel is written as is: $saved")

        val loaded = json.decodeFromString(ModelAndSimulation.serializer(), saved)
        val reaction = loaded.model.behaviours.first().reaction.first()
        assertEquals(ANY_AGENT, reaction.reactiveId, "the wildcard is read back")
        assertEquals(1, reaction.sourceReactive, "the age source is kept")
        assertEquals(saved, json.encodeToString(ModelAndSimulation.serializer(), loaded), "write is a fixed point")
    }

    @Test
    fun aFileWithoutTheWildcardWritesTheHistoricalShape() {
        val project = ModelAndSimulation(
            model(reaction = Reaction(reactiveId = 1, productId = 3, sourceReactive = 0)),
            createSimulation(size = size)
        )
        val saved = json.encodeToString(ModelAndSimulation.serializer(), project)
        assertTrue("\"reactiveId\":1" in saved, "a concrete reactive is unchanged: $saved")
        assertTrue("-3" !in saved, "nothing extra is written for a model that does not use the wildcard")
    }
}
