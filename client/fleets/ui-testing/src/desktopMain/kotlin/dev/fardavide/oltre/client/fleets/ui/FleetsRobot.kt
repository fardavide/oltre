package dev.fardavide.oltre.client.fleets.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.dispatch.ui.DispatchTestTags
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.ResourceKind
import kotlin.time.Duration

// A behaviour test says what the player sees; the Robot owns how it is looked for.
//
// **It stopped being all assertions at 0.13.** The header here used to say there was nothing to press
// on this screen — "the honest shape of a read-only destination" — and issue #62 is exactly the
// change that made that false: a worked row is a door back to a world, and the sheet behind it is
// the one the Galaxy tab raises.
@OptIn(ExperimentalTestApi::class)
fun fleets(
    uiState: FleetsUiState,
    width: Int = PHONE_WIDTH,
    onOpenWorld: (GalaxyCoordinate) -> Unit = {},
    onCloseDispatch: () -> Unit = {},
    onSelectGathering: (ResourceKind) -> Unit = {},
    onSelectShips: (Int) -> Unit = {},
    onSelectWindow: (Duration) -> Unit = {},
    onDispatchRun: () -> Unit = {},
    onToggleAnnounce: () -> Unit = {},
    block: FleetsRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = width, height = 852) {
        setContent {
            OltreTheme {
                Surface {
                    FleetsPage(
                        uiState = uiState,
                        onOpenWorld = onOpenWorld,
                        onCloseDispatch = onCloseDispatch,
                        onSelectGathering = onSelectGathering,
                        onSelectShips = onSelectShips,
                        onSelectWindow = onSelectWindow,
                        onDispatchRun = onDispatchRun,
                        onToggleAnnounce = onToggleAnnounce,
                    )
                }
            }
        }
        FleetsRobot(this).block()
    }
}

const val PHONE_WIDTH = 393
const val SLIDE_OVER_WIDTH = 320

@OptIn(ExperimentalTestApi::class)
class FleetsRobot(private val test: ComposeUiTest) {

    // Scoped to a card, because three cards of the same shape say many of the same things — an
    // unscoped query would match several and fail on the ambiguity rather than on the assertion.
    fun assertRunReads(index: Int, text: String) = apply {
        test.onNodeWithTag(FleetsTestTags.card(index), useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText(text, substring = true)))
    }

    fun assertRunDoesNotRead(index: Int, text: String) = apply {
        test.onNodeWithTag(FleetsTestTags.card(index), useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText(text, substring = true)).not())
    }

    fun assertShowsRun(index: Int) = apply {
        test.onNodeWithTag(FleetsTestTags.card(index), useUnmergedTree = true).assertIsDisplayed()
    }

    fun assertHasNoRun(index: Int) = apply {
        test.onNodeWithTag(FleetsTestTags.card(index), useUnmergedTree = true).assertDoesNotExist()
    }

    // ── Worlds worked ───────────────────────────────────────────────────────────────────────
    //
    // Scoped to a world rather than to a position, which is the fold stated as an assertion: the
    // section is a list of worlds now, and an index would name whichever run happened to be fifth.

    fun assertWorkedReads(at: GalaxyCoordinate, text: String) = apply {
        test.onNodeWithTag(FleetsTestTags.world(at), useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText(text, substring = true)))
    }

    fun assertTheWorkedRowDoesNotRead(at: GalaxyCoordinate, text: String) = apply {
        test.onNodeWithTag(FleetsTestTags.world(at), useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText(text, substring = true)).not())
    }

    fun assertHasNoWorked(at: GalaxyCoordinate) = apply {
        test.onNodeWithTag(FleetsTestTags.world(at), useUnmergedTree = true).assertDoesNotExist()
    }

    fun tapTheWorld(at: GalaxyCoordinate) = apply {
        test.onNodeWithTag(FleetsTestTags.world(at)).performClick()
    }

    // The line with no disc. Tapping it is the assertion rather than a step — a landing with no
    // target is not a world, so in a list of worlds it is not a door.
    fun tapTheUnrecordedLine() = apply {
        test.onNodeWithTag(FleetsTestTags.UNRECORDED).performClick()
    }

    fun assertTheUnrecordedLineReads(text: String) = apply {
        test.onNodeWithTag(FleetsTestTags.UNRECORDED, useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText(text, substring = true)).or(hasText(text, substring = true)))
    }

    // ── The sheet a row raises ──────────────────────────────────────────────────────────────

    fun assertTheSheetIsUp() = apply {
        test.onNodeWithTag(DispatchTestTags.SHEET).assertIsDisplayed()
    }

    fun assertNoSheet() = apply {
        test.onNodeWithTag(DispatchTestTags.SHEET).assertDoesNotExist()
    }

    // Scoped to the sheet, because it is drawn *over* the list: an unscoped query for a world's name
    // would match the row underneath it as well.
    fun assertTheSheetReads(text: String) = apply {
        test.onNodeWithTag(DispatchTestTags.SHEET)
            .assert(hasText(text, substring = true).or(hasAnyDescendant(hasText(text, substring = true))))
    }

    fun send() = apply {
        test.onNodeWithTag(DispatchTestTags.SEND).performClick()
    }

    fun assertOffersNoRun() = apply {
        test.onNodeWithTag(DispatchTestTags.SEND).assertDoesNotExist()
    }

    // The bell beside the verb. One handle for both of the sheet's verbs, because there is one
    // control — see `DispatchTestTags.ANNOUNCE`.
    fun tapTheBell() = apply {
        test.onNodeWithTag(DispatchTestTags.ANNOUNCE).performClick()
    }

    fun assertHasNoBell() = apply {
        test.onNodeWithTag(DispatchTestTags.ANNOUNCE).assertDoesNotExist()
    }

    fun bringBack(kind: ResourceKind) = apply {
        test.onNodeWithTag(DispatchTestTags.gather(kind)).performClick()
    }

    fun sendOneMore() = apply {
        test.onNodeWithTag(DispatchTestTags.SHIPS_MORE).performClick()
    }

    // **The one that moves from a default.** The sheet opens on the whole idle pool, so `+` is at
    // its stop the moment it appears and clicking it does nothing — which is how the first version
    // of the stepper test passed without ever changing a hull count.
    fun sendOneFewer() = apply {
        test.onNodeWithTag(DispatchTestTags.SHIPS_FEWER).performClick()
    }

    fun homeIn(window: Duration) = apply {
        test.onNodeWithTag(DispatchTestTags.window(window.inWholeMinutes)).performClick()
    }

    // Substring, because a line the screen composes from several Texts is still one line to the
    // player: the section rule renders as " · 5 of 6 away" next to its label.
    fun assertReads(text: String) = apply {
        test.onNodeWithText(text, substring = true, useUnmergedTree = true).assertIsDisplayed()
    }

    fun assertNothingReads(text: String) = apply {
        test.onNodeWithText(text, substring = true, useUnmergedTree = true).assertDoesNotExist()
    }
}
