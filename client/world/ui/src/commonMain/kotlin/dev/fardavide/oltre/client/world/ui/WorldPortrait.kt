package dev.fardavide.oltre.client.world.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.core.Hazard
import dev.fardavide.oltre.core.Pressure
import dev.fardavide.oltre.core.Temperature
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

// **The picture a survey buys.** A world drawn from its own traits and nothing else, so it is a
// reading rather than decoration: after a fortnight a player recognises a heavy cold world before
// reading a figure, which is the whole of what Davide asked for when he said the map had no
// identity. Claude Design's frames, 2026-08-14.
//
// Read in this order: **fill is temperature, diameter is gravity, banding is pressure**, and the
// marks are the hazards.
//
// ── Three things here are easy to get wrong and invisible once wrong ─────────────────────────────
//
// 1. **Every gradient radius is the farthest *corner* of the box, not the disc's own radius.** CSS
//    `radial-gradient(circle at 33% 27%, …)` defaults to `farthest-corner`, which is `0.990858·d`
//    here — while the clipped circle only ever reaches `0.793259` of that. So the `dark` stop and
//    the limb's full strength are **never actually painted**, and normalising the gradient to `d/2`
//    instead over-darkens every disc in the app. Nothing but a baseline would catch it.
// 2. **The level-of-detail gate is on the BOX, not on the drawn diameter.** A 60dp box at 0.15 g
//    draws a 31dp disc and still gets craters, storms, fractures and a ring; a 59dp box at 3.2 g
//    draws a 48dp disc and gets none of them. Keying it off `d` is the natural mistake, and the
//    compact discovery card at box 44 sits on the small side of a gate that reads like "medium".
// 3. **A fade-to-nothing stop is the same colour at alpha 0, never `Color.Transparent`.** CSS
//    interpolates gradients premultiplied, so a zero-alpha stop contributes no hue at all; Compose
//    interpolates straight, so `Color.Transparent` would drag every dark mark through black and
//    every light one through white. The limb is the one gradient whose CSS ends are genuinely
//    different hues, and premultiplication is exactly what makes it behave as an alpha ramp of the
//    dark end — which is why it is written as one colour here.

// The five temperature steps. **Mixed from the three resource hues and the window background**, and
// deliberately stopping short of amber and red: those two already mean *in transit* and *you are
// short*, and a filled body in either would be the first colour in the app that meant two things.
// So heat is carried by luminance and the hue rotation is narrow — Claude Design's call, and the
// half of "hue from temperature" that did not survive a screen.
private val TEMPERATURE_STEPS: List<Pair<Int, Color>> = listOf(
    -80 to Color(0xFF6E5FA8), // frost — the deuterium violet dropped into the ground
    -30 to Color(0xFF3F7C93), // cold
    45 to Color(0xFFAEB9C9), // in band — metal grey, the colour of the reference world
    90 to Color(0xFFDAB689), // hot — metal warmed toward amber, never reaching it
    Int.MAX_VALUE to Color(0xFFC97A62), // furnace — warmed further, never reaching red
)

private val WINDOW = Color(0xFF05070D)
private val OUTLINE = Color(0xFFE9EDF5)
private val HALO = Color(0xFFA98BFA)

// Every stroke in the component is exactly one dp — the outline, the halo, the ring and a fracture.
private val HAIRLINE = 1.dp

// `box >= 60` — see the second note above. A Dp comparison, because the box is a layout size rather
// than a drawn one.
private val LARGE_FROM = 60.dp

// **Which step a world's fill is mixed from, and every step owns its own boundary degree**: the
// comparison is `<=`, so −80 °C is the last frost rather than the first cold. The top step is
// written with no ceiling because there is none — a lookup that could fall off the end would throw
// on the hottest world in the galaxy rather than draw it.
//
// **`internal` rather than private**, with `surfaceFor`, `mixedWith` and `channel` below and nothing
// else in the file: those four are rules the design states in figures, and a picture is a poor place
// to read a figure back off — see `WorldPortraitTest`, which is their only other caller.
internal fun temperatureStep(temperature: Temperature): Color =
    TEMPERATURE_STEPS.first { temperature.celsius <= it.first }.second

@Composable
fun WorldPortrait(uiState: WorldPortraitUiState, box: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(box)) { drawWorldPortrait(uiState = uiState, box = box) }
}

