package dev.fardavide.oltre.client.colony.presentation

import dev.fardavide.oltre.core.Buildings
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import kotlin.test.Test
import kotlin.test.assertEquals

class ColonyUiStateTest {

    @Test
    fun `metal stock is formatted with thousands separators`() {
        // given
        val state = GameState(resources = Resources.of(metal = 482_910), buildings = Buildings.initial(), buildQueue = null, eventLog = emptyList())

        // when
        val uiState = state.toColonyUiState()

        // then
        assertEquals("482,910", uiState.metal)
    }

    @Test
    fun `metal production rate is formatted as an hourly delta`() {
        // given
        val state = GameState(resources = Resources.of(metal = 0), buildings = Buildings.initial(), buildQueue = null, eventLog = emptyList())

        // when
        val uiState = state.toColonyUiState()

        // then
        assertEquals("+3,600/h", uiState.metalRatePerHour)
    }
}
