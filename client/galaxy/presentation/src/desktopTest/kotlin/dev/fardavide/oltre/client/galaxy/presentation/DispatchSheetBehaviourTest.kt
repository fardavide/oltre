package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.ResourceKind
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import org.junit.Test

// **The slice this test is the point of.** `startRun` landed in `core` at 0.3.0 and nothing called
// it from a finger — the verb existed, the balance existed, the save format carried it, and a player
// tapping a world got nothing at all. What was missing was this sheet, and what raises it is the row.
//
// Driven through the Robot, never through a raw node query — the shape `ResearchRobot` set.
@OptIn(ExperimentalTestApi::class)
class DispatchSheetBehaviourTest {

    @Test
    fun `tapping a world you cannot live on asks for the sheet that can send a ship there`() {
        // The whole point of the mechanic in one assertion. Hostility gates *settling* and never
        // gathering, so the commonest verdict on the map — a world a colonist is locked out of — is
        // an ordinary target for a hold. That is what stops 98% of the galaxy being a wall.
        val opened = mutableListOf<Int>()

        galaxyScreen(uiState = homeSystemUiState, onOpenWorld = { opened += it }) {
            tapTheWorld(RUNNABLE_SLOT)
        }

        assertEquals(listOf(RUNNABLE_SLOT), opened.toList())
    }

    @Test
    fun `the sheet names the world it was raised from`() {
        val coordinate = homeSystemUiState.bands
            .flatMap { it.rows }
            .first { it.slot == RUNNABLE_SLOT }
            .coordinate

        galaxyScreen(uiState = dispatchOfferUiState) {
            assertTheSheetIsUp()
            assertTheSheetReads(coordinate)
        }
    }

    @Test
    fun `sending commits the run and nothing else does`() {
        // Three controls and one verb. Touching a control changes what the run *would* be; only the
        // verb spends anything, which is why the sheet costs nothing to open and has no cancel.
        var sent = 0

        galaxyScreen(uiState = dispatchOfferUiState, onDispatchRun = { sent++ }) {
            bringBack(ResourceKind.CRYSTAL)
            sendOneMore()
            assertEquals(0, sent, "a control is a choice, not a commitment")
            send()
        }

        assertEquals(1, sent)
    }

    @Test
    fun `a run brings back one resource and never deuterium`() {
        // The exclusion is load-bearing rather than cosmetic: deuterium buys the Robotics Factory
        // and Robotics 4 opens the adaptation ladders, so a fleet that could fetch it would undercut
        // the one gate the whole mid-game hangs on. Cold worlds are deuterium worlds, which is what
        // leaves Thermal the one ladder with a prize the fleet can never take.
        val chosen = mutableListOf<ResourceKind>()

        galaxyScreen(uiState = dispatchOfferUiState, onSelectGathering = { chosen += it }) {
            assertTheSheetReads("Metal")
            assertTheSheetReads("Crystal")
            assertTheSheetDoesNotRead("Deuterium")
            bringBack(ResourceKind.CRYSTAL)
            bringBack(ResourceKind.METAL)
        }

        assertEquals(listOf(ResourceKind.CRYSTAL, ResourceKind.METAL), chosen.toList())
    }

    @Test
    fun `the sheet states what comes back and never what it costs`() {
        // Design's fourth call and it is a subtraction: §1 charges nothing per run, so there is no
        // cost line and no affordability state. The hull was the price and it is paid at the
        // Shipyard. A cost line here would be the interface inventing a price to have something to
        // say — and "cannot afford" is drawn on the tab that can actually refuse.
        val offer = assertIs<DispatchUiState.Offer>(dispatchOfferUiState.dispatch)

        galaxyScreen(uiState = dispatchOfferUiState) {
            assertTheSheetDoesNotRead("cost")
            assertTheSheetDoesNotRead("Cannot afford")
            assertTheSheetDoesNotRead("in 1h")
            // The one figure, and it is a payout rather than a price: what this run brings home, in
            // the resource the player picked, at the window they picked. Read off the offer rather
            // than typed, because the amount is `FleetBalance.cargo` to the unit.
            assertTheSheetReads(offer.figure)
        }
    }

    @Test
    fun `the sheet opens on the resource the world is richer in`() {
        // The default that saves a tap on the commonest case: you came here because the row said
        // this world is good for something, and the sheet opens on the something. Compared in the
        // generator's own units rather than in the priced basket, because the player is choosing
        // between two columns of the same number.
        val offer = assertIs<DispatchUiState.Offer>(dispatchOfferUiState.dispatch)
        val richer = if (offer.metalRichness >= offer.crystalRichness) ResourceKind.METAL else ResourceKind.CRYSTAL

        assertEquals(richer, offer.gathering)
        // ...and the head line puts that same one first, so the eye lands on it before the control
        // below repeats it.
        assertTrue(offer.head.startsWith(if (richer == ResourceKind.METAL) "metal" else "crystal"), offer.head)
    }

