package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// Every verdict the design specifies, at both widths it specifies them for. Nothing here performs a
// gesture: a screenshot renders a state, and the state is passed in — a performClick before a
// capture bakes a hover highlight into the baseline forever.
@OptIn(ExperimentalTestApi::class)
class GalaxyScreenScreenshotTest {

    @Test
    fun `the home system at phone width`() {
        capture(width = 393, height = 1140, uiState = homeSystemUiState, name = "galaxy_home_system")
    }

    // The state 249 systems out of 250 are in on the day this ships. It is the screen, not a stage
    // before the screen, so it gets a baseline of its own.
    @Test
    fun `an unsurveyed system at phone width`() {
        capture(width = 393, height = 760, uiState = unsurveyedSystemUiState, name = "galaxy_unsurveyed")
    }

    @Test
    fun `every verdict at phone width`() {
        capture(width = 393, height = 1180, uiState = everyVerdictUiState, name = "galaxy_every_verdict")
    }

    // 320dp is narrower than any phone and reachable since the app became a real iPad app. The
    // blocked cards wrap and grow a line; nothing truncates and no string changes. Every frame is
    // taller here for exactly that reason.
    @Test
    fun `the home system in a Slide Over window`() {
        capture(width = 320, height = 1400, uiState = homeSystemUiState, name = "galaxy_home_system_slide_over")
    }

    @Test
    fun `an unsurveyed system in a Slide Over window`() {
        capture(width = 320, height = 840, uiState = unsurveyedSystemUiState, name = "galaxy_unsurveyed_slide_over")
    }

    @Test
    fun `every verdict in a Slide Over window`() {
        capture(width = 320, height = 1400, uiState = everyVerdictUiState, name = "galaxy_every_verdict_slide_over")
    }

    // Erring tall costs a band of empty background; erring short silently clips the last row out of
    // the baseline and asserts the truncation forever, which is how the first tab-bar baseline went
    // wrong. The screen scrolls, so a short window does not overflow visibly.
    private fun capture(width: Int, height: Int, uiState: GalaxyUiState, name: String) {
        runDesktopComposeUiTest(width = width, height = height) {
            setContent {
                OltreTheme {
                    Surface {
                        GalaxyPage(
                            uiState = uiState,
                            onSelectGalaxy = {},
                            onStepSystem = {},
                            onGoHome = {},
                        onOpenResearch = {},
                        )
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
