package dev.fardavide.oltre.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

// PLACEHOLDER balance numbers — every value here is provisional until decided on the Notion
// page or by Davide. This object is the single place placeholders live; never scatter literals.
object PlaceholderBalance {
    const val METAL_PRODUCTION_PER_HOUR: Long = 3_600
    const val CRYSTAL_PRODUCTION_PER_HOUR: Long = 1_800
    const val DEUTERIUM_PRODUCTION_PER_HOUR: Long = 900

    // Linear placeholder curve; the real curve comes from Notion or sim tuning.
    fun metalProductionPerHour(level: BuildingLevel): Long =
        METAL_PRODUCTION_PER_HOUR * level.value

    fun crystalProductionPerHour(level: BuildingLevel): Long =
        CRYSTAL_PRODUCTION_PER_HOUR * level.value

    fun deuteriumProductionPerHour(level: BuildingLevel): Long =
        DEUTERIUM_PRODUCTION_PER_HOUR * level.value

    // Exponential placeholder curves: cost doubles per level, duration grows linearly.
    fun upgradeCost(building: BuildingType, toLevel: BuildingLevel): Resources = when (building) {
        BuildingType.METAL_MINE -> Resources.of(
            metal = 60L shl (toLevel.value - 1),
            crystal = 15L shl (toLevel.value - 1),
        )
    }

    fun upgradeDuration(building: BuildingType, toLevel: BuildingLevel): Duration = when (building) {
        BuildingType.METAL_MINE -> (10 * toLevel.value).minutes
    }
}
