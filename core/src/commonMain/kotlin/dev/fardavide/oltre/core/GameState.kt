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
    // Hulls on the slipway, **in the order they will be served** — the one job list in the game that
    // is serial rather than parallel, and Davide's call. Builds run one per facility and probes and
    // runs run without a cap; the yard runs one at a time, so a check-in that can pay for four hulls
    // is buying a commitment rather than a fleet.
    //
    // A list rather than a single slot and a backlog, because a serial queue *is* a list and the
    // chaining is what makes it one: each entry's `startedAt` is the one before it finishing. The
    // rule is checked in `init` below rather than made unrepresentable, for `surveys`' own reason —
    // the shape that would state it is not one the save format can hold.
    val yard: List<YardJob>,
    // The one row the player has asked to be told the *price* of, across the facilities, the
    // technologies and the ladders alike — null when there is none. Not a job and not a booking: it
    // schedules nothing and `advance` never applies it, it only points at a row whose price the
    // stores have not reached yet. See `WatchTarget`.
    val watching: WatchTarget?,
    // The jobs the player has asked to be told the *completion* of. **A set rather than a second
    // single slot, and that asymmetry is the design's** — an affordability watch is a guess about
    // what to do next and there is one thing you are waiting for, where a completion is something
    // you already started and the model caps those at seven: six facilities and the one research
    // slot. So any number is safe and none of them can overflow the platform's ceiling.
    //
    // Emptied entry by entry as the jobs land — see `withoutSpentWatch`. A subscription is about the
    // job the player started, not a standing preference about the row.
    val subscribed: Set<WatchTarget>,
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
        // **The serial rule, and it is load-bearing twice over.** It is what makes the list an
        // ordering rather than a bag — and it is what lets `FutureEvents` state that two hulls can
        // never be due at the same instant, which is the difference between a total order over the
        // predictions and one that only misbehaves when two land on the same millisecond. Checked on
        // every construction, which includes every decode, so a hand-edited save fails here.
        require(yard.zipWithNext().all { (earlier, later) -> earlier.completesAt <= later.startedAt }) {
            "the yard serves one hull at a time: was ${yard.map { it.startedAt to it.completesAt }}"
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
            // **No hull. The first one is the first purchase** — Davide, 2026-08-12: *"We should
            // remove the default ship also, and allow the user to build them instead."*
            //
            // This used to be one granted skiff, on the argument the 500 metal is granted and the
            // mines start at level 1 — `BalanceCurveTest`'s own words, *"a new colony opens on a
            // decision, not on a wait."* **That sentence still holds and is the reason this is safe
            // rather than the reason the grant existed**: the opening stock covers a hull outright,
            // so the first sitting's verb goes from *send* to *buy, then send*, which is one more
            // decision rather than one more wait. `OpeningBalanceTest` pins both halves.
            //
            // What made the grant defensible was that there was nowhere to buy one; `buildShips`
            // landed at 0.8.0 and the Shipyard at 0.8.x, so the shop the grant was teaching the
            // player to want is now the thing they meet first. Note what does **not** follow: the
            // 7 -> 8 migration hop still grants one, deliberately — see `GameSave.kt`.
            ships = Ships.NONE,
            runs = emptyList(),
            yard = emptyList(),
            watching = null,
            subscribed = emptySet(),
            eventLog = emptyList(),
        )
    }
}
