package dev.fardavide.oltre.core

import kotlin.time.Instant

// The single entry point for time. Accrues continuously between discrete events and applies
// each event exactly at its instant, so any span produces the same state as any chain of
// sub-spans (the composability property).
fun advance(state: GameState, from: Instant, to: Instant): GameState {
    val job = state.buildQueue
    if (job != null && job.completesAt > from && job.completesAt <= to) {
        val atCompletion = accrue(state, from = from, to = job.completesAt).let { accrued ->
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
        return advance(atCompletion, from = job.completesAt, to = to)
    }
    return accrue(state, from = from, to = to)
}

private fun accrue(state: GameState, from: Instant, to: Instant): GameState {
    val elapsedMilliseconds = (to - from).inWholeMilliseconds
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
