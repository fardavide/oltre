package dev.fardavide.oltre.client.fleets.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// Every state the design specifies, at both widths. Nothing here performs a gesture: a screenshot
// renders a state, and the state is passed in.
//
// **The three-phase bar is what these exist for.** A colour and two hairline ticks are exactly the
// kind of thing no assertion can hold and a baseline can, which is why the first frame carries one
// run in each phase rather than three of the same.
@OptIn(ExperimentalTestApi::class)
class FleetsScreenScreenshotTest {

    @Test
    fun `three runs one in each phase at phone width`() {
        capture(width = PHONE_WIDTH, uiState = threeRunsUiState, name = "fleets_three_runs")
    }

    @Test
    fun `three runs one in each phase in a Slide Over window`() {
        capture(width = SLIDE_OVER_WIDTH, uiState = threeRunsUiState, name = "fleets_three_runs_slide_over")
    }

    // The first sitting: one run out and no ledger at all, because nothing has come back yet.
    @Test
    fun `the first run at phone width`() {
        capture(width = PHONE_WIDTH, uiState = firstRunUiState, name = "fleets_first_run")
    }

    // The state with no frame behind it, drawn in the idiom the Shipyard's footnote spends. A
    // baseline is what stops it quietly becoming an empty black rectangle again.
    @Test
    fun `nothing out at phone width`() {
        capture(width = PHONE_WIDTH, uiState = nothingOutUiState, name = "fleets_nothing_out")
    }

    // Comfortably taller than the content: 32dp of padding, two 33dp labels, three ~104dp cards,
    // two 8dp gaps, the 22dp seam and four ~22dp ledger rows come to about 500dp. The screen
    // scrolls, so a window that is too short clips the last row out of the baseline silently and
    // asserts the truncation forever — erring tall costs a band of empty background.
    private fun capture(width: Int, uiState: FleetsUiState, name: String) {
        runDesktopComposeUiTest(width = width, height = 620) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        FleetsScreen(uiState = uiState)
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
