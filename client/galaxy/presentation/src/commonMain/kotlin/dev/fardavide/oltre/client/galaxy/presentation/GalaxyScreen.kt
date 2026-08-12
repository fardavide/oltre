package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.oltreCardSurface
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.OltreLayout
import dev.fardavide.oltre.client.design.core.oltreMono
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
    // Which world the sheet is up on, and what has been chosen inside it. The feature's own
    // navigation exactly as `at` is — a world selector names only the galaxy, so nothing outside this
    // module has an opinion about it.
    //
    // Keyed on the system as well as the seed: a sheet is raised on a *slot*, and carrying that slot
    // across a change of system would leave it open on a different world than the one it was raised
    // from. Going somewhere else closes it, which is also what a player means by going somewhere else.
    var open by remember(state.galaxy.seed, at) { mutableStateOf<DispatchSelection?>(null) }
    val uiState = state.toGalaxyUiState(at = at, now = now, timeZone = timeZone, dispatch = open)
    GalaxyPage(
        uiState = uiState,
        onSelectGalaxy = { selected -> at = at.copy(galaxy = selected) },
        // Clamped rather than wrapped: 250 is the edge of a galaxy, and a band that jumped from the
        // last system to the first would be a different move than the one it looks like.
        onSelectSystem = { system ->
            at = at.copy(system = system.coerceIn(1, GalaxyBalance.SYSTEMS_PER_GALAXY))
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

@Composable
internal fun GalaxyPage(
    uiState: GalaxyUiState,
    onSelectGalaxy: (Int) -> Unit,
    onSelectSystem: (Int) -> Unit,
    onGoHome: () -> Unit,
    onOpenResearch: () -> Unit,
    onDispatchProbe: () -> Unit,
    onOpenWorld: (Int) -> Unit,
    onCloseDispatch: () -> Unit,
    onSelectGathering: (ResourceKind) -> Unit,
    onSelectShips: (Int) -> Unit,
    onSelectWindow: (Duration) -> Unit,
    onDispatchRun: () -> Unit,
    scrollState: ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // A width decision, not a change of voice: below this the header drops the word "worlds".
        // Measured on the window rather than on the capped column, because it is the window that is
        // a Slide Over pane.
        val compact = maxWidth < OltreLayout.compactWidth
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = OltreLayout.maxContentWidth)
                    .fillMaxWidth()
                    // Ahead of the padding, so the bounds a layout test reads are the column's own
                    // rather than its padded interior.
                    .testTag(GalaxyTestTags.CONTENT)
                    .padding(16.dp),
            ) {
                GalaxyNav(
                    uiState = uiState,
                    compact = compact,
                    onSelectGalaxy = onSelectGalaxy,
                    onGoHome = onGoHome,
                    modifier = Modifier.padding(bottom = 9.dp),
                )
                // Directly under the header, because it is a fact about the *system* — the distance
                // band is identical for all fifteen slots, so stating it on rows would be printing
                // one number up to fifteen times. See `GalaxyUiState.astronomy`.
                Text(
                    text = uiState.astronomy,
                    color = OltreColors.textSecondary,
                    fontFamily = oltreMono(),
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.testTag(GalaxyTestTags.ASTRONOMY).padding(bottom = 13.dp),
                )
                // Above the map rather than below it, because it answers "where am I going" and the
                // map answers "what is here" — and the header above it is what answers "where am
                // I". Reading order down the screen is place, reach, contents.
                ReachBand(
                    uiState = uiState.reach,
                    compact = compact,
                    onSelectSystem = onSelectSystem,
                    modifier = Modifier.padding(bottom = 13.dp),
                )
                // The map sits on the same card surface every row does, so the screen reads as one
                // stack rather than as a picture with a list under it — and since 0.2.0 the card
                // carries the probe in its footer. Everything the verb says lands in the card that
                // owns the thing it describes, which is the rule that already puts a build's
                // countdown inside its facility row.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(14.dp))
                        .background(oltreCardSurface, RoundedCornerShape(14.dp))
                        .testTag(GalaxyTestTags.MAP)
                        .padding(11.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    SystemMap(map = uiState.map)
                    ProbeAction(uiState = uiState.probe, compact = compact, onDispatch = onDispatchProbe)
                }
                WorldList(
                    bands = uiState.bands,
                    compact = compact,
                    onOpenResearch = onOpenResearch,
                    onOpenWorld = onOpenWorld,
                    modifier = Modifier.padding(top = 13.dp),
                )
            }
        }
        // Outside the scrolling column and last in the box, so it covers the whole destination
        // rather than a scrolled slice of it. It is the one thing in this app that sits over another
        // screen: everything else is a list you scroll, and a sheet is here because a run is the one
        // action with three inputs.
        uiState.dispatch?.let { dispatch ->
            DispatchSheet(
                uiState = dispatch,
                compact = compact,
                onDismiss = onCloseDispatch,
                onSelectGathering = onSelectGathering,
                onSelectShips = onSelectShips,
                onSelectWindow = onSelectWindow,
                onDispatch = onDispatchRun,
                onDispatchProbe = onDispatchProbe,
            )
        }
    }
}
