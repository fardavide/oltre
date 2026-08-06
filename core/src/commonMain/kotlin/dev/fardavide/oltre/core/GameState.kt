package dev.fardavide.oltre.core

data class GameState(
    val resources: Resources,
    val buildings: Buildings,
    val buildQueue: BuildJob?,
    val returningFleet: ReturningFleet?,
    val eventLog: List<Event>,
) {
    companion object {
        fun initial(): GameState = GameState(
            resources = Resources.of(metal = 0),
            buildings = Buildings.initial(),
            buildQueue = null,
            returningFleet = null,
            eventLog = emptyList(),
        )
    }
}
