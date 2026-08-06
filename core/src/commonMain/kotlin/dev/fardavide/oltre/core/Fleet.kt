package dev.fardavide.oltre.core

import kotlinx.serialization.Serializable
import kotlin.time.Instant

// PLACEHOLDER taxonomy (Davide, 2026-08-06): OGame-lineage names standing in until the real
// v1 ship set is decided on Notion. Rename here when it is — but note these constant names are
// now on-disk identifiers in every save, so a rename is a save-format change too.
@Serializable
enum class ShipType { CARGO, FIGHTER, CRUISER, COLONY_SHIP }

@Serializable
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

@Serializable
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
