package dev.fardavide.oltre.client.dispatch.ui

import dev.fardavide.oltre.client.design.component.WatchSquareUiState
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Ships
import kotlin.time.Duration

// Raised from a world row on Galaxy, and from a landing on Fleets. Three controls, one figure — and
// **no cost line and no affordability
// state**, which is Design's fourth call and a subtraction rather than an omission: `FleetBalance`
// charges nothing per run, so the sheet has nothing to be short of. The hull was the price and it is
// paid at the Shipyard, where "cannot afford" is drawn in the idiom the probe and the facility rows
// already spend.
sealed interface DispatchUiState {

    // **The world's name, and the coordinate went to the head line under it** — Claude Design,
    // 2026-08-16. The change is one string swapped for another and it is not cosmetic: the Fleets
    // worked list leads every row with a name, so a tap that landed on a sheet headed `[3:185:4]`
    // would look like it had opened something else. Galaxy's world rows lead with the name too, so
    // this is the same fix on both doors rather than a concession to the new one.
    //
    // `name` rather than `title`, because both refusals already have a title and it is a sentence:
    // *"Every hull is away."* Two fields called title on one sheet is one of them being read wrong.
    val name: TextRes

    // "[3:185:4] · metal 1.24 · crystal 0.74 · no hazards", richer resource first after the address
    // — the head is the world in one line, so the sheet answers "which world is this" before it
    // answers anything else.
    val head: TextRes

    // 320dp drops the lesser resource rather than ellipsising the pair. A width decision, not a
    // change of voice: what goes is the number you were not going to pick. **The address stays**,
    // because it is now the only thing on the sheet that says where this world is.
    val compactHead: TextRes

