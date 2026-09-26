package dev.fardavide.oltre.client.galaxy.ui

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.down
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.up
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.dispatch.ui.DispatchTestTags
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.SystemAddress
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration

const val PHONE_WIDTH = 393
const val SLIDE_OVER_WIDTH = 320

// What one notch of a wheel scrolls, in the pixels the desktop reports it in — and so, through
// `SkyViewState.WHEEL_STEP`, what one notch is worth in zoom.
const val WHEEL_NOTCH = 120f

// Measured off the shipped 0.12.0 screenshot rather than computed: an iPhone at 852dp leaves a
// destination about 650dp once the rail, the tab bar and the safe areas are paid for. Every frame and
// every behaviour test that does not say otherwise gets that, because a screen the device cannot
// produce is a screen no test should be asserting about.
//
// **612 since 0.16.0, and the 38dp came off the top.** The player strip is a fourth piece of chrome
// above the rail, so a destination is that much shorter than it was — and this constant is the whole
// reason the suite knows. It is hand-derived, which is exactly what made it dangerous once: 0.12.0
// shipped a map whose only control was off the bottom of the screen while every galaxy frame stayed
// green, because the number the frames were captured at described a device that did not exist. A
// slice that adds or removes chrome moves this in the same commit or it repeats that.
// **590 since 0.21, and the 22dp came off the top for the second time.** The offline chrome line sits
// between the rail and the destination whenever the server has not answered, which is the same shape
// the player strip was at 0.16 — a piece of chrome above the content, costing every screen the same
// height. Moved in the commit that adds it, which is the whole of what the paragraph above asks for.
//
// **It is the *offline* height and it is the one the frames use**, which is a deliberate choice rather
// than an oversight: the shorter destination is the one that can lose a control off the bottom, so the
// suite measures the case that can fail. A colony with signal has 612dp and 22 more to spare.
const val DESTINATION_HEIGHT = 590

// **Where a page harness opens: the region about home, framed on your star.** The same landing
// `GalaxyScreen` takes, stated once here for every frame that does not say otherwise.
fun SkyUiState.landing(): SkyView {
    val scene = SkyScene(this)
    val star = scene.positionOf(SystemAddress.of(home))
    return SkyView(
        ppu = SkyGeometry.ppuOf(SkyDepth.REGION),
        centreX = star.x,
        centreY = star.y,
        selection = SkySelection.System(SystemAddress.of(home)),
    )
}

// The harness and the Robot, copying `ResearchRobot` — the worked example the taxonomy points at.
// A behaviour test drives the screen through this and never queries a node in its own body.
//
// **A page harness holds one frame**, so what it can assert is what one frame affords: the words
// on it, the verbs it carries, and where a gesture leaves the eye. A tap that changes the words —
// selecting another star re-derives the caption from the save — is the stateful screen's to show,
// and `:client:galaxy:presentation` has the harness for it.
@OptIn(ExperimentalTestApi::class)
fun galaxyPage(
    uiState: SkyUiState,
    view: SkyView = uiState.landing(),
    width: Int = PHONE_WIDTH,
    // **What the shell actually leaves a destination**, not what the window is. A 393x852 phone pays
    // 55dp of resource rail, 52dp of tab bar and two safe-area insets before a screen sees any of it,
    // and a harness that hands the page the whole window asserts a layout no device can produce. That
    // is how 0.12.0 shipped a map whose caption was off the bottom of the screen.
    height: Int = DESTINATION_HEIGHT,
    onDispatchProbe: () -> Unit = {},
    onRun: () -> Unit = {},
    onCloseDispatch: () -> Unit = {},
    onSelectGathering: (ResourceKind) -> Unit = {},
    onSelectShips: (Int) -> Unit = {},
    onSelectWindow: (Duration) -> Unit = {},
    onDispatchRun: () -> Unit = {},
    onToggleAnnounce: () -> Unit = {},
    block: GalaxyRobot.() -> Unit,
) {
    val viewState = SkyViewState(view)
    galaxyContent(width = width, height = height, viewState = viewState, block = block) {
        GalaxyPage(
            uiState = uiState,
            viewState = viewState,
            onDispatchProbe = onDispatchProbe,
            onRun = onRun,
            onCloseDispatch = onCloseDispatch,
            onSelectGathering = onSelectGathering,
            onSelectShips = onSelectShips,
            onSelectWindow = onSelectWindow,
            onDispatchRun = onDispatchRun,
            onToggleAnnounce = onToggleAnnounce,
        )
    }
}

