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

    // Tall enough for three rows, the section label and the padding under them, and no taller: a
    // capture window with a field of empty background in it hides a layout change rather than
    // showing one. Three rows leave most of a phone empty by design, and that emptiness is the
    // scaffold's to draw, not this screen's.
    private fun capture(width: Int, uiState: ResearchUiState, name: String) {
        runDesktopComposeUiTest(width = width, height = 300) {
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
