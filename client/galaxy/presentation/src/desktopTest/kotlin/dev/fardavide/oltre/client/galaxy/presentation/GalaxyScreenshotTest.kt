package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.client.galaxy.ui.GalaxyPage
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

    // ── The ledger, which is what the tab opens on ───────────────────────────────────────────

    @Test
    fun `the ledger as the tab opens at phone width`() {
        capture(width = 393, height = 1400, uiState = frame(state = pinnedState), name = "galaxy_ledger")
    }

    // Five rows and an invitation. The design spends the empty half on the region rather than on an
    // apology, and this is the frame that has to earn that.
    @Test
    fun `the ledger at genesis`() {
        capture(width = 393, height = 900, uiState = frame(), name = "galaxy_ledger_genesis")
    }

    @Test
    fun `the ledger with three filters and nothing left`() {
        capture(
            width = 393,
            height = 700,
            uiState = frame(state = wellTravelledState, filters = excludingFilters),
            name = "galaxy_ledger_empty",
        )
    }

    // The survey moment: a section of the ledger rather than a layer over it, so it is photographed
    // in place rather than as a card of its own.
    @Test
    fun `the ledger with a world found overnight`() {
        capture(
            width = 393,
            height = 1400,
            uiState = frame(state = justSurveyedState),
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
            uiState = frame(state = wellTravelledState, query = name.take(6)),
            name = "galaxy_ledger_search",
        )
    }

    // ── The map, which is where you go to acquire a reading you do not have ──────────────────

    @Test
    fun `the home system at phone width`() {
        capture(
            width = 393,
            height = 1500,
            uiState = frame(view = GalaxyView.SYSTEM),
            name = "galaxy_home_system",
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

    // ── The region index, ten rows against a thousand pages ──────────────────────────────────

    @Test
    fun `the ten regions of a galaxy`() {
        capture(
            width = 393,
            height = 1500,
            uiState = frame(state = wellTravelledState, view = GalaxyView.REGIONS),
            name = "galaxy_regions",
        )
    }

    // ── 320dp, where nothing drops and the lines wrap instead ────────────────────────────────

    @Test
    fun `the ledger in a Slide Over window`() {
        capture(width = 320, height = 1600, uiState = frame(state = pinnedState), name = "galaxy_ledger_slide_over")
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

    @Test
    fun `the ten regions in a Slide Over window`() {
        capture(
            width = 320,
            height = 1700,
            uiState = frame(state = wellTravelledState, view = GalaxyView.REGIONS),
            name = "galaxy_regions_slide_over",
        )
    }

    private fun capture(width: Int, height: Int, uiState: GalaxyUiState, name: String) {
        runDesktopComposeUiTest(width = width, height = height) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        GalaxyPage(
                            uiState = uiState,
                            onSelectMode = {},
                            onQueryChange = {},
                            onToggleChip = {},
                            onCycleSort = {},
                            onSelectGalaxy = {},
                            onSelectSystem = {},
                            onOpenRegionIndex = {},
                            onOpenRegion = {},
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
}