// The half of the harness that puts anything on the glass, for the stateful screen's harness to
// share: theme, surface, size, and a robot over the result.
@OptIn(ExperimentalTestApi::class)
fun galaxyContent(
    width: Int = PHONE_WIDTH,
    height: Int = DESTINATION_HEIGHT,
    // The eye, when the harness holds it; the stateful screen holds its own and hands none.
    viewState: SkyViewState? = null,
    block: GalaxyRobot.() -> Unit,
    content: @Composable () -> Unit,
) {
    runDesktopComposeUiTest(width = width, height = height) {
        setContent {
            OltreTheme {
                Surface {
                    content()
                }
            }
        }
        GalaxyRobot(this, viewState).block()
    }
}

@OptIn(ExperimentalTestApi::class)
class GalaxyRobot(private val test: ComposeUiTest, private val viewState: SkyViewState?) {

    // ── The sky ──────────────────────────────────────────────────────────────────────────────

    fun assertTheSkyIsDrawn() = apply {
        test.onNodeWithTag(GalaxyTestTags.SKY).assertIsDisplayed()
    }

    // **A tap lands where the thing is drawn**, which is the whole selection model: the canvas has no
    // nodes, and the anchors laid over it are sizeless marks at every tappable thing at this depth.
    // Clicking one puts the pointer on the canvas underneath, so the tap is the same tap a thumb
    // makes. A second tap on the selection dives; the flight is 420ms and `waitForIdle` runs it out.
    fun tapGalaxy(galaxy: Int) = apply {
        test.onNodeWithTag(GalaxyTestTags.galaxy(galaxy)).performClick()
        test.waitForIdle()
    }

    fun tapRegion(galaxy: Int, region: Int) = apply {
        test.onNodeWithTag(GalaxyTestTags.region(galaxy, region)).performClick()
        test.waitForIdle()
    }

    fun tapStar(at: SystemAddress) = apply {
        test.onNodeWithTag(GalaxyTestTags.system(at.galaxy, at.system)).performClick()
        test.waitForIdle()
    }

    fun tapWorld(at: GalaxyCoordinate) = apply {
        test.onNodeWithTag(GalaxyTestTags.world(at)).performClick()
        test.waitForIdle()
    }

    fun assertNothingToTapAt(at: GalaxyCoordinate) = apply {
        test.onNodeWithTag(GalaxyTestTags.world(at)).assertDoesNotExist()
    }

    // A pinch about the middle of the sky, and `factor` above one zooms in. **The fingers turn
    // before they part**: a transform gesture spends its first movement passing touch slop and
    // applies none of it, so a pinch that opened from a hair's width would lose most of its zoom
    // to the slop. A half turn about the middle passes the slop as a rotation — which the sky
    // ignores, and which moves neither the centroid nor the span — so the zoom the sky then
    // receives is the whole `factor`, exactly.
    fun pinch(factor: Float) = apply {
        test.onNodeWithTag(GalaxyTestTags.SKY).performTouchInput {
            val span = height / 4f
            val from = if (factor > 1f) span / factor else span
            val to = from * factor
            fun finger(radius: Float, angle: Double): Offset =
                center + Offset((radius * sin(angle)).toFloat(), (-radius * cos(angle)).toFloat())
            down(0, finger(from, 0.0))
            down(1, finger(from, PI))
            for (i in 1..TURN_STEPS) {
                val angle = PI * i / TURN_STEPS
                moveTo(0, finger(from, angle))
                moveTo(1, finger(from, PI + angle))
            }
            for (i in 1..PINCH_STEPS) {
                val radius = from + (to - from) * i / PINCH_STEPS
                moveTo(0, finger(radius, PI))
                moveTo(1, finger(radius, 0.0))
            }
            up(0)
            up(1)
        }
        test.waitForIdle()
    }

    // A drag across the sky by a fraction of its width, rightwards when positive.
    fun pan(fractionOfWidth: Float) = apply {
        test.onNodeWithTag(GalaxyTestTags.SKY).performTouchInput {
            swipe(start = center, end = center + Offset(width * fractionOfWidth, 0f))
        }
        test.waitForIdle()
    }

    // A notch of the wheel over the middle of the sky, and `notches` above zero zooms in. **The clock
    // stops being run out**: a notch asks for a zoom and the sky eases to it, so what the eye is
    // doing a few frames later is the whole point of driving it — and every node query waits for
    // idle first, which on an automatic clock would land the flight before a second notch could
    // catch it in the air. From the first notch on, `after` moves the clock by hand.
    fun turnTheWheel(notches: Int) = apply {
        test.mainClock.autoAdvance = false
        test.onNodeWithTag(GalaxyTestTags.SKY).performMouseInput {
            moveTo(center)
            scroll(-notches * WHEEL_NOTCH)
        }
    }

