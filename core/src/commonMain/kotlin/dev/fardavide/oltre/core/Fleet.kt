package dev.fardavide.oltre.core

import kotlin.time.Instant

// PLACEHOLDER taxonomy (Davide, 2026-08-06): OGame-lineage names standing in until the real
// v1 ship set is decided on Notion. Rename here when it is.
enum class ShipType { CARGO, FIGHTER, CRUISER, COLONY_SHIP }

data class Coordinates(
    val galaxy: Int,
    val system: Int,
    val position: Int,
) {
    init {
        require(galaxy > 0 && system > 0 && position > 0) {
            "coordinates must be positive, were [$galaxy:$system:$position]"
        }
    }
}

data class ReturningFleet(
    val ships: Map<ShipType, Int>,
    val cargo: Resources,
    val origin: Coordinates,
    val arrivesAt: Instant,
) {
    init {
        require(ships.isNotEmpty()) { "a fleet must contain at least one ship" }
        require(ships.values.all { it > 0 }) { "ship counts must be positive, were $ships" }
    }
}
