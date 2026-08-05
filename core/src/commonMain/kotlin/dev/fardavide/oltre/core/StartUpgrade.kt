package dev.fardavide.oltre.core

import kotlin.time.Instant

enum class BuildingType {
    METAL_MINE,
}

data class BuildJob(
    val building: BuildingType,
    val toLevel: BuildingLevel,
    val completesAt: Instant,
)

sealed interface StartUpgradeResult {
    data class Started(val state: GameState) : StartUpgradeResult
    data object QueueBusy : StartUpgradeResult
    data object InsufficientResources : StartUpgradeResult
}

fun startUpgrade(state: GameState, building: BuildingType, at: Instant): StartUpgradeResult {
    if (state.buildQueue != null) return StartUpgradeResult.QueueBusy
    val toLevel = when (building) {
        BuildingType.METAL_MINE -> BuildingLevel(state.buildings.metalMine.value + 1)
    }
    val cost = PlaceholderBalance.upgradeCost(building, toLevel)
    if (!state.resources.covers(cost)) return StartUpgradeResult.InsufficientResources
    return StartUpgradeResult.Started(
        state.copy(
            resources = state.resources.minus(cost),
            buildQueue = BuildJob(
                building = building,
                toLevel = toLevel,
                completesAt = at + PlaceholderBalance.upgradeDuration(building, toLevel),
            ),
        ),
    )
}
