package dev.fardavide.oltre.core

import kotlin.time.Instant

sealed interface StartResearchResult {
    data class Started(val state: GameState) : StartResearchResult

    // Not "already researching this" — the slot is empire-wide, so a busy slot refuses every
    // technology, including the two the player is not looking at, and since 0.0.17 including every
    // adaptation ladder as well.
    data object SlotBusy : StartResearchResult
    data object InsufficientResources : StartResearchResult
    data object RequirementsNotMet : StartResearchResult
}

// The counterpart of `startUpgrade`, and deliberately the opposite shape: facilities upgrade in
// parallel with resources as the only limiter, while research runs one project at a time. That
// single slot is the only scarcity research has — its costs are small next to a mine of the same
// era, so without it the answer would always be "start all three".
fun startResearch(state: GameState, technology: Technology, at: Instant): StartResearchResult {
    // The slot, not this branch's half of it: an adaptation ladder in progress refuses an applied
    // technology exactly as another applied technology would. That is what makes an adaptation
    // level cost production levels the player did not buy.
    if (state.researchSlotFreesAt != null) return StartResearchResult.SlotBusy
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
