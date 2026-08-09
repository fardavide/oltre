package dev.fardavide.oltre.core

import kotlin.time.Instant

// The single entry point for time. Accrues continuously between discrete events and applies
// each event exactly at its instant, so any span produces the same state as any chain of
// sub-spans (the composability property).
fun advance(state: GameState, from: Instant, to: Instant): GameState {
    require(to >= from) { "advance must not go backwards: from=$from to=$to" }
    // Builds run in parallel, so several of them — plus a research project and a fleet arrival —
    // are in flight at once and each one changes what the following span accrues. Take the
    // earliest due event, apply it, and recurse.
    val nextEventAt = (
        state.builds.values.map { it.completesAt } +
            listOfNotNull(state.researchSlotFreesAt) +
            state.surveys.map { it.completesAt } +
            listOfNotNull(state.returningFleet?.arrivesAt)
        ).filter { it <= to }.minOrNull() ?: return accrue(state, from = from, to = to)
    // An event at or before `from` can only come from a caller resuming with a stale span;
    // apply it defensively instead of wedging it forever.
    val boundary = maxOf(nextEventAt, from)
    val atBoundary = accrue(state, from = from, to = boundary).applyEventsDueAt(nextEventAt)
    return advance(atBoundary, from = boundary, to = to)
}

private fun GameState.applyEventsDueAt(instant: Instant): GameState {
    var next = this
    // Several things can land on the same instant. Which order they are applied in changes only
    // the event log — everything up to the boundary has already accrued, and none of these
    // transitions reads another's result — but the log has to be reproducible, so the order is
    // fixed here and mirrored by `futureEvents`: build completions in building order, then the
    // research completion, then the adaptation completion, then survey landings in target order,
    // then the fleet arrival. Colony first, then the empire, then what arrives from outside it. The
    // two research branches share one slot
    // so only one of them can ever be due, but the order between them is still written down —
    // a tie-break that depends on which case happens to be reachable is one a later slice breaks.
    val completed = builds.values.filter { it.completesAt == instant }.sortedBy { it.building.ordinal }
    for (job in completed) {
        next = next.copy(
            buildings = next.buildings.withLevel(job.building, job.toLevel),
            builds = next.builds - job.building,
            eventLog = next.eventLog + Event.BuildCompleted(
                building = job.building,
                newLevel = job.toLevel,
                at = job.completesAt,
            ),
        )
    }
    val project = next.activeResearch
    if (project != null && project.completesAt == instant) {
        next = next.copy(
            research = next.research.withLevel(project.technology, project.toLevel),
            activeResearch = null,
            eventLog = next.eventLog + Event.ResearchCompleted(
                technology = project.technology,
                newLevel = project.toLevel,
                at = project.completesAt,
            ),
        )
    }
    val adaptation = next.activeAdaptation
    if (adaptation != null && adaptation.completesAt == instant) {
        next = next.copy(
            research = next.research.withLevel(adaptation.technology, adaptation.toLevel),
            activeAdaptation = null,
            eventLog = next.eventLog + Event.AdaptationCompleted(
                technology = adaptation.technology,
                newLevel = adaptation.toLevel,
                at = adaptation.completesAt,
            ),
        )
    }
    // Probes land in parallel and their durations are a pure function of distance, which is
    // quantised in whole systems — so two dispatched in one session to systems 117 and 119 from
    // home 118 complete at the identical millisecond, and a tie here is ordinary rather than
    // exotic. Broken on the target, which is intrinsic to the job: list order would be insertion
    // order, and a log whose order depends on the sequence of taps that produced it is one a
    // reloaded save reproduces only by accident.
    val landed = next.surveys
        .filter { it.completesAt == instant }
        .sortedWith(compareBy({ it.target.galaxy }, { it.target.system }))
    for (job in landed) {
        val found = GalaxyState.occupiedWorldsIn(next.galaxy.seed, job.target)
        next = next.copy(
            galaxy = next.galaxy.copy(surveyed = next.galaxy.surveyed + found),
            surveys = next.surveys - job,
            eventLog = next.eventLog + Event.SurveyCompleted(
                target = job.target,
                worldsFound = found.size,
                at = job.completesAt,
            ),
        )
    }
    val fleet = next.returningFleet
    if (fleet != null && fleet.arrivesAt == instant) {
        next = next.copy(
            resources = next.resources.deposit(fleet.cargo),
            returningFleet = null,
            eventLog = next.eventLog + Event.FleetReturned(
                ships = fleet.ships,
                cargo = fleet.cargo,
                at = fleet.arrivesAt,
            ),
        )
    }
    return next
}

