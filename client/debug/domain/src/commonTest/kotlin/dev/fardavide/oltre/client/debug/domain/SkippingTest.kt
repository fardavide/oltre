package dev.fardavide.oltre.client.debug.domain

import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.futureEvents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class SkippingTest {

    @Test
    fun `a skip lands on the next event and takes the clock with it`() {
        // given
        val state = buildingColony()
        val completesAt = futureEvents(state).minOf { it.at }

        // when
        val outcome = skipping(state, lastUpdatedAt = EPOCH, clock = DebugClock(), wallClock = EPOCH)

        // then — the three answers are one decision, and this is what makes them agree: the colony
        // is at the target, and the clock reads the target too.
        assertEquals(completesAt, outcome.at)
        assertEquals(completesAt, outcome.clock.now(EPOCH))
    }

    @Test
    fun `the colony handed back has the event applied`() {
        // The point of skipping rather than merely re-stamping: the build is finished when you
        // arrive, not a fraction short of finished.
        val state = buildingColony()

        val outcome = skipping(state, lastUpdatedAt = EPOCH, clock = DebugClock(), wallClock = EPOCH)

        assertTrue(outcome.state.builds.isEmpty(), "the build should have completed, was ${outcome.state.builds}")
        assertTrue(outcome.state.eventLog.size > state.eventLog.size)
    }

    @Test
    fun `an idle colony skips by the fallback hour`() {
        val outcome = skipping(freshColony(), lastUpdatedAt = EPOCH, clock = DebugClock(), wallClock = EPOCH)

        assertEquals(EPOCH + SKIP_FALLBACK, outcome.at)
        assertEquals(SKIP_FALLBACK, outcome.clock.offset)
    }

    @Test
    fun `skips accumulate on the clock rather than replacing each other`() {
        // Tap, tap, tap — the way the menu is actually used. Each skip has to start from where the
        // last one left the colony, which is the whole reason the clock is threaded through.
        var state = freshColony()
        var at = EPOCH
        var clock = DebugClock()

        repeat(3) {
            val outcome = skipping(state, lastUpdatedAt = at, clock = clock, wallClock = EPOCH)
            state = outcome.state
            at = outcome.at
            clock = outcome.clock
        }

        assertEquals(3.hours, clock.offset)
        assertEquals(EPOCH + 3.hours, at)
    }

    @Test
    fun `the target is chosen against the colony as it is now rather than as it was saved`() {
        // The first of the two `advance` calls, stated as a test. A colony saved an hour ago has
        // accrued since; choosing the target against the *saved* state would ask `futureEvents`
        // about a colony that does not exist any more.
        val state = buildingColony()
        val savedAt = EPOCH
        val now = EPOCH + 30.minutes

        val outcome = skipping(state, lastUpdatedAt = savedAt, clock = DebugClock(), wallClock = now)

        val expected = futureEvents(advance(state, from = savedAt, to = now))
            .filter { it.at > now }
            .minOfOrNull { it.at }
            ?: (now + SKIP_FALLBACK)
        assertEquals(expected, outcome.at)
    }

    @Test
    fun `a wall clock that stepped backwards does not drag the colony back with it`() {
        // The same clamp the tick loop applies. `advance` refuses to run backwards, so a device
        // whose clock moved must not be able to ask it to.
        val state = buildingColony()
        val savedAt = EPOCH + 2.hours

        val outcome = skipping(state, lastUpdatedAt = savedAt, clock = DebugClock(), wallClock = EPOCH)

        assertTrue(outcome.at > savedAt, "was ${outcome.at}")
    }

    @Test
    fun `skipping from an already skipped colony keeps the offset it had`() {
        // given a colony four hours ahead of the wall clock
        val clock = DebugClock(4.hours)
        val state = buildingColony(at = clock.now(EPOCH))

        // when
        val outcome = skipping(state, lastUpdatedAt = clock.now(EPOCH), clock = clock, wallClock = EPOCH)

        // then — the new offset is the old one plus the jump, never the jump alone
        assertTrue(outcome.clock.offset > 4.hours, "was ${outcome.clock.offset}")
        assertEquals(outcome.at, outcome.clock.now(EPOCH))
    }

    @Test
    fun `the offset is never negative however the clocks are arranged`() {
        val outcome = skipping(freshColony(), lastUpdatedAt = EPOCH, clock = DebugClock(), wallClock = EPOCH + 9.hours)

        assertTrue(outcome.clock.offset >= Duration.ZERO, "was ${outcome.clock.offset}")
    }
}
