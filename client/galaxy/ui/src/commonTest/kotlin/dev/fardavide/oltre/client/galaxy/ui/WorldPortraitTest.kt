package dev.fardavide.oltre.client.galaxy.ui

import androidx.compose.ui.graphics.Color
import dev.fardavide.oltre.core.Pressure
import dev.fardavide.oltre.core.Temperature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

// **The rules `WorldPortrait` states in numbers**, checked where they are numbers rather than where
// they are pixels.
//
// The component's specification is a drawing and its baseline is the primary check — see
// `WorldPortraitScreenshotTest`, and the two gradient mistakes its header warns about, which nothing
// but a picture would ever catch. What a baseline cannot do is answer *is this the rule*: −80 °C and
// −79 °C are one step apart in the table and indistinguishable in a photograph, and a world at 5,999
// milli-atm is banded where one at 6,000 is veiled. Both pairs are a rule the design wrote in
// figures, and a figure is worth reading back as a figure.
//
// So four helpers in that file are `internal` where everything else in it is private, and this is
// the only reader of them.
class WorldPortraitTest {

    // ── The mix that builds a disc's three stops ─────────────────────────────────────────────

    @Test
    fun `a channel rounds the midpoint up rather than dropping it`() {
        // Black and white meeting halfway is 127.5 exactly and it becomes 128. The stops are 8-bit
        // colours, so truncating instead would darken every disc in the app by a byte on every
        // channel — and the same pair blended through a gamma-corrected or perceptual space lands
        // near 188, which is the substitution `WorldPortrait`'s own note rules out in as many words.
        assertEquals(128f / 255f, channel(from = 0f, to = 1f, amount = 0.5f))
    }

    @Test
    fun `a mix moves every channel on its own and lands each on its nearest byte`() {
        // The frost step and the two stops the fill is really built from, computed by hand here
        // rather than read back off the component. Its three channels start at 110, 95 and 168 and
        // move 49.3, 54.4 and 29.58 — three different distances from one amount, which is what makes
        // this a per-channel mix rather than one figure applied to a colour.
        val frost = Color(0xFF6E5FA8)

        // The lit stop, at the large disc's 0.34 of white. 197.58 rounds up where 149.4 rounds down,
        // which is what *nearest* means — and what a floor and a ceiling would each get wrong once.
        assertEquals(Color(0xFF9F95C6), frost.mixedWith(Color.White, 0.34f))
        // The dark stop, at 0.62 of the window colour the whole app is drawn on.
        assertEquals(Color(0xFF2D2848), frost.mixedWith(Color(0xFF05070D), 0.62f))
    }

    // ── Fill is temperature ──────────────────────────────────────────────────────────────────

    @Test
    fun `every temperature step owns the degree at its own boundary`() {
        // `<=` rather than `<`: −80 is the last frost and −79 the first cold. The four boundaries
        // are not arbitrary places to be off by one — the generator drops the tolerable orbits
        // either side of them — so a degree moving here repaints a band of the galaxy.
        listOf(-80, -30, 45, 90).forEach { boundary ->
            assertEquals(
                step(boundary - 1),
                step(boundary),
                "$boundary is the last degree of the step below it",
            )
            assertNotEquals(
                step(boundary),
                step(boundary + 1),
                "one degree past $boundary opens the next step",
            )
        }
    }

    @Test
    fun `the five steps are five colours and the hottest of them has no ceiling`() {
        // One degree from inside each step. Five distinct fills is what makes temperature legible as
        // fill at all: two steps sharing a colour would be four steps wearing five names.
        val steps = listOf(-273, -50, 0, 60, 200).map { step(it) }

        assertEquals(5, steps.toSet().size, steps.toString())
        // Nothing caps the top step. A lookup that could fall off the end would throw on the hottest
        // world in the galaxy rather than draw it, which is why the table's last key is unbounded.
        assertEquals(step(200), step(Int.MAX_VALUE))
    }

    // ── Banding is pressure ──────────────────────────────────────────────────────────────────

    @Test
    fun `a large disc bands by the pressure table and names both of its ends`() {
        // Every boundary from both sides. Below 0.1 atm there is nothing to band and the disc is a
        // waste; past 6 atm the bands have closed into one veil and it is a shroud; the three
        // middles are the design's 2, 4 and 7.
        assertIs<WorldSurface.Waste>(surface(0, large = true))
        assertIs<WorldSurface.Waste>(surface(99, large = true))
        assertEquals(2, bands(100, large = true))
        assertEquals(2, bands(899, large = true))
        assertEquals(4, bands(900, large = true))
        assertEquals(4, bands(2_599, large = true))
        assertEquals(7, bands(2_600, large = true))
        assertEquals(7, bands(5_999, large = true))
        assertIs<WorldSurface.Shroud>(surface(6_000, large = true))
    }

    @Test
    fun `a row-scale disc holds every banded world at two`() {
        // At 26dp four stripes and seven are the same grey smear, so the three middle ranges are one
        // reading rather than three that cannot be told apart.
        listOf(100, 899, 900, 2_599, 2_600, 5_999).forEach { atm ->
            assertEquals(2, bands(atm, large = false), "$atm milli-atm at row scale")
        }
        // The two ends are not capped away with them. They are drawings rather than counts, and a
        // shroud that decayed into two stripes in a list would be the row saying the wrong thing
        // about the world the card says the right thing about.
        assertIs<WorldSurface.Waste>(surface(99, large = false))
        assertIs<WorldSurface.Shroud>(surface(6_000, large = false))
    }
}

private fun step(celsius: Int): Color = temperatureStep(Temperature(celsius))

private fun surface(milliAtm: Int, large: Boolean): WorldSurface =
    surfaceFor(Pressure(milliAtm), large)

private fun bands(milliAtm: Int, large: Boolean): Int =
    assertIs<WorldSurface.Bands>(surface(milliAtm, large)).count
