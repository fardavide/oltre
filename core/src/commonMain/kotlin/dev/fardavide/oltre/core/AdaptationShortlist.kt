package dev.fardavide.oltre.core

// What the next level of one ladder would actually buy, counted over the worlds the player has
// **already surveyed**. Davide's call, 2026-08-09.
//
// This is the consumer that makes surveying a decision rather than a bookmark, and the reason is
// worth stating because it is not obvious. `verdictFor` re-derives against *current* adaptation
// levels and `GalaxyState.surveyed` is monotone, so surveying later returns strictly better-
// labelled rows for the same price: waiting is free, and a verb whose optimal play is "not yet" is
// not a verb. This inverts that. The shortlist is a function of what you have surveyed, so the
// **order** becomes survey → read the shortlist → commit the one shared research slot, and the
// information has to arrive before the purchase rather than after it.
//
// It also answers, for the first time, a question the three ladders have never been able to pose.
// They cost identically once priced at 1 : 2 : 3 — that is the adaptation sheet's §4 idea — so with
// four home worlds surveyed the choice between Thermal, Gravitic and Atmospheric is arbitrary. With
// fifty worlds surveyed it is arithmetic.
data class LadderShortlist(
    val technology: AdaptationTechnology,
    val nextLevel: TechLevel,
    // Worlds that are `Blocked` now and would not be at `nextLevel`. Never counts a world that is
    // blocked on another axis too: a level of Gravitic that leaves a world still failing pressure
    // has unlocked nothing, and a count that claimed otherwise would be a promise the map breaks.
    val unlocks: Int,
    // Of those, the ones that would clear the worth-it threshold rather than land on `Barren`. The
    // honest half of the pair — most worlds that pass every band are still not worth taking, by
    // construction, and a shortlist that hid that would sell the ladder on a number the player
    // cannot spend.
    val worthTaking: Int,
)

// One entry per ladder, in enum order, always all three — a row that would unlock nothing still
// reports zero rather than disappearing, because "Thermal 3 → 0 worlds" is the sentence that makes
// the other two mean something.
fun adaptationShortlist(state: GameState): List<LadderShortlist> {
    val current = state.research.adaptationLevels()
    // Surveyed worlds only, and re-derived from the seed rather than stored — the galaxy is never
    // serialised, so this is the same regeneration every other reader performs.
    val worlds = state.galaxy.surveyed.mapNotNull { at -> worldAt(state.galaxy.seed, at) }
    return AdaptationTechnology.entries.map { technology ->
        val level = TechLevel(state.research.levelOf(technology).value + 1)
        val raised = current.withOneMore(technology)
        val unlocked = worlds.filter { world ->
            isBlocked(world, state.galaxy, current) && !isBlocked(world, state.galaxy, raised)
        }
        LadderShortlist(
            technology = technology,
            nextLevel = level,
            unlocks = unlocked.size,
            worthTaking = unlocked.count {
                GalaxyBalance.yieldScore(it.traits).perMillion >= GalaxyBalance.WORTH_IT_THRESHOLD.perMillion
            },
        )
    }
}

// Asked through `verdictFor` rather than against the bands directly, so a world that is Home,
// Occupied or Unsurveyed can never be counted as unlockable by a ladder — those verdicts win over
// the tolerance check, and a shortlist that re-implemented the band comparison would quietly
// disagree with the screen the day one of them moved.
private fun isBlocked(world: World, galaxy: GalaxyState, adaptation: AdaptationLevels): Boolean =
    verdictFor(world, galaxy, adaptation) is WorldVerdict.Blocked

private fun AdaptationLevels.withOneMore(technology: AdaptationTechnology): AdaptationLevels = when (technology) {
    AdaptationTechnology.THERMAL -> copy(thermal = thermal + 1)
    AdaptationTechnology.GRAVITIC -> copy(gravitic = gravitic + 1)
    AdaptationTechnology.ATMOSPHERIC -> copy(atmospheric = atmospheric + 1)
}
