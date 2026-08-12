package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.swipeUp
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.SystemAddress
import kotlin.time.Duration
import kotlinx.datetime.TimeZone

internal const val PHONE_WIDTH = 393
internal const val SLIDE_OVER_WIDTH = 320

// The harness and the Robot, copying `ResearchRobot` — the worked example the taxonomy points at.
// A behaviour test drives the screen through this and never queries a node in its own body.
@OptIn(ExperimentalTestApi::class)
internal fun galaxyScreen(
    uiState: GalaxyUiState,
    width: Int = PHONE_WIDTH,
    // Hoisted into the harness for one assertion: what the page's scroll position does while a sheet
    // is up over it. Nothing else in these tests looks at it.
    scrollState: ScrollState? = null,
    onSelectGalaxy: (Int) -> Unit = {},
    onSelectSystem: (Int) -> Unit = {},
    onGoHome: () -> Unit = {},
    onOpenResearch: () -> Unit = {},
    onDispatchProbe: () -> Unit = {},
    onOpenWorld: (Int) -> Unit = {},
    onCloseDispatch: () -> Unit = {},
    onSelectGathering: (ResourceKind) -> Unit = {},
    onSelectShips: (Int) -> Unit = {},
    onSelectWindow: (Duration) -> Unit = {},
    onDispatchRun: () -> Unit = {},
    block: GalaxyRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = width, height = 852) {
        setContent {
            OltreTheme {
                Surface {
                    GalaxyPage(
                        uiState = uiState,
                        scrollState = scrollState ?: rememberScrollState(),
                        onSelectGalaxy = onSelectGalaxy,
                        onSelectSystem = onSelectSystem,
                        onGoHome = onGoHome,
                        onOpenResearch = onOpenResearch,
                        onDispatchProbe = onDispatchProbe,
                        onOpenWorld = onOpenWorld,
                        onCloseDispatch = onCloseDispatch,
                        onSelectGathering = onSelectGathering,
                        onSelectShips = onSelectShips,
                        onSelectWindow = onSelectWindow,
                        onDispatchRun = onDispatchRun,
                    )
                }
            }
        }
        GalaxyRobot(this).block()
    }
}

// The stateful screen, which is a different subject from the page above rather than a fuller
// version of it: *which* world has its sheet up is this feature's own state — a `remember` keyed on
// the seed and the system — so a tap that raises a sheet, and a dispatch that puts it away again,
// can only be asserted from here. Everything else hands `GalaxyPage` a frame that already has one.
@OptIn(ExperimentalTestApi::class)
internal fun galaxyScreen(
    state: GameState,
    onOpenResearch: () -> Unit = {},
    onDispatchProbe: (SystemAddress) -> Unit = {},
    onDispatchRun: (GalaxyCoordinate, ResourceKind, Ships, Duration) -> Unit = { _, _, _, _ -> },
    block: GalaxyRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = PHONE_WIDTH, height = 852) {
        setContent {
            OltreTheme {
                Surface {
                    GalaxyScreen(
                        state = state,
                        now = FIXTURE_NOW,
                        timeZone = TimeZone.UTC,
                        onOpenResearch = onOpenResearch,
                        onDispatchProbe = onDispatchProbe,
                        onDispatchRun = onDispatchRun,
                    )
                }
            }
        }
        GalaxyRobot(this).block()
    }
}

@OptIn(ExperimentalTestApi::class)
internal class GalaxyRobot(private val test: ComposeUiTest) {

    fun openGalaxy(galaxy: Int) = apply {
        test.onNodeWithTag(GalaxyTestTags.galaxy(galaxy)).performClick()
    }

    // The lens cell beside the lit one is what the ±1 stepper was, and it says what it is before
    // you tap it.
    fun openSystem(system: Int) = apply {
        test.onNodeWithTag(GalaxyTestTags.reachCell(system)).performScrollTo().performClick()
    }

    // The one verb on this screen. Present only in the two states that would actually be honoured.
    fun dispatchAProbe() = apply {
        test.onNodeWithTag(GalaxyTestTags.DISPATCH).performScrollTo().performClick()
    }

    fun assertOffersNoFlight() = apply {
        test.onNodeWithTag(GalaxyTestTags.DISPATCH).assertDoesNotExist()
    }

    fun assertTheFooterReads(text: String) = apply {
        test.onNodeWithTag(GalaxyTestTags.PROBE_FOOTER)
            .assert(hasAnyDescendant(hasText(text, substring = true)))
    }

    fun assertTheBandIsDrawn() = apply {
        test.onNodeWithTag(GalaxyTestTags.REACH_STRIP).assertIsDisplayed()
    }

    fun goHome() = apply {
        test.onNodeWithTag(GalaxyTestTags.HOME).performClick()
    }

    // The blocked row's remedy, which is a tap target again now that Research can sell it.
    fun tapTheRemedy(slot: Int, technology: AdaptationTechnology) = apply {
        test.onNodeWithTag(GalaxyTestTags.adaptation(slot, technology)).performScrollTo().performClick()
    }

