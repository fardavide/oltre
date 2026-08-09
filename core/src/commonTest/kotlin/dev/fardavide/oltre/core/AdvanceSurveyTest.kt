package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class AdvanceSurveyTest {

    private val t0 = Instant.fromEpochMilliseconds(0)

    private fun rich(): GameState =
        GameState.initial().copy(resources = Resources.of(metal = 100_000, crystal = 10_000, deuterium = 10_000))

    // Away from home in whichever direction the map has room for, so a fixture cannot fall off the
    // edge of the coordinate space when the seed's home moves.
    private fun target(state: GameState, systemsAway: Int): SystemAddress {
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

    private fun GameState.dispatch(to: SystemAddress, at: Instant = t0): GameState =
        assertIs<StartSurveyResult.Started>(startSurvey(this, to, at = at)).state

    @Test
    fun `a probe that lands surveys every world of its target`() {
        // given
        val state = rich()
        val to = target(state, systemsAway = 4)
        val dispatched = state.dispatch(to)
        val expected = GalaxyState.occupiedWorldsIn(state.galaxy.seed, to)

        // when
        val landed = advance(dispatched, from = t0, to = t0 + 2.hours)

        // then
        assertTrue(expected.isNotEmpty(), "the fixture needs a target that holds worlds")
        assertTrue(landed.galaxy.surveyed.containsAll(expected))
        assertTrue(landed.surveys.isEmpty(), "a landed probe stops being in flight")
    }

    @Test
    fun `a landing is logged with what it found`() {
        // given
        val state = rich()
        val to = target(state, systemsAway = 6)
        val dispatched = state.dispatch(to)
        val found = GalaxyState.occupiedWorldsIn(state.galaxy.seed, to).size

        // when
        val landed = advance(dispatched, from = t0, to = t0 + 2.hours)

        // then
        val event = landed.eventLog.filterIsInstance<Event.SurveyCompleted>().single()
        assertEquals(to, event.target)
        assertEquals(found, event.worldsFound)
        assertEquals(dispatched.surveys.single().completesAt, event.at)
    }

    @Test
    fun `a probe still in flight has surveyed nothing`() {
        // given
        val state = rich()
        val dispatched = state.dispatch(target(state, systemsAway = 100))

        // when — well short of a 2h10m flight
        val early = advance(dispatched, from = t0, to = t0 + 30.minutes)

        // then
        assertEquals(state.galaxy.surveyed, early.galaxy.surveyed)
        assertEquals(1, early.surveys.size)
    }

    @Test
    fun `surveying is monotone — nothing a probe found is ever lost`() {
        // given two probes to different systems
        val state = rich()
        val first = target(state, systemsAway = 3)
        val dispatched = state.dispatch(first).dispatch(target(state, systemsAway = 90))

        // when
        val afterFirst = advance(dispatched, from = t0, to = t0 + 1.hours)
        val afterBoth = advance(afterFirst, from = t0 + 1.hours, to = t0 + 1.days)

        // then
        assertTrue(afterBoth.galaxy.surveyed.containsAll(afterFirst.galaxy.surveyed))
    }

    @Test
    fun `advance composes across a landing`() {
        // given the property everything downstream depends on, extended to the new event kind
        val base = rich()
        val state = base.dispatch(target(base, systemsAway = 8))
        val end = t0 + 6.hours

        // when
        val whole = advance(state, from = t0, to = end)
        val split = advance(advance(state, from = t0, to = t0 + 1.hours), from = t0 + 1.hours, to = end)

        // then
        assertEquals(whole, split)
    }

    @Test
    fun `two probes landing at the same instant are ordered by target, not by dispatch order`() {
        // given two systems equidistant from home in opposite directions, so their flights are the
        // same length to the millisecond
        val state = rich()
        val home = state.galaxy.home.system
        val lower = SystemAddress(galaxy = state.galaxy.home.galaxy, system = home - 5)
        val higher = SystemAddress(galaxy = state.galaxy.home.galaxy, system = home + 5)

        // when the two are dispatched in opposite orders
        val higherFirst = advance(state.dispatch(higher).dispatch(lower), from = t0, to = t0 + 2.hours)
        val lowerFirst = advance(state.dispatch(lower).dispatch(higher), from = t0, to = t0 + 2.hours)

        // then the log reads the same either way — insertion order must not reach it
        val landings = { s: GameState -> s.eventLog.filterIsInstance<Event.SurveyCompleted>().map { it.target } }
        assertEquals(listOf(lower, higher), landings(higherFirst))
        assertEquals(landings(higherFirst), landings(lowerFirst))
    }

    @Test
    fun `a probe in flight is something the player will be told about`() {
        // given the derivation local notifications are booked from
        val state = rich()
        val to = target(state, systemsAway = 20)
        val dispatched = state.dispatch(to)

        // when
        val pending = futureEvents(dispatched)

        // then
        val landing = pending.filterIsInstance<FutureEvent.SurveyLands>().single()
        assertEquals(to, landing.target)
        assertEquals(dispatched.surveys.single().completesAt, landing.at)
    }

    @Test
    fun `what futureEvents predicts is the order advance applies`() {
        // given several probes due at once, which is the case the secondary tie-break exists for
        val state = rich()
        val home = state.galaxy.home.system
        val dispatched = state
            .dispatch(SystemAddress(galaxy = state.galaxy.home.galaxy, system = home + 9))
            .dispatch(SystemAddress(galaxy = state.galaxy.home.galaxy, system = home - 9))

        // when
        val predicted = futureEvents(dispatched).filterIsInstance<FutureEvent.SurveyLands>().map { it.target }
        val applied = advance(dispatched, from = t0, to = t0 + 3.hours)
            .eventLog.filterIsInstance<Event.SurveyCompleted>().map { it.target }

        // then
        assertEquals(predicted, applied)
    }
}
