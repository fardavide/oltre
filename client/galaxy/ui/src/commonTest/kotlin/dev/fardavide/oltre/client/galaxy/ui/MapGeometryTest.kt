package dev.fardavide.oltre.client.galaxy.ui

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// **The one property the whole design rests on, checked as arithmetic rather than as a picture.**
//
// The fold is only honest if path order is index order. A screenshot cannot say that — it shows a
// field of dots, and a field of dots with two systems transposed looks exactly like a field of dots
// without. So the ordering, the reversal on odd bands, the continuity across a fold and the cap on
// the drift are asserted here, in numbers, and `GalaxyScreenshotTest` is left to own what it looks
// like.
class MapGeometryTest {

    private val span = 337f
    private val inset = 12f

    @Test
    fun `a band holds twenty-five systems and the first is its region`() {
        assertEquals(0, MapGeometry.bandOf(1))
        assertEquals(0, MapGeometry.bandOf(25))
        assertEquals(1, MapGeometry.bandOf(26))
        assertEquals(9, MapGeometry.bandOf(250))
    }

    @Test
    fun `a system sits at its own column in its own band`() {
        assertEquals(0, MapGeometry.columnOf(1))
        assertEquals(24, MapGeometry.columnOf(25))
        assertEquals(0, MapGeometry.columnOf(26))
        assertEquals(12, MapGeometry.columnOf(163))
    }

    @Test
    fun `an even band runs left to right`() {
        val first = MapGeometry.xOf(system = 1, span = span, inset = inset)
        val last = MapGeometry.xOf(system = 25, span = span, inset = inset)
        assertEquals(inset, first)
        assertEquals(inset + span, last)
    }

    // The reversal is what makes the ribbon continuous rather than a table of ten rows read the same
    // way, and it is the difference between a fold and a grid.
    @Test
    fun `an odd band runs right to left`() {
        val first = MapGeometry.xOf(system = 26, span = span, inset = inset)
        val last = MapGeometry.xOf(system = 50, span = span, inset = inset)
        assertEquals(inset + span, first)
        assertEquals(inset, last)
    }

    @Test
    fun `the two systems either side of a fold are drawn at the same end`() {
        for (band in 0 until MapGeometry.BANDS - 1) {
            val lastOfBand = (band + 1) * MapGeometry.PER_BAND
            val firstOfNext = lastOfBand + 1
            val before = MapGeometry.xOf(system = lastOfBand, span = span, inset = inset)
            val after = MapGeometry.xOf(system = firstOfNext, span = span, inset = inset)
            assertEquals(before, after, "systems $lastOfBand and $firstOfNext turn at the same edge")
        }
    }

    // The claim the design is named for. Two systems in one band, adjacent in index, are adjacent on
    // the drawing — and never in the wrong order, whatever the drift does.
    @Test
    fun `x is monotone in the system index inside every band`() {
        for (band in 0 until MapGeometry.BANDS) {
            val systems = (band * MapGeometry.PER_BAND + 1)..((band + 1) * MapGeometry.PER_BAND)
            val xs = systems.map { MapGeometry.xOf(system = it, span = span, inset = inset) }
            val ordered = if (band % 2 == 0) xs == xs.sorted() else xs == xs.sortedDescending()
            assertTrue(ordered, "band $band is not monotone")
        }
    }

    @Test
    fun `the pitch is the span over the twenty-four gaps in a band`() {
        assertEquals(span / 24f, MapGeometry.pitchOf(span))
    }

    // Half a pitch is the cap, and the reason is the test above it: at a full pitch a drifted star
    // could cross its neighbour and the drawing would start lying about which comes first.
    @Test
    fun `drift never reaches half a pitch`() {
        val pitch = MapGeometry.pitchOf(span)
        for (permille in -500..500) {
            assertTrue(abs(MapGeometry.driftOf(permille, pitch)) <= pitch / 2f)
        }
    }

    @Test
    fun `the lane of the last band ends where the map does`() {
        assertEquals(MapGeometry.BANDS, MapGeometry.bandOf(250) + 1)
        assertTrue(MapGeometry.laneMidOf(band = 9) < MapGeometry.HEIGHT_DP)
    }

    // A ten-band map is 531dp tall at 393dp and at 320dp alike, which is what lets there be one
    // geometry rather than two.
    @Test
    fun `the map is five hundred and thirty-one dp tall`() {
        assertEquals(531f, MapGeometry.HEIGHT_DP)
    }

    @Test
    fun `the nearest system to a touch is the one whose cell holds it`() {
        val width = 361f
        assertEquals(1, MapGeometry.systemAt(x = 0f, y = 0f, width = width))
        val band6 = MapGeometry.laneMidOf(band = 6)
        assertEquals(151, MapGeometry.systemAt(x = inset, y = band6, width = width))
        assertEquals(175, MapGeometry.systemAt(x = inset + span, y = band6, width = width))
    }

    @Test
    fun `a touch below the last band still lands on the map`() {
        val width = 361f
        assertTrue(MapGeometry.systemAt(x = 10f, y = 10_000f, width = width) in 226..250)
    }
}
