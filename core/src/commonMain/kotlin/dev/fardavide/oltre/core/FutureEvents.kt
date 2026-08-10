package dev.fardavide.oltre.core

import kotlin.time.Duration
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

    data class FleetReturns(
        val target: GalaxyCoordinate,
        val ships: Ships,
        val cargo: Resources,
        val dispatchedAt: Instant,
        override val at: Instant,
    ) : FutureEvent

    // **The one member that is not the mirror of an `Event`.** Nothing is in flight, `advance` will
    // write nothing to the log when this arrives, and the only trace of it afterwards is a watch
    // that has cleared itself. What it predicts is the instant a store crosses a price — projected
    // from the stocks and rates the colony has *now*, which is why it is also the one kind whose
    // instant moves the moment anything else about the colony does.
    data class AffordableAt(
        val purchase: WatchedPurchase,
        override val at: Instant,
    ) : FutureEvent
}

// Everything still to come, earliest first. Pure like the rest of core, and time is a parameter
// rather than a clock read: every job carries the instant it completes at, so `now` is used by
// exactly one member — the watch, whose instant is not stored anywhere and has to be projected
// forward from the moment the stocks are accurate as of. Callers still drop whatever has already
// passed, so a stale state is a caller's problem to see rather than a silently empty list.
fun futureEvents(state: GameState, now: Instant): List<FutureEvent> {
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
    val returns = state.runs.map { run ->
        FutureEvent.FleetReturns(
            target = run.target,
            ships = run.ships,
            cargo = run.cargo,
            dispatchedAt = run.dispatchedAt,
            at = run.returnsAt,
        )
    }
    // Projected rather than looked up, and only while there is still something to project: a row the
    // stores already cover has no future instant to name, and one whose binding resource has no net
    // income never reaches its price at all. Both cases are simply absent — an alert booked at
    // infinity, or in the past, is worse than no alert.
    val affordable = state.watchedPurchase()?.let { purchase ->
        timeUntilAffordable(state.resources, purchase.cost, state.buildings, state.research)
            .takeIf { it.isFinite() && it > Duration.ZERO }
            ?.let { wait -> FutureEvent.AffordableAt(purchase = purchase, at = now + wait) }
    }
    // Ties are broken exactly the way `advance` applies them — build completions in building
    // order, then the research completion, then the adaptation completion, then survey landings,
    // then fleet returns — so this list and the event log it predicts never disagree on order. The
    // watch sorts after all of them, and its place in the order is arbitrary in a way theirs is not:
    // it mirrors nothing `advance` does, so there is no log for it to agree with.
    return (builds + listOfNotNull(project) + listOfNotNull(ladder) + probes + returns + listOfNotNull(affordable))
        .sortedWith(
            compareBy({ it.at }, { it.tieBreak() }, { it.secondaryTieBreak() }, { it.tertiaryTieBreak() }),
        )
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

// **Explicit integers, not a derived ladder.** This used to read `ResearchCompletes ->
// BuildingType.entries.size`, `AdaptationCompletes -> + 1`, `SurveyLands -> + 2`, `FleetArrives ->
// Int.MAX_VALUE` — which meant adding a seventh building silently moved three constants that have
// nothing to do with buildings, and `Int.MAX_VALUE` sealed the end of the ladder so the next kind had
// nowhere to go. The relative order is unchanged, so this is a pure refactor; what it buys is that
// the next kind after this one costs nothing to add.
//
// Builds occupy 0…99 by ordinal, which is room for ninety-four more facilities than the design has.
private fun FutureEvent.tieBreak(): Int = when (this) {
    is FutureEvent.BuildCompletes -> building.ordinal
    is FutureEvent.ResearchCompletes -> 100
    is FutureEvent.AdaptationCompletes -> 200
    is FutureEvent.SurveyLands -> 300
    is FutureEvent.FleetReturns -> 400
    is FutureEvent.AffordableAt -> 500
}

// Probes were the first kind of job that can have *several* instances due at one instant, and runs
// are the second — so the primary tie-break is not enough to make this list total, and a kind that
// falls through to a constant produces a **non-total order** that only misbehaves when two land on
// the same millisecond. That is exactly what a check-in dispatching three runs to one system
// produces, so the branch is mandatory rather than defensive.
//
// Each mirrors `advance`'s own ordering of its kind, in the same shape: probes by target, runs by
// `(dispatchedAt, packed coordinate)` — and `packed` is literally the function `advance` sorts with.
private fun FutureEvent.secondaryTieBreak(): Long = when (this) {
    is FutureEvent.SurveyLands -> target.galaxy.toLong() * GalaxyBalance.SYSTEMS_PER_GALAXY + target.system
    is FutureEvent.FleetReturns -> dispatchedAt.toEpochMilliseconds()
    // The watch needs none: there is one of it in the whole game, so it cannot tie with itself.
    is FutureEvent.BuildCompletes,
    is FutureEvent.ResearchCompletes,
    is FutureEvent.AdaptationCompletes,
    is FutureEvent.AffordableAt,
    -> 0
}

// The runs' third key, needed because two runs dispatched at the same millisecond to different worlds
// are ordinary rather than exotic — one check-in, two taps.
private fun FutureEvent.tertiaryTieBreak(): Long = when (this) {
    is FutureEvent.FleetReturns -> packed(target)
    else -> 0
}
