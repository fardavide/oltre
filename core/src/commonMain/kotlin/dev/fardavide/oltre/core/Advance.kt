package dev.fardavide.oltre.core

import kotlin.time.Instant

// The single entry point for time. Accrues continuously between discrete events and applies
// each event exactly at its instant, so any span produces the same state as any chain of
// sub-spans (the composability property).
fun advance(state: GameState, from: Instant, to: Instant): GameState {
    require(to >= from) { "advance must not go backwards: from=$from to=$to" }
    val nextEventAt = listOfNotNull(
        state.buildQueue?.completesAt,
        state.returningFleet?.arrivesAt,
    ).filter { it <= to }.minOrNull() ?: return accrue(state, from = from, to = to)
    // An event at or before `from` can only come from a caller resuming with a stale span;
    // apply it defensively instead of wedging it forever.
    val boundary = maxOf(nextEventAt, from)
    val atBoundary = accrue(state, from = from, to = boundary).applyEventsDueAt(nextEventAt)
    return advance(atBoundary, from = boundary, to = to)
}

private fun GameState.applyEventsDueAt(instant: Instant): GameState {
    var next = this
    val job = next.buildQueue
    if (job != null && job.completesAt == instant) {
        next = next.copy(
            buildings = next.buildings.withLevel(job.building, job.toLevel),
            buildQueue = null,
            eventLog = next.eventLog + Event.BuildCompleted(
                building = job.building,
                newLevel = job.toLevel,
                at = job.completesAt,
            ),
        )
    }
    val fleet = next.returningFleet
    if (fleet != null && fleet.arrivesAt == instant) {
        next = next.copy(
            resources = next.resources.deposit(fleet.cargo),
            returningFleet = null,
            eventLog = next.eventLog + Event.FleetReturned(
                ships = fleet.ships,
                cargo = fleet.cargo,
                at = fleet.arrivesAt,
            ),
        )
    }
    return next
}

private fun accrue(state: GameState, from: Instant, to: Instant): GameState {
    // Quantize each INSTANT to epoch-milliseconds, never the span: floor(b)-floor(a) telescopes
    // exactly across any chain of sub-spans, while flooring the span does not — real wall-clock
    // instants carry sub-ms fractions and would silently break the composability property.
    val elapsedMilliseconds = to.toEpochMilliseconds() - from.toEpochMilliseconds()
    return state.copy(
        resources = state.resources.copy(
            metalFine = minOf(
                CAP_FINE,
                state.resources.metalFine +
                    PlaceholderBalance.effectiveMetalProductionPerHour(state.buildings) * elapsedMilliseconds,
            ),
            crystalFine = minOf(
                CAP_FINE,
                state.resources.crystalFine +
                    PlaceholderBalance.effectiveCrystalProductionPerHour(state.buildings) * elapsedMilliseconds,
            ),
            deuteriumFine = minOf(
                CAP_FINE,
                state.resources.deuteriumFine +
                    PlaceholderBalance.effectiveDeuteriumProductionPerHour(state.buildings) * elapsedMilliseconds,
            ),
        ),
    )
}

private fun Resources.deposit(cargo: Resources): Resources = copy(
    metalFine = minOf(CAP_FINE, metalFine + cargo.metalFine),
    crystalFine = minOf(CAP_FINE, crystalFine + cargo.crystalFine),
    deuteriumFine = minOf(CAP_FINE, deuteriumFine + cargo.deuteriumFine),
)

private val CAP_FINE = PlaceholderBalance.STORAGE_CAPACITY * Resources.FINE_PER_UNIT
