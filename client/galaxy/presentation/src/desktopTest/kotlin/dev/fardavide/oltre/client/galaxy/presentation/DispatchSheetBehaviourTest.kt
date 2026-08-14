package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.test.ExperimentalTestApi
import dev.fardavide.oltre.client.galaxy.ui.DispatchUiState
import dev.fardavide.oltre.client.galaxy.ui.VerdictUiState
import dev.fardavide.oltre.client.galaxy.ui.galaxyPage
import dev.fardavide.oltre.client.galaxy.ui.homeSystemUiState
import dev.fardavide.oltre.client.galaxy.ui.everyVerdictUiState
import dev.fardavide.oltre.client.galaxy.ui.dispatchOfferUiState
import dev.fardavide.oltre.client.galaxy.ui.dispatchUnsurveyedUiState
import dev.fardavide.oltre.client.galaxy.ui.dispatchNoShipsUiState
import dev.fardavide.oltre.client.galaxy.ui.dispatchClampedUiState
import dev.fardavide.oltre.client.galaxy.ui.dispatchWaitingUiState
import dev.fardavide.oltre.client.galaxy.ui.dispatchWaitingForeverUiState
import dev.fardavide.oltre.client.galaxy.ui.dispatchWorkedUiState
import dev.fardavide.oltre.client.galaxy.ui.dispatchFarUiState
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
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

        galaxyPage(uiState = homeSystemUiState, onOpenWorld = { opened += it }) {
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

        galaxyPage(uiState = dispatchOfferUiState) {
            assertTheSheetIsUp()
            assertTheSheetReads(coordinate)
        }
    }

    @Test
    fun `sending commits the run and nothing else does`() {
        // Three controls and one verb. Touching a control changes what the run *would* be; only the
        // verb spends anything, which is why the sheet costs nothing to open and has no cancel.
        var sent = 0

        galaxyPage(uiState = dispatchOfferUiState, onDispatchRun = { sent++ }) {
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

        galaxyPage(uiState = dispatchOfferUiState, onSelectGathering = { chosen += it }) {
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

        galaxyPage(uiState = dispatchOfferUiState) {
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

        galaxyPage(uiState = dispatchFarUiState) {
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
        galaxyPage(uiState = dispatchOfferUiState) {
            FleetBalance.WINDOWS.forEach { homeIn(it) }
            assertTheSheetDoesNotRead("No shorter window")
        }
    }

    @Test
    fun `choosing a window asks for that window`() {
        val chosen = mutableListOf<Long>()

        galaxyPage(uiState = dispatchOfferUiState, onSelectWindow = { chosen += it.inWholeMinutes }) {
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

        galaxyPage(uiState = dispatchUnsurveyedUiState, onDispatchProbe = { probed++ }) {
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

        galaxyPage(uiState = dispatchNoShipsUiState, onDispatchRun = { sent++ }) {
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

        galaxyPage(uiState = homeSystemUiState, onOpenWorld = { opened += it }) {
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

        galaxyPage(uiState = everyVerdictUiState, onOpenWorld = { opened += it }) {
            tapTheWorld(3)
        }

        assertTrue(opened.isEmpty())
    }

    // **The regression this sheet shipped with at 0.7.0, and the reason it is a `ModalBottomSheet`
    // now.** It was a Column parked at the bottom of the page's own `Box`: drawn last, so it looked
    // like an overlay, but with no pointer input of its own — so a drag that started on it fell
    // straight through to the world list behind and scrolled the screen under the player's thumb.
    // It was also confined to the destination's slot in the scaffold, which put it *above* the tab
    // bar rather than over it, and its handle was a drawn rectangle that did not drag.
    //
    // A sheet that is a popup cannot have any of those faults, so this asserts the one of the three
    // a desktop test can see: the page underneath does not move.
    @Test
    fun `a drag on the sheet leaves the screen behind it where it was`() {
        val scroll = ScrollState(initial = 0)

        galaxyPage(uiState = dispatchOfferUiState, scrollState = scroll) {
            dragTheSheet()
        }

        assertEquals(0, scroll.value, "the sheet is over the screen, not part of it")
    }

    @Test
    fun `the sheet says where the time goes and what the danger pays`() {
        // Both are deterministic and both are stated before the tap, which is the pillar being
        // signposted rather than being a tax wearing a story. Nothing in this mechanic is rolled.
        //
        // **"pays" rather than "takes" since round 21** — danger adds to the hold instead of taking
        // from it, and this fixture is the safe home-system world, so its clause is the zero case.
        galaxyPage(uiState = dispatchOfferUiState) {
            assertTheSheetReads("on station")
            assertTheSheetReads("danger 0")
            assertTheSheetReads("nothing added")
        }
    }

    // ── What the vein does to the sheet ──────────────────────────────────────────────────────

    @Test
    fun `a chip says what is left as well as how rich it is`() {
        // Richness moved here when the stocks took the row's headline, and this is the one card where
        // both readings sit together — which is what makes the currency choice a comparison rather
        // than a memory test.
        galaxyPage(uiState = dispatchOfferUiState) {
            assertTheSheetReads("deposit full")
        }
    }

    @Test
    fun `a fleet the world cannot fill is told so rather than shown a number twice`() {
        // The clamped state, which is the common one. The headline figure already *is* the deposit,
        // so what marks it is the slot beside it — one token, in a slot that already exists.
        galaxyPage(uiState = dispatchClampedUiState) {
            assertTheSheetReads("the whole deposit")
            // Design's copy is the one-idle-hull case — "The 4th brings nothing." — and this fixture
            // sends eight at a world two can empty, so it is the plural form of the same sentence.
            assertTheSheetReads("empty it.")
            assertTheSheetReads("bring nothing.")
        }
    }

    @Test
    fun `the sheet says how long the fleet is actually working`() {
        // The invariant made visible with no copy at all: because the vein and the rate carry one
        // multiplier, this segment reads the same on the doorstep as in the next galaxy.
        galaxyPage(uiState = dispatchClampedUiState) {
            assertTheSheetReads("working")
        }
    }

    @Test
    fun `a dry world keeps its controls and counts down to the ask instead of refusing`() {
        // **A mode rather than a refusal**, and the distinction is the whole design: the wait is a
        // function of the ask, so the chips and the ladder have to stay reachable for the remedy to
        // be in the player's hands at all.
        galaxyPage(uiState = dispatchWaitingUiState) {
            assertTheSheetReads("is empty.")
            assertTheSheetReads("Fewer skiffs, or a shorter window, is sooner.")
            // The ladder is still there to be tapped, which a refusal would not have.
            assertTheSheetReads("6h")
        }
    }

    @Test
    fun `an ask no world can ever hold says so instead of naming a date`() {
        // The other half of the waiting state, and the reason its controls stay live: a full fleet
        // wants several times what any world of this size holds, so there is no date to give and the
        // remedy is the stepper rather than the calendar.
        galaxyPage(uiState = dispatchWaitingForeverUiState) {
            assertTheSheetReads("No world this size ever holds that much.")
            assertTheSheetReads("Fewer skiffs, or a shorter window, is sooner.")
        }
    }

    @Test
    fun `a worked world states a fraction rather than a word`() {
        galaxyPage(uiState = dispatchWorkedUiState) {
            assertTheSheetReads("/")
        }
    }

    // ── The screen that owns the sheet ───────────────────────────────────────────────────────
    //
    // Which world has its sheet up is `GalaxyScreen`'s own state, so these two are the only
    // assertions in the file that cannot be made against a mapped frame: everything above is handed
    // a sheet that is already up.

    @Test
    fun `tapping a world raises the sheet on the screen itself`() {
        galaxyScreen(state = testGameState) {
            assertNoSheet()

            tapTheWorld(RUNNABLE_SLOT)

            assertTheSheetIsUp()
        }
    }

    @Test
    fun `the run that leaves is the one the sheet described and the sheet then has nothing left to say`() {
        // Read off the *rendered* offer rather than off the selection: the mapper is what resolved
        // the three defaults and what clamped the hull count to the idle pool, so dispatching the
        // raw selection would send a run the sheet never described.
        val sent = mutableListOf<Quadruple>()

        galaxyScreen(
            state = testGameState,
            onDispatchRun = { at, gathering, ships, window -> sent += Quadruple(at, gathering, ships, window) },
        ) {
            tapTheWorld(RUNNABLE_SLOT)
            bringBack(ResourceKind.CRYSTAL)
            send()

            // The state after the tap is its own receipt — the row's reach line and the Colony strip
            // both change — so leaving the sheet up would be leaving up an argument for a decision
            // already taken.
            assertNoSheet()
        }

        val run = sent.single()
        assertEquals(RUNNABLE_SLOT, run.at.slot)
        assertEquals(ResourceKind.CRYSTAL, run.gathering)
        // The whole idle pool by default, which at genesis is the one granted skiff.
        assertEquals(Ships.of(ShipType.SKIFF, 1), run.ships)
        assertEquals(3.hours, run.window)
    }

    @Test
    fun `the sheet is not on the screen until a world is tapped`() {
        galaxyPage(uiState = homeSystemUiState) { assertNoSheet() }
    }
}

// The four subjects of a run, kept together so one assertion can name all four rather than four
// mutable lists agreeing by luck about which tap they came from.
private data class Quadruple(
    val at: GalaxyCoordinate,
    val gathering: ResourceKind,
    val ships: Ships,
    val window: kotlin.time.Duration,
)
