package dev.fardavide.oltre.client

import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Research
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.TechLevel
import dev.fardavide.oltre.core.Technology
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Moved here with the rail itself at 0.0.12: it stopped being the Colony screen's and became the
// frame's, because Research shows it too.
class ResourceRailUiStateTest {

    @Test
    fun `metal stock is grouped by thousands`() {
        // given
        val state = freshState().copy(resources = Resources.of(metal = 482_910))

        // then
        assertEquals("482,910", state.toResourceRailUiState().metal)
    }

    @Test
    fun `metal rate reflects the mine level`() {
        // given
        val state = freshState().let {
            it.copy(buildings = it.buildings.withLevel(BuildingType.METAL_MINE, BuildingLevel(2)))
        }

        // then
        assertEquals("+112/h", state.toResourceRailUiState().metalRatePerHour)
    }

    @Test
    fun `all three resources appear with stock and rate`() {
        // given
        val state = freshState().copy(
            resources = Resources.of(metal = 1_000, crystal = 2_000, deuterium = 3_000),
        )

        // when
        val uiState = state.toResourceRailUiState()

        // then
        assertEquals("1,000", uiState.metal)
        assertEquals("+90/h", uiState.metalRatePerHour)
        assertEquals("2,000", uiState.crystal)
        assertEquals("+36/h", uiState.crystalRatePerHour)
        assertEquals("3,000", uiState.deuterium)
        assertEquals("+15/h", uiState.deuteriumRatePerHour)
    }

    @Test
    fun `the rate the rail shows is the one research has already raised`() {
        // given the same colony with two levels of Extraction - 90 and 36 per hour times 1_08^2
        val state = freshState().copy(
            research = Research.initial().withLevel(Technology.EXTRACTION, TechLevel(2)),
        )

        // when
        val uiState = state.toResourceRailUiState()

        // then - what the rail says has to be what advance will actually accrue
        assertEquals("+104/h", uiState.metalRatePerHour)
        assertEquals("+41/h", uiState.crystalRatePerHour)
    }

    @Test
    fun `an energy deficit shows in the rate rather than only in the simulation`() {
        // given mines that have outrun the plant
        val state = freshState().let {
            it.copy(buildings = it.buildings.withLevel(BuildingType.METAL_MINE, BuildingLevel(9)))
        }

        // then - 531 per hour scaled by 50 produced over 120 consumed, and marked as throttled
        assertEquals("+221/h", state.toResourceRailUiState().metalRatePerHour)
        assertTrue(state.toResourceRailUiState().throttled)
    }

    // `GameState.initial` takes a galaxy seed rather than defaulting one, so production cannot found
    // every colony in the same galaxy. The rail shows empire-wide stocks and no part of the map.
    private fun freshState(): GameState = GameState.initial(GalaxySeed(20_260_807))
}
