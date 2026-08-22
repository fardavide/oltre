package dev.fardavide.oltre.client.design.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Two bells, one behind the other — the second state of the Shipyard's control, where the first is
// [WatchBell] unchanged.
//
// **The mark is borrowed twice over, and that is the whole argument for it.** `WatchBell`'s own file
// records why it exists: three bespoke marks were drawn for that control and every one had to be
// explained before it could be read, so it settled on the one shape a player already knows means
// *tell me later*. This is that shape plus the one arrangement a player already knows means *more
// than one of these* — the overlap every icon set spends on duplicate, copy and grouped
// notifications. Nothing here is invented; what is new is that there are two of something.
//
// **The front bell is `WatchBell` at 0.78, shifted down and left**, which is what pays for the
// second one: the box is full at full size, and a mark that overflowed it would sit differently in
// the 29dp square from the bell it alternates with. Same 24-unit box, same 1.7 stroke, same round
// caps — the stroke deliberately does *not* scale, or the two states would differ in weight as well
// as in count and the pair would stop reading as one control.
//
// **The bell behind is drawn only where the front one does not cover it**: its shoulder, its right
// leg, and a stub of its rim. There is no knockout anywhere in this app, so the occlusion is done by
// *stopping the stroke* — the back arc ends in air about 1.5 units short of the front bell's crown,
// which at 17dp is roughly a pixel of daylight and is what makes one shape read as being in front of
// the other rather than beside it. It carries no crown and no clapper: they would be the two
// smallest marks in the glyph and both would land on top of the front bell.
@Composable
fun WatchBellStack(color: Color, modifier: Modifier = Modifier, size: Dp = 17.dp) {
    Canvas(modifier = modifier.size(size)) {
        // `this.size`, not `size`: the Dp parameter shadows the draw scope's own.
        val scale = this.size.width / VIEWBOX
        val stroke = Stroke(width = STROKE_WIDTH * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // The bell behind, as one contour: shoulder then right leg. **`forceMoveTo = true` here where
        // `WatchBell`'s dome uses false** — there is no current point to continue from, and `false`
        // would draw a line to the arc's start from wherever the path happened to begin.
        //
        // 235° to 360° in the draw scope's own convention, where 0° is due east and the sweep runs
        // clockwise on screen: it starts up and to the left of this bell's centre, passes over due
        // north at 270°, and ends due east — which is exactly where the right leg stands, so the
        // join is tangent-continuous and needs no correction.
        drawPath(
            path = Path().apply {
                arcTo(
                    rect = Rect(
                        center = Offset(BACK_DOME_CX * scale, BACK_DOME_CY * scale),
                        radius = BACK_DOME_R * scale,
                    ),
                    startAngleDegrees = BACK_ARC_START,
                    sweepAngleDegrees = BACK_ARC_SWEEP,
                    forceMoveTo = true,
                )
                lineTo(BACK_BODY_RIGHT_X * scale, BACK_RIM_Y * scale)
            },
            color = color,
            style = stroke,
        )
        // The stub of the bell behind, picking up to the right of the front bell's leg. It overhangs
        // its own leg on both sides, exactly as the front rim overhangs its two — a rim that stopped
        // at the leg would read as a corner rather than as the same bell seen again.
        drawLine(
            color = color,
            start = Offset(BACK_RIM_LEFT_X * scale, BACK_RIM_Y * scale),
            end = Offset(BACK_RIM_RIGHT_X * scale, BACK_RIM_Y * scale),
            strokeWidth = STROKE_WIDTH * scale,
            cap = StrokeCap.Round,
        )

        // Everything below is `WatchBell` at 0.78, primitive for primitive and in the same order. The
        // notes there are the notes here: one contour for the body so the shoulders do not thicken, a
        // rim that must name its cap or lose 0.85 units off each end, a crown that overlaps the dome
        // so it fuses into a knob, and a clapper whose sweep is negative where the dome's is positive.
        drawPath(
            path = Path().apply {
                moveTo(FRONT_BODY_LEFT_X * scale, FRONT_RIM_Y * scale)
                lineTo(FRONT_BODY_LEFT_X * scale, FRONT_DOME_CY * scale)
                arcTo(
                    rect = Rect(
                        center = Offset(FRONT_DOME_CX * scale, FRONT_DOME_CY * scale),
                        radius = FRONT_DOME_R * scale,
                    ),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false,
                )
                lineTo(FRONT_BODY_RIGHT_X * scale, FRONT_RIM_Y * scale)
            },
            color = color,
            style = stroke,
        )
        drawLine(
            color = color,
            start = Offset(FRONT_RIM_LEFT_X * scale, FRONT_RIM_Y * scale),
            end = Offset(FRONT_RIM_RIGHT_X * scale, FRONT_RIM_Y * scale),
            strokeWidth = STROKE_WIDTH * scale,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = color,
            radius = FRONT_CROWN_R * scale,
            center = Offset(FRONT_CROWN_CX * scale, FRONT_CROWN_CY * scale),
        )
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = -180f,
            useCenter = false,
            topLeft = Offset(
                (FRONT_CLAPPER_CX - FRONT_CLAPPER_R) * scale,
                (FRONT_CLAPPER_CY - FRONT_CLAPPER_R) * scale,
            ),
            size = Size(width = 2 * FRONT_CLAPPER_R * scale, height = 2 * FRONT_CLAPPER_R * scale),
            style = stroke,
        )
    }
}

