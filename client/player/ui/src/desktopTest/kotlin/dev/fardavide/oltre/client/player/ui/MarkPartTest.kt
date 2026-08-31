package dev.fardavide.oltre.client.player.ui

import androidx.compose.ui.graphics.Color
import dev.fardavide.oltre.protocol.MarkBody
import dev.fardavide.oltre.protocol.MarkPath
import dev.fardavide.oltre.protocol.MarkTerminus
import dev.fardavide.oltre.protocol.PlayerMark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// **Forty marks out of eleven drawings, and this is what pays for the other twenty-nine.** A set of
// six is checked mark by mark; a grammar cannot be, because nobody draws the forty and no baseline
// exists for thirty-nine of them. What holds them up instead is the property the composer is built
// on — *no two parts can occupy the same ink* — and the property is only true if every pairing keeps
// its distance. So the parts are checked one at a time against the box, and then every body is
// checked against every path along the ray where the two could meet.
//
// Twelve pairs, and the number is the design's: four bodies against three paths, because `NONE` puts
// nothing on the diagonal to collide with. The failure this catches is the one `PlayerMarkTest`
// already caught once for `THRESHOLD` — a world with a stalk is a magnifier — reaching every
// combination a player can now assemble rather than the single one that shipped.
class MarkPartTest {

    @Test
    fun `every part that draws stays inside the box it is drawn in`() {
        // Each part is rendered on its own, which is the only way this bound means anything: a body
        // measured inside a whole mark is measured together with a path that reaches further, and the
        // part that overflowed would be whichever one happened to be furthest out.
        for (body in MarkBody.entries) {
            val ink = markPixels {
                drawMarkBody(body = body, unit = INK_UNIT, dx = INK_PAD, dy = INK_PAD, color = Color.White)
            }.bounds()

            assertInsideTheBox(ink, what = "body $body")
        }
        for (path in MarkPath.entries.filterNot { it == MarkPath.NONE }) {
            val ink = markPixels {
                drawMarkPath(path = path, unit = INK_UNIT, dx = INK_PAD, dy = INK_PAD, color = Color.White)
            }.bounds()

            assertInsideTheBox(ink, what = "path $path")
        }
        for (terminus in MarkTerminus.entries.filterNot { it == MarkTerminus.NONE }) {
            val ink = markPixels {
                drawMarkTerminus(terminus = terminus, unit = INK_UNIT, dx = INK_PAD, dy = INK_PAD, color = Color.White)
            }.bounds()

            assertInsideTheBox(ink, what = "terminus $terminus")
        }
    }

    @Test
    fun `the two absences draw nothing at all`() {
        // **An absence is a drawing here**, and the reason it is worth a test is the rule it protects:
        // a terminus is the end of a path, so a mark with no path has no terminus, and the sealed
        // type refuses the pair outright. If either `NONE` ever drew a stub — a cap left behind, a
        // zero-length line that renders as a dot — the composer would put ink in a region the wire
        // says is empty, and the forty legal marks would quietly become forty-eight illegible ones.
        val path = markPixels {
            drawMarkPath(path = MarkPath.NONE, unit = INK_UNIT, dx = INK_PAD, dy = INK_PAD, color = Color.White)
        }
        val terminus = markPixels {
            drawMarkTerminus(
                terminus = MarkTerminus.NONE,
                unit = INK_UNIT,
                dx = INK_PAD,
                dy = INK_PAD,
                color = Color.White,
            )
        }

        assertEquals(0, path.inkedPixels(), "MarkPath.NONE left ink on the canvas")
        assertEquals(0, terminus.inkedPixels(), "MarkTerminus.NONE left ink on the canvas")
    }

    @Test
    fun `no body and path pair closes the space between them`() {
        // Walked out of the body's own centre along the diagonal every path starts on, which is the
        // one ray where a body and a path can reach each other: the regions are otherwise disjoint by
        // construction, and this is the seam between two of them.
        //
        // The boundary is what turns a list of runs into a clearance. Everything a body draws is
        // inside 5.5 units of its centre and everything a path draws is outside 6.55, so a probe at 6
        // sits in open canvas for all twelve pairs — the last ink before it is the body's and the
        // first after it is the path's, whichever body has how many arcs.
        for (body in MarkBody.entries) {
            for (path in MarkPath.entries.filterNot { it == MarkPath.NONE }) {
                val mark = PlayerMark.Composed(body = body, path = path, terminus = MarkTerminus.NONE)
                val runs = markPixels {
                    drawIdentityMark(mark = mark, unit = INK_UNIT, dx = INK_PAD, dy = INK_PAD, color = Color.White)
                }.inkRunsAlong(
                    fromX = BODY_CX,
                    fromY = BODY_CY,
                    degrees = -QUARTER_TURN / 2,
                    length = PAIR_RAY_LENGTH,
                )

                val bodyInk = assertNotNull(runs.lastOrNull { it.to < PAIR_BOUNDARY }, "$body drew nothing: $runs")
                val pathInk = assertNotNull(runs.firstOrNull { it.from > PAIR_BOUNDARY }, "$path drew nothing: $runs")
                val clearance = pathInk.from - bodyInk.to

                assertTrue(
                    clearance >= MINIMUM_CLEARANCE,
                    "$body and $path leave $clearance units between them which reads as a stalk: $runs",
                )
            }
        }
    }

    private companion object {

        // Past the far end of every path's near stroke and well short of the terminus region, so what
        // the walk finds is a body and a path and nothing else.
        const val PAIR_RAY_LENGTH = 12f

        // Between the furthest a body reaches (5.5) and the nearest a path starts (6.55).
        const val PAIR_BOUNDARY = 6f

        // **A unit, as the frame states it, and the drawing has 1.05.** A limb's stroke ends 5.5 units
        // out along this ray and a path's round cap begins at 6.55; measured through the bitmap the
        // tightest of the twelve pairs reads 1.050004, so the anti-aliased fringe costs nothing here
        // and the bound can be the design's own number rather than an allowance made for the harness.
        // Five hundredths of slack is one sample step, which is as tight as this walk can be read —
        // and the failure it is against is not a nudge but a stroke crossing into a region the
        // composer's whole legibility argument says is clear.
        const val MINIMUM_CLEARANCE = 1f
    }
}
