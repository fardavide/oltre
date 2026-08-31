package dev.fardavide.oltre.client.player.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.fardavide.oltre.protocol.MarkPreset

// **The six drawn silhouettes, and the set is the whole of the choice.** One diagonal, one centred
// disc, one horizontal, one corner, one nest of arcs, one vertical — six shapes a player can tell
// apart at 20dp rather than six versions of one idea, which is the frame's own reason for six and not
// twenty-four. Three primitives at most each, one stroke weight, and no fill anywhere but the
// terminus dot.
//
// **Colour is not a second axis and there is no palette import here.** Every hue in this app already
// means something about affordability or state, so a player-picked amber mark would sit forty dp from
// an amber fleet strip meaning something else entirely. The ink is always the caller's.
//
// Five drawings rather than six, because `THRESHOLD` is the one preset the composer can make and is
// drawn out of its three parts — see `drawPlayerMark`.

// **The one entry point that can go stale, and the `when` is what stops it.** No `else`: a seventh
// preset added to the wire cannot compile until somebody has drawn it, which is the only mechanism
// there is for a set whose members are pictures.
internal fun DrawScope.drawMarkPreset(preset: MarkPreset, unit: Float, dx: Float, dy: Float, color: Color) {
    when (preset) {
        MarkPreset.THRESHOLD -> drawPlayerMark(unit = unit, dx = dx, dy = dy, color = color)
        MarkPreset.TERMINATOR -> drawTerminatorMark(unit = unit, dx = dx, dy = dy, color = color)
        MarkPreset.APHELION -> drawAphelionMark(unit = unit, dx = dx, dy = dy, color = color)
        MarkPreset.SEXTANT -> drawSextantMark(unit = unit, dx = dx, dy = dy, color = color)
        MarkPreset.WAKE -> drawWakeMark(unit = unit, dx = dx, dy = dy, color = color)
        MarkPreset.SOUNDING -> drawSoundingMark(unit = unit, dx = dx, dy = dy, color = color)
    }
}

// A whole world with the line between its day and its night drawn across it. The only centred mark in
// the set, which is what makes its silhouette a disc where every other one is a direction.
private fun DrawScope.drawTerminatorMark(unit: Float, dx: Float, dy: Float, color: Color) {
    fun at(x: Float, y: Float) = Offset(x = (x + dx) * unit, y = (y + dy) * unit)

    drawCircle(
        color = color,
        radius = TERMINATOR_LIMB_R * unit,
        center = at(TERMINATOR_CX, TERMINATOR_CY),
        style = markStroke(unit),
    )
    // **The arc's vertical radius is the limb's**, so it starts and ends on the world's own edge and
    // is inside it everywhere between — which is what a terminator is, and what `MarkPresetTest`
    // samples across the middle of. Narrow it and the world is more nearly full; widen it past the
    // limb and the arc breaks out of the disc it is meant to divide.
    drawMarkArc(
        color = color,
        unit = unit,
        dx = dx,
        dy = dy,
        centreX = TERMINATOR_CX,
        centreY = TERMINATOR_CY,
        radiusX = TERMINATOR_ARC_RX,
        radiusY = TERMINATOR_LIMB_R,
        startAngle = TERMINATOR_ARC_START,
        sweepAngle = TERMINATOR_ARC_SWEEP,
    )
}

// The far end of an orbit, and the far end is the whole subject: a wide flat ellipse with the one
// point on it that the mark is named for.
private fun DrawScope.drawAphelionMark(unit: Float, dx: Float, dy: Float, color: Color) {
    fun at(x: Float, y: Float) = Offset(x = (x + dx) * unit, y = (y + dy) * unit)

    drawMarkOval(
        color = color,
        unit = unit,
        dx = dx,
        dy = dy,
        centreX = APHELION_CX,
        centreY = APHELION_CY,
        radiusX = APHELION_RX,
        radiusY = APHELION_RY,
    )
    // On the ellipse's right vertex, not near it. This is also the furthest ink any mark in the set
    // puts out, which is what fixes the ellipse's width — see `MarkPresetTest`.
    drawCircle(color = color, radius = DOT_R * unit, center = at(APHELION_DOT_CX, APHELION_DOT_CY))
}

