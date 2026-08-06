package dev.fardavide.oltre.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

enum class ResourceKind { METAL, CRYSTAL, DEUTERIUM }

fun Resources.shortfallOf(cost: Resources): Set<ResourceKind> = buildSet {
    if (metalFine < cost.metalFine) add(ResourceKind.METAL)
    if (crystalFine < cost.crystalFine) add(ResourceKind.CRYSTAL)
    if (deuteriumFine < cost.deuteriumFine) add(ResourceKind.DEUTERIUM)
}

// Fine units are hourly-rate × milliseconds, so deficitFine / ratePerHour is exactly the
// milliseconds until that deficit closes — ceiled so the wait never understates.
// Null means never at current rates (a short resource with zero effective production).
fun timeUntilAffordable(stock: Resources, cost: Resources, buildings: Buildings): Duration? {
    val waits = stock.shortfallOf(cost).map { kind ->
        val deficitFine = when (kind) {
            ResourceKind.METAL -> cost.metalFine - stock.metalFine
            ResourceKind.CRYSTAL -> cost.crystalFine - stock.crystalFine
            ResourceKind.DEUTERIUM -> cost.deuteriumFine - stock.deuteriumFine
        }
        val ratePerHour = when (kind) {
            ResourceKind.METAL -> PlaceholderBalance.effectiveMetalProductionPerHour(buildings)
            ResourceKind.CRYSTAL -> PlaceholderBalance.effectiveCrystalProductionPerHour(buildings)
            ResourceKind.DEUTERIUM -> PlaceholderBalance.effectiveDeuteriumProductionPerHour(buildings)
        }
        if (ratePerHour <= 0) return null
        ((deficitFine + ratePerHour - 1) / ratePerHour).milliseconds
    }
    return waits.maxOrNull() ?: Duration.ZERO
}
