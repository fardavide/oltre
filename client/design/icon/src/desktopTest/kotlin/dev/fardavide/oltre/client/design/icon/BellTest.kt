package dev.fardavide.oltre.client.design.icon

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// **The first test of a glyph in this repository, and the thing that had to change first was the
// code.** Until the two bells were made to share a painter, the drawing lived inside a
// `Canvas { }` lambda — unreachable except by composing a screen, which is why the entire icon set
// scored zero under the unit pass and every mark in it was held up by whichever feature's baseline
// happened to render it, at 17dp, inside a card.
//
// `drawBell` is a plain `DrawScope` function now, so this hands it a bitmap and measures the ink.
// What that is good for is exactly what a 17dp screenshot is worst at: **where the marks are**. A
// baseline tells you the picture changed; this tells you the bell left its box, or that the bell
// behind stopped being behind.
//
// `watch_square.png` is still the other half and neither replaces the other — a bound is not a
// likeness, and nothing here would notice a bell that was upside down.
class BellTest {

    @Test
    fun `a placement at scale one and no offset is the identity`() {
        // The property the whole refactor rests on: `WatchBell` is drawn through the same arithmetic
        // as the stack, so if this were not exact its baselines would have moved when the painter
        // arrived. `12 + (v - 12)` is `v`.
        val place = BellPlacement(unit = UNIT, scale = 1f, dx = 0f, dy = 0f)

        for (v in listOf(0f, 3.9f, 12f, 20.1f, 24f)) {
            assertEquals(v * UNIT, place.x(v), 0.001f, "x($v)")
            assertEquals(v * UNIT, place.y(v), 0.001f, "y($v)")
        }
        assertEquals(5.8f * UNIT, place.length(5.8f), 0.001f)
    }

    @Test
    fun `a placement scales about the centre of the box rather than its corner`() {
        // Which is what lets the stack shrink a bell without also sliding it: the middle of the box
        // is the one point every scale leaves alone.
        val half = BellPlacement(unit = UNIT, scale = 0.5f, dx = 0f, dy = 0f)

        assertEquals(12f * UNIT, half.x(12f), 0.001f, "the centre must not move")
        assertEquals(12f * UNIT, half.y(12f), 0.001f, "the centre must not move")
        // And a point a unit out lands half a unit out.
        assertEquals(12.5f * UNIT, half.x(13f), 0.001f)
    }

    @Test
    fun `an offset moves every coordinate by the same amount`() {
        val shifted = BellPlacement(unit = UNIT, scale = 1f, dx = 3f, dy = -2f)

        assertEquals(15f * UNIT, shifted.x(12f), 0.001f)
        assertEquals(10f * UNIT, shifted.y(12f), 0.001f)
        // A length is a radius rather than a position, so neither offset reaches it.
        assertEquals(4f * UNIT, shifted.length(4f), 0.001f)
    }

    @Test
    fun `the bell stays inside the box it is drawn in`() {
        // The bound a screenshot cannot state. A glyph that overflowed its 24 units would be clipped
        // by the 29dp square around it on one screen and not on another, and the frame that caught it
        // would look like a rendering difference rather than like a geometry error.
        assertInsideTheBox(inkedBounds { oneBell() })
    }

    @Test
    fun `the two bells stay inside the box the one bell does`() {
        // The one most likely to break: the bell behind is placed by two offsets added together, so
        // a nudge to either sends its rim over the right-hand edge before anything else complains.
        assertInsideTheBox(inkedBounds { twoBells() })
    }

    @Test
    fun `the second bell is drawn and it is drawn up and to the right`() {
        // What the glyph is *for*, as a measurement rather than as a likeness: there is more ink than
        // one bell puts down, and it reaches further right than one bell reaches. The direction is the
        // half worth pinning — a sign flipped on `BACK_DX` would tuck the second bell behind the first
        // where nothing but an eye would find it.
        val one = inkedBounds { oneBell() }
        val two = inkedBounds { twoBells() }

        assertTrue(two.pixels > one.pixels, "the second bell drew nothing: ${two.pixels} against ${one.pixels}")
        assertTrue(two.maxX > one.maxX, "the second bell is not to the right: ${two.maxX} against ${one.maxX}")
        // Not "higher than the single bell", which it is not — that one keeps its crown at full size
        // and reaches highest of anything here. What is asserted is that the shoulder behind gets up
        // among it rather than sitting down beside it.
        assertTrue(two.minY < one.minY + UNIT, "the second bell is not above: ${two.minY} against ${one.minY}")
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────

    // Both glyphs' ink must sit inside the 24-unit box with a little air — the marks reach about 2.4
    // units from the top and 3 from the left, so a bound of 1 unit is loose enough to survive a tuning
    // pass and tight enough to catch a mark that has left the box.
    private fun assertInsideTheBox(ink: InkBounds) {
        val low = (PAD + 1f) * UNIT
        val high = (PAD + BELL_VIEWBOX - 1f) * UNIT
        assertTrue(ink.minX >= low, "ink ${ink.minX} is left of the box")
        assertTrue(ink.minY >= low, "ink ${ink.minY} is above the box")
        assertTrue(ink.maxX <= high, "ink ${ink.maxX} is right of the box")
        assertTrue(ink.maxY <= high, "ink ${ink.maxY} is below the box")
    }

    // The two glyphs as the composables draw them, padded away from the edges. Both go through the
    // production functions rather than reassembling their placements here — a test that did that
    // would be asserting its own arithmetic.
    private fun DrawScope.oneBell() {
        drawBell(
            place = BellPlacement(unit = UNIT, scale = 1f, dx = PAD, dy = PAD),
            color = Color.White,
            stroke = bellStroke(UNIT),
        )
    }

    private fun DrawScope.twoBells() {
        drawStackedBells(unit = UNIT, dx = PAD, dy = PAD, color = Color.White)
    }

    // Renders into a bitmap larger than the box, so ink that leaves the box lands on canvas and can
    // be measured instead of being silently clipped away.
    private fun inkedBounds(draw: DrawScope.() -> Unit): InkBounds {
        val side = ((BELL_VIEWBOX + 2 * PAD) * UNIT).toInt()
        val bitmap = ImageBitmap(side, side)
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(bitmap),
            size = Size(side.toFloat(), side.toFloat()),
            block = draw,
        )
        val pixels = bitmap.toPixelMap()
        var minX = side; var minY = side; var maxX = -1; var maxY = -1; var count = 0
        for (y in 0 until side) {
            for (x in 0 until side) {
                // Anything the anti-aliasing left a trace of counts, so a bound cannot be squeaked
                // past by a stroke that only just crosses the line.
                if (pixels[x, y].alpha <= 0.02f) continue
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

    private class InkBounds(val minX: Int, val minY: Int, val maxX: Int, val maxY: Int, val pixels: Int)

    private companion object {
        // Ten pixels to the unit, so a 1.7-unit stroke is 17 pixels and a bound can be asserted to
        // within a tenth of a unit.
        const val UNIT = 10f

        // Room around the box on every side, so overflow is measurable rather than clipped.
        const val PAD = 6f
    }
}
