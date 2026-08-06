package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class AdvanceArrivalTest {

    @Test
    fun `a returning fleet deposits its cargo on arrival`() {
        // given a fleet arriving one hour in, advanced over two hours
        val t0 = Instant.fromEpochMilliseconds(0)
        val state = GameState.initial().copy(
            returningFleet = fleet(cargo = Resources.of(metal = 500, crystal = 200), arrivesAt = t0 + 1.hours),
        )

        // when
        val result = advance(state, from = t0, to = t0 + 2.hours)

        // then
        assertEquals(null, result.returningFleet)
        assertEquals(2 * PlaceholderBalance.METAL_PRODUCTION_PER_HOUR + 500, result.resources.metal)
        assertEquals(2 * PlaceholderBalance.CRYSTAL_PRODUCTION_PER_HOUR + 200, result.resources.crystal)
    }

    @Test
    fun `the arrival is logged as a fleet-returned event at its instant`() {
        // given
        val t0 = Instant.fromEpochMilliseconds(0)
        val arrivesAt = t0 + 1.hours
        val cargo = Resources.of(metal = 500)
        val ships = mapOf(ShipType.CARGO to 14, ShipType.CRUISER to 1)
        val state = GameState.initial().copy(
            returningFleet = fleet(cargo = cargo, arrivesAt = arrivesAt, ships = ships),
        )

        // when
        val result = advance(state, from = t0, to = t0 + 2.hours)

        // then
        assertEquals(
            listOf(Event.FleetReturned(ships = ships, cargo = cargo, at = arrivesAt)),
            result.eventLog,
        )
    }

    @Test
    fun `a fleet still in flight stays in flight`() {
        // given
        val t0 = Instant.fromEpochMilliseconds(0)
        val inFlight = fleet(cargo = Resources.of(metal = 500), arrivesAt = t0 + 3.hours)
        val state = GameState.initial().copy(returningFleet = inFlight)

        // when
        val result = advance(state, from = t0, to = t0 + 2.hours)

        // then
        assertEquals(inFlight, result.returningFleet)
        assertEquals(2 * PlaceholderBalance.METAL_PRODUCTION_PER_HOUR, result.resources.metal)
    }

    @Test
    fun `cargo deposit respects the storage cap`() {
        // given a full metal store and a metal cargo
        val t0 = Instant.fromEpochMilliseconds(0)
        val state = GameState.initial().copy(
            resources = Resources.of(metal = PlaceholderBalance.STORAGE_CAPACITY),
            returningFleet = fleet(cargo = Resources.of(metal = 500), arrivesAt = t0 + 1.hours),
        )

        // when
        val result = advance(state, from = t0, to = t0 + 2.hours)

        // then
        assertEquals(PlaceholderBalance.STORAGE_CAPACITY, result.resources.metal)
    }

    @Test
    fun `advancing in one span equals splitting around the arrival`() {
        // given an arrival between every pair of split points
        val t0 = Instant.fromEpochMilliseconds(0)
        val t2 = t0 + 4.hours
        val state = GameState.initial().copy(
            returningFleet = fleet(cargo = Resources.of(metal = 500, deuterium = 30), arrivesAt = t0 + 2.hours),
        )
        val oneShot = advance(state, from = t0, to = t2)

        for (milliseconds in listOf(1L, 3_600_000L, 7_200_000L, 7_200_001L, 10_000_000L)) {
            val t1 = Instant.fromEpochMilliseconds(milliseconds)

            // when
            val stepped = advance(advance(state, from = t0, to = t1), from = t1, to = t2)

            // then
            assertEquals(oneShot, stepped, "split at ${milliseconds}ms diverged")
        }
    }

    @Test
    fun `a build completion and an arrival in the same span apply in event order`() {
        // given a build completing at 20 minutes and a fleet arriving at 1 hour
        val t0 = Instant.fromEpochMilliseconds(0)
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2))
        val funded = GameState.initial().copy(
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal),
        )
        val started = (startUpgrade(funded, BuildingType.METAL_MINE, at = t0) as StartUpgradeResult.Started).state
        val state = started.copy(
            returningFleet = fleet(cargo = Resources.of(metal = 500), arrivesAt = t0 + 1.hours),
        )

        // when
        val result = advance(state, from = t0, to = t0 + 2.hours)

        // then
        assertEquals(BuildingLevel(2), result.buildings.metalMine)
        assertEquals(null, result.returningFleet)
        assertEquals(
            listOf("BuildStarted", "BuildCompleted", "FleetReturned"),
            result.eventLog.map { it::class.simpleName },
        )
    }
}

private fun fleet(
    cargo: Resources,
    arrivesAt: Instant,
    ships: Map<ShipType, Int> = mapOf(ShipType.CARGO to 14, ShipType.CRUISER to 1),
): ReturningFleet = ReturningFleet(
    ships = ships,
    cargo = cargo,
    origin = Coordinates(galaxy = 2, system = 117, position = 9),
    arrivesAt = arrivesAt,
)
