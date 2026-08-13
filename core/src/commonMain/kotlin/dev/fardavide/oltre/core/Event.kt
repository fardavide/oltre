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
    @SerialName("ResearchStarted")
    data class ResearchStarted(
        val technology: Technology,
        val toLevel: TechLevel,
        override val at: Instant,
    ) : Event

    @Serializable
    @SerialName("ResearchCompleted")
    data class ResearchCompleted(
        val technology: Technology,
        val newLevel: TechLevel,
        override val at: Instant,
    ) : Event

    // The adaptation branch's own pair rather than a wider `Research*`: what changed is not a
    // production multiplier, and a log a player will one day read as "while you were away" has to
    // be able to say which of the two kinds of thing happened.
    @Serializable
    @SerialName("AdaptationStarted")
    data class AdaptationStarted(
        val technology: AdaptationTechnology,
        val toLevel: TechLevel,
        override val at: Instant,
    ) : Event

    @Serializable
    @SerialName("AdaptationCompleted")
    data class AdaptationCompleted(
        val technology: AdaptationTechnology,
        val newLevel: TechLevel,
        override val at: Instant,
    ) : Event

    // `FleetReturned` finally gets the `Started` partner it has been missing since 0.0.6, which is
    // the taxonomy's own rule rather than an invention.
    @Serializable
    @SerialName("FleetDispatched")
    data class FleetDispatched(
        val target: GalaxyCoordinate,
        val gathering: ResourceKind,
        val ships: Ships,
        override val at: Instant,
    ) : Event

    @Serializable
    @SerialName("FleetReturned")
    data class FleetReturned(
        // Nullable because it is a real value the domain lacks, not a default in the banned sense: a
        // fleet folded forward by the schema-8 migration came from a coordinate no old event ever
        // recorded, and *"we do not know"* is the truthful answer. Filling it from `galaxy.home`
        // would be inventing a number, which the 2 → 3 hop's standard forbids.
        val from: GalaxyCoordinate?,
        val ships: Ships,
        val cargo: Resources,
        override val at: Instant,
    ) : Event

    // No `Completed` partner, and it is the only member of the taxonomy without one — because the
    // purchase has no duration to complete. There is no yard job: `buildShips` charges and delivers
    // in the same call, so the transition this records is the whole of it. See `BuildShips.kt` for
    // why the timer was refused.
    @Serializable
    @SerialName("ShipsBuilt")
    data class ShipsBuilt(
        val ships: Ships,
        override val at: Instant,
    ) : Event

    @Serializable
    @SerialName("SurveyStarted")
    data class SurveyStarted(
        val target: SystemAddress,
        override val at: Instant,
    ) : Event

    // Carries the count rather than the coordinates. The worlds themselves are never stored — the
    // galaxy is a seed — and the set they were added to is already on `GalaxyState`, so repeating
    // them here would be the one place in the save that holds a world. What a log entry needs to
    // say is "the probe reached 2:118 and found five", and the five are re-derivable for as long as
    // the seed exists, which is forever.
    @Serializable
    @SerialName("SurveyCompleted")
    data class SurveyCompleted(
        val target: SystemAddress,
        val worldsFound: Int,
        override val at: Instant,
    ) : Event
}
