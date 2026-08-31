package dev.fardavide.oltre.client.player.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.fardavide.oltre.protocol.MarkBody
import dev.fardavide.oltre.protocol.MarkPath
import dev.fardavide.oltre.protocol.MarkTerminus

// **Eleven drawings that make forty marks**, which is the trade the composer is: a fixed set pays one
// baseline per mark and a grammar pays one per part, so eleven parts and twelve pairwise checks buy
// the other twenty-nine marks nobody will ever draw.
//
// **What makes that safe is the regions.** A body is drawn inside the lower-left circle, a path in the
// diagonal band above and to the right of it, a terminus in the top-right corner — so no two parts
// can put ink in the same place whatever a player picks, and the gap that keeps `THRESHOLD` from
// reading as a magnifier is a property of the parts rather than of that particular pairing. It is
// only true while every part stays in its region, which is what `MarkPartTest` is for: the box for
// each part alone, and the seam walked for all twelve body-and-path pairs.
//
// Three dispatchers rather than one, because the three slots are independent and a test that wants to
// measure a body has to be able to draw a body. Each one is exhaustive with no `else`, so a part added
// to the wire cannot compile until it has been drawn.

internal fun DrawScope.drawMarkBody(body: MarkBody, unit: Float, dx: Float, dy: Float, color: Color) {
    when (body) {
        MarkBody.LIMB -> drawLimb(unit = unit, dx = dx, dy = dy, color = color)
        MarkBody.TERMINATOR -> drawTerminatorBody(unit = unit, dx = dx, dy = dy, color = color)
        MarkBody.ORBIT -> drawOrbitBody(unit = unit, dx = dx, dy = dy, color = color)
        MarkBody.WAKE -> drawWakeBody(unit = unit, dx = dx, dy = dy, color = color)
    }
}

internal fun DrawScope.drawMarkPath(path: MarkPath, unit: Float, dx: Float, dy: Float, color: Color) {
    when (path) {
        MarkPath.RISING -> drawRisingPath(unit = unit, dx = dx, dy = dy, color = color)
        MarkPath.TRANSFER -> drawTransferPath(unit = unit, dx = dx, dy = dy, color = color)
        MarkPath.TWIN -> drawTwinPath(unit = unit, dx = dx, dy = dy, color = color)
        // **Nothing, and nothing is a drawing.** A body on its own is a legal mark — it is the whole
        // reason the set is 4 × (3 × 3 + 1) rather than 4 × 3 × 3 — so this branch has to exist and
        // has to be empty. A stub left here would put ink in the diagonal band that the wire says is
        // clear, which is exactly the invariant the regions are built on.
        MarkPath.NONE -> Unit
    }
}

internal fun DrawScope.drawMarkTerminus(terminus: MarkTerminus, unit: Float, dx: Float, dy: Float, color: Color) {
    fun at(x: Float, y: Float) = Offset(x = (x + dx) * unit, y = (y + dy) * unit)

    when (terminus) {
        MarkTerminus.DOT -> drawCircle(color = color, radius = DOT_R * unit, center = at(TERMINUS_CX, TERMINUS_CY))
        // **Larger than the dot it is the alternative to, and the extra 0.3 is the whole difference.**
        // A ring of radius 1.7 stroked at 1.6 has 0.9 units of hole and closes up under
        // anti-aliasing at 20dp — it becomes the dot, and the player who picked *ring* gets *dot*.
        //
        // It is also as large as it can be: measured, its outer edge sits 1.3 units below the top of
        // the box, against the one unit of air every drawing in the set is held to. This is the
        // tightest part in the grammar and the next tenth comes out of the corner it is drawn in.
        MarkTerminus.RING -> drawCircle(
            color = color,
            radius = RING_R * unit,
            center = at(TERMINUS_CX, TERMINUS_CY),
            style = markStroke(unit),
        )
        // A terminus is the end of a path, so a mark with no path has none — the wire refuses the
        // other pairing outright. Empty for `MarkPath.NONE`'s reason.
        MarkTerminus.NONE -> Unit
    }
}

// ── Bodies: the lower-left circle ───────────────────────────────────────────────────────────

// The world, and the plainest thing in the grammar. Also `THRESHOLD`'s own limb, drawn from the same
// constants rather than from a second copy of them.
private fun DrawScope.drawLimb(unit: Float, dx: Float, dy: Float, color: Color) {
    fun at(x: Float, y: Float) = Offset(x = (x + dx) * unit, y = (y + dy) * unit)

    drawCircle(color = color, radius = LIMB_R * unit, center = at(BODY_CX, BODY_CY), style = markStroke(unit))
}

