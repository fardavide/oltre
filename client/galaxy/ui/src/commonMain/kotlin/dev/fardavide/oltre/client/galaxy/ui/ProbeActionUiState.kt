package dev.fardavide.oltre.client.galaxy.ui

import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.component.WatchSquareUiState

// **The verb lives in the footer of the system card, under the orbits.** The map is the only
// star-scoped object on the screen and a probe targets a star, so the star-scoped verb goes in it —
// the same rule that already puts a build's countdown inside its facility row rather than in a hero
// panel above the list. Everything the probe ever says, from the price to the receipt, lands in the
// card that owns the thing it describes.
//
// Not the header: that is navigation, and a purchase there would be the only buyable thing in the
// app sitting in a nav bar. Not a world row: a row-level button multiplies by 4.75 and asks the
// player to pick a slot for a flight that surveys all fifteen.
sealed interface ProbeActionUiState {

    // Cost then time, left to right, because the cost never moves and the time is the whole
    // purchase. The eye learns where the changing number is and stops reading the other one.
    //
    // **The bell rides with the verb and only with the verb.** This is the second of the two places
    // a probe can be bought — the dispatch sheet's unsurveyed refusal is the other — and the ask has
    // to be reachable from both, or which door a player came through would decide whether they hear
    // about the landing. The other five states below carry none: `Unaffordable` sells nothing today,
    // `InFlight` is a flight whose answer was fixed when it left, and the last three have no flight
    // at all.
    data class Dispatch(
        val offer: ProbeOfferUiState,
        val label: TextRes,
        val compactLabel: TextRes,
        // **Null under `AlertMode.BY_CATEGORY`, which is the sheet's call 1 reaching the one control
        // that is not on a row.** A probe is announced by its kind there, so the bell has nothing
        // left to decide — and absence is how this app says that, on a footer as on a row.
        val announce: WatchSquareUiState?,
        // **The one held control in the app with nowhere of its own to say which way it went**, and
        // Design raised it rather than solving it: this footer is one 58dp line and that line is the
        // round trip. The fix taken here is Design's own suggestion — while the bell is held, the
        // line carries the held string instead, *because a round trip you cannot fly is not the
        // useful fact*. Null with signal, and then the flight is back.
        val announceHeld: TextRes?,
        // **A probe cannot be held either**, so the verb refuses at the tap and the same line says
        // so, in red. It outranks the bell's line when both are present: the refusal is about the
        // thing the player has just pressed.
        //
        // One string rather than a lead and a body, unlike the sheet's block: on a line there is no
        // room for two, and joining two clauses is a decision about language that a `ui` module does
        // not make. See `Strings.sentences`.
        val refusal: TextRes?,
    ) : ProbeActionUiState

    // The committed idiom, unchanged: the chip reddens for the one resource you are short of and
    // the verb becomes a ghost carrying the wait. Two durations then share a row — "flight 39m" and
    // "in 1h 06m" — told apart by side, by colour and by the preposition. It is the tightest
    // reading on the screen.
    data class Unaffordable(val offer: ProbeOfferUiState, val availableIn: TextRes) : ProbeActionUiState

    // The three parts a running build already draws, in that order. No cancel: nothing in the app
    // cancels, and the state after the tap is its own receipt.
    data class InFlight(val countdown: TextRes, val lands: TextRes, val progressPercent: Int) :
        ProbeActionUiState

    // A receipt for a flight that was paid for. `find` is the honest half and is usually "none
    // settleable" — saying it in the same breath as the count is what keeps a run of these reading
    // as calibration rather than as bad luck, which is the job the Barren row's threshold already
    // does.
    data class Landed(
        val landedAt: TextRes,
        val summary: TextRes,
        val find: TextRes,
        val findKind: ProbeFindKind,
    ) : ProbeActionUiState

    // Home, which was never flown to. One tertiary line, no receipt, no verb.
    data class Charted(val note: TextRes) : ProbeActionUiState

    // The card refuses the sale and says why, in the words of the thing above it: fifteen ticks, no
    // dots. **It never says "already surveyed", because nothing was** — and `startSurvey` really
    // does refuse, because `hasSurveyed` is vacuously true where there is nothing to survey.
    data class NothingToSurvey(val note: TextRes) : ProbeActionUiState
}

// The price and the flight. `costWord` is its own field rather than part of a sentence because it
// is what 320dp drops — along with the word "flight" and the word "probe" on the button. Two words
// and no figures: 150 stays, 1h 22m stays, and the red chip still reddens. The colour does the work
// the word did.
data class ProbeOfferUiState(
    val cost: CostChipUiState,
    val costWord: TextRes,
    val flight: TextRes,
    val compactFlight: TextRes,
)

// Green once, and only on the count. `NEAR_MISS` is the middle tier — a world blocked on a single
// axis, about one dispatch in fourteen — and it is neither green nor red: a fact that points at
// Research, in body weight, because it is worth reading and not worth acting on today.
enum class ProbeFindKind { NONE, SETTLEABLE, NEAR_MISS }