    data class Offer(
        override val name: TextRes,
        override val head: TextRes,
        override val compactHead: TextRes,
        // **The three subjects of the run, resolved.** These are what `startRun` is actually called
        // with, and they are on the ui-state rather than left in `DispatchSelection` for one reason:
        // the mapper is what filled in the three defaults and what clamped the hull count to the
        // idle pool, so the selection and the offer can differ — and the offer is what the player
        // was shown. Dispatching anything else would send a run the sheet never described.
        //
        // **The target is the whole coordinate and never a slot.** The ledger raises this sheet from
        // rows belonging to six systems at once, so a slot would leave two thirds of the address to
        // be guessed from whatever the map was last parked on.
        val at: GalaxyCoordinate,
        val window: Duration,
        // **The manifest itself, and not a count the screen rebuilds.** Both screens used to make
        // `Ships.of(SKIFF, shipCount)` from the number beside the stepper, which was true while every
        // sendable hull was a skiff and became a silent refusal the moment that number turned into a
        // *berth* count: six berths is not six skiffs, and `startRun` would answer `NoSuchShips` to a
        // fleet the player does not own while the tap appeared to do nothing.
        //
        // It belongs here for the reason the three fields around it do — the mapper resolved the
        // defaults and did the clamping, so **the offer is what the player was shown**, and
        // dispatching anything else would send a run the sheet never described.
        val manifest: Ships,
        // Bring back, send, home in — in that order, because it is the order of decreasing
        // permanence. What your colony is short of changes over days, how many hulls you have
        // changes over hours, and how long you will be away changes every check-in.
        val gathering: ResourceKind,
        val metalRichness: TextRes,
        val crystalRichness: TextRes,
        // What each chip says about the world, under its currency: "richness 1.24 · deposit full",
        // "richness 0.74 · deposit 620/1,798", "richness 0.74 · deposit empty". **Richness lives here
        // now rather than on the row** — Design moved it when the stocks took the row's headline, and
        // the chip is where there is prose room for both.
        val metalDeposit: TextRes,
        val crystalDeposit: TextRes,
        // "6 berths", or "2 skiffs" while no hauler is idle — the unit changes with the fleet,
        // because a berth is a distinction only a second hull type creates.
        val ships: TextRes,
        val shipCount: Int,
        // **What each stepper sends, or null at the end of the range.** One representation rather
        // than a value and a boolean beside it: the hold does not climb by one — 1, 2, 4, 5, 6 at one
        // hauler and two skiffs — so "the next one" is a lookup into the reachable list and not
        // arithmetic the sheet can do for itself.
        val fewer: Int?,
        val more: Int?,
        val pool: TextRes,
        val windows: List<WindowRungUiState>,
        // **Empty when a second hull type is not idle**, which is 0.13.1 unchanged and is most
        // sheets: a control with one option is not a control. It is also every sheet where the
        // hauler is already in the sky, and every sheet before the Shipyard's hauler card is bought.
        val hullCells: List<HullCellUiState>,
        // One slot below the cells, one job — what the *other* cell would do. The counterfactual haul
        // when both fit the rung, the rung consequence when they do not, and the clamp instead of
        // either when the vein cannot fill the hold.
        val cellNote: TextRes?,
        // Present only when the ladder has narrowed. The rung that vanished is the copy — this
        // sentence exists so a player who never saw the full ladder still learns why.
        val ladderNote: LadderNoteUiState?,
        // "The 12h window brings the same." — the shortest rung that still takes everything there is,
        // named only when a shorter one exists and the chosen one is wasting hours. **Earned rather
        // than standing**: on a rung that is already the shortest that empties the vein there is
        // nothing to say, and a note that appeared on every dispatch would be furniture.
        //
        // No new control and no new state on the rungs. A rung whose extra hours bring nothing is not
        // locked and not disabled — inventing a state for *not better* would be the first greyed thing
        // in the app, and the ladder narrows by absence everywhere else.
        val rungNote: TextRes?,
        // "3 skiffs empty it. The 4th brings nothing." Present only when the clamp bites *and* there
        // is a remedy: at `atFewest` there is no smaller fleet to send, so the sheet shows the figure
        // and stops. Under the cliff the marginal hull is worth exactly zero, so this is arithmetic
        // stated before the tap rather than a scold.
        val clampNote: TextRes?,
        // The only thing on the sheet that moves when a control is touched, which is why it sits
        // under a rule and above the verb.
        val figure: TextRes,
        // **What this run leaves behind** — `870 left in the ground`, or `the whole deposit` when
        // the vein is what stopped it. One token in a slot that already existed.
        //
        // **It held the per-ship reading until 0.15.0**, and Design retired that: `449 each` is under
        // its *Retired* heading with the reason in five words — *"the per-ship reading, which a mix
        // has no answer for."* A hauler and two skiffs do not lift the same amount each, and a berth
        // is not a thing a player sends, so "each" names nothing the moment the manifest is mixed.
        //
        // The vein answers whatever the manifest is, which is why it is the replacement rather than a
        // second slot: it is a fact about the ground. **The figure is never restated** — when the
        // clamp bites, the headline number already *is* the deposit.
        val vein: TextRes?,
        val legs: TextRes,
        val compactLegs: TextRes,
        val danger: TextRes,
        val compactDanger: TextRes,
        // **The bell beside the verb, and the only control on this sheet that does not change the
        // run.** Everything above it moves the figure; this one decides whether the player hears
        // about the flight when it is over — Davide's call, 2026-08-22.
        //
        // Two states and never three. The square's third state belongs to a *queue* — see
        // `WatchSquareUiState.ASKED_SEVERAL` — and a run is one flight with one ending, so there is
        // one question to ask about it.
        //
        // Non-null, unlike the square on a facility row. A row without a square is a row with
        // nothing to wait for; the verb below this is always live in `Offer`, so there is always a
        // flight to ask about.
        val announce: WatchSquareUiState,
    ) : DispatchUiState

    // **A mode rather than a refusal, and that distinction is Design's.** A dry world keeps its whole
    // sheet — chips, stepper, ladder — and loses only the figure, which becomes a countdown to the
    // hold *this* offer would lift. That is what makes the state worth entering rather than backing
    // out of: **the wait is a function of the ask**, so the remedy is in the player's hands.
    //
    // It has to be, because of what Design measured: the vein and the rate carry one multiplier, so a
    // full fleet's lift is about the size of a vein — "four skiffs at 6h" is 18d 13h away, which reads
    // exactly like the "full again" this sheet ruled out. Shrink the ask to one skiff at 3h and the
    // same world is worth visiting in 2d 04h. The countdown is only honest because the controls above
    // it still move.
    data class Waiting(
        override val name: TextRes,
        override val head: TextRes,
        override val compactHead: TextRes,
        val at: GalaxyCoordinate,
        val window: Duration,
        val gathering: ResourceKind,
        val metalRichness: TextRes,
        val crystalRichness: TextRes,
        val metalDeposit: TextRes,
        val crystalDeposit: TextRes,
        // "6 berths", or "2 skiffs" while no hauler is idle — the unit changes with the fleet,
        // because a berth is a distinction only a second hull type creates.
        val ships: TextRes,
        val shipCount: Int,
        // **What each stepper sends, or null at the end of the range.** One representation rather
        // than a value and a boolean beside it: the hold does not climb by one — 1, 2, 4, 5, 6 at one
        // hauler and two skiffs — so "the next one" is a lookup into the reachable list and not
        // arithmetic the sheet can do for itself.
        val fewer: Int?,
        val more: Int?,
        val pool: TextRes,
        val windows: List<WindowRungUiState>,
        val hullCells: List<HullCellUiState>,
        val cellNote: TextRes?,
        val ladderNote: LadderNoteUiState?,
        val title: TextRes,
        val note: TextRes,
        // "in 18d 13h", or null when no amount of waiting covers this ask and only a smaller one will.
        val wait: TextRes?,
        val legs: TextRes,
        val compactLegs: TextRes,
        val danger: TextRes,
        val compactDanger: TextRes,
    ) : DispatchUiState

