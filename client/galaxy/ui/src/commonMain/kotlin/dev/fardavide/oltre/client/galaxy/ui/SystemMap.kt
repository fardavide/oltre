package dev.fardavide.oltre.client.galaxy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.core.oltreMono

// The system, drawn as what it is: a star with things going round it, seen from just above the
// plane. One ellipse per occupied slot, hot orbits tight to the star and cold ones out at the edge
// of the frame.
//
// **This replaced the fifteen-tick strip at 0.3.0, and it cost something real** — see
// `SystemMapUiState` for what and why. What it buys is that the picture is now a picture of the
// thing: an orbit's width says how far out the world is, its number says which slot, and the star
// at the middle is the object a probe is aimed at. The strip could only say the first of those by
// convention, left to right.
//
// Drawn as a Canvas rather than assembled from Boxes for the reason `PowerMark` is: a circle from a
// shaped Box resolves through the platform's shape renderer, and the screenshot baselines are
// recorded on macOS and verified on Linux. A Canvas is the same pixels everywhere. The slot numbers
// are real `Text` for the other half of the same argument — text measured by the Canvas and text
// measured by the layout are two different code paths, and only one of them is the one every other
// number in the app goes through.
@Composable
internal fun SystemMap(map: SystemMapUiState, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        // **A fixed aspect rather than a fixed height, and the geometry in fractions rather than in
        // dp.** The card this sits in is 266dp wide inside a 320dp Slide Over and 506dp at the
        // content cap, so a drawing laid out in absolute dp is a drawing that clips at one end of
        // that range and floats in the left half of the card at the other. Everything that *places*
        // something — the star's centre, the orbit radii — is therefore a fraction of the canvas,
        // and everything that *draws* a mark — the star's own diameter, a body's radius, a stroke —
        // stays in dp, because a 2.5dp world is 2.5dp wherever it is seen.
        val height = maxWidth * REFERENCE_HEIGHT_RATIO
        // Clipped for the reason the starfield is: the ambient glow is 180dp across and the outer
        // orbit reaches past the frame on the left, and Compose does not clip a child to its layout
        // bounds — so unclipped, both of them paint outside the card they belong to and over the
        // screen behind it. The design reference clips at exactly the same edge; its own outermost
        // ellipse is cut by the frame.
        Canvas(modifier = Modifier.fillMaxWidth().height(height).clipToBounds()) {
            val star = Offset(x = size.width * STAR_X_FRACTION, y = size.height * STAR_Y_FRACTION)
            drawSystemGlow(star)
            map.bodies.forEachIndexed { index, body ->
                drawOrbit(star = star, body = body, outwardness = index.toFloat() / map.bodies.lastIndex.coerceAtLeast(1))
            }
            drawStar(star)
            map.bodies.forEach { body -> drawBody(star = star, body = body) }
            map.trajectory?.let { drawTrajectory(star = star, bodies = map.bodies) }
        }
        // Under each body, on the same 24dp column whatever the digit count, so a two-digit slot
        // stays centred on its world rather than drifting right of it. Laid out rather than drawn,
        // so it goes through the same text stack every other number in the app does — which means
        // it needs the same fractions the Canvas uses, in dp.
        // Two rows rather than one once the bodies are closer together than their own numbers are
        // wide. A system can hold up to fifteen, and at 320dp nine of them put 12dp between
        // neighbours against a 24dp label — so from that density on, alternate slots drop further
        // and the numbers interleave instead of overprinting. Below it, one straight row.
        val spacing = maxWidth * (ORBIT_OUTER_FRACTION - ORBIT_INNER_FRACTION) *
            PHASE_COS / (map.bodies.size - 1).coerceAtLeast(1)
        val stagger = spacing < LABEL_WIDTH
        map.bodies.forEachIndexed { index, body ->
            val at = body.positionIn(width = maxWidth, height = height)
            val drop = if (stagger && index % 2 == 1) LABEL_DROP + LABEL_STAGGER else LABEL_DROP
            Box(
                modifier = Modifier
                    .offset(x = at.x.dp - LABEL_WIDTH / 2, y = at.y.dp + drop)
                    .width(LABEL_WIDTH),
            ) {
                Text(
                    text = Strings.orbitSlot(body.slot).resolve(),
                    color = OltreColors.textTertiary,
                    fontFamily = oltreMono(),
                    fontSize = 9.5.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        // At the faint end of the arc, which is where the probe is going rather than where it is.
        // Right-aligned onto the head of the trajectory rather than placed at a fixed offset, so it
        // arrives beside the dot at every width instead of drifting away from it.
        map.trajectory?.let { trajectory ->
            Text(
                text = trajectory.label.resolve(),
                color = OltreColors.accent,
                fontFamily = oltreMono(),
                fontSize = 9.5.sp,
                maxLines = 1,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .offset(
                        x = maxWidth * TRAJECTORY_END_X_FRACTION - TRAJECTORY_LABEL_WIDTH - TRAJECTORY_LABEL_GAP,
                        y = height * TRAJECTORY_END_Y_FRACTION - TRAJECTORY_LABEL_LIFT,
                    )
                    .width(TRAJECTORY_LABEL_WIDTH),
            )
        }
    }
}

// The one thing on the screen that is not drawn on the card: a wash of the system's own light,
// wide enough that its edge never lands anywhere the eye can find it. Crystal rather than amber,
// because amber is the star itself and a glow in the star's own hue would read as a bigger star.
private fun DrawScope.drawSystemGlow(star: Offset) {
    val radius = SYSTEM_GLOW_RADIUS.toPx()
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to OltreColors.crystal.copy(alpha = 0.10f),
                0.42f to OltreColors.crystal.copy(alpha = 0.03f),
                0.70f to Color.Transparent,
            ),
            center = star,
            radius = radius,
        ),
        radius = radius,
        center = star,
    )
}

