package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class AdvanceArrivalTest {

    @Test
    fun `a returning fleet deposits its cargo on arrival`() {
        // given a run returning one hour in, advanced over two hours
        val t0 = Instant.fromEpochMilliseconds(0)
        val state = GameState.initial().copy(
            runs = listOf(fleetRun(cargo = Resources.of(metal = 500, crystal = 200), returnsAt = t0 + 1.hours)),
        )

        // when
        val result = advance(state, from = t0, to = t0 + 2.hours)

        // then
        assertEquals(emptyList(), result.runs)
        assertEquals(
            state.resources.metal + 2 * PlaceholderBalance.METAL_PRODUCTION_PER_HOUR + 500,
            result.resources.metal,
        )
        assertEquals(
            state.resources.crystal + 2 * PlaceholderBalance.CRYSTAL_PRODUCTION_PER_HOUR + 200,
            result.resources.crystal,
        )
    }

    // The half of an arrival that the old `ReturningFleet` had no way to express: a run carries its
    // own manifest away from the idle pool, so landing has to hand the hulls back as well as the
    // cargo. A version that credited only the cargo would quietly eat the fleet.
    @Test
    fun `a returning fleet hands its hulls back to the idle pool`() {
        // given a colony whose one skiff is still at home and a run of fifteen more hulls inbound
        val t0 = Instant.fromEpochMilliseconds(0)
        val state = GameState.initial().copy(
            runs = listOf(fleetRun(cargo = Resources.of(metal = 500), returnsAt = t0 + 1.hours)),
        )

        // when
        val result = advance(state, from = t0, to = t0 + 2.hours)

        // then
        assertEquals(Ships(mapOf(ShipType.SKIFF to 15, ShipType.HAULER to 1)), result.ships)
    }

    @Test
    fun `the arrival is logged as a fleet-returned event at its instant`() {
        // given
        val t0 = Instant.fromEpochMilliseconds(0)
        val returnsAt = t0 + 1.hours
        val cargo = Resources.of(metal = 500)
        val ships = Ships(mapOf(ShipType.SKIFF to 14, ShipType.HAULER to 1))
        val state = GameState.initial().copy(
            runs = listOf(fleetRun(cargo = cargo, returnsAt = returnsAt, ships = ships)),
        )

        // when
        val result = advance(state, from = t0, to = t0 + 2.hours)

        // then — the entry now says where the hold was filled as well as what came back
        assertEquals(
            listOf(Event.FleetReturned(from = TARGET, ships = ships, cargo = cargo, at = returnsAt)),
            result.eventLog,
        )
    }

    @Test
    fun `a fleet still in flight stays in flight`() {
        // given
        val t0 = Instant.fromEpochMilliseconds(0)
        val inFlight = fleetRun(cargo = Resources.of(metal = 500), returnsAt = t0 + 3.hours)
        val state = GameState.initial().copy(runs = listOf(inFlight))

        // when
        val result = advance(state, from = t0, to = t0 + 2.hours)

        // then — and the hulls it carries are still out with it
        assertEquals(listOf(inFlight), result.runs)
        assertEquals(GameState.initial().ships, result.ships)
        assertEquals(
            state.resources.metal + 2 * PlaceholderBalance.METAL_PRODUCTION_PER_HOUR,
            result.resources.metal,
        )
    }

    @Test
    fun `cargo deposit respects the storage cap`() {
        // given a full metal store and a metal cargo
        val t0 = Instant.fromEpochMilliseconds(0)
        val state = GameState.initial().copy(
            resources = Resources.of(metal = PlaceholderBalance.STORAGE_CAPACITY),
            runs = listOf(fleetRun(cargo = Resources.of(metal = 500), returnsAt = t0 + 1.hours)),
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
            runs = listOf(
                fleetRun(cargo = Resources.of(metal = 500, deuterium = 30), returnsAt = t0 + 2.hours),
            ),
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
            runs = listOf(fleetRun(cargo = Resources.of(metal = 500), returnsAt = t0 + 1.hours)),
        )

        // when
        val result = advance(state, from = t0, to = t0 + 2.hours)

        // then
        assertEquals(BuildingLevel(2), result.buildings.metalMine)
        assertEquals(emptyList(), result.runs)
        assertEquals(
            listOf("BuildStarted", "BuildCompleted", "FleetReturned"),
            result.eventLog.map { it::class.simpleName },
        )
    }
}

// The old `Coordinates(2, 117, 9)` every fixture in this file flew home from, as the bounded
// coordinate that replaced it — a run names where it *went*, not where it is.
private val TARGET = GalaxyCoordinate(galaxy = 2, system = 117, slot = 9)

private fun fleetRun(
    cargo: Resources,
    returnsAt: Instant,
    ships: Ships = Ships(mapOf(ShipType.SKIFF to 14, ShipType.HAULER to 1)),
): FleetRun = FleetRun(
    target = TARGET,
    ships = ships,
    gathering = ResourceKind.METAL,
    cargo = cargo,
    // A run has two ends where the old model stored one. Nothing in `advance` reads this end, but
    // `FleetRun` requires a run to return after it left, so it is an hour before the landing rather
    // than an arbitrary instant.
    dispatchedAt = returnsAt - 1.hours,
    returnsAt = returnsAt,
)
