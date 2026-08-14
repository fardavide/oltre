package dev.fardavide.oltre.client.shipyard.ui

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.core.ShipType

// **What the Shipyard tab draws, and nothing about how it is derived.** The mapping from `GameState`
// into these types is `:client:shipyard:presentation`, which depends on this module rather than the
// other way round.

// **This tab owns the cannot-afford state**, which is the other half of that call and the reason the
// dispatch sheet has none: a run is free, the hull was the price, so the Shipyard is the only place
// in the fleet arc where a player can be short of something.
data class ShipyardUiState(
    // "6 hulls" beside the section rule — the fleet as one number, because the per-hull breakdown is
    // on the card that can act on it.
    val fleet: String,
    val hulls: List<HullUiState>,
    // The hulls a slice has not reached yet, drawn as dimmed cards carrying one line each: the
    // system's own rule for a thing that is coming and is not here. Never empty while the ship set
    // is scheduled to grow, which it is twice.
    val comingHulls: List<ComingHullUiState>,
)

data class HullUiState(
    val type: ShipType,
    val name: String,
    // "6 owned · 1 idle · 5 away · 2 building". A clause is absent rather than zeroed when it has
    // nothing to report — "5 away" is news and "0 away" is a column that never says anything.
    val pool: String,
    // What the hull is *for*, in the one line that has to survive a second hull arriving beside it.
    val purpose: String,
    val costs: List<CostChipUiState>,
    val action: BuildActionUiState,
    // The slipway, or null when it is empty. **Beside the action rather than instead of it**, which
    // is the one thing on this card that is not the Colony row's treatment: a facility that is
    // building cannot be started again, and a yard that is busy can always be given more. So the
    // verb stays live and this is a reading underneath it.
    val yard: YardUiState?,
)

// A hull being made, in the idiom the Colony's `Upgrading` row already spends — a countdown, a bar
// and the wall-clock instant — plus the one reading a facility row has never needed.
data class YardUiState(
    val countdown: String,
    // **The head job's progress, not the queue's.** A bar measuring the whole order would sit near a
    // tenth with three hulls queued and crawl for hours, which is the opposite of what a bar is for.
    // What the player is waiting for is the next hull.
    val progressPercent: Int,
    val doneAt: String,
    // "2 queued", or null when the one on the slipway is the only one. Absent rather than "0 queued"
    // for the pool line's reason, and it is the only place the serial queue is visible as a queue.
    val queued: String?,
)

// A card that is drawn and cannot be bought, because the slice that gives the hull a job has not
// landed. It carries a name and a sentence and no price at all — inventing one would be
// indistinguishable, to a reader, from a number somebody chose. See `FleetBalance.shipCost`.
data class ComingHullUiState(val type: ShipType, val name: String, val purpose: String)

sealed interface BuildActionUiState {

    data object Build : BuildActionUiState

    // The ghost the rest of the app already spends, carrying a time rather than a dead control: a
    // player who wants the hull they cannot afford yet is told **when**, not told no. "—" when the
    // binding resource has no production at all, because "in 2,000,000h" is a worse lie than nothing.
    data class AvailableIn(val label: String) : BuildActionUiState
}
