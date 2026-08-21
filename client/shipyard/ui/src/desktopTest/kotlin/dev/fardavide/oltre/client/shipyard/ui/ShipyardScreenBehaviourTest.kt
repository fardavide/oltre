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
    fun `the hull that is coming is drawn and cannot be pressed`() {
        // Design's sixth call: the Hauler ships from this slice as a dimmed card carrying its one
        // line, which is the system's rule for a thing that is coming and is not here.
        shipyard(uiState = oneHullUiState) {
            assertShowsCard(ShipType.HAULER)
            assertCardReads(ShipType.HAULER, "Four berths of hold")
            assertNothingToPress(ShipType.HAULER)
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
            assertReads("8 hulls")
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
            assertCardReads(ShipType.SKIFF, "1 owned · 1 idle")
            assertReads("2 hulls")
        }
    }

    @Test
    fun `the sentence naming what the hull is for survives a Slide Over window`() {
        shipyard(uiState = oneHullUiState, width = SLIDE_OVER_WIDTH) {
            assertCardReads(ShipType.SKIFF, "One berth of hold")
            assertOffersToBuild(ShipType.SKIFF)
        }
    }
}
