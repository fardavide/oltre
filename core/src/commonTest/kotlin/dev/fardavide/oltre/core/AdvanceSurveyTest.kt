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

    private fun rich(): GameState = GameState.initial().copy(
        resources = Resources.of(metal = 100_000, crystal = 10_000, deuterium = 10_000),
        ships = Ships.of(ShipType.SCOUT, 4),
    )

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
    fun `the scout comes home when its probe lands`() {
        // **A scout is spent for the flight, not consumed by it** — the same shape a gathering run
        // has, where the pool is the *idle* count and an arrival puts the hull back. What surveying
        // costs is therefore a hull's absence rather than a hull, so the scarcity is the wall clock
        // and not attrition: buy one and you may survey forever, one system at a time.
        val state = rich()
        val dispatched = state.dispatch(target(state, systemsAway = 4))
        assertEquals(3, dispatched.ships.countOf(ShipType.SCOUT))

        val landed = advance(dispatched, from = t0, to = t0 + 2.hours)

        assertEquals(4, landed.ships.countOf(ShipType.SCOUT))
    }

    @Test
    fun `a probe still in flight has not given its scout back`() {
        val state = rich()
        val dispatched = state.dispatch(target(state, systemsAway = 40))

        val partway = advance(dispatched, from = t0, to = t0 + 5.minutes)

        assertEquals(1, partway.surveys.size, "the fixture needs a probe still out at five minutes")
        assertEquals(3, partway.ships.countOf(ShipType.SCOUT))
    }

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
        val pending = futureEvents(dispatched, now = t0)

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
        val predicted = futureEvents(dispatched, now = t0).filterIsInstance<FutureEvent.SurveyLands>().single()
        val logged = advance(dispatched, from = t0, to = t0 + 3.hours)
            .eventLog.filterIsInstance<Event.SurveyCompleted>().single()

        // then
        assertTrue(logged.worldsFound > 0, "the fixture needs a target that holds worlds")
        assertEquals(logged.worldsFound, predicted.worldsFound)
    }

    @Test
    fun `a landing predicts the same verdict the screen will draw`() {
        // given the honest half of the payload, and the number an alert is allowed to say.
        //
        // **It counts what the Galaxy screen will call `Settleable`**, and the first version did
        // not: it counted the yield bar alone, on the theory that a threshold fixed against a
        // constant could not go stale between dispatch and landing. It could not, and it was still
        // wrong — yield alone admits half the galaxy, so the alert announced "worth a look" about
        // landings whose own card read "none settleable".
        val state = rich()
        val to = target(state, systemsAway = 14)
        val dispatched = state.dispatch(to)

        // when
        val predicted = futureEvents(dispatched, now = t0).filterIsInstance<FutureEvent.SurveyLands>().single()
        // and then the probe actually lands, so the same worlds can be asked the real question
        val landed = advance(dispatched, from = t0, to = t0 + 3.hours)
        val actual = GalaxyState.occupiedWorldsIn(state.galaxy.seed, to)
            .mapNotNull { at -> worldAt(state.galaxy.seed, at) }
            .count { verdictFor(it, landed) is WorldVerdict.Settleable }

        // then the prediction and the verdict agree, which is the whole contract
        assertEquals(actual, predicted.settleable)
        assertTrue(predicted.settleable <= predicted.worldsFound, "the honest half cannot exceed the whole")
    }

    @Test
    fun `the prediction is far tighter than the yield bar alone`() {
        // The measurement that condemned the first version: over a whole galaxy, counting yield
        // alone marks about half of all worlds, where the settleable test marks a few percent. An
        // alert built on the loose number says "worth a look" almost every time it fires.
        val state = rich()
        val galaxy = state.galaxy.home.galaxy
        val worlds = (1..GalaxyBalance.SYSTEMS_PER_GALAXY)
            .flatMap { system -> GalaxyState.occupiedWorldsIn(state.galaxy.seed, SystemAddress(galaxy, system)) }
            .mapNotNull { at -> worldAt(state.galaxy.seed, at) }
        val overYieldBar = worlds.count {
            GalaxyBalance.yieldScore(it.traits).perMillion >= GalaxyBalance.WORTH_IT_THRESHOLD.perMillion
        }
        val settleable = worlds.count { world ->
            verdictFor(world, state.galaxy.copy(surveyed = setOf(world.at)), AdaptationLevels.NONE) is
                WorldVerdict.Settleable
        }

        assertTrue(
            settleable * 5 < overYieldBar,
            "yield alone marked $overYieldBar of ${worlds.size} against $settleable settleable — if these " +
                "ever converge, the two measures have stopped being different questions",
        )
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
        val predicted = futureEvents(dispatched, now = t0).filterIsInstance<FutureEvent.SurveyLands>().map { it.target }
        val applied = advance(dispatched, from = t0, to = t0 + 3.hours)
            .eventLog.filterIsInstance<Event.SurveyCompleted>().map { it.target }

        // then
        assertEquals(predicted, applied)
    }
}
