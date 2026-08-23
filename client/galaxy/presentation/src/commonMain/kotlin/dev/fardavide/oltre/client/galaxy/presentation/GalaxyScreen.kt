package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.fardavide.oltre.client.dispatch.presentation.DispatchSelection
import dev.fardavide.oltre.client.dispatch.presentation.bringingBack
import dev.fardavide.oltre.client.dispatch.presentation.homingIn
import dev.fardavide.oltre.client.dispatch.ui.DispatchUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyPage
import dev.fardavide.oltre.client.galaxy.ui.LedgerMode
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.NotificationSettings
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.SystemAddress
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// The Galaxy tab. **Since 0.12 it lands on the drawn galaxy** — ten banded regions folded so that
// path order is index order — with the worlds you know one tap away on the head's own switch, and one
// system pushed under it when you go somewhere to act.
//
// **Which system is showing is this feature's own state, not the shell's.** The tab set names every
// feature, which is why navigation between tabs lives in the composition root — but a system selector
// names only the galaxy, and nothing outside this module has an opinion about it. So this composable
// is the one screen in the app that holds state, and `GalaxyPage` below is the stateless half the
// screenshots and the robot drive.
// The whole state rather than its `galaxy` half since 0.0.18: a world's verdict is a function of
// what the empire has researched as well as of the seed, and `verdictFor(world, state)` is the call
// that reads both. `onOpenResearch` is the one thing this screen asks the shell for — a blocked
// row's technology is a tap target now that Research can sell it.
@Composable
fun GalaxyScreen(
    state: GameState,
    now: Instant,
    // The instant this launch advanced *from*. Defaults to `now` — an empty span, so nothing is
    // new — which is what a test or a preview that does not care should get.
    since: Instant = now,
    timeZone: TimeZone,
    // **The one thing this tab remembers between launches**, and the one place Claude Design's rule
    // that nothing here reaches the save is deliberately overruled — Davide, 2026-08-15. It arrives
    // as a parameter rather than being read here because reading it is I/O: the composition root owns
    // the file, this screen owns the meaning.
    landing: GalaxyLanding,
    onLandingChange: (GalaxyLanding) -> Unit,
    onOpenResearch: () -> Unit,
    onDispatchProbe: (SystemAddress) -> Unit,
    // The fifth verb reaching a finger for the first time. It takes all three subjects at once
    // because they are three facets of one commitment rather than three decisions — see `startRun`,
    // which takes them the same way and for the same reason.
    onDispatchRun: (GalaxyCoordinate, ResourceKind, Ships, Duration) -> Unit,
    // The bell beside both verbs this tab shows — the map card's probe and the sheet's Dispatch. It
    // takes no subject, unlike the two above it: what it moves is the standing answer the next
    // flight will be sent with, and the verb is what writes that onto a job. See `toggleFlightAlerts`.
    onToggleAnnounce: () -> Unit,
    // What the player said on the settings screen, which decides whether the bell above is drawn at
    // all: once the categories are in charge, the Probes and Fleet returns switches answer for every
    // flight. A parameter for `landing`'s reason — the file is the composition root's, the meaning is
    // this screen's — and defaulted to what every colony is already in.
    alerts: NotificationSettings = NotificationSettings.DEFAULT,
    // Hoisted since the Sky pass — see the same parameter on `ColonyScreen`.
    scrollState: ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier,
) {
    var at by remember(state.galaxy.seed) {
        mutableStateOf(SystemSelection(galaxy = state.galaxy.home.galaxy, system = state.galaxy.home.system))
    }
    // **The tab opens where you left it, and on the map the first time.** Claude Design argued the
    // map outright — *"the galaxy exists nowhere else in the app; your held worlds are on Colony and
    // on Fleets"* — and Davide took the call with one amendment, which is this `remember`'s key: the
    // landing is an input, so a preference loaded after the first frame moves the view rather than
    // being ignored until the next launch.
    var view by remember(state.galaxy.seed, landing) { mutableStateOf(landing.asView()) }
    var query by remember(state.galaxy.seed) { mutableStateOf("") }
    // What the discovery section is measured from. **It starts where the launch's own advance
    // started, not at `now`** — by the time anything is composed the state has already been advanced
    // *to* `now`, so measuring from there would exclude every survey the launch itself landed, which
    // is exactly the set the section exists to show. It moves forward only once the player has
    // looked.
    var seenAt by remember(state.galaxy.seed) { mutableStateOf(since) }
    // Which world the sheet is up on, and what has been chosen inside it. The feature's own
    // navigation exactly as `at` is — a world selector names only the galaxy, so nothing outside this
    // module has an opinion about it.
    //
    // Keyed on the system as well as the seed, which is no longer a correctness need — the selection
    // carries a whole coordinate, so nothing about it can be misread against another system — but
    // going somewhere else still closes the sheet, which is what a player means by going somewhere
    // else.
    var open by remember(state.galaxy.seed, at) { mutableStateOf<DispatchSelection?>(null) }
    val nav = GalaxyNavigation(view = view, at = at, query = query, seenAt = seenAt)
    val uiState = state.toGalaxyUiState(
        nav = nav,
        now = now,
        timeZone = timeZone,
        alerts = alerts,
        dispatch = open,
    )
    GalaxyPage(
        uiState = uiState,
        onSelectMode = { mode ->
            // Leaving the worlds list is what banks the discoveries: they were on screen, so they
            // have been seen. Anything surveyed after this is new again.
            if (mode == LedgerMode.MAP) seenAt = now
            view = if (mode == LedgerMode.WORLDS) GalaxyView.WORLDS else GalaxyView.MAP
            // **Only the switch writes the preference**, and not the chip or the push. The universe
            // is a state of the map and the orbit page is somewhere you go from it, so neither is a
            // place the tab could land — and a player who ended a check-in inside a system should
            // come back to the map they reached it from rather than to that system.
            onLandingChange(if (mode == LedgerMode.WORLDS) GalaxyLanding.WORLDS else GalaxyLanding.MAP)
        },
        // One gesture up and one back down, in the map's own frame. Nothing is pushed, so there is
        // nothing to come back from and the tab bar never changes under you.
        onToggleScale = { view = if (view == GalaxyView.UNIVERSE) GalaxyView.MAP else GalaxyView.UNIVERSE },
        onQueryChange = { query = it },
        // A galaxy is chosen in two places and means the same thing in both: a disc in the universe
        // view selects the galaxy the caption then describes, and the orbit page's segmented control
        // steps sideways without leaving the system index. Neither changes which view is up.
        onSelectGalaxy = { selected -> at = at.copy(galaxy = selected) },
        // Clamped rather than wrapped: 250 is the edge of a galaxy, and a scrub that jumped from the
        // last system to the first would be a different move than the one it looks like.
        onSelectSystem = { system ->
            at = at.copy(system = system.coerceIn(1, GalaxyBalance.SYSTEMS_PER_GALAXY))
        },
        // The caption's own tap, and the tab's one real push. From the universe it steps down into
        // the galaxy it is describing rather than into a system, because the thing selected up there
        // is a galaxy.
        onOpenSelected = { view = if (view == GalaxyView.UNIVERSE) GalaxyView.MAP else GalaxyView.SYSTEM },
        // The region name in the system header — the only accent string there, and the way back out
        // to the fold framed on the system you were reading. It opened the region index until 0.12.
        onOpenMap = { view = GalaxyView.MAP },
        onGoHome = {
            at = SystemSelection(galaxy = state.galaxy.home.galaxy, system = state.galaxy.home.system)
        },
        onOpenResearch = onOpenResearch,
        // The system on screen *is* the target — a probe is aimed at the star the page is about,
        // which is why neither the footer nor the caption needs a target picker and the world rows
        // carry no button.
        onDispatchProbe = { onDispatchProbe(SystemAddress(galaxy = at.galaxy, system = at.system)) },
        // Nothing is read off the state here, deliberately: every field but the target is null until
        // the player touches a control, and the defaults — the richer resource, the whole idle pool,
        // the 3h rung — are the mapper's to fill in. So opening a sheet cannot disagree with the
        // sheet it opened.
        //
        // **The row's own coordinate, never this screen's `at`.** A ledger row belongs to whatever
        // system it came from, and completing its address from the map's selection is what made a
        // tap price the wrong world.
        onOpenWorld = { world ->
            open = DispatchSelection(at = world, gathering = null, ships = null, window = null)
        },
        onCloseDispatch = { open = null },
        // **Both of these drop the manifest**, so the sheet re-derives the fleet that empties the
        // vein — see `homingIn`, which is where the rule lives so that this door and Fleets' cannot
        // disagree about it. The stepper is the one control that keeps what it was given.
        onSelectGathering = { kind -> open = open?.bringingBack(kind) },
        onSelectShips = { count -> open = open?.copy(ships = count) },
        onSelectWindow = { window -> open = open?.homingIn(window) },
        onDispatchRun = {
            // Read off the *rendered* offer rather than off the selection, because the offer is what
            // the player was actually shown: the mapper is what resolved the three defaults and what
            // clamped the hull count to the idle pool, and dispatching the raw selection would send
            // a run the sheet never described.
            (uiState.dispatch as? DispatchUiState.Offer)?.let { offer ->
                onDispatchRun(
                    offer.at,
                    offer.gathering,
                    offer.manifest,
                    offer.window,
                )
                // The state after the tap is its own receipt — the row's reach line, the map card
                // and the Colony strip all change — so the sheet has nothing left to say.
                open = null
            }
        },
        onToggleAnnounce = onToggleAnnounce,
        scrollState = scrollState,
        modifier = modifier,
    )
}

private fun GalaxyLanding.asView(): GalaxyView = when (this) {
    GalaxyLanding.MAP -> GalaxyView.MAP
    GalaxyLanding.WORLDS -> GalaxyView.WORLDS
}
