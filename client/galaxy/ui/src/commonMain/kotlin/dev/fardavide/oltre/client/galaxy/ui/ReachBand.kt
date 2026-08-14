package dev.fardavide.oltre.client.galaxy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.StarClass

// Three parts stacked where the ±1 stepper used to be: a strip of all 250 systems with the free
// half of the charted tier drawn on it, an hour ruler measured from your own star, and a row of
// cells that are the only things you tap.
//
// **Two speeds, and the split is the design rather than a compromise.** Dragging the strip moves
// the lens, and at ~1.4dp a system that drag is coarse by construction — which is right: the strip
// picks a neighbourhood and an hour, the lens picks the star. Precision arrives exactly where it is
// needed and nowhere else.
@Composable
internal fun ReachBand(
    uiState: ReachBandUiState,
    compact: Boolean,
    onSelectSystem: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lens = if (compact) uiState.compactLens else uiState.lens
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Strip(ticks = uiState.ticks, lens = lens, onSelectSystem = onSelectSystem)
        Ruler(marks = uiState.marks)
        Lens(lens = lens, onSelectSystem = onSelectSystem)
    }
}

// 250 ticks in one `Canvas`, drawn once per selection change — no animation and no state that has
// to survive a foreground. Same argument that made the system map a Canvas: this is geometry, not
// 250 composables.
//
// **The 22dp of ticks claims 44dp of touch**, and it is a scrub target rather than 250 small ones:
// no tick is individually tappable at either width, and the design does not pretend otherwise.
@Composable
private fun Strip(ticks: List<ReachTickUiState>, lens: ReachLensUiState, onSelectSystem: (Int) -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(STRIP_TOUCH_HEIGHT)
            .testTag(GalaxyTestTags.REACH_STRIP),
        contentAlignment = Alignment.Center,
    ) {
        val step = maxWidth / GalaxyBalance.SYSTEMS_PER_GALAXY
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        // **Which cell contains the touch, not which boundary is nearest.** Rounding instead of
        // flooring shifts the whole mapping by half a system: at 1.44dp a tick, touching the left
        // half of a tick's cell would select its neighbour, and on a control whose entire job is
        // "put the lens where I pointed" that is the one error it cannot afford.
        val systemAt = { x: Float ->
            ((x / widthPx * GalaxyBalance.SYSTEMS_PER_GALAXY).toInt() + 1)
                .coerceIn(1, GalaxyBalance.SYSTEMS_PER_GALAXY)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(STRIP_HEIGHT)
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures { change, _ -> onSelectSystem(systemAt(change.position.x)) }
                }
                .pointerInput(widthPx) {
                    // A tap is a drag of length zero and should mean the same thing on a scrub
                    // target. Without this, touching the strip without moving does nothing at all.
                    detectTapGestures { offset -> onSelectSystem(systemAt(offset.x)) }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxWidth().height(STRIP_HEIGHT)) {
                val stepPx = size.width / GalaxyBalance.SYSTEMS_PER_GALAXY
                // The line the ticks stand on. Without it the strip is a row of marks; with it, it
                // is an axis — which is what the ruler underneath is measuring along.
                drawRect(
                    color = Color.White.copy(alpha = 0.09f),
                    topLeft = Offset(x = 0f, y = size.height - 1f),
                    size = Size(width = size.width, height = 1f),
                )
                ticks.forEach { tick ->
                    val spec = tick.mark.spec()
                    val thickness = spec.width.toPx()
                    val height = spec.height.toPx()
                    // Grounded on the baseline rather than centred, so class reads as height alone.
                    drawRect(
                        color = spec.color,
                        topLeft = Offset(
                            x = (tick.system - 0.5f) * stepPx - thickness / 2f,
                            y = size.height - height,
                        ),
                        size = Size(width = thickness, height = height),
                    )
                }
            }
            // The window the lens is showing, over the strip, so the two read as one instrument
            // rather than as a picture with a row of buttons under it.
            Box(
                modifier = Modifier
                    .offset(x = step * (lens.firstSystem - 1))
                    .width(step * lens.cells.size)
                    .height(STRIP_HEIGHT)
                    .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(3.dp)),
            )
        }
    }
}

// Hours of flight, not coordinates. Absolutely positioned rather than laid out in a Row, because a
// mark belongs to a *system* and systems are evenly spaced on the strip above rather than in a list.
@Composable
private fun Ruler(marks: List<ReachMarkUiState>) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(RULER_HEIGHT)) {
        val step = maxWidth / GalaxyBalance.SYSTEMS_PER_GALAXY
        marks.forEach { mark ->
            Text(
                text = mark.label,
                color = OltreColors.textTertiary,
                fontFamily = oltreMono(),
                fontSize = 9.5.sp,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .offset(x = step * (mark.system - 0.5f) - RULER_LABEL_WIDTH / 2)
                    .width(RULER_LABEL_WIDTH),
            )
        }
    }
}

