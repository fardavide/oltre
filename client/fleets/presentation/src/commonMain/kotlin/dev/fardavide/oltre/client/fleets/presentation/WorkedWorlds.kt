package dev.fardavide.oltre.client.fleets.presentation

import dev.fardavide.oltre.client.design.format.groupedByThousands
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.fleets.ui.WorkedListUiState
import dev.fardavide.oltre.client.fleets.ui.WorkedWorldUiState
import dev.fardavide.oltre.client.world.ui.WorldPortraitUiState
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.worldAt
import dev.fardavide.oltre.core.worldNameAt
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// **Worlds worked**, folded out of the event log. Claude Design's one move, 2026-08-16: the list
// stops being made of runs and starts being made of worlds, because eleven runs are five worlds and
// a row that is a world can carry facts a single landing never had.
//
// The fold costs no state at all — it is the first player-facing use the append-only log has ever
// had, and the second reading of it after the ledger it replaces.

internal fun GameState.toWorkedListUiState(now: Instant, since: Instant, timeZone: TimeZone): WorkedListUiState? {
    val returns = eventLog.filterIsInstance<Event.FleetReturned>()
    if (returns.isEmpty()) return null

    // Split before folding, because the two halves are different *kinds* of thing rather than two
    // cases of one: a landing with a target is a world, and a landing without one is an amount.
    val (recorded, unrecorded) = returns.partition { it.from != null }
    val rows = recorded
        .groupBy { checkNotNull(it.from) }
        .mapNotNull { (at, landings) -> toWorkedWorld(at = at, landings = landings, now = now, since = since, timeZone = timeZone) }
        // Most recent landing first, which is the order the eye can see — and the reason 320dp is
        // allowed to drop the words "newest first" from the trailing.
        .sortedByDescending { it.lastLandedAt }

    val runs = rows.sumOf { it.runs }
    return WorkedListUiState(
        trailing = Strings.clauses(listOf(Strings.runCount(runs), Strings.workedNewestFirst())),
        compactTrailing = Strings.runCount(runs),
        rows = rows.map { it.row },
        unrecorded = unrecorded.takeIf { it.isNotEmpty() }?.let { earlier ->
            val cargo = earlier.map { it.cargo }
            val kind = cargo.dominantKind()
            // Never "unknown" and never a dash in a column: the metal is still yours and the line
            // says so — it just has nowhere to send you.
            Strings.unrecordedRuns(
                count = earlier.size,
                total = cargo.total(kind).groupedByThousands(),
                kind = kind,
            )
        },
    )
}

// A row and the instant it is sorted on. The instant is not on the ui-state: what a row *says* is
// "landed 11:04" or nothing at all, and an ordering key is not something a screen renders.
private class WorkedWorld(val row: WorkedWorldUiState, val runs: Int, val lastLandedAt: Instant)

// Null when the seed no longer produces a world at that address, which cannot happen on a save whose
// seed is fixed — but a list of worlds may not invent one, and a row with no world has no name, no
// face and no deposit to print.
private fun GameState.toWorkedWorld(
    at: GalaxyCoordinate,
    landings: List<Event.FleetReturned>,
    now: Instant,
    since: Instant,
    timeZone: TimeZone,
): WorkedWorld? {
    val world = worldAt(galaxy.seed, at) ?: return null
    val cargo = landings.map { it.cargo }
    // **The resource it has actually paid the most of, rather than the one it is richest in.** A
    // player may have worked the lesser vein on purpose, and the row is a record of what happened.
    val kind = cargo.dominantKind()
    val deposit = depositReading(at = at, kind = kind, now = now)
    val lastLandedAt = landings.maxOf { it.at }
    val local = lastLandedAt.toLocalDateTime(timeZone)
    val runs = landings.size
    return WorkedWorld(
        runs = runs,
        lastLandedAt = lastLandedAt,
        row = WorkedWorldUiState(
            at = at,
            // A generated name: outside the catalogue by construction — see `TextRes.Raw`.
            name = TextRes(worldNameAt(galaxy.seed, at)),
            portrait = WorldPortraitUiState.Surveyed(
                temperature = world.traits.temperature,
                gravity = world.traits.gravity,
                pressure = world.traits.pressure,
                hazards = world.traits.hazards,
                hasRing = world.hasRing,
            ),
            total = Strings.amountOfResource(cargo.total(kind).groupedByThousands(), kind),
            kind = kind,
            prefix = Strings.worldRowPrefix(address = at.label(), runs = Strings.runCount(runs)),
            compactPrefix = Strings.runCount(runs),
            deposit = deposit.reading,
            depositIsEmpty = deposit.isEmpty,
            // **Only inside the span this launch advanced**, which is the same derivation the
            // discovery card makes — so no seen-flag and no new stored state. It carries the verb,
            // because a bare clock in Oltre is a countdown.
            landed = Strings.landedAt(hour = local.hour, minute = local.minute)
                .takeIf { lastLandedAt in since..now },
        ),
    )
}

// What the row says is left, and whether that reading is the one that means *this door leads
// nowhere*. **Both come off one read**, because two calls could disagree about one vein.
private class Deposit(val reading: TextRes, val isEmpty: Boolean)

// 0.9's deposit idiom, unchanged: the remaining figure, or a word at each end.
private fun GameState.depositReading(at: GalaxyCoordinate, kind: ResourceKind, now: Instant): Deposit {
    // **Deuterium has no deposit anywhere, and both `depositCap` and `remaining` throw rather than
    // answering.** A run cannot gather it — `startRun` is guarded to metal and crystal — but
    // `Event.FleetReturned` is the wider type, so a log can hold one and this is the row that would
    // have to draw it. "empty" is true of ground that never held any, and it is already the reading
    // that means *nothing here for you*.
    if (kind == ResourceKind.DEUTERIUM) return Deposit(reading = Strings.depositEmptyWord(), isEmpty = true)
    val cap = galaxy.depositCap(at, kind)
    val remaining = galaxy.remaining(at, kind, now)
    return when {
        cap == null || remaining <= 0 -> Deposit(reading = Strings.depositEmptyWord(), isEmpty = true)
        remaining >= cap -> Deposit(reading = Strings.depositFullWord(), isEmpty = false)
        else -> Deposit(reading = remaining.groupedByThousands(), isEmpty = false)
    }
}

// Whichever resource the most of has come home in. Ties go to the earlier entry of `ResourceKind`,
// which is metal — arbitrary, and only reachable by a world that has paid two resources exactly
// equally, where either answer is as true as the other.
//
// Summed across the landings rather than over one basket, because `Resources` has no addition and
// wants none: it models a stock the colony holds, and a pile of cargo manifests is not one.
private fun List<Resources>.dominantKind(): ResourceKind = ResourceKind.entries.maxBy { total(it) }

private fun List<Resources>.total(kind: ResourceKind): Long = sumOf { it.of(kind) }

private fun GalaxyCoordinate.label(): TextRes = Strings.coordinate(galaxy, system, slot)

private fun Resources.of(kind: ResourceKind): Long = when (kind) {
    ResourceKind.METAL -> metal
    ResourceKind.CRYSTAL -> crystal
    ResourceKind.DEUTERIUM -> deuterium
}