// The same 24x24 box and the same stroke `WatchBell` is drawn in, so the two states of the square are
// one glyph seen twice rather than two drawings.
private const val VIEWBOX = 24f
private const val STROKE_WIDTH = 1.7f

// `WatchBell`'s geometry at 0.76 about the centre of the box, then shifted 2.1 left and 1.9 down.
// Written out rather than computed from the original's constants, so this glyph can be compared with
// what it draws instead of with the arithmetic that produced it — which is `WatchBell`'s own rule
// about keeping the design's coordinates.
private const val FRONT_RIM_Y = 17.2f
private const val FRONT_RIM_LEFT_X = 3.7f
private const val FRONT_RIM_RIGHT_X = 16.1f

private const val FRONT_BODY_LEFT_X = 5.5f
private const val FRONT_BODY_RIGHT_X = 14.3f
private const val FRONT_DOME_CX = 9.9f
private const val FRONT_DOME_CY = 12.6f
private const val FRONT_DOME_R = 4.4f

private const val FRONT_CROWN_CX = 9.9f
private const val FRONT_CROWN_CY = 7.4f
private const val FRONT_CROWN_R = 0.9f

private const val FRONT_CLAPPER_CX = 9.9f
private const val FRONT_CLAPPER_CY = 19.4f
private const val FRONT_CLAPPER_R = 1.6f

// The same bell again, 5.4 right and 4.4 up of the front one — far enough that its leg and rim clear
// the front bell's outline outright and half its dome shows, close enough that the two still cross
// and it reads as the same object repeated rather than as a second mark beside it. Push it much
// further and the pair separates into two icons; pull it in and the bell behind stops being a bell.
private const val BACK_DOME_CX = 15.3f
private const val BACK_DOME_CY = 8.2f
private const val BACK_DOME_R = 4.4f

// **Where the stroke stops, which is the occlusion — and the number this glyph is most sensitive to.**
// The two domes actually cross at 188°, so that is where the bell behind genuinely disappears; the
// arc stops at 220° instead, which buys about a unit of daylight from the front bell's crown and its
// shoulder both. Below about 215° the gap closes to nothing and the two shapes fuse into one blot at
// 17dp; much above 220° and what is left stops being a dome at all.
//
// **140° of it, and the first draft drew 125° over a smaller offset** — which was the whole defect.
// At that length the visible fragment was an arc, a stub of leg and a stub of rim, and it read as a
// hook rather than as a bell. Half a dome plus a full leg plus a rim that overhangs on both sides is
// the least that reads as the same object seen twice.
private const val BACK_ARC_START = 220f
private const val BACK_ARC_SWEEP = 140f

private const val BACK_BODY_RIGHT_X = 19.7f
private const val BACK_RIM_Y = 12.8f

// Picks up about 1.4 units clear of the front bell's right leg, for the reason the arc stops where it
// does — and runs 3.2 units past its own leg on the left and 1.8 past it on the right, so it reads as
// a rim overhanging a bell rather than as the foot of a bracket.
private const val BACK_RIM_LEFT_X = 16.5f
private const val BACK_RIM_RIGHT_X = 21.5f
