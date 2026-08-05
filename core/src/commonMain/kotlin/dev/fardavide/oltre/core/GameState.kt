package dev.fardavide.oltre.core

data class GameState(
    val resources: Resources,
    val buildings: Buildings,
) {
    companion object {
        fun initial(): GameState = GameState(
            resources = Resources.of(metal = 0),
            buildings = Buildings.initial(),
        )
    }
}
