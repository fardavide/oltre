package dev.fardavide.oltre.client

import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.PlaceholderBalance

// The three stocks and what they are earning per hour. The rates are the *effective* ones — after
// the building curve, the research multipliers and any energy deficit — because the rail's job is
// to say what the colony is actually making, not what it would make in ideal conditions.
data class ResourceRailUiState(
    val metal: String,
    val metalRatePerHour: String,
    val crystal: String,
    val crystalRatePerHour: String,
    val deuterium: String,
    val deuteriumRatePerHour: String,
    // Whether a power shortage is holding those rates down. The rates are already the throttled
    // figures; what misled the player was a true rate presented as an untroubled one. The Colony
    // screen explains the shortage — this is only the mark that says the numbers above are it.
    val throttled: Boolean,
)

internal fun GameState.toResourceRailUiState(): ResourceRailUiState = ResourceRailUiState(
    metal = resources.metal.groupedByThousands(),
    metalRatePerHour = PlaceholderBalance.effectiveMetalProductionPerHour(buildings, research).toRate(),
    crystal = resources.crystal.groupedByThousands(),
    crystalRatePerHour = PlaceholderBalance.effectiveCrystalProductionPerHour(buildings, research).toRate(),
    deuterium = resources.deuterium.groupedByThousands(),
    deuteriumRatePerHour = PlaceholderBalance.effectiveDeuteriumProductionPerHour(buildings, research).toRate(),
    // Derived from core, not handed down from the Colony screen: energy is a rule, and the rail
    // sits above every destination including the ones that have no colony ui-state to ask.
    throttled = PlaceholderBalance.energyBalance(buildings, research).isDeficit,
)

private fun Long.toRate(): String = "+${groupedByThousands()}/h"

private fun Long.groupedByThousands(): String =
    toString().reversed().chunked(3).joinToString(",").reversed()