    @Test
    fun `a window is missing rather than dead when the trip will not fit inside it`() {
        // The only way to show "too far" without a control that refuses its own tap — and the rung
        // that vanishes is the copy: a ladder narrowing on a distant target teaches distance before
        // any sentence does. One galaxy hop is 4h 40m each way, so the three short rungs cannot
        // leave the twenty minutes on the surface that make the trip worth taking.
        //
        // **The first assertion is that there is a ladder at all**, and it is here because the first
        // version of this fixture was unsurveyed: the sheet refused before it priced anything, so
        // every `assertNoRungFor` below passed against a sheet with no rungs on it whatever.
        val far = assertIs<DispatchUiState.Offer>(dispatchFarUiState.dispatch)
        assertEquals(listOf(12.hours, 24.hours), far.windows.map { it.window })

        galaxyScreen(uiState = dispatchFarUiState) {
            assertNoRungFor(1.hours)
            assertNoRungFor(3.hours)
            assertNoRungFor(6.hours)
            homeIn(12.hours)
            homeIn(24.hours)
            // ...and the sentence that says why, so a player who never saw the full ladder can still
            // find out that one is missing.
            assertTheSheetReads("No shorter window leaves 20 minutes on the surface")
        }
        // Next door every rung is offered, because 20m out and back leaves surface time on all five
        // — and there the sentence is absent, because it would be explaining nothing.
        galaxyScreen(uiState = dispatchOfferUiState) {
            FleetBalance.WINDOWS.forEach { homeIn(it) }
            assertTheSheetDoesNotRead("No shorter window")
        }
    }

    @Test
    fun `choosing a window asks for that window`() {
        val chosen = mutableListOf<Long>()

        galaxyScreen(uiState = dispatchOfferUiState, onSelectWindow = { chosen += it.inWholeMinutes }) {
            homeIn(3.hours)
            homeIn(24.hours)
        }

        assertEquals(listOf(180L, 1440L), chosen.toList())
    }

    @Test
    fun `a world nobody has looked at refuses the run and hands back the flight that would fix it`() {
        // The one refusal in the app that returns a verb rather than a wait. It also chains the two
        // verbs: a probe used to buy a verdict and stop, and now it buys the right to send a ship —
        // so surveying acquires a second-order payoff that does not run out the way verdicts do.
        var probed = 0

        galaxyScreen(uiState = dispatchUnsurveyedUiState, onDispatchProbe = { probed++ }) {
            assertTheSheetIsUp()
            assertOffersNoRun()
            assertTheSheetReads("cannot be priced")
            takeTheRefusalsOffer()
        }

        assertEquals(1, probed)
    }

    @Test
    fun `a fleet that is entirely away refuses the run and says when a hull is back`() {
        // The pool is the idle count, so this is genesis with its one granted skiff already out. No
        // verb, because there is nothing to send — and a countdown rather than a dead button, which
        // is the idiom the unaffordable probe already spends.
        var sent = 0

        galaxyScreen(uiState = dispatchNoShipsUiState, onDispatchRun = { sent++ }) {
            assertTheSheetIsUp()
            assertOffersNoRun()
            assertTheSheetReads("away")
            takeTheRefusalsOffer()
        }

        assertEquals(0, sent, "a countdown is a reading, not a control")
    }

    @Test
    fun `home is not a target and raises nothing`() {
        // `startRun` refuses your own world outright, so the row must not offer a sheet that would
        // be refused the moment it was used. The screen and the model agree about this rather than
        // the screen finding out afterwards.
        val opened = mutableListOf<Int>()
        val homeSlot = homeSystemUiState.bands
            .flatMap { it.rows }
            .first { it.verdict is VerdictUiState.Home }
            .slot

        galaxyScreen(uiState = homeSystemUiState, onOpenWorld = { opened += it }) {
            tapTheWorld(homeSlot)
        }

        assertTrue(opened.isEmpty(), "a run cannot be sent to the world it is sent from")
    }

    @Test
    fun `a relay is a point of interest and still not a destination`() {
        // The galaxy sheet says it in as many words — the screen may label a relay, it may not be
        // tappable — and no holding mechanic exists until multiplayer. Nothing about the fleet
        // changes that; a relay is not a world and has no hold to fill.
        val opened = mutableListOf<Int>()

        galaxyScreen(uiState = everyVerdictUiState, onOpenWorld = { opened += it }) {
            tapTheWorld(3)
        }

        assertTrue(opened.isEmpty())
    }

    @Test
    fun `dismissing the sheet spends nothing`() {
        // There is no cancel button because there is nothing to cancel: the sheet is a reading until
        // the verb is tapped. Tapping away is the way out, and it commits exactly as much as never
        // having opened it.
        var closed = 0
        var sent = 0

        galaxyScreen(
            uiState = dispatchOfferUiState,
            onCloseDispatch = { closed++ },
            onDispatchRun = { sent++ },
        ) {
            dismissTheSheet()
        }

        assertEquals(1, closed)
        assertEquals(0, sent)
    }

    @Test
    fun `the sheet says where the time goes and what the danger takes`() {
        // Both are deterministic and both are stated before the tap, which is the pillar being
        // signposted rather than being a tax wearing a story. Nothing in this mechanic is rolled.
        galaxyScreen(uiState = dispatchOfferUiState) {
            assertTheSheetReads("on station")
            assertTheSheetReads("danger 0")
            assertTheSheetReads("nothing taken")
        }
    }

    @Test
    fun `the sheet is not on the screen until a world is tapped`() {
        galaxyScreen(uiState = homeSystemUiState) { assertNoSheet() }
    }
}