// **The drawing, separated from the composable that hosts it** — which is not tidying: a `Canvas`
// body cannot be reached by anything but a rendered frame, and this is `DrawScope` code, so a test
// can hand it a `CanvasDrawScope` over an `ImageBitmap` and read the pixels back. That is what turns
// the three warnings at the top of this file from comments into assertions.
fun DrawScope.drawWorldPortrait(uiState: WorldPortraitUiState, box: Dp) {
    val large = box >= LARGE_FROM
    val centre = Offset(size.width / 2f, size.height / 2f)
    val hairline = HAIRLINE.toPx()

    when (uiState) {
        // **One size, and gravity is not applied.** Sizing the socket by gravity would leak the
        // first trait of a world nobody has looked at — and 98% of a list of identical sockets is
        // what makes the list read as an appetite rather than as a table already read.
        WorldPortraitUiState.Unsurveyed -> {
            val d = wholeDp(box, 0.62f)
            drawCircle(
                color = OUTLINE.copy(alpha = 0.16f),
                radius = (d - hairline) / 2f,
                center = centre,
                style = Stroke(width = hairline),
            )
        }

        is WorldPortraitUiState.Surveyed -> drawSurveyed(uiState, box, large, centre, hairline)
    }
}

private fun DrawScope.drawSurveyed(
    world: WorldPortraitUiState.Surveyed,
    box: Dp,
    large: Boolean,
    centre: Offset,
    hairline: Float,
) {
    // 0.50 … 0.82 of a box that never changes size, so a column of rows stays a column and a heavy
    // world still reads as heavy against its neighbours.
    val gravityFraction = (world.gravity.milliG / 3_000f).coerceIn(0.06f, 1f)
    val d = wholeDp(box, 0.50f + 0.32f * gravityFraction)
    val topLeft = Offset(centre.x - d / 2f, centre.y - d / 2f)

    val base = temperatureStep(world.temperature)
    val lit = base.mixedWith(Color.White, if (large) 0.34f else 0.30f)
    val dark = base.mixedWith(WINDOW, 0.62f)

    val tidal = Hazard.TIDALLY_LOCKED in world.hazards

    // Both sit UNDER the disc and outside the clip, which is why they are drawn first and against
    // the box centre rather than the disc's.
    if (world.hasRing && large) drawRing(d, centre, hairline)
    if (Hazard.RADIATION_BELT in world.hazards) drawHalo(d, centre, hairline, large)

    val disc = Path().apply { addOval(Rect(topLeft, Size(d, d))) }
    clipPath(disc) {
        translate(left = topLeft.x, top = topLeft.y) {
            // Everything below is disc-local: the origin is the disc's top-left and it spans 0…d.
            drawBaseFill(d, lit, base, dark)
            drawSurface(d, world, large)
            if (tidal) drawTerminator(d) else drawLimb(d)
            if (large && Hazard.ION_STORMS in world.hazards) drawStorm(d)
            drawFractures(d, world.hazards, large)
        }
    }
}

// `radial-gradient(circle at 33% 27%, lit 0%, base 46%, dark 100%)`, farthest-corner.
private fun DrawScope.drawBaseFill(d: Float, lit: Color, base: Color, dark: Color) {
    drawCircle(
        brush = Brush.radialGradient(
            0f to lit,
            0.46f to base,
            1f to dark,
            center = Offset(0.33f * d, 0.27f * d),
            radius = FARTHEST_CORNER_33_27 * d,
        ),
        radius = d / 2f,
        center = Offset(d / 2f, d / 2f),
    )
}

// **Exactly one of these, or none**, and both ends of the table are epithet nouns: no bands at all
// is a **waste**, bands closed into one featureless veil is a **shroud** — so the vocabulary and the
// drawing agree, and a player tells them apart at 26dp without reading either word.
//
// A type rather than an `Int`, because a count of zero would mean two different pictures: a waste
// is bare ground and a shroud is the thickest atmosphere in the game, and they sit at opposite ends
// of the same axis.
internal sealed interface WorldSurface {

    // Nothing to band. A large disc gets craters and a small one gets nothing at all — at 26dp they
    // are sub-pixel and would read as noise — so the reading is an absence either way.
    data object Waste : WorldSurface

    data class Bands(val count: Int) : WorldSurface

    data object Shroud : WorldSurface
}

// The design's own pressure table. **Capped at two bands on a small disc**, which is why the three
// middle ranges are one reading at 26dp rather than three that cannot be told apart — and neither
// end is capped, because a waste and a shroud are drawings rather than counts and say the same thing
// at both sizes.
internal fun surfaceFor(pressure: Pressure, large: Boolean): WorldSurface {
    val atm = pressure.milliAtm
    return when {
        atm >= 6_000 -> WorldSurface.Shroud
        atm < 100 -> WorldSurface.Waste
        else -> {
            val bands = if (atm < 900) 2 else if (atm < 2_600) 4 else 7
            WorldSurface.Bands(if (large) bands else minOf(bands, 2))
        }
    }
}

