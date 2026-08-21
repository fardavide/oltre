package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class StartSurveyTest {

    private val t0 = Instant.fromEpochMilliseconds(0)

    // Deep stores **and** a scout to fly, which since 0.15 are two different things: a probe is a
    // hull's errand now, so a colony with a bank and an empty slipway cannot survey at all.
    private fun rich(): GameState = GameState.initial().let { state ->
        state.copy(
            resources = Resources.of(metal = 10_000, crystal = 10_000, deuterium = 10_000),
            ships = Ships.of(ShipType.SCOUT, 4),
        )
    }

    private fun elsewhere(state: GameState, systemsAway: Int): SystemAddress {
        val home = state.galaxy.home
        val up = home.system + systemsAway
        val down = home.system - systemsAway
        val system = when {
            up <= GalaxyBalance.SYSTEMS_PER_GALAXY -> up
            down >= 1 -> down
            // Further than the map is wide in either direction: take the far end, which is the
            // longest flight this seed's home can buy.
            else -> 1
        }
        return SystemAddress(galaxy = home.galaxy, system = system)
    }

    @Test
    fun `a dispatch charges metal and books a landing`() {
        // given
        val state = rich()
        val target = elsewhere(state, systemsAway = 10)

        // when
        val result = startSurvey(state, target, at = t0)

        // then
        val started = assertIs<StartSurveyResult.Started>(result).state
        assertEquals(10_000 - SurveyBalance.COST_METAL, started.resources.metal)
        assertEquals(1, started.surveys.size)
        assertEquals(target, started.surveys.single().target)
        assertEquals(t0 + 40.minutes, started.surveys.single().completesAt)
    }

    @Test
    fun `a dispatch costs no crystal and no deuterium`() {
        // given the resources that gate every other branch: deuterium buys the Robotics Factory,
        // which opens research and the ladders. A probe must not compete with that.
        val state = rich()

        // when
        val started = assertIs<StartSurveyResult.Started>(startSurvey(state, elsewhere(state, 5), at = t0)).state

        // then
        assertEquals(state.resources.crystal, started.resources.crystal)
        assertEquals(state.resources.deuterium, started.resources.deuterium)
    }

    @Test
    fun `a new colony can afford the scout and the probe it flies out of the genesis stock`() {
        // **The property that sizes the scout's price**, and the one #83's ruling put at risk: a
        // colony owns no hulls, so making a probe cost one could have left a fleet-second player with
        // no exploration at all for two days. It does not, and this is the arithmetic that says so —
        // 200 metal for the hull and 150 for the flight, against a genesis stock of 500.
        //
        // The verb is still ungated in the sense that mattered: what stands between a new colony and
        // its first probe is one purchase it can already pay for, not a building and not a wait.
        val state = GameState.initial()
        assertEquals(Ships.NONE, state.ships, "genesis grants no hull, which is what makes this a test")

        // the hull cannot be conjured — it is bought, and then it flies
        assertEquals(StartSurveyResult.NoIdleScout, startSurvey(state, elsewhere(state, 3), at = t0))
        val ordered = assertIs<BuildShipsResult.Started>(
            buildShips(state, Ships.of(ShipType.SCOUT, 1), at = t0),
        ).state
        val delivered = advance(ordered, from = t0, to = t0 + 24.hours)

        assertIs<StartSurveyResult.Started>(startSurvey(delivered, elsewhere(delivered, 3), at = t0 + 24.hours))
    }

    @Test
    fun `distance changes when a probe lands and never what it costs`() {
        // given
        val state = rich()

        // when
        val near = assertIs<StartSurveyResult.Started>(startSurvey(state, elsewhere(state, 1), at = t0)).state
        val far = assertIs<StartSurveyResult.Started>(startSurvey(state, elsewhere(state, 200), at = t0)).state

        // then the price is flat — a far probe is not a worse buy, it is a longer one
        assertEquals(near.resources.metal, far.resources.metal)
        assertTrue(far.surveys.single().completesAt > near.surveys.single().completesAt)
    }

    @Test
    fun `a probe can cover a night away`() {
        // given the gap round 8 measured as uncovered: 8h33m of silence, against a busiest
        // check-in that booked 72 minutes
        val state = rich()
        val acrossTheGalaxy = SystemAddress(
            galaxy = if (state.galaxy.home.galaxy == 1) 2 else 1,
            system = state.galaxy.home.system,
        )

        // when
        val started = assertIs<StartSurveyResult.Started>(startSurvey(state, acrossTheGalaxy, at = t0)).state

        // then
        assertTrue(
            started.surveys.single().completesAt >= t0 + 4.hours,
            "a player who will be away for hours must be able to buy hours of cover",
        )
    }

    @Test
    fun `probes still run in parallel and the pool is what limits them`() {
        // **The rule that changed, and the half of it that did not.** Probes never queued behind one
        // another and still do not — the settled construction rule, applied to this verb rather than
        // re-litigated. What used to make that a defect is that *nothing else* limited them either:
        // metal was flat, so ten dispatched in one check-in all landed together and the tenth cost no
        // wall-clock at all. Now each takes a hull, and the fourth one has none left.
        val state = rich()
        assertEquals(4, state.ships.countOf(ShipType.SCOUT))

        var next = state
        for (away in 1..4) {
            next = assertIs<StartSurveyResult.Started>(startSurvey(next, elsewhere(next, away), at = t0)).state
        }

        assertEquals(4, next.surveys.size, "four scouts should buy four simultaneous probes")
        assertEquals(StartSurveyResult.NoIdleScout, startSurvey(next, elsewhere(next, 5), at = t0))
    }

    @Test
    fun `a second probe to the same system is refused`() {
        // given
        val state = rich()
        val target = elsewhere(state, systemsAway = 7)
        val once = assertIs<StartSurveyResult.Started>(startSurvey(state, target, at = t0)).state

        // then
        assertEquals(StartSurveyResult.AlreadySurveying, startSurvey(once, target, at = t0))
    }

    @Test
    fun `the home system is already known so it cannot be surveyed again`() {
        // given
        val state = rich()

        // then a player can never pay for information they already own
        assertEquals(
            StartSurveyResult.AlreadySurveyed,
            startSurvey(state, SystemAddress.of(state.galaxy.home), at = t0),
        )
    }

    @Test
    fun `a colony that cannot pay is refused`() {
        // given
        val state = rich().copy(resources = Resources.of(metal = SurveyBalance.COST_METAL - 1))

        // then
        assertEquals(StartSurveyResult.InsufficientResources, startSurvey(state, elsewhere(state, 4), at = t0))
    }

    // ── A probe is a hull's errand ──────────────────────────────────────────────────────────

    @Test
    fun `a probe takes a scout out of the idle pool`() {
        // **Davide's call, 2026-08-16**, having played 0.12.2: *"Surveying other systems seems way too
        // easy… Exploring the world must feel rewarding, not just a tap away."* The price was never
        // the thing that made it a tap — ten probes dispatched in one check-in all landed together,
        // so the tenth cost no wall-clock at all. A hull does what a price could not: the pool is
        // finite, and what is out is out.
        val state = rich()

        val started = assertIs<StartSurveyResult.Started>(startSurvey(state, elsewhere(state, 8), at = t0)).state

        assertEquals(3, started.ships.countOf(ShipType.SCOUT))
    }

    @Test
    fun `a colony with no scout cannot survey however deep its stores are`() {
        // The scarcity that was missing, stated as a refusal. A bank is no longer an answer.
        val state = rich().copy(ships = Ships.NONE)

        assertEquals(StartSurveyResult.NoIdleScout, startSurvey(state, elsewhere(state, 4), at = t0))
    }

    @Test
    fun `a skiff cannot survey`() {
        // **Davide's call, 2026-08-16: the scout is the only way.** A skiff that could survey would
        // let the scarcity leak straight back out — a fleet bought for gathering would double as an
        // exploration budget, and the pool would stop being a choice between the two.
        val state = rich().copy(ships = Ships.of(ShipType.SKIFF, 5))

        assertEquals(StartSurveyResult.NoIdleScout, startSurvey(state, elsewhere(state, 4), at = t0))
    }

    @Test
    fun `a scout already out on a probe cannot fly a second one`() {
        val state = rich().copy(ships = Ships.of(ShipType.SCOUT, 1))
        val once = assertIs<StartSurveyResult.Started>(startSurvey(state, elsewhere(state, 6), at = t0)).state

        assertEquals(StartSurveyResult.NoIdleScout, startSurvey(once, elsewhere(once, 9), at = t0))
    }

    @Test
    fun `the hull is taken before the metal so a refused probe costs nothing`() {
        // Order of checks, and it is the order every other verb uses: validity, then requirements,
        // then cost. A colony refused for want of a hull must not have been charged for the flight.
        val state = rich().copy(ships = Ships.NONE)

        assertEquals(StartSurveyResult.NoIdleScout, startSurvey(state, elsewhere(state, 4), at = t0))
    }

    @Test
    fun `a probe leaves the gathering fleet alone`() {
        // The two verbs compete for one pool but not for one hull: a scout going out must not take a
        // skiff with it.
        val state = rich().copy(ships = Ships(mapOf(ShipType.SCOUT to 2, ShipType.SKIFF to 3)))

        val started = assertIs<StartSurveyResult.Started>(startSurvey(state, elsewhere(state, 7), at = t0)).state

        assertEquals(3, started.ships.countOf(ShipType.SKIFF))
        assertEquals(1, started.ships.countOf(ShipType.SCOUT))
    }

    @Test
    fun `a dispatch is logged`() {
        // given
        val state = rich()
        val target = elsewhere(state, systemsAway = 12)

        // when
        val started = assertIs<StartSurveyResult.Started>(startSurvey(state, target, at = t0)).state

        // then
        assertEquals(Event.SurveyStarted(target = target, at = t0), started.eventLog.last())
    }
}
