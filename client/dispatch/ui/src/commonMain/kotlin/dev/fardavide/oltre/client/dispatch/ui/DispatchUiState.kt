package dev.fardavide.oltre.client.dispatch.ui

import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.ResourceKind
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
    // *"Every skiff is away."* Two fields called title on one sheet is one of them being read wrong.
    val name: String

    // "[3:185:4] · metal 1.24 · crystal 0.74 · no hazards", richer resource first after the address
    // — the head is the world in one line, so the sheet answers "which world is this" before it
    // answers anything else.
    val head: String

    // 320dp drops the lesser resource rather than ellipsising the pair. A width decision, not a
    // change of voice: what goes is the number you were not going to pick. **The address stays**,
    // because it is now the only thing on the sheet that says where this world is.
    val compactHead: String

    data class Offer(
        override val name: String,
        override val head: String,
        override val compactHead: String,
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
        // Bring back, send, home in — in that order, because it is the order of decreasing
        // permanence. What your colony is short of changes over days, how many hulls you have
        // changes over hours, and how long you will be away changes every check-in.
        val gathering: ResourceKind,
        val metalRichness: String,
        val crystalRichness: String,
        // What each chip says about the world, under its currency: "richness 1.24 · deposit full",
        // "richness 0.74 · deposit 620/1,798", "richness 0.74 · deposit empty". **Richness lives here
        // now rather than on the row** — Design moved it when the stocks took the row's headline, and
        // the chip is where there is prose room for both.
        val metalDeposit: String,
        val crystalDeposit: String,
        val ships: String,
        val shipCount: Int,
        val atFewest: Boolean,
        val atMost: Boolean,
        val pool: String,
        val windows: List<WindowRungUiState>,
        // Present only when the ladder has narrowed. The rung that vanished is the copy — this
        // sentence exists so a player who never saw the full ladder still learns why.
        val ladderNote: String?,
        // "The 12h window brings the same." — the shortest rung that still takes everything there is,
        // named only when a shorter one exists and the chosen one is wasting hours. **Earned rather
        // than standing**: on a rung that is already the shortest that empties the vein there is
        // nothing to say, and a note that appeared on every dispatch would be furniture.
        //
        // No new control and no new state on the rungs. A rung whose extra hours bring nothing is not
        // locked and not disabled — inventing a state for *not better* would be the first greyed thing
        // in the app, and the ladder narrows by absence everywhere else.
        val rungNote: String?,
        // "3 skiffs empty it. The 4th brings nothing." Present only when the clamp bites *and* there
        // is a remedy: at `atFewest` there is no smaller fleet to send, so the sheet shows the figure
        // and stops. Under the cliff the marginal hull is worth exactly zero, so this is arithmetic
        // stated before the tap rather than a scold.
        val clampNote: String?,
        // The only thing on the sheet that moves when a control is touched, which is why it sits
        // under a rule and above the verb.
        val figure: String,
        // "449 each" on an unclamped run, "the whole deposit" when the vein is what stopped it —
        // one token in a slot that already exists, and the only marker the clamped state needs.
        // **The figure is never restated**: when the clamp bites the headline number already *is* the
        // deposit, and printing it twice is the defect the null-on-a-single-hull rule below exists to
        // prevent. Null on a single unclamped hull, because "132 each" beside "132 metal" is the same
        // number twice.
        val perShip: String?,
        val legs: String,
        val compactLegs: String,
        val danger: String,
        val compactDanger: String,
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
        override val name: String,
        override val head: String,
        override val compactHead: String,
        val at: GalaxyCoordinate,
        val window: Duration,
        val gathering: ResourceKind,
        val metalRichness: String,
        val crystalRichness: String,
        val metalDeposit: String,
        val crystalDeposit: String,
        val ships: String,
        val shipCount: Int,
        val atFewest: Boolean,
        val atMost: Boolean,
        val pool: String,
        val windows: List<WindowRungUiState>,
        val ladderNote: String?,
        val title: String,
        val note: String,
        // "in 18d 13h", or null when no amount of waiting covers this ask and only a smaller one will.
        val wait: String?,
        val legs: String,
        val compactLegs: String,
        val danger: String,
        val compactDanger: String,
    ) : DispatchUiState

    // The sheet refuses the sale and says why, in the words of the thing that refused it — the same
    // shape `ProbeActionUiState.NothingToSurvey` already has. Both refusals are reachable on a first
    // check-in, and neither is an error state.
    data class Refuse(
        override val name: String,
        override val head: String,
        override val compactHead: String,
        val title: String,
        val note: String,
        val action: RefuseActionUiState?,
    ) : DispatchUiState
}

// A rung of the ladder. Keyed by its own duration rather than by its index, because the ladder
// **narrows** on a distant target rather than greying rungs out — so the rung at index 0 is a
// different window depending on how far away the world is.
data class WindowRungUiState(val label: String, val window: Duration, val selected: Boolean)

sealed interface RefuseActionUiState {

    // The one refusal in the app that hands back a verb. It also chains the two: a probe used to buy
    // a verdict and stop, and now it buys the right to send a ship.
    data class Probe(val label: String) : RefuseActionUiState

    // A reading, not a control — the idiom the unaffordable probe already spends. There is nothing
    // to send, so there is no button to grey out.
    data class Waiting(val label: String) : RefuseActionUiState
}
