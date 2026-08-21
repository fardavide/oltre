package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

// Every branch of `StartRunResult`, against the real generated galaxy — genesis surveys the home
// system, so a neighbour of home is a legal target from hour zero and no test has to fake a survey.
class StartRunTest {

    private val t0 = Instant.fromEpochMilliseconds(0)

    // A window every home-system target admits: the widest round trip inside one system is 34m, so
    // three hours always leaves far more than `MINIMUM_STATION`.
    private val threeHours = 3.hours

    @Test
    fun `a dispatch books a run and the hulls leave the idle pool`() {
        // given
        val state = fleetOf(3)
        val target = neighbourOfHome(state)

        // when
        val started = dispatch(state, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 2))

        // then
        val run = started.runs.single()
        assertEquals(target, run.target)
        assertEquals(Ships.of(ShipType.SKIFF, 2), run.ships)
        assertEquals(ResourceKind.METAL, run.gathering)
        assertEquals(t0, run.dispatchedAt)
        assertEquals(t0 + threeHours, run.returnsAt)

        // the pool is the *idle* count, so what was sent is no longer in it
        assertEquals(Ships.of(ShipType.SKIFF, 1), started.ships)
    }

    @Test
    fun `sending the last hull leaves an empty pool rather than a zero`() {
        // given a colony that has bought exactly one hull and holds nothing else
        val state = fleetOf(1)

        // when
        val started = dispatch(state, neighbourOfHome(state), ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1))

        // then
        assertTrue(started.ships.isEmpty)
        assertEquals(Ships.NONE, started.ships)
    }

    @Test
    fun `a new colony can send its first skiff at hour zero`() {
        // The verb that exists to fix an empty opening cannot sit behind a building — `startSurvey`'s
        // argument, with more force.
        //
        // **The hull is now bought rather than granted, and the assertion is unchanged on purpose.**
        // What this pins is that *dispatch* has no requirement in front of it, which was true when
        // genesis handed a colony a skiff and has to stay true now that it earns one: a player whose
        // first hull leaves the slipway at hour zero must be able to send it at hour zero. The wait
        // that removing the grant added belongs to the yard and to the price, and `OpeningBalanceTest`
        // is where it is measured — putting it here would turn a test about a gate into a test about
        // a clock.
        val state = fleetOf(1)
        assertIs<StartRunResult.Started>(
            startRun(
                state = state,
                target = neighbourOfHome(state),
                gathering = ResourceKind.METAL,
                ships = Ships.of(ShipType.SKIFF, 1),
                window = threeHours,
                at = t0,
            ),
        )
    }

    @Test
    fun `a dispatch is logged`() {
        // given
        val state = fleetOf(1)
        val target = neighbourOfHome(state)

        // when
        val started = dispatch(state, target, ResourceKind.CRYSTAL, Ships.of(ShipType.SKIFF, 1))

        // then
        assertEquals(
            Event.FleetDispatched(
                target = target,
                gathering = ResourceKind.CRYSTAL,
                ships = Ships.of(ShipType.SKIFF, 1),
                at = t0,
            ),
            started.eventLog.last(),
        )
    }

    @Test
    fun `a run costs nothing to send`() {
        // The hull is the cost and it is bought once — no fuel and no dispatch fee — so the player
        // who was away longest is never the one taxed.
        val state = fleetOf(1)

        val started = dispatch(state, neighbourOfHome(state), ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1))

        assertEquals(state.resources, started.resources)
    }

    @Test
    fun `the cargo is fixed on the run at dispatch`() {
        // given
        val state = fleetOf(1)
        val target = neighbourOfHome(state)
        val world = checkNotNull(worldAt(state.galaxy.seed, target))
        val ships = Ships.of(ShipType.SKIFF, 1)

        // when
        val run = dispatch(state, target, ResourceKind.METAL, ships).runs.single()

        // then — the hold is decided now against the window the player chose now
        assertEquals(
            FleetBalance.cargo(
                world = world,
                gathering = ResourceKind.METAL,
                ships = ships,
                station = FleetBalance.stationFor(
                    from = state.galaxy.home,
                    to = target,
                    window = threeHours,
                    research = state.research,
                    ships = ships,
                ),
                danger = FleetBalance.danger(from = state.galaxy.home, world = world),
                research = Research.initial(),
            ),
            run.cargo,
        )
        assertTrue(run.cargo.metal > 0)
    }

    @Test
    fun `a longer window is committed to a bigger hold at the moment it is chosen`() {
        // given
        val state = fleetOf(2)
        val target = neighbourOfHome(state)

        // when
        val short = dispatch(state, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), window = 3.hours)
        val long = dispatch(state, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), window = 6.hours)

        // then
        assertTrue(long.runs.single().cargo.metal > short.runs.single().cargo.metal)
    }

    @Test
    fun `several runs may target the same world`() {
        // No `distinctBy` rule and deliberately so: one run per target would make the size of your
        // surveyed map the fleet's ceiling and turn every probe into ~4.75 guaranteed dispatch slots.
        // A guard added here later must fail this test rather than pass quietly.
        val state = fleetOf(2)
        val target = neighbourOfHome(state)

        val once = dispatch(state, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1))
        val twice = dispatch(once, target, ResourceKind.CRYSTAL, Ships.of(ShipType.SKIFF, 1), window = 6.hours)

        assertEquals(2, twice.runs.size)
        assertEquals(listOf(target, target), twice.runs.map { it.target })
        assertTrue(twice.ships.isEmpty)
    }

    @Test
    fun `every rung the ladder offers is accepted`() {
        // The screen shows what `windowsFor` returns and the verb must take all of it — a rung the
        // ladder offers and the verb refuses would be a dead control by another route.
        val state = fleetOf(1)
        val target = neighbourOfHome(state)
        val ladder = FleetBalance.windowsFor(state.galaxy.home, target, state.research, Ships.of(ShipType.SKIFF, 1))
        assertEquals(FleetBalance.WINDOWS, ladder, "a target in the home system offers every rung")

        for (rung in ladder) {
            assertIs<StartRunResult.Started>(
                startRun(
                    state = state,
                    target = target,
                    gathering = ResourceKind.METAL,
                    ships = Ships.of(ShipType.SKIFF, 1),
                    window = rung,
                    at = t0,
                ),
                "the ladder offered $rung and the verb refused it",
            )
        }
    }

    // ── NoSuchShips ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `a manifest bigger than the idle pool is refused`() {
        val state = fleetOf(1)
        assertEquals(
            StartRunResult.NoSuchShips,
            startRun(state, neighbourOfHome(state), ResourceKind.METAL, Ships.of(ShipType.SKIFF, 2), threeHours, t0),
        )
    }

    @Test
    fun `a hull the colony does not own is refused`() {
        val state = fleetOf(3)
        assertEquals(
            StartRunResult.NoSuchShips,
            startRun(state, neighbourOfHome(state), ResourceKind.METAL, Ships.of(ShipType.HAULER, 1), threeHours, t0),
        )
    }

    @Test
    fun `an empty manifest is refused`() {
        val state = fleetOf(3)
        assertEquals(
            StartRunResult.NoSuchShips,
            startRun(state, neighbourOfHome(state), ResourceKind.METAL, Ships.NONE, threeHours, t0),
        )
    }

    @Test
    fun `hulls already in flight cannot be sent again`() {
        // The pool is what is checked, so what is out is not available a second time.
        val state = fleetOf(1)
        val target = neighbourOfHome(state)
        val once = dispatch(state, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1))

        assertEquals(
            StartRunResult.NoSuchShips,
            startRun(once, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), threeHours, t0),
        )
    }

    // ── NotAGatheringHull ───────────────────────────────────────────────────────────────────

    @Test
    fun `a scout cannot be sent to gather`() {
        // **The one rule a fifth `ShipType` costs.** `Ships` is a map and every other consumer of it
        // assumes a hull is something you can dispatch on a run — the scout is the first that is not,
        // and this is where that is said. It is refused although the colony owns it, which is why it
        // cannot be `NoSuchShips`: the pool is not the problem, the hull is.
        val state = GameState.initial().copy(ships = Ships.of(ShipType.SCOUT, 1))

        assertEquals(
            StartRunResult.NotAGatheringHull,
            startRun(state, neighbourOfHome(state), ResourceKind.METAL, Ships.of(ShipType.SCOUT, 1), threeHours, t0),
        )
    }

    @Test
    fun `a manifest carrying one scout is refused whole`() {
        // The same shape `buildShips` uses for a hull with no price: a mixed manifest is refused
        // rather than quietly stripped, because a run that left the scout at home would be a fleet
        // the player did not choose.
        val state = GameState.initial().copy(ships = Ships(mapOf(ShipType.SKIFF to 2, ShipType.SCOUT to 1)))

        assertEquals(
            StartRunResult.NotAGatheringHull,
            startRun(
                state,
                neighbourOfHome(state),
                ResourceKind.METAL,
                Ships(mapOf(ShipType.SKIFF to 1, ShipType.SCOUT to 1)),
                threeHours,
                t0,
            ),
        )
    }

    @Test
    fun `owning a scout does not make a skiff run any richer`() {
        // The trap a fifth hull opens: `cargo` sums `ships.total`, so a scout that reached a manifest
        // would be paid as a berth. It cannot reach one — but the pool sitting beside the manifest
        // must not reach it either.
        val alone = fleetOf(1)
        val alongside = alone.copy(ships = alone.ships + Ships.of(ShipType.SCOUT, 3))
        val target = neighbourOfHome(alone)

        val without = dispatch(alone, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1))
        val with = dispatch(alongside, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1))

        assertEquals(without.runs.single().cargo, with.runs.single().cargo)
    }

    // ── Unsurveyed ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `a world nobody has charted cannot be worked`() {
        // You cannot price a hold you cannot see — which is what chains the probe to the ship.
        val state = fleetOf(1)
        val target = unsurveyedWorld(state)
        assertTrue(target !in state.galaxy.surveyed)

        assertEquals(
            StartRunResult.Unsurveyed,
            startRun(state, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), 24.hours, t0),
        )
    }

    @Test
    fun `surveying that world is what makes it a legal target`() {
        // given the same world with the same everything else — only the chart changed
        val state = fleetOf(1)
        val target = unsurveyedWorld(state)
        val charted = state.copy(galaxy = state.galaxy.copy(surveyed = state.galaxy.surveyed + target))

        assertIs<StartRunResult.Started>(
            startRun(charted, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), 24.hours, t0),
        )
    }

    // ── NotAValidTarget ─────────────────────────────────────────────────────────────────────

    @Test
    fun `home is not a target`() {
        val state = fleetOf(1)
        assertEquals(
            StartRunResult.NotAValidTarget,
            startRun(state, state.galaxy.home, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), threeHours, t0),
        )
    }

    @Test
    fun `an empty slot is not a target`() {
        val state = fleetOf(1)
        val empty = emptySlotInHomeSystem(state)
        assertNull(worldAt(state.galaxy.seed, empty))

        assertEquals(
            StartRunResult.NotAValidTarget,
            startRun(state, empty, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), threeHours, t0),
        )
    }

    @Test
    fun `a world somebody else holds is not a target`() {
        val state = fleetOf(1)
        val target = neighbourOfHome(state)
        val contested = state.copy(
            galaxy = state.galaxy.copy(
                ownership = state.galaxy.ownership + WorldOwnership(at = target, holder = EmpireId("rival")),
            ),
        )

        assertEquals(
            StartRunResult.NotAValidTarget,
            startRun(contested, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), threeHours, t0),
        )
    }

    @Test
    fun `deuterium is not something a run may go and fetch`() {
        // The resource that gates the Robotics Factory and therefore the whole research branch. A
        // fleet that could fetch it would undercut the one ladder with a prize of its own.
        val state = fleetOf(1)
        assertEquals(
            StartRunResult.NotAValidTarget,
            startRun(
                state,
                neighbourOfHome(state),
                ResourceKind.DEUTERIUM,
                Ships.of(ShipType.SKIFF, 1),
                threeHours,
                t0,
            ),
        )
    }

    // ── WindowTooShort ──────────────────────────────────────────────────────────────────────

    @Test
    fun `a window that leaves no time on the surface is refused`() {
        val state = fleetOf(1)
        val target = neighbourOfHome(state)

        assertEquals(
            StartRunResult.WindowTooShort,
            startRun(state, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), 21.minutes, t0),
        )
    }

    @Test
    fun `the round trip plus the minimum station is exactly enough`() {
        // given the boundary itself
        val state = fleetOf(1)
        val target = neighbourOfHome(state)
        val exact = FleetBalance.roundTrip(
            state.galaxy.home,
            target,
            state.research,
            Ships.of(ShipType.SKIFF, 1),
        ) + FleetBalance.MINIMUM_STATION

        // then it is inclusive at the boundary and refuses one minute short of it
        assertIs<StartRunResult.Started>(
            startRun(state, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), exact, t0),
        )
        assertEquals(
            StartRunResult.WindowTooShort,
            startRun(state, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), exact - 1.minutes, t0),
        )
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────

    private fun fleetOf(hulls: Int): GameState =
        GameState.initial().let { it.copy(ships = Ships.of(ShipType.SKIFF, hulls)) }

    private fun dispatch(
        state: GameState,
        target: GalaxyCoordinate,
        gathering: ResourceKind,
        ships: Ships,
        window: Duration = threeHours,
    ): GameState = assertIs<StartRunResult.Started>(
        startRun(state = state, target = target, gathering = gathering, ships = ships, window = window, at = t0),
    ).state

    // Genesis surveys the whole home system, so its other worlds are legal targets on turn one.
    // Lowest slot first, which keeps the choice stable for a given seed.
    private fun neighbourOfHome(state: GameState): GalaxyCoordinate =
        state.galaxy.surveyed.filter { it != state.galaxy.home }.minByOrNull { it.slot }
            ?: error("the test seed's home system holds no world but home")

    private fun emptySlotInHomeSystem(state: GameState): GalaxyCoordinate =
        (1..GalaxyBalance.SLOTS_PER_SYSTEM)
            .map { slot ->
                GalaxyCoordinate(
                    galaxy = state.galaxy.home.galaxy,
                    system = state.galaxy.home.system,
                    slot = slot,
                )
            }
            .firstOrNull { worldAt(state.galaxy.seed, it) == null }
            ?: error("the test seed's home system fills all fifteen slots")

    // A real world outside the home system — generated and unsurveyed rather than absent. Scans in
    // coordinate order for the same reason `firstWorld` does.
    private fun unsurveyedWorld(state: GameState): GalaxyCoordinate {
        val home = state.galaxy.home
        for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            if (system == home.system) continue
            for (slot in 1..GalaxyBalance.SLOTS_PER_SYSTEM) {
                val at = GalaxyCoordinate(galaxy = home.galaxy, system = system, slot = slot)
                if (worldAt(state.galaxy.seed, at) != null) return at
            }
        }
        error("the test seed generated no world outside the home system")
    }
}
