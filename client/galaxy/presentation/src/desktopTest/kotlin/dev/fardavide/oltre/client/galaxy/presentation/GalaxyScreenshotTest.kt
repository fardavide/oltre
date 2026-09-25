package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Italian
import dev.fardavide.oltre.client.design.text.Translations
import dev.fardavide.oltre.client.dispatch.ui.DispatchTestTags
import dev.fardavide.oltre.client.galaxy.ui.DESTINATION_HEIGHT
import dev.fardavide.oltre.client.galaxy.ui.GalaxyPage
import dev.fardavide.oltre.client.galaxy.ui.PHONE_WIDTH
import dev.fardavide.oltre.client.galaxy.ui.SLIDE_OVER_WIDTH
import dev.fardavide.oltre.client.galaxy.ui.SkyScene
import dev.fardavide.oltre.client.galaxy.ui.SkyViewState
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// Every zoom the Galaxy tab has, at both widths the app is baselined for.
//
// **Each frame is the real mapper's output over a real `GameState`** — see `GalaxyFrames`. Nothing
// here performs a gesture: a screenshot renders a state and an eye, and both are passed in. The
// height is what the shell leaves a destination rather than the window, because the sky does not
// scroll: its window *is* the screen, and a generous one photographs a device that does not exist.
@OptIn(ExperimentalTestApi::class)
class GalaxyScreenshotTest {

    // ── The five depths ──────────────────────────────────────────────────────────────────────

    @Test
    fun `the universe — nine galaxies and yours`() {
        capture(frame = universeFrame, name = "galaxy_universe")
    }

    @Test
    fun `the galaxy — a spiral with the light on one arm`() {
        capture(frame = galaxyFrame, name = "galaxy_galaxy")
    }

    // The landing. 98% unsurveyed is the state the sky is in nearly always, so it is the frame that
    // had to be good first.
    @Test
    fun `the region as the tab opens`() {
        capture(frame = regionFrame, name = "galaxy_region")
    }

    @Test
    fun `the home system with its five worlds in orbit`() {
        capture(frame = systemFrame, name = "galaxy_system")
    }

    @Test
    fun `in front of home`() {
        capture(frame = worldFrame, name = "galaxy_world")
    }

    // ── The caption's other faces ────────────────────────────────────────────────────────────

    // Scrubbed off home: a different name beside a different star, and a caption offering a probe
    // rather than quoting a round trip.
    @Test
    fun `the region with an unsurveyed star selected`() {
        capture(frame = unsurveyedStarFrame, name = "galaxy_region_unsurveyed")
    }

    // **The third tier, selected.** A grain star answering, with its address for a name and the fog
    // yield where the world count would be.
    @Test
    fun `the region with a star selected past the light`() {
        capture(frame = darkStarFrame, name = "galaxy_region_uncharted")
    }

    // The flight path from home, and a caption that reads a clock rather than offering a second
    // flight.
    @Test
    fun `the region with a probe in flight`() {
        capture(frame = probeInFlightFrame, name = "galaxy_region_in_flight")
    }

    // A fortnight in: names and veins across the region.
    @Test
    fun `the region a fortnight in`() {
        capture(frame = wellTravelledFrame, name = "galaxy_region_travelled")
    }

    // The same fortnight pinched in: the worlds of every surveyed star strung across its arm, the
    // one drawing no step of the bar flies to.
    @Test
    fun `the region pinched in to the neighbourhood`() {
        capture(frame = hoodFrame, name = "galaxy_region_hood")
    }

    // The orbit view of a star past the light: the fog's word where the orbits would be.
    @Test
    fun `the system of a star past the light`() {
        capture(frame = darkSystemFrame, name = "galaxy_system_uncharted")
    }

    // The orbit view of a charted star nobody has been to: sockets where the worlds would be.
    @Test
    fun `the system of a charted star with nothing surveyed`() {
        capture(frame = socketFrame, name = "galaxy_system_sockets")
    }

    // The one frame whose caption carries the run verb.
    @Test
    fun `in front of a world a run may be sent to`() {
        capture(frame = runnableWorldFrame, name = "galaxy_world_run")
    }

    // **Halfway through the dive from the region to home**, the one frame that shows the flight
    // the design specified: 420ms, easing out, the zoom in log space and the centre in a straight
    // line, with the bar and the count line already saying where it is going. A settled frame of
    // either depth says nothing about it — and a flight nothing photographs is a flight that can
    // quietly become a cut.
    @Test
    fun `the region halfway through the dive to home`() {
        capture(frame = regionFrame, name = "galaxy_region_diving", dive = true)
    }

