package dev.fardavide.oltre.client.player.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.fardavide.oltre.protocol.MarkPreset
import dev.fardavide.oltre.protocol.PlayerMark
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// **Six drawings and no baseline apiece, which is a trade rather than a saving.** A 20dp capture of a
// mark can say the picture moved; it cannot say *where the ink is*, and every defect this set is
// prone to reads as anti-aliasing at that size — an arc that has drifted into the limb it is meant
// to divide, a dot that has come off the curve it is meant to sit on, two arcs that have closed on
// each other until the nest is a blob. `PlayerMarkTest` made that argument once for `THRESHOLD`
// (whose ray walk stays there rather than being copied here) and this is it made five more times.
//
// One assertion per mark, and each one is the sentence the frame wrote about that mark rather than a
// generic health check. What they share is the box rule, which is the only thing every mark in the
// set has in common.
class MarkPresetTest {

    @Test
    fun `every mark stays inside the box it is drawn in`() {
        // Looped rather than written out six times, because the claim is about the *set*: a seventh
        // preset added without a drawing that fits would fail here on the day it is added, which is
        // the whole reason the dispatcher is exhaustive.
        for (preset in MarkPreset.entries) {
            val ink = markPixels { drawPreset(preset) }.bounds()

            assertInsideTheBox(ink, what = preset.name)
        }
    }

    @Test
    fun `the terminator's inner arc meets the limb only at the poles`() {
        // The arc's vertical radius *is* the limb's radius, which is what makes the two meet at the
        // top and the bottom and is the whole of the shape: a day-night boundary starts and ends on
        // the world's own edge. What it must not do is graze that edge anywhere in between, because
        // an arc that ran along the limb for any distance would draw a thick rim rather than a
        // terminator and the mark would read as a heavy ring at 20dp.
        //
        // Sampled over the arc's middle half rather than end to end, and the reason is arithmetic
        // rather than convenience: the clearance goes to nothing *at* the poles by construction, so
        // a sample twelve degrees off one of them sits 0.11 units inside and always will. The middle
        // half is the widest span the frame's 1.2 can be claimed over — at each end of it the arc is
        // 1.41 units clear, so the bound has room to catch a drift without being met exactly.
        val from = TERMINATOR_ARC_START + TERMINATOR_ARC_SWEEP / 4
        val span = TERMINATOR_ARC_SWEEP / 2

        for (step in 0 until ARC_SAMPLES) {
            val angle = (from + span * step / (ARC_SAMPLES - 1)) * RADIANS_PER_DEGREE
            val fromCentre = hypot(TERMINATOR_ARC_RX * cos(angle), TERMINATOR_LIMB_R * sin(angle))

            assertTrue(
                TERMINATOR_LIMB_R - fromCentre >= POLE_CLEARANCE,
                "the terminator is ${TERMINATOR_LIMB_R - fromCentre} inside the limb at $angle rad",
            )
        }
    }

    @Test
    fun `the aphelion dot sits on the ellipse at its right vertex`() {
        // The name is the assertion. An aphelion is the far end of an orbit, so the dot belongs *on*
        // the ellipse and at the point furthest from its centre — a dot floating off the curve, or
        // sitting on its flank, would be a diagram of nothing. Both coordinates are checked because
        // only both together put it on the vertex: the right x alone would leave it anywhere on a
        // vertical line through it.
        assertEquals(APHELION_DOT_CX, APHELION_CX + APHELION_RX, ON_THE_CURVE, "the dot is off the ellipse's end")
        assertEquals(APHELION_DOT_CY, APHELION_CY, ON_THE_CURVE, "the dot is off the ellipse's axis")
    }

    @Test
    fun `the aphelion dot is the furthest ink in the set and still inside the box`() {
        // 20.8 + 1.7 + 0.8 = 23.3, which is the frame's own arithmetic and deliberately conservative:
        // the dot is filled rather than stroked, so the ink actually stops at 22.5 and the half
        // stroke is counted anyway. This is the mark that decides the ellipse's width — a wider one
        // pushes the dot out of the box, and the failure would be a clipped dot on one container and
        // a whole one on another.
        val furthest = APHELION_DOT_CX + DOT_R + MARK_STROKE_WIDTH / 2

        assertTrue(furthest <= MARK_VIEWBOX, "the aphelion dot reaches $furthest in a $MARK_VIEWBOX box")
    }

    @Test
    fun `the sextant's dot sits on the arc`() {
        // 8.8 across and 8.8 down from the arc's centre is 12.445, against a radius of 12.4 — the
        // frame's numbers, four decimal places apart, and the tolerance is the frame's too. What it
        // pins is that the index arm's end is a point *on the scale*: an instrument whose index sat
        // off its own arc would be drawn wrong in the one way a reader of the glyph could name.
        val fromCentre = hypot(SEXTANT_DOT_CX - SEXTANT_CX, SEXTANT_DOT_CY - SEXTANT_CY)

        assertEquals(SEXTANT_R, fromCentre, ON_THE_ARC, "the sextant's dot is $fromCentre from a $SEXTANT_R arc")
    }

    @Test
    fun `the sextant's arm reaches its dot without a break`() {
        // **The one place in the set where ink is meant to meet.** Everywhere else a gap is the
        // drawing — `THRESHOLD` is a magnifier the moment its trajectory touches its limb — and here
        // the opposite is true: an index arm that stopped short of its own index is a broken
        // instrument, not a departure. So this walks the arm's own ray out to past the dot's far edge
        // and requires *one* run of ink rather than two.
        val runs = markPixels { drawPreset(MarkPreset.SEXTANT) }
            .inkRunsAlong(
                fromX = SEXTANT_CX,
                fromY = SEXTANT_CY,
                degrees = -QUARTER_TURN / 2,
                length = SEXTANT_R + DOT_R,
            )

        assertEquals(1, runs.size, "the sextant's arm and dot are $runs rather than one stroke")
    }

