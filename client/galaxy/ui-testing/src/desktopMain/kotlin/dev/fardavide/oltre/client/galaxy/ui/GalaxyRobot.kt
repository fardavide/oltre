package dev.fardavide.oltre.client.galaxy.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Surface
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.swipeUp
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.ResourceKind
import kotlin.time.Duration

const val PHONE_WIDTH = 393
const val SLIDE_OVER_WIDTH = 320

// The harness and the Robot, copying `ResearchRobot` — the worked example the taxonomy points at.
// A behaviour test drives the screen through this and never queries a node in its own body.
@OptIn(ExperimentalTestApi::class)
fun galaxyPage(
    uiState: GalaxyUiState,
    width: Int = PHONE_WIDTH,
    // Hoisted into the harness for one assertion: what the page's scroll position does while a sheet
    // is up over it. Nothing else in these tests looks at it.
    scrollState: ScrollState? = null,
    onSelectGalaxy: (Int) -> Unit = {},
    onSelectSystem: (Int) -> Unit = {},
    onGoHome: () -> Unit = {},
    onSelectMode: (LedgerMode) -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onToggleChip: (LedgerFilter) -> Unit = {},
    onCycleSort: () -> Unit = {},
    onOpenRegionIndex: () -> Unit = {},
    onOpenRegion: (Int) -> Unit = {},
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
                        onSelectMode = onSelectMode,
                        onQueryChange = onQueryChange,
                        onToggleChip = onToggleChip,
                        onCycleSort = onCycleSort,
                        onOpenRegionIndex = onOpenRegionIndex,
                        onOpenRegion = onOpenRegion,
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

@OptIn(ExperimentalTestApi::class)
class GalaxyRobot(private val test: ComposeUiTest) {

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

    // ── The ledger, which is what the tab opens on since 0.11 ───────────────────────────────

    fun openTheMap() = apply {
        test.onNodeWithTag(GalaxyTestTags.mode(LedgerMode.MAP)).performClick()
    }

    fun openTheLedger() = apply {
        test.onNodeWithTag(GalaxyTestTags.mode(LedgerMode.WORLDS)).performClick()
    }

    fun search(query: String) = apply {
        test.onNodeWithTag(GalaxyTestTags.LEDGER_SEARCH).performTextInput(query)
    }

    // **By the word it prints, because nothing tags it.** `GalaxyTestTags.chip(label)` exists and no
    // composable applies it — `LedgerHead` tags the two mode pills, the search field and the sort
    // string and misses the chips — so the only handle on a filter is the string a finger lands on.
    // That is not a bad handle: the tag itself is keyed by the label for exactly this reason, *"a
    // chip's label is what a player reads and what a robot looks for"*. It is the exact string rather
    // than a substring so that a chip reading `settleable` is not also matched by a row's
    // `SETTLEABLE`, which differs only in case.
    fun toggle(chip: String) = apply {
        // **The head first, and it is not belt and braces.** A chip's own scroll parent is the
        // horizontal strip it sits in, so scrolling to a chip moves the strip and never the page —
        // and a test that has already walked down a long ledger is looking at a screen with the whole
        // head above it, where a click lands on nothing and fails as a wrong answer rather than as a
        // missed tap. The search field is the head's one tagged anchor and its scroll parent is the
        // page.
        test.onNodeWithTag(GalaxyTestTags.LEDGER_SEARCH).performScrollTo()
        test.onNodeWithText(chip).performScrollTo().performClick()
    }

    fun changeTheSort() = apply {
        test.onNodeWithTag(GalaxyTestTags.LEDGER_SORT).performScrollTo().performClick()
    }

    // The region name in the system header is the only accent string there, which is exactly what
    // makes it read as the way into the index.
    fun openTheRegionIndex() = apply {
        test.onNodeWithTag(GalaxyTestTags.REGION).performScrollTo().performClick()
    }

    fun openRegion(region: Int) = apply {
        test.onNodeWithTag(GalaxyTestTags.regionRow(region)).performScrollTo().performClick()
    }

    fun assertTheIndexIsUp() = apply {
        test.onNodeWithTag(GalaxyTestTags.REGION_INDEX).assertIsDisplayed()
    }

    // **The index is a chooser rather than a level you pass through**, so its absence is as much a
    // claim as its presence: choosing a region has to leave it, and nothing else on the tab may be
    // reached through it.
    fun assertTheIndexIsAway() = apply {
        test.onNodeWithTag(GalaxyTestTags.REGION_INDEX).assertDoesNotExist()
    }

    // Scrolls first, for `assertShowsWorld`'s reason and more of it: ten cards carrying a histogram
    // and four lines each are three screens at 393x852, so only the first two are ever above the
    // fold. A region nobody drew a card for still throws here.
    fun assertTheIndexOffers(region: Int) = apply {
        test.onNodeWithTag(GalaxyTestTags.regionRow(region)).performScrollTo().assertIsDisplayed()
    }

    // Scoped to the card, for `assertRowReads`' reason: a galaxy's ten regions are a permutation of
    // one fixed list, so "even mix" is on two cards at once and every strategy fact is on four — an
    // unscoped query for one would fail on the ambiguity rather than on the claim.
    fun assertTheRegionReads(region: Int, text: String) = apply {
        test.onNodeWithTag(GalaxyTestTags.regionRow(region)).assert(containing(text))
    }

    // **Scrolls first**, and that is the same budget `assertShowsWorld` already spends: a ledger of
    // six surveyed systems is several screens long at 393x852, so a row below the fold is still a row
    // the ledger lists. What it cannot do is invent one — a name nowhere in the list throws here.
    fun assertTheLedgerLists(name: String) = apply {
        test.onNodeWithText(name, substring = true).performScrollTo().assertIsDisplayed()
    }

    fun assertNothingIsListed(name: String) = apply {
        test.onNodeWithText(name, substring = true).assertDoesNotExist()
    }

    // The system view, absent — which is the whole of what "the tab opens on what you know" claims.
    // Its inverse is `assertTheMapIsDrawn`, and between them they are the switch.
    fun assertNoMapIsDrawn() = apply {
        test.onNodeWithTag(GalaxyTestTags.MAP).assertDoesNotExist()
    }

    // **Reading order, which is all a sort or a pin ever does to a player**: this world is now above
    // that one. Asked as a comparison rather than as an index because the ledger has no positional
    // handle — its rows are tagged by slot and rows from six systems share the fifteen slot numbers,
    // so `row(7)` names as many nodes as there are systems in the list.
    fun assertListedAbove(name: String, other: String) = apply {
        val above = test.onNodeWithText(name, substring = true).fetchSemanticsNode().positionInRoot.y
        val below = test.onNodeWithText(other, substring = true).fetchSemanticsNode().positionInRoot.y
        check(above < below) { "\"$name\" was listed at ${above}px and \"$other\" at ${below}px" }
    }

    // **A pin moves a world; it does not copy one.** `assertTheLedgerLists` would already fail on a
    // second match, but it would fail on the ambiguity rather than on the claim — this one says out
    // loud what the second row would mean.
    fun assertListedOnce(name: String) = apply {
        test.onAllNodesWithText(name, substring = true).assertCountEquals(1)
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

    // The mirror of `assertRowReads`, and the only way to pin a *subtraction* to one row: an unscoped
    // `assertNothingReads` is also satisfied by a screen that moved the string one card up.
    fun assertTheRowDoesNotRead(slot: Int, text: String) = apply {
        test.onNodeWithTag(GalaxyTestTags.row(slot)).assert(containing(text).not())
    }

    // **The row's own strings in the order they are painted**, which is what turns "leads with the
    // name" from a wish into an assertion: a row is a merged node, and merging keeps its children's
    // text in draw order. A ladder is not in this list — each one is a tap target and therefore a
    // node of its own, which is why a remedy is asserted with `assertRowReads` instead.
    fun assertTheRowReadsInOrder(slot: Int, vararg texts: String) = apply {
        val painted = test.onNodeWithTag(GalaxyTestTags.row(slot))
            .fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text)
            .orEmpty()
            .map { it.text }
        var from = 0
        for (text in texts) {
            val at = painted.drop(from).indexOfFirst { text in it }
            check(at >= 0) { "row $slot reads $painted, which has no '$text' after the line before it" }
            from += at + 1
        }
    }

    // The address under the system name, and the one reading on the page that says *where you are*.
    // Every way of going somewhere — a cell, a galaxy, a region — ends here, which is what makes it
    // the thing to assert rather than whichever control was tapped.
    fun assertTheHeaderNames(coordinate: String) = apply {
        test.onNodeWithTag(GalaxyTestTags.COORDINATE).assert(containing(coordinate))
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
