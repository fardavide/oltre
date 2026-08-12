package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
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
        capture(width = 393, height = 1530, uiState = homeSystemUiState, name = "galaxy_home_system")
    }

    // The state 249 systems out of 250 are in on the day this ships. It is the screen, not a stage
    // before the screen, so it gets a baseline of its own — and since 0.2.0 it is also the frame
    // that carries the offer, because a system you have not been to is the only one you can buy.
    @Test
    fun `an unsurveyed system at phone width`() {
        capture(width = 393, height = 1150, uiState = unsurveyedSystemUiState, name = "galaxy_unsurveyed")
    }

    @Test
    fun `every verdict at phone width`() {
        capture(width = 393, height = 1570, uiState = everyVerdictUiState, name = "galaxy_every_verdict")
    }

    // 320dp is narrower than any phone and reachable since the app became a real iPad app. The
    // blocked cards wrap and grow a line; nothing truncates and no string changes. Every frame is
    // taller here for exactly that reason.
    @Test
    fun `the home system in a Slide Over window`() {
        capture(width = 320, height = 1790, uiState = homeSystemUiState, name = "galaxy_home_system_slide_over")
    }

    @Test
    fun `an unsurveyed system in a Slide Over window`() {
        capture(width = 320, height = 1230, uiState = unsurveyedSystemUiState, name = "galaxy_unsurveyed_slide_over")
    }

    @Test
    fun `every verdict in a Slide Over window`() {
        capture(width = 320, height = 1790, uiState = everyVerdictUiState, name = "galaxy_every_verdict_slide_over")
    }

    // ── The probe, six states and both widths ────────────────────────────────────────────────
    //
    // `available` is the unsurveyed frame above: a fresh colony can afford its first probe, which
    // is what leaving the verb ungated buys. The five below are the ones a `GameState` cannot be
    // walked into without dispatching, advancing a clock and landing on a system the seed happens
    // to have stocked the right way — so they are written out, and only the footer differs.

    @Test
    fun `a dispatch the colony cannot afford yet`() {
        capture(width = 393, height = 1150, uiState = probeUnaffordableUiState, name = "galaxy_probe_short")
    }

    @Test
    fun `a probe in flight`() {
        capture(width = 393, height = 1170, uiState = probeInFlightUiState, name = "galaxy_probe_in_flight")
    }

    // Fifty-nine dispatches in sixty. It is the state the verb usually produces, so it gets the
    // same standing as the two that are rarer than it.
    @Test
    fun `a landing that found nothing worth taking`() {
        capture(width = 393, height = 1550, uiState = probeLandedUiState, name = "galaxy_probe_landed")
    }

    @Test
    fun `a landing that found somewhere to settle`() {
        capture(width = 393, height = 1550, uiState = probeSettleableUiState, name = "galaxy_probe_settleable")
    }

    @Test
    fun `a landing that found a world blocked on one axis`() {
        capture(width = 393, height = 1550, uiState = probeNearMissUiState, name = "galaxy_probe_near_miss")
    }

    // The only frame with an arc on it. A probe is aimed at a star and launched from home, so the
    // flight is drawn on the home map and on no other — the target system's own card counts the
    // same probe down in its footer, which is the reading you get standing at the other end.
    @Test
    fun `the home system with a probe on its way out`() {
        capture(width = 393, height = 1530, uiState = homeWithProbeOutUiState, name = "galaxy_probe_outbound")
    }

    // Nine bodies at the narrowest width the app ships at, which is where the slot numbers run out
    // of room and start interleaving. Slide Over rather than phone width deliberately: it is the
    // case that breaks first.
    @Test
    fun `a crowded system in a Slide Over window`() {
        capture(width = 320, height = 1230, uiState = crowdedSystemUiState, name = "galaxy_crowded_slide_over")
    }

    @Test
    fun `a star with nothing around it`() {
        capture(width = 393, height = 830, uiState = probeNothingToSurveyUiState, name = "galaxy_probe_empty")
    }

    // The two the width actually changes: the offer drops two words, and the ghost does not
    // abbreviate at all because "in 1h 06m" is already the shortest form of itself.
    @Test
    fun `a dispatch in a Slide Over window`() {
        capture(
            width = 320,
            height = 1230,
            uiState = unsurveyedSystemUiState,
            name = "galaxy_probe_available_slide_over",
        )
    }

    @Test
    fun `a dispatch the colony cannot afford in a Slide Over window`() {
        capture(width = 320, height = 1230, uiState = probeUnaffordableUiState, name = "galaxy_probe_short_slide_over")
    }

    // ── The dispatch sheet, its two shapes and both widths ───────────────────────────────────
    //
    // **Raised on the real screen and captured through the real sheet**, which is a shape no other
    // baseline in this project has and is worth the four lines it costs. `captureSheet` composes the
    // whole `GalaxyPage` — so what these five frames are pictures of is the sheet a tap actually
    // produces, drag handle and container included, rather than its contents rendered on their own.
    //
    // It cannot be one frame of both halves, and that is the fix stating itself: a sheet is a popup,
    // a popup is a second root, and `onRoot` fails on the ambiguity. At 0.7.0 these were pictures of
    // the whole page with a panel sitting on it, because the panel was part of the page — which is
    // exactly what made it stop above the tab bar and let a drag through.
    //
    // 852 is the delivery target's own height. Every frame above is a picture of a list that scrolls
    // and is captured tall enough not to clip it; a sheet is anchored to the bottom edge of a
    // *window*, so a 1530dp-tall capture would put it somewhere no phone ever shows it.

    @Test
    fun `the dispatch sheet on a world a colonist cannot have`() {
        captureSheet(width = PHONE_WIDTH, uiState = dispatchOfferUiState, name = "galaxy_dispatch")
    }

    // The ladder narrowed to what a nine-hour flight leaves room for, with the sentence that says
    // why. A rung is absent rather than disabled, so this frame is what "too far" looks like.
    @Test
    fun `the dispatch sheet on a world in another galaxy`() {
        captureSheet(width = PHONE_WIDTH, uiState = dispatchFarUiState, name = "galaxy_dispatch_far")
    }

    // The one refusal in the app that hands back a verb.
    @Test
    fun `the dispatch sheet on a world nobody has surveyed`() {
        captureSheet(width = PHONE_WIDTH, uiState = dispatchUnsurveyedUiState, name = "galaxy_dispatch_unsurveyed")
    }

    @Test
    fun `the dispatch sheet with every hull already away`() {
        captureSheet(width = PHONE_WIDTH, uiState = dispatchNoShipsUiState, name = "galaxy_dispatch_no_ships")
    }

    // At 320dp the head drops the lesser richness and the two long lines under the figure drop a
    // noun each — "on station" becomes "station", and the danger line keeps its number and its
    // consequence and loses the middle clause naming where the danger came from.
    @Test
    fun `the dispatch sheet in a Slide Over window`() {
        captureSheet(width = SLIDE_OVER_WIDTH, uiState = dispatchOfferUiState, name = "galaxy_dispatch_slide_over")
    }

    // Erring tall costs a band of empty background; erring short silently clips the last row out of
    // the baseline and asserts the truncation forever, which is how the first tab-bar baseline went
    // wrong. The screen scrolls, so a short window does not overflow visibly.
    //
    // Every height above went up by 210dp at 0.3.0 and by exactly that: the map block was a 76dp
    // strip of fifteen ticks and is now the 286dp orbit view. Nothing else on the screen moved.
    private fun capture(width: Int, height: Int, uiState: GalaxyUiState, name: String) {
        runDesktopComposeUiTest(width = width, height = height) {
            mainClock.autoAdvance = false
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
                            onOpenWorld = {},
                            onCloseDispatch = {},
                            onSelectGathering = {},
                            onSelectShips = {},
                            onSelectWindow = {},
                            onDispatchRun = {},
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

    // The same page, and then the *other* root. A sheet is a popup and a popup is a root of its own,
    // so `onRoot` finds two and refuses to choose; the one to photograph is named by what is inside
    // it rather than by the order the two arrive in, which is not this test's business to know.
    private fun captureSheet(width: Int, uiState: GalaxyUiState, name: String) {
        runDesktopComposeUiTest(width = width, height = 852) {
            mainClock.autoAdvance = false
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
                            onOpenWorld = {},
                            onCloseDispatch = {},
                            onSelectGathering = {},
                            onSelectShips = {},
                            onSelectWindow = {},
                            onDispatchRun = {},
                        )
                    }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onNode(isRoot() and hasAnyDescendant(hasTestTag(GalaxyTestTags.SHEET))).captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }
}