    // ── The slide-over width ─────────────────────────────────────────────────────────────────

    @Test
    fun `the universe in a slide-over`() {
        capture(width = SLIDE_OVER_WIDTH, frame = universeFrame, name = "galaxy_universe_slide_over")
    }

    @Test
    fun `the region in a slide-over`() {
        capture(width = SLIDE_OVER_WIDTH, frame = regionFrame, name = "galaxy_region_slide_over")
    }

    @Test
    fun `the home system in a slide-over`() {
        capture(width = SLIDE_OVER_WIDTH, frame = systemFrame, name = "galaxy_system_slide_over")
    }

    @Test
    fun `in front of home in a slide-over`() {
        capture(width = SLIDE_OVER_WIDTH, frame = worldFrame, name = "galaxy_world_slide_over")
    }

    // ── The sheet ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the dispatch sheet offering a run`() {
        captureSheet(frame = dispatchOfferFrame, name = "galaxy_dispatch")
    }

    @Test
    fun `the dispatch sheet with the bell rung`() {
        captureSheet(frame = dispatchAnnouncedFrame, name = "galaxy_dispatch_announced")
    }

    @Test
    fun `the dispatch sheet under a by-category alert setting`() {
        captureSheet(frame = dispatchByCategoryFrame, name = "galaxy_dispatch_by_category")
    }

    @Test
    fun `the dispatch sheet refusing a world nobody has surveyed`() {
        captureSheet(frame = dispatchUnsurveyedFrame, name = "galaxy_dispatch_unsurveyed")
    }

    @Test
    fun `the dispatch sheet with every hull away`() {
        captureSheet(frame = dispatchNoShipsFrame, name = "galaxy_dispatch_no_ships")
    }

    @Test
    fun `the dispatch sheet waiting on a dry vein`() {
        captureSheet(frame = dispatchWaitingFrame, name = "galaxy_dispatch_waiting")
    }

    @Test
    fun `the dispatch sheet one galaxy over`() {
        captureSheet(frame = dispatchFarFrame, name = "galaxy_dispatch_far")
    }

    @Test
    fun `the dispatch sheet with the whole deposit in the hold`() {
        captureSheet(frame = dispatchWholeDepositFrame, name = "galaxy_dispatch_whole_deposit")
    }

    @Test
    fun `the dispatch sheet asking more than the world holds`() {
        captureSheet(frame = dispatchWaitingForeverFrame, name = "galaxy_dispatch_waiting_forever")
    }

    @Test
    fun `the dispatch sheet on a worked vein`() {
        captureSheet(frame = dispatchWorkedFrame, name = "galaxy_dispatch_worked")
    }

    @Test
    fun `the dispatch sheet clamped to the idle pool`() {
        captureSheet(frame = dispatchClampedFrame, name = "galaxy_dispatch_clamped")
    }

    @Test
    fun `the dispatch sheet opening on the fleet that empties the vein`() {
        captureSheet(frame = dispatchSuggestedFrame, name = "galaxy_dispatch_suggested")
    }

    @Test
    fun `the dispatch sheet in a slide-over`() {
        captureSheet(width = SLIDE_OVER_WIDTH, frame = dispatchOfferFrame, name = "galaxy_dispatch_slide_over")
    }

    @Test
    fun `the dispatch sheet waiting in a slide-over`() {
        captureSheet(width = SLIDE_OVER_WIDTH, frame = dispatchWaitingFrame, name = "galaxy_dispatch_waiting_slide_over")
    }

    @Test
    fun `the dispatch sheet with every hull away in a slide-over`() {
        captureSheet(width = SLIDE_OVER_WIDTH, frame = dispatchNoShipsFrame, name = "galaxy_dispatch_no_ships_slide_over")
    }

    // ── The picker ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `the picker with a hauler and two skiffs idle`() {
        captureSheet(frame = dispatchPickerFrame, name = "galaxy_picker")
    }

    @Test
    fun `the picker in a slide-over`() {
        captureSheet(width = SLIDE_OVER_WIDTH, frame = dispatchPickerFrame, name = "galaxy_picker_slide_over")
    }

    @Test
    fun `the picker with the skiffs chosen`() {
        captureSheet(frame = dispatchPickerSkiffsFrame, name = "galaxy_picker_skiffs")
    }

    @Test
    fun `the picker narrowed by distance`() {
        captureSheet(frame = dispatchPickerNarrowedFrame, name = "galaxy_picker_narrowed")
    }

    @Test
    fun `the picker narrowed by distance in a slide-over`() {
        captureSheet(width = SLIDE_OVER_WIDTH, frame = dispatchPickerNarrowedFrame, name = "galaxy_picker_narrowed_slide_over")
    }

    @Test
    fun `the picker after the hauler moved the run`() {
        captureSheet(frame = dispatchPickerMovedFrame, name = "galaxy_picker_moved")
    }

    @Test
    fun `the picker after the hauler moved the run in a slide-over`() {
        captureSheet(width = SLIDE_OVER_WIDTH, frame = dispatchPickerMovedFrame, name = "galaxy_picker_moved_slide_over")
    }

    @Test
    fun `the picker clamped by the vein`() {
        captureSheet(frame = dispatchPickerClampedFrame, name = "galaxy_picker_clamped")
    }

    // ── Italian ──────────────────────────────────────────────────────────────────────────────
    //
    // The narrow width, because that is where a longer language runs out of room first.

    @Test
    fun `the region in a slide-over in Italian`() {
        capture(width = SLIDE_OVER_WIDTH, frame = regionFrame, name = "galaxy_region_slide_over_it", translations = Italian)
    }

    @Test
    fun `in front of home in a slide-over in Italian`() {
        capture(width = SLIDE_OVER_WIDTH, frame = worldFrame, name = "galaxy_world_slide_over_it", translations = Italian)
    }

    @Test
    fun `the dispatch sheet in a slide-over in Italian`() {
        captureSheet(width = SLIDE_OVER_WIDTH, frame = dispatchOfferFrame, name = "galaxy_dispatch_slide_over_it", translations = Italian)
    }

    @Test
    fun `the dispatch sheet waiting in a slide-over in Italian`() {
        captureSheet(width = SLIDE_OVER_WIDTH, frame = dispatchWaitingFrame, name = "galaxy_dispatch_waiting_slide_over_it", translations = Italian)
    }

    // The sheet is a popup with its own root, so the capture is of that root rather than the
    // window's: the window's would photograph the scrim and nothing under it.
    private fun captureSheet(
        width: Int = PHONE_WIDTH,
        frame: SkyFrame,
        name: String,
        translations: Translations = English,
    ) {
        runDesktopComposeUiTest(width = width, height = 852) {
            mainClock.autoAdvance = false
            setContent { OltreTheme(translations) { Surface { Page(frame) } } }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onNode(isRoot() and hasAnyDescendant(hasTestTag(DispatchTestTags.SHEET))).captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    // **English by default rather than the device's language**, which is the one thing a screenshot
    // test must not read: a baseline recorded on an Italian Mac and verified on an English runner
    // would fail on every frame, and for a reason nothing in the diff would explain. The shell reads
    // the locale; a frame is told which language it is about.
    private fun capture(
        width: Int = PHONE_WIDTH,
        height: Int = DESTINATION_HEIGHT,
        frame: SkyFrame,
        name: String,
        translations: Translations = English,
        // When set, the frame is taken mid-flight rather than settled: the dive into the frame's
        // selection is started on the first composition and the clock is wound to the middle of it.
        dive: Boolean = false,
    ) {
        runDesktopComposeUiTest(width = width, height = height) {
            mainClock.autoAdvance = false
            setContent { OltreTheme(translations) { Surface { Page(frame, dive = dive) } } }
            mainClock.advanceTimeBy(if (dive) HALF_FLIGHT_MILLIS else SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    // Every callback is empty: a screenshot renders a state, and a frame that could react to a tap
    // would be a frame whose baseline depended on where the mouse was.
    @Composable
    private fun Page(frame: SkyFrame, dive: Boolean = false) {
        val viewState = remember { SkyViewState(frame.view) }
        if (dive) {
            LaunchedEffect(Unit) { viewState.fly(SkyScene(frame.uiState).dived(frame.view)) }
        }
        GalaxyPage(
            uiState = frame.uiState,
            viewState = viewState,
            onDispatchProbe = {},
            onRun = {},
            onCloseDispatch = {},
            onSelectGathering = {},
            onSelectShips = {},
            onSelectWindow = {},
            onDispatchRun = {},
            onToggleAnnounce = {},
        )
    }

    private companion object {
        // Half of the design's 420ms, where the view is neither depth and the flight is visible.
        const val HALF_FLIGHT_MILLIS = SkyViewState.FLIGHT_MILLIS / 2L
    }
}
