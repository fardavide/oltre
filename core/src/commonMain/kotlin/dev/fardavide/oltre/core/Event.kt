package dev.fardavide.oltre.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

// Append-only: every discrete state transition is recorded here, timestamped. This is what
// makes "while you were away", combat reports and replay debugging fall out for free, and it
// is the server persistence model.
@Serializable
sealed interface Event {
    val at: Instant

    // The @SerialName values are on-disk identifiers in every existing save; renaming the class
    // is free, changing these is a schema break.
    @Serializable
    @SerialName("BuildStarted")
    data class BuildStarted(
        val building: BuildingType,
        val toLevel: BuildingLevel,
        override val at: Instant,
    ) : Event

    @Serializable
    @SerialName("BuildCompleted")
    data class BuildCompleted(
        val building: BuildingType,
        val newLevel: BuildingLevel,
        override val at: Instant,
    ) : Event

    @Serializable
    @SerialName("FleetReturned")
    data class FleetReturned(
        val ships: Map<ShipType, Int>,
        val cargo: Resources,
        override val at: Instant,
    ) : Event
}
