package com.centyllion.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the formula variables available in the behaviour formulas:
 * - `step` : the current simulation step (0 at the first step, incremented at
 *   the end of every step),
 * - `age(n)` : the age of reactive n, with the same 1-based numbering as the
 *   age-inheritance column (1 = main agent, 2 to N+1 = neighbours of the
 *   reactions).
 *
 * Both variables are usable in the predicate formula and in the actions
 * formula. In the default parallel update mode, an agent alive since the
 * start of the simulation (and regenerating itself with sourceReactive = 0)
 * has read age k during the k-th call to [Simulator.oneStep].
 */
class FormulaVariablesTest {

    @Test
    fun testStepIsSimulationStepInPredicate() {
        // A -> B when step == 2, i.e. during the 3rd call to oneStep.
        // With sourceReactive = 0 the agent's read age is k during the k-th
        // call, so under the old step = agent age semantics the conversion
        // would have happened during the 2nd call.
        val model = GrainModel(
            grains = listOf(Grain(id = 0, name = "A", halfLife = 0), Grain(id = 1, name = "B", halfLife = 0)),
            behaviours = listOf(
                Behaviour(name = "A->B at step 2", mainReactiveId = 0, mainProductId = 1,
                    sourceReactive = 0, formula = "step == 2", probability = 1.0)
            )
        )
        val simulation = createSimulation(size = 10) { if (it == 0) 0 else -1 }
        val simulator = Simulator(model, simulation)
        // during calls 1 and 2, step is 0 and 1: no conversion
        simulator.oneStep()
        simulator.oneStep()
        assertEquals(0, simulator.agents[0])
        // during call 3, step is 2: A -> B
        simulator.oneStep()
        assertEquals(1, simulator.agents[0])
    }

    @Test
    fun testAgeOfMainAgentInPredicate() {
        // A -> B when the main agent's age reaches 3 (age(1)).
        val model = GrainModel(
            grains = listOf(Grain(id = 0, name = "A", halfLife = 0), Grain(id = 1, name = "B", halfLife = 0)),
            behaviours = listOf(
                Behaviour(name = "A->B at age 3", mainReactiveId = 0, mainProductId = 1,
                    sourceReactive = 0, formula = "age(1) == 3", probability = 1.0)
            )
        )
        val simulation = createSimulation(size = 10) { if (it == 0) 0 else -1 }
        val simulator = Simulator(model, simulation)
        simulator.oneStep() // read age 1
        simulator.oneStep() // read age 2
        assertEquals(0, simulator.agents[0])
        simulator.oneStep() // read age 3 -> conversion
        assertEquals(1, simulator.agents[0])
        assertEquals(3, simulator.ages[0])
    }

    @Test
    fun testAgeOfNeighbourInPredicate() {
        // A -> B when the age of the reaction neighbour (B) reaches 3 (age(2)).
        // The main agent A resets its age each step (sourceReactive default
        // -1), so its read age stays 1: if age(2) were mistakenly bound to
        // the main agent's age, the behaviour would never apply.
        val model = GrainModel(
            grains = listOf(Grain(id = 0, name = "A", halfLife = 0), Grain(id = 1, name = "B", halfLife = 0)),
            behaviours = listOf(
                Behaviour(name = "A->B when neighbour B is 3", mainReactiveId = 0, mainProductId = 1,
                    formula = "age(2) == 3", probability = 1.0,
                    reaction = listOf(Reaction(reactiveId = 1, productId = 1, sourceReactive = 0)))
            )
        )
        // A at (0,0), B east at (1,0)
        val simulation = createSimulation(size = 10) { if (it == 0) 0 else if (it == 1) 1 else -1 }
        val simulator = Simulator(model, simulation)
        simulator.oneStep() // B read age 1
        simulator.oneStep() // B read age 2
        assertEquals(0, simulator.agents[0])
        simulator.oneStep() // B read age 3 -> conversion
        assertEquals(1, simulator.agents[0])
        assertEquals(1, simulator.agents[1]) // B survives (identity reaction)
    }

    @Test
    fun testAgeInAction() {
        // Add(2, 1, age(2) + 5): the target row depends on the reaction
        // neighbour's age. B is generated at (1,0) during call 2 (step == 1)
        // with age 0; A ages with sourceReactive = 0. The B -> B reaction has
        // sourceReactive = 0, so B inherits the main agent's age each step.
        //  - call 1 (step 0): no B neighbour -> behaviour not applicable
        //  - call 2 (step 1): B generated; not present at step start
        //  - call 3 (step 2): B read age 1, A read age 3 -> C at (1, 6)
        //    (if age(2) were mistakenly the main agent's age, C would be
        //    placed at (1, 8); if it were 0, at (1, 5))
        //  - call 4 (step 3): B read age 4 (A's age 3 + 1) -> C at (1, 9)
        val model = GrainModel(
            grains = listOf(
                Grain(id = 0, name = "A", halfLife = 0),
                Grain(id = 1, name = "B", halfLife = 0),
                Grain(id = 2, name = "C", halfLife = 0)
            ),
            behaviours = listOf(
                Behaviour(name = "generate B at (1,0) on step 1", mainReactiveId = -1, mainProductId = 1,
                    formula = "step == 1 && x == 1 && y == 0", probability = 1.0),
                Behaviour(name = "add C below B", mainReactiveId = 0, mainProductId = 0,
                    sourceReactive = 0, probability = 1.0,
                    reaction = listOf(Reaction(reactiveId = 1, productId = 1, sourceReactive = 0)),
                    actions = "Add(2, 1, age(2) + 5)")
            )
        )
        // A at (0,0)
        val simulation = createSimulation(size = 10) { if (it == 0) 0 else -1 }
        val simulator = Simulator(model, simulation)
        simulator.oneStep() // step 0: nothing
        simulator.oneStep() // step 1: B generated at (1,0)
        assertEquals(1, simulator.agents[1])
        assertEquals(-1, simulator.agents[51]) // no C yet
        simulator.oneStep() // step 2: B read age 1 -> C at (1,6)
        assertEquals(2, simulator.agents[61])
        assertEquals(-1, simulator.agents[51]) // not (1,5) (would mean age 0)
        assertEquals(-1, simulator.agents[81]) // not (1,8) (would mean the main agent's age 3)
        simulator.oneStep() // step 3: B read age 4 -> C at (1,9)
        assertEquals(2, simulator.agents[91])
    }
}
