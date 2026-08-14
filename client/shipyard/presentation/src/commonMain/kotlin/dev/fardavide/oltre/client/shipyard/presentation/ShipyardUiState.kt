package dev.fardavide.oltre.client.shipyard.presentation

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.format.pad2
import dev.fardavide.oltre.client.design.format.toCountdown
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.shipyard.ui.BuildActionUiState
import dev.fardavide.oltre.client.shipyard.ui.ComingHullUiState
import dev.fardavide.oltre.client.shipyard.ui.HullUiState
import dev.fardavide.oltre.client.shipyard.ui.ShipyardUiState
import dev.fardavide.oltre.client.shipyard.ui.YardUiState
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.ownedShips
import dev.fardavide.oltre.core.priceOf
import dev.fardavide.oltre.core.shortfallOf
import dev.fardavide.oltre.core.timeUntilAffordable
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

// **Everything the Shipyard tab decides.** The types it produces live in `:client:shipyard:ui`,
// which knows nothing about `GameState` — this is the one file that prices a hull, reads the
// slipway and writes what a card says.

// `now` is what the yard needs and nothing else on this screen does: every price here is a pure
// function of the state, and a countdown is not. It is the same parameter the Colony screen takes,
// for the same reason and read the same way.
fun GameState.toShipyardUiState(now: Instant, timeZone: TimeZone): ShipyardUiState {
    val owned = ownedShips()
    return ShipyardUiState(
        // **The fleet that exists, not the fleet that is paid for.** A hull on the slipway cannot be
        // sent, so counting it here would put a number on the heading that the Fleets tab disagrees
        // with. What it *does* count against is the price, one line down.
        fleet = owned.total.let { if (it == 1) "1 hull" else "$it hulls" },
        hulls = FOR_SALE.map { toHullRow(it, owned = owned, now = now, timeZone = timeZone) },
        comingHulls = COMING.map {
            ComingHullUiState(type = it.type, name = it.name, purpose = it.purpose)
        },
    )
}

private fun GameState.toHullRow(
    hull: HullCopy,
    owned: Ships,
    now: Instant,
    timeZone: TimeZone,
): HullUiState {
    val type = hull.type
    // **The chips are the verb's own answer, asked for rather than reconstructed.** This line used to
    // call `FleetBalance.shipCost` with a fleet count it derived itself, which was a second copy of
    // the rule inside `buildShips` — so the card could quote a price the tap would not honour, and
    // only a comment and a behaviour test stood between the two. `priceOf` is the same function the
    // verb charges from, so they cannot disagree, and a price rule that changes does not reach this
    // file at all.
    val cost = priceOf(Ships.of(type, 1))
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
        yard = yardLine(type = type, now = now, timeZone = timeZone),
    )
}

// "6 owned · 1 idle · 5 away · 2 building", and the four numbers are one fact seen four ways rather
// than four facts: `core` stores the idle pool, each run's manifest and the queue, so every clause
// here is a fold over what is already there and none of them can disagree with the others.
//
// **"building" is outside "owned" on purpose.** The other three add up — owned is idle plus away —
// and a hull on the slipway belongs to none of them, because it is not a hull yet. Folding it into
// the total would make the card and the heading say different things about the same fleet.
private fun GameState.poolLine(type: ShipType, owned: Ships): String {
    val total = owned.countOf(type)
    val idle = ships.countOf(type)
    val away = total - idle
    val building = yard.count { it.ship == type }
    val clauses = listOfNotNull(
        "$total owned",
        "$idle idle",
        "$away away".takeIf { away > 0 },
        "$building building".takeIf { building > 0 },
    )
    return clauses.joinToString(SEPARATOR)
}

// The head of the queue for this hull, or null when there is none. Read off the *first* entry rather
// than searched for, because the yard is serial: the one being made is the one at the front, and a
// `firstOrNull { it.ship == type }` would quietly start reporting the wrong job the day a second
// hull is on sale and a Hauler is ahead of a Skiff in the queue.
private fun GameState.yardLine(type: ShipType, now: Instant, timeZone: TimeZone): YardUiState? {
    val head = yard.firstOrNull() ?: return null
    if (head.ship != type) return null
    val totalMs = (head.completesAt.toEpochMilliseconds() - head.startedAt.toEpochMilliseconds()).coerceAtLeast(1)
    val elapsedMs = (now.toEpochMilliseconds() - head.startedAt.toEpochMilliseconds()).coerceIn(0, totalMs)
    val remainingMs = (head.completesAt.toEpochMilliseconds() - now.toEpochMilliseconds()).coerceAtLeast(0)
    val behind = yard.size - 1
    return YardUiState(
        // Ceil the remainder so a countdown only reads 00:00:00 once the hull is actually done — the
        // Colony row's rule, and the two have to agree or one screen finishes before the other.
        countdown = ((remainingMs + 999) / 1000).toCountdown(),
        progressPercent = (elapsedMs * 100 / totalMs).toInt(),
        doneAt = head.completesAt.toLocalDateTime(timeZone).let { "done ${it.hour.pad2()}:${it.minute.pad2()}" },
        queued = "$behind queued".takeIf { behind > 0 },
    )
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
