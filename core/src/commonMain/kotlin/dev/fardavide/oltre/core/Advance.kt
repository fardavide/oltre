package dev.fardavide.oltre.core

import kotlin.time.Instant

fun advance(state: GameState, from: Instant, to: Instant): GameState {
    val elapsedMilliseconds = (to - from).inWholeMilliseconds
    return state.copy(
        resources = state.resources.copy(
            metalFine = state.resources.metalFine +
                PlaceholderBalance.metalProductionPerHour(state.buildings.metalMine) * elapsedMilliseconds,
            crystalFine = state.resources.crystalFine +
                PlaceholderBalance.crystalProductionPerHour(state.buildings.crystalMine) * elapsedMilliseconds,
            deuteriumFine = state.resources.deuteriumFine +
                PlaceholderBalance.deuteriumProductionPerHour(state.buildings.deuteriumSynthesizer) * elapsedMilliseconds,
        ),
    )
}
