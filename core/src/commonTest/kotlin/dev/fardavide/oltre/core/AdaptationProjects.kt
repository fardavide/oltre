package dev.fardavide.oltre.core

import kotlin.test.assertIs
import kotlin.time.Instant

// The adaptation counterpart of `ResearchProjects.kt`, and deliberately the same shape: every test
// needs a colony past the gate that can pay for the next level, and spelling that out per test
// buries what the test is actually about.
internal fun GameState.readyToAdapt(technology: AdaptationTechnology): GameState {
    val toLevel = TechLevel(research.levelOf(technology).value + 1)
    val cost = AdaptationBalance.adaptationCost(technology, toLevel)
    val gated = when (val requirement = AdaptationBalance.requirementFor(technology)) {
        is ResearchRequirement.Facility -> copy(buildings = buildings.withLevel(requirement.building, requirement.level))
        is ResearchRequirement.Tech -> copy(research = research.withLevel(requirement.technology, requirement.level))
    }
    return gated.copy(
        resources = Resources.of(metal = cost.metal, crystal = cost.crystal, deuterium = cost.deuterium),
    )
}

internal fun GameState.adapting(technology: AdaptationTechnology, at: Instant): GameState =
    assertIs<StartAdaptationResult.Started>(startAdaptation(readyToAdapt(technology), technology, at = at)).state

internal fun GameState.ladder(): AdaptationJob =
    checkNotNull(activeAdaptation) { "expected an adaptation project, the slot was empty" }

// Climbing a ladder without paying for it or waiting: the shortcut the verdict tests need, because
// what they are about is what a *level* does to a world and not how it was bought.
internal fun GameState.climbed(technology: AdaptationTechnology, to: Int): GameState =
    copy(research = research.withLevel(technology, TechLevel(to)))
