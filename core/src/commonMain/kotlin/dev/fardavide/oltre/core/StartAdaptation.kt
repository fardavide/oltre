package dev.fardavide.oltre.core

import kotlin.time.Instant

sealed interface StartAdaptationResult {
    data class Started(val state: GameState) : StartAdaptationResult

    // "Already adapting", and only that. The branch's slot is empire-wide — one ladder at a time,
    // whichever ladder — so the choice of which axis to widen next is still a choice. It has not
    // meant "already researching" since 0.12.1: a player two hours into Extraction 4 can start
    // Gravitic 1, which is exactly what Davide asked for and exactly what the adaptation sheet's §2
    // argued against.
    data object SlotBusy : StartAdaptationResult
    data object InsufficientResources : StartAdaptationResult
    data object RequirementsNotMet : StartAdaptationResult
}

// The adaptation branch's counterpart of `startResearch`, and deliberately the same shape down to
// the order of the checks — the two branches differ in what they buy, not in how they are bought.
//
// What a level buys is not decided here or in `AdaptationBalance`: it widens a tolerance band, and
// the bands are `GalaxyBalance`'s. That separation is the point. This function moves a number in
// `Research`; the map re-reads it through `Research.adaptationLevels()` and worlds that were
// `Blocked` stop being blocked, without anything here knowing a world exists.
fun startAdaptation(state: GameState, technology: AdaptationTechnology, at: Instant): StartAdaptationResult {
    if (state.activeAdaptation != null) return StartAdaptationResult.SlotBusy
    if (!AdaptationBalance.requirementFor(technology).isMetBy(state)) return StartAdaptationResult.RequirementsNotMet
    val toLevel = TechLevel(state.research.levelOf(technology).value + 1)
    val cost = AdaptationBalance.adaptationCost(technology, toLevel)
    if (!state.resources.covers(cost)) return StartAdaptationResult.InsufficientResources
    val job = AdaptationJob(
        technology = technology,
        toLevel = toLevel,
        startedAt = at,
        // Fixed the moment the project starts, so a Robotics Factory that finishes mid-project does
        // not retroactively shorten it — the rule both other kinds of job already follow, and the
        // one that keeps a booked notification honest.
        completesAt = at + AdaptationBalance.adaptationDuration(technology, toLevel, state.buildings.roboticsFactory),
    )
    return StartAdaptationResult.Started(
        state.copy(
            resources = state.resources.minus(cost),
            activeAdaptation = job,
            eventLog = state.eventLog + Event.AdaptationStarted(technology = technology, toLevel = toLevel, at = at),
        ),
    )
}
