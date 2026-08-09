package dev.fardavide.oltre.core

import kotlin.time.Instant

sealed interface StartSurveyResult {
    data class Started(val state: GameState) : StartSurveyResult

    // One probe per target, which is the same rule `builds` states with its map key: sending a
    // second probe to a system already expecting one buys nothing, because what comes back is not a
    // quantity.
    data object AlreadySurveying : StartSurveyResult

    // Every world around that star is already known. Refusing is not a restriction, it is the
    // absence of a tax: a player cannot accidentally pay for information they own, and — unlike
    // OGame's Discovery, which puts a seven-day cooldown on a coordinate — nothing ever expires
    // back into needing to be re-bought. `surveyed` is monotone, permanently.
    data object AlreadySurveyed : StartSurveyResult
    data object InsufficientResources : StartSurveyResult
}

// The fourth verb, and the first whose subject is a place rather than one of twelve enum rows.
//
// Deliberately the same `(state, subject, at) -> Result` shape as `startUpgrade`, `startResearch`
// and `startAdaptation`, down to the order of the checks. What differs is the scarcity: probes run
// **in parallel with each other and with everything else**, limited by metal alone. That is not a
// missing cap, it is the settled rule — Davide, 2026-08-08, on construction: *"There's still a need
// to decide, as you will use resources to chose which to upgrade"*. A probe competes with a mine
// level for the same stock, and that is the decision.
//
// Nothing gates it. The verb whose whole job is to exist at hour zero cannot be behind a building,
// and the unlock pace the player likes is protected from the resource side by the price.
fun startSurvey(state: GameState, target: SystemAddress, at: Instant): StartSurveyResult {
    if (state.surveys.any { it.target == target }) return StartSurveyResult.AlreadySurveying
    if (state.galaxy.hasSurveyed(target)) return StartSurveyResult.AlreadySurveyed
    val cost = SurveyBalance.cost()
    if (!state.resources.covers(cost)) return StartSurveyResult.InsufficientResources
    val job = SurveyJob(
        target = target,
        startedAt = at,
        // Fixed at dispatch, like every other job's completion. Here it also means the player has
        // bought a specific instant rather than a rate — the probe lands when they chose it to.
        completesAt = at + SurveyBalance.duration(from = SystemAddress.of(state.galaxy.home), to = target),
    )
    return StartSurveyResult.Started(
        state.copy(
            resources = state.resources.minus(cost),
            surveys = state.surveys + job,
            eventLog = state.eventLog + Event.SurveyStarted(target = target, at = at),
        ),
    )
}
