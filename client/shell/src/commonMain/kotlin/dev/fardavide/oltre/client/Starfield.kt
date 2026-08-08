package dev.fardavide.oltre.client

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.design.core.OltreColors

// The back layer of every destination, and the one change in the depth pass that breaks a written
// rule — the background is meant to be flat, with no texture on it. It is also the only thing that
// makes the black read as space rather than as absence, which is the trade being made.
//
// Cards occlude it, so it is legible only in the outer margins and in the gaps between rows. That
// is the point rather than a limitation: a star seen *through* a card would be dust on a surface,
// and a star seen beside one is space behind it. It is the cheapest thing in the pass to delete if
// it stops earning its place.
@Composable
internal fun Starfield(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        STARS.forEach { star ->
            drawCircle(
                color = star.color.copy(alpha = star.alpha),
                radius = star.radius.toPx(),
                center = Offset(x = size.width * star.x, y = size.height * star.y),
            )
        }
    }
}

// Fractions of the box rather than offsets, so the field survives every window the app has to
// live in without a second table.
private data class Star(
    val x: Float,
    val y: Float,
    val radius: Dp,
    val color: Color,
    val alpha: Float,
)

// Hard-coded, and that is the requirement rather than laziness. A seeded field would still be a
// field that changes the day the seed or the generator does; a per-composition random one changes
// every Roborazzi run and turns every screenshot baseline in the repo red for no reason anybody
// could read off the diff. Twenty-six positions written down is the only version of this that a
// screenshot test can hold still.
//
// Two of the twenty-six are tinted and the rest are white — enough for the field to belong to this
// palette, few enough that it does not read as confetti.
private val STARS = listOf(
    Star(x = 0.14f, y = 0.13f, radius = 1.00.dp, color = Color.White, alpha = 0.62f),
    Star(x = 0.63f, y = 0.07f, radius = 0.75.dp, color = Color.White, alpha = 0.34f),
    Star(x = 0.82f, y = 0.19f, radius = 1.25.dp, color = OltreColors.crystal, alpha = 0.55f),
    Star(x = 0.31f, y = 0.27f, radius = 0.75.dp, color = Color.White, alpha = 0.26f),
    Star(x = 0.08f, y = 0.41f, radius = 0.75.dp, color = Color.White, alpha = 0.42f),
    Star(x = 0.47f, y = 0.37f, radius = 0.75.dp, color = Color.White, alpha = 0.22f),
    Star(x = 0.90f, y = 0.46f, radius = 1.00.dp, color = Color.White, alpha = 0.50f),
    Star(x = 0.22f, y = 0.57f, radius = 0.75.dp, color = Color.White, alpha = 0.30f),
    Star(x = 0.71f, y = 0.63f, radius = 1.00.dp, color = Color.White, alpha = 0.46f),
    Star(x = 0.39f, y = 0.72f, radius = 0.75.dp, color = Color.White, alpha = 0.24f),
    Star(x = 0.86f, y = 0.79f, radius = 0.75.dp, color = Color.White, alpha = 0.36f),
    Star(x = 0.12f, y = 0.86f, radius = 1.25.dp, color = OltreColors.deuterium, alpha = 0.50f),
    Star(x = 0.57f, y = 0.91f, radius = 0.75.dp, color = Color.White, alpha = 0.28f),
    Star(x = 0.27f, y = 0.04f, radius = 0.75.dp, color = Color.White, alpha = 0.30f),
    Star(x = 0.94f, y = 0.09f, radius = 0.75.dp, color = Color.White, alpha = 0.24f),
    Star(x = 0.52f, y = 0.21f, radius = 0.75.dp, color = Color.White, alpha = 0.34f),
    Star(x = 0.04f, y = 0.24f, radius = 0.75.dp, color = Color.White, alpha = 0.20f),
    Star(x = 0.76f, y = 0.33f, radius = 0.75.dp, color = Color.White, alpha = 0.30f),
    Star(x = 0.18f, y = 0.44f, radius = 0.75.dp, color = Color.White, alpha = 0.22f),
    Star(x = 0.61f, y = 0.51f, radius = 1.00.dp, color = Color.White, alpha = 0.40f),
    Star(x = 0.34f, y = 0.61f, radius = 0.75.dp, color = Color.White, alpha = 0.26f),
    Star(x = 0.95f, y = 0.67f, radius = 0.75.dp, color = Color.White, alpha = 0.32f),
    Star(x = 0.06f, y = 0.71f, radius = 0.75.dp, color = Color.White, alpha = 0.24f),
    Star(x = 0.66f, y = 0.84f, radius = 0.75.dp, color = Color.White, alpha = 0.30f),
    Star(x = 0.44f, y = 0.96f, radius = 0.75.dp, color = Color.White, alpha = 0.22f),
    Star(x = 0.88f, y = 0.94f, radius = 0.75.dp, color = Color.White, alpha = 0.28f),
)