private fun DrawScope.drawSurface(d: Float, world: WorldPortraitUiState.Surveyed, large: Boolean) {
    when (val surface = surfaceFor(world.pressure, large)) {
        WorldSurface.Shroud -> drawShroud(d)
        WorldSurface.Waste -> if (large) drawCraters(d)
        is WorldSurface.Bands -> drawBands(d, surface.count)
    }
}

private fun DrawScope.drawShroud(d: Float) {
    drawRect(
        brush = Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.16f),
            0.70f to Color.White.copy(alpha = 0.05f),
            center = Offset(0.40f * d, 0.34f * d),
            radius = FARTHEST_CORNER_40_34 * d,
        ),
        size = Size(d, d),
    )
}

// Three flat dark dots with a feather about a tenth of their own width. Large discs only — at 26dp
// they are sub-pixel and would read as noise.
private fun DrawScope.drawCraters(d: Float) {
    val craters = listOf(
        Triple(Offset(0.62f * d, 0.66f * d), 0.30f, 0.072443f to 0.081498f),
        Triple(Offset(0.34f * d, 0.72f * d), 0.26f, 0.058604f to 0.068371f),
        Triple(Offset(0.70f * d, 0.38f * d), 0.22f, 0.046755f to 0.056106f),
    )
    for ((at, alpha, radii) in craters) {
        val (solid, edge) = radii
        drawCircle(
            brush = Brush.radialGradient(
                0f to WINDOW.copy(alpha = alpha),
                solid / edge to WINDOW.copy(alpha = alpha),
                1f to WINDOW.copy(alpha = 0f),
                center = at,
                radius = edge * d,
            ),
            radius = edge * d,
            center = at,
        )
    }
}

// `repeating-linear-gradient(8deg, …)` — hard-edged stripes, no interpolation. Compose has no
// repeating gradient, so the stripes are drawn as rectangles in a frame rotated by the same 8°, and
// the phase is anchored where CSS anchors it: distance is measured along the gradient axis from the
// point below the disc that CSS calls position zero. Reproduce that anchor or the stripes land in
// the wrong place on a disc that is any other size.
private fun DrawScope.drawBands(d: Float, count: Int) {
    val period = max(2f, d / (count * 2f))
    val axis = Offset(sin(BAND_RADIANS), -cos(BAND_RADIANS))
    val anchor = Offset(0.421406f * d, 1.059225f * d)
    val centre = Offset(d / 2f, d / 2f)
    val centreAlong = (centre - anchor).dot(axis)

    rotate(degrees = BAND_DEGREES, pivot = centre) {
        // In this frame the axis points straight up, so a stripe is a horizontal run and its
        // boundaries are at `centre.y - (k * period - centreAlong)`.
        val first = floor((centreAlong - d) / period).toInt()
        val last = ((centreAlong + d) / period).toInt() + 1
        for (k in first..last) {
            val top = centre.y - ((k + 1) * period - centreAlong)
            drawRect(
                color = if (k.mod(2) == 0) Color.White.copy(alpha = 0.09f) else WINDOW.copy(alpha = 0.10f),
                topLeft = Offset(centre.x - d, top),
                size = Size(2f * d, period),
            )
        }
    }
}

// The soft limb every world has, unless it is tidally locked — in which case the falloff is replaced
// outright by a hard edge and the night side is flooded.
private fun DrawScope.drawLimb(d: Float) {
    drawRect(
        brush = Brush.radialGradient(
            0.52f to WINDOW.copy(alpha = 0f),
            1f to WINDOW.copy(alpha = 0.55f),
            center = Offset(0.33f * d, 0.27f * d),
            radius = FARTHEST_CORNER_33_27 * d,
        ),
        size = Size(d, d),
    )
}

private fun DrawScope.drawTerminator(d: Float) {
    val axis = Offset(sin(TIDAL_RADIANS), -cos(TIDAL_RADIANS))
    val start = Offset(-0.046515f * d, 0.442559f * d)
    val length = 1.099050f * d
    drawRect(
        brush = Brush.linearGradient(
            0f to WINDOW.copy(alpha = 0f),
            0.46f to WINDOW.copy(alpha = 0f),
            0.47f to WINDOW.copy(alpha = 0.90f),
            1f to WINDOW.copy(alpha = 0.90f),
            start = start,
            end = start + axis * length,
        ),
        size = Size(d, d),
    )
}