    // The rest of the card, which is not one: the row belongs to the world.
    fun tapTheWorld(slot: Int) = apply {
        test.onNodeWithTag(GalaxyTestTags.row(slot)).performScrollTo().performClick()
    }

    // Scrolls first, and that is the "assume it scrolls" budget the reach band and the card footer
    // spent: the map card grew 40dp and the band added 97dp above it, so two of the home system's
    // four world rows now start below the fold at 393x852 where all four used to be on screen.
    // The rows are still there and still reachable — which is what this asserts.
    fun assertShowsWorld(slot: Int) = apply {
        test.onNodeWithTag(GalaxyTestTags.row(slot)).performScrollTo().assertIsDisplayed()
    }

    fun assertShowsNoWorld(slot: Int) = apply {
        test.onNodeWithTag(GalaxyTestTags.row(slot)).assertDoesNotExist()
    }

    // Scoped to the row, because a verdict word appears on several of them at once — an unscoped
    // query for "BLOCKED" on the home system would match three nodes and fail on the ambiguity
    // rather than on the assertion.
    fun assertRowReads(slot: Int, text: String) = apply {
        test.onNodeWithTag(GalaxyTestTags.row(slot)).assert(containing(text))
    }

    fun assertReads(text: String) = apply {
        test.onNodeWithText(text, substring = true).assertIsDisplayed()
    }

    fun assertNothingReads(text: String) = apply {
        test.onNodeWithText(text, substring = true).assertDoesNotExist()
    }

    fun assertTheMapIsDrawn() = apply {
        test.onNodeWithTag(GalaxyTestTags.MAP).assertIsDisplayed()
    }

    // ── The dispatch sheet ───────────────────────────────────────────────────────────────────

    fun assertTheSheetIsUp() = apply {
        test.onNodeWithTag(GalaxyTestTags.SHEET).assertIsDisplayed()
    }

    fun assertNoSheet() = apply {
        test.onNodeWithTag(GalaxyTestTags.SHEET).assertDoesNotExist()
    }

    // Scoped to the sheet for `assertRowReads`'s reason and then some: the sheet is drawn *over* the
    // world list, so an unscoped query for a coordinate would match the row underneath it as well.
    fun assertTheSheetReads(text: String) = apply {
        test.onNodeWithTag(GalaxyTestTags.SHEET).assert(containing(text))
    }

    fun assertTheSheetDoesNotRead(text: String) = apply {
        test.onNodeWithTag(GalaxyTestTags.SHEET).assert(containing(text).not())
    }

    // A drag that starts on the sheet, which is the gesture that told us the sheet was not one: a
    // panel parked in the page's own layout has no pointer input of its own, so the drag fell
    // through to the list behind it and the screen scrolled under the player's thumb.
    fun dragTheSheet() = apply {
        test.onNodeWithTag(GalaxyTestTags.SHEET).performTouchInput { swipeUp() }
        test.waitForIdle()
    }

    fun bringBack(kind: ResourceKind) = apply {
        test.onNodeWithTag(GalaxyTestTags.gather(kind)).performClick()
    }

    fun sendOneMore() = apply {
        test.onNodeWithTag(GalaxyTestTags.SHIPS_MORE).performClick()
    }

    fun sendOneFewer() = apply {
        test.onNodeWithTag(GalaxyTestTags.SHIPS_FEWER).performClick()
    }

    fun homeIn(window: Duration) = apply {
        test.onNodeWithTag(GalaxyTestTags.window(window.inWholeMinutes)).performClick()
    }

    fun assertNoRungFor(window: Duration) = apply {
        test.onNodeWithTag(GalaxyTestTags.window(window.inWholeMinutes)).assertDoesNotExist()
    }

    fun send() = apply {
        test.onNodeWithTag(GalaxyTestTags.SEND).performClick()
    }

    // The offer's verb, absent in both refusals — the same shape `assertOffersNoFlight` has for the
    // probe, and the same assertion: the screen and `startRun` agree about what would be honoured.
    fun assertOffersNoRun() = apply {
        test.onNodeWithTag(GalaxyTestTags.SEND).assertDoesNotExist()
    }

    fun takeTheRefusalsOffer() = apply {
        test.onNodeWithTag(GalaxyTestTags.SHEET_ACTION).performClick()
    }

    // Under the system header, and the one place the distance band is stated: it is astronomy, so
    // it is identical for all fifteen slots and belongs to the system rather than to a row.
    fun assertTheAstronomyReads(text: String) = apply {
        test.onNodeWithTag(GalaxyTestTags.ASTRONOMY).assert(containing(text))
    }
}

// **The node's own text or a descendant's, and both halves are load-bearing.** A row and the sheet
// are both `clickable` now, and a clickable node *merges* its children's semantics into itself — so
// the strings a test is looking for stop being on descendants and appear as the node's own text
// list. Asking only for a descendant was right until the row became a tap target and would silently
// go on being right for the two nodes that are not one, which is the worst shape a helper can have.
private fun containing(text: String): SemanticsMatcher =
    hasText(text, substring = true).or(hasAnyDescendant(hasText(text, substring = true)))
