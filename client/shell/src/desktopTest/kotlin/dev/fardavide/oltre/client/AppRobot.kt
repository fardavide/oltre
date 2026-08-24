package dev.fardavide.oltre.client

import dev.fardavide.oltre.client.design.text.English
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.test.assertEquals
import dev.fardavide.oltre.client.changelog.presentation.ChangelogText
import dev.fardavide.oltre.client.changelog.presentation.EnglishChangelog
import dev.fardavide.oltre.client.changelog.ui.ChangelogTestTags
import dev.fardavide.oltre.client.colony.ui.ColonyTestTags
import dev.fardavide.oltre.client.debug.data.ShakeDetector
import dev.fardavide.oltre.client.design.core.OltreMotion
import dev.fardavide.oltre.client.dispatch.ui.DispatchTestTags
import dev.fardavide.oltre.client.notifications.data.GameNotifications
import dev.fardavide.oltre.client.notifications.data.LocalNotification
import dev.fardavide.oltre.client.notifications.data.NotificationScheduler
import dev.fardavide.oltre.client.player.ui.PlayerTestTags
import dev.fardavide.oltre.client.galaxy.ui.GalaxyTestTags
import dev.fardavide.oltre.client.galaxy.ui.LedgerMode
import dev.fardavide.oltre.client.save.data.GameStore
import dev.fardavide.oltre.client.save.data.Preferences
import dev.fardavide.oltre.client.save.data.PreferencesStore
import dev.fardavide.oltre.client.save.data.SaveFile
import dev.fardavide.oltre.client.settings.ui.SettingsTestTags
import dev.fardavide.oltre.client.shipyard.ui.ShipyardTestTags
import dev.fardavide.oltre.core.AlertCategory
import dev.fardavide.oltre.core.AlertDelivery
import dev.fardavide.oltre.core.AlertMode
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.ShipType
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

// The settings sheet swapping its face, which is the design's own 210ms and one-shot.
private const val SWAP_MILLIS: Long = 210

// Material's own description for a `ModalBottomSheet`'s scrim. A literal because the string belongs
// to Material rather than to this app's catalogue, and there is no other handle on the one node that
// dismisses a sheet from outside it.
private const val CLOSE_SHEET = "Close sheet"

// Long enough for a `ModalBottomSheet` to finish going away. Material animates the hide before it
// calls back, so a test that asserted straight after the tap would be asking about a sheet that is
// still on its way out.
private const val SHEET_HIDE_MILLIS: Long = 1_000

@OptIn(ExperimentalTestApi::class)
internal class AppRobot(private val test: ComposeUiTest, private val booked: RecordingNotifications) {

    // The launch runs on the auto-advancing clock, because it is a chain of real suspending work —
    // reading the save, advancing the colony, booking alerts — with no fixed length. A test that
    // wants to watch a transition stops the clock once that is done and winds it by hand from
    // there, exactly as every screenshot test does.
    fun pauseTheClock() = apply { test.mainClock.autoAdvance = false }

    fun open(tab: OltreTab) = apply {
        test.onNodeWithTag(ShellTestTags.tab(tab)).performClick()
        // Two frames for the tab's state change to land and for the destination it selects to
        // compose, then the length of the switch itself: since 0.13.1 a destination arrives over
        // `SWITCH_MILLIS` and the one it replaces is still composed for exactly that long, so a test
        // that asserted immediately after the tap would be counting rows on two screens at once.
        // Needed only with the clock stopped; harmless while it is running.
        //
        // **This is a wait, not a settle** — it is as short as the switch and no shorter, so a test
        // that cares about what else is happening on the arriving screen still arrives early enough
        // to see it. The completion sweep is the case that matters: it does not put a band on a card
        // for another 420ms and does not move a level badge for 930, so opening a tab still lands
        // well before either.
        repeat(2) { test.mainClock.advanceTimeByFrame() }
        test.mainClock.advanceTimeBy(OltreMotion.SWITCH_MILLIS.toLong())
        test.waitForIdle()
    }

    // The Galaxy tab's own switch, reached from the shell rather than from the feature: what this
    // adds over `LedgerBehaviourTest`'s version of it is the composition root's half — the lambda
    // that turns a tap into a line in a preferences file.
    fun openTheWorldsList() = apply {
        test.onNodeWithTag(GalaxyTestTags.mode(LedgerMode.WORLDS)).performClick()
        test.waitForIdle()
    }

    fun assertDoesNotRead(text: String) = apply {
        test.onNodeWithText(text, substring = true).assertDoesNotExist()
    }

    fun assertReads(text: String) = apply {
        test.onNodeWithText(text, substring = true).assertIsDisplayed()
    }