// A conic sweep on the lit side. Compose sweeps from three o'clock where CSS starts at twelve, and
// the element carries its own 18° rotation on top of the gradient's 200° — so the frame is rotated
// by the difference rather than the gradient being re-authored.
private fun DrawScope.drawStorm(d: Float) {
    val side = (d * 0.46f).roundToInt().toFloat()
    val at = Offset((d * 0.52f).roundToInt().toFloat(), (d * 0.22f).roundToInt().toFloat())
    val centre = Offset(at.x + side / 2f, at.y + side / 2f)
    rotate(degrees = STORM_FROM_DEGREES - 90f, pivot = centre) {
        drawCircle(
            brush = Brush.sweepGradient(
                0f to Color.White.copy(alpha = 0.26f),
                0.42f to Color.White.copy(alpha = 0.02f),
                0.60f to Color.White.copy(alpha = 0f),
                1f to Color.White.copy(alpha = 0f),
                center = centre,
            ),
            radius = side / 2f,
            center = centre,
        )
    }
}

// **Seismic wins outright over thin crust rather than adding to it** — three deeper lines, never
// five. They are the same mark at two strengths because they are the same kind of fact.
private fun DrawScope.drawFractures(d: Float, hazards: Set<Hazard>, large: Boolean) {
    val deep = Hazard.SEISMIC_INSTABILITY in hazards
    val angles = when {
        !large -> return
        deep -> listOf(26f, -34f, 8f)
        Hazard.THIN_CRUST in hazards -> listOf(-22f, 40f)
        else -> return
    }
    val alpha = if (deep) 0.34f else 0.22f
    val left = (d * 0.06f).roundToInt().toFloat()
    val width = (d * 0.88f).roundToInt().toFloat()
    val hairline = HAIRLINE.toPx()

    angles.forEachIndexed { index, angle ->
        val top = (d * (0.30f + index * 0.20f)).roundToInt().toFloat()
        rotate(degrees = angle, pivot = Offset(0.50f * d, top + hairline / 2f)) {
            drawRect(
                // The peak sits at 30% rather than in the middle: the line is deliberately brighter
                // toward its left end.
                brush = Brush.linearGradient(
                    0f to OUTLINE.copy(alpha = 0f),
                    0.30f to OUTLINE.copy(alpha = alpha),
                    1f to OUTLINE.copy(alpha = 0f),
                    start = Offset(left, top),
                    end = Offset(left + width, top),
                ),
                topLeft = Offset(left, top),
                size = Size(width, hairline),
            )
        }
    }
}

// Behind the disc, and the only thing in the component allowed to escape the box.
private fun DrawScope.drawRing(d: Float, centre: Offset, hairline: Float) {
    val width = (d * 1.28f).roundToInt().toFloat()
    val height = (d * 0.30f).roundToInt().toFloat()
    rotate(degrees = -16f, pivot = centre) {
        drawOval(
            color = OUTLINE.copy(alpha = 0.30f),
            topLeft = Offset(centre.x - (width - hairline) / 2f, centre.y - (height - hairline) / 2f),
            size = Size(width - hairline, height - hairline),
            style = Stroke(width = hairline),
        )
    }
}

private fun DrawScope.drawHalo(d: Float, centre: Offset, hairline: Float, large: Boolean) {
    val gap = (if (large) 7.dp else 4.dp).toPx()
    val outer = d + gap * 2f
    drawCircle(
        color = HALO.copy(alpha = 0.34f),
        radius = (outer - hairline) / 2f,
        center = centre,
        style = Stroke(width = hairline),
    )
}

// A whole number of dp, which is what the design rounds to — so a disc lands on the same edge on
// every density rather than a third of a pixel away from it.
private fun DrawScope.wholeDp(box: Dp, fraction: Float): Float =
    (box.value * fraction).roundToInt().dp.toPx()

// Per-channel sRGB, half-up, no gamma correction and no OkLab — the design mixes in the space CSS
// mixes in, and a perceptual blend here would shift every base colour.
internal fun Color.mixedWith(other: Color, amount: Float): Color = Color(
    red = channel(red, other.red, amount),
    green = channel(green, other.green, amount),
    blue = channel(blue, other.blue, amount),
)

internal fun channel(from: Float, to: Float, amount: Float): Float =
    ((from * 255f) + ((to * 255f) - (from * 255f)) * amount).roundToInt() / 255f

private fun Offset.dot(other: Offset): Float = x * other.x + y * other.y

// `sqrt(0.67² + 0.73²)` and `sqrt(0.60² + 0.66²)` — the distance from each gradient's centre to the
// farthest corner of the d×d box. Written out because they are the single most consequential
// numbers in this file; see the first note at the top.
private const val FARTHEST_CORNER_33_27 = 0.990858f
private const val FARTHEST_CORNER_40_34 = 0.891964f

private const val BAND_DEGREES = 8f
private val BAND_RADIANS = BAND_DEGREES * PI.toFloat() / 180f
private val TIDAL_RADIANS = 96f * PI.toFloat() / 180f

// CSS `conic-gradient(from 200deg, …)` plus the element's own `rotate(18deg)`.
private const val STORM_FROM_DEGREES = 218f