// Seven cells at a phone and five in a Slide Over — the cell size holds and the count drops, so a
// target is never under 44dp. A cell is one system: its number, and a dot sized by how many worlds
// it holds and lit by its class. The lit one takes the border an active card takes.
@Composable
private fun Lens(lens: ReachLensUiState, onSelectSystem: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
        lens.cells.forEach { cell ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(CELL_HEIGHT)
                    .border(
                        1.dp,
                        if (cell.selected) {
                            OltreColors.accent.copy(alpha = 0.45f)
                        } else {
                            Color.White.copy(alpha = 0.09f)
                        },
                        RoundedCornerShape(6.dp),
                    )
                    .background(
                        if (cell.selected) OltreColors.accent.copy(alpha = 0.10f) else Color.Transparent,
                        RoundedCornerShape(6.dp),
                    )
                    .clickable { onSelectSystem(cell.system) }
                    .testTag(GalaxyTestTags.reachCell(cell.system)),
            ) {
                Box(modifier = Modifier.height(DOT_ROW_HEIGHT), contentAlignment = Alignment.Center) {
                    Dot(dot = cell.dot)
                }
                Text(
                    text = cell.label,
                    color = if (cell.selected) OltreColors.text else OltreColors.textSecondary,
                    fontFamily = oltreMono(),
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun Dot(dot: ReachDotUiState) {
    when (dot) {
        ReachDotUiState.Home -> Box(modifier = Modifier.size(8.dp).background(OltreColors.accent, CircleShape))
        ReachDotUiState.Empty -> Box(
            modifier = Modifier.size(2.dp).background(Color.White.copy(alpha = 0.18f), CircleShape),
        )
        is ReachDotUiState.Worlds -> Box(
            modifier = Modifier
                .size(DOT_BASE + DOT_PER_WORLD * dot.count.coerceAtMost(MAX_DRAWN_WORLDS))
                .background(Color.White.copy(alpha = dot.starClass.dotAlpha()), CircleShape),
        )
    }
}

private fun ReachTick.spec(): TickSpec = when (this) {
    // Dim is short and faint because that is what dim means — which makes the *desirable* class the
    // least visible mark on the strip. Kept: it is true, it costs nothing to learn, and inverting it
    // would mean a bright tick means "dim star", a lie the player carries forever. Design call 4.
    ReachTick.DIM -> TickSpec(1.dp, 4.dp, Color.White.copy(alpha = 0.30f))
    ReachTick.STANDARD -> TickSpec(1.dp, 7.dp, Color.White.copy(alpha = 0.42f))
    ReachTick.BRIGHT -> TickSpec(1.dp, 11.dp, Color.White.copy(alpha = 0.66f))
    ReachTick.ORIGIN -> TickSpec(2.dp, 17.dp, OltreColors.accent)
    ReachTick.FOREIGN_ORIGIN -> TickSpec(2.dp, 17.dp, Color.White.copy(alpha = 0.85f))
    ReachTick.PROBE -> TickSpec(2.dp, 14.dp, OltreColors.warn)
}

private data class TickSpec(val width: Dp, val height: Dp, val color: Color)

private fun StarClass.dotAlpha(): Float = when (this) {
    StarClass.DIM -> 0.34f
    StarClass.STANDARD -> 0.52f
    StarClass.BRIGHT -> 0.80f
}

private val STRIP_HEIGHT = 22.dp

// 22dp of ticks plus 11dp above and below: a scrub target that clears the delivery platform's 44pt
// minimum without any tick pretending to be a button.
private val STRIP_TOUCH_HEIGHT = 44.dp

// 14dp rather than the design's 12: a 9.5sp line box measures ~13dp in Compose, and a Box that
// short constrains the label's *height* rather than merely reserving space for it — the first
// recording came out with the bottom half of every "3h" sliced off.
private val RULER_HEIGHT = 14.dp

// Wide enough for "9h" in the mono face and narrow enough that two marks an hour apart do not
// collide at 320dp — the closest pair is 60 systems, which is ~43dp there.
private val RULER_LABEL_WIDTH = 22.dp

private val CELL_HEIGHT = 46.dp
private val DOT_ROW_HEIGHT = 12.dp
private val DOT_BASE = 3.dp
private val DOT_PER_WORLD = 0.6.dp
private const val MAX_DRAWN_WORLDS: Int = 9
