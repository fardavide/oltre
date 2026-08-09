package dev.fardavide.oltre.client.debug.domain

import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.FutureEvent
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.futureEvents
import dev.fardavide.oltre.core.startUpgrade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class SkipAheadTest {

    @Test
    fun `an idle colony has nothing to skip to and falls back to an hour`() {
        // The case that makes the action total. Without it the menu has a dead end that is easy to
        // reach — finish a build, cannot afford the next one — and impossible to escape.
        val skip = skipAhead(freshColony(), now = EPOCH)

        assertIs<SkipAhead.ByFallback>(skip)
        assertEquals(EPOCH + SKIP_FALLBACK, skip.to)
    }

    @Test
    fun `a colony mid-build skips to the completion`() {
        // given
        val state = buildingColony()

        // when
        val skip = skipAhead(state, now = EPOCH)

        // then
        val event = assertIs<FutureEvent.BuildCompletes>(assertIs<SkipAhead.ToEvent>(skip).event)
        assertEquals(event.at, skip.to)
        assertTrue(skip.to > EPOCH)
    }

    @Test
    fun `the skip target is the same instant the notification is booked at`() {
        // Both read `futureEvents`, and they must never disagree about what happens next — a menu
        // that says one thing while the lock screen says another is worse than either being wrong.
        val state = buildingColony()

        val soonest = futureEvents(state).minByOrNull { it.at }

        assertEquals(soonest?.at, skipAhead(state, now = EPOCH).to)
    }

    @Test
    fun `skipping lands exactly on the event so advancing there applies it`() {
        // The point of the whole action: the instant handed back is one `advance` will do something
        // at, so a skip followed by an advance is a completed build rather than a colony parked a
        // fraction short of one.
        val state = buildingColony()

        val skip = skipAhead(state, now = EPOCH)
        val advanced = advance(state, from = EPOCH, to = skip.to)

        assertTrue(advanced.builds.isEmpty(), "the build should have completed, was ${advanced.builds}")
        assertTrue(advanced.eventLog.size > state.eventLog.size)
    }

    @Test
    fun `an event already due is not a skip target`() {
        // An event at or before now is one `advance` is about to apply or already has, so skipping
        // to it would move nothing and the menu would look broken. Same rule `notificationsFor`
        // applies, and for the same reason.
        val state = buildingColony()
        val completesAt = soonest(state)

        val skip = skipAhead(state, now = completesAt)

        assertIs<SkipAhead.ByFallback>(skip)
        assertEquals(completesAt + SKIP_FALLBACK, skip.to)
    }

    @Test
    fun `the soonest event wins when two facilities are building at once`() {
        // Builds run in parallel, one job per facility, so a colony can hold several completion
        // instants at the same time. The answer has to be the nearest of them.
        val state = assertIs<StartUpgradeResult.Started>(
            startUpgrade(buildingColony(), BuildingType.CRYSTAL_MINE, at = EPOCH),
        ).state
        val pending = futureEvents(state).map { it.at }

        assertEquals(2, state.builds.size, "both facilities should be building, was ${state.builds.keys}")

        val skip = skipAhead(state, now = EPOCH)

        assertEquals(pending.min(), skip.to)
        assertTrue(pending.any { it > skip.to }, "the later completion should still be pending")
    }

    @Test
    fun `repeated skipping walks the colony forward one event at a time`() {
        // How the menu is actually used - tap, tap, tap - so it is worth a test that the loop
        // terminates and keeps making progress rather than sticking on one instant.
        var state = buildingColony()
        var now = EPOCH
        val visited = mutableListOf<Instant>()

        repeat(6) {
            val skip = skipAhead(state, now = now)
            state = advance(state, from = now, to = skip.to)
            now = skip.to
            visited += now
        }

        assertEquals(visited.sorted(), visited, "each skip must land strictly later than the last")
        assertEquals(visited.distinct(), visited)
    }
}

private fun soonest(state: GameState): Instant = futureEvents(state).minOf { it.at }
