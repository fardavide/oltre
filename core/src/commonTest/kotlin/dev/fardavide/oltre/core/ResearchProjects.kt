package dev.fardavide.oltre.core

import kotlin.test.assertIs
import kotlin.time.Instant

// The research counterpart of BuildJobs.kt: every research test needs a colony that has cleared
// the gate and can pay for the next level, and spelling that out per test buries what the test is
// actually about.
internal fun GameState.readyToResearch(technology: Technology): GameState {
    val toLevel = TechLevel(research.levelOf(technology).value + 1)
    val cost = ResearchBalance.researchCost(technology, toLevel)
    val gated = when (val requirement = ResearchBalance.requirementFor(technology)) {
        is ResearchRequirement.Facility -> copy(buildings = buildings.withLevel(requirement.building, requirement.level))
        is ResearchRequirement.Tech -> copy(research = research.withLevel(requirement.technology, requirement.level))
    }
    return gated.copy(
        resources = Resources.of(metal = cost.metal, crystal = cost.crystal, deuterium = cost.deuterium),
    )
}

internal fun GameState.researching(technology: Technology, at: Instant): GameState =
    assertIs<StartResearchResult.Started>(startResearch(readyToResearch(technology), technology, at = at)).state

internal fun GameState.project(): ResearchJob =
    checkNotNull(activeResearch) { "expected a research project, the slot was empty" }
