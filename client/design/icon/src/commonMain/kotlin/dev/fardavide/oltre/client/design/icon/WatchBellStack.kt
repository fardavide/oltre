package dev.fardavide.oltre.client.design.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
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
// **It is literally the same bell, twice** — `drawBell` against two `BellPlacement`s, rather than a
// second table of pre-multiplied numbers under a comment promising they match. The front one is
// `WatchBell` at 0.76, shifted down and left, which is what pays for the second: the box is full at
// full size, and a mark that overflowed it would sit differently in the 29dp square from the bell it
// alternates with.
//
// **The bell behind is drawn only where the front one does not cover it**: its shoulder, its right
// leg, and a stub of its rim. There is no knockout anywhere in this app, so the occlusion is done by
// *stopping the stroke* — the arc ends in air about a unit short of the front bell's crown and its
// shoulder, which at 17dp is roughly a pixel of daylight and is what makes one shape read as being
// in front of the other rather than beside it. It carries no crown and no clapper: they would be the
// two smallest marks in the glyph and both would land on top of the front bell.
@Composable
fun WatchBellStack(color: Color, modifier: Modifier = Modifier, size: Dp = 17.dp) {
    Canvas(modifier = modifier.size(size)) {
        // `this.size`, not `size`: the Dp parameter shadows the draw scope's own.
        drawStackedBells(unit = this.size.width / BELL_VIEWBOX, dx = 0f, dy = 0f, color = color)
    }
}

// **A plain `DrawScope` function with the composable reduced to a call to it**, exactly as `drawBell`
// is — and here it buys the same two things. The two placements are one expression rather than two
// tables that could drift, and `BellTest` can render the real glyph rather than an approximation of
// it: a test that reassembled these two calls itself would be asserting its own arithmetic.
//
// `dx` and `dy` are what let a test place the whole mark away from the edges of its bitmap, so ink
// that leaves the box lands on canvas and can be measured instead of being silently clipped.
internal fun DrawScope.drawStackedBells(unit: Float, dx: Float, dy: Float, color: Color) {
    val stroke = bellStroke(unit)
    drawBellBehind(
        place = BellPlacement(unit = unit, scale = SCALE, dx = dx + FRONT_DX + BACK_DX, dy = dy + FRONT_DY + BACK_DY),
        color = color,
        stroke = stroke,
    )
    // The front bell, whole, over the top of it.
    drawBell(
        place = BellPlacement(unit = unit, scale = SCALE, dx = dx + FRONT_DX, dy = dy + FRONT_DY),
        color = color,
        stroke = stroke,
    )
}

// What is left of a bell with another one in front of it: a shoulder, a right leg, and a stub of rim.
private fun DrawScope.drawBellBehind(place: BellPlacement, color: Color, stroke: Stroke) {
    // Shoulder then right leg, as one contour. **`forceMoveTo = true` here where `drawBell`'s dome
    // uses false** — there is no current point to continue from, and `false` would draw a line to the
    // arc's start from wherever the path happened to begin.
    //
    // The arc ends due east, which is exactly where this bell's right leg stands, so the join is
    // tangent-continuous and needs no correction.
    drawPath(
        path = Path().apply {
            arcTo(
                rect = Rect(center = place.at(DOME_CX, DOME_CY), radius = place.length(DOME_R)),
                startAngleDegrees = BACK_ARC_START,
                sweepAngleDegrees = BACK_ARC_SWEEP,
                forceMoveTo = true,
            )
            lineTo(place.x(BODY_RIGHT_X), place.y(RIM_Y))
        },
        color = color,
        style = stroke,
    )
    // Its rim, picked up to the right of the front bell's leg rather than at its own left end.
    drawRim(place = place, leftX = BACK_RIM_LEFT_X, color = color, stroke = stroke)
}

// Both bells, because they are the same bell: 0.76 is what makes room for the second without either
// of them leaving the box.
private const val SCALE = 0.76f

// Down and left, to clear the top-right corner for the bell behind.
private const val FRONT_DX = -2.1f
private const val FRONT_DY = 1.9f

// And the bell behind, up and right of the front one — far enough that its leg and rim clear the
// front bell's outline outright and half its dome shows, close enough that the two still cross and
// it reads as the same object repeated rather than as a second mark beside it. Push it further and
// the pair separates into two icons; pull it in and the bell behind stops being a bell.
private const val BACK_DX = 5.4f
private const val BACK_DY = -4.4f

// **Where the stroke stops, which is the occlusion — and the number this glyph is most sensitive
// to.** The two domes actually cross at about 188°, so that is where the bell behind genuinely
// disappears; the arc stops at 220° instead, which buys about a unit of daylight from the front
// bell's crown and its shoulder both. Below about 215° the gap closes to nothing and the two shapes
// fuse into one blot at 17dp; much above 220° and what is left stops being a dome at all.
//
// **140° of it, and the first draft drew 125° over a smaller offset** — which was the whole defect.
// At that length the visible fragment was an arc, a stub of leg and a stub of rim, and it read as a
// hook rather than as a bell. Half a dome plus a full leg plus a rim that overhangs on both sides is
// the least that reads as the same object seen twice. `watch_square.png` is where that was found.
private const val BACK_ARC_START = 220f
private const val BACK_ARC_SWEEP = 140f

// In the bell's own coordinates, like everything else here: 1.6 units right of its centre, which
// once placed leaves about 1.4 units of daylight from the front bell's right leg. It still overhangs
// its own leg on both sides — 3.2 units to the left of it and 1.8 to the right — so it reads as a
// rim under a bell rather than as the foot of a bracket.
private const val BACK_RIM_LEFT_X = 13.6f