    @Test
    fun `the wake's arcs clear the dot and each other`() {
        // Three arcs of one nest, walked along the diagonal all three cross: the dot, then the inner
        // arc 3.6 units off its edge, then the outer one 3.8 further out. Three runs, and the gaps
        // are what make it a wake rather than a smudge — at 20dp the whole glyph is fourteen pixels
        // across, so a pair that closed by a unit would fill in and the baseline would show a
        // thickened blob that looks like a rendering difference.
        val runs = markPixels { drawPreset(MarkPreset.WAKE) }
            .inkRunsAlong(
                fromX = WAKE_DOT_CX,
                fromY = WAKE_DOT_CY,
                degrees = WAKE_RAY_DEGREES,
                length = WAKE_OUTER_R + MARK_STROKE_WIDTH,
            )

        assertEquals(3, runs.size, "the wake is $runs rather than a dot and two arcs")
        assertTrue(runs[1].from - runs[0].to >= WAKE_MINIMUM_GAP, "the inner arc has closed on the dot: $runs")
        assertTrue(runs[2].from - runs[1].to >= WAKE_MINIMUM_GAP, "the two arcs have closed on each other: $runs")
    }

    @Test
    fun `the sounding's plumb line meets its dot`() {
        // The vertical ends exactly on the dot's upper edge, so with the round cap the two overlap by
        // a half stroke. That is deliberate and it is the opposite of `THRESHOLD`'s gap: a sounding
        // line is *attached* to its weight, and a plumb that floated above its bob would read as two
        // unrelated marks stacked up.
        val runs = markPixels { drawPreset(MarkPreset.SOUNDING) }
            .inkRunsAlong(
                fromX = SOUNDING_X,
                fromY = SOUNDING_Y,
                degrees = QUARTER_TURN,
                length = SOUNDING_DOT_CY + SOUNDING_DOT_R - SOUNDING_Y,
            )

        assertEquals(1, runs.size, "the plumb and its weight are $runs rather than one stroke")
    }

    @Test
    fun `the sounding's horizontal ends where its caps put it`() {
        // 4.6 and 19.4 are where the line is drawn to; 3.8 and 20.2 are where the ink ends, because a
        // round cap adds a half stroke beyond each endpoint. Worth measuring rather than asserting
        // on the constants, because the cap is the part nobody remembers: it is the widest thing in
        // the mark, it is what the box rule is actually about, and it is invisible in the numbers the
        // frame wrote down.
        val ink = markPixels { drawPreset(MarkPreset.SOUNDING) }.bounds()
        val left = ink.minX / INK_UNIT - INK_PAD
        val right = ink.maxX / INK_UNIT - INK_PAD

        assertEquals(SOUNDING_LEFT_X - MARK_STROKE_WIDTH / 2, left, CAP_TOLERANCE, "the left cap lands at $left")
        assertEquals(SOUNDING_RIGHT_X + MARK_STROKE_WIDTH / 2, right, CAP_TOLERANCE, "the right cap lands at $right")
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────

    // Through the dispatcher rather than the drawing, so what the tests measure is what a caller
    // handing over a `PlayerMark` would actually get.
    private fun DrawScope.drawPreset(preset: MarkPreset) {
        drawIdentityMark(
            mark = PlayerMark.Preset(preset),
            unit = INK_UNIT,
            dx = INK_PAD,
            dy = INK_PAD,
            color = Color.White,
        )
    }

    private companion object {

        const val RADIANS_PER_DEGREE = 0.017453292f

        // Sixteen samples across the terminator's clear span, which is fine enough that an arc bent
        // by a tenth of a unit anywhere along it is caught and coarse enough to read in a failure.
        const val ARC_SAMPLES = 16

        // The frame's number for how far the terminator stays off the limb between the poles.
        const val POLE_CLEARANCE = 1.2f

        // Four decimal places, which is what "the dot is on the curve" is worth asserting to: the
        // frame rounded 12.445 to 12.4 and the drawing keeps both numbers.
        const val ON_THE_ARC = 0.05f

        // Tighter, because nothing was rounded — the aphelion's dot is placed *at* the vertex, so
        // anything past float noise is a coordinate that has been edited.
        const val ON_THE_CURVE = 0.001f

        // Up and to the left out of the wake's dot, the one diagonal both arcs cross.
        const val WAKE_RAY_DEGREES = 225f

        // The drawn gaps are 2.8 and 2.2 units and measure 2.75 and 2.0, the difference being the
        // anti-aliased fringe on four arc edges. Three quarters of the smaller is loose enough to
        // survive a tuning pass and far tighter than the point at which the nest fills in.
        const val WAKE_MINIMUM_GAP = 1.5f

        // Measured: the left cap lands exactly on 3.8 and the right on 20.1 against a drawn 20.2,
        // one pixel short — the very tip of a round cap covers too little of its last pixel to clear
        // the alpha threshold, and a pixel here is a tenth of a unit. Two tenths is that with a
        // pixel of slack, and it is eight times tighter than a cap that had gone missing altogether.
        const val CAP_TOLERANCE = 0.2f
    }
}