private fun accrue(state: GameState, from: Instant, to: Instant): GameState {
    // Quantize each INSTANT to epoch-milliseconds, never the span: floor(b)-floor(a) telescopes
    // exactly across any chain of sub-spans, while flooring the span does not — real wall-clock
    // instants carry sub-ms fractions and would silently break the composability property.
    val elapsedMilliseconds = to.toEpochMilliseconds() - from.toEpochMilliseconds()
    return state.copy(
        resources = state.resources.copy(
            metalFine = accrued(
                state.resources.metalFine,
                PlaceholderBalance.effectiveMetalProductionPerHour(state.buildings, state.research),
                elapsedMilliseconds,
            ),
            crystalFine = accrued(
                state.resources.crystalFine,
                PlaceholderBalance.effectiveCrystalProductionPerHour(state.buildings, state.research),
                elapsedMilliseconds,
            ),
            deuteriumFine = accrued(
                state.resources.deuteriumFine,
                PlaceholderBalance.effectiveDeuteriumProductionPerHour(state.buildings, state.research),
                elapsedMilliseconds,
            ),
        ),
    )
}

// **The cap is applied to the time, not to the product**, and that is the whole point of this
// function rather than the one line it replaces.
//
// `stock + ratePerHour * elapsedMilliseconds` clamped afterwards is correct arithmetic and unsafe
// storage: the clamp is 3.6e13 but the product it clamps is unbounded, so a deep colony returning
// after a long absence — or a save whose `lastUpdatedAt` is far in the past, or a device clock that
// jumped — computes an intermediate that wraps negative, and `Resources`' own non-negative guard
// turns that into a crash on load. Nothing is wrong with the game state; the arithmetic on the way
// to it overflowed.
//
// Working out how many milliseconds it would take to *fill* the store first bounds every term by
// construction: the product can never exceed the headroom plus one hour's production, whatever the
// elapsed span is. A colony away for a century now gets exactly what a colony away for a week with
// a full store gets, which is the cap, and it gets there without ever forming a large number.
private fun accrued(stockFine: Long, ratePerHour: Long, elapsedMilliseconds: Long): Long {
    if (stockFine >= CAP_FINE) return CAP_FINE
    if (ratePerHour <= 0 || elapsedMilliseconds <= 0) return stockFine
    val headroom = CAP_FINE - stockFine
    // `+ 1` so the store still reaches the cap on the millisecond it would have, rather than
    // stopping a unit short of it by integer division.
    val millisecondsToFill = headroom / ratePerHour + 1
    val effective = minOf(elapsedMilliseconds, millisecondsToFill)
    return minOf(CAP_FINE, stockFine + ratePerHour * effective)
}

private fun Resources.deposit(cargo: Resources): Resources = copy(
    metalFine = minOf(CAP_FINE, metalFine + cargo.metalFine),
    crystalFine = minOf(CAP_FINE, crystalFine + cargo.crystalFine),
    deuteriumFine = minOf(CAP_FINE, deuteriumFine + cargo.deuteriumFine),
)

private val CAP_FINE = PlaceholderBalance.STORAGE_CAPACITY * Resources.FINE_PER_UNIT
