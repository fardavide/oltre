package dev.fardavide.oltre.client.galaxy.presentation

import dev.fardavide.oltre.client.galaxy.ui.GalaxyUiState
import dev.fardavide.oltre.client.galaxy.ui.LedgerFilter
import dev.fardavide.oltre.client.galaxy.ui.LedgerSort
import dev.fardavide.oltre.client.galaxy.ui.WorldVerdictUiState
import dev.fardavide.oltre.core.Event
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.worldAt
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
    view: GalaxyView = GalaxyView.LEDGER,
    at: SystemSelection = state.homeSelection(),
    query: String = "",
    filters: Set<LedgerFilter> = emptySet(),
    sort: LedgerSort = LedgerSort.NEAREST,
    dispatch: DispatchSelection? = null,
): GalaxyUiState = state.toGalaxyUiState(
    nav = GalaxyNavigation(
        view = view,
        at = at,
        query = query,
        filters = filters,
        sort = sort,
        // The epoch, so nothing is ever "new" unless a frame deliberately makes it so — a discovery
        // card that appeared in every baseline would be a card nobody could photograph the absence
        // of.
        seenAt = FIXTURE_NOW,
        availableFilters = state.availableFiltersFor(at),
    ),
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
internal val justSurveyedState: GameState = wellTravelledState.let { state ->
    val target = SystemAddress(
        galaxy = state.galaxy.home.galaxy,
        system = state.galaxy.home.system + 2,
    )
    state.copy(
        eventLog = state.eventLog + Event.SurveyCompleted(
            target = target,
            worldsFound = state.worldsOf(SystemSelection(target.galaxy, target.system)).size,
            at = FIXTURE_NOW + kotlin.time.Duration.parse("1h"),
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

// The three filters of the design's "nothing left" frame — chosen so that together they really do
// exclude everything, which is the state the empty copy exists for.
internal val excludingFilters: Set<LedgerFilter> = setOf(
    LedgerFilter.ReachableWithin(hours = 2),
    LedgerFilter.StillHolding,
    LedgerFilter.Verdict(WorldVerdictUiState.SETTLEABLE),
)
