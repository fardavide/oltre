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
    fun `two probes landing at the same instant are ordered by target rather than by dispatch order`() {
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
    fun `a landing is predicted with the count the log will record`() {
        // given the two derivations a notification is written from — what is going to happen, and
        // what did. An alert booked in advance that named a different number from the event it
        // announces would be the game contradicting itself on a lock screen, and the only way that
        // cannot happen is for both to be read off the same state by the same rule.
        val state = rich()
        val to = target(state, systemsAway = 14)
        val dispatched = state.dispatch(to)

        // when
        val predicted = futureEvents(dispatched).filterIsInstance<FutureEvent.SurveyLands>().single()
        val logged = advance(dispatched, from = t0, to = t0 + 3.hours)
            .eventLog.filterIsInstance<Event.SurveyCompleted>().single()

        // then
        assertTrue(logged.worldsFound > 0, "the fixture needs a target that holds worlds")
        assertEquals(logged.worldsFound, predicted.worldsFound)
    }

    @Test
    fun `a landing is predicted with how much of what it charts clears the bar`() {
        // given the honest half of the payload, and the reason a landing needs two numbers rather
        // than one: round 9 measured ~14 dispatches to see one world worth remarking on, so "four
        // worlds charted" on its own is the alert overselling the verb thirteen times out of
        // fourteen.
        //
        // The bar is the worth-it threshold rather than the `Settleable` verdict, and that is
        // load-bearing for an alert booked hours ahead: `Settleable` re-derives against the
        // empire's *current* adaptation levels, so a ladder bought mid-flight would silently make
        // a pending notification wrong. A yield against a fixed threshold cannot go stale.
        val state = rich()
        val to = target(state, systemsAway = 14)
        val dispatched = state.dispatch(to)
        val expected = GalaxyState.occupiedWorldsIn(state.galaxy.seed, to)
            .mapNotNull { at -> worldAt(state.galaxy.seed, at) }
            .count { GalaxyBalance.yieldScore(it.traits).perMillion >= GalaxyBalance.WORTH_IT_THRESHOLD.perMillion }

        // when
        val predicted = futureEvents(dispatched).filterIsInstance<FutureEvent.SurveyLands>().single()

        // then
        assertEquals(expected, predicted.worthTaking)
        assertTrue(predicted.worthTaking <= predicted.worldsFound, "the honest half cannot exceed the whole")
    }

    @Test
    fun `a star with nothing around it cannot be dispatched to at all`() {
        // given the rare system whose fifteen slots are all empty — roughly one in 390. Whether a
        // slot holds a world is *charted*, free and galaxy-wide, so there is genuinely nothing
        // there a probe could learn, and `hasSurveyed` answers vacuously true.
        //
        // Pinned as a refusal rather than left to be discovered by a screen: a player must not be
        // able to spend 150 metal and nine hours on a star to be told what the map already drew.
        // What it costs is that a dispatch action has a sixth state to render, which is `nothing to
        // survey` and emphatically not `already surveyed`.
        val state = rich()

        // then
        assertEquals(
            StartSurveyResult.AlreadySurveyed,
            startSurvey(state, firstWorldlessSystem(state.galaxy.seed), at = t0),
        )
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