// A hairline, and dimmer the further out it is. The ramp is what stops fifteen possible orbits
// reading as a target: the near ones are the ones a player can act on soonest, so they are the ones
// that carry the ink.
private fun DrawScope.drawOrbit(star: Offset, body: MapBodyUiState, outwardness: Float) {
    val rx = body.orbitRadiusPx(size.width)
    val ry = rx * ORBIT_FLATTENING
    drawOval(
        color = Color.White.copy(alpha = ORBIT_ALPHA_NEAR + (ORBIT_ALPHA_FAR - ORBIT_ALPHA_NEAR) * outwardness),
        topLeft = Offset(x = star.x - rx, y = star.y - ry),
        size = Size(width = rx * 2f, height = ry * 2f),
        style = Stroke(width = 1.dp.toPx()),
    )
}

// The one legitimate gradient in the app, and now it has a corona: a star is a lit sphere, and
// everything else on both maps is a flat circle or a hairline. The offset origin is what makes it a
// sphere rather than a disc — light arrives from somewhere.
private fun DrawScope.drawStar(star: Offset) {
    val corona = STAR_CORONA.toPx() / 2f
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to CORONA_CORE,
                0.26f to OltreColors.warn.copy(alpha = 0.22f),
                0.52f to OltreColors.warn.copy(alpha = 0.06f),
                0.72f to Color.Transparent,
            ),
            center = star,
            radius = corona,
        ),
        radius = corona,
        center = star,
    )
    val radius = STAR_DIAMETER.toPx() / 2f
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(0f to STAR_CORE, 0.45f to STAR_MID, 1f to STAR_EDGE),
            center = Offset(x = star.x - radius * 0.20f, y = star.y - radius * 0.28f),
            radius = radius * STAR_GRADIENT_REACH,
        ),
        radius = radius,
        center = star,
    )
}

