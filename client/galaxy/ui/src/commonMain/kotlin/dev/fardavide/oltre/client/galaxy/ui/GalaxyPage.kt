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
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.ResourceKind
import kotlin.time.Duration

// The Galaxy tab as one frame, and the whole of what this module draws.
//
// **Since 0.12 the tab opens on the map.** Four bodies under two heads: the drawn galaxy, the four
// discs of the universe, one system, and the worlds you know. Claude Design overruled its own 0.11.0
// call to land here — *"the galaxy exists nowhere else in the app; your held worlds are on Colony and
// on Fleets"* — and Davide added that the tab should then open on whichever of the two lists he last
// used, which is the one thing about this screen that reaches the save.
//
// **The map and the universe do not scroll and do not draw the starfield.** Both are deliberate.
// The fold is 531dp and fits the content area at 393dp and at 320dp alike, so a scroll would only
// ever move a map that was already whole; and decorative stars behind a drawing made of real ones is
// noise that cannot be told from data — at 320dp the shell's third parallax plane reads as extra dim
// systems. The worlds list keeps both.
//
// **Stateless, which is what makes it the ui half.** Which view is showing, what has been typed,
// which system is selected and which world has its sheet up are the feature's own navigation, and
// they live one layer up in `GalaxyScreen` — with the mapper, because deciding to look somewhere else
// and re-deriving the page from a `GameState` are the same act.
@Composable
fun GalaxyPage(
    uiState: GalaxyUiState,
    onSelectMode: (LedgerMode) -> Unit,
    onToggleScale: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectGalaxy: (Int) -> Unit,
    onSelectSystem: (Int) -> Unit,
    onOpenSelected: () -> Unit,
    onOpenMap: () -> Unit,
    onGoHome: () -> Unit,
    onOpenResearch: () -> Unit,
    onDispatchProbe: () -> Unit,
    onOpenWorld: (GalaxyCoordinate) -> Unit,
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
        val drawn = uiState.body is GalaxyBodyUiState.Map || uiState.body is GalaxyBodyUiState.Universe
        Column(
            modifier = Modifier
                .fillMaxSize()
                // The map's own opaque ground, and the cheapest honest way to keep the shell's sky
                // off it: the starfield is drawn first inside the destination box, under every
                // screen, and a feature cannot reach up and switch it off. Painting over it is one
                // rect and needs nothing hoisted.
                .then(if (drawn) Modifier.background(OltreColors.background) else Modifier)
                .then(if (drawn) Modifier else Modifier.verticalScroll(scrollState)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = OltreLayout.maxContentWidth)
                    .fillMaxWidth()
                    // Ahead of the padding, so the bounds a layout test reads are the column's own
                    // rather than its padded interior.
                    .testTag(GalaxyTestTags.CONTENT)
                    // Same reason as the body below: an unweighted child of a Column is measured
                    // against an unbounded height, so `fillMaxSize` here would wrap instead of
                    // claiming the screen and the weight inside it would have nothing to divide.
                    .then(if (drawn) Modifier.weight(1f) else Modifier)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
            ) {
                when (val head = uiState.head) {
                    is GalaxyHeadsUiState.Map -> GalaxyHead(
                        uiState = head.head,
                        onSelectMode = onSelectMode,
                        onToggleScale = onToggleScale,
                    )

                    is GalaxyHeadsUiState.Worlds -> LedgerHead(
                        uiState = head.head,
                        onSelectMode = onSelectMode,
                        onQueryChange = onQueryChange,
                    )
                }
                when (val body = uiState.body) {
                    // **`weight` and not `fillMaxSize`**, and the difference is the caption's place on
                    // the screen: a Column measures an unweighted child against an *unbounded* height,
                    // so `fillMaxSize` there silently degrades to wrap-content and the bar rides up
                    // under the fold instead of sitting at the foot. The weight is what turns the
                    // leftover space into the gap the design puts between them.
                    is GalaxyBodyUiState.Map -> MapBody(
                        body = body,
                        compact = compact,
                        onSelectSystem = onSelectSystem,
                        onOpenSelected = onOpenSelected,
                        onDispatchProbe = onDispatchProbe,
                        modifier = Modifier.weight(1f),
                    )

                    is GalaxyBodyUiState.Universe -> UniverseBody(
                        body = body,
                        compact = compact,
                        onSelectGalaxy = onSelectGalaxy,
                        onOpenSelected = onOpenSelected,
                        modifier = Modifier.weight(1f),
                    )

                    is GalaxyBodyUiState.System -> SystemBody(
                        body = body,
                        compact = compact,
                        onSelectGalaxy = onSelectGalaxy,
                        onOpenMap = onOpenMap,
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

// **The tab's landing screen since 0.12.** The fold fills what the head leaves, the caption is pushed
// to the foot by the space between them, and nothing scrolls — so the whole galaxy is on one screen
// and the one place your thumb has to land is 44dp tall and always in the same spot.
@Composable
private fun MapBody(
    body: GalaxyBodyUiState.Map,
    compact: Boolean,
    onSelectSystem: (Int) -> Unit,
    onOpenSelected: () -> Unit,
    onDispatchProbe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        GalaxyMap(uiState = body.map, onSelectSystem = onSelectSystem)
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.Bottom,
        ) {
            MapCaption(
                uiState = body.caption,
                compact = compact,
                onOpen = onOpenSelected,
                onDispatchProbe = onDispatchProbe,
            )
        }
    }
}

// One gesture up, in the map's own frame. The caption keeps its place at the foot so the two scales
// read as two states of one surface rather than as two screens.
@Composable
private fun UniverseBody(
    body: GalaxyBodyUiState.Universe,
    compact: Boolean,
    onSelectGalaxy: (Int) -> Unit,
    onOpenSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        UniverseGrid(uiState = body.universe, onSelectGalaxy = onSelectGalaxy)
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.Bottom,
        ) {
            MapCaption(
                uiState = body.caption,
                compact = compact,
                onOpen = onOpenSelected,
                onDispatchProbe = {},
            )
        }
    }
}

// Where you go to acquire a reading you do not have, and the one real push in the tab. Reading order
// down the screen is place, contents — the header answers "where am I" and the map and the list
// answer "what is here". **"Where could I go" left with the reach strip at 0.12**: it is the fold's
// question now, and the strip's own figure was already printed one line above it in the astronomy
// line, which is a duplicate 0.11.0 found and did not act on.
@Composable
private fun SystemBody(
    body: GalaxyBodyUiState.System,
    compact: Boolean,
    onSelectGalaxy: (Int) -> Unit,
    onOpenMap: () -> Unit,
    onGoHome: () -> Unit,
    onOpenResearch: () -> Unit,
    onDispatchProbe: () -> Unit,
    onOpenWorld: (GalaxyCoordinate) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        SystemHead(
            uiState = body.header,
            onSelectGalaxy = onSelectGalaxy,
            onOpenRegion = onOpenMap,
            onGoHome = onGoHome,
        )
        // The map sits on the same card surface every row does, so the screen reads as one stack
        // rather than as a picture with a list under it — and the card carries the probe in its
        // footer, because everything a verb says lands in the card that owns the thing it describes.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(14.dp))
                .background(oltreCardSurface, RoundedCornerShape(14.dp))
                .testTag(GalaxyTestTags.SYSTEM_MAP)
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
    onOpenWorld: (GalaxyCoordinate) -> Unit,
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
    onOpenWorld: (GalaxyCoordinate) -> Unit,
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
