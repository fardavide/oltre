package dev.fardavide.oltre.client.player.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// **A cog has to read as a cog, and the first one drawn here did not.** Six strokes standing off a
// ring at stroke 1.6 is a sun, not a fastener — which is what it looked like the moment it was
// rendered and not once while it was being specified. What fixed it was a rim to hang the teeth on
// and a heavier stroke on the teeth than on the circles, and the second test below is what stops
// either of those drifting back.
class SettingsGlyphTest {

    @Test
    fun `the gear stays inside the box it is drawn in`() {
        val ink = inkedBounds { drawSettingsGlyph(unit = UNIT, dx = PAD, dy = PAD, color = Color.White) }

        assertInsideTheBox(ink)
    }

    @Test
    fun `the gear has six teeth`() {
        // Counted where only teeth are: a ring of samples at 7 units out clears the rim's outer edge
        // at 6.4 and sits inside the teeth's reach of 8.9. Six arcs of ink separated by six of
        // background is the whole claim, and it is the one a sun would fail differently — a sun's
        // rays start further out, so this radius would find nothing at all.
        assertEquals(6, inkArcsAt(TOOTH_SAMPLE_RADIUS), "the teeth are not six separated marks")
    }

    @Test
    fun `the rim is a closed ring`() {
        // The rim is what makes the teeth read as teeth rather than as rays. Sampled on the rim's own
        // radius, the ink is unbroken all the way round — one arc, not six.
        assertEquals(1, inkArcsAt(RIM_RADIUS), "the rim is broken, so the teeth have nothing to hang on")
    }

    @Test
    fun `the hub is a hole rather than a dot`() {
        // A filled centre would make it a pressed button; the glyph is a cog and a cog has a bore.
        val centre = glyphPixels { drawSettingsGlyph(unit = UNIT, dx = PAD, dy = PAD, color = Color.White) }
            .inked(x = ((GEAR_CENTRE + PAD) * UNIT).toInt(), y = ((GEAR_CENTRE + PAD) * UNIT).toInt())

        assertTrue(!centre, "the hub is filled, so the glyph reads as a button rather than a cog")
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────

    // How many separate arcs of ink a circle of the given radius crosses. Wrapping is handled by
    // rotating the samples so the run starting at zero degrees is joined to the one ending at 360.
    private fun inkArcsAt(radius: Float): Int {
        val pixels = glyphPixels { drawSettingsGlyph(unit = UNIT, dx = PAD, dy = PAD, color = Color.White) }
        val samples = (0 until FULL_TURN_SAMPLES).map { step ->
            val angle = step * (FULL_TURN / FULL_TURN_SAMPLES) * PI_OVER_180
            pixels.inked(
                x = ((GEAR_CENTRE + radius * cos(angle) + PAD) * UNIT).toInt(),
                y = ((GEAR_CENTRE + radius * sin(angle) + PAD) * UNIT).toInt(),
            )
        }
        if (samples.all { it }) return 1
        // Start counting from a gap, so the first run is never a partial one wrapped from the end.
        val offset = samples.indexOfFirst { !it }
        val rotated = samples.drop(offset) + samples.take(offset)
        return rotated.zipWithNext().count { (previous, current) -> !previous && current }
    }

    private fun assertInsideTheBox(ink: InkBounds) {
        val low = (PAD + 1f) * UNIT
        val high = (PAD + MARK_VIEWBOX - 1f) * UNIT
        assertTrue(ink.minX >= low, "ink ${ink.minX} is left of the box")
        assertTrue(ink.minY >= low, "ink ${ink.minY} is above the box")
        assertTrue(ink.maxX <= high, "ink ${ink.maxX} is right of the box")
        assertTrue(ink.maxY <= high, "ink ${ink.maxY} is below the box")
    }

    private companion object {

        const val UNIT = 10f
        const val PAD = 6f

        const val FULL_TURN = 360f
        const val FULL_TURN_SAMPLES = 720
        const val PI_OVER_180 = 0.017453292f

        // Clear of the rim's outer edge (5.6 + 0.8 = 6.4) and inside the teeth's reach (7.9 + 1.0).
        const val TOOTH_SAMPLE_RADIUS = 7f

        // On the rim's own centreline, where its stroke is unbroken.
        const val RIM_RADIUS = 5.6f
    }
}
