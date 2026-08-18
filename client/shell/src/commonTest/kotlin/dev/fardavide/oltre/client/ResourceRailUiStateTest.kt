package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.design.text.English
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
//
// The stocks are numbers rather than strings since the Sky pass, and the rail writes them. A value
// that rolls from one figure to another over 900ms has to be arithmetic on the way — a formatted
// string is the end of that journey and cannot be the middle of it. How a figure is *written* is
// still one decision in one place: `groupedByThousands` in `:client:design:format`, which has its
// own tests. What this file answers for is which figures the rail is given.
class ResourceRailUiStateTest {

    @Test
    fun `the rail carries the metal the colony holds`() {
        // given
        val state = freshState().copy(resources = Resources.of(metal = 482_910))

        // then
        assertEquals(482_910L, state.toResourceRailUiState().metal.stock)
    }

    @Test
    fun `metal rate reflects the mine level`() {
        // given
        val state = freshState().let {
            it.copy(buildings = it.buildings.withLevel(BuildingType.METAL_MINE, BuildingLevel(2)))
        }

        // then
        assertEquals("+112/h", English.resolve(state.toResourceRailUiState().metal.ratePerHour))
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
        assertEquals(1_000L, uiState.metal.stock)
        assertEquals("+90/h", English.resolve(uiState.metal.ratePerHour))
        assertEquals(2_000L, uiState.crystal.stock)
        assertEquals("+36/h", English.resolve(uiState.crystal.ratePerHour))
        assertEquals(3_000L, uiState.deuterium.stock)
        assertEquals("+15/h", English.resolve(uiState.deuterium.ratePerHour))
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
        assertEquals("+104/h", English.resolve(uiState.metal.ratePerHour))
        assertEquals("+41/h", English.resolve(uiState.crystal.ratePerHour))
    }

    @Test
    fun `an energy deficit shows in the rate rather than only in the simulation`() {
        // given mines that have outrun the plant
        val state = freshState().let {
            it.copy(buildings = it.buildings.withLevel(BuildingType.METAL_MINE, BuildingLevel(9)))
        }

        // then - 531 per hour scaled by 50 produced over 120 consumed, and marked as throttled
        assertEquals("+221/h", English.resolve(state.toResourceRailUiState().metal.ratePerHour))
        assertTrue(state.toResourceRailUiState().throttled)
    }

    @Test
    fun `a launch that found offline production rolls from the stock the player last saw`() {
        // given a colony that was closed holding 1_000 metal and has accrued to 1_400
        val state = freshState().copy(resources = Resources.of(metal = 1_400, crystal = 900))
        val lastSeen = Resources.of(metal = 1_000, crystal = 900)

        // when
        val uiState = state.toResourceRailUiState(lastSeen = lastSeen)

        // then the metal cell has somewhere to roll from and the crystal cell has not
        assertEquals(1_000L, uiState.metal.lastSeenStock)
        assertEquals(1_400L, uiState.metal.stock)
        assertEquals(900L, uiState.crystal.lastSeenStock)
        assertEquals(900L, uiState.crystal.stock)
    }

    @Test
    fun `with nothing last seen every cell starts where it already is`() {
        // given a first launch - there is no earlier reading to roll from
        val state = freshState().copy(resources = Resources.of(metal = 1_400))

        // when
        val uiState = state.toResourceRailUiState()

        // then
        assertEquals(1_400L, uiState.metal.lastSeenStock)
        assertEquals(1_400L, uiState.metal.stock)
    }

    // `GameState.initial` takes a galaxy seed rather than defaulting one, so production cannot found
    // every colony in the same galaxy. The rail shows empire-wide stocks and no part of the map.
    private fun freshState(): GameState = GameState.initial(GalaxySeed(20_260_807))
}
