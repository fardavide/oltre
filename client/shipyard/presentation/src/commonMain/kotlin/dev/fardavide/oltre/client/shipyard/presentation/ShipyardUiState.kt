package dev.fardavide.oltre.client.shipyard.presentation

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.core.FleetBalance
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.ownedShips
import dev.fardavide.oltre.core.shortfallOf
import dev.fardavide.oltre.core.timeUntilAffordable

// **A price list, not a hero panel** — Design's sixth call, 2026-08-10. At one hull the tab is a
// single card and a sentence, and the sentence has to name what the hull is *for*, so that slice 4's
// "four berths at half the speed" lands as a trade rather than as a bigger number.
//
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
    // "6 owned · 1 idle · 5 away". The trailing clause is absent rather than zeroed when nothing is
    // out — "5 away" is news and "0 away" is a column that never says anything.
    val pool: String,
    // What the hull is *for*, in the one line that has to survive a second hull arriving beside it.
    val purpose: String,
    val costs: List<CostChipUiState>,
    val action: BuildActionUiState,
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

fun GameState.toShipyardUiState(): ShipyardUiState {
    val owned = ownedShips()
    return ShipyardUiState(
        fleet = owned.total.let { if (it == 1) "1 hull" else "$it hulls" },
        hulls = FOR_SALE.map { toHullRow(it, owned) },
        comingHulls = COMING.map {
            ComingHullUiState(type = it.type, name = it.name, purpose = it.purpose)
        },
    )
}

private fun GameState.toHullRow(hull: HullCopy, owned: Ships): HullUiState {
    val type = hull.type
    val cost = FleetBalance.shipCost(type, alreadyOwned = owned.countOf(type))
    val short = resources.shortfallOf(cost)
    return HullUiState(
        type = type,
        name = hull.name,
        pool = poolLine(type = type, owned = owned),
        purpose = hull.purpose,
        costs = listOfNotNull(
            cost.metal.toCostChip(ResourceKind.METAL, short),
            cost.crystal.toCostChip(ResourceKind.CRYSTAL, short),
            cost.deuterium.toCostChip(ResourceKind.DEUTERIUM, short),
        ),
        action = buildOrWait(cost),
    )
}

// "6 owned · 1 idle · 5 away", and the three numbers are one fact seen three ways rather than three
// facts: `core` stores the idle pool and each run's manifest, so *owned* and *away* are both folds
// over what is already there and none of them can disagree with the others.
private fun GameState.poolLine(type: ShipType, owned: Ships): String {
    val total = owned.countOf(type)
    val idle = ships.countOf(type)
    val away = total - idle
    val clauses = listOfNotNull(
        "$total owned",
        "$idle idle",
        "$away away".takeIf { away > 0 },
    )
    return clauses.joinToString(SEPARATOR)
}

// The ghost's contract, shared with every other screen in the app: one number with one meaning,
// which here is only ever the price — a hull waits on nothing else. There is no yard slot to be
// busy and no requirement to be unmet, so unlike Research's version this takes the maximum of one
// thing.
private fun GameState.buildOrWait(cost: Resources): BuildActionUiState {
    if (resources.covers(cost)) return BuildActionUiState.Build
    val wait = timeUntilAffordable(resources, cost, buildings, research)
    return if (wait.isFinite()) {
        BuildActionUiState.AvailableIn("in ${wait.toChipLabel()}")
    } else {
        BuildActionUiState.AvailableIn("—")
    }
}

private fun Long.toCostChip(kind: ResourceKind, short: Set<ResourceKind>): CostChipUiState? =
    takeIf { it > 0 }?.let { CostChipUiState(kind = kind, amount = it.groupedByThousands(), short = kind in short) }

// **What a hull is called and what it is for, carried by the list that decides it is drawn at all.**
// This was a pair of `when`s over `ShipType` and that was speculative in the precise sense
// `ShipType` warns about: `ESCORT` and `SETTLER` had copy nothing renders, invented for slices that
// have not been designed. Copy written before its slice is copy nobody chose — and it is a branch no
// test can reach, because the only way onto the screen is through the two lists below.
//
// **The hull's line is a promise about the next slice as much as a description of this one.** The
// skiff's has to be worth reading before a second hull exists, or "four berths at half the speed"
// arrives as a bigger number rather than as a trade — so both are the same two clauses, speed against
// hold, and read as a column.
//
// PLACEHOLDER copy, like every string the app says: content is Davide's.
private class HullCopy(val type: ShipType, val name: String, val purpose: String)

// **What is on sale, and it must stay one hull behind `FleetBalance`.** `shipCost` raises for a hull
// with no price, so a card drawn here for one the balance cannot price would crash the tab rather
// than dim it.
private val FOR_SALE: List<HullCopy> = listOf(
    HullCopy(ShipType.SKIFF, "Skiff", "One berth of hold · 10m + 1m per 10 units, one way"),
)

// **Only the Hauler is drawn**, not all three unbuilt hulls. Design's call names it by name — *"the
// Hauler ships from slice 3 as a dimmed card carrying its one line"* — and the reason it is not the
// whole remainder is the same reason the ship set has four constants rather than a dozen: the escort
// is a combat model and the settler is colonisation, so a card for either would be advertising a
// slice nobody has scheduled. The Hauler is next.
private val COMING: List<HullCopy> = listOf(
    HullCopy(ShipType.HAULER, "Hauler", "Four berths of hold, at half a skiff's speed."),
)

private const val SEPARATOR = " · "
