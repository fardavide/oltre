package dev.fardavide.oltre.client.shipyard.presentation

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.component.WatchSquareUiState
import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.format.toChipLabel
import dev.fardavide.oltre.client.design.format.toCountdown
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.shipyard.ui.BuildActionUiState
import dev.fardavide.oltre.client.shipyard.ui.HullUiState
import dev.fardavide.oltre.client.shipyard.ui.ShipyardUiState
import dev.fardavide.oltre.client.shipyard.ui.YardUiState
import dev.fardavide.oltre.core.AlertCategory
import dev.fardavide.oltre.core.asksOnRow
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.HullAlert
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
        fleet = Strings.hullsInFleet(owned.total),
        hulls = FOR_SALE.map { toHullRow(it, owned = owned, now = now, timeZone = timeZone) },
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
        alert = alertFor(type),
    )
}

// **What the square shows, or that there is none.** Null whenever the yard holds nothing of this
// hull, which is the app's rule about controls rather than a special case: an idle card has no
// completion to be told about, so there is nothing to offer and nothing to grey out.
//
// Read off the *whole* queue rather than off its head, unlike `yardLine` next door — and the
// difference is the point. A footer reports the hull being made, which is one job and has to be the
// one on the slipway; the square asks about an order, and a hauler queued behind two skiffs is an
// order the player is waiting on even though its card shows no countdown at all.
private fun GameState.alertFor(type: ShipType): WatchSquareUiState? {
    // **The card loses its square under `BY_CATEGORY`, and it loses more than the other screens do.**
    // Every hull is announced there, because one switch cannot carry this control's three states —
    // off, each hull, whole order — so the middle state goes with the square. It is not lost: `One
    // per category` is *when the whole order is done*, exactly, for hulls and for everything else. So
    // the third state becomes a global preference rather than a per-order one, which is worth saying
    // out loud because it makes Delivery load-bearing for something the yard used to own.
    if (!alerts.asksOnRow(AlertCategory.HULLS)) return null
    if (yard.none { it.ship == type }) return null
    return when (hullAlerts[type]) {
        null -> WatchSquareUiState.UNASKED
        HullAlert.WHEN_ALL_DONE -> WatchSquareUiState.ASKED
        HullAlert.EACH_HULL -> WatchSquareUiState.ASKED_SEVERAL
    }
}

// "6 owned · 1 idle · 5 away · 2 building", and the four numbers are one fact seen four ways rather
// than four facts: `core` stores the idle pool, each run's manifest and the queue, so every clause
// here is a fold over what is already there and none of them can disagree with the others.
//
// **"building" is outside "owned" on purpose.** The other three add up — owned is idle plus away —
// and a hull on the slipway belongs to none of them, because it is not a hull yet. Folding it into
// the total would make the card and the heading say different things about the same fleet.
private fun GameState.poolLine(type: ShipType, owned: Ships): TextRes {
    val total = owned.countOf(type)
    val idle = ships.countOf(type)
    val away = total - idle
    val building = yard.count { it.ship == type }
    return Strings.clauses(
        listOfNotNull(
            Strings.shipsOwned(total),
            Strings.shipsIdle(idle),
            Strings.shipsAway(away).takeIf { away > 0 },
            Strings.shipsBuilding(building).takeIf { building > 0 },
        ),
    )
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
    val doneAt = head.completesAt.toLocalDateTime(timeZone).let { Strings.doneAt(it.hour, it.minute) }
    val queued = Strings.shipsQueued(behind).takeIf { behind > 0 }
    return YardUiState(
        // Ceil the remainder so a countdown only reads 00:00:00 once the hull is actually done — the
        // Colony row's rule, and the two have to agree or one screen finishes before the other.
        countdown = ((remainingMs + 999) / 1000).toCountdown(),
        progressPercent = (elapsedMs * 100 / totalMs).toInt(),
        doneAt = doneAt,
        queued = queued,
        // The two trailing clauses joined into the one run the card draws — "done 14:05 · 2 queued"
        // reads as one aside rather than as two competing readings.
        footer = Strings.clauses(listOfNotNull(doneAt, queued)),
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
        BuildActionUiState.AvailableIn(Strings.availableIn(wait.toChipLabel()))
    } else {
        BuildActionUiState.AvailableIn(Strings.availableNever())
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
// PLACEHOLDER copy, like every string the app says: content is Davide's. It is in the catalogue
// rather than in this file since #86 — a placeholder that is *catalogued* is a placeholder somebody
// can find and replace in one place, which is most of what the catalogue was bought for.
private class HullCopy(val type: ShipType, val name: TextRes, val purpose: TextRes)

// **What is on sale, and it must agree with `FleetBalance.FOR_SALE` exactly.** `shipCost` raises for
// a hull with no price, so a card drawn here for one the balance cannot price would crash the tab
// rather than dim it — and the mirror failure is worse and quieter: a hull `buildShips` will sell
// with no card is a hull nobody can buy. That shipped at 0.15 and is now a test, because it cannot
// be caught in `core`: this list is *copy*, a name and a purpose per hull, which nothing can derive.
// **The scout leads, because it is what a colony buys first.** It owns no hulls at genesis and a
// probe needs one, so this card is the first thing on the first screen a new player has a reason to
// tap — and it is a quarter of the price of the one under it.
//
// **The order of this list is the order of the cards**, and `ShipyardUiStateTest` holds the *set*
// against `FleetBalance.FOR_SALE` rather than the sequence: which hulls are sellable is `core`'s to
// say and cannot be got wrong twice, and which order they read in is a design decision this file
// owns.
private val FOR_SALE: List<HullCopy> = listOf(
    HullCopy(ShipType.SCOUT, Strings.scoutName(), Strings.scoutPurpose()),
    HullCopy(ShipType.SKIFF, Strings.skiffName(), Strings.skiffPurpose()),
    HullCopy(ShipType.HAULER, Strings.haulerName(), Strings.haulerPurpose()),
)

// **`NOT YET BUILT` is gone, and it is the promise being kept rather than a section being lost.**
// Design named one card for it — *"the Hauler ships from slice 3 as a dimmed card carrying its one
// line"* — and the Hauler has now shipped, so there was nothing left for the section to promise.
//
// **It is deleted rather than left empty**, which is this file's own rule about the `when`s it
// replaced: *"copy written before its slice is copy nobody chose — and it is a branch no test can
// reach."* An empty list, a component nothing renders and a type nothing constructs are the same
// speculation wearing a different shape, and the escort and the settler are still slices nobody has
// scheduled. The section comes back with the first hull that has a date, and `git log` is where its
// drawing is.

