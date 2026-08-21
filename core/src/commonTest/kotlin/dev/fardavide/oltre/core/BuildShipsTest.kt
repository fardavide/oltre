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
        // given an empty pool and enough metal for the first hull
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
    fun `every hull in one order takes the same time as the one before it`() {
        // The wait is taken from the price, and since the price went flat so is the wait: three hulls
        // are three copies of one job, laid end to end. **The queue is the only thing that grows now**
        // — what a fourth hull costs a player is the three ahead of it, not a rung.
        val state = wealthy(GameState.initial())

        val spans = build(state, Ships.of(ShipType.SKIFF, 3)).yard.map { it.completesAt - it.startedAt }

        assertEquals(1, spans.distinct().size, "the queue was not flat: $spans")
    }

    @Test
    fun `a skiff costs the base whatever the fleet already is`() {
        // **Davide's call, 2026-08-14: flat.** The state here already owns a skiff — bought, since
        // genesis grants none — and the charge is the published base rather than a second rung. The
        // hull is put there explicitly rather than inherited from `initial`, which is what the test
        // was doing until the grant went: a premise a fixture happens to supply is a premise that
        // disappears silently.
        val state = wealthy(GameState.initial()).copy(ships = Ships.of(ShipType.SKIFF, 1))
        assertEquals(1, state.ownedShips().total)

        val built = build(state, Ships.of(ShipType.SKIFF, 1))

        assertEquals(state.resources - FleetBalance.shipCost(ShipType.SKIFF), built.resources)
    }

    @Test
    fun `the price a screen can quote is the price the verb charges`() {
        // **The seam this exists to close.** The Shipyard used to assemble the price itself — read the
        // fleet, add the slipway, call `FleetBalance.shipCost` with the total — which was a second
        // implementation of a rule this file already had, kept in agreement by a comment. So a change
        // to the rule was a change to the screen, and 0.10.1 paid that bill across four files.
        //
        // `priceOf` is what a caller asks instead. It takes the state rather than an ingredient of the
        // curve, so a price that starts reading the fleet, the research or the buildings again moves
        // this function and nothing else.
        val state = wealthy(GameState.initial())
        val manifest = Ships.of(ShipType.SKIFF, 3)

        val quoted = state.priceOf(manifest)
        val built = build(state, manifest)

        assertEquals(state.resources - quoted, built.resources)
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
    fun `two hulls in one call cost twice one hull`() {
        // The manifest is a flat multiple now, and the property that mattered when it was not still
        // holds and is still the one worth pinning: buying two together costs exactly what buying
        // them one after the other costs. It is what stops the price and the queue disagreeing.
        val state = wealthy(GameState.initial())

        val together = build(state, Ships.of(ShipType.SKIFF, 2))
        val separately = build(build(state, Ships.of(ShipType.SKIFF, 1)), Ships.of(ShipType.SKIFF, 1))

        assertEquals(separately.resources, together.resources)
        val one = FleetBalance.shipCost(ShipType.SKIFF)
        assertEquals(state.resources - one - one, together.resources)
        assertEquals(separately.yard.map { it.ship }, together.yard.map { it.ship })
    }

    @Test
    fun `a hull on the slipway does not change what the next one costs`() {
        // The mirror of what this test asserted until 0.10.1, when a queue *had* to count against the
        // price or it would have been a way round the compounding curve. With a flat price there is
        // no rung to skip, so what the slipway costs the next hull is the wait, not the money.
        val state = wealthy(GameState.initial())
        val queued = build(state, Ships.of(ShipType.SKIFF, 1))

        val next = build(queued, Ships.of(ShipType.SKIFF, 1))

        assertEquals(queued.resources - FleetBalance.shipCost(ShipType.SKIFF), next.resources)
    }

    @Test
    fun `a hull already in flight does not change what the next one costs`() {
        // The pool is the *idle* count, so a fleet that is out looks like no fleet at all from
        // `state.ships` — which used to be a price bug waiting to happen and is now simply not an
        // input. Kept as a test because the day a hull is priced against the fleet again, this is the
        // case that will be got wrong.
        val state = wealthy(GameState.initial()).copy(ships = Ships.of(ShipType.SKIFF, 1))
        val target = state.galaxy.surveyed.first { it != state.galaxy.home }
        val away = assertIs<StartRunResult.Started>(
            startRun(state, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), 3.hours, t0),
        ).state
        assertTrue(away.ships.isEmpty)

        val built = build(away, Ships.of(ShipType.SKIFF, 1))

        assertEquals(away.resources - FleetBalance.shipCost(ShipType.SKIFF), built.resources)
    }

    @Test
    fun `an order placed at an instant the queue has already passed still falls in behind it`() {
        // The defensive branch in `yardFreesAt`, and the same defence `advance` applies at its own
        // boundary: a caller resuming with a stale span can hand this verb an instant the queue has
        // already gone past. Chaining from the tail rather than from `at` is what keeps
        // `GameState.init`'s serial rule true — starting at the stale instant would produce two jobs
        // running at once and the state would refuse to construct.
        val state = wealthy(GameState.initial())
        val queued = build(state, Ships.of(ShipType.SKIFF, 1))
        val tail = queued.yard.single().completesAt

        val stale = assertIs<BuildShipsResult.Started>(
            buildShips(queued, Ships.of(ShipType.SKIFF, 1), at = t0 - 1.hours),
        ).state

        assertEquals(tail, stale.yard.last().startedAt)
    }

    @Test
    fun `a queue whose last hull is already due starts the next one now rather than in the past`() {
        // The other side of the same `maxOf`, and the reason it is a `maxOf` rather than the tail
        // outright: a state that has not been advanced yet can hold a job whose completion has
        // passed, and chaining onto it would lay a hull down before the moment the player tapped.
        val state = wealthy(GameState.initial())
        val queued = build(state, Ships.of(ShipType.SKIFF, 1))
        val longAfter = queued.yard.single().completesAt + 1.hours

        val next = assertIs<BuildShipsResult.Started>(
            buildShips(queued, Ships.of(ShipType.SKIFF, 1), at = longAfter),
        ).state

        assertEquals(longAfter, next.yard.last().startedAt)
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
        // 200 hulls at a flat 800 metal is 160,000 against a stock of 100,000. It took 40 to break
        // the bank on the compounding curve and it takes two hundred now, which is the trade in one
        // number: **a check-in can buy as many hulls as it can pay for, and the price no longer
        // climbs to stop it.**
        val state = wealthy(GameState.initial())

        assertEquals(
            BuildShipsResult.InsufficientResources,
            buildShips(state, Ships.of(ShipType.SKIFF, 200), at = t0),
        )
    }

    // ── NotForSale ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `a hull with no price yet is refused rather than priced at a guess`() {
        // `shipCost` raises for the two that have no slice, and a verb reachable from a finger may
        // not throw: the Shipyard draws them as dimmed cards and the refusal is what that card means.
        //
        // **The hauler left this list at 0.15.0**, with the manifest picker — which is what it was
        // waiting for. Until the dispatch sheet could send a two-hull manifest, a purchasable hauler
        // was a hull a player could own and never use.
        val state = wealthy(GameState.initial())

        for (type in listOf(ShipType.ESCORT, ShipType.SETTLER)) {
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
                Ships(mapOf(ShipType.SKIFF to 1, ShipType.ESCORT to 1)),
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
