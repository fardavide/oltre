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
import dev.fardavide.oltre.client.tilt.domain.Tilt

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
// **The field now moves on two inputs, and the second one costs a sentence the first did not.**
// 0.4.0 wrote here, and in `decisions.md`, that the parallax is not an animation because it "has no
// duration, no clock and no running state". Of the scroll term that is still exactly true. Of the
// tilt term it is **not**: `TiltMonitor` keeps two exponential averages between samples, so there
// is running state behind this, and each average has a time constant. Saying otherwise would be a
// dodge, and the 0.4.0 entry was careful to say it was not making one.
//
// What survives — and what the rule was actually protecting — is the reason. The no-animation rule
// exists so that a game whose whole premise is that it progresses while closed never draws anything
// a player could read as *it is happening now*. Nothing here loops, nothing repeats, and nothing
// advances on its own: **put the phone down and the sky stops dead.** It is the only movement in
// the app that is a readout of the player's own hand rather than of anything the game is doing, and
// a hand is not a clock. The time constants are a filter on an input — the same kind of thing as
// the fling on the list this sits behind — rather than a duration anything is playing for.
//
// Both inputs are lambdas rather than values on purpose: read inside the draw scope they make a
// scroll or a lean a *redraw*, where a parameter would make either one a recomposition of the whole
// frame on every pixel of a drag and every sample of a sensor.
@Composable
internal fun Starfield(
    scrollOffset: () -> Float,
    tilt: () -> Tilt = { Tilt.NONE },
    modifier: Modifier = Modifier,
) {
    // **Clipped, and it is load-bearing rather than tidy.** Star `y` runs −0.08..1.08 so that a
    // translated plane never exposes an empty edge, and Compose does not clip a child to its layout
    // bounds — so without this the two rows of stars outside 0..1 are drawn over the resource rail
    // above and the tab bar below. Space showing through a surface is the one thing the opaque
    // fills in this app exist to prevent, and the chrome is a surface.
    Canvas(modifier = modifier.fillMaxSize().clipToBounds()) {
        val offset = scrollOffset()
        val lean = tilt()
        val travel = TILT_TRAVEL.toPx()
        // Back to front, so the near plane's brighter stars are the ones that survive an overlap.
        SKY_PLANES.forEach { plane ->
            // **The lean reuses each plane's own parallax factor rather than introducing three more
            // numbers.** The planes already have a settled spread — 0.12, 0.30, 0.58 — chosen so
            // that the gap between any two of them reads as distance, and a second table of tilt
            // weights would be a second opinion about the same depth that could only ever drift out
            // of step with the first. One distance in dp, scaled by what each plane already is.
            val leanX = lean.x * plane.parallax * travel
            val leanY = lean.y * plane.parallax * travel
            // **Wrapped rather than translated**, which a plain shift is not enough for. The near
            // plane keeps 58% of the list's speed, so one viewport of scroll carries it more than
            // half a screen up and leaves the bottom of the destination with no stars in it at all
            // — an empty sky under a list that is still scrolling. Taking the shift modulo the
            // plane's height and drawing each star twice, one height apart, makes the field tile:
            // whatever leaves the top comes back at the bottom. The lean folds into the same shift
            // and so tiles with it for free.
            val shift = (-offset * plane.parallax + leanY).mod(size.height)
            // The horizontal wrap has to be earned separately, because the star table has no margin
            // across: `x` runs 0.0004..0.9845, edge to edge, where `y` was given bleed on purpose.
            // So a sideways lean would drag a bare strip in at whichever edge it came from, and the
            // fix is the one the vertical shift already uses — take it modulo the width and draw
            // each star again one width across.
            //
            // **Guarded on the lean being exactly zero, and that guard is what protects forty-one
            // screenshot baselines.** Desktop has no motion sensor and reports `Tilt.NONE` forever,
            // so on the machine every baseline is recorded on this branch is not taken, no `mod` is
            // applied to `x`, and the draw calls are the same two per star that were recorded before
            // any of this existed. Without it, folding an unchanged `x` through `mod` would come
            // back a fraction of a pixel different — small enough to pass the verifier and quite
            // large enough to be a drift nobody could read off a diff.
            val wraps = leanX != 0f
            plane.stars.forEach { star ->
                val colour = star.color.copy(alpha = star.alpha)
                val radius = star.radius.toPx()
                // The table's y runs −0.08..1.08 so that an un-wrapped plane had bleed at both
                // edges; folded into 0..1 it tiles seamlessly instead, and the two rows that used
                // to be the bleed become the two rows either side of the seam.
                val y = size.height * ((star.y + 1f).mod(1f)) + shift
                if (wraps) {
                    val x = (size.width * star.x + leanX).mod(size.width)
                    drawCircle(color = colour, radius = radius, center = Offset(x = x, y = y))
                    drawCircle(color = colour, radius = radius, center = Offset(x = x, y = y - size.height))
                    drawCircle(color = colour, radius = radius, center = Offset(x = x - size.width, y = y))
                    drawCircle(
                        color = colour,
                        radius = radius,
                        center = Offset(x = x - size.width, y = y - size.height),
                    )
                } else {
                    val x = size.width * star.x
                    drawCircle(color = colour, radius = radius, center = Offset(x = x, y = y))
                    drawCircle(color = colour, radius = radius, center = Offset(x = x, y = y - size.height))
                }
            }
        }
    }
}

// How far the nearest plane would travel at a full lean, before its parallax factor is applied — so
// the near plane reaches 0.58 of this, the far one 0.12, and the spread between them is the depth.
//
// **A feel decision made without a device, and the first one to move when there is one.** Fourteen
// device-independent pixels of travel on the nearest plane is deliberately less than an eighth of
// what a single screen of scrolling already moves it: the lean is meant to be an accent on a field
// that is mostly driven by the list in front of it, and something a player notices only after a few
// sessions. See `TiltMonitor`'s constants, which carry the same caveat for the same reason.
private val TILT_TRAVEL: Dp = 24.dp

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
