package dev.fardavide.oltre.client

import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxySeed
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

        // then — the galaxy is seeded from the instant the colony was founded, so the whole state
        // is a function of `now` and nothing else.
        assertEquals(GameState.initial(GalaxySeed(now.toEpochMilliseconds())), session.state)
        assertEquals(now, session.lastUpdatedAt)
    }

    @Test
    fun `two colonies founded at different instants get different galaxies`() {
        // A default seed would have handed every player the same map, which is exactly why
        // `GameState.initial` takes one rather than defaulting it.
        val mine = resume(saved = null, now = EPOCH + 5.hours).state.galaxy
        val theirs = resume(saved = null, now = EPOCH + 9.hours).state.galaxy

        assertTrue(mine.seed != theirs.seed, "two launches must not share a galaxy seed")
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