private fun DrawScope.drawBody(star: Offset, body: MapBodyUiState) {
    val at = star + body.positionOffset(size.width)
    // Your own colony is the one body on the map with light of its own. Everything else is lit by
    // the star, which is why the rest are flat fills and this one carries a halo.
    if (body.mark == MapMark.HOME) {
        val halo = COLONY_HALO.toPx() / 2f
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to OltreColors.accent.copy(alpha = 0.34f),
                    0.48f to OltreColors.accent.copy(alpha = 0.08f),
                    0.72f to Color.Transparent,
                ),
                center = at,
                radius = halo,
            ),
            radius = halo,
            center = at,
        )
    }
    when (body.mark) {
        // Ringed rather than filled, exactly as the strip drew it: a relay is a structure rather
        // than a world, and the difference has to survive being 5dp across.
        MapMark.RELAY -> drawCircle(
            color = OltreColors.accent.copy(alpha = 0.55f),
            radius = RELAY_RADIUS.toPx(),
            center = at,
            style = Stroke(width = 1.dp.toPx()),
        )
        else -> drawCircle(color = body.mark.hue(), radius = body.mark.radius().toPx(), center = at)
    }
}

// A probe leaving home, drawn as the arc it is flying rather than as a line to somewhere. It fades
// along its own length — bright where the probe is, faint where it is going — which is the only
// mark on either map that carries a direction.
private fun DrawScope.drawTrajectory(star: Offset, bodies: List<MapBodyUiState>) {
    val from = bodies.firstOrNull { it.mark == MapMark.HOME }?.let { star + it.positionOffset(size.width) } ?: return
    val to = Offset(x = size.width * TRAJECTORY_END_X_FRACTION, y = size.height * TRAJECTORY_END_Y_FRACTION)
    val path = Path().apply {
        moveTo(from.x, from.y)
        // Bowed above the chord, so it reads as an orbit being left rather than as a ruler laid
        // between two points.
        cubicTo(
            x1 = from.x + (to.x - from.x) * 0.26f,
            y1 = from.y - (from.y - to.y) * 0.32f,
            x2 = from.x + (to.x - from.x) * 0.55f,
            y2 = from.y - (from.y - to.y) * 0.76f,
            x3 = to.x,
            y3 = to.y,
        )
    }
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                0f to OltreColors.accent.copy(alpha = 0.85f),
                1f to OltreColors.accent.copy(alpha = 0.14f),
            ),
            start = from,
            end = to,
        ),
        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
    )
    drawCircle(color = OltreColors.accent, radius = 2.6.dp.toPx(), center = to)
}

// Where a body sits, relative to the star, in whatever unit the width was given in — pixels for the
// Canvas and dp for the label. One function, so the number under a world cannot drift off it.
private fun MapBodyUiState.positionOffset(width: Float): Offset {
    val rx = orbitRadiusPx(width)
    return Offset(x = rx * PHASE_COS, y = rx * ORBIT_FLATTENING * PHASE_SIN)
}

private fun MapBodyUiState.positionIn(width: Dp, height: Dp): Offset {
    val from = positionOffset(width.value)
    return Offset(x = width.value * STAR_X_FRACTION + from.x, y = height.value * STAR_Y_FRACTION + from.y)
}

private fun MapBodyUiState.orbitRadiusPx(width: Float): Float =
    width * (ORBIT_INNER_FRACTION + (ORBIT_OUTER_FRACTION - ORBIT_INNER_FRACTION) * orbit)

// Red for Blocked because it is the same "you are short of something" that reddens a cost chip,
// green for Settleable because it is production, and grey for the two that are facts rather than
// states. Unsurveyed is bright rather than dim: dimming is the *locked* treatment, and calling
// 4,746 surveyable worlds locked would be a lie.
private fun MapMark.hue(): Color = when (this) {
    MapMark.HOME, MapMark.RELAY -> OltreColors.accent
    MapMark.BLOCKED -> OltreColors.danger
    MapMark.SETTLEABLE -> OltreColors.ok
    MapMark.OCCUPIED, MapMark.BARREN -> OltreColors.textSecondary
    MapMark.UNSURVEYED -> OltreColors.text.copy(alpha = 0.62f)
    MapMark.EMPTY -> Color.White.copy(alpha = 0.16f)
}

