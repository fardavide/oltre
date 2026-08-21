package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// What the vein does to the fifth verb. The clamp is the state this mechanic actually lives in —
// `Depleted` is reachable and rare, because a stripped world puts a whole unit back every twenty
// minutes.
class StartRunDepositTest {

    private val t0 = Instant.fromEpochMilliseconds(1_800_000_000_000)
    private val threeHours = 3.hours

    @Test
    fun `a run leaves behind what it did not take`() {
        val state = fleetOf(1)
        val target = neighbourOfHome(state)
        val before = state.galaxy.remaining(target, ResourceKind.METAL, t0)

        val started = dispatch(state, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1))

        val taken = started.runs.single().cargo.metal
        assertTrue(taken > 0, "a three-hour run brings something home")
        assertEquals(before - taken, started.galaxy.remaining(target, ResourceKind.METAL, t0))
    }

    @Test
    fun `the vein is debited at dispatch rather than at arrival`() {
        // The rule every other verb follows one step further — a cost is spent when it is committed,
        // and `FleetRun` fixes its cargo at dispatch for the same reason.
        val state = fleetOf(1)
        val target = neighbourOfHome(state)
        val before = state.galaxy.remaining(target, ResourceKind.METAL, t0)

        val started = dispatch(state, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1))

        assertTrue(
            started.galaxy.remaining(target, ResourceKind.METAL, t0) < before,
            "the hole is there while the run is still in flight",
        )
    }

    @Test
    fun `a fleet that could lift more than the world holds brings the world and no more`() {
        val state = fleetOf(8)
        val target = neighbourOfHome(state)
        val ships = Ships.of(ShipType.SKIFF, 8)
        val vein = state.galaxy.remaining(target, ResourceKind.METAL, t0)

        // given a fleet whose unclamped lift is bigger than the vein
        val world = worldAt(state.galaxy.seed, target)!!
        val danger = FleetBalance.danger(from = state.galaxy.home, world = world)
        val station = FleetBalance.stationFor(
            from = state.galaxy.home,
            to = target,
            window = 24.hours,
            research = state.research,
            ships = ships,
        )
        val unclamped = FleetBalance.cargo(world, ResourceKind.METAL, ships, station, danger, state.research).metal
        assertTrue(unclamped > vein, "the fleet would lift $unclamped from a vein of $vein")

        // when
        val started = dispatch(state, target, ResourceKind.METAL, ships, window = 24.hours)

        // then the run carries the vein, and the world is empty
        assertEquals(vein, started.runs.single().cargo.metal)
        assertEquals(0, started.galaxy.remaining(target, ResourceKind.METAL, t0))
    }

    @Test
    fun `two runs in one check-in take from one vein rather than two`() {
        val state = fleetOf(2)
        val target = neighbourOfHome(state)
        val before = state.galaxy.remaining(target, ResourceKind.METAL, t0)

        val first = dispatch(state, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1))
        val second = dispatch(first, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1))

        val hauls = second.runs.map { it.cargo.metal }
        assertEquals(before - hauls.sum(), second.galaxy.remaining(target, ResourceKind.METAL, t0))
    }

    @Test
    fun `the second run of a check-in is clamped by what the first one left`() {
        // A day's run by the four-hull manifest takes all but a sliver of a full vein — the sizing
        // rule, in a test — so the run behind it is clamped to the sliver rather than to what its
        // hulls could lift. **Four hulls, because issue #68 re-derived the rule against a fleet**:
        // at 5,800 a lone skiff leaves three quarters of the world standing and this would be
        // measuring the fleet rather than the vein.
        val state = fleetOf(5)
        val target = neighbourOfHome(state)

        val first = dispatch(state, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 4), window = 24.hours)
        val leftOver = first.galaxy.remaining(target, ResourceKind.METAL, t0)
        assertTrue(leftOver > 0, "four hulls cannot quite strip a full world in a day")

        // The second run gets a window big enough to over-lift the sliver, so what this measures is
        // the clamp and not the hold. It used to be the 3h rung, and the drive's halved base speed
        // took enough station time out of that rung for a lone skiff to fall *under* the sliver —
        // at which point the test was passing on the hold rather than on the clamp it names.
        val second = dispatch(first, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 1), window = 24.hours)

        assertEquals(leftOver, second.runs.last().cargo.metal)
        assertEquals(0, second.galaxy.remaining(target, ResourceKind.METAL, t0))
    }

    @Test
    fun `a world with nothing left in the asked-for resource refuses`() {
        val state = fleetOf(8)
        val target = neighbourOfHome(state)
        val stripped = dispatch(state, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 8), window = 24.hours)

        val again = startRun(
            state = stripped.copy(ships = Ships.of(ShipType.SKIFF, 1)),
            target = target,
            gathering = ResourceKind.METAL,
            ships = Ships.of(ShipType.SKIFF, 1),
            window = threeHours,
            at = t0,
        )

        assertIs<StartRunResult.Depleted>(again)
    }

    @Test
    fun `a world whose metal is gone still sells the crystal run`() {
        // Design's third row state — "metal empty · crystal full is a world with a full run still in
        // it" — and the reason the two deposits are separate stocks rather than one.
        val state = fleetOf(8)
        val target = neighbourOfHome(state)
        val stripped = dispatch(state, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 8), window = 24.hours)

        val crystal = startRun(
            state = stripped.copy(ships = Ships.of(ShipType.SKIFF, 1)),
            target = target,
            gathering = ResourceKind.CRYSTAL,
            ships = Ships.of(ShipType.SKIFF, 1),
            window = threeHours,
            at = t0,
        )

        assertIs<StartRunResult.Started>(crystal)
        assertTrue(crystal.state.runs.last().cargo.crystal > 0)
    }

    @Test
    fun `a stripped world is worth a run again once it has put something back`() {
        val state = fleetOf(8)
        val target = neighbourOfHome(state)
        val stripped = dispatch(state, target, ResourceKind.METAL, Ships.of(ShipType.SKIFF, 8), window = 24.hours)
        val later = t0 + 1.days

        val again = startRun(
            state = stripped.copy(ships = Ships.of(ShipType.SKIFF, 1)),
            target = target,
            gathering = ResourceKind.METAL,
            ships = Ships.of(ShipType.SKIFF, 1),
            window = threeHours,
            at = later,
        )

        assertIs<StartRunResult.Started>(again)
        assertTrue(again.state.runs.last().cargo.metal > 0)
    }

    @Test
    fun `an unsurveyed world is still refused before its vein is ever consulted`() {
        // Order of checks, unchanged: you cannot price a hold you cannot see, and that answer must
        // not be replaced by one about a deposit nobody has looked at.
        val state = fleetOf(1)
        val far = GalaxyCoordinate(galaxy = state.galaxy.home.galaxy, system = 200, slot = 4)

        val result = startRun(
            state = state,
            target = far,
            gathering = ResourceKind.METAL,
            ships = Ships.of(ShipType.SKIFF, 1),
            window = 24.hours,
            at = t0,
        )

        assertIs<StartRunResult.Unsurveyed>(result)
    }

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

    private fun neighbourOfHome(state: GameState): GalaxyCoordinate =
        state.galaxy.surveyed.filter { it != state.galaxy.home }.minByOrNull { it.slot }
            ?: error("the test seed's home system holds no world but home")
}
