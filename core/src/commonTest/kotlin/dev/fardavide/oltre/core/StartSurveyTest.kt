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

    private fun rich(): GameState = GameState.initial().let { state ->
        state.copy(resources = Resources.of(metal = 10_000, crystal = 10_000, deuterium = 10_000))
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
    fun `a new colony can afford its first probe`() {
        // given the starting stock, and nothing else
        val state = GameState.initial()

        // then the second verb exists at hour zero, which is the whole point of leaving it ungated
        assertIs<StartSurveyResult.Started>(startSurvey(state, elsewhere(state, 3), at = t0))
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
    fun `probes run in parallel because resources are the only limiter`() {
        // given
        val state = rich()

        // when
        var next = state
        for (away in 1..5) {
            next = assertIs<StartSurveyResult.Started>(startSurvey(next, elsewhere(next, away), at = t0)).state
        }

        // then — the settled construction rule, applied to the new verb rather than re-litigated
        assertEquals(5, next.surveys.size)
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
        val state = GameState.initial().let { it.copy(resources = Resources.of(metal = SurveyBalance.COST_METAL - 1)) }

        // then
        assertEquals(StartSurveyResult.InsufficientResources, startSurvey(state, elsewhere(state, 4), at = t0))
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
