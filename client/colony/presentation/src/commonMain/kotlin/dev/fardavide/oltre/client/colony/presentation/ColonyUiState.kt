package dev.fardavide.oltre.client.colony.presentation

import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.PlaceholderBalance

data class ColonyUiState(
    val metal: String,
    val metalRatePerHour: String,
    val crystal: String,
    val crystalRatePerHour: String,
    val deuterium: String,
    val deuteriumRatePerHour: String,
)

fun GameState.toColonyUiState(): ColonyUiState = ColonyUiState(
    metal = resources.metal.groupedByThousands(),
    metalRatePerHour = "+${PlaceholderBalance.effectiveMetalProductionPerHour(buildings).groupedByThousands()}/h",
    crystal = resources.crystal.groupedByThousands(),
    crystalRatePerHour = "+${PlaceholderBalance.effectiveCrystalProductionPerHour(buildings).groupedByThousands()}/h",
    deuterium = resources.deuterium.groupedByThousands(),
    deuteriumRatePerHour = "+${PlaceholderBalance.effectiveDeuteriumProductionPerHour(buildings).groupedByThousands()}/h",
)

private fun Long.groupedByThousands(): String =
    toString().reversed().chunked(3).joinToString(",").reversed()
