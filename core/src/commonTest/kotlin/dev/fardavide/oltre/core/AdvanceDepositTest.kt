package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// The one thing `advance` does about deposits, and the property that lets it.
class AdvanceDepositTest {

    private val t0 = Instant.fromEpochMilliseconds(1_800_000_000_000)

    @Test
    fun `a world that has refilled stops being carried in the save`() {
        val stripped = stripped()
        assertEquals(1, stripped.galaxy.deposits.size)

        val later = advance(stripped, from = t0, to = t0 + 21.days)

        assertTrue(later.galaxy.deposits.isEmpty(), "a full world is an absent entry")
    }

    @Test
    fun `a world still short of full is still carried`() {
        val later = advance(stripped(), from = t0, to = t0 + 5.days)

        assertEquals(1, later.galaxy.deposits.size)
    }

    @Test
    fun `the prune does not break the composability property`() {
        // One span and two spans have to produce the same state, and the prune is settled after the
        // span for exactly this reason — being full is monotone, so pruning early cannot change
        // where a later prune lands.
        val stripped = stripped()

        val whole = advance(stripped, from = t0, to = t0 + 30.days)
        val split = advance(advance(stripped, from = t0, to = t0 + 11.days), from = t0 + 11.days, to = t0 + 30.days)

        assertEquals(whole, split)
    }

    @Test
    fun `a run in flight still lands its clamped hold`() {
        // The debit happened at dispatch; the arrival must still credit what the run was carrying.
        val stripped = stripped()
        val run = stripped.runs.single()

        val landed = advance(stripped, from = t0, to = run.returnsAt)

        val returned = landed.eventLog.filterIsInstance<Event.FleetReturned>().single()
        assertEquals(run.cargo, returned.cargo)
        assertEquals(run.cargo.metal, returned.cargo.metal)
        assertTrue(landed.runs.isEmpty())
    }

    private fun stripped(): GameState {
        val state = GameState.initial().let { it.copy(ships = Ships.of(ShipType.SKIFF, 8)) }
        val target = state.galaxy.surveyed.filter { it != state.galaxy.home }.minByOrNull { it.slot }!!
        return assertIs<StartRunResult.Started>(
            startRun(
                state = state,
                target = target,
                gathering = ResourceKind.METAL,
                ships = Ships.of(ShipType.SKIFF, 8),
                window = 24.hours,
                at = t0,
            ),
        ).state
    }
}
