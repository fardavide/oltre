package dev.fardavide.oltre.client.player.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

// **The player's mark: a world, and something that has already left it.** It is the app icon's own
// idea at glyph scale — *Threshold* is the lit curve of a world with one trajectory rising past it
// into empty space — reduced to the three primitives a Canvas can carry, in the icon set's own
// 24-unit box at the icon set's own stroke.
//
// **The trajectory does not touch the limb, and that gap is the drawing.** The first cut of this
// ran the line out of the circle's edge, which is a stalk, and a circle with a stalk is a magnifier
// — the galaxy brief already has one of those. It only became visible when the mark was rendered
// rather than described, which is most of the argument for rendering things. `PlayerMarkTest` walks
// the one ray where the two could meet and fails if the gap closes.
//
// **Nothing here varies with the save.** A mark generated from the galaxy seed would assert an
// identity the save cannot back — nobody chose the seed, and there is no name, level or history
// behind it yet — and it would buy a generator, a property test and a baseline per variant for a
// slice whose level is zero. The day identity earns variation it should be drawn for it.
@Composable
internal fun PlayerMark(color: Color, modifier: Modifier = Modifier) {
    // **No `size` parameter**, unlike `WatchBell` which has two callers at two sizes. This has one,
    // at one size, and a defaulted parameter nothing overrides is untested surface bought for
    // nothing — the constant says the same thing and cannot be passed wrongly.
    Canvas(modifier = modifier.size(MARK_SIZE)) {
        drawPlayerMark(unit = size.width / MARK_VIEWBOX, dx = 0f, dy = 0f, color = color)
    }
}

// **A plain `DrawScope` function rather than a body inside the `Canvas { }` lambda**, which is what
// makes the geometry reachable from a test at all. The lambda is not `@Composable`, so the unit
// coverage pass counts it and cannot execute it — issue #99's shape, and the reason `:client:design:icon`
// pulled the bell out of its own lambda first.
internal fun DrawScope.drawPlayerMark(unit: Float, dx: Float, dy: Float, color: Color) {
    fun at(x: Float, y: Float) = Offset(x = (x + dx) * unit, y = (y + dy) * unit)

    val stroke = Stroke(width = MARK_STROKE_WIDTH * unit, cap = StrokeCap.Round)
    drawCircle(
        color = color,
        radius = WORLD_R * unit,
        center = at(WORLD_CX, WORLD_CY),
        style = stroke,
    )
    // The trajectory. Its infinite line passes through the world's centre — the two are on the same
    // diagonal — and the *segment* starts well outside the limb, which is what puts the gap where it
    // is rather than leaving it to a coordinate nobody would question.
    drawLine(
        color = color,
        start = at(TRAJECTORY_START_X, TRAJECTORY_START_Y),
        end = at(TRAJECTORY_END_X, TRAJECTORY_END_Y),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round,
    )
    // Where it got to, and the one filled shape the icon rules allow.
    drawCircle(color = color, radius = DOT_R * unit, center = at(DOT_CX, DOT_CY))
}

// The glyph's geometry in the 24-unit box it was drawn in, kept as the design's own coordinates so
// the shape can be read against the frame rather than reverse-engineered from scaled numbers.
internal const val MARK_VIEWBOX = 24f
internal const val MARK_STROKE_WIDTH = 1.6f

// 20dp: the tallest thing in a 38dp strip, and what sets the strip's height once the rail's own 9dp
// of vertical padding is matched above and below it.
internal val MARK_SIZE = 20.dp

internal const val WORLD_CX = 8.2f
internal const val WORLD_CY = 15.8f
private const val WORLD_R = 4.7f

private const val TRAJECTORY_START_X = 13.4f
private const val TRAJECTORY_START_Y = 10.6f
private const val TRAJECTORY_END_X = 18.5f
private const val TRAJECTORY_END_Y = 5.5f

internal const val DOT_CX = 19.9f
internal const val DOT_CY = 4.1f
private const val DOT_R = 1.7f
