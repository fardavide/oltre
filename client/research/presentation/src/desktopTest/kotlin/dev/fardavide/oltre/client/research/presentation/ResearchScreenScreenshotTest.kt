package dev.fardavide.oltre.client.research.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
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

    // Comfortably taller than six rows plus two section labels and the seam between them, with
    // headroom. The screen scrolls, so a capture window that is too short does not overflow
    // visibly — it silently clips the last row out of the baseline and asserts the truncation
    // forever, which is exactly how the first tab-bar baseline went wrong. Erring tall costs a
    // band of empty background; erring short costs the assertion.
    //
    // 420 was enough for one branch and is nowhere near enough for two. Measured rather than
    // guessed, because the first attempt at this number was guessed and clipped the sixth row out
    // of the baseline: a startable row is 106dp, not the 74dp the design's arithmetic used, so the
    // worst case is 32dp of padding, two 33dp labels, six 106dp rows, five 8dp gaps and the 22dp
    // seam — 788dp. 860 leaves the headroom the single-branch frame had.
    //
    // That 106dp is also why the real screen scrolls at 393x852 where the design expected it not
    // to; see `decisions.md`. The baseline deliberately shows the whole screen anyway — it is the
    // record of what the screen *is*, not of what one window happens to reveal.
    private fun capture(width: Int, uiState: ResearchUiState, name: String) {
        runDesktopComposeUiTest(width = width, height = 860) {
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
}
