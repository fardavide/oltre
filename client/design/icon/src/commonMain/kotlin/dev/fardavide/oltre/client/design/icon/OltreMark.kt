package dev.fardavide.oltre.client.design.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.sqrt

// **The game's own mark, and the only place in the app that draws it large.** Three primitives in the
// icon set's 24-unit box: a world's limb, a trajectory leaving it, and the star the trajectory is
// aimed at. That is the name — *oltre*, beyond — as a picture, and it is why the trajectory and the
// star share the accent while the limb does not: the thing you leave is grey and the thing you are
// going to is blue.
//
// **Two colours as parameters rather than a palette import**, which is `WatchBell`'s rule: this module
// draws shapes and names no colour, so a caller that wanted the mark in one ink could ask for it.
@Composable
fun OltreMark(limb: Color, trajectory: Color, modifier: Modifier = Modifier, size: Dp = 88.dp) {
    Canvas(modifier = modifier.size(size)) {
        val unit = this.size.width / MARK_VIEWBOX
        val stroke = Stroke(width = MARK_STROKE_WIDTH * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // **The limb, derived rather than transcribed.** The design gives it as two endpoints and a
        // radius; a centre and two angles are what Compose takes, and computing them here means the
        // numbers in this file are still the design's own — which is the only way the shape can be
        // compared with the source instead of reverse-engineered from scaled constants.
        //
        // The centre sits *below* the chord, so the minor arc bulges upward: this is the top of a
        // world seen from just above it, not a bowl.
        val halfChord = (LIMB_RIGHT_X - LIMB_LEFT_X) / 2
        val centreY = LIMB_Y + sqrt(LIMB_R * LIMB_R - halfChord * halfChord)
        val half = asin(halfChord / LIMB_R) * DEGREES_PER_RADIAN
        drawArc(
            color = limb,
            // 270° is straight up in Compose's screen angles, which is where the arc's apex is; the
            // half-angle either side of it is the whole of the minor arc.
            startAngle = STRAIGHT_UP - half,
            sweepAngle = 2 * half,
            useCenter = false,
            topLeft = Offset((LIMB_CX - LIMB_R) * unit, (centreY - LIMB_R) * unit),
            size = Size(2 * LIMB_R * unit, 2 * LIMB_R * unit),
            style = stroke,
        )

        // The trajectory, as the design's own cubic. It leaves *below* the limb's left end and passes
        // in front of it, which is what makes the mark read as departure rather than as a diagram.
        drawPath(
            path = Path().apply {
                moveTo(TRAJECTORY_X0 * unit, TRAJECTORY_Y0 * unit)
                cubicTo(
                    TRAJECTORY_CX1 * unit, TRAJECTORY_CY1 * unit,
                    TRAJECTORY_CX2 * unit, TRAJECTORY_CY2 * unit,
                    TRAJECTORY_X1 * unit, TRAJECTORY_Y1 * unit,
                )
            },
            color = trajectory,
            style = stroke,
        )

        // The star, and the one filled shape. It overlaps the trajectory's end cap on purpose, so the
        // line arrives *at* it rather than stopping short of it.
        drawCircle(
            color = trajectory,
            radius = STAR_R * unit,
            center = Offset(STAR_CX * unit, STAR_CY * unit),
        )
    }
}

// The design's coordinates in the 24-unit box, unscaled and unrearranged.
private const val MARK_VIEWBOX = 24f
private const val MARK_STROKE_WIDTH = 1.5f

private const val LIMB_LEFT_X = 1.4f
private const val LIMB_RIGHT_X = 22.6f
private const val LIMB_Y = 19.8f
private const val LIMB_R = 13.6f
private const val LIMB_CX = 12f

private const val TRAJECTORY_X0 = 4.6f
private const val TRAJECTORY_Y0 = 22.2f
private const val TRAJECTORY_CX1 = 8.4f
private const val TRAJECTORY_CY1 = 15.2f
private const val TRAJECTORY_CX2 = 12.4f
private const val TRAJECTORY_CY2 = 9.2f
private const val TRAJECTORY_X1 = 18.6f
private const val TRAJECTORY_Y1 = 5.2f

private const val STAR_CX = 19.2f
private const val STAR_CY = 4.8f
private const val STAR_R = 1.6f

private const val STRAIGHT_UP = 270f
private const val DEGREES_PER_RADIAN = (180 / PI).toFloat()
