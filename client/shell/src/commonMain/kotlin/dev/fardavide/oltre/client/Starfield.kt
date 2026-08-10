package dev.fardavide.oltre.client

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.core.OltreColors

// The back layer of every destination, and the one change in the depth pass that broke a written
// rule — the background is meant to be flat, with no texture on it. It is also the only thing that
// makes the black read as space rather than as absence, which is the trade being made.
//
// The Sky pass extends that same exception rather than taking a second one: twenty-six stars on one
// plane become a hundred and one on three, and the three move against each other as the list under
// them scrolls. What it buys is the thing a single plane could never say — that the field has depth
// — and it buys it for nothing, because a plane that translates is still a plane of circles.
//
// Cards occlude it, so it is legible only in the outer margins and in the gaps between rows. That
// is the point rather than a limitation: a star seen *through* a card would be dust on a surface,
// and a star seen beside one is space behind it.
//
// **The parallax is not an animation.** It has no duration, no clock and no running state; it is a
// function of the scroll position, exactly as the position of the list itself is. Nothing here
// implies that time is passing, which is the whole of what the no-animation rule was protecting.
//
// `scrollOffset` is a lambda rather than a value on purpose: read inside the draw scope it makes a
// scroll a redraw, where a parameter would make it a recomposition of the whole frame on every
// pixel of a drag.
@Composable
internal fun Starfield(scrollOffset: () -> Float, modifier: Modifier = Modifier) {
    // **Clipped, and it is load-bearing rather than tidy.** Star `y` runs −0.08..1.08 so that a
    // translated plane never exposes an empty edge, and Compose does not clip a child to its layout
    // bounds — so without this the two rows of stars outside 0..1 are drawn over the resource rail
    // above and the tab bar below. Space showing through a surface is the one thing the opaque
    // fills in this app exist to prevent, and the chrome is a surface.
    Canvas(modifier = modifier.fillMaxSize().clipToBounds()) {
        val offset = scrollOffset()
        // Back to front, so the near plane's brighter stars are the ones that survive an overlap.
        SKY_PLANES.forEach { plane ->
            // **Wrapped rather than translated**, which a plain shift is not enough for. The near
            // plane keeps 58% of the list's speed, so one viewport of scroll carries it more than
            // half a screen up and leaves the bottom of the destination with no stars in it at all
            // — an empty sky under a list that is still scrolling. Taking the shift modulo the
            // plane's height and drawing each star twice, one height apart, makes the field tile:
            // whatever leaves the top comes back at the bottom, and it is still a pure function of
            // the scroll offset with no clock in it.
            val shift = (-offset * plane.parallax).mod(size.height)
            plane.stars.forEach { star ->
                val colour = star.color.copy(alpha = star.alpha)
                val radius = star.radius.toPx()
                val x = size.width * star.x
                // The table's y runs −0.08..1.08 so that an un-wrapped plane had bleed at both
                // edges; folded into 0..1 it tiles seamlessly instead, and the two rows that used
                // to be the bleed become the two rows either side of the seam.
                val y = size.height * ((star.y + 1f).mod(1f)) + shift
                drawCircle(color = colour, radius = radius, center = Offset(x = x, y = y))
                drawCircle(color = colour, radius = radius, center = Offset(x = x, y = y - size.height))
            }
        }
    }
}

// Fractions of the box rather than offsets, so the field survives every window the app has to live
// in without a second table. `y` runs −0.08..1.08 rather than 0..1 precisely so that a plane which
// has been translated never exposes an empty edge at the top or the bottom of the destination.
private data class Star(
    val x: Float,
    val y: Float,
    val radius: Dp,
    val color: Color,
    val alpha: Float,
)

// How much of the destination's scroll each plane takes. The spread is what separates them: at 0.12
// the far plane is almost fixed and at 0.58 the near one keeps well over half of the list's speed,
// so the gap between any two of them is wide enough to read as distance rather than as drift.
private data class StarPlane(val stars: List<Star>, val parallax: Float)

// Hard-coded, and that is the requirement rather than laziness. A seeded field would still be a
// field that changes the day the seed or the generator does; a per-composition random one changes
// every Roborazzi run and turns every screenshot baseline in the repo red for no reason anybody
// could read off the diff. A hundred and one positions written down is the only version of this
// that a screenshot test can hold still.
//
// The table below is generated from the accepted design reference and is meant to be replaced
// wholesale rather than edited by hand. Columns are `x, y, radius, colour, alpha`. Roughly one star
// in twenty is crystal and one in thirty deuterium — enough for the field to belong to this
// palette, few enough that it does not read as confetti.
private val white = Color.White
private val crystal = OltreColors.crystal
private val deuterium = OltreColors.deuterium

