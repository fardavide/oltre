package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.debug.domain.DebugClock
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
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// The two things the shell does on every frame and every tap, as functions. Both lived inside `App`
// — one in a `LaunchedEffect`, one in a local `fun` — where nothing could execute either, which is
// why the clamp below had a comment explaining it and no test asserting it.
class SessionTickTest {

    @Test
    fun `a tick brings the colony up to now`() {
        // given
        val session = GameSession(midBuild(), EPOCH)

        // when
        val ticked = session.ticked(DebugClock(), wallClock = EPOCH + 3.hours)

        // then
        assertEquals(EPOCH + 3.hours, ticked.lastUpdatedAt)
        assertTrue(ticked.state.resources.metal > session.state.resources.metal)
    }

    @Test
    fun `a tick applies whatever completed on the way`() {
        val session = GameSession(midBuild(), EPOCH)

        val ticked = session.ticked(DebugClock(), wallClock = EPOCH + 12.hours)

        assertTrue(ticked.state.builds.isEmpty(), "was ${ticked.state.builds}")
        assertTrue(ticked.hasNewEventsSince(session))
    }

    @Test
    fun `a wall clock that stepped backwards does not drag the colony back with it`() {
        // The clamp, and the reason it exists: NTP or a player changing the device time can move the
        // wall clock behind the saved instant, and `advance` requires `to >= from`. Without this the
        // colony crashes on a clock correction.
        //
        // The build has to still be *due* for "nothing moved" to mean anything. The first version
        // stamped the session five hours ahead of a build that had completed at 24 minutes, so
        // `advance` rightly applied the completion and the assertion failed against a colony that
        // was behaving correctly — a badly built scenario rather than a bug in the code under test.
        val session = GameSession(midBuild(), EPOCH)

        val ticked = session.ticked(DebugClock(), wallClock = EPOCH - 3.hours)

        assertEquals(EPOCH, ticked.lastUpdatedAt)
        assertEquals(session.state, ticked.state)
    }

    @Test
    fun `a tick reads the debug clock rather than the wall clock`() {
        // A skipped colony keeps running ahead: the tick has to carry on from where the skip left
        // it, not fall back to real time.
        val session = GameSession(midBuild(), EPOCH)

        val ticked = session.ticked(DebugClock(6.hours), wallClock = EPOCH)

        assertEquals(EPOCH + 6.hours, ticked.lastUpdatedAt)
    }

    @Test
    fun `a tick carries the debug mark`() {
        val session = GameSession(midBuild(), EPOCH, debugUsed = true)

        assertTrue(session.ticked(DebugClock(), wallClock = EPOCH + 1.hours).debugUsed)
    }

    @Test
    fun `acting applies the transition at the instant the player acted`() {
        // given
        val session = GameSession(freshState(), EPOCH)
        var seen: Instant? = null

        // when
        val acted = session.acting(DebugClock(), wallClock = EPOCH + 2.hours) { state, at ->
            seen = at
            state
        }

        // then
        assertEquals(EPOCH + 2.hours, seen)
        assertEquals(EPOCH + 2.hours, acted.lastUpdatedAt)
    }

    @Test
    fun `acting hands the transition a colony that has already accrued`() {
        // The bug this shape prevents: acting on the saved state would spend resources the colony
        // has not earned yet, so a player who left the app open for an hour could not buy what the
        // screen says they can afford.
        val session = GameSession(freshState(), EPOCH)
        var stock = Resources.of()

        session.acting(DebugClock(), wallClock = EPOCH + 4.hours) { state, _ ->
            stock = state.resources
            state
        }

        assertTrue(stock.metal > session.state.resources.metal, "was ${stock.metal}")
    }

    @Test
    fun `acting clamps a backwards wall clock too`() {
        val session = GameSession(freshState(), EPOCH + 5.hours)

        val acted = session.acting(DebugClock(), wallClock = EPOCH) { state, _ -> state }

        assertEquals(EPOCH + 5.hours, acted.lastUpdatedAt)
    }

    @Test
    fun `a refused action leaves the colony advanced but otherwise untouched`() {
        // Every caller in `App` returns the state unchanged when core refuses — an unaffordable
        // upgrade, a busy research slot. The accrual still stands, and no event is logged.
        val session = GameSession(freshState(), EPOCH)

        val acted = session.acting(DebugClock(), wallClock = EPOCH + 1.hours) { state, _ -> state }

        assertEquals(EPOCH + 1.hours, acted.lastUpdatedAt)
        assertTrue(!acted.hasNewEventsSince(session))
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
