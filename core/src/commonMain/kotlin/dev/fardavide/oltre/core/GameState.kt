package dev.fardavide.oltre.core

data class GameState(
    val resources: Resources,
) {
    companion object {
        fun initial(): GameState = GameState(resources = Resources.of(metal = 0))
    }
}
