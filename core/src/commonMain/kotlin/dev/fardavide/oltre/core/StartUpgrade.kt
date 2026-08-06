package dev.fardavide.oltre.core

import kotlinx.serialization.Serializable
import kotlin.time.Instant

// Enum names are on-disk identifiers in every existing save; adding a constant is free,
// renaming one is a schema break.
@Serializable
enum class BuildingType {
    METAL_MINE,
    CRYSTAL_MINE,
    DEUTERIUM_SYNTHESIZER,
    SOLAR_PLANT,
    ROBOTICS_FACTORY,
    NANITE_FACTORY,
}

@Serializable
data class BuildJob(
    val building: BuildingType,
    val toLevel: BuildingLevel,
    val startedAt: Instant,
    val completesAt: Instant,
)

sealed interface StartUpgradeResult {
    data class Started(val state: GameState) : StartUpgradeResult
    data object QueueBusy : StartUpgradeResult
    data object InsufficientResources : StartUpgradeResult
    data object RequirementsNotMet : StartUpgradeResult
}

fun startUpgrade(state: GameState, building: BuildingType, at: Instant): StartUpgradeResult {
    if (state.buildQueue != null) return StartUpgradeResult.QueueBusy
    // Nanite requires Robotics 10 (mockup rule); the research half of the gate arrives in M4.
    if (building == BuildingType.NANITE_FACTORY && state.buildings.roboticsFactory.value < PlaceholderBalance.NANITE_ROBOTICS_REQUIREMENT) {
        return StartUpgradeResult.RequirementsNotMet
    }
    val toLevel = when (building) {
        BuildingType.METAL_MINE -> BuildingLevel(state.buildings.metalMine.value + 1)
        BuildingType.CRYSTAL_MINE -> BuildingLevel(state.buildings.crystalMine.value + 1)
        BuildingType.DEUTERIUM_SYNTHESIZER -> BuildingLevel(state.buildings.deuteriumSynthesizer.value + 1)
        BuildingType.SOLAR_PLANT -> BuildingLevel(state.buildings.solarPlant.value + 1)
        BuildingType.ROBOTICS_FACTORY -> BuildingLevel(state.buildings.roboticsFactory.value + 1)
        BuildingType.NANITE_FACTORY -> BuildingLevel(state.buildings.naniteFactory.value + 1)
    }
    val cost = PlaceholderBalance.upgradeCost(building, toLevel)
    if (!state.resources.covers(cost)) return StartUpgradeResult.InsufficientResources
    return StartUpgradeResult.Started(
        state.copy(
            resources = state.resources.minus(cost),
            buildQueue = BuildJob(
                building = building,
                toLevel = toLevel,
                startedAt = at,
                completesAt = at + PlaceholderBalance.upgradeDuration(building, toLevel, state.buildings.roboticsFactory),
            ),
            eventLog = state.eventLog + Event.BuildStarted(building = building, toLevel = toLevel, at = at),
        ),
    )
}
