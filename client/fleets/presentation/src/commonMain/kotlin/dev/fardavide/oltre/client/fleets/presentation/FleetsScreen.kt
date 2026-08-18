package dev.fardavide.oltre.client.fleets.presentation

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
import dev.fardavide.oltre.client.fleets.ui.FleetsPage
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

// The Fleets tab. **It stopped being read-only at 0.13**, which is the whole of issue #62: the list
// of what came home is where a player remembers which world was worth going to, so tapping one
// raises the same sheet a world row raises on Galaxy.
//
// **Which world has its sheet up is this feature's own state**, exactly as it is Galaxy's — nothing
// outside this module has an opinion about it — so this composable holds it and `FleetsPage` is the
// stateless half the screenshots and the robot drive. That is the second `presentation` module with
// Compose in it, and it passes the test the first one set: *does the module decide, or does it draw
// a frame it was handed.*
@Composable
fun FleetsScreen(
    state: GameState,
    now: Instant,
    // The instant this launch advanced from, so a world that landed while the app was closed says
    // so. Defaults to `now` — an empty span, so nothing is new.
    since: Instant = now,
    timeZone: TimeZone,
    // The fifth verb, reaching a second finger. It takes all three subjects at once because they are
    // three facets of one commitment rather than three decisions — see `startRun`, and see
    // `GalaxyScreen`, which hands it over the same way.
    onDispatchRun: (GalaxyCoordinate, ResourceKind, Ships, Duration) -> Unit,
    // Hoisted since the Sky pass — see the same parameter on `ColonyScreen`.
    scrollState: ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier,
) {
    // Keyed on the seed alone: this tab has no "somewhere else" to go, so nothing but a new galaxy
    // closes the sheet from underneath.
    var open by remember(state.galaxy.seed) { mutableStateOf<DispatchSelection?>(null) }
    val uiState = state.toFleetsUiState(now = now, since = since, timeZone = timeZone, dispatch = open)
    FleetsPage(
        uiState = uiState,
        // **Nothing is read off the run that was tapped, and that is the ruling rather than an
        // omission** (Davide, 2026-08-13). Every field but the target is null, so the mapper fills
        // the blanks with defaults that move with the state — the richer resource, the whole idle
        // pool, the 3h rung. Pre-filling the tapped run's resource, manifest and window would be
        // "relaunch with last settings" arriving through a side door, which `fleet-sheet.md` §8
        // rejects by name. The ledger shortcuts navigation and nothing else.
        onOpenWorld = { world ->
            open = DispatchSelection(at = world, gathering = null, ships = null, window = null)
        },
        onCloseDispatch = { open = null },
        // Both drop the manifest so the sheet re-derives the fleet that empties the vein — the rule
        // is `homingIn`'s rather than this screen's, so that this door and Galaxy's cannot disagree
        // about what a rung means.
        onSelectGathering = { kind -> open = open?.bringingBack(kind) },
        onSelectShips = { count -> open = open?.copy(ships = count) },
        onSelectWindow = { window -> open = open?.homingIn(window) },
        onDispatchRun = {
            // Read off the *rendered* offer rather than off the selection, because the offer is what
            // the player was actually shown — see `GalaxyScreen`, where the same three lines guard
            // the same mistake.
            (uiState.dispatch as? DispatchUiState.Offer)?.let { offer ->
                onDispatchRun(offer.at, offer.gathering, Ships.of(ShipType.SKIFF, offer.shipCount), offer.window)
                // The state after the tap is its own receipt — a card appears in In flight above and
                // the run count on the row goes up — so the sheet has nothing left to say.
                open = null
            }
        },
        scrollState = scrollState,
        modifier = modifier,
    )
}
