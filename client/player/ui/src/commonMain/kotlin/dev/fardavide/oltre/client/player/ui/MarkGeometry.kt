package dev.fardavide.oltre.client.player.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

// **Every number *A Name You Chose* wrote down, in the 24-unit box it wrote them in.** Kept as the
// frame's own coordinates rather than as scaled pixels, for the reason `OltreMark` gives: a shape
// stated in the design's units can be compared against the design, and a shape stated in anything
// else has to be reverse-engineered before it can be argued with.
//
// **A separate file from the drawings, exactly as `PlayerStripGeometry` is**, and for that file's
// measured reason rather than by analogy: a test that reaches for a constant loads the file class it
// lives in, and loading a file that also holds a drawing drags every uncoverable line of that drawing
// into the denominator behind it. Seventeen drawings now share these numbers, so the seam earns
// itself several times over. Nothing here is `private` for the same reason that file states — a
// constant a test cannot read is a constant the test has to re-type, and a re-typed constant is a
// second copy of the geometry that can drift from the first.
//
// **The two conversions live here too, and that is a decision rather than an oversight.** The frame
// writes an arc as a centre and a radius, or as two endpoints and a radius; Compose takes a bounding
// box and two angles. Turning one into the other *is* geometry, it is needed by both the presets and
// the parts, and a copy of it in each file would be precisely the duplicate this file exists to
// prevent. Both are executed by every drawing test, so they cost the pass nothing.

// ── The box ─────────────────────────────────────────────────────────────────────────────────

internal const val MARK_VIEWBOX = 24f
internal const val MARK_STROKE_WIDTH = 1.6f

// ── The composer's three regions ────────────────────────────────────────────────────────────
//
// **Bodies in the lower-left circle, paths in the diagonal band, termini in the corner** — the
// design's own words, and the property the whole grammar rests on: no combination of the eleven parts
// can put two strokes in the same place, so a legal mark is legible by construction rather than by
// forty separate checks. `MarkPartTest` walks the seam between the first two regions for every one of
// the twelve pairings, because that is the one place the three regions come close enough to argue.
//
// `THRESHOLD` is drawn out of these same numbers rather than out of a fourth set of its own, which is
// `MarkPreset.asComposed()` said in geometry: it is the one preset the grammar can make, and two
// copies of one shape are two things to keep in step.

internal const val BODY_CX = 8.2f
internal const val BODY_CY = 15.8f

// The world, and the radius every body is drawn at or inside.
internal const val LIMB_R = 4.7f

// The day-night boundary across the limb. Its vertical radius *is* the limb's, which is what makes it
// meet the world at both poles and nowhere else.
internal const val BODY_TERMINATOR_RX = 2.9f

// Flattened to a ring seen nearly edge-on. Its horizontal radius is the limb's, so an orbit and a
// limb are the same width and the difference between the two bodies is entirely the squash.
internal const val ORBIT_RY = 2.6f

// The nest: the limb's own radius and a second arc close in, drawn as quarters rather than as circles
// so the body reads as something that has passed rather than something that is there.
internal const val BODY_WAKE_INNER_R = 1.8f

internal const val PATH_START_X = 13.4f
internal const val PATH_START_Y = 10.6f
internal const val PATH_END_X = 18.5f
internal const val PATH_END_Y = 5.5f

// The transfer bows off the straight path at this radius; see `TRANSFER_CENTRE_X` for which of the
// two circles through the endpoints that leaves.
internal const val TRANSFER_R = 4.2f

// **The twin is the rising path shifted, so the two are parallel by construction** rather than by two
// pairs of coordinates that happen to agree. The frame writes the second line out as 11/8.2 →
// 16.1/3.1, which is this shift on each axis; what it measured is the *perpendicular* distance that
// leaves — 3.4 units — because at 2.4 the pair merged into one thick stroke at 20dp and stopped being
// a twin at all.
internal const val TWIN_SHIFT = 2.4f

internal const val TERMINUS_CX = 19.9f
internal const val TERMINUS_CY = 4.1f

// Filled, and the only fill the icon rules allow. A stroked ring at this radius fills in at 1.6 and
// becomes the dot again, which is why the ring below is drawn larger.
internal const val DOT_R = 1.7f
internal const val RING_R = 2f

