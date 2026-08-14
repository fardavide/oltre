package dev.fardavide.oltre.client.research.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.component.RowSheetContent
import dev.fardavide.oltre.client.design.component.RowSheetUiState
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// Every state the design specifies, at both widths it specifies them for. Nothing here performs a
// gesture: a screenshot renders a state, and the state is passed in — a performClick before a
// capture bakes a hover highlight into the baseline forever.
@OptIn(ExperimentalTestApi::class)
class ResearchScreenScreenshotTest {

    @Test
    fun `before the gate at phone width`() {
        capture(width = 393, uiState = beforeTheGateUiState, name = "research_before_the_gate")
    }

    // The frame the whole design decision is for: both gates open, six rows, four of them startable
    // and starting any one of them stopping the other five.
    @Test
    fun `both branches buyable at phone width`() {
        capture(width = 393, uiState = gateOpenUiState, name = "research_gate_open")
    }

    @Test
    fun `both branches buyable in a Slide Over window`() {
        capture(width = 320, uiState = gateOpenUiState, name = "research_gate_open_slide_over")
    }

    @Test
    fun `nothing running at phone width`() {
        capture(width = 393, uiState = nothingRunningUiState, name = "research_nothing_running")
    }

    @Test
    fun `one project in flight at phone width`() {
        capture(width = 393, uiState = oneProjectInFlightUiState, name = "research_in_flight")
    }

    // 320dp is narrower than any phone and reachable since the app became a real iPad app. One
    // string changes: the effect line drops its trailing noun and the section rule shortens.
    @Test
    fun `before the gate in a Slide Over window`() {
        capture(width = 320, uiState = beforeTheGateUiState, name = "research_before_the_gate_slide_over")
    }

    @Test
    fun `nothing running in a Slide Over window`() {
        capture(width = 320, uiState = nothingRunningUiState, name = "research_nothing_running_slide_over")
    }

    @Test
    fun `one project in flight in a Slide Over window`() {
        capture(width = 320, uiState = oneProjectInFlightUiState, name = "research_in_flight_slide_over")
    }

    // The watch, on the screen that shares its one slot with the colony. Two things are in the frame
    // that are not on any other: a heading that has given its trailing slot up to name the watched
    // row, and a lit square on a row whose ghost time is about the price rather than about the slot.
    @Test
    fun `a watched row on a phone`() {
        capture(width = PHONE_WIDTH, uiState = watchedUiState, name = "research_watching_phone")
    }

    // The other half of the square, on the screen that shares its slot: a project in flight that the
    // player has asked to be told about, beside five rows that were not asked about. Nothing is added
    // to the running row but the lit square — its accent line already says when it lands.
    @Test
    fun `a subscribed project in flight on a phone`() {
        capture(width = PHONE_WIDTH, uiState = subscribedUiState, name = "research_subscribed_phone")
    }

    // ── The sheet a row opens ────────────────────────────────────────────────────────────────
    //
    // Three frames rather than one, because a sheet is not a single drawing: which of the row's
    // states opened it decides what it carries, and between them these three put every part of the
    // component in front of a baseline — prose, a pointer, a ladder, a live action, a ghost one and
    // a footer that is absent altogether.
    //
    // Captured as contents rather than through the real `ModalBottomSheet`, for the reason the
    // behaviour tests give: a baseline of what a sheet *says* has no business also depending on an
    // enter animation having settled.

    // The reading the whole component exists for: a row whose verdict is "nothing" showing its
    // arithmetic, and the only shape that names a row to buy instead.
    @Test
    fun `an inert row's sheet at phone width`() {
        captureSheet(width = PHONE_WIDTH, uiState = inertSheetUiState, name = "research_sheet_inert")
    }

    // The one sheet whose prose reflows at 320dp — three sentences of it, where the other two are
    // one and two.
    @Test
    fun `an inert row's sheet in a Slide Over window`() {
        captureSheet(
            width = SLIDE_OVER_WIDTH,
            uiState = inertSheetUiState,
            name = "research_sheet_inert_slide_over",
        )
    }

    @Test
    fun `a gating row's sheet at phone width`() {
        captureSheet(width = PHONE_WIDTH, uiState = gatedSheetUiState, name = "research_sheet_gated")
    }

    @Test
    fun `a locked row's sheet at phone width`() {
        captureSheet(width = PHONE_WIDTH, uiState = lockedSheetUiState, name = "research_sheet_locked")
    }

    // Comfortably taller than six rows plus two section labels and the seam between them, with
    // headroom. The screen scrolls, so a capture window that is too short does not overflow
    // visibly — it silently clips the last row out of the baseline and asserts the truncation
    // forever, which is exactly how the first tab-bar baseline went wrong. Erring tall costs a
    // band of empty background; erring short costs the assertion.
    //
    // **840 rather than 860, and the twenty came off a row.** Measured rather than guessed, as the
    // 860 before it was: every row is now 97dp, where a startable applied row was 106dp and a
    // startable ladder more — the verdict replaced the effect line on one branch and the effect line
    // *and* the shortlist line on the other, so the two branches are the same height for the first
    // time. The tallest frame is the watched one: 32dp of padding, two 33dp labels, six 97dp rows,
    // five 8dp gaps and the 22dp seam make 734dp, and the booked row's "→ affordable" line adds 30
    // more — 764dp. 840 leaves the 76dp of headroom the old number had.
    //
    // The screen still scrolls at 393x852, by about 50dp rather than 105; see `decisions.md`. The
    // baseline deliberately shows the whole screen anyway — it is the record of what the screen
    // *is*, not of what one window happens to reveal.
    private fun capture(width: Int, uiState: ResearchUiState, name: String) {
        runDesktopComposeUiTest(width = width, height = 840) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        ResearchScreen(
                            uiState = uiState,
                            onStartResearch = {},
                            onStartAdaptation = {},
                            onToggleTechnologyWatch = {},
                            onToggleAdaptationWatch = {},
                        )
                    }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    // One height for all four, sized to the tallest: the inert sheet at 320dp, where three
    // sentences reflow to 352dp. The locked sheet is 150dp and therefore carries 230dp of empty
    // background, which is the cheap half of the same trade the window above makes — a sheet that
    // outgrew its window would be clipped silently, and a sheet that has not is merely surrounded.
    private fun captureSheet(width: Int, uiState: RowSheetUiState, name: String) {
        runDesktopComposeUiTest(width = width, height = 380) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        RowSheetContent(uiState = uiState, onAct = {})
                    }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }
}
