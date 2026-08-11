package dev.fardavide.oltre.client

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.test.assertEquals
import dev.fardavide.oltre.client.debug.data.ShakeDetector
import dev.fardavide.oltre.client.notifications.data.GameNotifications
import dev.fardavide.oltre.client.notifications.data.LocalNotification
import dev.fardavide.oltre.client.notifications.data.NotificationScheduler
import dev.fardavide.oltre.client.save.data.GameStore
import dev.fardavide.oltre.client.save.data.SaveFile
import dev.fardavide.oltre.client.colony.presentation.ColonyTestTags
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GameSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking

// The composition root, driven for the first time. Everything `App` reaches for outside itself is
// already a parameter — the store, the notifications and the shake detector — so this needs three
// handwritten fakes and no framework at all.
//
// **What it is for is the one thing neither a unit test nor a component test can reach**: what a
// *launch* does. `arrivalOf` is unit-tested against two states, `CompletionSweepBehaviourTest`
// drives the row that draws the result, and between those two there is a seam — whether the thing
// the launch found actually survives long enough to reach the screen that announces it. That seam
// is where the first version of this pass was broken, and it was broken in a way that a green build
// and forty-one verified baselines said nothing about.
// The completion sweep's whole crossing, delay included. Winding past it settles the level badge.
private const val SWEEP_TOTAL_MILLIS: Long = 1_200

@OptIn(ExperimentalTestApi::class)
internal class AppRobot(private val test: ComposeUiTest, private val booked: RecordingNotifications) {

    // The launch runs on the auto-advancing clock, because it is a chain of real suspending work —
    // reading the save, advancing the colony, booking alerts — with no fixed length. A test that
    // wants to watch a transition stops the clock once that is done and winds it by hand from
    // there, exactly as every screenshot test does.
    fun pauseTheClock() = apply { test.mainClock.autoAdvance = false }

    fun open(tab: OltreTab) = apply {
        test.onNodeWithTag(ShellTestTags.tab(tab)).performClick()
        // Two frames: one for the tab's state change to land and one for the destination it selects
        // to compose. Needed only with the clock stopped; harmless while it is running.
        repeat(2) { test.mainClock.advanceTimeByFrame() }
        test.waitForIdle()
    }

    fun assertReads(text: String) = apply {
        test.onNodeWithText(text, substring = true).assertIsDisplayed()
    }

    // How many rows read something, which is the only unambiguous way to ask about a level badge
    // from outside the feature that owns the row tags: five of Research's six rows are genuinely at
    // level 0, so "does a row read LV 0" cannot distinguish the sixth holding its old level from
    // the sixth having already taken its new one. The count can.
    fun assertRowsReading(text: String, count: Int) = apply {
        assertEquals(count, test.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size)
    }

    fun letTheSweepFinish() = apply { test.mainClock.advanceTimeBy(SWEEP_TOTAL_MILLIS) }

    // The one control in the app with no words on it, so the one the shell reaches by tag. See
    // `ColonyTestTags`, which is public for this.
    fun tapTheWatchOn(building: BuildingType) = apply {
        test.onNodeWithTag(ColonyTestTags.watch(building)).performClick()
        test.waitForIdle()
    }

    // What a tap is *for*: the alert is booked by the `notifications.sync` inside the commit, and
    // that commit is unconditional precisely because asking for an alert writes no event.
    fun assertAlertsBooked(count: Int) = apply {
        assertEquals(count, booked.scheduled.size, "booked: ${booked.scheduled.map { it.id }}")
    }

    // Scoped to the cell, because all three rates end in the same two characters and an unscoped
    // query for one of them matches three nodes and fails on the ambiguity rather than on the
    // assertion.
    fun assertTheMetalCellReads(text: String) = apply {
        test.onNodeWithTag(ShellTestTags.resourceCell("METAL"))
            .assert(hasAnyDescendant(hasText(text, substring = true)))
    }
}

// A save file that never touches a disk. The real desktop one writes to a fixed path in the user's
// home, so a test that used it would overwrite the colony of whoever ran it.
internal class InMemorySaveFile : SaveFile {
    private var text: String? = null

    override suspend fun read(): String? = text
    override suspend fun write(text: String) { this.text = text }
    override suspend fun clear() { text = null }
}

// The platform edge, recorded rather than refused. What each alert *says* is
// `GameNotificationsTest`'s business; what this one is for is the seam above it — whether a tap
// reaches the scheduler at all, which no test below the composition root can see.
internal class RecordingNotifications : NotificationScheduler {
    var scheduled: List<LocalNotification> = emptyList()
        private set

    override suspend fun replaceAll(notifications: List<LocalNotification>) {
        scheduled = notifications
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun app(saved: GameSnapshot?, block: AppRobot.() -> Unit) {
    // Written *through* `GameStore` rather than encoded by hand. The store owns the save's schema
    // envelope, and a fixture that wrote its own JSON produced a blob the store could not read —
    // which `load` answers with null rather than an error, so the app quietly started a brand new
    // colony and every assertion about what the launch found passed for the wrong reason.
    val file = InMemorySaveFile()
    if (saved != null) {
        runBlocking { GameStore(file).save(saved) }
    }
    val booked = RecordingNotifications()
    runDesktopComposeUiTest(width = 393, height = 852) {
        setContent {
            App(
                store = GameStore(file),
                notifications = GameNotifications(booked),
                // Never shaken: the debug sheet is a modal over everything, and a test about what a
                // launch says must not have one open on top of it.
                shakeDetector = ShakeDetector { emptyFlow<Unit>() as Flow<Unit> },
            )
        }
        waitForIdle()
        AppRobot(this, booked).block()
    }
}
