package dev.fardavide.oltre.client.shipyard.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Italian
import dev.fardavide.oltre.client.design.text.Translations
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

    // **The state with the fullest pool line had no 320dp baseline**, which is why nothing had ever
    // measured it at the width it is tightest. Added here rather than in a slice of its own because
    // the Italian frame below is only readable as a diff against it: three clauses fit in English
    // with room to spare, and the same three do not fit in Italian.
    @Test
    fun `a fleet at depth in a Slide Over window`() {
        capture(width = SLIDE_OVER_WIDTH, uiState = sixHullsUiState, name = "shipyard_six_hulls_slide_over")
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

    // ── Italian, at the width the pool line is measured for ──────────────────────────────────
    //
    // **This tab is one of only three in the repository whose baselines can see a language**, and it
    // is the one that gets there by a different route: the Galaxy and Fleets frames are the real
    // mapper's output over a real `GameState`, while `TestShipyardUiState` is hand-written and every
    // one of its thirty-three strings was built through `Strings`. So this is not a lucky frame — it
    // is what a fixture written in the catalogue's own vocabulary buys, and the pattern the other
    // eleven `ui`-module fixtures would have to follow to be worth photographing in a second
    // language at all.
    //
    // The pool line is why it is here, and it is #87's own candidate: three clauses on one row at
    // 320dp, and the width the whole card is measured against.
    //
    // **It does not fit, and the frame pair is the finding.** English reads
    // `6 owned · 1 idle · 5 away` with room to spare; Italian gets `6 totali · 1 in porto · 5…` and
    // loses the third clause to an ellipsis. Thirty-one characters against twenty-five, under a name
    // that grew from `Skiff` to `Scialuppa` — and the Italian words are already the shortest that are
    // gender-free, because a hull card's subject is whichever of two feminine and two masculine nouns
    // that card is about. This is #38 arriving where #87 said it would, and the answer is a compact
    // pool line rather than worse Italian.
    @Test
    fun `a fleet at depth in a Slide Over window in Italian`() {
        capture(
            width = SLIDE_OVER_WIDTH,
            uiState = sixHullsUiState,
            name = "shipyard_six_hulls_slide_over_it",
            translations = Italian,
        )
    }

    // The slipway, which is where the yard says the most in one window: a countdown, a queue clause
    // and the verb, under a name that grew by six characters.
    @Test
    fun `a hull on the slipway in a Slide Over window in Italian`() {
        capture(
            width = SLIDE_OVER_WIDTH,
            uiState = buildingUiState,
            name = "shipyard_building_slide_over_it",
            translations = Italian,
        )
    }

    // Comfortably taller than the content: 32dp of padding, two 33dp labels, one ~103dp card, the
    // ~51dp footnote, the 22dp seam and one ~64dp dimmed card come to about 340dp. The screen
    // scrolls, so a window that is too short does not overflow visibly — it silently clips the last
    // card out of the baseline and asserts the truncation forever, which is how the first tab-bar
    // baseline went wrong. Erring tall costs a band of empty background; erring short costs the
    // assertion.
    //
    // **English by default rather than the device's language**, which is the one thing a screenshot
    // test must not read: a baseline recorded on an Italian Mac and verified on an English runner
    // would fail on every frame, for a reason nothing in the diff would explain.
    private fun capture(
        width: Int,
        uiState: ShipyardUiState,
        name: String,
        translations: Translations = English,
    ) {
        runDesktopComposeUiTest(width = width, height = 460) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme(translations) {
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
