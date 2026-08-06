package dev.fardavide.oltre.client.research.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.OltreTheme
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

    // Comfortably taller than three rows plus the section label, with headroom. The screen
    // scrolls, so a capture window that is too short does not overflow visibly — it silently
    // clips the last row out of the baseline and asserts the truncation forever, which is exactly
    // how the first tab-bar baseline went wrong. Erring tall costs a band of empty background;
    // erring short costs the assertion. The emptiness is honest anyway: three rows leave most of
    // a phone empty by design, and a branch that fills the screen is a branch with filler in it.
    private fun capture(width: Int, uiState: ResearchUiState, name: String) {
        runDesktopComposeUiTest(width = width, height = 420) {
            setContent {
                OltreTheme {
                    Surface {
                        ResearchScreen(uiState = uiState, onStartResearch = {})
                    }
                }
            }
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }
}
