package dev.fardavide.oltre.core

import kotlin.time.Instant

// Append-only: every discrete state transition is recorded here, timestamped. This is what
// makes "while you were away", combat reports and replay debugging fall out for free, and it
// is the server persistence model.
sealed interface Event {
    val at: Instant

    data class BuildCompleted(
        val building: BuildingType,
        val newLevel: BuildingLevel,
        override val at: Instant,
    ) : Event
}
