package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.debug.domain.DebugClock
import dev.fardavide.oltre.core.BuildShipsResult
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.HullAlert
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.WatchTarget
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.buildShips
import dev.fardavide.oltre.core.cycleHullAlert
import dev.fardavide.oltre.core.startUpgrade
import dev.fardavide.oltre.core.toggleAlert
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class GameSessionTest {

    // **`resume` no longer founds anything, and this is what replaced the two tests that said it
    // did.** A first launch used to mean *a null save*, and this function drew a `GalaxySeed` from
    // the clock to mint a colony — which a shared galaxy cannot allow: two devices signing into one
    // account would each have invented a map, and neither would be the one the server holds.
    //
    // What is left that is local is the debug menu's reset, which is where the seed is drawn now.
    @Test
    fun `a reset colony is founded as of the instant it was reset`() {
        // given
        val now = EPOCH + 5.hours

        // when
        val outcome = resetColony(wallClock = now)

        // then — the galaxy is seeded from the instant it was founded, so the whole state is a
        // function of `now` and nothing else.
        assertEquals(GameState.initial(GalaxySeed(now.toEpochMilliseconds())), outcome.session.state)
        assertEquals(now, outcome.session.lastUpdatedAt)
    }

    @Test
    fun `two colonies founded at different instants get different galaxies`() {
        // A default seed would have handed every player the same map, which is exactly why
        // `GameState.initial` takes one rather than defaulting it.
        val mine = resetColony(wallClock = EPOCH + 5.hours).session.state.galaxy
        val theirs = resetColony(wallClock = EPOCH + 9.hours).session.state.galaxy

        assertTrue(mine.seed != theirs.seed, "two resets must not share a galaxy seed")
    }

    @Test
    fun `reopening the app credits every hour it was closed`() {
        // given
        val saved = GameSnapshot(lastUpdatedAt = EPOCH, state = freshState())
        val reopenedAt = EPOCH + 8.hours

        // when
        val session = resume(saved, now = reopenedAt)

        // then
        assertEquals(advance(freshState(), from = EPOCH, to = reopenedAt), session.state)
        assertEquals(reopenedAt, session.lastUpdatedAt)
    }

    @Test
    fun `a build that finished while the app was closed is finished on reopening`() {
        // given
        val started = midBuild()
        val completesAt = checkNotNull(started.builds[BuildingType.METAL_MINE]).completesAt

        // when
        val session = resume(
            GameSnapshot(lastUpdatedAt = EPOCH, state = started),
            now = completesAt + 1.minutes,
        )

        // then
        assertEquals(BuildingLevel(2), session.state.buildings.metalMine)
        assertTrue(session.state.builds.isEmpty())
    }

    @Test
    fun `a save from the future is clamped instead of losing the colony`() {
        // given — the device clock moved backwards between sessions
        val saved = GameSnapshot(lastUpdatedAt = EPOCH + 10.hours, state = freshState())

        // when
        val session = resume(saved, now = EPOCH)

        // then
        assertEquals(saved.state, session.state)
        assertEquals(saved.lastUpdatedAt, session.lastUpdatedAt)
    }

    @Test
    fun `a session round trips through its snapshot`() {
        // given — still mid-build at the saved instant, so resuming has nothing to apply. One
        // minute rather than five: a second Metal Mine is a two-minute build since 0.2.7, so five
        // would land the completion and this would be testing resume-applies-a-build instead.
        val session = GameSession(state = midBuild(), lastUpdatedAt = EPOCH + 1.minutes)

        // when
        val restored = resume(session.toSnapshot(), now = session.lastUpdatedAt)

        // then
        assertEquals(session, restored)
    }

    @Test
    fun `a tick that only accrued resources is not worth saving`() {
        // given
        val before = GameSession(freshState(), EPOCH)

        // when
        val after = GameSession(advance(before.state, from = EPOCH, to = EPOCH + 1.hours), EPOCH + 1.hours)

        // then
        assertFalse(after.hasNewEventsSince(before))
    }

    @Test
    fun `a tick that completed a build is worth saving`() {
        // given
        val before = GameSession(midBuild(), EPOCH)
        val completesAt = checkNotNull(before.state.builds[BuildingType.METAL_MINE]).completesAt

        // when
        val after = GameSession(advance(before.state, from = EPOCH, to = completesAt), completesAt)

        // then
        assertTrue(after.hasNewEventsSince(before))
    }

    // **The race `alerting` exists for, and the reason it is the one verb that transitions before it
    // advances.** The tick loop runs once a second, so a card can show a lit bell on a build that
    // finished 400ms ago. Advance-first, that tap would find the row settled, fall through to the
    // affordability branch, and move the empire's single watch onto it — unbooking the alert the
    // player had actually set, and persisting it, because this action commits unconditionally.
    @Test
    fun `a square tapped on a build that has just landed does not steal the watch`() {
        // given a colony watching one row and building another it has asked about, and a clock that
        // has passed the build's completion without the tick loop having caught up
        val watched = WatchTarget.Facility(BuildingType.CRYSTAL_MINE)
        val subscribed = WatchTarget.Facility(BuildingType.METAL_MINE)
        val state = toggleAlert(toggleAlert(midBuild(), watched), subscribed)
        val session = GameSession(state, EPOCH)
        val landed = checkNotNull(state.builds[BuildingType.METAL_MINE]).completesAt

        // when the player taps the lit bell on the row that has just landed
        val after = session.alerting(DebugClock(), wallClock = landed, target = subscribed)

        // then the build is done, its subscription is spent, and the watch is where it was
        assertEquals(BuildingLevel(2), after.state.buildings.metalMine)
        assertEquals(emptySet(), after.state.subscribed)
        assertEquals(watched, after.state.watching)
    }

    @Test
    fun `a square tapped on a running build subscribes to it`() {
        // given the ordinary case, well inside the build
        val target = WatchTarget.Facility(BuildingType.METAL_MINE)
        val session = GameSession(midBuild(), EPOCH)

        // when
        val after = session.alerting(DebugClock(), wallClock = EPOCH + 1.seconds, target = target)

        // then
        assertEquals(setOf(target), after.state.subscribed)
        assertNull(after.state.watching)
    }

    // **Not the race `alerting` has, and this test exists to say so rather than to imply it.** It was
    // first written as "a square tapped on an order that has just landed does not silently do
    // nothing", which its own assertions contradicted — they assert the ask ends absent, which *is*
    // the tap doing nothing, and both orderings produce it.
    //
    // What is actually true is the equivalence, so that is what is asserted: `advance` only ever
    // removes yard jobs, so a type present after a span was present before it, and
    // `withoutFinishedHullAlerts` prunes exactly what `cycleHullAlert`'s guard would have refused.
    // The ordering in `alertingHull` is therefore consistency with the other square rather than a
    // fix, and this is the test that holds that claim up — see the comment on the verb.
    @Test
    fun `a tap at the instant an order lands settles the same way whichever end it is taken from`() {
        // given a colony two hulls into an order it has not asked about
        val session = GameSession(midOrder(), EPOCH)
        val landed = session.state.yard.last().completesAt

        // when the player taps the square at the instant the last hull lands — cycle then advance,
        // which is what the verb does
        val cycledFirst = session.alertingHull(DebugClock(), wallClock = landed, ship = ShipType.SKIFF)
        // ...against the other order, spelled out rather than described
        val advancedFirst = cycleHullAlert(advance(session.state, from = EPOCH, to = landed), ShipType.SKIFF)

        // then the two agree, and both are the empty ask: the order the square was about is finished,
        // so there is nothing left to be told about
        assertEquals(advancedFirst.hullAlerts, cycledFirst.state.hullAlerts)
        assertEquals(emptyMap(), cycledFirst.state.hullAlerts)
        assertEquals(emptyList(), cycledFirst.state.yard)
    }

    @Test
    fun `a square tapped on a queue still building asks about the whole order`() {
        val session = GameSession(midOrder(), EPOCH)

        val after = session.alertingHull(DebugClock(), wallClock = EPOCH + 1.seconds, ship = ShipType.SKIFF)

        assertEquals(mapOf(ShipType.SKIFF to HullAlert.WHEN_ALL_DONE), after.state.hullAlerts)
    }

    @Test
    fun `a second tap moves the same order onto hull-by-hull alerts`() {
        val session = GameSession(midOrder(), EPOCH)
            .alertingHull(DebugClock(), wallClock = EPOCH + 1.seconds, ship = ShipType.SKIFF)

        val after = session.alertingHull(DebugClock(), wallClock = EPOCH + 2.seconds, ship = ShipType.SKIFF)

        assertEquals(mapOf(ShipType.SKIFF to HullAlert.EACH_HULL), after.state.hullAlerts)
    }

    @Test
    fun `the sheet's bell moves the standing answer and brings the colony up to now`() {
        // The third square, and the one with nothing to race against: it flips a flag no job is keyed
        // to. What it still has to do is advance, because this action commits — and a save stamped at
        // an instant the colony had not caught up to would be a colony that lost the span.
        val session = GameSession(midBuild(), EPOCH)
        val landed = checkNotNull(session.state.builds[BuildingType.METAL_MINE]).completesAt

        val after = session.alertingFlights(DebugClock(), wallClock = landed)

        assertTrue(after.state.announceFlights)
        assertEquals(landed, after.lastUpdatedAt)
        assertEquals(BuildingLevel(2), after.state.buildings.metalMine)
    }

    @Test
    fun `a bell tapped on a clock that has stepped backwards still answers`() {
        // The clamp every verb in this file carries, and it is load-bearing rather than defensive:
        // a wall clock really does move backwards — NTP, or the player changing the device time —
        // and `advance` requires `to >= from`, so without it the tap would crash the colony instead
        // of booking an alert.
        val session = GameSession(freshState(), EPOCH + 10.hours)

        val after = session.alertingFlights(DebugClock(), wallClock = EPOCH)

        assertTrue(after.state.announceFlights)
        assertEquals(EPOCH + 10.hours, after.lastUpdatedAt, "the colony must not be dragged backwards")
    }

    @Test
    fun `a second tap takes the standing answer back`() {
        val session = GameSession(freshState(), EPOCH)
            .alertingFlights(DebugClock(), wallClock = EPOCH + 1.seconds)

        val after = session.alertingFlights(DebugClock(), wallClock = EPOCH + 2.seconds)

        assertFalse(after.state.announceFlights)
    }

    private fun midOrder(): GameState = assertIs<BuildShipsResult.Started>(
        buildShips(
            freshState().copy(resources = Resources.of(metal = 100_000, crystal = 100_000)),
            Ships.of(ShipType.SKIFF, 2),
            at = EPOCH,
        ),
    ).state

    private fun midBuild(): GameState {
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2))
        val funded = freshState().copy(
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal),
        )
        return assertIs<StartUpgradeResult.Started>(
            startUpgrade(funded, BuildingType.METAL_MINE, at = EPOCH),
        ).state
    }

    // `GameState.initial` takes a galaxy seed rather than defaulting one, so that production cannot
    // quietly found every colony in the same galaxy. Tests that do not care which map they get say
    // so once, here.
    private fun freshState(): GameState = GameState.initial(GalaxySeed(20_260_807))

    private companion object {
        val EPOCH = Instant.fromEpochMilliseconds(0)
    }
}