// ── The four presets the grammar cannot make ────────────────────────────────────────────────
//
// A centred disc, a full-width ellipse, a 12.4-unit arc and a full-height plumb line: none of them
// fits the three regions above, which is the whole argument for keeping both kinds of mark rather
// than replacing the set with the composer.

// A world seen whole, with its terminator across it. Centred, unlike every other mark in the set,
// which is what makes it the one silhouette that is a disc.
internal const val TERMINATOR_CX = 12f
internal const val TERMINATOR_CY = 12f
internal const val TERMINATOR_LIMB_R = 8.4f
internal const val TERMINATOR_ARC_RX = 5.2f

// The furthest point of an orbit, drawn as the orbit and the point. Wide and flat, so the silhouette
// is horizontal where `SOUNDING`'s is vertical.
internal const val APHELION_CX = 12f
internal const val APHELION_CY = 13.6f
internal const val APHELION_RX = 8.8f
internal const val APHELION_RY = 4.6f
internal const val APHELION_DOT_CX = 20.8f
internal const val APHELION_DOT_CY = 13.6f

// **An instrument: a scale, an index arm and where the arm is set.** The arm runs from the arc's own
// centre out to a point on it, which is why the centre is a corner of the mark rather than its middle
// — the frame gives the arc as two endpoints and a radius, and the two happen to be exactly a radius
// apart on each axis, so the centre falls on 5.4/18.6 with nothing to derive.
internal const val SEXTANT_CX = 5.4f
internal const val SEXTANT_CY = 18.6f
internal const val SEXTANT_R = 12.4f
internal const val SEXTANT_DOT_CX = 14.2f
internal const val SEXTANT_DOT_CY = 9.8f

// A nest of arcs behind something that has passed. Both arcs are centred on the dot, so the mark is
// three concentric statements about one point rather than three shapes that happen to be near each
// other.
internal const val WAKE_DOT_CX = 17.6f
internal const val WAKE_DOT_CY = 16.4f
internal const val WAKE_DOT_R = 2f
internal const val WAKE_INNER_R = 5.6f
internal const val WAKE_OUTER_R = 9.4f

// A depth taken: a horizon, a line dropped from it and the weight on the end. The one mark in the set
// whose silhouette is vertical, and the only one that touches both the top and the bottom of the box.
internal const val SOUNDING_LEFT_X = 4.6f
internal const val SOUNDING_RIGHT_X = 19.4f
internal const val SOUNDING_Y = 5.2f
internal const val SOUNDING_X = 12f
internal const val SOUNDING_BOTTOM_Y = 18.1f
internal const val SOUNDING_DOT_CY = 19.9f
internal const val SOUNDING_DOT_R = 1.8f

// ── Angles, in the screen's own reckoning ───────────────────────────────────────────────────
//
// Compose measures from due right and increases *clockwise*, because y runs down the screen. So
// `STRAIGHT_UP` is 270 and a negative sweep turns anticlockwise, which is the opposite of what the
// names would mean on paper and the single easiest thing to get backwards here.

internal const val QUARTER_TURN = 90f
internal const val HALF_TURN = 180f
internal const val STRAIGHT_UP = 270f
internal const val DUE_RIGHT = 0f

// **Which way the terminator bows, on both the preset and the body**, and the frame does not settle
// it: two endpoints and a pair of radii describe either half of the ellipse. The set does settle it.
// Every mark in it leans up and to the right — a trajectory rising out of the top-right of
// `THRESHOLD`, an aphelion at the right vertex, a sextant's arm set toward the upper right — so the
// light is coming from that side and the terminator bows *toward* it rather than away. Stated once
// here so the preset and the composer's body cannot drift apart.
internal const val TERMINATOR_ARC_START = STRAIGHT_UP
internal const val TERMINATOR_ARC_SWEEP = HALF_TURN

