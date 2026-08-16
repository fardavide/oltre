package dev.fardavide.oltre.client.fleets.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.SectionLabel
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.OltreLayout
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.dispatch.ui.DispatchSheet
import dev.fardavide.oltre.client.dispatch.ui.DispatchUiState
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.ResourceKind
import kotlin.time.Duration

// **The door the Colony strip has been pointing at since 0.7.0.** The strip names the next event and
// then says `2 more away`; this is the list those two are in.
//
// Two sections, and the seam between them is the same 22dp Research spends between its branches: what
// is out, and what came back. No cancel and no recall — there is none anywhere in this app, and the
// cargo is fixed at dispatch, so a recall would have to re-derive from a commitment already made.
@Composable
fun FleetsPage(
    uiState: FleetsUiState,
    onOpenWorld: (GalaxyCoordinate) -> Unit,
    onCloseDispatch: () -> Unit,
    onSelectGathering: (ResourceKind) -> Unit,
    onSelectShips: (Int) -> Unit,
    onSelectWindow: (Duration) -> Unit,
    onDispatchRun: () -> Unit,
    // Hoisted since the Sky pass — see the same parameter on `ColonyScreen`.
    scrollState: ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // A width decision, not a change of voice — the same rule Colony and Research measure by,
        // and the same thing that drops: a trailing noun, never a number. Here the three legs lose
        // "out", "on station" and "home", because a run card is three columns of durations and the
        // words are recoverable from their order. Measured on the window rather than on the capped
        // column, because it is the window that is a Slide Over pane.
        val compact = maxWidth < OltreLayout.compactWidth
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = OltreLayout.maxContentWidth)
                    .fillMaxWidth()
                    // Ahead of the padding, as on every other screen: a tag placed after it marks the
                    // padded interior rather than the column.
                    .testTag(FleetsTestTags.CONTENT)
                    .padding(16.dp),
            ) {
                SectionLabel(text = "IN FLIGHT", rule = uiState.away)
                if (uiState.runs.isEmpty()) {
                    // **The one state on this screen with no frame behind it**, and it is drawn in the
                    // idiom the Shipyard's footnote already spends rather than invented: a muted
                    // sentence where the cards would be. A new colony has an idle pool and nothing out,
                    // so this is what the tab says on a first launch — and an empty black rectangle
                    // reads as a bug in the game rather than as a gap in it, which is the argument
                    // `UnbuiltTabScreen` was built on and the one thing worth keeping from it.
                    //
                    // PLACEHOLDER copy, like every string in the app.
                    Text(
                        text = "Nothing is out. A run starts from a world on the Galaxy tab.",
                        color = OltreColors.textTertiary,
                        fontFamily = oltreMono(),
                        fontSize = 10.5.sp,
                        lineHeight = 17.sp,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        uiState.runs.forEachIndexed { index, run ->
                            RunCard(uiState = run, index = index, compact = compact)
                        }
                    }
                }
                // Absent rather than empty on a colony nothing has ever come back to: a heading over
                // nothing is a section claiming there is a history when there is not.
                uiState.worked?.let { worked ->
                    Spacer(modifier = Modifier.height(22.dp))
                    WorkedList(uiState = worked, compact = compact, onOpenWorld = onOpenWorld)
                }
            }
        }
        // A popup rather than a layer of this box, for `GalaxyPage`'s reason: a panel drawn inside
        // the destination stops where the destination stops — above the tab bar — and lets a drag
        // through to the list behind it.
        uiState.dispatch?.let { dispatch ->
            DispatchSheet(
                uiState = dispatch,
                compact = compact,
                onDismiss = onCloseDispatch,
                onSelectGathering = onSelectGathering,
                onSelectShips = onSelectShips,
                onSelectWindow = onSelectWindow,
                onDispatch = onDispatchRun,
                // **Unreachable from here, and that is a fact rather than a stub.** The refusal that
                // hands back a probe only occurs on an unsurveyed world, and a world this list holds
                // is one a fleet has already been sent to — so it was surveyed, and `surveyed` is
                // never removed. `toFleetsUiState` passes no probe offer for the same reason.
                onDispatchProbe = {},
            )
        }
    }
}
