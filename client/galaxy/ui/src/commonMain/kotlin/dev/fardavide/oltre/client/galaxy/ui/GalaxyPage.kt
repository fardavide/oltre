package dev.fardavide.oltre.client.galaxy.ui

import androidx.compose.foundation.ScrollState
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.SectionLabel
import dev.fardavide.oltre.client.design.component.oltreCardSurface
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.OltreLayout
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.core.ResourceKind
import kotlin.time.Duration

// The Galaxy tab as one frame, and the whole of what this module draws.
//
// **Since 0.11 the tab opens on what you know.** One head sits above three bodies — the ledger, one
// system, and the ten regions of a galaxy — and the head's own switch is what moves between the
// first two. Davide's call on Claude Design's recommendation: the map is where you spend probes and
// the ledger is where you spend ships, and runs go out several times a day where probes go once or
// twice, so before this the rarer errand was sitting in the commoner one's chair.
//
// **Stateless, which is what makes it the ui half.** Which view is showing, what has been typed,
// which chips are lit and which world has its sheet up are the feature's own navigation, and they
// live one layer up in `GalaxyScreen` — with the mapper, because deciding to look somewhere else and
// re-deriving the page from a `GameState` are the same act.
@Composable
fun GalaxyPage(
    uiState: GalaxyUiState,
    onSelectMode: (LedgerMode) -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleChip: (LedgerFilter) -> Unit,
    onCycleSort: () -> Unit,
    onSelectGalaxy: (Int) -> Unit,
    onSelectSystem: (Int) -> Unit,
    onOpenRegionIndex: () -> Unit,
    onOpenRegion: (Int) -> Unit,
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
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                LedgerHead(
                    uiState = uiState.head,
                    onSelectMode = onSelectMode,
                    onQueryChange = onQueryChange,
                    onToggleChip = onToggleChip,
                    onCycleSort = onCycleSort,
                )
                when (val body = uiState.body) {
                    is GalaxyBodyUiState.System -> SystemBody(
                        body = body,
                        compact = compact,
                        onSelectGalaxy = onSelectGalaxy,
                        onSelectSystem = onSelectSystem,
                        onOpenRegionIndex = onOpenRegionIndex,
                        onGoHome = onGoHome,
                        onOpenResearch = onOpenResearch,
                        onDispatchProbe = onDispatchProbe,
                        onOpenWorld = onOpenWorld,
                    )

                    is GalaxyBodyUiState.Ledger -> LedgerBody(
                        body = body.body,
                        onOpenResearch = onOpenResearch,
                        onOpenWorld = onOpenWorld,
                    )

                    is GalaxyBodyUiState.Regions -> RegionIndex(
                        galaxy = body.galaxy,
                        scope = body.scope,
                        rows = body.rows,
                        onOpenRegion = onOpenRegion,
                        modifier = Modifier.testTag(GalaxyTestTags.REGION_INDEX),
                    )
                }
            }
        }
        // A popup rather than a layer of this box: a panel drawn inside the destination stops where
        // the destination stops — above the tab bar — and lets a drag through to the list behind it.
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

// Where you go to acquire a reading you do not have. Reading order down the screen is place, reach,
// contents — the header answers "where am I", the strip answers "where could I go", and the map and
// the list answer "what is here".
@Composable
private fun SystemBody(
    body: GalaxyBodyUiState.System,
    compact: Boolean,
    onSelectGalaxy: (Int) -> Unit,
    onSelectSystem: (Int) -> Unit,
    onOpenRegionIndex: () -> Unit,
    onGoHome: () -> Unit,
    onOpenResearch: () -> Unit,
    onDispatchProbe: () -> Unit,
    onOpenWorld: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        SystemHead(
            uiState = body.header,
            onSelectGalaxy = onSelectGalaxy,
            onOpenRegion = onOpenRegionIndex,
            onGoHome = onGoHome,
        )
        RegionStrip(uiState = body.strip, compact = compact, onSelectSystem = onSelectSystem)
        // The map sits on the same card surface every row does, so the screen reads as one stack
        // rather than as a picture with a list under it — and the card carries the probe in its
        // footer, because everything a verb says lands in the card that owns the thing it describes.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(14.dp))
                .background(oltreCardSurface, RoundedCornerShape(14.dp))
                .testTag(GalaxyTestTags.MAP)
                .padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            SystemMap(map = body.map)
            ProbeAction(uiState = body.probe, compact = compact, onDispatch = onDispatchProbe)
        }
        WorldRows(rows = body.rows, onOpenResearch = onOpenResearch, onOpenWorld = onOpenWorld)
    }
}

// Where you go to spend a ship: everything you have a reading on, nearest first, in the same row the
// map uses.
@Composable
private fun LedgerBody(
    body: LedgerBodyUiState,
    onOpenResearch: () -> Unit,
    onOpenWorld: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        if (body.discoveries.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(
                    text = if (body.discoveries.size == 1) {
                        "SURVEYED"
                    } else {
                        "${body.discoveries.size} WORLDS SURVEYED"
                    },
                )
                // One gets the ceremony; two or more protect the scroll instead. At three, the thing
                // worth keeping is the check-in.
                body.discoveries.forEach { discovery ->
                    DiscoveryCard(uiState = discovery, compact = body.discoveries.size > 1)
                }
            }
        }
        if (body.pinned.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel(text = "PINNED")
                body.pinned.forEach { row ->
                    WorldRow(row = row, onOpenResearch = onOpenResearch, onOpenWorld = onOpenWorld)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            body.rows.forEach { row ->
                WorldRow(row = row, onOpenResearch = onOpenResearch, onOpenWorld = onOpenWorld)
            }
        }
        // **Never a dead end, always the next number** — the `time-until-affordable` pattern applied
        // to a query: which filter is doing the excluding, and what dropping it would return.
        body.emptiness?.let { empty ->
            Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = empty.headline,
                    color = OltreColors.textSecondary,
                    fontFamily = oltreMono(),
                    fontSize = 12.sp,
                    lineHeight = 19.2.sp,
                )
                Text(
                    text = empty.detail,
                    color = OltreColors.textTertiary,
                    fontFamily = oltreMono(),
                    fontSize = 11.5.sp,
                    lineHeight = 19.55.sp,
                )
            }
        }
    }
}

// **No band headings.** They went with the orbit tag at 0.11 — the disc's fill is the temperature
// at higher resolution and in the leading position, so a `HOT · SLOTS 1–3` heading was a caption for
// a fact the row now draws.
@Composable
private fun WorldRows(
    rows: List<GalaxyRowUiState>,
    onOpenResearch: () -> Unit,
    onOpenWorld: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            when (row) {
                is GalaxyRowUiState.World -> WorldRow(
                    row = row,
                    onOpenResearch = onOpenResearch,
                    onOpenWorld = onOpenWorld,
                )

                is GalaxyRowUiState.Relay -> RelayRow(row = row)
            }
        }
    }
}
