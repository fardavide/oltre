package dev.fardavide.oltre.client.galaxy.ui

import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.design.component.CostChipUiState

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
    data class Dispatch(val offer: ProbeOfferUiState, val label: TextRes, val compactLabel: TextRes) :
        ProbeActionUiState

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
