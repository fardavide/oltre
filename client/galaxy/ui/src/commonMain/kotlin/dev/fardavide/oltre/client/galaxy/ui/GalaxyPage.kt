package dev.fardavide.oltre.client.galaxy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.component.pressable
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.OltreLayout
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.core.settlingColor
import dev.fardavide.oltre.client.dispatch.ui.DispatchSheet
import dev.fardavide.oltre.core.ResourceKind
import kotlinx.coroutines.launch
import kotlin.time.Duration

// The Galaxy tab as one frame, and the whole of what this module draws.
//
// **Since One Sky the tab is one drawing.** The bar across the top is the address — universe,
// galaxy, region, system, world — with the depth the zoom has reached lit; the line under it is
// the count at that depth; the sky fills what is left; and the caption at the foot names what is
// selected and carries its one verb. There is no map, no system page and no ledger, because the
// four of them were four zooms of the same picture and a pinch is the whole of the navigation.
//
// **Nothing scrolls and the shell's starfield is painted over.** The sky is the scroll; and
// decorative stars behind a drawing made of real ones is noise that cannot be told from data.
//
// **Stateless, which is what makes it the ui half.** Where the eye is lives in the `SkyViewState`
// the screen holds, and the words and flags come from the mapper, one layer up — deciding to look
// somewhere else and re-deriving the page from a `GameState` are the same act.
@Composable
fun GalaxyPage(
    uiState: SkyUiState,
    viewState: SkyViewState,
    onDispatchProbe: () -> Unit,
    onRun: () -> Unit,
    onCloseDispatch: () -> Unit,
    onSelectGathering: (ResourceKind) -> Unit,
    onSelectShips: (Int) -> Unit,
    onSelectWindow: (Duration) -> Unit,
    onDispatchRun: () -> Unit,
    // **One callback for two bells**, because there is one answer: the sheet's verb and the probe's
    // both ask whether the next flight should be heard from, and both write the same standing flag.
    onToggleAnnounce: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scene = remember(uiState) { SkyScene(uiState) }
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Measured on the window rather than on the capped column, because it is the window that is
        // a Slide Over pane.
        val compact = maxWidth < OltreLayout.compactWidth
        Column(
            // The sky's own opaque ground, and the cheapest honest way to keep the shell's starfield
            // off it: painting over it is one rect and needs nothing hoisted.
            modifier = Modifier.fillMaxSize().background(OltreColors.background),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = OltreLayout.maxContentWidth)
                    .fillMaxWidth()
                    // Ahead of the padding, so the bounds a layout test reads are the column's own
                    // rather than its padded interior.
                    .testTag(GalaxyTestTags.CONTENT)
                    // An unweighted child of a Column is measured against an unbounded height, so
                    // `fillMaxSize` here would wrap instead of claiming the screen and the weight
                    // inside it would have nothing to divide.
                    .weight(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StepBar(
                    steps = uiState.steps,
                    active = viewState.view.depth,
                    onStep = { depth -> scope.launch { viewState.fly(scene.flightTo(viewState.view, depth)) } },
                )
                Text(
                    text = uiState.count.resolve(),
                    color = OltreColors.textTertiary,
                    fontFamily = oltreMono(),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(GalaxyTestTags.COUNT),
                )
                // **`weight` and not `fillMaxSize`**, and the difference is the caption's place on
                // the screen: a Column measures an unweighted child against an *unbounded* height,
                // so `fillMaxSize` there silently degrades to wrap-content and the bar rides up under
                // the sky instead of sitting at the foot. The sky is the weighted child; the caption
                // is the sky's only control and the last thing that may give.
                UniverseCanvas(
                    scene = scene,
                    viewState = viewState,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                )
                MapCaption(
                    uiState = uiState.caption,
                    compact = compact,
                    onOpen = { scope.launch { viewState.fly(scene.dived(viewState.view)) } },
                    onDispatchProbe = onDispatchProbe,
                    onRun = onRun,
                )
            }
        }
        // A popup rather than a layer of this box: a panel drawn inside the destination stops where
        // the destination stops — above the tab bar — and lets a drag through to the sky behind it.
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
                onToggleAnnounce = onToggleAnnounce,
            )
        }
    }
}

// **The address, one step per depth, and the one lit is where you are.** A tap on a step is a
// flight to that depth about the selection — the way back out that a pinch would also give, for a
// thumb that would rather not. The bar scrolls sideways when the address is longer than the
// screen, and the lit step is kept in view because it is the one you read first.
@Composable
private fun StepBar(
    steps: List<SkyStepUiState>,
    active: SkyDepth,
    onStep: (SkyDepth) -> Unit,
) {
    val scroll = rememberScrollState()
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(active, steps.size) {
        // The effect fires before the lit step has been placed, and a requester with no
        // coordinates asks for nothing; one frame later it has them.
        withFrameNanos {}
        requester.bringIntoView()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
    ) {
        steps.forEachIndexed { index, step ->
            if (index > 0) {
                Text(
                    text = "/",
                    color = OltreColors.textTertiary,
                    fontFamily = oltreMono(),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            val lit = step.depth == active
            Text(
                text = step.label.resolve(),
                color = settlingColor(if (lit) OltreColors.accent else OltreColors.textSecondary),
                fontFamily = oltreMono(),
                fontSize = 11.sp,
                fontWeight = if (lit) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .then(if (lit) Modifier.bringIntoViewRequester(requester) else Modifier)
                    .pressable(shape = STEP_SHAPE, onClick = { onStep(step.depth) })
                    .testTag(GalaxyTestTags.step(step.depth))
                    .heightIn(min = 32.dp)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
    }
}

private val STEP_SHAPE = RoundedCornerShape(8.dp)
