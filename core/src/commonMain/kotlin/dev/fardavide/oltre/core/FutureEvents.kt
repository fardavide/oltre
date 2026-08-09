package dev.fardavide.oltre.core

import kotlin.time.Instant

// The mirror of `Event`: that log says what happened, this says what is still going to happen.
// Deriving it in core rather than in the client is the point — a local notification, a
// "while you were away" summary and (later) the server's push scheduling must all agree with
// what `advance` will actually do, and the only way to guarantee that is to read it off the
// same state with the same ordering rules.
sealed interface FutureEvent {
    val at: Instant

    data class BuildCompletes(
        val building: BuildingType,
        val toLevel: BuildingLevel,
        override val at: Instant,
    ) : FutureEvent

    data class ResearchCompletes(
        val technology: Technology,
        val toLevel: TechLevel,
        override val at: Instant,
    ) : FutureEvent

    data class AdaptationCompletes(
        val technology: AdaptationTechnology,
        val toLevel: TechLevel,
        override val at: Instant,
    ) : FutureEvent

    data class SurveyLands(
        val target: SystemAddress,
        override val at: Instant,
    ) : FutureEvent

    data class FleetArrives(
        val origin: Coordinates,
        val ships: Map<ShipType, Int>,
        override val at: Instant,
    ) : FutureEvent
}

// Everything still in flight, earliest first. Pure and clock-free like the rest of core: the
// caller knows what "now" is and drops whatever has already passed — passing a stale state is
// then a caller's problem to see rather than a silently empty list.
fun futureEvents(state: GameState): List<FutureEvent> {
    val builds = state.builds.values.map { job ->
        FutureEvent.BuildCompletes(building = job.building, toLevel = job.toLevel, at = job.completesAt)
    }
    val project = state.activeResearch?.let { job ->
        FutureEvent.ResearchCompletes(technology = job.technology, toLevel = job.toLevel, at = job.completesAt)
    }
    // The other half of the same slot. Only one of the two can be set, so at most one of these two
    // lines ever contributes — but both are read, because a derivation that assumed which branch
    // holds the slot would silently stop predicting the day that assumption changed.
    val ladder = state.activeAdaptation?.let { job ->
        FutureEvent.AdaptationCompletes(technology = job.technology, toLevel = job.toLevel, at = job.completesAt)
    }
    val probes = state.surveys.map { job ->
        FutureEvent.SurveyLands(target = job.target, at = job.completesAt)
    }
    val arrival = state.returningFleet?.let { fleet ->
        FutureEvent.FleetArrives(origin = fleet.origin, ships = fleet.ships, at = fleet.arrivesAt)
    }
    // Ties are broken exactly the way `advance` applies them — build completions in building
    // order, then the research completion, then the adaptation completion, then the arrival — so
    // this list and the event log it predicts never disagree on order.
    return (builds + listOfNotNull(project) + listOfNotNull(ladder) + probes + listOfNotNull(arrival))
        .sortedWith(compareBy({ it.at }, { it.tieBreak() }, { it.secondaryTieBreak() }))
}

private fun FutureEvent.tieBreak(): Int = when (this) {
    is FutureEvent.BuildCompletes -> building.ordinal
    // Immediately after the last possible build, whatever the building set grows to.
    is FutureEvent.ResearchCompletes -> BuildingType.entries.size
    is FutureEvent.AdaptationCompletes -> BuildingType.entries.size + 1
    is FutureEvent.SurveyLands -> BuildingType.entries.size + 2
    is FutureEvent.FleetArrives -> Int.MAX_VALUE
}

// Probes are the first kind of job that can have *several* instances due at one instant, so the
// primary tie-break is no longer enough to make this list total. Mirrors `advance`'s own ordering
// of simultaneous landings — by target, which is intrinsic to the job — so the prediction and the
// log it predicts still cannot disagree.
private fun FutureEvent.secondaryTieBreak(): Long = when (this) {
    is FutureEvent.SurveyLands -> target.galaxy.toLong() * GalaxyBalance.SYSTEMS_PER_GALAXY + target.system
    else -> 0
}
