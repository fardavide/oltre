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

    // No idle `SCOUT`. **The scarcity that a price could not buy** — see the verb below — and it is
    // its own result rather than folded into `InsufficientResources` because the two are answered by
    // different things: one is waited out, the other is bought at the Shipyard.
    data object NoIdleScout : StartSurveyResult

    data object InsufficientResources : StartSurveyResult
}

// The fourth verb, and the first whose subject is a place rather than one of twelve enum rows.
//
// Deliberately the same `(state, subject, at) -> Result` shape as `startUpgrade`, `startResearch`
// and `startAdaptation`, down to the order of the checks — validity, then requirements, then cost.
//
// **A probe flies a hull now, and that is what the scarcity is.** Davide, 2026-08-16, having played
// 0.12.2: *"Surveying other systems seems way too easy. A small bunch of metal, a few minutes of
// waiting, and you can even survey 10 systems from another galaxy for 1500 metal in total, in less
// than one hour… Exploring the world must feel rewarding, not just a tap away."*
//
// The price was never what made it a tap. Probes ran **in parallel with each other and with
// everything else, limited by metal alone**, so ten dispatched in one check-in landed together and
// the marginal wall-clock cost of the tenth was zero — no constant could have fixed that. A finite
// pool of hulls can: what is out is out, and the tenth probe waits for the first to come back.
//
// **A `SCOUT` and never a skiff** — Davide's call in the same breath. A skiff that could survey
// would let the scarcity leak straight back out, because a fleet bought for gathering would double
// as an exploration budget and the pool would stop being a choice between the two. So the two verbs
// compete for one *pool* and never for one *hull*.
//
// **Still gated by nothing else.** The old reason survives the ruling intact — *"the verb whose whole
// job is to exist at hour zero cannot be behind a building"* — and it is why the scout is priced at
// 200 metal rather than at a skiff's 800: what a colony must do before its first probe is one
// purchase it can afford out of the genesis stock, not two days of production.
fun startSurvey(state: GameState, target: SystemAddress, at: Instant): StartSurveyResult {
    if (state.surveys.any { it.target == target }) return StartSurveyResult.AlreadySurveying
    if (state.galaxy.hasSurveyed(target)) return StartSurveyResult.AlreadySurveyed
    if (!state.ships.covers(SurveyBalance.SHIPS)) return StartSurveyResult.NoIdleScout
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
            // The pool is the *idle* count, exactly as `startRun` leaves it: the scout is away rather
            // than gone, and `advance` puts it back when the probe lands.
            ships = state.ships - SurveyBalance.SHIPS,
            surveys = state.surveys + job,
            eventLog = state.eventLog + Event.SurveyStarted(target = target, at = at),
        ),
    )
}
