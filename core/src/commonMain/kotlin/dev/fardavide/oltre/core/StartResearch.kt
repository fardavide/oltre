package dev.fardavide.oltre.core

import kotlin.time.Instant

sealed interface StartResearchResult {
    data class Started(val state: GameState) : StartResearchResult

    // Not "already researching this" — the slot is empire-wide, so a busy slot refuses every
    // technology, including the ones the player is not looking at. **It stops at the branch**: an
    // adaptation ladder has held a slot of its own since 0.12.1 and no longer refuses anything here.
    data object SlotBusy : StartResearchResult
    data object InsufficientResources : StartResearchResult
    data object RequirementsNotMet : StartResearchResult
}

// The counterpart of `startUpgrade`, and deliberately the opposite shape: facilities upgrade in
// parallel with resources as the only limiter, while research runs one project at a time. That
// single slot is the only scarcity research has — its costs are small next to a mine of the same
// era, so without it the answer would always be "start all three".
fun startResearch(state: GameState, technology: Technology, at: Instant): StartResearchResult {
    // This branch's own slot and nothing else. A ladder climbing beside it is not this function's
    // business — see `GameState.activeAdaptation` for why that stopped being true at 0.12.1.
    if (state.activeResearch != null) return StartResearchResult.SlotBusy
    if (!ResearchBalance.requirementFor(technology).isMetBy(state)) return StartResearchResult.RequirementsNotMet
    val toLevel = TechLevel(state.research.levelOf(technology).value + 1)
    val cost = ResearchBalance.researchCost(technology, toLevel)
    if (!state.resources.covers(cost)) return StartResearchResult.InsufficientResources
    val job = ResearchJob(
        technology = technology,
        toLevel = toLevel,
        startedAt = at,
        // Duration is fixed the moment the project starts, so a Robotics Factory that finishes
        // mid-project does not retroactively shorten it — the same rule construction already
        // follows, and the one that keeps a booked notification honest.
        completesAt = at + ResearchBalance.researchDuration(technology, toLevel, state.buildings.roboticsFactory),
    )
    return StartResearchResult.Started(
        state.copy(
            resources = state.resources.minus(cost),
            activeResearch = job,
            eventLog = state.eventLog + Event.ResearchStarted(technology = technology, toLevel = toLevel, at = at),
        ),
    )
}
