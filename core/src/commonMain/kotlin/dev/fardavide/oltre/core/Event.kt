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

    // **The thirteenth, and the first entry in this log about something outside the colony.**
    // Everything above happens *to* a colony and can be replayed from it; this is resources leaving
    // one for a pool `core` does not know exists. What makes it belong here anyway is that the
    // debit is a thing that happened to this colony at an instant, and this list is where those
    // live — the pool's side of the transaction is the server's and is a column rather than an
    // event.
    //
    // **It has no partner and needs none.** The pattern above is start-then-completion, and a
    // contribution is both at once: the resources are gone the moment the verb is accepted, there
    // is no job, nothing is scheduled, and `FutureEvents` gains no term. The *name* says so —
    // `ResourcesContributed` rather than `ContributionStarted`.
    //
    // The amount is carried whole rather than priced, because the log records what happened and the
    // 1 : 2 : 3 is an interpretation of it. The server prices it for the alliance's own ladder; a
    // reader here wants the basket.
    @Serializable
    @SerialName("ResourcesContributed")
    data class ResourcesContributed(
        val amount: Resources,
        override val at: Instant,
    ) : Event

    // **What founding an alliance cost this colony**, and the second member whose payoff is outside
    // it. `ResourcesContributed` above argues the whole shape and this one inherits it: the debit is
    // a thing that happened to this colony at an instant, the alliance's side is a row the server
    // writes, and `core` goes on not knowing that alliances exist.
    //
    // **Its own member rather than a second `ResourcesContributed`**, because the two are different
    // facts and one of them is read: the alliance's own ladder is paid by contributions, and a
    // founding charge folded into that member would either pay the new alliance its own founding
    // price back as experience or force every reader to carry a flag saying which kind it was.
    //
    // No partner, for the same reason: the price is gone the moment the alliance exists, nothing is
    // scheduled, and `FutureEvents` gains no term. The name says so.
    @Serializable
    @SerialName("AllianceFounded")
    data class AllianceFounded(
        val price: Resources,
        override val at: Instant,
    ) : Event
}
