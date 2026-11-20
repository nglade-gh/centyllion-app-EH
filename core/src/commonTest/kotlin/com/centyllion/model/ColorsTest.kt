package com.centyllion.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** New agents and fields get a vivid, never repeated colour (as long as possible). */
class ColorsTest {

    @Test
    fun testAvailableColorUsesVividPaletteWithoutRepetition() {
        val used = mutableListOf<String>()

        repeat(vividColorNames.size) {
            val color = availableColor(used)
            assertTrue(color in vividColorNames, "'$color' must come from the vivid palette")
            assertTrue(color !in used, "'$color' is already used")
            assertTrue(color in colorNames.keys, "'$color' must be a known colour name")
            used += color
        }
    }

    @Test
    fun testAvailableColorFallsBackWhenPaletteIsExhausted() {
        val used = vividColorNames.toMutableList()
        val color = availableColor(used)
        assertTrue(color in colorNames.keys, "'$color' must be a known colour name")
        assertTrue(color !in used, "'$color' must not be reused")
    }

    @Test
    fun testNewGrainAndFieldGetVividDistinctColors() {
        val model = GrainModel()
        val grain1 = model.newGrain("Grain")
        val grain2 = model.copy(grains = listOf(grain1)).newGrain("Grain")
        val field = model.copy(grains = listOf(grain1, grain2)).newField("Field")

        assertTrue(grain1.color in vividColorNames, "grain colour must be vivid: ${grain1.color}")
        assertTrue(grain2.color in vividColorNames, "grain colour must be vivid: ${grain2.color}")
        assertTrue(field.color in vividColorNames, "field colour must be vivid: ${field.color}")
        assertEquals(3, setOf(grain1.color, grain2.color, field.color).size, "colours must differ")
    }
}