    fun after(millis: Long) = apply {
        test.mainClock.advanceTimeBy(millis)
    }

    // Two readings of the zoom a frame apart: on its way in, or arrived.
    fun assertTheZoomIsStillClimbing() = apply {
        val before = eye().ppu
        test.mainClock.advanceTimeByFrame()
        check(eye().ppu > before) { "the zoom is not climbing: it read $before and then ${eye().ppu}" }
    }

    fun assertTheZoomHasSettled() = apply {
        val before = eye().ppu
        test.mainClock.advanceTimeByFrame()
        check(eye().ppu == before) { "the zoom is still moving: it read $before and then ${eye().ppu}" }
    }

    fun assertTheEyeIsCloserThan(view: SkyView) = apply {
        check(eye().ppu > view.ppu) { "the eye is at ${eye().ppu} pixels a unit, no closer than ${view.ppu}" }
    }

    // To a tenth of a percent: a flight lands exactly where it was sent, and a zoom is a product of
    // floats.
    fun assertTheZoomIs(ppu: Float) = apply {
        check(abs(eye().ppu - ppu) <= ppu * 0.001f) { "the eye is at ${eye().ppu} pixels a unit, not $ppu" }
    }

    // ── The bar and the count ────────────────────────────────────────────────────────────────

    fun openStep(depth: SkyDepth) = apply {
        test.onNodeWithTag(GalaxyTestTags.step(depth)).performClick()
        test.waitForIdle()
    }

    fun assertTheBarReads(depth: SkyDepth, text: String) = apply {
        test.onNodeWithTag(GalaxyTestTags.step(depth)).assert(containing(text))
    }

    fun assertTheBarHasNoStep(depth: SkyDepth) = apply {
        test.onNodeWithTag(GalaxyTestTags.step(depth)).assertDoesNotExist()
    }

    // The bar scrolls, so a step can exist and be off its right edge. A step you cannot see is one
    // you cannot tap, which at the last depth is the whole address.
    fun assertTheBarShows(depth: SkyDepth) = apply {
        test.onNodeWithTag(GalaxyTestTags.step(depth)).assertIsDisplayed()
    }

    // **Read off the eye rather than off a colour**: which step is lit is a weight and a tint, and
    // neither is a semantic. The depth is derived from the zoom, so this is the same fact.
    fun assertTheDepthIs(depth: SkyDepth) = apply {
        val view = eye()
        check(view.depth == depth) { "the eye is at ${view.depth} (ppu ${view.ppu}), not $depth" }
    }

    fun assertTheSelectionIs(selection: SkySelection) = apply {
        val view = eye()
        check(view.selection == selection) { "the selection is ${view.selection}, not $selection" }
    }

    private fun eye(): SkyView =
        checkNotNull(viewState) { "the stateful screen holds its own eye; assert the bar or the caption" }.view

    fun assertTheCountReads(text: String) = apply {
        test.onNodeWithTag(GalaxyTestTags.COUNT).assert(containing(text))
    }

    // ── The caption ──────────────────────────────────────────────────────────────────────────

    // The bar at the foot: the sky's one readout, and a tap on it is the dive.
    fun openTheSelection() = apply {
        test.onNodeWithTag(GalaxyTestTags.CAPTION).performClick()
        test.waitForIdle()
    }

    fun assertTheCaptionReads(text: String) = apply {
        test.onNodeWithTag(GalaxyTestTags.CAPTION).assert(containing(text))
    }

    fun assertTheCaptionFullyDisplays(text: String) = apply {
        val layouts = mutableListOf<TextLayoutResult>()
        test.onNode(
            hasText(text, substring = true) and hasAnyAncestor(hasTestTag(GalaxyTestTags.CAPTION)),
            useUnmergedTree = true,
        ).assertIsDisplayed().performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        check(layouts.isNotEmpty()) { "the caption returned no text layout" }
        check(layouts.none { it.hasVisualOverflow }) { "the caption truncates $text" }
    }

    fun assertTheCaptionDoesNotRead(text: String) = apply {
        test.onNodeWithTag(GalaxyTestTags.CAPTION).assert(containing(text).not())
    }

    // Present exactly when the selection affords a verb the row's own tap does not: a probe to send,
    // a fleet to run, a depth to open.
    fun takeTheCaptionsVerb() = apply {
        test.onNodeWithTag(GalaxyTestTags.CAPTION_ACTION).performClick()
        test.waitForIdle()
    }

    fun assertTheCaptionOffers(verb: String) = apply {
        test.onNodeWithTag(GalaxyTestTags.CAPTION_ACTION).assert(containing(verb))
    }

