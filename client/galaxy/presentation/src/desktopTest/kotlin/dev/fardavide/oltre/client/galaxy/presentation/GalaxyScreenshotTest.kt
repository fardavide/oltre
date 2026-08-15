package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.client.galaxy.ui.GalaxyPage
import dev.fardavide.oltre.client.galaxy.ui.GalaxyTestTags
import dev.fardavide.oltre.client.galaxy.ui.GalaxyUiState
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// Every view the Galaxy tab has, at both widths the app is baselined for.
//
// **Each frame is the real mapper's output over a real `GameState`** — see `GalaxyFrames`, and the
// three thousand lines of stated fixtures it replaced. Nothing here performs a gesture: a screenshot
// renders a state, and the state is passed in.
@OptIn(ExperimentalTestApi::class)
class GalaxyScreenshotTest {

    // ── The fold, which is what the tab opens on since 0.12 ─────────────────────────────────
    //
    // **The map is 852dp tall in every one of these and never 1,400**, unlike the list frames below
    // it: the fold does not scroll, so a taller window would photograph a screen no device can
    // produce. That is the whole claim being photographed — the galaxy fits.

    @Test
    fun `the map as the tab opens at phone width`() {
        capture(width = 393, height = 852, uiState = frame(state = pinnedState), name = "galaxy_map")
    }

    // 98% unsurveyed is the state the map is in nearly always, so it is the frame that had to be good
    // first — and it is the one the old ledger was worst at, being five rows and a header.
    @Test
    fun `the map on day one with nothing surveyed`() {
        capture(width = 393, height = 852, uiState = frame(), name = "galaxy_map_genesis")
    }

    // Scrubbed off home, which is the map's own second state: a different band lit, a different name
    // beside a different star, and a caption offering a probe rather than quoting a round trip.
    @Test
    fun `the map with a star selected away from home`() {
        capture(
            width = 393,
            height = 852,
            uiState = frame(state = pinnedState, at = pinnedState.homeSelection().copy(system = 195)),
            name = "galaxy_map_selected",
        )
    }

    // The amber ring, which is the fleet strip's amber meaning what it means there.
    @Test
    fun `the map with a probe in flight`() {
        capture(width = 393, height = 852, uiState = probeInFlightMapUiState, name = "galaxy_map_in_flight")
    }

    @Test
    fun `the four galaxies at phone width`() {
        capture(
            width = 393,
            height = 852,
            uiState = frame(state = pinnedState, view = GalaxyView.UNIVERSE),
            name = "galaxy_universe",
        )
    }

    // ── The worlds list, one tap away ────────────────────────────────────────────────────────

    @Test
    fun `the ledger as the tab opens at phone width`() {
        capture(
            width = 393,
            height = 1400,
            uiState = frame(state = pinnedState, view = GalaxyView.WORLDS),
            name = "galaxy_ledger",
        )
    }

    // Five rows and an invitation. The design spends the empty half on the region rather than on an
    // apology, and this is the frame that has to earn that.
    @Test
    fun `the ledger at genesis`() {
        capture(
            width = 393,
            height = 900,
            uiState = frame(view = GalaxyView.WORLDS),
            name = "galaxy_ledger_genesis",
        )
    }

    // `galaxy_ledger_empty` was retired here rather than re-recorded. It photographed *"no world
    // matches all three"* — the state three filter chips could put the list in — and with the chips
    // gone the only remaining emptiness is the genesis one above and a query nothing answers to,
    // which the search frame already carries.

    // The survey moment: a section of the ledger rather than a layer over it, so it is photographed
    // in place rather than as a card of its own.
    @Test
    fun `the ledger with a world found overnight`() {
        capture(
            width = 393,
            height = 1400,
            uiState = frame(state = justSurveyedState, view = GalaxyView.WORLDS, seenAt = JUST_SURVEYED_SINCE),
            name = "galaxy_ledger_discovery",
        )
    }

    @Test
    fun `search returns one place`() {
        val name = wellTravelledState.let { state ->
            dev.fardavide.oltre.core.systemNameAt(
                state.galaxy.seed,
                state.galaxy.home.galaxy,
                state.galaxy.home.system,
            )
        }
        capture(
            width = 393,
            height = 900,
            uiState = frame(state = wellTravelledState, view = GalaxyView.WORLDS, query = name.take(6)),
            name = "galaxy_ledger_search",
        )
    }

