package dev.fardavide.oltre.client.shipyard.presentation

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// The three states Design specifies, at the widths it specifies them for. Nothing here performs a
// gesture: a screenshot renders a state, and the state is passed in — a `performClick` before a
// capture bakes a hover highlight into the baseline forever.
@OptIn(ExperimentalTestApi::class)
class ShipyardScreenScreenshotTest {

    // The first sitting, and the frame that has to justify the tab existing at one hull: a single
    // card, a sentence naming what the hull is for, and the argument against the purchase.
    @Test
    fun `one hull at phone width`() {
        capture(width = PHONE_WIDTH, uiState = oneHullUiState, name = "shipyard_one_hull")
    }

    @Test
    fun `one hull in a Slide Over window`() {
        capture(width = SLIDE_OVER_WIDTH, uiState = oneHullUiState, name = "shipyard_one_hull_slide_over")
    }

    // A fleet at depth: the pool has three clauses and the price has walked six rungs up the curve.
    @Test
    fun `a fleet at depth at phone width`() {
        capture(width = PHONE_WIDTH, uiState = sixHullsUiState, name = "shipyard_six_hulls")
    }

    // **The state 0.9.0 added.** The card is lit, the footer counts down, and the verb is still
    // there — a busy yard takes another order, unlike a busy facility.
    @Test
    fun `a hull on the slipway at phone width`() {
        capture(width = PHONE_WIDTH, uiState = buildingUiState, name = "shipyard_building")
    }

    @Test
    fun `a hull on the slipway in a Slide Over window`() {
        capture(width = SLIDE_OVER_WIDTH, uiState = buildingUiState, name = "shipyard_building_slide_over")
    }

    // **The state this tab owns**, and the whole reason the dispatch sheet has none: the metal chip
    // reddens and the verb becomes a ghost carrying the wait.
    @Test
    fun `a hull the colony cannot afford at phone width`() {
        capture(width = PHONE_WIDTH, uiState = cannotAffordUiState, name = "shipyard_cannot_afford")
    }

    @Test
    fun `a hull the colony cannot afford in a Slide Over window`() {
        capture(
            width = SLIDE_OVER_WIDTH,
            uiState = cannotAffordUiState,
            name = "shipyard_cannot_afford_slide_over",
        )
    }

    // Comfortably taller than the content: 32dp of padding, two 33dp labels, one ~103dp card, the
    // ~51dp footnote, the 22dp seam and one ~64dp dimmed card come to about 340dp. The screen
    // scrolls, so a window that is too short does not overflow visibly — it silently clips the last
    // card out of the baseline and asserts the truncation forever, which is how the first tab-bar
    // baseline went wrong. Erring tall costs a band of empty background; erring short costs the
    // assertion.
    private fun capture(width: Int, uiState: ShipyardUiState, name: String) {
        runDesktopComposeUiTest(width = width, height = 460) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme {
                    Surface {
                        ShipyardScreen(uiState = uiState, onBuild = {})
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
