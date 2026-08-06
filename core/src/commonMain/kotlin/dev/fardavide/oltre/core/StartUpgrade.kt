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
    data object AlreadyUpgrading : StartUpgradeResult
    data object InsufficientResources : StartUpgradeResult
    data object RequirementsNotMet : StartUpgradeResult
}

fun startUpgrade(state: GameState, building: BuildingType, at: Instant): StartUpgradeResult {
    // Facilities upgrade in parallel; the only queue rule is that one facility cannot be
    // upgraded twice at once. Resources are the real limiter, and they should be the only one.
    if (building in state.builds) return StartUpgradeResult.AlreadyUpgrading
    // Nanite requires Robotics 10 (mockup rule); the research half of the gate arrives in M4.
    if (building == BuildingType.NANITE_FACTORY && state.buildings.roboticsFactory.value < PlaceholderBalance.NANITE_ROBOTICS_REQUIREMENT) {
        return StartUpgradeResult.RequirementsNotMet
    }
    val toLevel = BuildingLevel(state.buildings.levelOf(building).value + 1)
    val cost = PlaceholderBalance.upgradeCost(building, toLevel)
    if (!state.resources.covers(cost)) return StartUpgradeResult.InsufficientResources
    val job = BuildJob(
        building = building,
        toLevel = toLevel,
        startedAt = at,
        completesAt = at + PlaceholderBalance.upgradeDuration(building, toLevel, state.buildings.roboticsFactory),
    )
    return StartUpgradeResult.Started(
        state.copy(
            resources = state.resources.minus(cost),
            builds = state.builds + (building to job),
            eventLog = state.eventLog + Event.BuildStarted(building = building, toLevel = toLevel, at = at),
        ),
    )
}
