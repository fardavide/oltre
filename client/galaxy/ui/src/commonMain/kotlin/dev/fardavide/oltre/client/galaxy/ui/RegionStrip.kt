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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextOverflow
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
//
// ── What 0.11 changed, and what each change bought ───────────────────────────────────────────────
//
// - **Nine hairline breaks.** The bias was always drawn — 25 short dark ticks in a row *is* a Deep —
//   but texture with no edges is not ten places. Nine and not ten: the galaxy's outer edges are the
//   ends of the strip, and a line there would say something the strip already ends by saying.
// - **A pinned system is a mark of its own.** Shorter than a probe and taller than any star, so the
//   three things that are *yours* — your star, your probe, anywhere you said mattered — are findable
//   in a field of 250 without a legend.
// - **Five named cells rather than seven numbered ones.** 65dp a cell fits a nine-character name at
//   the 9.5 floor and 46dp did not, so the cell grew to 56dp to hold a name row and the picker
//   stopped being a row of coordinates and started being a row of places.
//
// Ten region names *on the strip* were drawn, measured and rejected: a region is 33dp of strip at
// 393dp and its name is 68–90dp of type at the same floor, so ten of them overlap by a factor of two
// and a half before one is legible. The system header names the region you are in instead.
@Composable
internal fun RegionStrip(
    uiState: RegionStripUiState,
    compact: Boolean,
    onSelectSystem: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Which lens a narrow window gets is `presentation`'s call and not the design's — the frames
    // settle five as the phone count and say nothing about 320dp, where five cells leave 53dp each
    // and the name row is what the extra dp were bought for.
    val lens = if (compact) uiState.compactLens else uiState.lens
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Strip(ticks = uiState.ticks, lens = lens, onSelectSystem = onSelectSystem)
        Ruler(marks = uiState.marks)
        Picker(lens = lens, onSelectSystem = onSelectSystem)
    }
}

