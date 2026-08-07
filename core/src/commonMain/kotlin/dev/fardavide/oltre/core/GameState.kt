package dev.fardavide.oltre.core

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class GameState(
    val resources: Resources,
    val buildings: Buildings,
    // Upgrades run in parallel across facilities, one job per facility — the map key is the
    // rule. Ordering never matters here: `advance` picks the next completion by instant.
    val builds: Map<BuildingType, BuildJob>,
    val research: Research,
    // One project at a time, empire-wide — the deliberate opposite of `builds`. The colony is
    // limited by resources and research is limited by time, which is what gives the two screens
    // different characters.
    val activeResearch: ResearchJob?,
    // **The same slot**, held by the other branch. At most one of these two is ever set; the rule
    // is in `init` below. Two fields rather than one sealed project because the branches carry
    // different subjects and a sum type here would make every existing reader of `activeResearch`
    // answer for a project it does not render — the 0.3 adaptation sheet's §2 says so, and says
    // the reason has a shelf life.
    val activeAdaptation: AdaptationJob?,
    // The seed and what the player has changed about the map — never the worlds themselves. See
    // `GalaxyState`.
    val galaxy: GalaxyState,
    val returningFleet: ReturningFleet?,
    val eventLog: List<Event>,
) {
    init {
        // The single slot is research's only scarcity — 0.1 wrote it down in as many words — so a
        // second branch that ran alongside it would mean the answer is always "run both" and the
        // ladder that changes the map would cost nothing to push. This is the one place in `core`
        // where a rule is checked rather than made unrepresentable; it is checked on every
        // construction, which includes every decode, so a hand-edited save fails here.
        require(activeResearch == null || activeAdaptation == null) {
            "the research slot holds one project: was $activeResearch and $activeAdaptation"
        }
    }

    // What is holding the slot, whichever branch it belongs to. Null means it is free now.
    val researchSlotFreesAt: Instant?
        get() = activeResearch?.completesAt ?: activeAdaptation?.completesAt

    companion object {

        // The galaxy seed is a required argument rather than a default, because a default is how
        // every player quietly ends up in the same galaxy. Core cannot mint one — it reads no clock
        // and no random source — so the composition root does, once, at genesis.
        fun initial(galaxySeed: GalaxySeed): GameState = GameState(
            resources = PlaceholderBalance.startingResources(),
            buildings = Buildings.initial(),
            builds = emptyMap(),
            research = Research.initial(),
            activeResearch = null,
            activeAdaptation = null,
            galaxy = GalaxyState.initial(galaxySeed),
            returningFleet = null,
            eventLog = emptyList(),
        )
    }
}
