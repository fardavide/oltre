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
    val arrival = state.returningFleet?.let { fleet ->
        FutureEvent.FleetArrives(origin = fleet.origin, ships = fleet.ships, at = fleet.arrivesAt)
    }
    // Ties are broken exactly the way `advance` applies them — completions in building order,
    // then the arrival — so this list and the event log it predicts never disagree on order.
    return (builds + listOfNotNull(arrival)).sortedWith(compareBy({ it.at }, { it.tieBreak() }))
}

private fun FutureEvent.tieBreak(): Int = when (this) {
    is FutureEvent.BuildCompletes -> building.ordinal
    is FutureEvent.FleetArrives -> Int.MAX_VALUE
}
