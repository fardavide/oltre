package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.dispatch.presentation.DispatchSelection
import dev.fardavide.oltre.client.galaxy.ui.GalaxyUiState
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.startSurvey
import dev.fardavide.oltre.core.worldAt
import kotlin.test.assertIs
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// **Every frame the Galaxy tab is photographed in, derived from a real `GameState` through the real
// mapper.** Until 0.11 these were three thousand lines of Kotlin emitted by a generator and pasted
// into `:client:galaxy:ui-testing`, and that file's own header named the cost it was accepting:
//
//   *"the drift the old header warned about is now real again. Nothing recomputes these; a mapper
//    that re-words a verdict or re-rounds a richness leaves this file asserting the old text, and
//    the baselines will agree with it."*
//
// **The redesign made that unaffordable rather than merely untidy** — every row, every header and
// the whole body shape changed at once, so a hand-stated copy would have had to be regenerated
// wholesale anyway. What it is replaced with is the thing the split was thought to forbid: the
// screenshot tests moved from `:client:galaxy:ui` to this module, which owns the same feature and
// *can* see a `GameState`. The `screenshot-testing` skill asks for the owning client module's
// `desktopTest`, and this is one.
//
// So a frame is now `state.toGalaxyUiState(nav)` — the same call the app makes — and a mapper that
// re-words anything moves a baseline, which is what a baseline is for.

// The colony every frame describes. One seed, so a coordinate means the same thing in all of them.
internal val frameState: GameState = testGameState

internal fun frame(
    state: GameState = frameState,
    // The map, because that is what the tab lands on since 0.12 — a default that is the screen a
    // player actually opens on is the one worth having in every frame that does not say otherwise.
    view: GalaxyView = GalaxyView.MAP,
    at: SystemSelection = state.homeSelection(),
    query: String = "",
    // Where the frame's "what happened while you were away" span begins. Every frame but the
    // discovery one starts it at the frame's own instant — an empty span, so nothing is new and a
    // card cannot appear in a baseline that is not about one.
    seenAt: Instant = FIXTURE_NOW,
    dispatch: DispatchSelection? = null,
): GalaxyUiState = state.toGalaxyUiState(
    nav = GalaxyNavigation(view = view, at = at, query = query, seenAt = seenAt),
    now = FIXTURE_NOW,
    timeZone = TimeZone.UTC,
    dispatch = dispatch,
)

internal fun GameState.homeSelection(): SystemSelection =
    SystemSelection(galaxy = galaxy.home.galaxy, system = galaxy.home.system)

// A neighbour nobody has looked at: 249 systems in 250 are in this state on the day the slice
// ships, so it is the screen rather than a stage before the screen.
internal fun GameState.neighbourSelection(): SystemSelection =
    homeSelection().let { it.copy(system = (it.system + 1).coerceAtMost(GalaxyBalance.SYSTEMS_PER_GALAXY)) }

// That neighbour as a whole page. 249 systems in 250 read exactly like this on the day the slice
// ships, so it is the screen an unsurveyed row has to be honest on rather than a stage before one.
internal val unsurveyedSystemUiState: GalaxyUiState =
    frame(view = GalaxyView.SYSTEM, at = frameState.neighbourSelection())

// A colony a fortnight in: it has surveyed a spread of systems, so the ledger has something to sort
// and the region index has something to count. Built by surveying rather than by hand-writing a set,
// so every world in it is one a probe could really have reached.
internal val wellTravelledState: GameState = frameState.surveying(
    systems = listOf(-7, -1, 2, 6, 13),
)

// The same colony with two worlds pinned, so the ledger's own top section has something in it.
internal val pinnedState: GameState = wellTravelledState.let { state ->
    val two = state.galaxy.surveyed
        .filter { it != state.galaxy.home }
        .sortedWith(compareBy({ it.system }, { it.slot }))
        .take(2)
        .toSet()
    state.copy(galaxy = state.galaxy.copy(pinned = two))
}