private val FAR_PLANE = StarPlane(
    parallax = 0.12f,
    stars = listOf(
        Star(0.8826f, 0.9420f, 1.13.dp, white, 0.25f),
        Star(0.6001f, 0.8940f, 0.83.dp, white, 0.27f),
        Star(0.4527f, 0.2608f, 1.06.dp, white, 0.21f),
        Star(0.8120f, 0.9275f, 1.35.dp, white, 0.14f),
        Star(0.7035f, 0.1326f, 1.34.dp, white, 0.26f),
        Star(0.9152f, 0.9977f, 1.03.dp, white, 0.30f),
        Star(0.9037f, 0.5868f, 1.29.dp, white, 0.26f),
        Star(0.5782f, 0.0513f, 0.92.dp, white, 0.33f),
        Star(0.0189f, 0.3249f, 1.25.dp, white, 0.17f),
        Star(0.7326f, 0.5175f, 1.14.dp, white, 0.15f),
        Star(0.5285f, -0.0628f, 0.84.dp, white, 0.31f),
        Star(0.7418f, 0.6468f, 1.05.dp, white, 0.22f),
        Star(0.1945f, 0.3974f, 1.23.dp, white, 0.21f),
        Star(0.5187f, 0.9941f, 1.01.dp, white, 0.23f),
        Star(0.0967f, 0.5991f, 1.20.dp, white, 0.21f),
        Star(0.8637f, 0.7884f, 1.02.dp, crystal, 0.34f),
        Star(0.7784f, -0.0082f, 1.32.dp, crystal, 0.14f),
        Star(0.2300f, 0.4676f, 0.84.dp, white, 0.18f),
        Star(0.3938f, 0.9618f, 1.04.dp, white, 0.21f),
        Star(0.6130f, 0.3293f, 0.98.dp, white, 0.18f),
        Star(0.5952f, 0.0486f, 0.84.dp, white, 0.24f),
        Star(0.7982f, 0.4113f, 0.96.dp, white, 0.20f),
        Star(0.3837f, 0.2745f, 1.14.dp, crystal, 0.19f),
        Star(0.7618f, 0.8192f, 1.21.dp, white, 0.24f),
        Star(0.2740f, 0.9251f, 0.93.dp, white, 0.33f),
        Star(0.4437f, 0.5759f, 1.15.dp, white, 0.26f),
        Star(0.4262f, 0.5455f, 0.96.dp, white, 0.33f),
        Star(0.0314f, 0.4751f, 0.82.dp, crystal, 0.20f),
        Star(0.6071f, 0.6930f, 1.24.dp, white, 0.22f),
        Star(0.8135f, 0.4804f, 1.37.dp, white, 0.34f),
        Star(0.4183f, 1.0645f, 0.86.dp, white, 0.21f),
        Star(0.2864f, 0.4110f, 0.84.dp, white, 0.33f),
        Star(0.4850f, -0.0076f, 1.17.dp, deuterium, 0.19f),
        Star(0.9845f, 0.0596f, 1.10.dp, white, 0.27f),
        Star(0.7969f, 0.8207f, 1.25.dp, white, 0.23f),
        Star(0.2760f, 0.8347f, 0.82.dp, deuterium, 0.25f),
        Star(0.5997f, 0.7919f, 0.83.dp, white, 0.31f),
        Star(0.1024f, 0.4469f, 1.15.dp, white, 0.26f),
        Star(0.8714f, 0.5595f, 1.26.dp, white, 0.21f),
        Star(0.9426f, 0.8625f, 0.95.dp, white, 0.22f),
        Star(0.2018f, 0.1456f, 1.20.dp, crystal, 0.22f),
        Star(0.2319f, 0.7699f, 0.97.dp, white, 0.25f),
        Star(0.4720f, 0.5827f, 1.34.dp, white, 0.22f),
        Star(0.2798f, 0.6531f, 0.85.dp, white, 0.30f),
        Star(0.1627f, 1.0427f, 1.39.dp, white, 0.29f),
        Star(0.7009f, 0.4590f, 0.91.dp, white, 0.27f),
        Star(0.6246f, 0.3589f, 0.92.dp, white, 0.18f),
        Star(0.6727f, 0.9185f, 0.85.dp, white, 0.16f),
        Star(0.6365f, 0.4250f, 1.32.dp, crystal, 0.16f),
        Star(0.2978f, 0.9814f, 1.30.dp, white, 0.15f),
        Star(0.6738f, 0.3517f, 1.37.dp, white, 0.18f),
        Star(0.4951f, 1.0166f, 1.01.dp, white, 0.20f),
        Star(0.9125f, -0.0544f, 1.15.dp, white, 0.16f),
        Star(0.0004f, 0.2513f, 1.19.dp, white, 0.24f),
        Star(0.6351f, 0.2374f, 0.82.dp, white, 0.28f),
        Star(0.9221f, 0.2093f, 0.95.dp, crystal, 0.24f),
        Star(0.3753f, 0.5227f, 1.35.dp, white, 0.29f),
        Star(0.6630f, 0.3266f, 1.22.dp, white, 0.27f),
        Star(0.1398f, 0.5128f, 1.37.dp, white, 0.18f),
        Star(0.8075f, 0.5893f, 1.27.dp, white, 0.18f),
        Star(0.8576f, -0.0375f, 0.97.dp, white, 0.23f),
        Star(0.6607f, 0.0606f, 0.85.dp, white, 0.20f),
        Star(0.5592f, 0.6446f, 1.01.dp, white, 0.33f),
        Star(0.5943f, 0.6772f, 1.20.dp, white, 0.17f),
    ),
)

