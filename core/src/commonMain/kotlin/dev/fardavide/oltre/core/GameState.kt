package dev.fardavide.oltre.core

import kotlinx.serialization.Serializable

@Serializable
data class GameState(
    val resources: Resources,
    val buildings: Buildings,
    // Upgrades run in parallel across facilities, one job per facility — the map key is the
    // rule. Ordering never matters here: `advance` picks the next completion by instant.
    val builds: Map<BuildingType, BuildJob>,
    val returningFleet: ReturningFleet?,
    val eventLog: List<Event>,
) {
    companion object {
        fun initial(): GameState = GameState(
            resources = PlaceholderBalance.startingResources(),
            buildings = Buildings.initial(),
            builds = emptyMap(),
            returningFleet = null,
            eventLog = emptyList(),
        )
    }
}
