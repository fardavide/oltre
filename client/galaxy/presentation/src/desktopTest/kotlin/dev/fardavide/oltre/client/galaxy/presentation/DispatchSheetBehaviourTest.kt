package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.design.text.English
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.test.ExperimentalTestApi
import dev.fardavide.oltre.client.dispatch.presentation.DispatchSelection
import dev.fardavide.oltre.client.dispatch.presentation.toDispatchUiState
import dev.fardavide.oltre.client.dispatch.ui.DispatchUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyBodyUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyRowUiState
import dev.fardavide.oltre.client.galaxy.ui.WorldVerdictUiState
import dev.fardavide.oltre.client.galaxy.ui.galaxyPage
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
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
        val opened = mutableListOf<GalaxyCoordinate>()

        galaxyPage(uiState = homeSystemUiState, onOpenWorld = { opened += it }) {
            tapTheWorld(RUNNABLE)
        }

        assertEquals(listOf(RUNNABLE), opened.toList())
    }

    @Test
    fun `the sheet names the world it was raised from`() {
        // **The row's own name, not its address.** Both lead with the name since 0.13, which is what
        // makes a tap land on something that looks like what was tapped.
        val coordinate = assertIs<GalaxyBodyUiState.System>(homeSystemUiState.body).rows
            .filterIsInstance<GalaxyRowUiState.World>()
            .first { it.at == RUNNABLE }
            .name

        galaxyPage(uiState = dispatchOfferUiState) {
            assertTheSheetIsUp()
            assertTheSheetReads(English.resolve(coordinate))
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
            assertTheSheetReads(English.resolve(offer.figure))
        }
    }

    @Test
    fun `the sheet opens on the resource the world is richer in`() {
        // The default that saves a tap on the commonest case: you came here because the row said
        // this world is good for something, and the sheet opens on the something. Compared in the
        // generator's own units rather than in the priced basket, because the player is choosing
        // between two columns of the same number.
        val offer = assertIs<DispatchUiState.Offer>(dispatchOfferUiState.dispatch)
        val richer = if (English.resolve(offer.metalRichness) >= English.resolve(offer.crystalRichness)) {
            ResourceKind.METAL
        } else {
            ResourceKind.CRYSTAL
        }

        assertEquals(richer, offer.gathering)
        // ...and the chip for that resource is the one drawn as selected, which is where the reading
        // lives since 0.13. The head is the address and the hazards now — see `DispatchUiState`.
        assertTrue(
            English.resolve(offer.head).startsWith(English.resolve(offer.at.label())),
            English.resolve(offer.head),
        )
    }

    @Test
    fun `a window is missing rather than dead when the trip will not fit inside it`() {
        // The only way to show "too far" without a control that refuses its own tap — and the rung
        // that vanishes is the copy: a ladder narrowing on a distant target teaches distance before
        // any sentence does. One galaxy hop is 9h 10m each way at drive 0, so **only the longest rung
        // survives** — the four below it cannot leave the twenty minutes on the surface that make
        // the trip worth taking.
        //
        // **It used to be two rungs and 4h 40m, which is what drive 1 costs.** The fixture has
        // researched nothing, so this is the ladder a new colony really meets: the frontier is a
        // 24h-rung-only proposition until the drive is bought, and buying it hands the 12h rung
        // back. That is the whole teaching device, and it needs no copy at all.
        //
        // **The first assertion is that there is a ladder at all**, and it is here because the first
        // version of this fixture was unsurveyed: the sheet refused before it priced anything, so
        // every `assertNoRungFor` below passed against a sheet with no rungs on it whatever.
        val far = assertIs<DispatchUiState.Offer>(dispatchFarUiState.dispatch)
        assertEquals(listOf(24.hours), far.windows.map { it.window })

        galaxyPage(uiState = dispatchFarUiState) {
            assertNoRungFor(1.hours)
            assertNoRungFor(3.hours)
            assertNoRungFor(6.hours)
            assertNoRungFor(12.hours)
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
        val opened = mutableListOf<GalaxyCoordinate>()
        val ownWorld = assertIs<GalaxyBodyUiState.System>(homeSystemUiState.body).rows
            .filterIsInstance<GalaxyRowUiState.World>()
            .first { it.verdict == WorldVerdictUiState.HOME }
            .at

        galaxyPage(uiState = homeSystemUiState, onOpenWorld = { opened += it }) {
            tapTheWorld(ownWorld)
        }

        assertTrue(opened.isEmpty(), "a run cannot be sent to the world it is sent from")
    }

    @Test
    fun `a relay is a point of interest and still not a destination`() {
        // The galaxy sheet says it in as many words — the screen may label a relay, it may not be
        // tappable — and no holding mechanic exists until multiplayer. Nothing about the fleet
        // changes that; a relay is not a world and has no hold to fill.
        val opened = mutableListOf<GalaxyCoordinate>()

        galaxyPage(uiState = relaySystemUiState, onOpenWorld = { opened += it }) {
            tapTheWorld(relayCoordinate)
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
        galaxyPage(uiState = dispatchWholeDepositUiState) {
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
    //
    // **All three land on the worlds list rather than tapping their way to it**, and that is a
    // statement about the subject rather than a shortcut: a run is raised from a *row*, the tab has
    // landed on the drawn map since 0.12, and which of the two the tab opens on is a preference the
    // screen is handed. Walking the switch first would make these tests about the switch, which
    // `LedgerBehaviourTest` already owns.

    @Test
    fun `tapping a world raises the sheet on the screen itself`() {
        galaxyScreen(state = testGameState, landing = GalaxyLanding.WORLDS) {
            assertNoSheet()

            tapTheWorld(RUNNABLE)

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
            landing = GalaxyLanding.WORLDS,
            onDispatchRun = { at, gathering, ships, window -> sent += Quadruple(at, gathering, ships, window) },
        ) {
            tapTheWorld(RUNNABLE)
            bringBack(ResourceKind.CRYSTAL)
            send()

            // The state after the tap is its own receipt — the row's reach line and the Colony strip
            // both change — so leaving the sheet up would be leaving up an argument for a decision
            // already taken.
            assertNoSheet()
        }

        val run = sent.single()
        assertEquals(RUNNABLE, run.at)
        assertEquals(ResourceKind.CRYSTAL, run.gathering)
        // The whole idle pool by default, which at genesis is the one granted skiff.
        assertEquals(Ships.of(ShipType.SKIFF, 1), run.ships)
        assertEquals(3.hours, run.window)
    }

    @Test
    fun `a ledger row raises the sheet on its own world and sends the run there`() {
        // **The defect 0.11 shipped, and the reason a row carries its whole address.** The ledger
        // lists worlds from six systems at once and a tap used to hand the sheet a slot alone, so the
        // other two thirds of the target were filled in from whatever system the *map* was parked on
        // — home, which is where it starts. A row reading `crystal full` raised a sheet reading
        // `deposit empty`, about a world in another system entirely, and the verb would have sent the
        // run there too. The defect is if anything easier to reach since 0.12, because the map is now
        // the screen the tab lands on and the selection on it moves under a thumb.
        //
        // Asserted on the stateful screen because that is where the address used to be lost: every
        // frame in this file is handed a sheet that is already up.
        val sent = mutableListOf<Quadruple>()

        galaxyScreen(
            state = wellTravelledState,
            landing = GalaxyLanding.WORLDS,
            onDispatchRun = { at, gathering, ships, window -> sent += Quadruple(at, gathering, ships, window) },
        ) {
            tapTheWorld(elsewhere)
            assertTheSheetReads(English.resolve(elsewhere.label()))
            send()
        }

        assertEquals(elsewhere, sent.single().at)
    }

    @Test
    fun `the sheet is not on the screen until a world is tapped`() {
        galaxyPage(uiState = homeSystemUiState) { assertNoSheet() }
    }

    // ── The manifest suggests itself ─────────────────────────────────────────────────────────
    //
    // **Davide, 2026-08-17** — *"going from 55 to 3 is a lot of taps"*. The sheet opens on the fleet
    // that empties the vein rather than on every hull the colony owns, and the two controls that
    // change what a fleet would lift put the number back where the arithmetic says it should be.
    // Both are asserted on the stateful screen, because the selection they reset is `GalaxyScreen`'s.

    @Test
    fun `a rung puts the fleet back to the one that empties the vein`() {
        // A rung is a change of ask rather than a change of schedule: the same vein wants a smaller
        // fleet the longer the fleet may stay on the surface, so a count chosen against the 3h rung
        // is arithmetic about a run that no longer exists.
        val onTheLongRung = suggestionFor(window = 24.hours)
        val stepped = suggestionFor().shipCount + 1
        assertTrue(stepped != onTheLongRung.shipCount, "the two have to differ for this to be a claim")

        galaxyScreen(state = bigFleetState, landing = GalaxyLanding.WORLDS) {
            tapTheWorld(RUNNABLE)
            sendOneMore()
            assertTheSheetReads("$stepped skiffs")

            homeIn(24.hours)

            assertTheSheetReads(English.resolve(onTheLongRung.ships))
        }
    }

    @Test
    fun `a currency puts the fleet back to the one that empties the vein`() {
        // The other half of the same rule and Davide's call of 2026-08-17: the two deposits are
        // different sizes, so a count suggested for one is arithmetic about the wrong world.
        val onCrystal = suggestionFor(gathering = ResourceKind.CRYSTAL)
        val stepped = suggestionFor().shipCount + 1
        assertTrue(stepped != onCrystal.shipCount, "the two have to differ for this to be a claim")

        galaxyScreen(state = bigFleetState, landing = GalaxyLanding.WORLDS) {
            tapTheWorld(RUNNABLE)
            sendOneMore()
            assertTheSheetReads("$stepped skiffs")

            bringBack(ResourceKind.CRYSTAL)

            assertTheSheetReads(English.resolve(onCrystal.ships))
        }
    }

    @Test
    fun `the sheet opens on the fleet that empties the vein rather than on every hull you own`() {
        // **A suggestion rather than a cap**, which is what the pool line beside the label is for:
        // the number opens where the arithmetic is and the `+` still reaches all 55.
        galaxyScreen(state = bigFleetState, landing = GalaxyLanding.WORLDS) {
            tapTheWorld(RUNNABLE)

            assertTheSheetReads(English.resolve(suggestionFor().ships))
            assertTheSheetReads("of 55 idle")
            assertTheSheetDoesNotRead("55 skiffs")
        }
    }

    // ── The stepper repeats while it is held ─────────────────────────────────────────────────

    @Test
    fun `a stepper held down keeps stepping instead of asking for a tap each time`() {
        // The other half of Davide's 2026-08-17 call: the suggestion means the walk is usually short,
        // and a walk that is not short should not be 52 taps. The hold is what makes the whole pool
        // reachable from the suggestion and back.
        val asked = mutableListOf<Int>()

        galaxyPage(uiState = dispatchSuggestedUiState, onSelectShips = { asked += it }) {
            holdSendMore(millis = 1_500)
        }

        assertTrue(asked.size > 1, "a hold produced ${asked.size} steps")
    }

    @Test
    fun `a tap is one step and a hold does not add one more when the finger comes off`() {
        // The off-by-one a repeat invites: the release is still an up on a control whose tap fires on
        // up, so the hold has to say it already stepped. A tap is unchanged — one step, no hold.
        val tapped = mutableListOf<Int>()
        val held = mutableListOf<Int>()

        galaxyPage(uiState = dispatchSuggestedUiState, onSelectShips = { tapped += it }) {
            sendOneMore()
        }
        galaxyPage(uiState = dispatchSuggestedUiState, onSelectShips = { held += it }) {
            holdSendMore(millis = 1_500)
        }

        assertEquals(1, tapped.size)
        // The frame is static, so every step asks for the same number — what is being counted is how
        // many times the control fired, and the release must not be one of them.
        assertEquals(held.size, held.count { it == tapped.single() })
    }

    @Test
    fun `a stepper at its bound stays at its bound however long it is held`() {
        // The pool is one hull in this frame, so `−` is disabled and the repeat must not start: a
        // hold on a dead control is the one place a repeat could invent a step the tap cannot make.
        val asked = mutableListOf<Int>()

        galaxyPage(uiState = dispatchOfferUiState, onSelectShips = { asked += it }) {
            holdSendFewer(millis = 1_500)
        }

        assertTrue(asked.isEmpty(), "a disabled stepper fired ${asked.size} steps")
    }

    private companion object {

        // Big enough that the vein is what stops the run, which is the state the default was wrong
        // in: 55 idle hulls at a world three of them can empty.
        val bigFleetState: GameState = testGameState.copy(ships = Ships.of(ShipType.SKIFF, 55))

        // What the mapper would suggest, read off the mapper rather than written down — the number
        // is `FleetBalance.hullsToLift` to the hull, and a figure typed here would be this test
        // asserting the balance instead of the screen.
        fun suggestionFor(
            gathering: ResourceKind? = null,
            window: kotlin.time.Duration? = null,
        ): DispatchUiState.Offer = assertIs<DispatchUiState.Offer>(
            bigFleetState.toDispatchUiState(
                selection = DispatchSelection(at = RUNNABLE, gathering = gathering, ships = null, window = window),
                probe = null,
                now = FIXTURE_NOW,
            ),
        )

        // A surveyed world outside the home system — the nearest one, so the ledger's own ordering
        // puts it near the top and the ladder is a full five rungs. Found rather than written down:
        // which systems a fortnight of probes reached is `wellTravelledState`'s business.
        val elsewhere: GalaxyCoordinate = wellTravelledState.galaxy.let { galaxy ->
            galaxy.surveyed
                .filter { it.system != galaxy.home.system }
                .minBy { FleetBalance.roundTrip(from = galaxy.home, to = it, research = wellTravelledState.research) }
        }
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