// An instrument, drawn as one: a scale, an arm pivoted at the scale's own centre, and the reading the
// arm is set to. The corner silhouette in the set.
private fun DrawScope.drawSextantMark(unit: Float, dx: Float, dy: Float, color: Color) {
    fun at(x: Float, y: Float) = Offset(x = (x + dx) * unit, y = (y + dy) * unit)

    // A quarter of a circle whose centre is the pivot below-left of it: from due right round to
    // straight up, anticlockwise, which on a screen whose y runs down is a negative sweep.
    drawMarkArc(
        color = color,
        unit = unit,
        dx = dx,
        dy = dy,
        centreX = SEXTANT_CX,
        centreY = SEXTANT_CY,
        radiusX = SEXTANT_R,
        radiusY = SEXTANT_R,
        startAngle = DUE_RIGHT,
        sweepAngle = -QUARTER_TURN,
    )
    // **The arm ends *at* the dot's centre rather than short of it**, which is the opposite of
    // `THRESHOLD`'s rule and deliberate: an index that floated off its own scale would be a broken
    // instrument, where a trajectory that touched its world would be a magnifier. Written as the
    // dot's coordinates rather than as a second copy of them, so the two cannot come apart.
    drawLine(
        color = color,
        start = at(SEXTANT_CX, SEXTANT_CY),
        end = at(SEXTANT_DOT_CX, SEXTANT_DOT_CY),
        strokeWidth = MARK_STROKE_WIDTH * unit,
        cap = StrokeCap.Round,
    )
    drawCircle(color = color, radius = DOT_R * unit, center = at(SEXTANT_DOT_CX, SEXTANT_DOT_CY))
}

// What something leaves behind it: two quarter arcs thrown back from a point, both centred on the
// point itself. The nest in the set, and the one most at risk of filling in at 20dp — which is what
// the gaps in `MarkPresetTest` are about.
private fun DrawScope.drawWakeMark(unit: Float, dx: Float, dy: Float, color: Color) {
    fun at(x: Float, y: Float) = Offset(x = (x + dx) * unit, y = (y + dy) * unit)

    // Both from straight up round to due left, anticlockwise: the upper-left quadrant, so the wake
    // trails behind something heading down and to the right.
    drawMarkArc(
        color = color,
        unit = unit,
        dx = dx,
        dy = dy,
        centreX = WAKE_DOT_CX,
        centreY = WAKE_DOT_CY,
        radiusX = WAKE_INNER_R,
        radiusY = WAKE_INNER_R,
        startAngle = STRAIGHT_UP,
        sweepAngle = -QUARTER_TURN,
    )
    drawMarkArc(
        color = color,
        unit = unit,
        dx = dx,
        dy = dy,
        centreX = WAKE_DOT_CX,
        centreY = WAKE_DOT_CY,
        radiusX = WAKE_OUTER_R,
        radiusY = WAKE_OUTER_R,
        startAngle = STRAIGHT_UP,
        sweepAngle = -QUARTER_TURN,
    )
    // Larger than the terminus dot, because it has two arcs standing off it and a 1.7 dot under a 5.6
    // arc reads as the smaller of two circles rather than as the thing the arcs are about.
    drawCircle(color = color, radius = WAKE_DOT_R * unit, center = at(WAKE_DOT_CX, WAKE_DOT_CY))
}

// A depth taken: a horizon, a line dropped square from it, and the weight on the end. The vertical
// silhouette, and the only mark that reaches both the top and the bottom of the box.
private fun DrawScope.drawSoundingMark(unit: Float, dx: Float, dy: Float, color: Color) {
    fun at(x: Float, y: Float) = Offset(x = (x + dx) * unit, y = (y + dy) * unit)

    drawLine(
        color = color,
        start = at(SOUNDING_LEFT_X, SOUNDING_Y),
        end = at(SOUNDING_RIGHT_X, SOUNDING_Y),
        strokeWidth = MARK_STROKE_WIDTH * unit,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = at(SOUNDING_X, SOUNDING_Y),
        end = at(SOUNDING_X, SOUNDING_BOTTOM_Y),
        strokeWidth = MARK_STROKE_WIDTH * unit,
        cap = StrokeCap.Round,
    )
    // **The line stops exactly on the weight's upper edge**, so with the round cap the two overlap by
    // a half stroke and read as one object hanging from another. A gap here would be two marks
    // stacked up rather than a sounding.
    drawCircle(color = color, radius = SOUNDING_DOT_R * unit, center = at(SOUNDING_X, SOUNDING_DOT_CY))
}
