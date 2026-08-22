package dev.fardavide.oltre.client.shipyard.ui

import androidx.compose.ui.test.ExperimentalTestApi
import dev.fardavide.oltre.core.ShipType
import kotlin.test.assertEquals
import org.junit.Test

// The one interaction this tab has, and the three states around it. A screenshot says what the tab
// looks like; this says what happens when a thumb lands on it.
@OptIn(ExperimentalTestApi::class)
class ShipyardScreenBehaviourTest {

    @Test
    fun `pressing Build asks for exactly the hull that was pressed`() {
        val bought = mutableListOf<ShipType>()

        shipyard(uiState = oneHullUiState, onBuild = { bought += it }) {
            buy(ShipType.SKIFF)
        }

        assertEquals(listOf(ShipType.SKIFF), bought)
    }

    @Test
    fun `a hull the colony cannot pay for says when rather than offering a dead button`() {
        shipyard(uiState = cannotAffordUiState) {
            assertWaits(ShipType.SKIFF, "in 1h 06m")
        }
    }

    @Test
    fun `the pool is on the card and the fleet is on the heading`() {
        // **The heading counts every hull and a card counts its own type**, which is the whole of
        // the split and only became observable at 0.15: until the scout there was one card, so "the
        // pool" and "the fleet" were the same six. Eight now — six skiffs and two scouts — and the
        // skiff's card still says six.
        shipyard(uiState = sixHullsUiState) {
            assertCardReads(ShipType.SKIFF, "6 owned · 1 idle · 5 away")
            assertCardReads(ShipType.SCOUT, "2 owned · 1 idle · 1 away")
            assertReads("9 hulls")
        }
    }

    @Test
    fun `at one hull the tab is a single card and a sentence`() {
        // The tab has to be honest about being small rather than dress one row up as a facility.
        // Two cards since 0.15 — the scout is what a colony buys first — so the smallest the tab
        // gets is two, and the sentence under them is unchanged.
        shipyard(uiState = oneHullUiState) {
            assertShowsCard(ShipType.SCOUT)
            assertShowsCard(ShipType.SKIFF)
            assertShowsCard(ShipType.HAULER)
            assertCardReads(ShipType.SKIFF, "1 owned · 1 idle")
            assertReads("3 hulls")
        }
    }

    @Test
    fun `the sentence naming what the hull is for survives a Slide Over window`() {
        shipyard(uiState = oneHullUiState, width = SLIDE_OVER_WIDTH) {
            assertCardReads(ShipType.SKIFF, "One berth of hold")
            assertOffersToBuild(ShipType.SKIFF)
        }
    }

    // ── The square, and the one thing a behaviour test can say about it ──────────────────────
    //
    // Which bell is drawn is a `Canvas` and carries no semantics, so it belongs to
    // `ShipyardScreenScreenshotTest` and to `watch_square.png`. What is checkable from here is where
    // the control appears, where it does not, and that pressing it names the right hull.

    @Test
    fun `pressing the square asks about exactly the hull it belongs to`() {
        val asked = mutableListOf<ShipType>()

        shipyard(uiState = askedForOrderUiState, onToggleAlert = { asked += it }) {
            tapAlert(ShipType.SKIFF)
        }

        assertEquals(listOf(ShipType.SKIFF), asked)
    }

    @Test
    fun `only the card with hulls on the slipway carries a square`() {
        // The absence of a control rather than a disabled one. In this state the skiff has three
        // hulls in the yard and the two cards either side of it have none.
        shipyard(uiState = buildingUiState) {
            assertOffersAlert(ShipType.SKIFF)
            assertNoAlert(ShipType.SCOUT)
            assertNoAlert(ShipType.HAULER)
        }
    }

    @Test
    fun `the square keeps the verb beside it rather than replacing it`() {
        // **The one thing this card does that a facility row does not**, and the square must not
        // quietly undo it: a serial yard can always be given another hull, so BUILD stays live while
        // the slipway is busy — which is exactly when the square is there to be pressed.
        shipyard(uiState = askedForEachUiState) {
            assertOffersToBuild(ShipType.SKIFF)
            assertOffersAlert(ShipType.SKIFF)
        }
    }

    @Test
    fun `the square survives a Slide Over window where it stacks under the verb`() {
        // 29dp out of a row that already carries two chips and a verb. Below the compact width the
        // pair stacks rather than squeezing the chips, and both halves still answer a thumb.
        val asked = mutableListOf<ShipType>()

        shipyard(uiState = askedForEachUiState, width = SLIDE_OVER_WIDTH, onToggleAlert = { asked += it }) {
            assertOffersToBuild(ShipType.SKIFF)
            tapAlert(ShipType.SKIFF)
        }

        assertEquals(listOf(ShipType.SKIFF), asked)
    }

    @Test
    fun `the hauler is a card you can buy rather than a promise`() {
        // The section that carried it as a dimmed promise is empty now, because the promise is kept:
        // *"the Hauler ships from slice 3 as a dimmed card carrying its one line"*, and it has.
        shipyard(uiState = sixHullsUiState) {
            assertShowsCard(ShipType.HAULER)
            assertOffersToBuild(ShipType.HAULER)
            assertCardReads(ShipType.HAULER, "Four berths of hold")
        }
    }
}
