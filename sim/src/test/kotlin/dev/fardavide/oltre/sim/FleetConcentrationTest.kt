package dev.fardavide.oltre.sim

import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.GalaxySeed
import kotlin.test.Test
import kotlin.test.assertTrue

class FleetConcentrationTest {
    @Test
    fun `grouping dispatches several hulls when a planet can feed them`() {
        val state = concentrationRun(GalaxySeed(20_260_807), days = 7, grouped = true)
        val dispatches = state.eventLog.filterIsInstance<Event.FleetDispatched>()
        assertTrue(dispatches.any { it.ships.total > 1 })
    }
}