    fun assertTheCaptionOffersNoVerb() = apply {
        test.onNodeWithTag(GalaxyTestTags.CAPTION_ACTION).assertDoesNotExist()
    }

    fun assertReads(text: String) = apply {
        test.onNodeWithText(text, substring = true).assertIsDisplayed()
    }

    fun assertNothingReads(text: String) = apply {
        test.onNodeWithText(text, substring = true).assertDoesNotExist()
    }

    // ── The dispatch sheet ───────────────────────────────────────────────────────────────────

    fun assertTheSheetIsUp() = apply {
        test.onNodeWithTag(DispatchTestTags.SHEET).assertIsDisplayed()
    }

    fun assertNoSheet() = apply {
        test.onNodeWithTag(DispatchTestTags.SHEET).assertDoesNotExist()
    }

    // Scoped to the sheet: the sheet is drawn *over* the sky and its caption, so an unscoped query
    // for a coordinate would match the caption underneath it as well.
    fun assertTheSheetReads(text: String) = apply {
        test.onNodeWithTag(DispatchTestTags.SHEET).assert(containing(text))
    }

    fun assertTheSheetDoesNotRead(text: String) = apply {
        test.onNodeWithTag(DispatchTestTags.SHEET).assert(containing(text).not())
    }

    // A drag that starts on the sheet, which is the gesture that told us the sheet was not one: a
    // panel parked in the page's own layout has no pointer input of its own, so the drag fell
    // through to the sky behind it and the view moved under the player's thumb.
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

    // **A rung that is drawn but not flyable with this mix.** Distinguished from an absent one by
    // the requirement under it — Design: *"Absent means never. Dim means not with these hulls."* —
    // and it is a `hasAnyDescendant` rather than a state flag because the requirement *is* the state.
    fun assertRungIsLocked(window: Duration, requirement: String) = apply {
        test.onNodeWithTag(DispatchTestTags.window(window.inWholeMinutes), useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText(requirement, substring = true)))
    }

    fun assertRungIsNotLocked(window: Duration, requirement: String) = apply {
        test.onNodeWithTag(DispatchTestTags.window(window.inWholeMinutes), useUnmergedTree = true)
            .assert(hasAnyDescendant(hasText(requirement, substring = true)).not())
    }

    // The two cells under the stepper, tapped by the hold they would fly — which is what the cell
    // sends, so the tap and the tag are one fact.
    fun sendWith(berths: Int) = apply {
        // No `performScrollTo`: the sheet is a bottom sheet rather than a scroller, and the cells
        // sit above the fold in every state that has them — the same reason `homeIn` taps a rung
        // directly. Asking to scroll raises "no parent layout with a Scroll SemanticsAction".
        test.onNodeWithTag(DispatchTestTags.hullCell(berths)).performClick()
        test.waitForIdle()
    }

    fun send() = apply {
        test.onNodeWithTag(DispatchTestTags.SEND).performClick()
    }

    // The offer's verb, absent in both refusals: the screen and `startRun` agree about what would
    // be honoured.
    fun assertOffersNoRun() = apply {
        test.onNodeWithTag(DispatchTestTags.SEND).assertDoesNotExist()
    }

    // The sheet's probe, which is the caption's verb again with a refusal over it when the sheet is
    // up on a world nobody has surveyed.
    fun dispatchAProbe() = apply {
        test.onNodeWithTag(DispatchTestTags.SHEET_ACTION).performClick()
    }

    // The refusal with nothing to send keeps the chip and puts a countdown on it, so what this reads
    // is that the chip cannot be taken: a countdown is a reading, not a control.
    fun assertOffersNoFlight() = apply {
        test.onNodeWithTag(DispatchTestTags.SHEET_ACTION).assertIsNotEnabled()
    }

    fun tapTheSheetsBell() = apply {
        test.onNodeWithTag(DispatchTestTags.ANNOUNCE).performClick()
    }

    fun assertTheSheetHasNoBell() = apply {
        test.onNodeWithTag(DispatchTestTags.ANNOUNCE).assertDoesNotExist()
    }

    private companion object {
        const val TURN_STEPS = 12
        const val PINCH_STEPS = 8
    }
}

// **The node's own text or a descendant's, and both halves are load-bearing.** The caption and the
// sheet are both `clickable`, and a clickable node *merges* its children's semantics into itself —
// so the strings a test is looking for stop being on descendants and appear as the node's own text
// list. Asking only for a descendant was right until the row became a tap target and would silently
// go on being right for the two nodes that are not one, which is the worst shape a helper can have.
private fun containing(text: String): SemanticsMatcher =
    hasText(text, substring = true).or(hasAnyDescendant(hasText(text, substring = true)))
