package dev.fardavide.oltre.client.colony.ui

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.component.RowSheetUiState
import dev.fardavide.oltre.client.design.component.SheetAction
import dev.fardavide.oltre.client.design.component.SheetFooter
import dev.fardavide.oltre.client.design.component.SheetLadderStep
import dev.fardavide.oltre.client.design.component.SheetLine
import dev.fardavide.oltre.client.design.component.SheetPointer
import dev.fardavide.oltre.client.design.component.VerdictUiState
import dev.fardavide.oltre.client.design.component.WatchUiState
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType

// **What the Colony tab draws, and nothing about how it is derived.** The mapping from `GameState`
// into these types is `:client:colony:presentation`, which depends on this module rather than the
// other way round — a ui module is a leaf. `BuildingType` and `BuildingLevel` come from `core` and
// are the judgement the rule allows: the row is keyed by a facility, and a screen that carried a
// `String` there would hand the shell back a name to parse.

// The stocks and their rates are deliberately absent: the resource rail is the shell's chrome
// now, because it frames every destination rather than only this one.
data class ColonyUiState(
    val energy: EnergyUiState,
    val facilities: List<FacilityRowUiState>,
    val returningFleet: ReturningFleetUiState?,
    // "watching Metal Mine" over the facility list, and null when nothing is watched. It names the
    // watched row **even when that row is on another screen**, which is the whole answer to a slot
    // shared with research and adaptation: one watch exists in the game, and this is where you read
    // which. Handed in rather than derived, because what a technology is called is not the colony's
    // to know — see the same field on `ResearchUiState`.
    val watching: String?,
)

// Energy is not shown as a fourth resource, because it is not one: it never accumulates, so a
// stock and a per-hour rate would both be lies. It reads as a verdict — the consequence, which
// is the only reason a player needs the number at all — over a track, over the two terms.
//
// A deficit is the normal state and it arrives almost immediately, so this is present in both
// states rather than appearing when things go wrong: the healthy reading is what teaches the
// mechanic, long before the player is confused by it.
data class EnergyUiState(
    val verdict: String,
    val terms: String,
    // The green length of the track, which spans the larger of the two terms. Healthy, the fill
    // is the draw and the empty tail is the headroom. In deficit the fill is what the plant
    // actually supplies, so the boundary is the plant's ceiling and the amber tail is how far
    // past it the colony has been built.
    val coveredFraction: Float,
    val deficit: Boolean,
)

// This facility's own contribution to the balance, shown only while the colony is in deficit.
// It carries the facility's draw rather than the headline percentage: each mine floors
// independently, so three cards reading "55%" would each be slightly wrong, where the draw is
// exactly true per card and is the number that changes when the player acts.
data class FacilityPowerUiState(
    val label: String,
    val supply: Boolean,
)

data class ReturningFleetUiState(
    val title: String,
    val subtitle: String,
    val countdown: String,
)

data class FacilityRowUiState(
    val building: BuildingType,
    val name: String,
    // What the row is called when the window is a Slide Over pane. One name differs — "Robotics
    // Factory" becomes "Robotics", which is what the game already calls it in "Requires Robotics 10"
    // — and it differs because it is the one name the square's 29dp would otherwise clip mid-word,
    // which this app never does. The other five fit at both widths and are the same string twice.
    val compactName: String,
    val level: BuildingLevel,
    val costs: List<CostChipUiState>,
    val duration: String,
    val action: FacilityActionUiState,
    // Null while the colony is healthy, and on anything that neither draws nor supplies — an
    // unbuilt facility draws nothing, so it has nothing to attribute and nothing to fight the
    // locked row's dim.
    val power: FacilityPowerUiState?,
    // Only the Solar Plant, only in a deficit, and only when one more level would end it. It
    // sits in the slot a card already uses to say what its next level is, which is why it is a
    // specification rather than a nag.
    val fix: String?,
    // The square beside the ghost time, and null on every row that has no instant to book: an
    // affordable row is not waiting for anything, a building one is already the thing happening, a
    // locked one has no price yet, and a row whose binding resource has no net income never reaches
    // its price at all. See `WatchUiState`.
    val watch: WatchUiState?,
    // What one more level is worth to *this* colony now, in the slot the adaptation ladder has spent
    // on its shortlist since that line shipped. Null in the two states where nobody is choosing: a
    // row in flight, where the decision was made when the player tapped the action and the slot
    // belongs to the countdown, and a row at its ceiling, where there is no level left to price.
    val verdict: VerdictUiState?,
    // Everything the sheet says that the row does not already say. The rest of `RowSheetUiState` is
    // assembled from this row — see `toRowSheetUiState` — which is what keeps the sheet a second
    // rendering of a state the screen already holds rather than a second state.
    val detail: FacilityDetailUiState,
    // True on at most one row, and only for the first couple of seconds after a launch: this is the
    // upgrade that landed while the app was closed. The row answers it with a band of light crossing
    // the card once and a level badge that changes behind the band.
    //
    // It is a fact about *this launch* rather than about the colony, which is why it lives on the
    // row rather than on the action: the same colony rendered a minute later has the same levels and
    // nothing to announce.
    val finishedWhileAway: Boolean,
)