    // ── The orbit page, which is where you go to acquire a reading you do not have ───────────

    @Test
    fun `the home system at phone width`() {
        capture(
            width = 393,
            // Tall enough for all seven rows. At 1500 the frame cut `Elyotis X` mid-row and slot 11
            // never rendered at all, so the two rows most likely to wrap had no baseline — a
            // screenshot that stops short asserts its own truncation forever.
            height = 1800,
            uiState = frame(view = GalaxyView.SYSTEM),
            name = "galaxy_home_system",
        )
    }

    // The arc, and the only frame that has one: a flight leaves from where it was launched, and every
    // probe in this game is launched from home. Without this the curve, its gradient and the label at
    // its faint end are drawn by nothing and asserted by nothing.
    @Test
    fun `the home system with a probe away`() {
        capture(
            width = 393,
            height = 1800,
            uiState = probeOutFromHomeUiState,
            name = "galaxy_home_system_probe_out",
        )
    }

    @Test
    fun `a system nobody has looked at`() {
        capture(
            width = 393,
            height = 1200,
            uiState = frame(view = GalaxyView.SYSTEM, at = frameState.neighbourSelection()),
            name = "galaxy_unsurveyed",
        )
    }

    // The one row on the whole screen that is neither a card nor a target: a hairline, the address,
    // the word and the effect. It is drawn by a composable no other frame reaches — one system in
    // forty carries a relay and the home system does not — so without this the demotion from accent
    // settled at 0.0.18 is asserted by a node query and by nothing that can see a colour.
    @Test
    fun `a system carrying a relay`() {
        capture(
            width = 393,
            height = 1100,
            uiState = relaySystemUiState,
            name = "galaxy_relay_system",
        )
    }

    // The region index's two baselines — `galaxy_regions` and `galaxy_regions_slide_over` — are
    // deleted rather than re-recorded. Ten names did not fit on 393dp *of one dimension*, which is
    // the measurement that justified a screen of ten rows; they fit trivially on ten bands, so the
    // measurement stands and its conclusion has expired.

    // ── 320dp, where nothing drops and the lines wrap instead ────────────────────────────────
    //
    // **The map is the same 531dp drawing here as at 393dp**, which is the one measurement this pair
    // of frames exists to keep: the content area is 587dp at 393 and 570dp at 320, so one geometry
    // fits both and there is no compact variant to drift.

    @Test
    fun `the map in a Slide Over window`() {
        capture(
            width = 320,
            height = 852,
            uiState = frame(state = pinnedState),
            name = "galaxy_map_slide_over",
        )
    }

    @Test
    fun `the four galaxies in a Slide Over window`() {
        capture(
            width = 320,
            height = 852,
            uiState = frame(state = pinnedState, view = GalaxyView.UNIVERSE),
            name = "galaxy_universe_slide_over",
        )
    }

    @Test
    fun `the ledger in a Slide Over window`() {
        capture(
            width = 320,
            height = 1600,
            uiState = frame(state = pinnedState, view = GalaxyView.WORLDS),
            name = "galaxy_ledger_slide_over",
        )
    }

    @Test
    fun `the home system in a Slide Over window`() {
        capture(
            width = 320,
            height = 1700,
            uiState = frame(view = GalaxyView.SYSTEM),
            name = "galaxy_home_system_slide_over",
        )
    }


    // ── The dispatch sheet, and the probe footer it quotes ───────────────────────────────────
    //
    // **These carried baselines before 0.11 and keep them**, which is the point: the sheet and the
    // footer are unchanged by this slice, so a moved pixel in either is a regression rather than a
    // redesign. Four of them are also the README's screens.

    @Test
    fun `the dispatch sheet as it opens`() {
        captureSheet(uiState = dispatchOfferUiState, name = "galaxy_dispatch")
    }

    @Test
    fun `the sheet refuses a world nobody has looked at and offers a probe`() {
        captureSheet(uiState = dispatchUnsurveyedUiState, name = "galaxy_dispatch_unsurveyed")
    }

