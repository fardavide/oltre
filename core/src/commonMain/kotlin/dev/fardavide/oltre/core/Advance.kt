package dev.fardavide.oltre.core

import kotlin.time.Instant

fun advance(state: GameState, from: Instant, to: Instant): GameState {
    val elapsedMilliseconds = (to - from).inWholeMilliseconds
    return state.copy(
        resources = state.resources.copy(
            metalFine = state.resources.metalFine +
                PlaceholderBalance.METAL_PRODUCTION_PER_HOUR * elapsedMilliseconds,
            crystalFine = state.resources.crystalFine +
                PlaceholderBalance.CRYSTAL_PRODUCTION_PER_HOUR * elapsedMilliseconds,
            deuteriumFine = state.resources.deuteriumFine +
                PlaceholderBalance.DEUTERIUM_PRODUCTION_PER_HOUR * elapsedMilliseconds,
        ),
    )
}
