package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.notifications.data.GameNotifications
import dev.fardavide.oltre.client.notifications.data.LocalNotification
import dev.fardavide.oltre.client.notifications.data.NotificationScheduler
import dev.fardavide.oltre.client.save.data.GameStore
import dev.fardavide.oltre.client.save.data.SaveFile
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.startUpgrade
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class CommitTest {

    @Test
    fun `committing writes the colony down and books its alerts together`() = runTest {
        // given a colony with a build running
        val file = RecordingSaveFile()
        val scheduler = RecordingScheduler()
        val session = GameSession(midBuild(), EPOCH)

        // when
        session.commit(GameStore(file), GameNotifications(scheduler))

        // then
        assertNotNull(file.content, "the colony was not written")
        assertEquals(1, scheduler.scheduled.size)
    }

    @Test
    fun `alerts are booked against the instant the session is accurate as of`() = runTest {
        // given a build that finishes half an hour in, and a session already past it
        val scheduler = RecordingScheduler()
        val started = midBuild()
        val completesAt = checkNotNull(started.builds[BuildingType.METAL_MINE]).completesAt
        val after = EPOCH + 1.hours
        val session = GameSession(advance(started, from = EPOCH, to = after), after)

        // when
        session.commit(GameStore(RecordingSaveFile()), GameNotifications(scheduler))

        // then — the build has been applied, so there is nothing left to announce
        assertEquals(BuildingLevel(2), session.state.buildings.metalMine)
        assertEquals(emptyList(), scheduler.scheduled)
        assertTrue(completesAt < after, "the build should already have been due")
    }

    @Test
    fun `re-committing replaces the previous schedule rather than adding to it`() = runTest {
        // given
        val scheduler = RecordingScheduler()
        val notifications = GameNotifications(scheduler)
        val store = GameStore(RecordingSaveFile())
        val session = GameSession(midBuild(), EPOCH)

        // when the same colony is committed twice, as a tick and an upgrade both would
        session.commit(store, notifications)
        session.commit(store, notifications)

        // then the second call replaced the first rather than doubling the alerts
        assertEquals(1, scheduler.scheduled.size)
        assertEquals(2, scheduler.replaceCount)
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

// Local doubles rather than shared ones: each is the whole of a one-method interface, and the
// modules that own them are KMP and cannot host Gradle test fixtures. When a second consumer
// needs either, it graduates to a sibling `:testing` module — see `.claude/docs/decisions.md`.
private class RecordingSaveFile : SaveFile {
    var content: String? = null
        private set

    override suspend fun read(): String? = content

    override suspend fun write(text: String) {
        content = text
    }
}

private class RecordingScheduler : NotificationScheduler {
    var scheduled: List<LocalNotification> = emptyList()
        private set

    var replaceCount: Int = 0
        private set

    override suspend fun replaceAll(notifications: List<LocalNotification>) {
        scheduled = notifications
        replaceCount++
    }
}
