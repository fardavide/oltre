package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// The sixth verb, and the one that charges now and delivers later. What the branches have to pin is
// a price and a queue: which hull is on sale, what the next one costs, that a hull in flight or on
// the slipway is still a hull you have paid for, and that orders serve one after another.
class BuildShipsTest {

    private val t0 = Instant.fromEpochMilliseconds(0)

    @Test
    fun `a purchase lays the hull down in the yard rather than handing it over`() {
        // given the granted skiff and enough metal for the second
        val state = wealthy(GameState.initial())

        // when
        val built = build(state, Ships.of(ShipType.SKIFF, 1))

        // then — the pool is untouched and the yard holds the order
        assertEquals(state.ships, built.ships)
        assertEquals(1, built.yard.size)
        assertEquals(ShipType.SKIFF, built.yard.single().ship)
    }

    @Test
    fun `the wait is the hull's own price taken at the colony's clock`() {
        val state = wealthy(GameState.initial())

        val job = build(state, Ships.of(ShipType.SKIFF, 1)).yard.single()

        assertEquals(t0, job.startedAt)
        assertEquals(
            t0 + FleetBalance.buildDuration(
                ShipType.SKIFF,
                alreadyOwned = 1,
                roboticsFactory = state.buildings.levelOf(BuildingType.ROBOTICS_FACTORY),
            ),
            job.completesAt,
        )
    }

    @Test
    fun `the Robotics Factory the order was placed under is the one it is served at`() {
        // The rule every other job in the game follows: a factory finishing mid-build must not
        // retroactively shorten a build already under way.
        val slow = wealthy(GameState.initial())
        val quick = slow.copy(buildings = slow.buildings.withLevel(BuildingType.ROBOTICS_FACTORY, BuildingLevel(4)))

        val slowJob = build(slow, Ships.of(ShipType.SKIFF, 1)).yard.single()
        val quickJob = build(quick, Ships.of(ShipType.SKIFF, 1)).yard.single()

        assertTrue(
            quickJob.completesAt < slowJob.completesAt,
            "the factory bought nothing: $quickJob against $slowJob",
        )
    }

    @Test
    fun `orders queue behind one another rather than running side by side`() {
        // Davide's call, 2026-08-13: a serial queue. A check-in can spend everything it has, and the
        // yard serves it in the order it was ordered.
        val state = wealthy(GameState.initial())

        val yard = build(state, Ships.of(ShipType.SKIFF, 3)).yard

        assertEquals(3, yard.size)
        assertEquals(t0, yard.first().startedAt)
        for ((earlier, later) in yard.zipWithNext()) {
            assertEquals(earlier.completesAt, later.startedAt, "the yard served two hulls at once")
        }
    }

    @Test
    fun `a second order falls in behind the first rather than starting now`() {
        val state = wealthy(GameState.initial())
        val first = build(state, Ships.of(ShipType.SKIFF, 1))

        val second = build(first, Ships.of(ShipType.SKIFF, 1))

        assertEquals(first.yard.single().completesAt, second.yard.last().startedAt)
    }

    @Test
    fun `each hull in one order is slower than the one before it`() {
        // The wait is taken from the price and the price compounds, so the queue is not three copies
        // of one job — it is the curve, in minutes.
        val state = wealthy(GameState.initial())

        val spans = build(state, Ships.of(ShipType.SKIFF, 3)).yard.map { it.completesAt - it.startedAt }

        assertEquals(spans.sorted(), spans)
        assertTrue(spans.first() < spans.last(), "the queue was flat: $spans")
    }

    @Test
    fun `the second skiff costs what the curve says the second skiff costs`() {
        // given one hull owned
        val state = wealthy(GameState.initial())

        // when
        val built = build(state, Ships.of(ShipType.SKIFF, 1))

        // then
        assertEquals(
            state.resources - FleetBalance.shipCost(ShipType.SKIFF, alreadyOwned = 1),
            built.resources,
        )
    }

    @Test
    fun `an order is logged`() {
        // `GameSession` detects a discrete transition by the log growing, so a verb that appends
        // nothing writes no save and re-syncs no notification. The order and the delivery are two
        // events now, and this is the first of them.
        val state = wealthy(GameState.initial())

        val built = build(state, Ships.of(ShipType.SKIFF, 1))

        assertEquals(Event.ShipsOrdered(ships = Ships.of(ShipType.SKIFF, 1), at = t0), built.eventLog.last())
    }

    @Test
    fun `two hulls in one call are charged at two consecutive rungs of the curve`() {
        // The manifest is not a quantity discount and it is not a flat multiple: buying the second
        // and the third together costs what buying them one after the other costs.
        val state = wealthy(GameState.initial())

        val together = build(state, Ships.of(ShipType.SKIFF, 2))
        val separately = build(build(state, Ships.of(ShipType.SKIFF, 1)), Ships.of(ShipType.SKIFF, 1))

        assertEquals(separately.resources, together.resources)
        assertEquals(separately.yard.map { it.ship }, together.yard.map { it.ship })
    }

