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
    // Probes in flight, in parallel, limited by metal and by nothing else — the same scarcity
    // `builds` has and deliberately not the single slot `activeResearch` has.
    //
    // A list rather than the `Map<SystemAddress, SurveyJob>` that would mirror `builds` and state
    // the one-per-target rule in the type, for the reason `GalaxyState.ownership` is a list of
    // records: JSON cannot use a structured object as a map key at all, and the alternative is
    // `allowStructuredMapKeys`, which changes how the *whole* save encodes every map to buy an
    // unreadable one. The rule is checked in `init` instead.
    val surveys: List<SurveyJob>,
    // The **idle** pool, not the total: a dispatched hull leaves it and an arrival returns it, the
    // same shape `resources` has. A run in flight carries its own manifest, so the fleet you own is
    // `ships` plus every run's `ships` and nothing has to store the sum.
    val ships: Ships,
    // Runs in flight, in parallel, and deliberately with **no** one-per-target guard — unlike
    // `surveys`. See `startRun`: a one-per-target rule would turn each probe into ~4.75 guaranteed
    // dispatch slots and make surveying strictly efficient, which the galaxy sheet forbids.
    val runs: List<FleetRun>,
    // The one row the player has asked to be told about, across the facilities, the technologies and
    // the ladders alike — null when there is none. Not a job and not a booking: it schedules nothing
    // and `advance` never applies it, it only points at a row whose price the stores have not
    // reached yet. See `WatchTarget`.
    val watching: WatchTarget?,
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
        // The rule `builds` gets from its map key, stated here because the save format cannot hold
        // the map that would state it. Checked on every construction, which includes every decode.
        require(surveys.distinctBy { it.target }.size == surveys.size) {
            "one probe per target system: was ${surveys.map { it.target }}"
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
            surveys = emptyList(),
            // One skiff, granted, on the same argument the 500 metal is granted and the mines start
            // at level 1 — `BalanceCurveTest`'s own words, *"a new colony opens on a decision, not on
            // a wait."* One and not two, so the second hull is the first fleet purchase and the
            // player learns the shop exists by wanting something from it.
            ships = Ships.of(ShipType.SKIFF, 1),
            runs = emptyList(),
            watching = null,
            eventLog = emptyList(),
        )
    }
}