// The same world with its day-night line across it — the preset's shape at the composer's scale, and
// bowing the same way for the reason `TERMINATOR_ARC_START` gives.
private fun DrawScope.drawTerminatorBody(unit: Float, dx: Float, dy: Float, color: Color) {
    drawLimb(unit = unit, dx = dx, dy = dy, color = color)
    drawMarkArc(
        color = color,
        unit = unit,
        dx = dx,
        dy = dy,
        centreX = BODY_CX,
        centreY = BODY_CY,
        radiusX = BODY_TERMINATOR_RX,
        // The limb's own radius, so the arc lands on the world's edge at both poles.
        radiusY = LIMB_R,
        startAngle = TERMINATOR_ARC_START,
        sweepAngle = TERMINATOR_ARC_SWEEP,
    )
}

// A ring seen nearly edge-on. As wide as the limb and less than half as tall, so the two bodies read
// as the same object flat and upright rather than as two sizes of circle.
private fun DrawScope.drawOrbitBody(unit: Float, dx: Float, dy: Float, color: Color) {
    drawMarkOval(
        color = color,
        unit = unit,
        dx = dx,
        dy = dy,
        centreX = BODY_CX,
        centreY = BODY_CY,
        radiusX = LIMB_R,
        radiusY = ORBIT_RY,
    )
}

// Two quarter arcs and no world: what a body leaves rather than the body. Both are centred where the
// limb would have been, so a wake and a limb occupy the same region and a player switching between
// them sees one shape replace another in place.
private fun DrawScope.drawWakeBody(unit: Float, dx: Float, dy: Float, color: Color) {
    // Both from due right round to straight up, anticlockwise — the upper-right quadrant, which is the
    // side the paths leave from, so the nest opens toward the direction of travel.
    drawMarkArc(
        color = color,
        unit = unit,
        dx = dx,
        dy = dy,
        centreX = BODY_CX,
        centreY = BODY_CY,
        radiusX = BODY_WAKE_INNER_R,
        radiusY = BODY_WAKE_INNER_R,
        startAngle = DUE_RIGHT,
        sweepAngle = -QUARTER_TURN,
    )
    drawMarkArc(
        color = color,
        unit = unit,
        dx = dx,
        dy = dy,
        centreX = BODY_CX,
        centreY = BODY_CY,
        radiusX = LIMB_R,
        radiusY = LIMB_R,
        startAngle = DUE_RIGHT,
        sweepAngle = -QUARTER_TURN,
    )
}

// ── Paths: the diagonal band ────────────────────────────────────────────────────────────────

// Straight out and up. `THRESHOLD`'s own trajectory: its infinite line passes through the body's
// centre, and the *segment* starts well clear of the limb, which is what puts the gap where it is
// rather than leaving it to a coordinate nobody would question.
private fun DrawScope.drawRisingPath(unit: Float, dx: Float, dy: Float, color: Color) {
    fun at(x: Float, y: Float) = Offset(x = (x + dx) * unit, y = (y + dy) * unit)

    drawLine(
        color = color,
        start = at(PATH_START_X, PATH_START_Y),
        end = at(PATH_END_X, PATH_END_Y),
        strokeWidth = MARK_STROKE_WIDTH * unit,
        cap = StrokeCap.Round,
    )
}

// The same two ends, bowed. It shares the rising path's endpoints exactly, so the difference between
// the two is the curvature and nothing else — which is what makes them read as two ways of making the
// same journey.
private fun DrawScope.drawTransferPath(unit: Float, dx: Float, dy: Float, color: Color) {
    drawMarkArc(
        color = color,
        unit = unit,
        dx = dx,
        dy = dy,
        centreX = TRANSFER_CENTRE_X,
        centreY = TRANSFER_CENTRE_Y,
        radiusX = TRANSFER_R,
        radiusY = TRANSFER_R,
        startAngle = TRANSFER_START_ANGLE,
        sweepAngle = TRANSFER_SWEEP_ANGLE,
    )
}

// Two of the rising path, offset. The second is the first shifted by `TWIN_SHIFT` on each axis, so
// they are parallel because they are the same line rather than because two pairs of coordinates
// agree — and the perpendicular distance that leaves is the measured 3.4 the frame settled on.
private fun DrawScope.drawTwinPath(unit: Float, dx: Float, dy: Float, color: Color) {
    fun at(x: Float, y: Float) = Offset(x = (x + dx) * unit, y = (y + dy) * unit)

    drawRisingPath(unit = unit, dx = dx, dy = dy, color = color)
    drawLine(
        color = color,
        start = at(PATH_START_X - TWIN_SHIFT, PATH_START_Y - TWIN_SHIFT),
        end = at(PATH_END_X - TWIN_SHIFT, PATH_END_Y - TWIN_SHIFT),
        strokeWidth = MARK_STROKE_WIDTH * unit,
        cap = StrokeCap.Round,
    )
}
