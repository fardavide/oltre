package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.notifications.data.GameNotifications
import dev.fardavide.oltre.client.notifications.data.LocalNotification
import dev.fardavide.oltre.client.notifications.data.NotificationScheduler
import dev.fardavide.oltre.client.save.data.GameStore
import dev.fardavide.oltre.client.save.data.SaveFile
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.WatchTarget
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.startUpgrade
import dev.fardavide.oltre.core.toggleAlert
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
        session.commit(GameStore(file), GameNotifications(scheduler, English))

        // then
        assertNotNull(file.content, "the colony was not written")
        assertEquals(1, scheduler.scheduled.size)
    }

    // **The seam this has to be measured at**, because `commit` is the one thing that runs without a
    // player in front of it: the shell's tick loop calls it whenever an event lands, and on Android
    // that loop outlives the foreground. A clear folded in here would take down the alarm the system
    // had posted moments before, and the colony would look as though it had never announced anything.
    // Opening the app is the only thing that clears — see `App`'s launch effect.
    @Test
    fun `committing never takes down an alert the player has already been shown`() = runTest {
        // given a colony with a build running
        val scheduler = RecordingScheduler()
        val session = GameSession(midBuild(), EPOCH)

        // when
        session.commit(GameStore(RecordingSaveFile()), GameNotifications(scheduler, English))

        // then
        assertEquals(0, scheduler.clearCount)
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
        session.commit(GameStore(RecordingSaveFile()), GameNotifications(scheduler, English))

        // then — the build has been applied, so there is nothing left to announce
        assertEquals(BuildingLevel(2), session.state.buildings.metalMine)
        assertEquals(emptyList(), scheduler.scheduled)
        assertTrue(completesAt < after, "the build should already have been due")
    }

    @Test
    fun `re-committing replaces the previous schedule rather than adding to it`() = runTest {
        // given
        val scheduler = RecordingScheduler()
        val notifications = GameNotifications(scheduler, English)
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
        // A galaxy seed is required rather than defaulted, so production cannot found every colony
        // in the same galaxy. Which one this test gets does not matter.
        val funded = GameState.initial(GalaxySeed(20_260_807)).copy(
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal),
        )
        val started = assertIs<StartUpgradeResult.Started>(
            startUpgrade(funded, BuildingType.METAL_MINE, at = EPOCH),
        ).state
        // **Subscribed, because since 0.5.0 a build nobody asked about books nothing** — and what
        // these tests are about is the commit booking *something*, not the gate. The gate has its
        // own tests in `GameNotificationsTest`.
        return toggleAlert(started, WatchTarget.Facility(BuildingType.METAL_MINE))
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

    var clearCount: Int = 0
        private set

    override suspend fun clear() {
        content = null
        clearCount++
    }
}

private class RecordingScheduler : NotificationScheduler {
    var scheduled: List<LocalNotification> = emptyList()
        private set

    var replaceCount: Int = 0
        private set

    var clearCount: Int = 0
        private set

    override suspend fun replaceAll(notifications: List<LocalNotification>) {
        scheduled = notifications
        replaceCount++
    }

    override suspend fun clearDelivered() {
        clearCount++
    }
}
