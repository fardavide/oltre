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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import dev.fardavide.oltre.client.design.component.oltreCardSurface
import dev.fardavide.oltre.client.design.core.OltreLayout
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.SystemAddress
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
    modifier: Modifier = Modifier,
) {
    var at by remember(state.galaxy.seed) {
        mutableStateOf(SystemSelection(galaxy = state.galaxy.home.galaxy, system = state.galaxy.home.system))
    }
    GalaxyPage(
        uiState = state.toGalaxyUiState(at = at, now = now, timeZone = timeZone),
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
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // A width decision, not a change of voice: below this the header drops the word "worlds".
        // Measured on the window rather than on the capped column, because it is the window that is
        // a Slide Over pane.
        val compact = maxWidth < OltreLayout.compactWidth
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
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
                    modifier = Modifier.padding(bottom = 13.dp),
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
                    modifier = Modifier.padding(top = 13.dp),
                )
            }
        }
    }
}