    @Test
    fun `a hull still on the slipway counts against the price of the next one`() {
        // Otherwise a queue is a way round the compounding price, which is the whole ceiling: four
        // taps in one check-in would each pay the second rung.
        val state = wealthy(GameState.initial())
        val queued = build(state, Ships.of(ShipType.SKIFF, 1))

        val next = build(queued, Ships.of(ShipType.SKIFF, 1))

        assertEquals(
            queued.resources - FleetBalance.shipCost(ShipType.SKIFF, alreadyOwned = 2),
            next.resources,
        )
    }

    @Test
    fun `a hull already in flight still counts against the price of the next one`() {
        // The pool is the *idle* count, so a fleet that is out would otherwise look like a fleet
        // that was never bought — and the compounding curve is the only ceiling this design has.
        val state = wealthy(GameState.initial())
        val target = state.galaxy.surveyed.first { it != state.galaxy.home }
        val away = assertIs<StartRunResult.Started>(
            startRun(state, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), 3.hours, t0),
        ).state
        assertTrue(away.ships.isEmpty)

        val built = build(away, Ships.of(ShipType.SKIFF, 1))

        assertEquals(
            away.resources - FleetBalance.shipCost(ShipType.SKIFF, alreadyOwned = 1),
            built.resources,
        )
    }

    @Test
    fun `an empty manifest is refused before the yard is touched`() {
        val state = wealthy(GameState.initial())
        val queued = build(state, Ships.of(ShipType.SKIFF, 1))

        assertEquals(BuildShipsResult.NothingToBuild, buildShips(queued, Ships.NONE, at = t0))
    }

    @Test
    fun `nothing else about the colony moves`() {
        // No slot taken, no facility touched, no probe disturbed: the hull competes for metal and
        // for nothing else.
        val state = wealthy(GameState.initial())

        val built = build(state, Ships.of(ShipType.SKIFF, 1))

        assertEquals(state.buildings, built.buildings)
        assertEquals(state.builds, built.builds)
        assertEquals(state.research, built.research)
        assertEquals(state.activeResearch, built.activeResearch)
        assertEquals(state.activeAdaptation, built.activeAdaptation)
        assertEquals(state.surveys, built.surveys)
        assertEquals(state.runs, built.runs)
    }

    // ── InsufficientResources ───────────────────────────────────────────────────────────────

    @Test
    fun `a hull the colony cannot pay for is refused`() {
        val state = GameState.initial().copy(resources = Resources.of())

        assertEquals(
            BuildShipsResult.InsufficientResources,
            buildShips(state, Ships.of(ShipType.SKIFF, 1), at = t0),
        )
    }

    @Test
    fun `a manifest is refused whole rather than part-filled`() {
        val state = wealthy(GameState.initial())

        assertEquals(
            BuildShipsResult.InsufficientResources,
            buildShips(state, Ships.of(ShipType.SKIFF, 40), at = t0),
        )
    }

    // ── NotForSale ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `a hull with no price yet is refused rather than priced at a guess`() {
        // `shipCost` raises for the other three, and a verb reachable from a finger may not throw:
        // the Shipyard draws the Hauler as a dimmed card and the refusal is what that card means.
        val state = wealthy(GameState.initial())

        for (type in listOf(ShipType.HAULER, ShipType.ESCORT, ShipType.SETTLER)) {
            assertEquals(
                BuildShipsResult.NotForSale,
                buildShips(state, Ships.of(type, 1), at = t0),
                "$type has no price and was not refused",
            )
        }
    }

    @Test
    fun `a manifest carrying one unsellable hull is refused whole`() {
        val state = wealthy(GameState.initial())

        assertEquals(
            BuildShipsResult.NotForSale,
            buildShips(
                state,
                Ships(mapOf(ShipType.SKIFF to 1, ShipType.HAULER to 1)),
                at = t0,
            ),
        )
    }

    // ── NothingToBuild ──────────────────────────────────────────────────────────────────────

    @Test
    fun `an empty manifest buys nothing rather than charging nothing`() {
        // A purchase that appended `ShipsBuilt` with an empty manifest would write a save and book a
        // notification sweep for a transition that did not happen.
        val state = wealthy(GameState.initial())

        assertEquals(BuildShipsResult.NothingToBuild, buildShips(state, Ships.NONE, at = t0))
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────

    // Deep enough stocks that the price is never the thing under test. Well inside the store's cap.
    private fun wealthy(state: GameState): GameState =
        state.copy(resources = Resources.of(metal = 100_000, crystal = 100_000, deuterium = 100_000))

    private fun build(state: GameState, ships: Ships): GameState =
        assertIs<BuildShipsResult.Started>(buildShips(state, ships, at = t0)).state
}