    // How many rows read something, which is the only unambiguous way to ask about a level badge
    // from outside the feature that owns the row tags: five of Research's six rows are genuinely at
    // level 0, so "does a row read LV 0" cannot distinguish the sixth holding its old level from
    // the sixth having already taken its new one. The count can.
    //
    // **The player strip is subtracted, and that is the word `rows` being kept honest.** It draws a
    // level badge of its own, in the same words, above every destination — so counting the whole
    // tree would tie an assertion about how many technologies exist to a piece of chrome that is not
    // a technology. `PlayerTestTags` is public for exactly this, as `ColonyTestTags` is.
    fun assertRowsReading(text: String, count: Int) = apply {
        val rows = test.onAllNodes(
            hasText(text, substring = true) and hasAnyAncestor(hasTestTag(PlayerTestTags.CONTENT)).not(),
        )
        assertEquals(count, rows.fetchSemanticsNodes().size)
    }

    // The badge the strip wears, asked for inside the strip rather than anywhere on screen. The
    // counterpart of `assertRowsReading`'s subtraction and needed for the same reason: `LV 4` is a
    // sentence a facility row can say too, so an unscoped assertion about the player's level would
    // pass on a mine.
    fun assertThePlayerStripReads(text: String) = apply {
        test.onNode(
            hasText(text, substring = true) and hasAnyAncestor(hasTestTag(PlayerTestTags.CONTENT)),
        ).assertIsDisplayed()
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

    // The other half of the platform seam, and the one no test below the composition root can see:
    // whether *opening* the app wiped what the OS had already put in front of the player. Counted
    // rather than asserted true, because the number is the thing that could go wrong — a clear that
    // rode along with every commit would fire on every tap and, on Android, in the background.
    fun assertTrayCleared(times: Int) = apply {
        assertEquals(times, booked.clearCount, "clearDelivered calls")
    }

    // The Shipyard's other control, and the one with no words on it at all — so, like the colony's
    // square, it is reached by tag. What it *shows* is a bell and no query can read a Canvas; what
    // this can drive is the cycle, and what that cycle is worth is the alert count above.
    fun tapTheAlertOn(type: ShipType) = apply {
        test.onNodeWithTag(ShipyardTestTags.alert(type), useUnmergedTree = true).performScrollTo().performClick()
        test.waitForIdle()
    }

    fun assertOffersAlertOn(type: ShipType) = apply {
        test.onNodeWithTag(ShipyardTestTags.alert(type), useUnmergedTree = true).assertExists()
    }

    fun assertNoAlertOn(type: ShipType) = apply {
        test.onNodeWithTag(ShipyardTestTags.alert(type), useUnmergedTree = true).assertDoesNotExist()
    }

    // ── The settings sheet, from the gear ───────────────────────────────────────────────────
    //
    // Four taps that only mean anything together, and here rather than in `:client:settings:ui` for
    // the dispatch bell's reason: a chip is a control on the sheet, what it is worth is the schedule
    // the composition root books in `prefer`, and nothing below this can see both ends.

    fun openTheSettings() = apply {
        test.onNodeWithTag(PlayerTestTags.SETTINGS, useUnmergedTree = true).performClick()
        test.waitForIdle()
    }

    // **The scrim, which is the exit a test can actually take.** The gear is behind it — a
    // `ModalBottomSheet` covers the window, scrim included, so the strip underneath consumes nothing
    // while the sheet is up. Material gives the scrim an `onClick` and a content description of its
    // own, and that is what a finger tapping outside the sheet lands on.
    //
    // Measured at 0.19: driving this through the gear silently did nothing, and the two tests that
    // asked what dismissal *writes* are what noticed. `AlertSheetAppBehaviourTest` had used the gear
    // since 0.18 without ever depending on the sheet actually closing.
    fun dismissTheSettings() = apply {
        // **The scrim's action rather than a tap on it**, and that is not a shortcut around the
        // gesture — it is the only way to express it. This sheet is full height, so the scrim's
        // *centre* is behind the sheet: `performClick` aims at a node's middle and would land on the
        // panel it is trying to dismiss. Measured at 0.19, where three tests agreed the sheet was
        // still up after being told to close.
        test.onNodeWithContentDescription(CLOSE_SHEET).performSemanticsAction(SemanticsActions.OnClick)
        // Material settles the sheet before it reports the dismissal, so whatever the callback writes
        // down lands a whole hide animation after the gesture.
        test.mainClock.advanceTimeBy(SHEET_HIDE_MILLIS)
        test.waitForIdle()
    }

    // **The door from settings to the changelog**, which is the whole of what the build row is for.
    // The swap takes 210ms and nothing under it moves, so this waits the length of the swap rather
    // than settling an animation that has no end.
    fun openTheChangelog() = apply {
        test.onNodeWithTag(ChangelogTestTags.BUILD_ROW).performScrollTo().performClick()
        test.mainClock.advanceTimeBy(SWAP_MILLIS)
        test.waitForIdle()
    }

    fun assertChangelogShowing(showing: Boolean = true) = apply {
        val found = test.onAllNodesWithTag(ChangelogTestTags.SHEET).fetchSemanticsNodes().isNotEmpty()
        assertEquals(showing, found, "the changelog is ${if (found) "up" else "not up"}")
    }

    fun assertSettingsShowing(showing: Boolean = true) = apply {
        val found = test.onAllNodesWithTag(SettingsTestTags.SHEET).fetchSemanticsNodes().isNotEmpty()
        assertEquals(showing, found, "the settings sheet is ${if (found) "up" else "not up"}")
    }

    fun chooseMode(mode: AlertMode) = apply {
        test.onNodeWithTag(SettingsTestTags.mode(mode)).performClick()
        test.waitForIdle()
    }

    fun chooseByCategory() = chooseMode(AlertMode.BY_CATEGORY)

    fun chooseDelivery(delivery: AlertDelivery) = apply {
        test.onNodeWithTag(SettingsTestTags.delivery(delivery)).performClick()
        test.waitForIdle()
    }

    // The row, not the square — the whole 38dp width answers, which is what lets the square stay at
    // the colony's own 29dp without carrying a 44dp hit area on its own.
    fun toggleCategory(category: AlertCategory) = apply {
        test.onNodeWithTag(SettingsTestTags.category(category)).performScrollTo().performClick()
        test.waitForIdle()
    }

    // **What `AlertDelivery.TOTAL` actually promises**, asserted where it can be seen: several
    // bookings, one tray entry. The ids have to differ — neither platform will hold two pending
    // requests under one identifier — so the distinct count of the *other* field is the whole claim.
    fun assertOneTrayEntry() = apply {
        assertEquals(
            1,
            booked.scheduled.map { it.collapseId }.distinct().size,
            "tray entries: ${booked.scheduled.map { it.collapseId }}",
        )
    }

    // Whether the colony's rows still carry the square, asked of the tab rather than of a state:
    // under `BY_CATEGORY` the question has been answered one level up and the control is *absent*,
    // which is the only thing this app does with a control that has nothing left to decide.
    fun assertColonyOffersSquares(offered: Boolean) = apply {
        open(OltreTab.COLONY)
        val node = test.onAllNodesWithTag(ColonyTestTags.watch(BuildingType.METAL_MINE), useUnmergedTree = true)
        assertEquals(
            offered,
            node.fetchSemanticsNodes().isNotEmpty(),
            if (offered) "the row should carry a square" else "the row should carry none",
        )
    }

    // ── The dispatch sheet, from the ledger ─────────────────────────────────────────────────
    //
    // Three taps that only mean anything together, which is why they are here rather than in a
    // feature Robot: the bell is a control on the sheet and the alert it is worth is booked by the
    // composition root's commit, and nothing below this can see both ends.

    fun openTheWorld(at: GalaxyCoordinate) = apply {
        test.onNodeWithTag(GalaxyTestTags.row(at)).performScrollTo().performClick()
        test.waitForIdle()
    }

    // No words on it, like the other two squares in the app — so it is reached by tag as well.
    fun tapTheBellOnTheSheet() = apply {
        test.onNodeWithTag(DispatchTestTags.ANNOUNCE).performClick()
        test.waitForIdle()
    }

    // The sheet opens on a manifest it derived, which may be the whole idle pool — so a test that
    // wants to send twice has to leave a hull behind. At the stepper's bound this is a no-op, which
    // is the same answer, so a caller gets "one hull" either way.
    fun sendOneFewer() = apply {
        test.onNodeWithTag(DispatchTestTags.SHIPS_FEWER).performClick()
        test.waitForIdle()
    }

    fun sendTheRun() = apply {
        test.onNodeWithTag(DispatchTestTags.SEND).performClick()
        test.waitForIdle()
    }

    // The Shipyard's one control. Reached by tag rather than by its word, because "Build" is a
    // verb the app uses on more than one surface and an unscoped query would be ambiguous the day a
    // second one appears.
    fun buyAHull() = apply {
        buyAHull(ShipType.SKIFF)
    }

    // Overloaded rather than widened, so the no-argument call keeps reading as "the hull" on the
    // tests written before there were two — and so a caller has to name the hull the moment naming
    // it is the point.
    fun buyAHull(type: ShipType) = apply {
        test.onNodeWithTag(ShipyardTestTags.action(type), useUnmergedTree = true).performScrollTo().performClick()
        test.waitForIdle()
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

    var clearCount: Int = 0
        private set

    override suspend fun replaceAll(notifications: List<LocalNotification>) {
        scheduled = notifications
    }

    override suspend fun clearDelivered() {
        clearCount++
    }
}

// The instant every launch in these tests happens at, and the reason it is a constant rather than
// `Clock.System.now()`.
//
// A launch with no save mints its galaxy from the instant it happened, so reading the real clock
// gave `app(saved = null)` a different map on every run — and a different map walks a different set
// of branches in `GalaxyGeneration`. That is invisible in the assertions, which never mention a
// world, and loud in the coverage report: behaviour branch coverage moved across the 66.85% rounding
// line between runs of identical code, and the gate that compares it to `main` has no slack, so
// pull requests failed over a number that was never about their diff.
//
// Any fixed instant would do. This one is the day the tests stopped depending on which one it was.
internal val TEST_NOW: Instant = Instant.parse("2026-08-21T12:00:00Z")

// Handwritten rather than a mocking framework, per the project's test-double convention — and there
// is nothing to configure here beyond the one instant.
private class FixedClock(private val at: Instant) : Clock {
    override fun now(): Instant = at
}

// A preferences file that has already seen this build's changelog, which is what every test that is
// not about the changelog wants. The version is read the way the app reads it — the head of the
// catalogue — so a release bump needs nothing changed here.
internal fun changelogAlreadyRead(): PreferencesStore {
    val store = PreferencesStore(InMemorySaveFile())
    runBlocking {
        store.save(
            Preferences(
                galaxyLanding = null,
                lastSeenVersion = EnglishChangelog.releases.first().version.printed,
            ),
        )
    }
    return store
}

@OptIn(ExperimentalTestApi::class)
internal fun app(
    saved: GameSnapshot?,
    // **Handed in only when a test is about what the app remembers**, which since 0.19 is a real
    // question: whether the changelog raises itself depends on the version last shown.
    //
    // **The default says this build's changelog has already been read**, and that is load-bearing
    // rather than tidy: a colony on disk with nothing remembered is an upgrade, so the sheet raises
    // itself over the whole app — which is exactly right for a player and fatal for eighteen tests
    // that then tap a control behind a scrim. Every test about something else opens a build whose
    // news has been seen; `ChangelogAppBehaviourTest` is where the other cases are driven.
    preferences: PreferencesStore = changelogAlreadyRead(),
    // The language the changelog speaks, for the one test that is about the other document. The
    // translations beside it stay English deliberately: what is being driven is the *document*, and
    // a frame in two languages at once would be asserting two things.
    changelog: ChangelogText = EnglishChangelog,
    // **A save from an older build**, and the only way to put the launch through a migration:
    // `GameStore.save` always writes the *current* schema, so `saved` above can never be a document
    // this build has to upgrade — and a migration is the one thing in a release that rewrites a save
    // a player already has.
    //
    // The blob is still produced by `GameSave.encode` and then *downgraded*, never hand-written, for
    // the reason the note below gives: a fixture that composed its own JSON produced something
    // `load` answers with null, and the app then started a brand new colony while every assertion
    // passed.
    legacy: String? = null,
    block: AppRobot.() -> Unit,
) {
    // Written *through* `GameStore` rather than encoded by hand. The store owns the save's schema
    // envelope, and a fixture that wrote its own JSON produced a blob the store could not read —
    // which `load` answers with null rather than an error, so the app quietly started a brand new
    // colony and every assertion about what the launch found passed for the wrong reason.
    val file = InMemorySaveFile()
    if (saved != null) {
        runBlocking { GameStore(file).save(saved) }
    }
    if (legacy != null) {
        runBlocking { file.write(legacy) }
    }
    val booked = RecordingNotifications()
    runDesktopComposeUiTest(width = 393, height = 852) {
        setContent {
            App(
                store = GameStore(file),
                // **In memory, and this is not tidiness.** `App`'s default reaches
                // `defaultPreferencesFile()`, which is a real file in the machine's own application
                // directory — so without this every test that opens the Galaxy tab would read the
                // developer's own landing preference and *write* to it on the first tap of the
                // switch. A behaviour test whose result depends on the machine it runs on is the one
                // failure mode the whole `SaveFile` seam exists to prevent.
                preferences = preferences,
                changelog = changelog,
                notifications = GameNotifications(booked, English),
                // Never shaken: the debug sheet is a modal over everything, and a test about what a
                // launch says must not have one open on top of it.
                shakeDetector = ShakeDetector { emptyFlow<Unit>() as Flow<Unit> },
                // See `TEST_NOW`. The one seam that stops these tests depending on the second they
                // happen to run in.
                wallClock = FixedClock(TEST_NOW),
            )
        }
        waitForIdle()
        AppRobot(this, booked).block()
    }
}
