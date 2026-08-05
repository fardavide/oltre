package dev.fardavide.oltre.core

import kotlin.time.Instant

fun advance(state: GameState, from: Instant, to: Instant): GameState {
    val elapsedMilliseconds = (to - from).inWholeMilliseconds
    val producedFine = PlaceholderBalance.METAL_PRODUCTION_PER_HOUR * elapsedMilliseconds
    return state.copy(
        resources = state.resources.copy(metalFine = state.resources.metalFine + producedFine),
    )
}
