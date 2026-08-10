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

// A bell — drawn to the icon set's measurements and deliberately not from its vocabulary: the same
// 24-unit box, the same round caps, stroke 1.7 rather than the bolt's 1.9.
//
// **It replaces a bespoke mark, and that is the point.** A beacon shipped here first — a source and
// two waves — and before it a body above a limb and a trajectory past a world; every one of them had
// to be explained before it could be read, which is the whole test failed. A control nobody can read
// is worse than a borrowed shape, and a bell is barely borrowed: it is the one mark a player already
// knows means *tell me later*, which is exactly what the square does.
//
// **The shape does not change between states — only its colour**, so there is no `lit` parameter to
// port from the beacon. What says "on" is the square around it: a 45% accent border over the same
// 12% tint an actionable card wears. Four shapes, four primitives, which is the whole glyph:
// a stroked path for the body, a line for the rim, a filled circle for the crown, an arc for the
// clapper.
@Composable
fun WatchBell(color: Color, modifier: Modifier = Modifier, size: Dp = 17.dp) {
    Canvas(modifier = modifier.size(size)) {
        // `this.size`, not `size`: the Dp parameter shadows the draw scope's own.
        val scale = this.size.width / VIEWBOX
        val stroke = Stroke(width = STROKE_WIDTH * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // The body, as one contour: left leg up, dome over the top, right leg down. One path rather
        // than three primitives, because three would each take a pair of round caps and thicken the
        // shoulders where they meet. The dome is an exact semicircle — the design wrote the arc's
        // endpoints two radii apart — so its centre is the chord's midpoint and no ellipse solve is
        // involved.
        drawPath(
            path = Path().apply {
                moveTo(BODY_LEFT_X * scale, RIM_Y * scale)
                lineTo(BODY_LEFT_X * scale, DOME_CY * scale)
                arcTo(
                    rect = Rect(
                        center = Offset(DOME_CX * scale, DOME_CY * scale),
                        radius = DOME_R * scale,
                    ),
                    // 180° is due west, which is where the leg already ended; +180° sweeps clockwise
                    // over due north and down to due east. Positive because the design's arc carries
                    // sweep-flag 1 — and the clapper below carries 0, so the two signs differ.
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 180f,
                    // The current point is already the arc's start, so the implicit line this inserts
                    // has zero length. `true` would open a new contour and break the body into three
                    // floating pieces.
                    forceMoveTo = false,
                )
                lineTo(BODY_RIGHT_X * scale, RIM_Y * scale)
            },
            color = color,
            style = stroke,
        )
        // The rim, overhanging the legs on both sides. `drawLine` takes no `DrawStyle`, so the cap
        // has to be named here — it defaults to Butt, which would cut 0.85 units off each end and
        // leave a visibly stubbier bell.
        drawLine(
            color = color,
            start = Offset(RIM_LEFT_X * scale, RIM_Y * scale),
            end = Offset(RIM_RIGHT_X * scale, RIM_Y * scale),
            strokeWidth = STROKE_WIDTH * scale,
            cap = StrokeCap.Round,
        )
        // The crown, and the one filled shape in the glyph. It overlaps the dome's stroke by 0.75
        // units on purpose — the dot fuses into a knob rather than floating above a gap — which is
        // also why the colour must stay opaque: at any alpha the overlap would read as a dark patch.
        drawCircle(
            color = color,
            radius = CROWN_R * scale,
            center = Offset(CROWN_CX * scale, CROWN_CY * scale),
        )
        // The clapper, swinging under the rim. **Negative sweep**, unlike the dome: from due west
        // *counter*-clockwise through due south to due east. Positive would tuck a second dome
        // inside the first — which still reads bell-ish at 17dp and is wrong, so the sign is worth
        // stating rather than eyeballing.
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = -180f,
            useCenter = false,
            topLeft = Offset((CLAPPER_CX - CLAPPER_R) * scale, (CLAPPER_CY - CLAPPER_R) * scale),
            // The whole circle's box, not the visible half's.
            size = Size(width = 2 * CLAPPER_R * scale, height = 2 * CLAPPER_R * scale),
            // `drawArc` fills by default, which here would be a solid half-disc under the rim.
            style = stroke,
        )
    }
}

// The glyph's geometry in the 24x24 box it was drawn in, kept as the design's own coordinates so
// the shape can be compared with the source rather than reverse-engineered from scaled numbers.
private const val VIEWBOX = 24f
private const val STROKE_WIDTH = 1.7f

private const val RIM_Y = 16.4f
private const val RIM_LEFT_X = 3.9f
private const val RIM_RIGHT_X = 20.1f

// The legs stand exactly a radius either side of the dome's centre, which is what makes the arc a
// semicircle and the joins tangent-continuous.
private const val BODY_LEFT_X = 6.2f
private const val BODY_RIGHT_X = 17.8f
private const val DOME_CX = 12f
private const val DOME_CY = 10.3f
private const val DOME_R = 5.8f

private const val CROWN_CX = 12f
private const val CROWN_CY = 3.4f
private const val CROWN_R = 1f

private const val CLAPPER_CX = 12f
private const val CLAPPER_CY = 19.2f
private const val CLAPPER_R = 2.1f
