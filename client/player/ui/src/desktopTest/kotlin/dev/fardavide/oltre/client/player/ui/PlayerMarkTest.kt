package dev.fardavide.oltre.client.player.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// **The mark, measured rather than looked at.** `BellTest` is the model and the argument is the
// same: a 20dp baseline tells you the picture changed, and this tells you *where the ink is* — which
// is the half a 20dp picture is worst at.
//
// The second test here is the one worth having. The trajectory not touching the world is a decision,
// not a nicety: while it was being described in prose the mark was a world with a stalk, and the
// moment it was rendered it was a magnifier. A stroke that grows one unit closer would take it back
// there, no baseline would say why, and the diff would read as anti-aliasing.
class PlayerMarkTest {

    @Test
    fun `the mark stays inside the box it is drawn in`() {
        // The bound a 20dp picture cannot state. A glyph that overflowed its 24 units would be
        // clipped by the strip on one window width and not another, and the frame that caught it
        // would look like a rendering difference rather than a geometry error.
        val ink = inkedBounds { drawPlayerMark(unit = UNIT, dx = PAD, dy = PAD, color = Color.White) }

        assertInsideTheBox(ink)
    }

    @Test
    fun `the trajectory does not touch the world`() {
        // Sampled along the one ray where the two could meet — out of the world's centre, in the
        // direction the trajectory runs. The world's centre sits on the trajectory's own line, so
        // this ray crosses the limb and then reaches the segment's near end; anything between them
        // is the gap, and the gap is the whole difference between a departure and a magnifier.
        val runs = inkRunsAlongTheTrajectory()

        assertEquals(2, runs.size, "expected the limb and the trajectory as two separate runs, got $runs")
        val gap = runs[1].first - runs[0].second
        assertTrue(gap >= MINIMUM_GAP, "the trajectory is $gap units off the limb, which reads as a stalk")
    }

    @Test
    fun `the core dot is filled rather than stroked`() {
        // The one filled element the icon rules permit, and the difference is not decorative: a
        // stroked ring at 1.7 units reads as a second, smaller world at 20dp.
        val centre = sampleAt(x = DOT_CX, y = DOT_CY)

        assertTrue(centre, "the dot's middle is empty, so it is a ring rather than a dot")
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────

    // Where the ink starts and stops along the ray, in viewbox units from the world's centre. Each
    // pair is one contiguous run.
    private fun inkRunsAlongTheTrajectory(): List<Pair<Float, Float>> {
        val runs = mutableListOf<Pair<Float, Float>>()
        var start: Float? = null
        var previous = 0f
        var t = 0f
        while (t <= RAY_LENGTH) {
            val angle = -QUARTER_TURN / 2f
            val inked = sampleAt(
                x = WORLD_CX + t * cos(angle.toRadians()),
                y = WORLD_CY + t * sin(angle.toRadians()),
            )
            if (inked && start == null) start = t
            if (!inked && start != null) {
                runs += start to previous
                start = null
            }
            previous = t
            t += RAY_STEP
        }
        start?.let { runs += it to previous }
        return runs
    }

    private fun assertInsideTheBox(ink: InkBounds) {
        val low = (PAD + 1f) * UNIT
        val high = (PAD + MARK_VIEWBOX - 1f) * UNIT
        assertTrue(ink.minX >= low, "ink ${ink.minX} is left of the box")
        assertTrue(ink.minY >= low, "ink ${ink.minY} is above the box")
        assertTrue(ink.maxX <= high, "ink ${ink.maxX} is right of the box")
        assertTrue(ink.maxY <= high, "ink ${ink.maxY} is below the box")
    }

    // A single viewbox coordinate, asked of the rendered bitmap. Goes through the production
    // function rather than reassembling its arithmetic — a test that redrew the mark itself would be
    // asserting its own copy of the geometry.
    private fun sampleAt(x: Float, y: Float): Boolean =
        glyphPixels { drawPlayerMark(unit = UNIT, dx = PAD, dy = PAD, color = Color.White) }
            .inked(x = ((x + PAD) * UNIT).toInt(), y = ((y + PAD) * UNIT).toInt())

    private fun Float.toRadians(): Float = this * PI_OVER_180

    private companion object {

        // Ten pixels to the unit, so a 1.6-unit stroke is 16 pixels and a gap can be measured to
        // within a tenth of a unit.
        const val UNIT = 10f

        // Room around the box on every side, so overflow is measurable rather than clipped.
        const val PAD = 6f

        const val QUARTER_TURN = 90f
        const val PI_OVER_180 = 0.017453292f

        const val RAY_LENGTH = 12f
        const val RAY_STEP = 0.05f

        // The limb's stroke ends at 5.5 units out and the trajectory's round cap begins at 6.55, so
        // the drawn gap is about 1.05. Half of it is loose enough to survive a tuning pass and tight
        // enough to catch a stalk.
        const val MINIMUM_GAP = 0.5f
    }
}
