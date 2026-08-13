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

    // The partner `ShipsBuilt` lacked from 0.8.0 to 0.9.0, and the reason it lacked one is gone: the
    // yard has a clock now, so an order and a delivery are two things that happen at two instants.
    // It carries the whole manifest because that is what the player tapped; the delivery below
    // carries one hull, because that is what arrives.
    @Serializable
    @SerialName("ShipsOrdered")
    data class ShipsOrdered(
        val ships: Ships,
        override val at: Instant,
    ) : Event

    // **The meaning is unchanged and that is why the name and the identifier are** — this has always
    // said *a hull exists now*, which is exactly what the yard finishing says. A save written before
    // 0.9.0 holds these for purchases that were instant, and they are still true about that colony:
    // it bought a hull, and at that instant it had one.
    //
    // One hull per event rather than a manifest, because the yard serves one hull at a time. A
    // three-hull order writes one `ShipsOrdered` and three of these, hours apart — which is the log
    // saying what actually happened rather than what was asked for.
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
