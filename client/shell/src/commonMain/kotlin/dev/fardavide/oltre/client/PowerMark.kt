package dev.fardavide.oltre.client

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// The one bespoke glyph in the app, and the whole vocabulary of the energy work: a bolt, and its
// hue is the state — amber where a draw is being throttled, green where energy is supplied. It
// appears in exactly two places, the rail's rates and a facility card's second line, and never
// while the colony is healthy.
//
// Duplicated from :client:colony:presentation, which owns the other of those two places. The rail
// became the shell's chrome when Research landed as a second screen showing it, and features never
// depend on each other. This is the third such duplication (with the cost chip and
// oltreRoborazziOptions) and the first that is *drawn* — a path is the kind of code where a typo
// compiles — so it is the one that makes the case for a shared UI module rather than a fourth copy.
//
// Drawn as a stroked polyline rather than set as text: an emoji or an icon font resolves to a
// different typeface on macOS and Linux and breaks every screenshot baseline it appears in at
// the glyph level. A Path is the same pixels everywhere.
@Composable
internal fun PowerMark(color: Color, modifier: Modifier = Modifier, width: Dp = 8.dp, height: Dp = 11.dp) {
    Canvas(modifier = modifier.size(width = width, height = height)) {
        val scaleX = size.width / VIEWBOX_WIDTH
        val scaleY = size.height / VIEWBOX_HEIGHT
        val path = Path().apply {
            moveTo(12.5f * scaleX, 3f * scaleY)
            lineTo(6f * scaleX, 12.5f * scaleY)
            lineTo(11f * scaleX, 12.5f * scaleY)
            lineTo(7f * scaleX, 21f * scaleY)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = STROKE_WIDTH * scaleX,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

// The mark's geometry, in the 18x24 box it was drawn in. Kept as the design's own coordinates so
// the shape can be compared with the source rather than reverse-engineered from scaled numbers.
private const val VIEWBOX_WIDTH = 18f
private const val VIEWBOX_HEIGHT = 24f
private const val STROKE_WIDTH = 1.9f
