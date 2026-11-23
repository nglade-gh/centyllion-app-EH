package com.centyllion.model

import kotlin.test.Test
import kotlin.test.assertEquals

/** The model built by the modifiers tutorial must be the one of Exemple_Tuto_MetaActions.nbd2. */
class TutorialModelTest {

    private fun tutorialModel(): GrainModel {
        val cell = Grain(id = 0, name = "cell", color = "Lime", movementProbability = 0.0)
        val virus = Grain(id = 1, name = "Virus", color = "Crimson", movementProbability = 1.0)
        return GrainModel(
            name = "Modifiers",
            description = "A grain and a behaviour, ready for a modifier",
            grains = listOf(cell, virus),
            behaviours = listOf(
                Behaviour(name = "stay", mainReactiveId = 0, mainProductId = 0, id = 0),
                Behaviour(
                    name = "Kill", mainReactiveId = 0, mainProductId = 1, id = 1,
                    reaction = listOf(Reaction(reactiveId = 1, productId = 1, sourceReactive = 1))
                )
            )
        )
    }

    @Test
    fun testGrains() {
        val m = tutorialModel()
        assertEquals(listOf(0, 1), m.grains.map { it.id })
        assertEquals(listOf("cell", "Virus"), m.grains.map { it.name })
        assertEquals(0.0, m.grains[0].movementProbability)
        assertEquals(1.0, m.grains[1].movementProbability)
    }

    @Test
    fun testBehaviours() {
        val m = tutorialModel()
        assertEquals(listOf(0, 1), m.behaviours.map { it.id })
        assertEquals(listOf("stay", "Kill"), m.behaviours.map { it.name })
        val kill = m.behaviours[1]
        assertEquals(0, kill.mainReactiveId)
        assertEquals(1, kill.mainProductId)
        assertEquals(1, kill.reaction.size)
        assertEquals(1, kill.reaction[0].reactiveId)
        assertEquals(1, kill.reaction[0].productId)
        assertEquals(1, kill.reaction[0].sourceReactive)
    }

    @Test
    fun testModifierOfTheExample() {
        val modifier = SimulationModifier(
            name = "Modificateur",
            formula = "step == 10",
            actions = "AddRect(1, 50, 10, 10, 20, 20), Unuse(0)",
            id = 0
        )
        assertEquals(null, validateModifierActions(modifier.actions), "the actions of the example must be valid")
    }
}
