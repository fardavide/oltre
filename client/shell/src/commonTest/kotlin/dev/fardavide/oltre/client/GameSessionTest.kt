package dev.fardavide.oltre.client

import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.startUpgrade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class GameSessionTest {

    @Test
    fun `a first launch starts a fresh colony as of now`() {
        // given
        val now = EPOCH + 5.hours

        // when
        val session = resume(saved = null, now = now)

        // then
        assertEquals(GameState.initial(), session.state)
        assertEquals(now, session.lastUpdatedAt)
    }

    @Test
    fun `reopening the app credits every hour it was closed`() {
        // given
        val saved = GameSnapshot(lastUpdatedAt = EPOCH, state = GameState.initial())
        val reopenedAt = EPOCH + 8.hours

        // when
        val session = resume(saved, now = reopenedAt)

        // then
        assertEquals(advance(GameState.initial(), from = EPOCH, to = reopenedAt), session.state)
        assertEquals(reopenedAt, session.lastUpdatedAt)
    }

    @Test
    fun `a build that finished while the app was closed is finished on reopening`() {
        // given
        val started = midBuild()
        val completesAt = checkNotNull(started.buildQueue).completesAt

        // when
        val session = resume(
            GameSnapshot(lastUpdatedAt = EPOCH, state = started),
            now = completesAt + 1.minutes,
        )

        // then
        assertEquals(BuildingLevel(2), session.state.buildings.metalMine)
        assertNull(session.state.buildQueue)
    }

    @Test
    fun `a save from the future is clamped instead of losing the colony`() {
        // given — the device clock moved backwards between sessions
        val saved = GameSnapshot(lastUpdatedAt = EPOCH + 10.hours, state = GameState.initial())

        // when
        val session = resume(saved, now = EPOCH)

        // then
        assertEquals(saved.state, session.state)
        assertEquals(saved.lastUpdatedAt, session.lastUpdatedAt)
    }

    @Test
    fun `a session round trips through its snapshot`() {
        // given — still mid-build at the saved instant, so resuming has nothing to apply
        val session = GameSession(state = midBuild(), lastUpdatedAt = EPOCH + 5.minutes)

        // when
        val restored = resume(session.toSnapshot(), now = session.lastUpdatedAt)

        // then
        assertEquals(session, restored)
    }

    @Test
    fun `a tick that only accrued resources is not worth saving`() {
        // given
        val before = GameSession(GameState.initial(), EPOCH)

        // when
        val after = GameSession(advance(before.state, from = EPOCH, to = EPOCH + 1.hours), EPOCH + 1.hours)

        // then
        assertFalse(after.hasNewEventsSince(before))
    }

    @Test
    fun `a tick that completed a build is worth saving`() {
        // given
        val before = GameSession(midBuild(), EPOCH)
        val completesAt = checkNotNull(before.state.buildQueue).completesAt

        // when
        val after = GameSession(advance(before.state, from = EPOCH, to = completesAt), completesAt)

        // then
        assertTrue(after.hasNewEventsSince(before))
    }

    private fun midBuild(): GameState {
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2))
        val funded = GameState.initial().copy(
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal),
        )
        return assertIs<StartUpgradeResult.Started>(
            startUpgrade(funded, BuildingType.METAL_MINE, at = EPOCH),
        ).state
    }

    private companion object {
        val EPOCH = Instant.fromEpochMilliseconds(0)
    }
}
