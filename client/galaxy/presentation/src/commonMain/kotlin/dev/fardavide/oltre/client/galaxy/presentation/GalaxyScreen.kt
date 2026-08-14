package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.fardavide.oltre.client.galaxy.ui.DispatchUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyPage
import dev.fardavide.oltre.client.galaxy.ui.LedgerFilter
import dev.fardavide.oltre.client.galaxy.ui.LedgerMode
import dev.fardavide.oltre.client.galaxy.ui.LedgerSort
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.SystemAddress
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// The Galaxy tab. One system fills the screen: the map is its fifteen orbits drawn once, left to
// right, hot to cold, and the list below carries only the slots that hold something.
//
// **Which system is showing is this feature's own state, not the shell's.** The tab set names every
// feature, which is why navigation between tabs lives in the composition root — but a system
// selector names only the galaxy, and nothing outside this module has an opinion about it. So this
// composable is the one screen in the app that holds state, and `GalaxyPage` below is the stateless
// half the screenshots and the robot drive.
// The whole state rather than its `galaxy` half since 0.0.18: a world's verdict is a function of
// what the empire has researched as well as of the seed, and `verdictFor(world, state)` is the call
// that reads both. `onOpenResearch` is the one thing this screen asks the shell for — a blocked
// row's technology is a tap target now that Research can sell it.
@Composable
fun GalaxyScreen(
    state: GameState,
    now: Instant,
    timeZone: TimeZone,
    onOpenResearch: () -> Unit,
    onDispatchProbe: (SystemAddress) -> Unit,
    // The fifth verb reaching a finger for the first time. It takes all three subjects at once
    // because they are three facets of one commitment rather than three decisions — see `startRun`,
    // which takes them the same way and for the same reason.
    onDispatchRun: (GalaxyCoordinate, ResourceKind, Ships, Duration) -> Unit,
    // Hoisted since the Sky pass — see the same parameter on `ColonyScreen`.
    scrollState: ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier,
) {
    var at by remember(state.galaxy.seed) {
        mutableStateOf(SystemSelection(galaxy = state.galaxy.home.galaxy, system = state.galaxy.home.system))
    }
    // **The tab opens on what you know.** Davide's call, 2026-08-14 — the map is where you spend
    // probes and the ledger is where you spend ships, and runs go out several times a day where
    // probes go once or twice.
    var view by remember(state.galaxy.seed) { mutableStateOf(GalaxyView.LEDGER) }
    var query by remember(state.galaxy.seed) { mutableStateOf("") }
    var filters by remember(state.galaxy.seed) { mutableStateOf(emptySet<LedgerFilter>()) }
    var sort by remember(state.galaxy.seed) { mutableStateOf(LedgerSort.NEAREST) }
    // What the discovery section is measured from. It starts at the instant this screen was first
    // composed, so a world surveyed while the app was closed is new and one surveyed before that is
    // not — and it moves forward only when the player has actually looked.
    var seenAt by remember(state.galaxy.seed) { mutableStateOf(now) }
    // Which world the sheet is up on, and what has been chosen inside it. The feature's own
    // navigation exactly as `at` is — a world selector names only the galaxy, so nothing outside this
    // module has an opinion about it.
    //
    // Keyed on the system as well as the seed: a sheet is raised on a *slot*, and carrying that slot
    // across a change of system would leave it open on a different world than the one it was raised
    // from. Going somewhere else closes it, which is also what a player means by going somewhere else.
    var open by remember(state.galaxy.seed, at) { mutableStateOf<DispatchSelection?>(null) }
    val nav = GalaxyNavigation(
        view = view,
        at = at,
        query = query,
        filters = filters,
        sort = sort,
        seenAt = seenAt,
        availableFilters = state.availableFiltersFor(at),
    )
    val uiState = state.toGalaxyUiState(nav = nav, now = now, timeZone = timeZone, dispatch = open)
    GalaxyPage(
        uiState = uiState,
        onSelectMode = { mode ->
            // Leaving the ledger is what banks the discoveries: they were on screen, so they have
            // been seen. Anything surveyed after this is new again.
            if (mode == LedgerMode.MAP) seenAt = now
            view = if (mode == LedgerMode.WORLDS) GalaxyView.LEDGER else GalaxyView.SYSTEM
        },
        onQueryChange = { query = it },
        onToggleChip = { filter -> filters = if (filter in filters) filters - filter else filters + filter },
        onCycleSort = { sort = LedgerSort.entries[(sort.ordinal + 1) % LedgerSort.entries.size] },
        onOpenRegionIndex = { view = GalaxyView.REGIONS },
        // Choosing a region puts you at its first system and hands you back the map — the index is a
        // chooser, not a level you stay in.
        onOpenRegion = { region ->
            at = at.copy(system = (region - 1) * GalaxyBalance.SYSTEMS_PER_REGION + 1)
            view = GalaxyView.SYSTEM
        },
        onSelectGalaxy = { selected -> at = at.copy(galaxy = selected) },
        // Clamped rather than wrapped: 250 is the edge of a galaxy, and a band that jumped from the
        // last system to the first would be a different move than the one it looks like.
        onSelectSystem = { system ->
            at = at.copy(system = system.coerceIn(1, GalaxyBalance.SYSTEMS_PER_GALAXY))
            view = GalaxyView.SYSTEM
        },
        onGoHome = {
            at = SystemSelection(galaxy = state.galaxy.home.galaxy, system = state.galaxy.home.system)
        },
        onOpenResearch = onOpenResearch,
        // The system on screen *is* the target — a probe is aimed at the star the page is about,
        // which is why the footer needs no target picker and the world rows carry no button.
        onDispatchProbe = { onDispatchProbe(SystemAddress(galaxy = at.galaxy, system = at.system)) },
        // Nothing is read off the state here, deliberately: every field but the slot is null until
        // the player touches a control, and the defaults — the richer resource, the whole idle pool,
        // the 3h rung — are the mapper's to fill in. So opening a sheet cannot disagree with the
        // sheet it opened.
        onOpenWorld = { slot ->
            open = DispatchSelection(slot = slot, gathering = null, ships = null, window = null)
        },
        onCloseDispatch = { open = null },
        onSelectGathering = { kind -> open = open?.copy(gathering = kind) },
        onSelectShips = { count -> open = open?.copy(ships = count) },
        onSelectWindow = { window -> open = open?.copy(window = window) },
        onDispatchRun = {
            // Read off the *rendered* offer rather than off the selection, because the offer is what
            // the player was actually shown: the mapper is what resolved the three defaults and what
            // clamped the hull count to the idle pool, and dispatching the raw selection would send
            // a run the sheet never described.
            (uiState.dispatch as? DispatchUiState.Offer)?.let { offer ->
                onDispatchRun(
                    GalaxyCoordinate(galaxy = at.galaxy, system = at.system, slot = offer.slot),
                    offer.gathering,
                    Ships.of(ShipType.SKIFF, offer.shipCount),
                    offer.window,
                )
                // The state after the tap is its own receipt — the row's reach line, the map card
                // and the Colony strip all change — so the sheet has nothing left to say.
                open = null
            }
        },
        scrollState = scrollState,
        modifier = modifier,
    )
}