// ── The transfer's centre, derived rather than transcribed ──────────────────────────────────
//
// The frame gives this one as SVG gives arcs — two endpoints, a radius and a sweep flag — which is
// the one arc in the set Compose's centre-and-angles form does not fall out of. Two circles of radius
// 4.2 pass through both endpoints; `sweep 1` with no large-arc flag means *the minor arc drawn in the
// direction of increasing screen angle*, and that picks the circle on the lower-right side of the
// chord, so the curve bows up and left of the straight rising path it replaces. Derived here rather
// than transcribed as a coordinate, so the numbers in this file stay the frame's own and a reader can
// check the choice instead of trusting it.

private val TRANSFER_HALF_CHORD = hypot(PATH_END_X - PATH_START_X, PATH_END_Y - PATH_START_Y) / 2f

// How far the centre sits off the chord's midpoint, along the chord's own perpendicular.
private val TRANSFER_OFFSET = sqrt(TRANSFER_R * TRANSFER_R - TRANSFER_HALF_CHORD * TRANSFER_HALF_CHORD)

// The chord runs up and to the right, so its perpendicular rotated a quarter turn clockwise points
// down and to the right — the side `sweep 1` puts the centre on.
private val TRANSFER_PERPENDICULAR_X = -(PATH_END_Y - PATH_START_Y) / (2f * TRANSFER_HALF_CHORD)
private val TRANSFER_PERPENDICULAR_Y = (PATH_END_X - PATH_START_X) / (2f * TRANSFER_HALF_CHORD)

internal val TRANSFER_CENTRE_X =
    (PATH_START_X + PATH_END_X) / 2f + TRANSFER_OFFSET * TRANSFER_PERPENDICULAR_X
internal val TRANSFER_CENTRE_Y =
    (PATH_START_Y + PATH_END_Y) / 2f + TRANSFER_OFFSET * TRANSFER_PERPENDICULAR_Y

internal val TRANSFER_START_ANGLE =
    atan2(PATH_START_Y - TRANSFER_CENTRE_Y, PATH_START_X - TRANSFER_CENTRE_X) * DEGREES_PER_RADIAN

// The central angle a chord of this length subtends at this radius. Positive, because `sweep 1` turns
// the way screen angles increase.
internal val TRANSFER_SWEEP_ANGLE = 2f * asin(TRANSFER_HALF_CHORD / TRANSFER_R) * DEGREES_PER_RADIAN

// ── The two conversions ─────────────────────────────────────────────────────────────────────

// One stroke for the whole set, round-capped. A glyph names no palette value, so the ink is always
// the caller's; what is fixed is the weight, because the icon set is a set only while every mark in
// it is drawn at the same one.
internal fun markStroke(unit: Float): Stroke = Stroke(width = MARK_STROKE_WIDTH * unit, cap = StrokeCap.Round)

// An arc of an ellipse, given the way the frame gives them and drawn the way Compose takes them. A
// circle is the case where the two radii agree, which is most of them.
internal fun DrawScope.drawMarkArc(
    color: Color,
    unit: Float,
    dx: Float,
    dy: Float,
    centreX: Float,
    centreY: Float,
    radiusX: Float,
    radiusY: Float,
    startAngle: Float,
    sweepAngle: Float,
) {
    drawArc(
        color = color,
        startAngle = startAngle,
        sweepAngle = sweepAngle,
        // Never: an arc closed back through its own centre would be a pie slice, and there is not one
        // in the set.
        useCenter = false,
        topLeft = Offset(x = (centreX - radiusX + dx) * unit, y = (centreY - radiusY + dy) * unit),
        size = Size(width = 2f * radiusX * unit, height = 2f * radiusY * unit),
        style = markStroke(unit),
    )
}

// A closed ellipse, stated as a centre and two radii for the same reason.
internal fun DrawScope.drawMarkOval(
    color: Color,
    unit: Float,
    dx: Float,
    dy: Float,
    centreX: Float,
    centreY: Float,
    radiusX: Float,
    radiusY: Float,
) {
    drawOval(
        color = color,
        topLeft = Offset(x = (centreX - radiusX + dx) * unit, y = (centreY - radiusY + dy) * unit),
        size = Size(width = 2f * radiusX * unit, height = 2f * radiusY * unit),
        style = markStroke(unit),
    )
}

private const val DEGREES_PER_RADIAN = (180 / PI).toFloat()
