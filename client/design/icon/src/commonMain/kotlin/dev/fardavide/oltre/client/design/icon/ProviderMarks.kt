package dev.fardavide.oltre.client.design.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// **The two marks the game does not own**, and the only two shapes in this module that were not drawn
// for it. Both are the platform's own artwork and neither may be redrawn, recoloured or approximated
// — which is why they are transcribed as their published path data and parsed, rather than rebuilt
// out of arcs and lines the way every other glyph here is.
//
// `PathParser` takes the `d` attribute verbatim, so what is in this file is exactly what the vendor
// publishes and a reviewer can diff it against the source. Rebuilding Google's four-colour G out of
// primitives would be a redrawing, and redrawing it is what the brand guidelines forbid.

// Apple's mark, one colour, taking the ink of the button it sits on — black on the white fill the
// guidelines require.
@Composable
fun AppleMark(color: Color, modifier: Modifier = Modifier, size: Dp = 18.dp) {
    val leaf = rememberVectorPath(APPLE_LEAF)
    val body = rememberVectorPath(APPLE_BODY)
    Canvas(modifier = modifier.size(size)) {
        inViewBox {
            drawPath(leaf, color)
            drawPath(body, color)
        }
    }
}

// Google's G, in its four published colours and in no others. **No `color` parameter**, deliberately:
// a caller that could tint this could ship a monochrome G, which the brand guidelines forbid outright.
@Composable
fun GoogleMark(modifier: Modifier = Modifier, size: Dp = 17.dp) {
    val blue = rememberVectorPath(GOOGLE_BLUE)
    val green = rememberVectorPath(GOOGLE_GREEN)
    val yellow = rememberVectorPath(GOOGLE_YELLOW)
    val red = rememberVectorPath(GOOGLE_RED)
    Canvas(modifier = modifier.size(size)) {
        inViewBox {
            drawPath(blue, GOOGLE_BLUE_INK)
            drawPath(green, GOOGLE_GREEN_INK)
            drawPath(yellow, GOOGLE_YELLOW_INK)
            drawPath(red, GOOGLE_RED_INK)
        }
    }
}

// Parsed once per composition rather than on every frame. A path string is a constant, so this is a
// `remember` with no key — the shape cannot change while the composition lives.
@Composable
private fun rememberVectorPath(data: String): Path = remember(data) { PathParser().parsePathString(data).toPath() }

// Both marks are published in a 24-unit box and neither is drawn at 24dp, so every path is scaled by
// the same factor about the origin. One helper rather than the factor written six times.
private inline fun DrawScope.inViewBox(block: DrawScope.() -> Unit) {
    val factor = size.width / MARK_VIEWBOX
    scale(scaleX = factor, scaleY = factor, pivot = androidx.compose.ui.geometry.Offset.Zero) { block() }
}

private const val MARK_VIEWBOX = 24f

private const val APPLE_LEAF =
    "M13.1 5.3c.62-.79 1.04-1.86.92-2.96-.98.05-2.14.66-2.83 1.48-.62.72-1.11 1.85-.95 2.88 " +
        "1.11.09 2.2-.6 2.86-1.4z"

private const val APPLE_BODY =
    "M16.6 12.7c0-2.42 1.94-3.57 2.03-3.63-1.11-1.62-2.83-1.84-3.44-1.86-1.47-.15-2.87.86-3.61.86" +
        "-.75 0-1.9-.84-3.12-.82-1.6.03-3.08.93-3.9 2.36-1.66 2.88-.43 7.15 1.19 9.49.79 1.14 1.74 " +
        "2.42 2.99 2.37 1.2-.05 1.65-.78 3.1-.78 1.44 0 1.86.77 3.12.75 1.29-.02 2.11-1.17 2.9-2.32" +
        ".91-1.32 1.28-2.6 1.3-2.67-.03-.01-2.5-.96-2.56-3.75z"

private const val GOOGLE_BLUE =
    "M21.6 12.2c0-.72-.06-1.4-.18-2.06H12v3.9h5.36a4.6 4.6 0 0 1-1.99 3.02v2.53h3.21c1.88-1.73 " +
        "3.02-4.29 3.02-7.39z"

private const val GOOGLE_GREEN =
    "M12 22c2.7 0 4.96-.9 6.6-2.41l-3.22-2.53c-.9.6-2.05.96-3.38.96-2.6 0-4.8-1.76-5.58-4.12H3.09" +
        "v2.6A9.99 9.99 0 0 0 12 22z"

private const val GOOGLE_YELLOW = "M6.42 13.9a6.01 6.01 0 0 1 0-3.83V7.5H3.09a10 10 0 0 0 0 9l3.33-2.6z"

private const val GOOGLE_RED =
    "M12 5.96c1.47 0 2.79.51 3.83 1.5l2.85-2.85C16.95 3 14.7 2 12 2 8.13 2 4.79 4.22 3.09 7.5l3.33 " +
        "2.6C7.2 7.73 9.4 5.96 12 5.96z"

private val GOOGLE_BLUE_INK = Color(0xFF4285F4)
private val GOOGLE_GREEN_INK = Color(0xFF34A853)
private val GOOGLE_YELLOW_INK = Color(0xFFFBBC05)
private val GOOGLE_RED_INK = Color(0xFFEA4335)
