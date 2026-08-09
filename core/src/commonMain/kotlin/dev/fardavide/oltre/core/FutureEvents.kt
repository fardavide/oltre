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
        // What the landing will chart, carried for the same reason `BuildCompletes` carries the
        // level it will reach: a caller booking an alert in advance has to be able to say what it
        // is about, and the only figure it may say is the one `advance` will actually log. Zero is
        // a real answer — a star with fifteen empty slots is roughly one in 390 — and it is the
        // one landing that has to be *said* differently, so the prediction has to be able to tell
        // the two apart before either happens.
        val worldsFound: Int,
        // The honest half, and the reason a landing carries two numbers: round 9 measured ~60
        // dispatches to see one settleable world, so an alert that only counted worlds would
        // oversell the verb almost every time it fired.
        //
        // **This counts what the Galaxy screen will call `Settleable`** — passes every tolerance
        // band the empire currently holds *and* clears the worth-it yield — and the emphasis is on
        // *the same*: the first version measured yield alone, on the theory that a bar fixed
        // against a constant could not go stale between dispatch and landing. It could not, and it
        // was still wrong twice over. Yield alone admits 51% of worlds rather than 1.5%, so the
        // rare branch became the common one; and it made a notification say "5 worth a look" about
        // the same landing whose card says "none settleable". A game contradicting itself between
        // the lock screen and the app is worse than a number that has to be re-derived.
        //
        // Staleness was never the problem it looked like. `GameNotifications.sync` replaces the
        // whole pending set on every discrete transition, and buying or completing a ladder *is*
        // one — so an alert booked before a ladder lands is re-booked the moment it does.
        val settleable: Int,
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
    // Regenerated from the seed rather than stored, which is what every other reader of the galaxy
    // does — and what makes the count here provably the one the landing branch of `advance` writes:
    // both call `occupiedWorldsIn`, so there is no second rule to drift from.
    val tolerance = GalaxyBalance.tolerance(state.research.adaptationLevels())
    val probes = state.surveys.map { job ->
        val charted = GalaxyState.occupiedWorldsIn(state.galaxy.seed, job.target)
            .mapNotNull { at -> worldAt(state.galaxy.seed, at) }
        FutureEvent.SurveyLands(
            target = job.target,
            worldsFound = charted.size,
            settleable = charted.count { it.wouldBeSettleable(tolerance) },
            at = job.completesAt,
        )
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

// The `Settleable` test, spelled out rather than asked of `verdictFor` — and it has to be, because
// the worlds this asks about have not been surveyed yet. `verdictFor` would answer `Unsurveyed` for
// every one of them, which is true now and not what a prediction is for.
//
// It is the same two conditions in the same order as `verdictFor`'s surveyed branch, and the risk
// of that duplication is stated here rather than hidden: if one of them moves, both have to. The
// alternative was a prediction that disagreed with the screen it predicts, which is what shipped
// first and was worse.
private fun World.wouldBeSettleable(tolerance: Tolerance): Boolean {
    val passesEveryBand = HostilityAxis.entries.all { axis -> traits.axisValue(axis) in tolerance.bandOf(axis) }
    return passesEveryBand &&
        GalaxyBalance.yieldScore(traits).perMillion >= GalaxyBalance.WORTH_IT_THRESHOLD.perMillion
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
