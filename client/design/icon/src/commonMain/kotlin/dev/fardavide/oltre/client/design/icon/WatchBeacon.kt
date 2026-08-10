package dev.fardavide.oltre.client.design.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// A beacon: a source, and two waves leaving it. The second glyph in the set, drawn in the same
// 24-unit box and at the same weight as the destination glyphs on the tab bar, so a mark that
// appears inside a card sits at the same density as the ones that appear under it.
//
// Both arcs are centred on the source rather than merely near it — radius 6 and radius 10 about
// (8, 16), each a quarter turn from due east to due north. That is what makes them read as one
// thing radiating rather than as two curves stacked, and it is why this is three primitives and no
// path: two `drawArc` and one `drawCircle`.
//
// `lit` is the whole of the state. Unlit drops the dot and the source is silent — the waves are
// still drawn, so the shape is legible as the same object in both states and the eye is not asked
// to learn two glyphs. The colour is a parameter for the reason the bolt's is: the icon set knows
// the palette's shape and none of its values.
@Composable
fun WatchBeacon(color: Color, lit: Boolean, modifier: Modifier = Modifier, size: Dp = 17.dp) {
    Canvas(modifier = modifier.size(size)) {
        val scale = this.size.width / VIEWBOX
        val stroke = Stroke(width = STROKE_WIDTH * scale, cap = StrokeCap.Round)
        for (radius in listOf(INNER_RADIUS, OUTER_RADIUS)) {
            drawArc(
                color = color,
                // Due north, a quarter turn clockwise to due east: the arc the design draws from
                // east to north with a counter-clockwise sweep, stated in the direction Compose
                // measures in so the sign does not have to be read twice.
                startAngle = -90f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(
                    x = (SOURCE_X - radius) * scale,
                    y = (SOURCE_Y - radius) * scale,
                ),
                size = Size(width = 2 * radius * scale, height = 2 * radius * scale),
                style = stroke,
            )
        }
        if (lit) {
            drawCircle(
                color = color,
                radius = DOT_RADIUS * scale,
                center = Offset(x = SOURCE_X * scale, y = SOURCE_Y * scale),
            )
        }
    }
}

// The glyph's geometry in the 24x24 box it was drawn in, kept as the design's own coordinates so
// the shape can be compared with the source rather than reverse-engineered from scaled numbers.
private const val VIEWBOX = 24f
private const val SOURCE_X = 8f
private const val SOURCE_Y = 16f
private const val INNER_RADIUS = 6f
private const val OUTER_RADIUS = 10f
private const val DOT_RADIUS = 1.7f
private const val STROKE_WIDTH = 1.8f
