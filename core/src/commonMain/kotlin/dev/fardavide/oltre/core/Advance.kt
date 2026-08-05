package dev.fardavide.oltre.core

import kotlin.time.Instant

// The single entry point for time. Accrues continuously between discrete events and applies
// each event exactly at its instant, so any span produces the same state as any chain of
// sub-spans (the composability property).
fun advance(state: GameState, from: Instant, to: Instant): GameState {
    require(to >= from) { "advance must not go backwards: from=$from to=$to" }
    val job = state.buildQueue
    if (job != null && job.completesAt <= to) {
        // A completion at or before `from` can only come from a caller resuming with a stale
        // span; apply it defensively instead of wedging the queue forever.
        val boundary = maxOf(job.completesAt, from)
        val atCompletion = accrue(state, from = from, to = boundary).let { accrued ->
            accrued.copy(
                buildings = accrued.buildings.withLevel(job.building, job.toLevel),
                buildQueue = null,
                eventLog = accrued.eventLog + Event.BuildCompleted(
                    building = job.building,
                    newLevel = job.toLevel,
                    at = job.completesAt,
                ),
            )
        }
        return advance(atCompletion, from = boundary, to = to)
    }
    return accrue(state, from = from, to = to)
}

private fun accrue(state: GameState, from: Instant, to: Instant): GameState {
    // Quantize each INSTANT to epoch-milliseconds, never the span: floor(b)-floor(a) telescopes
    // exactly across any chain of sub-spans, while flooring the span does not — real wall-clock
    // instants carry sub-ms fractions and would silently break the composability property.
    val elapsedMilliseconds = to.toEpochMilliseconds() - from.toEpochMilliseconds()
    val capFine = PlaceholderBalance.STORAGE_CAPACITY * Resources.FINE_PER_UNIT
    return state.copy(
        resources = state.resources.copy(
            metalFine = minOf(
                capFine,
                state.resources.metalFine +
                    PlaceholderBalance.effectiveMetalProductionPerHour(state.buildings) * elapsedMilliseconds,
            ),
            crystalFine = minOf(
                capFine,
                state.resources.crystalFine +
                    PlaceholderBalance.effectiveCrystalProductionPerHour(state.buildings) * elapsedMilliseconds,
            ),
            deuteriumFine = minOf(
                capFine,
                state.resources.deuteriumFine +
                    PlaceholderBalance.effectiveDeuteriumProductionPerHour(state.buildings) * elapsedMilliseconds,
            ),
        ),
    )
}
