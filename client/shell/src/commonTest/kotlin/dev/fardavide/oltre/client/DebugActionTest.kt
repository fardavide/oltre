package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.debug.domain.DebugClock
import dev.fardavide.oltre.client.debug.domain.SKIP_FALLBACK
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.startUpgrade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// The two debug actions, tested as functions. They used to be bodies inside `App`, where nothing
// could reach them — which is exactly what the coverage report said when the shell fell 7.7 points
// in one commit. The arithmetic is `:client:debug:domain`'s and tested there; what is tested here is
// the part that is the shell's own, which is what happens to the session and to the debug mark.
class DebugActionTest {

    @Test
    fun `skipping moves the session to the instant the simulation chose`() {
        // given
        val session = GameSession(midBuild(), EPOCH)

        // when
        val outcome = session.skipped(DebugClock(), wallClock = EPOCH)

        // then
        assertTrue(outcome.session.lastUpdatedAt > EPOCH)
        assertEquals(outcome.session.lastUpdatedAt, outcome.clock.now(EPOCH))
    }

    @Test
    fun `skipping applies the event it skipped to`() {
        val session = GameSession(midBuild(), EPOCH)

        val outcome = session.skipped(DebugClock(), wallClock = EPOCH)

        assertTrue(outcome.session.state.builds.isEmpty(), "was ${outcome.session.state.builds}")
        assertTrue(outcome.session.hasNewEventsSince(session))
    }

    @Test
    fun `skipping marks the colony`() {
        // The mark is the shell's to carry, because the shell is what writes the save.
        val session = GameSession(midBuild(), EPOCH)

        assertFalse(session.debugUsed)
        assertTrue(session.skipped(DebugClock(), wallClock = EPOCH).session.debugUsed)
    }

    @Test
    fun `a skipped session writes its future instant into the save`() {
        // The whole reason the offset needs no file of its own: the instant on disk *is* the offset,
        // and `DebugClock.resuming` reads it back on the next launch.
        val outcome = GameSession(midBuild(), EPOCH).skipped(DebugClock(), wallClock = EPOCH)

        val snapshot = outcome.session.toSnapshot()

        assertEquals(outcome.session.lastUpdatedAt, snapshot.lastUpdatedAt)
        assertTrue(snapshot.debugUsed)
        assertEquals(outcome.clock.offset, DebugClock.resuming(snapshot.lastUpdatedAt, wallClock = EPOCH).offset)
    }

    @Test
    fun `skipping an idle colony still moves it`() {
        // Nothing in flight, so there is no event to land on — and the action still has to do
        // something, or the menu has a dead end.
        val session = GameSession(freshState(), EPOCH)

        val outcome = session.skipped(DebugClock(), wallClock = EPOCH)

        assertEquals(EPOCH + SKIP_FALLBACK, outcome.session.lastUpdatedAt)
    }

    @Test
    fun `resetting founds a colony with nothing built and nothing in flight`() {
        val outcome = resetColony(wallClock = EPOCH)

        assertEquals(GameState.initial(GalaxySeed(EPOCH.toEpochMilliseconds())), outcome.session.state)
        assertEquals(EPOCH, outcome.session.lastUpdatedAt)
        assertTrue(outcome.session.state.builds.isEmpty())
        assertTrue(outcome.session.state.eventLog.isEmpty())
    }

    @Test
    fun `resetting drops the offset`() {
        // A new colony is not the old one's future. Starting it hours ahead of the wall clock would
        // be inheriting a debt it never ran up.
        assertEquals(Duration.ZERO, resetColony(wallClock = EPOCH).clock.offset)
    }

    @Test
    fun `resetting clears the mark because the colony it hands back has no history`() {
        // Davide's call, reversing what 0.2.5 shipped. The flag answers "has this colony's clock
        // been moved by hand", and nothing has been skipped in a colony that was founded a moment
        // ago — carrying the mark across would have made it a fact about the device rather than
        // about the save.
        assertFalse(resetColony(wallClock = EPOCH).session.debugUsed)
    }

    @Test
    fun `resetting a marked colony clears the mark`() {
        // The case the rule is actually about: skip first, then reset. The colony that comes back is
        // clean, and the flag says so.
        val skipped = GameSession(midBuild(), EPOCH).skipped(DebugClock(), wallClock = EPOCH).session
        assertTrue(skipped.debugUsed)

        assertFalse(resetColony(wallClock = EPOCH).session.debugUsed)
    }

    @Test
    fun `skipping is the only thing that sets the mark`() {
        // Stated as one test because it is one rule, and because the two halves of it were the other
        // way round a version ago.
        assertTrue(GameSession(midBuild(), EPOCH).skipped(DebugClock(), wallClock = EPOCH).session.debugUsed)
        assertFalse(resetColony(wallClock = EPOCH).session.debugUsed)
    }

    @Test
    fun `resetting at a different instant founds a different galaxy`() {
        // A reset is a first launch, and a first launch seeds its map from the instant it happened.
        val once = resetColony(wallClock = EPOCH).session.state.galaxy.seed
        val later = resetColony(wallClock = EPOCH + 5.hours).session.state.galaxy.seed

        assertNotEquals(once, later)
    }

    @Test
    fun `a reset colony is what the next launch reads back`() {
        // Reset is a first launch rather than a second way of founding a colony, so what it hands
        // back has to survive a save and a resume unchanged — the clean mark included.
        val outcome = resetColony(wallClock = EPOCH)

        val reloaded = resume(outcome.session.toSnapshot(), now = EPOCH)

        assertEquals(outcome.session.state, reloaded.state)
        assertFalse(reloaded.debugUsed)
    }

    private fun freshState(): GameState = GameState.initial(GalaxySeed(20_260_807))

    private fun midBuild(): GameState {
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2))
        val funded = freshState().copy(
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal),
        )
        return assertIs<StartUpgradeResult.Started>(
            startUpgrade(funded, BuildingType.METAL_MINE, at = EPOCH),
        ).state
    }

    private companion object {
        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)
    }
}