// 250 ticks, nine breaks and a baseline in one `Canvas`, drawn once per selection change — no
// animation and no state that has to survive a foreground. Same argument that made the system map a
// Canvas: this is geometry, not 250 composables.
//
// **The Canvas is the whole 44dp, not the 22dp of ink**, and that is what the breaks forced. A break
// reaches 5dp above the tallest tick and 4dp below the line the ticks stand on, so a surface sized to
// the ink has nowhere to put either end — and the baseline sits one dp *under* the ticks rather than
// inside their last dp, which the ink-sized version could not express either. Every y here is
// therefore measured from the top of the touch target.
//
// **The 22dp of ticks claims 44dp of touch**, and it is a scrub target rather than 250 small ones:
// no tick is individually tappable at either width, and the design does not pretend otherwise.
@Composable
private fun Strip(ticks: List<ReachTickUiState>, lens: ReachLensUiState, onSelectSystem: (Int) -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(STRIP_HEIGHT)
            .testTag(GalaxyTestTags.REACH_STRIP),
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
                .fillMaxSize()
                .pointerInput(widthPx) {
                    detectHorizontalDragGestures { change, _ -> onSelectSystem(systemAt(change.position.x)) }
                }
                .pointerInput(widthPx) {
                    // A tap is a drag of length zero and should mean the same thing on a scrub
                    // target. Without this, touching the strip without moving does nothing at all.
                    detectTapGestures { offset -> onSelectSystem(systemAt(offset.x)) }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stepPx = size.width / GalaxyBalance.SYSTEMS_PER_GALAXY
                val hairline = HAIRLINE.toPx()
                val ground = STRIP_GROUND.toPx()
                // The line the ticks stand on. Without it the strip is a row of marks; with it, it
                // is an axis — which is what the ruler underneath is measuring along.
                drawRect(
                    color = Color.White.copy(alpha = 0.09f),
                    topLeft = Offset(x = 0f, y = ground),
                    size = Size(width = size.width, height = hairline),
                )
                // **Under the ticks, deliberately.** Painted last, the nine of them turn a field of
                // texture into a grid and the eye starts reading the cells instead of the runs; under
                // the ticks they are a division of something continuous, which is what a region is.
                //
                // Left edge on the boundary rather than centred on it, so a break belongs to the
                // first system of the region it opens rather than straddling two.
                for (region in 1 until GalaxyBalance.REGIONS_PER_GALAXY) {
                    drawRect(
                        color = Color.White.copy(alpha = 0.16f),
                        topLeft = Offset(
                            x = stepPx * region * GalaxyBalance.SYSTEMS_PER_REGION,
                            y = BREAK_TOP.toPx(),
                        ),
                        size = Size(width = hairline, height = BREAK_HEIGHT.toPx()),
                    )
                }
                ticks.forEach { tick ->
                    val spec = tick.mark.spec()
                    val thickness = spec.width.toPx()
                    val height = spec.height.toPx()
                    // Grounded on the baseline rather than centred, so class reads as height alone.
                    drawRect(
                        color = spec.color,
                        topLeft = Offset(
                            x = (tick.system - 0.5f) * stepPx - thickness / 2f,
                            y = ground - height,
                        ),
                        size = Size(width = thickness, height = height),
                    )
                }
            }
            // The window the lens is showing, over the strip, so the two read as one instrument
            // rather than as a picture with a row of buttons under it.
            //
            // **A break out-ranges it by a dp at each end** — 6…38 against the frame's 7…37 — so the
            // lens can never sit on a boundary and swallow it, which is the one place the two marks
            // compete for the same pixels.
            Box(
                modifier = Modifier
                    .offset(x = step * (lens.firstSystem - 1), y = LENS_TOP)
                    .width(step * lens.cells.size)
                    .height(LENS_HEIGHT)
                    .border(HAIRLINE, Color.White.copy(alpha = 0.30f), RoundedCornerShape(3.dp)),
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
                // The caption track, which every other 9.5sp reading in the app carries and this one
                // had been missing: an hour mark is a legend on an axis, and a legend that tracks
                // tighter than the captions beside it reads as a different size rather than as the
                // same one.
                letterSpacing = 1.sp,
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

// A cell is one system: its name, its coordinate, and a dot sized by how many worlds it holds and lit
// by its class. The lit one takes the border an active card takes.
//
// **Selection is said once, by the name.** The number is the faintest ink in the component in every
// state — it is an address, and an address does not become truer for being chosen. Said twice, the
// cell reads as two changes and the eye stops trusting either.
@Composable
private fun Picker(lens: ReachLensUiState, onSelectSystem: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
        lens.cells.forEach { cell ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                modifier = Modifier
                    .weight(1f)
                    .height(CELL_HEIGHT)
                    .border(
                        HAIRLINE,
                        if (cell.selected) {
                            OltreColors.accent.copy(alpha = 0.45f)
                        } else {
                            Color.White.copy(alpha = 0.09f)
                        },
                        RoundedCornerShape(CELL_RADIUS),
                    )
                    .background(
                        if (cell.selected) OltreColors.accent.copy(alpha = 0.10f) else Color.Transparent,
                        RoundedCornerShape(CELL_RADIUS),
                    )
                    .clickable { onSelectSystem(cell.system) }
                    .testTag(GalaxyTestTags.reachCell(cell.system))
                    .padding(horizontal = 2.dp),
            ) {
                Box(modifier = Modifier.height(DOT_ROW_HEIGHT), contentAlignment = Alignment.Center) {
                    Dot(dot = cell.dot)
                }
                // **One line, ellipsised, never wrapped and never shrunk.** Two names in a row are
                // two heights, and a name set a point smaller than its neighbour is a name the eye
                // reads as less important — where a name cut short is still the same row of places.
                Text(
                    text = cell.name,
                    color = if (cell.selected) OltreColors.text else OltreColors.textSecondary,
                    fontFamily = oltreMono(),
                    fontSize = 9.5.sp,
                    lineHeight = 12.35.sp,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = cell.label,
                    color = OltreColors.textTertiary,
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
        // The one dot drawn in pure white rather than in the text ink: it is the absence of a
        // reading rather than a faint one, and the smallest mark the component has.
        ReachDotUiState.Empty -> Box(
            modifier = Modifier.size(2.dp).background(Color.White.copy(alpha = 0.18f), CircleShape),
        )
        is ReachDotUiState.Worlds -> Box(
            modifier = Modifier
                .size(DOT_BASE + DOT_PER_WORLD * dot.count.coerceAtMost(MAX_DRAWN_WORLDS))
                .background(OltreColors.text.copy(alpha = dot.starClass.dotAlpha()), CircleShape),
        )
    }
}

// **The precedence is already spent by the time a tick arrives here.** The design states it as a
// chain — your star beats a probe beats a pin beats the star class — but a system carries exactly one
// mark, so what resolved it lives in `presentation` and this is a total `when` over what survived.
// Re-deriving the chain in the draw would be the same fact decided in two places.
private fun ReachTick.spec(): TickSpec = when (this) {
    // Dim is short and faint because that is what dim means — which makes the *desirable* class the
    // least visible mark on the strip. Kept: it is true, it costs nothing to learn, and inverting it
    // would mean a bright tick means "dim star", a lie the player carries forever. Design call 4.
    ReachTick.DIM -> TickSpec(1.dp, 4.dp, OltreColors.text.copy(alpha = 0.30f))
    ReachTick.STANDARD -> TickSpec(1.dp, 7.dp, OltreColors.text.copy(alpha = 0.42f))
    ReachTick.BRIGHT -> TickSpec(1.dp, 11.dp, OltreColors.text.copy(alpha = 0.66f))
    ReachTick.ORIGIN -> TickSpec(2.dp, 17.dp, OltreColors.accent)
    ReachTick.FOREIGN_ORIGIN -> TickSpec(2.dp, 17.dp, OltreColors.text.copy(alpha = 0.85f))
    ReachTick.PROBE -> TickSpec(2.dp, 14.dp, OltreColors.warn)
    // Between the probe and the tallest star, which is where it belongs: a pin outranks anything the
    // sky did on its own and never outranks something of yours that is moving.
    ReachTick.PIN -> TickSpec(2.dp, 13.dp, OltreColors.text.copy(alpha = 0.90f))
}

private data class TickSpec(val width: Dp, val height: Dp, val color: Color)

private fun StarClass.dotAlpha(): Float = when (this) {
    StarClass.DIM -> 0.34f
    StarClass.STANDARD -> 0.52f
    StarClass.BRIGHT -> 0.80f
}

// Every line in the component is exactly one dp — the baseline, a region break, the lens frame and a
// cell's border.
private val HAIRLINE = 1.dp

// 22dp of ticks plus 11dp above and below: a scrub target that clears the delivery platform's 44pt
// minimum without any tick pretending to be a button.
private val STRIP_HEIGHT = 44.dp

// Where the ticks stand, measured from the top of that 44dp — 11dp of padding plus the 22dp of ink.
private val STRIP_GROUND = 33.dp

// 6 and 32 rather than 11 and 22: a break clears the tallest tick by 5dp and drops 4dp past the
// baseline, which is what makes it read as a division of the strip rather than as one more tick.
private val BREAK_TOP = 6.dp
private val BREAK_HEIGHT = 32.dp

// One dp inside the break at each end, so the frame and the boundary never contend for a pixel.
private val LENS_TOP = 7.dp
private val LENS_HEIGHT = 30.dp

// 14dp rather than the design's 12: a 9.5sp line box measures ~13dp in Compose, and a Box that
// short constrains the label's *height* rather than merely reserving space for it — the first
// recording came out with the bottom half of every "3h" sliced off.
private val RULER_HEIGHT = 14.dp

// Wide enough for "9h" in the mono face and narrow enough that two marks an hour apart do not
// collide at 320dp — the closest pair is 60 systems, which is ~43dp there.
private val RULER_LABEL_WIDTH = 22.dp

// 46dp held a dot and a number. 56dp holds a dot, a name and a number, and its content still only
// fills about 38 of them — the slack is what keeps a nine-character name from touching a border.
private val CELL_HEIGHT = 56.dp

// The tab radius rather than the card's: five of these in a row is a segmented control, and it takes
// the corner every other segmented control in the app takes.
private val CELL_RADIUS = 10.dp

private val DOT_ROW_HEIGHT = 12.dp
private val DOT_BASE = 3.dp
private val DOT_PER_WORLD = 0.6.dp
private const val MAX_DRAWN_WORLDS: Int = 9
