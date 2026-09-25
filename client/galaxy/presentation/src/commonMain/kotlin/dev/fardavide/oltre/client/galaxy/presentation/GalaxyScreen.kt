package dev.fardavide.oltre.client.galaxy.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.fardavide.oltre.client.design.component.RefusalUiState
import dev.fardavide.oltre.client.dispatch.presentation.DispatchSelection
import dev.fardavide.oltre.client.dispatch.presentation.bringingBack
import dev.fardavide.oltre.client.dispatch.presentation.homingIn
import dev.fardavide.oltre.client.dispatch.ui.DispatchUiState
import dev.fardavide.oltre.client.galaxy.ui.GalaxyPage
import dev.fardavide.oltre.client.galaxy.ui.SkyDepth
import dev.fardavide.oltre.client.galaxy.ui.SkyGeometry
import dev.fardavide.oltre.client.galaxy.ui.SkySelection
import dev.fardavide.oltre.client.galaxy.ui.SkyView
import dev.fardavide.oltre.client.galaxy.ui.SkyViewState
import dev.fardavide.oltre.client.galaxy.ui.system
import dev.fardavide.oltre.client.galaxy.ui.world
import dev.fardavide.oltre.client.net.domain.HeldActions
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.layoutAt
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// The Galaxy tab. **Since One Sky it is one drawing**, and the whole of its navigation is where
// the eye is: a zoom, a centre and a selection, which a pinch, a tap and the step bar all move.
// There is no map, no system page and no worlds ledger, because the three of them were three zooms
// of the same picture.
//
// **Where the eye is is this feature's own state, not the shell's.** The tab set names every
// feature, which is why navigation between tabs lives in the composition root — but a view over the
// sky names only the galaxy, and nothing outside this module has an opinion about it. So this
// composable is the one screen in the app that holds state, and `GalaxyPage` is the stateless half
// the screenshots and the robot drive.
//
// **It lands on your own region every launch, framed on your star.** The old tab remembered a
// landing between launches — map or worlds — and that was a choice between two pages. One drawing
// has no pages to choose between, and the region about home is where every session starts anyway:
// the probes you can afford and the veins you know are all within a pinch of it.
//
// The whole state rather than its `galaxy` half: a world's verdict is a function of what the
// empire has researched as well as of the seed, and `verdictFor(world, state)` is the call that
// reads both.
@Composable
fun GalaxyScreen(
    state: GameState,
    now: Instant,
    timeZone: TimeZone,
    onDispatchProbe: (SystemAddress) -> Unit,
    // The fifth verb reaching a finger. It takes all three subjects at once because they are three
    // facets of one commitment rather than three decisions — see `startRun`, which takes them the
    // same way and for the same reason.
    // **It answers whether the tap was kept**, which since 0.21 is a question a dispatch can have a
    // *no* to: a run aims at a shared galaxy, so it cannot be queued and refuses at the tap. The
    // screen needs that answer synchronously to know whether to close the sheet, and the composition
    // root is the only thing that can give it — see `App`, and see the note at the call site.
    onDispatchRun: (GalaxyCoordinate, ResourceKind, Ships, Duration) -> Boolean,
    // The bell beside the sheet's verbs. It takes no subject, unlike the two above it: what it moves
    // is the standing answer the next flight will be sent with, and the verb is what writes that
    // onto a job. See `toggleFlightAlerts`.
    onToggleAnnounce: () -> Unit,
    // What the phone has accepted and the server has not, and what the last tap on a verb that
    // cannot be held produced. Both are facts about the network rather than about the sky, which is
    // why they arrive from the composition root — see the same pair on the colony's mapper.
    held: HeldActions = HeldActions.NONE,
    refusal: RefusalUiState? = null,
    modifier: Modifier = Modifier,
) {
    val home = state.galaxy.home
    val viewState = remember(state.galaxy.seed) {
        val star = SkyGeometry(home.galaxy).positionOf(
            galaxy = home.galaxy,
            system = home.system,
            driftPermille = layoutAt(state.galaxy.seed, home.galaxy, home.system).driftPermille,
        )
        SkyViewState(
            SkyView(
                ppu = SkyGeometry.ppuOf(SkyDepth.REGION),
                centreX = star.x,
                centreY = star.y,
                selection = SkySelection.System(SystemAddress.of(home)),
            ),
        )
    }
    val view = viewState.view
    // Which world the sheet is up on, and what has been chosen inside it. The feature's own
    // navigation exactly as the view is — a world selector names only the galaxy, so nothing outside
    // this module has an opinion about it.
    //
    // Keyed on the selected system as well as the seed: going somewhere else closes the sheet, which
    // is what a player means by going somewhere else.
    var open by remember(state.galaxy.seed, view.selection.system) { mutableStateOf<DispatchSelection?>(null) }
    val uiState = state.toSkyUiState(
        selection = view.selection,
        depth = view.depth,
        now = now,
        timeZone = timeZone,
        dispatch = open,
        held = held,
        refusal = refusal,
    )
    GalaxyPage(
        uiState = uiState,
        viewState = viewState,
        // The selected star *is* the target — a probe is aimed at the star the caption is about,
        // which is why neither the caption nor the sheet needs a target picker. Absent a system the
        // caption offers no probe, so the fallback is a verb that is never reached.
        onDispatchProbe = { view.selection.system?.let(onDispatchProbe) },
        // Nothing is read off the state here, deliberately: every field but the target is null until
        // the player touches a control, and the defaults — the richer resource, the whole idle pool,
        // the 3h rung — are the mapper's to fill in. So opening a sheet cannot disagree with the
        // sheet it opened.
        onRun = {
            view.selection.world?.let { world ->
                open = DispatchSelection(at = world, gathering = null, ships = null, window = null)
            }
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
                val kept = onDispatchRun(
                    offer.at,
                    offer.gathering,
                    offer.manifest,
                    offer.window,
                )
                // **The sheet closes on a dispatch that was kept and stays up on one that was not**,
                // which is the whole of the refusal's shape: the block appears above the button, the
                // button holds its place, and the manifest and the clock stay on screen — because the
                // refusal is about the *target* rather than about the run the player just assembled,
                // and reopening the sheet later should not cost them the assembly.
                //
                // **The answer comes back from the callback rather than from a flag about the
                // network**, which is what keeps this screen knowing nothing about connections: it
                // asks *was this kept*, and the composition root is the only thing that can say.
                if (kept) open = null
            }
        },
        onToggleAnnounce = onToggleAnnounce,
        modifier = modifier,
    )
}