// The three things a row cannot say in one clause: the arithmetic behind a verdict that reads
// "nothing", the ladder of what the level gates, and the row worth reading instead.
//
// Held as design-system types rather than as prose, for the reason `SheetLine` states next door — a
// mapper's test can assert the figures without parsing anything, and what "picked out" looks like
// stays the component's business.
data class FacilityDetailUiState(
    val lines: List<SheetLine>,
    val ladder: List<SheetLadderStep>,
    val pointer: SheetPointer?,
)

sealed interface FacilityActionUiState {
    data object Upgrade : FacilityActionUiState
    data class AffordableIn(val label: String) : FacilityActionUiState
    data class Locked(val reason: String) : FacilityActionUiState

    // Builds run in parallel, so progress belongs to the facility that is building rather than
    // to a single card at the top of the screen.
    data class Upgrading(
        val toLevel: BuildingLevel,
        val countdown: String,
        val progressPercent: Int,
        val doneAt: String,
    ) : FacilityActionUiState
}

// ── The sheet, assembled from the row it came from ───────────────────────────────────────────
//
// **Here rather than one layer up, and the test is who reads it.** `ColonyScreen` derives the sheet
// where it is opened, so a ui module that could not do this would have to be handed a second state
// it already holds the ingredients for. Nothing about it looks at `GameState`: it is one rendering
// of a row into another, which is what a ui module is allowed to do and a presentation module is
// not needed for.
fun FacilityRowUiState.toRowSheetUiState(): RowSheetUiState = RowSheetUiState(
    // The full name, never the compact one — the sheet is the full width of the window.
    name = name,
    level = level.value,
    verdict = sheetHeading(),
    lines = detail.lines,
    ladder = detail.ladder,
    pointer = detail.pointer,
    footer = when (val current = action) {
        FacilityActionUiState.Upgrade -> SheetFooter(
            costs = costs,
            duration = duration,
            action = SheetAction.Live("Upgrade"),
        )
        is FacilityActionUiState.AffordableIn -> SheetFooter(
            costs = costs,
            duration = duration,
            action = SheetAction.Ghost(current.label),
        )
        // A locked row has no price yet and a running one has already been paid for. Both end on
        // what to do about it instead.
        is FacilityActionUiState.Locked,
        is FacilityActionUiState.Upgrading,
        -> null
    },
)

// The sentence the player has just read on the row, repeated where the sheet answers a question they
// still have in mind. It is the **compact** verdict when the sheet carries a ladder, because the
// clause a narrow row drops is the ladder and the sheet is about to say it in full.
private fun FacilityRowUiState.sheetHeading(): String = when (val current = action) {
    is FacilityActionUiState.Locked -> current.reason
    is FacilityActionUiState.Upgrading -> current.becomes()
    FacilityActionUiState.Upgrade,
    is FacilityActionUiState.AffordableIn,
    -> verdict?.let { if (detail.ladder.isEmpty()) it.label else it.compactLabel }.orEmpty()
}

// The accent line a running row already carries, authored once and read twice: the card draws it in
// the "→ becomes" slot and the sheet repeats it where the verdict would have been. A row that said
// one thing and a sheet that said another would be the worst failure this pass has available.
internal fun FacilityActionUiState.Upgrading.becomes(): String = "→ LV ${toLevel.value} · $doneAt"
