package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class EnergyTest {

    @Test
    fun `production runs at full rate while the solar plant covers consumption`() {
        // given
        val state = GameState.initial()

        // then
        assertTrue(
            PlaceholderBalance.energyProduction(state.buildings) >=
                PlaceholderBalance.energyConsumption(state.buildings),
            "initial solar plant must cover initial mines",
        )
        val start = Instant.fromEpochMilliseconds(0)
        val produced = advance(state, from = start, to = start + 1.hours).resources.metal
        assertEquals(PlaceholderBalance.metalProductionPerHour(state.buildings.metalMine), produced)
    }

    @Test
    fun `production scales down proportionally when energy is short`() {
        // given
        val start = Instant.fromEpochMilliseconds(0)
        val initial = GameState.initial()
        val hungry = initial.copy(
            buildings = initial.buildings.copy(metalMine = BuildingLevel(9)),
        )
        val produced = PlaceholderBalance.energyProduction(hungry.buildings)
        val consumed = PlaceholderBalance.energyConsumption(hungry.buildings)
        assertTrue(produced < consumed, "scenario must actually be energy-starved")

        // when
        val metalAfterHour = advance(hungry, from = start, to = start + 1.hours).resources.metal

        // then
        val fullRate = PlaceholderBalance.metalProductionPerHour(BuildingLevel(9))
        val expected = fullRate * produced / consumed
        assertEquals(expected, metalAfterHour)
        assertTrue(metalAfterHour < fullRate, "starved mine must under-produce")
    }
}