    // The sheet refuses the sale and says why, in the words of the thing that refused it — the same
    // shape `ProbeActionUiState.NothingToSurvey` already has. Both refusals are reachable on a first
    // check-in, and neither is an error state.
    data class Refuse(
        override val name: TextRes,
        override val head: TextRes,
        override val compactHead: TextRes,
        val title: TextRes,
        val note: TextRes,
        val action: RefuseActionUiState?,
    ) : DispatchUiState
}

// A rung of the ladder. Keyed by its own duration rather than by its index, because the ladder
// **narrows** on a distant target rather than greying rungs out — so the rung at index 0 is a
// different window depending on how far away the world is.
//
// **Four states, and the fourth arrived with the hauler** — Claude Design, *Twice the Flight*:
// *"Absent means never. Dim means not with these hulls."* The shipped ladder teaches distance by
// absence, and that lesson only survives if absence keeps **one** cause. So a rung no manifest can
// fly is still not drawn, and a rung *this* manifest cannot fly is drawn at 42% with the hull that
// would fly it underneath.
//
// **It is not a disabled control, because this app has none** — it is the locked-facility idiom, and
// it is the undo: tapping it takes the hauler out and selects the rung. `requirement` non-null *is*
// the locked state; there is no boolean beside it, because a lock with nothing to say would be a
// greyed control by another name.
data class WindowRungUiState(
    val label: TextRes,
    val window: Duration,
    val selected: Boolean,
    val requirement: TextRes?,
)

// One of the two cells under the stepper. **Two, because the clock has two values** — a manifest's
// flight is set by its slowest hull, so however many hulls go there are only ever two answers, and
// the cells are the window ladder's own idiom at two rungs wide because they are the same kind of
// choice: a time.
//
// They carry no section label of their own. The cells name their hulls, and Design measured a fourth
// label at 21dp the 320dp sheet does not have.
// `berths` is what tapping it sends, and it is the *same* callback the stepper spends: Design's
// *"both controls move one cursor along one ordered list of reachable manifests."* A cell whose hold
// is smaller than the stepper's says so before it is tapped, so tapping it clamps and the number
// moves where the label already said it would — which is why nothing here is ever a dead control.
data class HullCellUiState(
    val label: TextRes,
    val trip: TextRes,
    val berths: Int,
    val selected: Boolean,
)

// A line under the ladder, and the weight is the whole announcement — Design: *"muted states a rule
// that was already true, body states something that just changed."* No animation, no toast, no
// highlight: the app has none of those, and a moved selection does not earn the first.
data class LadderNoteUiState(val label: TextRes, val emphasised: Boolean)

sealed interface RefuseActionUiState {

    // The one refusal in the app that hands back a verb. It also chains the two: a probe used to buy
    // a verdict and stop, and now it buys the right to send a ship.
    //
    // It carries the bell for the same reason the offer does — a probe is a flight, and the sheet
    // that sends one is the only place there is to ask about it. The same control and the same
    // standing answer: what the player set on a run's sheet is what this one opens showing.
    data class Probe(val label: TextRes, val announce: WatchSquareUiState) : RefuseActionUiState

    // A reading, not a control — the idiom the unaffordable probe already spends. There is nothing
    // to send, so there is no button to grey out.
    data class Waiting(val label: TextRes) : RefuseActionUiState
}