// Your colony is the biggest thing on the map after the star, and everything else is one size. The
// map ranks by *kind*, never by verdict: a settleable world drawn larger than a blocked one would
// be the picture making the decision the list is there to let the player make.
private fun MapMark.radius(): Dp = when (this) {
    MapMark.HOME -> 5.dp
    MapMark.RELAY -> RELAY_RADIUS
    MapMark.EMPTY,
    MapMark.OCCUPIED,
    MapMark.UNSURVEYED,
    MapMark.BLOCKED,
    MapMark.BARREN,
    MapMark.SETTLEABLE,
    -> 3.4.dp
}

// **Every body sits at the same phase**, which is what the design reference does and what makes the
// map legible: with one angle, both coordinates are monotone in the orbit's width, so bodies run up
// and to the right in slot order and no two of them can ever land on each other. Varying the phase
// per body — the obvious way to make it look less mechanical — is what put two worlds on the same
// pixel, because a wider orbit at a shallower angle reaches no further across than a narrower one
// at a steeper.
//
// −33° is the middle of the band the reference's four worlds occupy: far enough round to lift each
// body clear of the orbit below it, near enough to the right extent that the outermost still fits
// the frame.
private const val PHASE_COS = 0.8387f
private const val PHASE_SIN = -0.5446f

// Everything below is the design reference's own frame — a 361 x 286 block with the star at
// (70, 156), orbits from 52 to 214 and the flight leaving at (330, 62) — expressed as fractions of
// it. Keeping the ratios rather than the lengths is what lets one drawing serve a 266dp card and a
// 506dp one.
private const val REFERENCE_HEIGHT_RATIO = 286f / 361f
private const val STAR_X_FRACTION = 70f / 361f
private const val STAR_Y_FRACTION = 156f / 286f

// The innermost and outermost orbit the map draws, which the reference drew for its first and last
// world. Everything between is `orbitOf`'s even spread.
private const val ORBIT_INNER_FRACTION = 52f / 361f
private const val ORBIT_OUTER_FRACTION = 214f / 361f

// Where the flight leaves the frame. The label is right-aligned to land just short of the head.
private const val TRAJECTORY_END_X_FRACTION = 330f / 361f
private const val TRAJECTORY_END_Y_FRACTION = 62f / 286f
// Wide enough for the longest coordinate the space can produce — "[4:250] · 23h 59m" — so the
// label never truncates silently on a single line.
private val TRAJECTORY_LABEL_WIDTH = 112.dp
private val TRAJECTORY_LABEL_LIFT = 17.dp

// The label stops short of the head rather than running under it: the dot is the destination and
// the words name it, so they read as one thing only if they do not overlap.
private val TRAJECTORY_LABEL_GAP = 7.dp

// How far the plane is tipped. At 0.36 an orbit reads as a circle seen from above rather than as an
// ellipse drawn for its own sake, and the outer orbits still clear the star.
private const val ORBIT_FLATTENING = 0.36f
private const val ORBIT_ALPHA_NEAR = 0.10f
private const val ORBIT_ALPHA_FAR = 0.055f

private val STAR_DIAMETER = 36.dp
private val STAR_CORONA = 136.dp
private val SYSTEM_GLOW_RADIUS = 180.dp
private val COLONY_HALO = 56.dp
private val RELAY_RADIUS = 2.5.dp

// The gradient reaches past the disc it fills, which is what keeps the limb from banding: clipped
// at its own radius the outermost stop would land exactly on the edge.
private const val STAR_GRADIENT_REACH = 1.75f

private val LABEL_WIDTH = 24.dp
private val LABEL_DROP = 8.dp
private val LABEL_STAGGER = 13.dp

private val STAR_CORE = Color(0xFFFFF3DC)
private val STAR_MID = Color(0xFFFFD08A)
private val STAR_EDGE = Color(0xFFFFB454)
private val CORONA_CORE = Color(0xFFFFD696).copy(alpha = 0.55f)
