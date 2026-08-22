package dev.fardavide.oltre.client.player.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

// Rendering a `DrawScope` function into a bitmap and asking where the ink landed. Lifted in shape
// from `:client:design:icon`'s `BellTest`, which argued the approach; it lives in its own file here
// because two glyphs share it and a private copy in each would be the thing the bell's own painter
// refactor was about.
//
// The bitmap is larger than the 24-unit box on every side, so a mark that leaves the box lands on
// canvas and can be measured rather than being silently clipped away.
internal class GlyphPixels(private val pixels: (Int, Int) -> Float, private val side: Int) {

    // Anything the anti-aliasing left a trace of counts, so a bound cannot be squeaked past by a
    // stroke that only just crosses the line.
    fun inked(x: Int, y: Int): Boolean =
        x in 0 until side && y in 0 until side && pixels(x, y) > INK_THRESHOLD

    fun bounds(): InkBounds {
        var minX = side
        var minY = side
        var maxX = -1
        var maxY = -1
        var count = 0
        for (y in 0 until side) {
            for (x in 0 until side) {
                if (!inked(x, y)) continue
                count++
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }
        }
        check(count > 0) { "nothing was drawn" }
        return InkBounds(minX = minX, minY = minY, maxX = maxX, maxY = maxY, pixels = count)
    }
}

internal class InkBounds(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int, val pixels: Int)

internal fun glyphPixels(unit: Float = 10f, pad: Float = 6f, draw: DrawScope.() -> Unit): GlyphPixels {
    val side = ((MARK_VIEWBOX + 2 * pad) * unit).toInt()
    val bitmap = ImageBitmap(side, side)
    CanvasDrawScope().draw(
        density = Density(1f),
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(bitmap),
        size = Size(side.toFloat(), side.toFloat()),
        block = draw,
    )
    val map = bitmap.toPixelMap()
    return GlyphPixels(pixels = { x, y -> map[x, y].alpha }, side = side)
}

internal fun inkedBounds(draw: DrawScope.() -> Unit): InkBounds = glyphPixels(draw = draw).bounds()

private const val INK_THRESHOLD = 0.02f