// A colony that surveyed something while the player was away. The event is what makes it a
// discovery — nothing is stored on the world — so the frame is a real log entry rather than a flag.
// An hour before the frame's instant, so the landing is inside the span the frame is measured from.
internal val JUST_SURVEYED_SINCE: Instant = FIXTURE_NOW - kotlin.time.Duration.parse("2h")

internal val justSurveyedState: GameState = wellTravelledState.let { state ->
    val target = SystemAddress(
        galaxy = state.galaxy.home.galaxy,
        system = state.galaxy.home.system + 2,
    )
    state.copy(
        eventLog = state.eventLog + Event.SurveyCompleted(
            target = target,
            worldsFound = state.worldsOf(SystemSelection(target.galaxy, target.system)).size,
            // An hour *before* the frame's instant, so the card reads "found 1h 00m ago" rather
            // than a negative span — a survey cannot land in the future, and the frame is measured
            // from `FIXTURE_NOW` the way a launch is measured from where it advanced.
            at = FIXTURE_NOW - kotlin.time.Duration.parse("1h"),
        ),
    )
}

// Surveys the systems at the given offsets from home, which is what a fortnight of probes buys.
private fun GameState.surveying(systems: List<Int>): GameState {
    val added = systems.flatMap { offset ->
        val system = (galaxy.home.system + offset).coerceIn(1, GalaxyBalance.SYSTEMS_PER_GALAXY)
        (1..GalaxyBalance.SLOTS_PER_SYSTEM)
            .map { slot -> GalaxyCoordinate(galaxy = galaxy.home.galaxy, system = system, slot = slot) }
            .filter { worldAt(galaxy.seed, it) != null }
    }
    return copy(galaxy = galaxy.copy(surveyed = galaxy.surveyed + added))
}

// The fold with a probe out, which is the one overlay no other map frame carries. It lives here
// rather than in `ProbeFrames` because it is a frame of the *map* — that file is the orbit page's
// footer in the two states that are a job rather than an offer, and a baseline belongs beside the
// screen it photographs.
internal val probeInFlightMapUiState: GalaxyUiState = frameState
    .copy(resources = Resources.of(metal = 40_000, crystal = 9_000))
    .let { wealthy ->
        val target = wealthy.neighbourSelection()
            .let { SystemAddress(galaxy = it.galaxy, system = it.system) }
        val dispatched = assertIs<StartSurveyResult.Started>(
            startSurvey(wealthy, target, at = FIXTURE_NOW),
        ).state
        frame(state = dispatched, view = GalaxyView.MAP, at = dispatched.homeSelection())
    }

// **The home system while a probe is away, which is the only place the trajectory arc is ever
// drawn.** `toSystemMapUiState` gives a map its arc when the page is home *and* a survey is out, so
// every other frame of the orbit page — the target's, the relay's, the unsurveyed neighbour's — is a
// map with no arc on it.
//
// It is here because 0.12 nearly lost it. Until this slice the arc was covered incidentally: the
// behaviour suite dispatched a probe from the home system's own footer and stayed there to watch the
// countdown. The map is where a probe is aimed from now, so nobody was standing on that page any
// more, and thirty-four lines of `SystemMap` stopped being executed by anything at all — which the
// coverage table caught and no test failure would have.
internal val probeOutFromHomeUiState: GalaxyUiState = frameState
    .copy(resources = Resources.of(metal = 40_000, crystal = 9_000))
    .let { wealthy ->
        val target = wealthy.neighbourSelection()
            .let { SystemAddress(galaxy = it.galaxy, system = it.system) }
        val dispatched = assertIs<StartSurveyResult.Started>(
            startSurvey(wealthy, target, at = FIXTURE_NOW),
        ).state
        frame(state = dispatched, view = GalaxyView.SYSTEM, at = dispatched.homeSelection())
    }
