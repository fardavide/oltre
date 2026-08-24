package dev.fardavide.oltre.client.changelog.domain

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// **The mark, as numbers.** Claude Design's *A Sky Per Build* §2, accepted 2026-08-23: a release is
// drawn as `minor + patch` bodies laid on a golden-angle spiral over a world's limb — the first
// `minor` of them filled, because a minor line is settled, and the rest hollow, because a patch is
// riding on a line that is not.
//
// **A pure function of the version and of nothing else**, which is the whole of Davide's call. Not
// the date, not an index into the release list, not a table somebody keeps: any of those turns a
// mark that costs nothing per release back into a chore, and a chore is what the call was made to
// avoid. `0.31.2` gets its sky the day it is cut, from this file, with nobody asked.
//
// It is here rather than in `:client:changelog:ui` because it is arithmetic. What the ui module has
// left is four `drawCircle`s and an arc, which is the amount of drawing code that cannot be wrong
// without being visibly wrong — everything a test could actually catch is in the properties over
// this, and `VersionSkyTest` is what the design asked for in place of a 29dp baseline.
//
// **Everything here is in dp**, including the floors, so the caller multiplies by one density and
// nothing in the rule is resolution-dependent.
fun ReleaseVersion.skyAt(side: Float): VersionSky {
    val bodyCount = minor + patch
    val bodyRadius = max(BODY_FLOOR, BODY_RADIUS * side)
    val ringRadius = max(RING_FLOOR, RING_RADIUS * side)
    val reach = DISC_RADIUS * side
    val centreX = 0.5f * side
    val centreY = DISC_CENTRE_Y * side

    val bodies = (1..bodyCount).map { index ->
        // The bearing carries `index + patch` and the radius carries `index / N`, so a patch does
        // not add a dot to a sky — it re-lays the whole sky. That is what makes two versions
        // impossible to confuse without keeping a table of what has been used.
        val bearing = GOLDEN_ANGLE * (index + patch) * PI.toFloat() / 180f
        // Equal area per body, so the outermost always sits at `reach` whatever N is: a three-body
        // sky and a nineteen-body sky are the same size, and only their density differs.
        val distance = reach * sqrt(index.toFloat() / bodyCount)
        val filled = index <= minor
        SkyBody(
            // Clockwise from straight up, which is why sine drives x and cosine is subtracted from
            // y rather than the other way round.
            x = centreX + distance * sin(bearing),
            y = centreY - distance * cos(bearing),
            radius = if (filled) bodyRadius else ringRadius,
            filled = filled,
        )
    }

    val limbStroke = max(LIMB_FLOOR, LIMB_STROKE * side)
    val crestY = LIMB_CREST_Y * side
    val limbRadius = LIMB_RADIUS * side
    // How far round the circle the box's own edges are, measured from the crest. The limb is a
    // segment of a circle far bigger than the mark, so only this much of it is ever drawn.
    val halfSweep = asin(0.5f * side / limbRadius) * 180f / PI.toFloat()
    val limb = SkyLimb(
        crestY = crestY,
        radius = limbRadius,
        // Where the arc meets the two sides of the box, from the chord half-width of side / 2.
        edgeY = crestY + limbRadius - sqrt(limbRadius * limbRadius - 0.25f * side * side),
        stroke = limbStroke,
        // Compose measures an arc from three o'clock and sweeps clockwise, so the crest — straight
        // up from the circle's centre — is 270°. The angles are here rather than in the drawing so
        // that "the limb spans the box and no more" is a property a test can execute; a sweep
        // guessed at in a `DrawScope` is a line that runs off the card and is only ever caught by
        // somebody looking at it.
        startAngleDegrees = CREST_ANGLE - halfSweep,
        sweepAngleDegrees = 2f * halfSweep,
    )

    val worldRadius = WORLD_RADIUS * side
    val worlds = (0 until major).map { index ->
        SkyWorld(
            x = centreX + (index - (major - 1) / 2f) * WORLD_SPACING * worldRadius,
            y = crestY - worldRadius - limbStroke,
            radius = worldRadius,
        )
    }

    return VersionSky(side = side, bodies = bodies, worlds = worlds, limb = limb, ringStroke = max(HAIRLINE_FLOOR, HAIRLINE * side))
}

// One release's drawing, in dp, ready for a `DrawScope` to walk. Nothing here knows a colour: the
// mark is white at four alphas and the ui module is where those live, because every hue in this app
// is spoken for and a release makes no claim on one.
data class VersionSky(
    val side: Float,
    val bodies: List<SkyBody>,
    val worlds: List<SkyWorld>,
    val limb: SkyLimb,
    val ringStroke: Float,
)

// A minor line reached (`filled`) or a patch riding on the current one (hollow). The radius differs
// between the two because a ring reads lighter than a disc of the same size and the two have to
// weigh the same on the page.
data class SkyBody(
    val x: Float,
    val y: Float,
    val radius: Float,
    val filled: Boolean,
)

// The world the sky sits over. Constant in every mark, and it earns its place twice: it is what the
// version line sits on, so the page needs no separator between the picture and the copy.
data class SkyLimb(
    val crestY: Float,
    val radius: Float,
    val edgeY: Float,
    val stroke: Float,
    val startAngleDegrees: Float,
    val sweepAngleDegrees: Float,
)

// One finished world per major, resting on the crest. There are none of these until 1.0.0 and the
// design took that on purpose: a major empties the sky, which is the only moment this drawing is
// ever quiet, and the alternative — carrying the previous line's bodies forward — is the first
// clause of a table.
data class SkyWorld(
    val x: Float,
    val y: Float,
    val radius: Float,
)

// 137.5077° is the golden angle, and it is here for the reason a sunflower has it: consecutive
// bodies land as far apart as bodies can and no bearing in a sky ever repeats, so nineteen of them
// stay legible with no jitter, no seed and no table.
private const val GOLDEN_ANGLE = 137.5077f

private const val DISC_CENTRE_Y = 0.44f
private const val DISC_RADIUS = 0.36f
private const val BODY_RADIUS = 0.0125f
private const val RING_RADIUS = 0.0155f
private const val HAIRLINE = 0.0062f
private const val LIMB_STROKE = 0.005f
private const val LIMB_CREST_Y = 0.90f
private const val LIMB_RADIUS = 1.5f

// Straight up from the circle's centre, in the clock Compose's `drawArc` uses.
private const val CREST_ANGLE = 270f
private const val WORLD_RADIUS = 0.05f
private const val WORLD_SPACING = 2.7f

// The floors are what make the mark survive the 29dp it is drawn at on the settings row, where the
// arithmetic alone would give a 0.36dp body — which is nothing at all on a screen. A body is a fill
// rather than a stroke, so the icon set's 1.4dp stroke floor does not apply to it.
private const val BODY_FLOOR = 1.3f
private const val RING_FLOOR = 1.6f
private const val HAIRLINE_FLOOR = 0.8f
private const val LIMB_FLOOR = 1.1f
