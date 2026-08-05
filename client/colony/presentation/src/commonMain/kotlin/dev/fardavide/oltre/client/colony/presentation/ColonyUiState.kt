package dev.fardavide.oltre.client.colony.presentation

import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.PlaceholderBalance

data class ColonyUiState(
    val metal: String,
    val metalRatePerHour: String,
)

fun GameState.toColonyUiState(): ColonyUiState = ColonyUiState(
    metal = resources.metal.groupedByThousands(),
    metalRatePerHour = "+${PlaceholderBalance.METAL_PRODUCTION_PER_HOUR.groupedByThousands()}/h",
)

private fun Long.groupedByThousands(): String =
    toString().reversed().chunked(3).joinToString(",").reversed()
