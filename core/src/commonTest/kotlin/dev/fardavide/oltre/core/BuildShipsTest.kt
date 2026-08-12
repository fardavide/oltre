package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// The sixth verb, and the only one that charges and delivers in the same call. What the branches
// have to pin is therefore not a job's shape but a price's: which hull is on sale, what the next one
// costs, and that a hull in flight is still a hull you own.
class BuildShipsTest {

    private val t0 = Instant.fromEpochMilliseconds(0)

    @Test
    fun `a purchase delivers the hull into the idle pool at once`() {
        // given the granted skiff and enough metal for the second
        val state = wealthy(GameState.initial())

        // when
        val built = build(state, Ships.of(ShipType.SKIFF, 1))

        // then — no yard job anywhere, and the hull is idle now
        assertEquals(Ships.of(ShipType.SKIFF, 2), built.ships)
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
    fun `a purchase is logged`() {
        // `GameSession` detects a discrete transition by the log growing, so a verb that appends
        // nothing writes no save and re-syncs no notification.
        val state = wealthy(GameState.initial())

        val built = build(state, Ships.of(ShipType.SKIFF, 1))

        assertEquals(Event.ShipsBuilt(ships = Ships.of(ShipType.SKIFF, 1), at = t0), built.eventLog.last())
    }

    @Test
    fun `two hulls in one call are charged at two consecutive rungs of the curve`() {
        // The manifest is not a quantity discount and it is not a flat multiple: buying the second
        // and the third together costs what buying them one after the other costs.
        val state = wealthy(GameState.initial())

        val together = build(state, Ships.of(ShipType.SKIFF, 2))
        val separately = build(build(state, Ships.of(ShipType.SKIFF, 1)), Ships.of(ShipType.SKIFF, 1))

        assertEquals(separately.resources, together.resources)
        assertEquals(separately.ships, together.ships)
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
        // Genesis opens on 500 metal, which buys the second skiff at 120 and not the two after it.
        val state = GameState.initial()
        assertTrue(state.resources.covers(FleetBalance.shipCost(ShipType.SKIFF, alreadyOwned = 1)))

        assertEquals(
            BuildShipsResult.InsufficientResources,
            buildShips(state, Ships.of(ShipType.SKIFF, 9), at = t0),
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
