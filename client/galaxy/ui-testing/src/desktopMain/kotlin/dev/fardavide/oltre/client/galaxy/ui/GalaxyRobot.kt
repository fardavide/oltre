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
import androidx.compose.ui.test.click
import androidx.compose.ui.test.down
import androidx.compose.ui.test.up
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
import androidx.compose.ui.geometry.Offset
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.dispatch.ui.DispatchTestTags
import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.ResourceKind
import kotlin.time.Duration

const val PHONE_WIDTH = 393
const val SLIDE_OVER_WIDTH = 320

// Measured off the shipped 0.12.0 screenshot rather than computed: an iPhone at 852dp leaves a
// destination about 650dp once the rail, the tab bar and the safe areas are paid for. Every frame and
// every behaviour test that does not say otherwise gets that, because a screen the device cannot
// produce is a screen no test should be asserting about.
const val DESTINATION_HEIGHT = 650

// The harness and the Robot, copying `ResearchRobot` — the worked example the taxonomy points at.
// A behaviour test drives the screen through this and never queries a node in its own body.
@OptIn(ExperimentalTestApi::class)
fun galaxyPage(
    uiState: GalaxyUiState,
    width: Int = PHONE_WIDTH,
    // **What the shell actually leaves a destination**, not what the window is. A 393x852 phone pays
    // 55dp of resource rail, 52dp of tab bar and two safe-area insets before a screen sees any of it,
    // and a harness that hands the page the whole window asserts a layout no device can produce. That
    // is how 0.12.0 shipped a map whose caption was off the bottom of the screen.
    height: Int = DESTINATION_HEIGHT,
    // Hoisted into the harness for one assertion: what the page's scroll position does while a sheet
    // is up over it. Nothing else in these tests looks at it.
    scrollState: ScrollState? = null,
    onSelectGalaxy: (Int) -> Unit = {},
    onSelectSystem: (Int) -> Unit = {},
    onGoHome: () -> Unit = {},
    onSelectMode: (LedgerMode) -> Unit = {},
    onToggleScale: () -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onOpenSelected: () -> Unit = {},
    onOpenMap: () -> Unit = {},
    onOpenResearch: () -> Unit = {},
    onDispatchProbe: () -> Unit = {},
    onOpenWorld: (GalaxyCoordinate) -> Unit = {},
    onCloseDispatch: () -> Unit = {},
    onSelectGathering: (ResourceKind) -> Unit = {},
    onSelectShips: (Int) -> Unit = {},
    onSelectWindow: (Duration) -> Unit = {},
    onDispatchRun: () -> Unit = {},
    block: GalaxyRobot.() -> Unit,
) {
    runDesktopComposeUiTest(width = width, height = height) {
        setContent {
            OltreTheme {
                Surface {
                    GalaxyPage(
                        uiState = uiState,
                        scrollState = scrollState ?: rememberScrollState(),
                        onSelectMode = onSelectMode,
                        onToggleScale = onToggleScale,
                        onQueryChange = onQueryChange,
                        onOpenSelected = onOpenSelected,
                        onOpenMap = onOpenMap,
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

    // **A star is not a tap target and is not meant to be**, which is the whole selection model: the
    // fold is one 44dp-tall scrub surface and the caption under it is what a finger acts on. So a
    // robot picks a system the way a thumb does — by landing on the drawing where that system is
    // drawn — and `MapGeometry` is what turns the index into the place.
    fun scrubTo(system: Int) = apply {
        val node = test.onNodeWithTag(GalaxyTestTags.GALAXY_MAP).fetchSemanticsNode()
        val scale = test.density.density
        test.onNodeWithTag(GalaxyTestTags.GALAXY_MAP).performTouchInput {
            click(
                mapPointOf(
                    system = system,
                    widthDp = node.size.width / scale,
                    heightDp = node.size.height / scale,
                    scale = scale,
                ),
            )
        }
    }

    // The bar under the fold: the map's one readout, and the tab's one real push.
    fun openTheSelectedSystem() = apply {
        test.onNodeWithTag(GalaxyTestTags.CAPTION).performClick()
    }

    fun assertTheCaptionReads(text: String) = apply {
        test.onNodeWithTag(GalaxyTestTags.CAPTION).assert(containing(text))
    }

    // Present exactly when a probe would be honoured, which is the same claim `assertOffersNoFlight`
    // makes for the orbit page's footer.
    fun dispatchAProbeFromTheMap() = apply {
        test.onNodeWithTag(GalaxyTestTags.CAPTION_ACTION).performClick()
    }

    fun assertTheCaptionOffersNoProbe() = apply {
        test.onNodeWithTag(GalaxyTestTags.CAPTION_ACTION).assertDoesNotExist()
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

    // ── The fold, which is what the tab opens on since 0.12 ─────────────────────────────────

    fun assertTheGalaxyIsDrawn() = apply {
        test.onNodeWithTag(GalaxyTestTags.GALAXY_MAP).assertIsDisplayed()
    }

    fun assertNoGalaxyIsDrawn() = apply {
        test.onNodeWithTag(GalaxyTestTags.GALAXY_MAP).assertDoesNotExist()
    }

    // The chip at the right of the map's head — one gesture up, one back down, no stack.
    fun toggleTheScale() = apply {
        test.onNodeWithTag(GalaxyTestTags.SCALE_CHIP).performClick()
    }

    fun assertTheUniverseIsUp() = apply {
        test.onNodeWithTag(GalaxyTestTags.UNIVERSE).assertIsDisplayed()
    }

    fun assertTheUniverseIsAway() = apply {
        test.onNodeWithTag(GalaxyTestTags.UNIVERSE).assertDoesNotExist()
    }

    fun chooseGalaxy(galaxy: Int) = apply {
        test.onNodeWithTag(GalaxyTestTags.disc(galaxy)).performClick()
    }

    fun assertTheDiscReads(galaxy: Int, text: String) = apply {
        test.onNodeWithTag(GalaxyTestTags.disc(galaxy)).assert(containing(text))
    }


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
    // The region name in the system header is the only accent string there, which is exactly what
    // makes it read as the way back out to the fold — framed on the system you were reading, which is
    // where it used to open the region index.
    fun openTheMapFromTheHeader() = apply {
        test.onNodeWithTag(GalaxyTestTags.REGION).performScrollTo().performClick()
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
    fun assertNoSystemIsDrawn() = apply {
        test.onNodeWithTag(GalaxyTestTags.SYSTEM_MAP).assertDoesNotExist()
    }

    // **Reading order, which is all a sort or a pin ever does to a player**: this world is now above
    // that one. Asked as a comparison rather than as an index because the ledger has no positional
    // handle — a row's tag names the world it draws and says nothing about where in the list it sits.
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
    fun tapTheRemedy(at: GalaxyCoordinate, technology: AdaptationTechnology) = apply {
        test.onNodeWithTag(GalaxyTestTags.adaptation(at, technology)).performScrollTo().performClick()
    }

    // The rest of the card, which is not one: the row belongs to the world.
    fun tapTheWorld(at: GalaxyCoordinate) = apply {
        test.onNodeWithTag(GalaxyTestTags.row(at)).performScrollTo().performClick()
    }

    // Scrolls first, and that is the "assume it scrolls" budget the reach band and the card footer
    // spent: the map card grew 40dp and the band added 97dp above it, so two of the home system's
    // four world rows now start below the fold at 393x852 where all four used to be on screen.
    // The rows are still there and still reachable — which is what this asserts.
    fun assertShowsWorld(at: GalaxyCoordinate) = apply {
        test.onNodeWithTag(GalaxyTestTags.row(at)).performScrollTo().assertIsDisplayed()
    }

    fun assertShowsNoWorld(at: GalaxyCoordinate) = apply {
        test.onNodeWithTag(GalaxyTestTags.row(at)).assertDoesNotExist()
    }

    // Scoped to the row, because a verdict word appears on several of them at once — an unscoped
    // query for "BLOCKED" on the home system would match three nodes and fail on the ambiguity
    // rather than on the assertion.
    fun assertRowReads(at: GalaxyCoordinate, text: String) = apply {
        test.onNodeWithTag(GalaxyTestTags.row(at)).assert(containing(text))
    }

    // The mirror of `assertRowReads`, and the only way to pin a *subtraction* to one row: an unscoped
    // `assertNothingReads` is also satisfied by a screen that moved the string one card up.
    fun assertTheRowDoesNotRead(at: GalaxyCoordinate, text: String) = apply {
        test.onNodeWithTag(GalaxyTestTags.row(at)).assert(containing(text).not())
    }

    // **The row's own strings in the order they are painted**, which is what turns "leads with the
    // name" from a wish into an assertion: a row is a merged node, and merging keeps its children's
    // text in draw order. A ladder is not in this list — each one is a tap target and therefore a
    // node of its own, which is why a remedy is asserted with `assertRowReads` instead.
    fun assertTheRowReadsInOrder(at: GalaxyCoordinate, vararg texts: String) = apply {
        val painted = test.onNodeWithTag(GalaxyTestTags.row(at))
            .fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.Text)
            .orEmpty()
            .map { it.text }
        var from = 0
        for (text in texts) {
            val found = painted.drop(from).indexOfFirst { text in it }
            check(found >= 0) { "row $at reads $painted, which has no '$text' after the line before it" }
            from += found + 1
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

    fun assertTheSystemIsDrawn() = apply {
        test.onNodeWithTag(GalaxyTestTags.SYSTEM_MAP).assertIsDisplayed()
    }

    // ── The dispatch sheet ───────────────────────────────────────────────────────────────────

    fun assertTheSheetIsUp() = apply {
        test.onNodeWithTag(DispatchTestTags.SHEET).assertIsDisplayed()
    }

    fun assertNoSheet() = apply {
        test.onNodeWithTag(DispatchTestTags.SHEET).assertDoesNotExist()
    }

    // Scoped to the sheet for `assertRowReads`'s reason and then some: the sheet is drawn *over* the
    // world list, so an unscoped query for a coordinate would match the row underneath it as well.
    fun assertTheSheetReads(text: String) = apply {
        test.onNodeWithTag(DispatchTestTags.SHEET).assert(containing(text))
    }

    fun assertTheSheetDoesNotRead(text: String) = apply {
        test.onNodeWithTag(DispatchTestTags.SHEET).assert(containing(text).not())
    }

    // A drag that starts on the sheet, which is the gesture that told us the sheet was not one: a
    // panel parked in the page's own layout has no pointer input of its own, so the drag fell
    // through to the list behind it and the screen scrolled under the player's thumb.
    fun dragTheSheet() = apply {
        test.onNodeWithTag(DispatchTestTags.SHEET).performTouchInput { swipeUp() }
        test.waitForIdle()
    }

    fun bringBack(kind: ResourceKind) = apply {
        test.onNodeWithTag(DispatchTestTags.gather(kind)).performClick()
    }

    fun sendOneMore() = apply {
        test.onNodeWithTag(DispatchTestTags.SHIPS_MORE).performClick()
    }

    fun sendOneFewer() = apply {
        test.onNodeWithTag(DispatchTestTags.SHIPS_FEWER).performClick()
    }

    // A finger left on a stepper, which is a different gesture from a tap rather than a slow one:
    // past the hold the control repeats on its own and the release stops adding a step of its own.
    // **The down and the up are two injections with the clock advanced between them**, because a
    // single `performTouchInput` block cannot hold a pointer while virtual time passes.
    fun holdSendFewer(millis: Long) = apply {
        holdStepper(tag = DispatchTestTags.SHIPS_FEWER, millis = millis)
    }

    fun holdSendMore(millis: Long) = apply {
        holdStepper(tag = DispatchTestTags.SHIPS_MORE, millis = millis)
    }

    private fun holdStepper(tag: String, millis: Long) {
        test.onNodeWithTag(tag).performTouchInput { down(center) }
        test.mainClock.advanceTimeBy(millis)
        test.onNodeWithTag(tag).performTouchInput { up() }
        test.waitForIdle()
    }

    fun homeIn(window: Duration) = apply {
        test.onNodeWithTag(DispatchTestTags.window(window.inWholeMinutes)).performClick()
    }

    fun assertNoRungFor(window: Duration) = apply {
        test.onNodeWithTag(DispatchTestTags.window(window.inWholeMinutes)).assertDoesNotExist()
    }

    fun send() = apply {
        test.onNodeWithTag(DispatchTestTags.SEND).performClick()
    }

    // The offer's verb, absent in both refusals — the same shape `assertOffersNoFlight` has for the
    // probe, and the same assertion: the screen and `startRun` agree about what would be honoured.
    fun assertOffersNoRun() = apply {
        test.onNodeWithTag(DispatchTestTags.SEND).assertDoesNotExist()
    }

    fun takeTheRefusalsOffer() = apply {
        test.onNodeWithTag(DispatchTestTags.SHEET_ACTION).performClick()
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

// Where a system is drawn, in the map node's own pixels. The robot has to do this arithmetic because
// the drawing does: `MapGeometry` places a star from its index, and a test that guessed at a
// coordinate would be asserting against a guess rather than against the fold.
//
// **The band height comes off the node rather than off the constant**, for the reason the fold itself
// scales: a map given less than 531dp folds into shorter bands, and a robot still reading the design
// figure would tap three bands out at the bottom of a squeezed screen — which is exactly the class of
// error the scaling was introduced to fix.
private fun mapPointOf(system: Int, widthDp: Float, heightDp: Float, scale: Float): Offset {
    val band = MapGeometry.bandHeightOf(heightDp)
    val span = widthDp - MapGeometry.INSET_DP * 2f
    val laneMid = MapGeometry.laneMidOf(
        band = MapGeometry.bandOf(system),
        labelRow = MapGeometry.LABEL_ROW_DP,
        lane = band - MapGeometry.LABEL_ROW_DP - MapGeometry.BAND_GAP_DP,
        gap = MapGeometry.BAND_GAP_DP,
    )
    return Offset(
        x = MapGeometry.xOf(system = system, span = span, inset = MapGeometry.INSET_DP) * scale,
        y = laneMid * scale,
    )
}
