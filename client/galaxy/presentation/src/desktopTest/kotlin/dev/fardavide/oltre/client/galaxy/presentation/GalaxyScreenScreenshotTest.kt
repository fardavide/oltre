package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// Every verdict and every probe state the design specifies, at both widths it specifies them for.
// Nothing here performs a gesture: a screenshot renders a state, and the state is passed in — a
// performClick before a capture bakes a hover highlight into the baseline forever.
@OptIn(ExperimentalTestApi::class)
class GalaxyScreenScreenshotTest {

    @Test
    fun `the home system at phone width`() {
        capture(width = 393, height = 1320, uiState = homeSystemUiState, name = "galaxy_home_system")
    }

    // The state 249 systems out of 250 are in on the day this ships. It is the screen, not a stage
    // before the screen, so it gets a baseline of its own — and since 0.2.0 it is also the frame
    // that carries the offer, because a system you have not been to is the only one you can buy.
    @Test
    fun `an unsurveyed system at phone width`() {
        capture(width = 393, height = 940, uiState = unsurveyedSystemUiState, name = "galaxy_unsurveyed")
    }

    @Test
    fun `every verdict at phone width`() {
        capture(width = 393, height = 1360, uiState = everyVerdictUiState, name = "galaxy_every_verdict")
    }

    // 320dp is narrower than any phone and reachable since the app became a real iPad app. The
    // blocked cards wrap and grow a line; nothing truncates and no string changes. Every frame is
    // taller here for exactly that reason.
    @Test
    fun `the home system in a Slide Over window`() {
        capture(width = 320, height = 1580, uiState = homeSystemUiState, name = "galaxy_home_system_slide_over")
    }

    @Test
    fun `an unsurveyed system in a Slide Over window`() {
        capture(width = 320, height = 1020, uiState = unsurveyedSystemUiState, name = "galaxy_unsurveyed_slide_over")
    }

    @Test
    fun `every verdict in a Slide Over window`() {
        capture(width = 320, height = 1580, uiState = everyVerdictUiState, name = "galaxy_every_verdict_slide_over")
    }

    // ── The probe, six states and both widths ────────────────────────────────────────────────
    //
    // `available` is the unsurveyed frame above: a fresh colony can afford its first probe, which
    // is what leaving the verb ungated buys. The five below are the ones a `GameState` cannot be
    // walked into without dispatching, advancing a clock and landing on a system the seed happens
    // to have stocked the right way — so they are written out, and only the footer differs.

    @Test
    fun `a dispatch the colony cannot afford yet`() {
        capture(width = 393, height = 940, uiState = probeUnaffordableUiState, name = "galaxy_probe_short")
    }

    @Test
    fun `a probe in flight`() {
        capture(width = 393, height = 960, uiState = probeInFlightUiState, name = "galaxy_probe_in_flight")
    }

    // Fifty-nine dispatches in sixty. It is the state the verb usually produces, so it gets the
    // same standing as the two that are rarer than it.
    @Test
    fun `a landing that found nothing worth taking`() {
        capture(width = 393, height = 1340, uiState = probeLandedUiState, name = "galaxy_probe_landed")
    }

    @Test
    fun `a landing that found somewhere to settle`() {
        capture(width = 393, height = 1340, uiState = probeSettleableUiState, name = "galaxy_probe_settleable")
    }

    @Test
    fun `a landing that found a world blocked on one axis`() {
        capture(width = 393, height = 1340, uiState = probeNearMissUiState, name = "galaxy_probe_near_miss")
    }

    @Test
    fun `a star with nothing around it`() {
        capture(width = 393, height = 620, uiState = probeNothingToSurveyUiState, name = "galaxy_probe_empty")
    }

    // The two the width actually changes: the offer drops two words, and the ghost does not
    // abbreviate at all because "in 1h 06m" is already the shortest form of itself.
    @Test
    fun `a dispatch in a Slide Over window`() {
        capture(
            width = 320,
            height = 1020,
            uiState = unsurveyedSystemUiState,
            name = "galaxy_probe_available_slide_over",
        )
    }

    @Test
    fun `a dispatch the colony cannot afford in a Slide Over window`() {
        capture(width = 320, height = 1020, uiState = probeUnaffordableUiState, name = "galaxy_probe_short_slide_over")
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
                            onSelectSystem = {},
                            onGoHome = {},
                            onOpenResearch = {},
                            onDispatchProbe = {},
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
