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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
        val unit = this.size.width / BELL_VIEWBOX
        drawBell(BellPlacement(unit = unit, scale = 1f, dx = 0f, dy = 0f), color = color, stroke = bellStroke(unit))
    }
}

// **Where the bell in the 24-unit box lands on the canvas** — a scale about the box's centre and a
// shift, applied to every coordinate below.
//
// It exists because there are two bells now. `WatchBellStack` is this same glyph at 0.76 with a
// second copy of it behind, and before this it was a second copy of the *code* too: forty lines of
// the same four primitives against a table of pre-multiplied constants, under a comment promising
// they matched. A promise a compiler cannot check is the kind this file is least able to keep — the
// numbers are the drawing, and two of them drifting apart would be two bells that are not the same
// bell.
//
// Identity at `scale = 1, dx = 0, dy = 0`, exactly: `12 + (v - 12)` is `v`, so the glyph above is
// drawn from the same arithmetic and its baselines did not move when this arrived.
internal class BellPlacement(private val unit: Float, private val scale: Float, private val dx: Float, private val dy: Float) {

    fun x(v: Float): Float = (CENTRE + scale * (v - CENTRE) + dx) * unit

    fun y(v: Float): Float = (CENTRE + scale * (v - CENTRE) + dy) * unit

    // A radius rather than a position, so it takes the scale and neither offset.
    fun length(v: Float): Float = scale * v * unit

    fun at(x: Float, y: Float): Offset = Offset(x(x), y(y))
}

// **The stroke does not scale with the bell**, which is why it is built from the unit alone. A
// smaller bell drawn in a thinner line would read as a lighter mark rather than as a nearer one, and
// the two states of the square would then differ in weight as well as in count.
internal fun bellStroke(unit: Float): Stroke =
    Stroke(width = BELL_STROKE_WIDTH * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)

// The whole bell, four primitives, wherever `place` puts it.
//
// **A plain `DrawScope` function rather than a body inside the composable**, which is what lets the
// bell behind be the same bell rather than a copy of it.
internal fun DrawScope.drawBell(place: BellPlacement, color: Color, stroke: Stroke) {
    // The body, as one contour: left leg up, dome over the top, right leg down. One path rather
    // than three primitives, because three would each take a pair of round caps and thicken the
    // shoulders where they meet. The dome is an exact semicircle — the design wrote the arc's
    // endpoints two radii apart — so its centre is the chord's midpoint and no ellipse solve is
    // involved.
    drawPath(
        path = Path().apply {
            moveTo(place.x(BODY_LEFT_X), place.y(RIM_Y))
            lineTo(place.x(BODY_LEFT_X), place.y(DOME_CY))
            arcTo(
                rect = Rect(center = place.at(DOME_CX, DOME_CY), radius = place.length(DOME_R)),
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
            lineTo(place.x(BODY_RIGHT_X), place.y(RIM_Y))
        },
        color = color,
        style = stroke,
    )
    drawRim(place = place, leftX = RIM_LEFT_X, color = color, stroke = stroke)
    // The crown, and the one filled shape in the glyph. It overlaps the dome's stroke by 0.75
    // units on purpose — the dot fuses into a knob rather than floating above a gap — which is
    // also why the colour must stay opaque: at any alpha the overlap would read as a dark patch.
    drawCircle(
        color = color,
        radius = place.length(CROWN_R),
        center = place.at(CROWN_CX, CROWN_CY),
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
        topLeft = place.at(CLAPPER_CX - CLAPPER_R, CLAPPER_CY - CLAPPER_R),
        // The whole circle's box, not the visible half's.
        size = Size(width = 2 * place.length(CLAPPER_R), height = 2 * place.length(CLAPPER_R)),
        // `drawArc` fills by default, which here would be a solid half-disc under the rim.
        style = stroke,
    )
}

// The rim, overhanging the legs on both sides. `drawLine` takes no `DrawStyle`, so the cap has to be
// named here — it defaults to Butt, which would cut 0.85 units off each end and leave a visibly
// stubbier bell.
//
// **`leftX` is a parameter for the bell behind**, whose rim is picked up to the right of the front
// bell rather than at its own left end. The right end never moves: nothing is ever in front of that.
internal fun DrawScope.drawRim(place: BellPlacement, leftX: Float, color: Color, stroke: Stroke) {
    drawLine(
        color = color,
        start = place.at(leftX, RIM_Y),
        end = place.at(RIM_RIGHT_X, RIM_Y),
        // `stroke.width`, so the rim cannot end up a different weight from the body it hangs under.
        strokeWidth = stroke.width,
        cap = StrokeCap.Round,
    )
}

// The glyph's geometry in the 24x24 box it was drawn in, kept as the design's own coordinates so
// the shape can be compared with the source rather than reverse-engineered from scaled numbers.
internal const val BELL_VIEWBOX = 24f
internal const val BELL_STROKE_WIDTH = 1.7f

// Both the box's centre and the point every placement scales about, which is why it is one constant
// rather than `BELL_VIEWBOX / 2` written twice.
private const val CENTRE = 12f

internal const val RIM_Y = 16.4f
private const val RIM_LEFT_X = 3.9f
internal const val RIM_RIGHT_X = 20.1f

// The legs stand exactly a radius either side of the dome's centre, which is what makes the arc a
// semicircle and the joins tangent-continuous.
private const val BODY_LEFT_X = 6.2f
internal const val BODY_RIGHT_X = 17.8f
internal const val DOME_CX = 12f
internal const val DOME_CY = 10.3f
internal const val DOME_R = 5.8f

private const val CROWN_CX = 12f
private const val CROWN_CY = 3.4f
private const val CROWN_R = 1f

private const val CLAPPER_CX = 12f
private const val CLAPPER_CY = 19.2f
private const val CLAPPER_R = 2.1f