private val MID_PLANE = StarPlane(
    parallax = 0.30f,
    stars = listOf(
        Star(0.3075f, 0.7812f, 1.43.dp, white, 0.43f),
        Star(0.7709f, 0.4499f, 1.64.dp, white, 0.37f),
        Star(0.0047f, 0.4009f, 1.45.dp, white, 0.55f),
        Star(0.3285f, -0.0709f, 1.33.dp, white, 0.43f),
        Star(0.0022f, 0.4133f, 1.83.dp, white, 0.48f),
        Star(0.2385f, 1.0021f, 1.24.dp, white, 0.40f),
        Star(0.8317f, 0.7988f, 1.34.dp, white, 0.33f),
        Star(0.4317f, 0.7856f, 1.21.dp, deuterium, 0.45f),
        Star(0.8226f, 0.5683f, 1.11.dp, white, 0.54f),
        Star(0.1165f, 0.6436f, 1.49.dp, white, 0.36f),
        Star(0.4258f, 0.1672f, 1.37.dp, white, 0.44f),
        Star(0.0596f, 0.2595f, 1.89.dp, white, 0.39f),
        Star(0.3975f, 0.4942f, 1.29.dp, white, 0.54f),
        Star(0.0588f, 0.7659f, 1.69.dp, white, 0.40f),
        Star(0.2007f, 0.6711f, 1.63.dp, white, 0.31f),
        Star(0.1651f, 0.1237f, 1.83.dp, white, 0.47f),
        Star(0.1565f, 0.2054f, 1.66.dp, white, 0.36f),
        Star(0.6686f, 0.4057f, 1.33.dp, white, 0.53f),
        Star(0.2595f, 0.9308f, 1.44.dp, white, 0.37f),
        Star(0.1546f, 0.8180f, 1.85.dp, deuterium, 0.45f),
        Star(0.6573f, 0.9512f, 1.83.dp, white, 0.51f),
        Star(0.4252f, 0.0321f, 1.73.dp, white, 0.37f),
        Star(0.8328f, 0.3825f, 1.67.dp, white, 0.48f),
        Star(0.3183f, 0.9823f, 1.14.dp, white, 0.51f),
        Star(0.4426f, 0.5236f, 1.20.dp, white, 0.35f),
        Star(0.6783f, 0.2400f, 1.14.dp, white, 0.49f),
    ),
)

private val NEAR_PLANE = StarPlane(
    parallax = 0.58f,
    stars = listOf(
        Star(0.8460f, 0.9808f, 1.86.dp, crystal, 0.65f),
        Star(0.5103f, 0.5219f, 1.66.dp, white, 0.59f),
        Star(0.1564f, 0.1884f, 2.29.dp, white, 0.74f),
        Star(0.2074f, 0.9768f, 2.19.dp, white, 0.64f),
        Star(0.3721f, 0.7468f, 1.84.dp, white, 0.77f),
        Star(0.7103f, 0.3094f, 2.08.dp, white, 0.64f),
        Star(0.5005f, -0.0734f, 2.08.dp, white, 0.78f),
        Star(0.2852f, 0.5222f, 2.34.dp, white, 0.52f),
        Star(0.1789f, 0.7033f, 1.99.dp, white, 0.56f),
        Star(0.8886f, 0.3103f, 2.51.dp, white, 0.62f),
        Star(0.2936f, 0.2220f, 2.02.dp, white, 0.60f),
    ),
)

private val SKY_PLANES = listOf(FAR_PLANE, MID_PLANE, NEAR_PLANE)
