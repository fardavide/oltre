package dev.fardavide.oltre.client.colony.presentation

import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.PlaceholderBalance

data class ColonyUiState(
    val metal: String,
    val metalRatePerHour: String,
    val crystal: String,
    val crystalRatePerHour: String,
    val deuterium: String,
    val deuteriumRatePerHour: String,
    val facilities: List<FacilityRowUiState>,
)

data class FacilityRowUiState(
    val building: BuildingType,
    val name: String,
    val level: Int,
    val metalCost: String,
    val crystalCost: String,
    val deuteriumCost: String,
    val affordable: Boolean,
    val locked: Boolean,
    val lockedReason: String?,
)

fun GameState.toColonyUiState(): ColonyUiState = ColonyUiState(
    metal = resources.metal.groupedByThousands(),
    metalRatePerHour = "+${PlaceholderBalance.effectiveMetalProductionPerHour(buildings).groupedByThousands()}/h",
    crystal = resources.crystal.groupedByThousands(),
    crystalRatePerHour = "+${PlaceholderBalance.effectiveCrystalProductionPerHour(buildings).groupedByThousands()}/h",
    deuterium = resources.deuterium.groupedByThousands(),
    deuteriumRatePerHour = "+${PlaceholderBalance.effectiveDeuteriumProductionPerHour(buildings).groupedByThousands()}/h",
    facilities = BuildingType.entries.map { toFacilityRow(it) },
)

private fun GameState.toFacilityRow(building: BuildingType): FacilityRowUiState {
    val level = buildings.levelOf(building)
    val cost = PlaceholderBalance.upgradeCost(building, BuildingLevel(level.value + 1))
    val locked = building == BuildingType.NANITE_FACTORY &&
        buildings.roboticsFactory.value < PlaceholderBalance.NANITE_ROBOTICS_REQUIREMENT
    return FacilityRowUiState(
        building = building,
        name = building.displayName(),
        level = level.value,
        metalCost = cost.metal.groupedByThousands(),
        crystalCost = cost.crystal.groupedByThousands(),
        deuteriumCost = cost.deuterium.groupedByThousands(),
        affordable = resources.covers(cost),
        locked = locked,
        lockedReason = if (locked) {
            "Requires Robotics ${PlaceholderBalance.NANITE_ROBOTICS_REQUIREMENT}"
        } else {
            null
        },
    )
}

private fun BuildingType.displayName(): String = when (this) {
    BuildingType.METAL_MINE -> "Metal Mine"
    BuildingType.CRYSTAL_MINE -> "Crystal Mine"
    BuildingType.DEUTERIUM_SYNTHESIZER -> "Deuterium Synth."
    BuildingType.SOLAR_PLANT -> "Solar Plant"
    BuildingType.ROBOTICS_FACTORY -> "Robotics Factory"
    BuildingType.NANITE_FACTORY -> "Nanite Factory"
}

private fun Long.groupedByThousands(): String =
    toString().reversed().chunked(3).joinToString(",").reversed()
