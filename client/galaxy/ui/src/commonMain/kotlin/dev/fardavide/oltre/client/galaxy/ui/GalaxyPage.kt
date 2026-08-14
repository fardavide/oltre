package dev.fardavide.oltre.client.galaxy.ui

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
import dev.fardavide.oltre.core.ResourceKind
import kotlin.time.Duration

// The Galaxy tab as one frame, and the whole of what this module draws. One system fills the screen:
// the map is its fifteen orbits drawn once, left to right, hot to cold, and the list below carries
// only the slots that hold something.
//
// **Stateless, which is what makes it the ui half.** Which system is showing and which world has its
// sheet up are the feature's own navigation, and they live one layer up in `GalaxyScreen` — with the
// mapper, because deciding to look somewhere else and re-deriving the page from a `GameState` are
// the same act. Everything here is handed a frame and draws it.
@Composable
fun GalaxyPage(
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
        // A popup rather than a layer of this box, which is what it was at 0.7.0: a panel drawn
        // inside the destination stops where the destination stops — above the tab bar — and lets a
        // drag through to the list behind it. `OltreBottomSheet` covers the window instead. It stays
        // outside the scrolling column for the reason it always did, which now follows rather than
        // being arranged for.
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