    @Test
    fun `the sheet with every hull away`() {
        captureSheet(uiState = dispatchNoShipsUiState, name = "galaxy_dispatch_no_ships")
    }

    @Test
    fun `the sheet on a world that has been stripped`() {
        captureSheet(uiState = dispatchWaitingUiState, name = "galaxy_dispatch_waiting")
    }

    @Test
    fun `the sheet on a target in another galaxy`() {
        captureSheet(uiState = dispatchFarUiState, name = "galaxy_dispatch_far")
    }

    // The clamp, which is the one place the sheet prints a word where a figure would be: the fleet
    // would lift more than the ground holds, so the headline is the deposit and the slot beside it
    // says so. The clamp note underneath is earned rather than standing — it only exists when some
    // of the hulls sent are contributing nothing — so this is the frame that would catch it going
    // missing or, worse, appearing on every dispatch.
    @Test
    fun `the sheet on a fleet the world cannot fill`() {
        captureSheet(uiState = dispatchWholeDepositUiState, name = "galaxy_dispatch_whole_deposit")
    }

    // The other half of the waiting state, and the reason it is a frame of its own: the stripped
    // world above still has a date, and this ask never will. So the tile that carries "in 2d 02h"
    // is simply absent here, and the sheet ends on a sentence — the one layout in the family whose
    // last row is missing rather than different.
    @Test
    fun `the sheet on an ask no world can ever hold`() {
        captureSheet(uiState = dispatchWaitingForeverUiState, name = "galaxy_dispatch_waiting_forever")
    }

    // Between full and empty, where the deposit chips state a fraction. Both other stock words —
    // "full" and "empty" — are already photographed, so this is the third and last reading the chip
    // has, and the only one whose glyphs can be clipped by a chip sized for a word.
    @Test
    fun `the sheet on a world part worked`() {
        captureSheet(uiState = dispatchWorkedUiState, name = "galaxy_dispatch_worked")
    }

    @Test
    fun `the sheet in a Slide Over window`() {
        captureSheet(width = 320, uiState = dispatchOfferUiState, name = "galaxy_dispatch_slide_over")
    }

    @Test
    fun `a probe in flight counts down in the footer`() {
        capture(
            width = 393,
            height = 1200,
            uiState = probeInFlightUiState,
            name = "galaxy_probe_in_flight",
        )
    }

    @Test
    fun `a landed probe is a receipt`() {
        capture(width = 393, height = 1200, uiState = probeLandedUiState, name = "galaxy_probe_landed")
    }

    // Every verdict on one screen, which is the frame that would catch one of the six being drawn
    // like another.
    @Test
    fun `every verdict at phone width`() {
        capture(
            width = 393,
            height = 1600,
            uiState = everyVerdictUiState,
            name = "galaxy_every_verdict",
        )
    }

    // The sheet is a popup and a popup is a root of its own, so `onRoot` finds two and refuses to
    // choose. The one to photograph is named by what is inside it rather than by the order the two
    // arrive in.
    private fun captureSheet(width: Int = 393, uiState: GalaxyUiState, name: String) {
        runDesktopComposeUiTest(width = width, height = 852) {
            mainClock.autoAdvance = false
            setContent { OltreTheme { Surface { Page(uiState) } } }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onNode(isRoot() and hasAnyDescendant(hasTestTag(GalaxyTestTags.SHEET))).captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    private fun capture(width: Int, height: Int, uiState: GalaxyUiState, name: String) {
        runDesktopComposeUiTest(width = width, height = height) {
            mainClock.autoAdvance = false
            setContent { OltreTheme { Surface { Page(uiState) } } }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    // Every callback is empty: a screenshot renders a state, and a frame that could react to a tap
    // would be a frame whose baseline depended on where the mouse was.
    @Composable
    private fun Page(uiState: GalaxyUiState) {
        GalaxyPage(
            uiState = uiState,
            onSelectMode = {},
            onToggleScale = {},
            onQueryChange = {},
            onSelectGalaxy = {},
            onSelectSystem = {},
            onOpenSelected = {},
            onOpenMap = {},
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
