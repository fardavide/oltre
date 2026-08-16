package dev.fardavide.oltre.client.world.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.core.Gravity
import dev.fardavide.oltre.core.Hazard
import dev.fardavide.oltre.core.Pressure
import dev.fardavide.oltre.core.Temperature
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// **The rules `WorldPortrait` states in figures and the rules it states in pixels**, each checked as
// what it is — which for half of them means executing the drawing.
//
// The figures are read back as figures. −80 °C and −79 °C are one step apart in the table and
// indistinguishable in a photograph, and a world at 5,999 milli-atm is banded where one at 6,000 is
// veiled: both pairs are a rule the design wrote in numbers, and a number is worth reading back as a
// number.
//
// The pixels are read back by handing `drawWorldPortrait` a `CanvasDrawScope` over an `ImageBitmap`,
// which is the whole reason the drawing was separated from the composable that hosts it. **This is
// not a second baseline.** `WorldPortraitScreenshotTest` owns what the component looks like, and the
// two gradient mistakes `WorldPortrait`'s header warns about are still things only a picture would
// ever catch. What a picture cannot do is say *which rule it is showing*: a recorded frame of an
// unsurveyed socket is exactly as green with a gravity-sized socket as without one, because nobody
// has ever seen the other frame to diff it against. So what is asserted here is the sentence rather
// than the drawing — no fill inside a socket, a diameter that moves with gravity and one that cannot,
// a fill that leans violet at the cold end, a halo outside the limb, and a small disc that drops the
// marks it has no room for.
//
// So five helpers in that file are `internal` where everything else in it is private, and this is the
// only reader of them.
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

    @Test
    fun `the five steps reach the drawn disc as five different fills`() {
        // The table's five colours are three gradient stops by the time they are painted, and two
        // steps that arrived at the same pixel would be a temperature the player cannot read off the
        // disc at all — which is the one thing fill is for.
        val fills = FIVE_STEPS.map { render(surveyed(celsius = it), CARD).middle() }

        assertEquals(5, fills.toSet().size, fills.toString())
    }

    @Test
    fun `a cold fill leans violet where a hot one leans red`() {
        // The channel relationship rather than the hex, because the rule the design states is a
        // direction and not a colour: heat is carried by luminance and the hue rotation is narrow —
        // deliberately stopping short of amber and red, which already mean *in transit* and *you are
        // short*. Pinning the byte here would make a re-mix of the palette read as a broken test
        // rather than as the deliberate change it would be.
        val (frost, cold, inBand, hot, furnace) = FIVE_STEPS.map { render(surveyed(celsius = it), CARD).middle() }

        assertTrue(frost.blue > frost.red && cold.blue > cold.red, "the cold end is bluer than it is red")
        assertTrue(hot.red > hot.blue && furnace.red > furnace.blue, "the hot end is redder than it is blue")
        // The reference world sits between the two ends rather than at one of them: it is the metal
        // grey the rest of the palette is built from, and a habitable world reading as *cold* is the
        // one misreading this ladder cannot afford.
        assertTrue(inBand.blue - inBand.red < cold.blue - cold.red, "in band is less blue than cold")
        assertTrue(inBand.blue - inBand.red > hot.blue - hot.red, "in band is less red than hot")
        // And the two cold steps are not one colour twice: frost is the deuterium violet, so its red
        // outruns its green, where the cold step is a blue whose green outruns its red.
        assertTrue(frost.red > frost.green, "frost leans violet")
        assertTrue(cold.green > cold.red, "cold leans blue")
    }

    // ── Diameter is gravity ──────────────────────────────────────────────────────────────────

    @Test
    fun `gravity moves the drawn diameter across the band and never outside it`() {
        // Both extremes of the range the generator can produce, and the coercion at each end means
        // neither is the formula's own limit: a world with no gravity worth the name still draws a
        // disc rather than a dot, and one heavier than 3 g stops growing rather than filling the box.
        val lightest = render(surveyed(milliG = 0), CARD).drawnDiameter()
        val heaviest = render(surveyed(milliG = 9_000), CARD).drawnDiameter()

        assertTrue(lightest >= 0.50f * CARD.value, "the lightest world is $lightest of ${CARD.value}")
        // 0.82 of the box, plus the half dp that rounding to a whole dp can add and the antialiased
        // pixel an odd diameter puts half of past each limb.
        assertTrue(heaviest <= 0.82f * CARD.value + 1.5f, "the heaviest world is $heaviest of ${CARD.value}")
        // A quarter of the box between the two ends. The box never changes size, so this is the whole
        // of what makes a heavy world read as heavy against the neighbour it is listed beside.
        assertTrue(heaviest - lightest > 0.25f * CARD.value, "$lightest and $heaviest are too close")
    }

    @Test
    fun `an unsurveyed socket has no gravity to be sized by`() {
        // One size, and the *only* thing it is a size of is the box — which is what the sealed state
        // buys: there is no gravity on `Unsurveyed` for a diameter to leak, so 98% of a list is a
        // column of identical sockets rather than a table of first traits nobody has paid for.
        val card = render(WorldPortraitUiState.Unsurveyed, CARD).drawnDiameter() / CARD.value
        val row = render(WorldPortraitUiState.Unsurveyed, ROW).drawnDiameter() / ROW.value

        assertTrue(abs(card - 0.62f) < 0.01f, "a socket on a card is $card of the box")
        assertTrue(abs(row - 0.62f) < 0.01f, "a socket in a row is $row of the box")
    }

    @Test
    fun `an unsurveyed socket is a hole where a surveyed world is a fill`() {
        // The leak-proofing the whole sealed state exists for, read at the one pixel that cannot lie
        // about it: a socket is an outline around nothing, so the middle of it is what the window was
        // before the component drew anything at all.
        val socket = render(WorldPortraitUiState.Unsurveyed, CARD)

        assertEquals(0f, socket.middle().alpha, "the middle of a socket is untouched")
        assertTrue(socket.drawnDiameter() > 0f, "though the hairline that makes it a socket is drawn")
        assertEquals(1f, render(surveyed(), CARD).middle().alpha, "a surveyed world is filled")
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

    @Test
    fun `more pressure crosses the disc with more stripes and neither end of the table has any`() {
        // The table's count is a number until something draws it. These are the stripes themselves,
        // counted as the hard steps a column crosses — and the two ends of the table cross none,
        // which is the drawn half of the claim that a waste and a shroud are pictures rather than
        // counts of zero.
        val waste = render(surveyed(milliAtm = 20), CARD).bandEdgesDownTheMiddle()
        val two = render(surveyed(milliAtm = 400), CARD).bandEdgesDownTheMiddle()
        val four = render(surveyed(milliAtm = 1_400), CARD).bandEdgesDownTheMiddle()
        val seven = render(surveyed(milliAtm = 3_800), CARD).bandEdgesDownTheMiddle()
        val shroud = render(surveyed(milliAtm = 8_600), CARD).bandEdgesDownTheMiddle()

        assertTrue(two < four && four < seven, "the three banded steps drew $two then $four then $seven")
        assertEquals(0, waste, "bare ground is banded by nothing")
        assertEquals(0, shroud, "a shroud is one veil rather than the stripes it closed from")
    }

    @Test
    fun `a row-scale disc draws the two bands it counts rather than the four or seven it would`() {
        // The cap reaches the pixels: the three banded ranges are one drawing at row scale and three
        // different ones on a card. **The 2-against-4 pair is the load-bearing one** — a `drawBands`
        // that ignored the cap and banded a row-scale disc by the raw pressure would still draw 4 and
        // 7 identically, because at 16dp of disc both ask for a period below the two-pixel floor and
        // get the floor. Only the pair either side of that floor can tell a capped disc from an
        // uncapped one, which is exactly the kind of thing a test states and a picture does not.
        val two = surveyed(milliAtm = 400)
        val four = surveyed(milliAtm = 1_400)
        val seven = surveyed(milliAtm = 3_800)

        assertTrue(render(two, ROW).sameAs(render(four, ROW)), "two bands and four are one row-scale disc")
        assertTrue(render(four, ROW).sameAs(render(seven, ROW)), "four bands and seven are one row-scale disc")
        assertFalse(render(two, CARD).sameAs(render(four, CARD)), "and two different discs on a card")
        assertFalse(render(four, CARD).sameAs(render(seven, CARD)), "as are four and seven")
    }

    // ── The marks are the hazards ────────────────────────────────────────────────────────────

    @Test
    fun `a tidally locked world is night on the far side of a hard terminator`() {
        // The terminator crosses within a couple of pixels of the middle, so a quarter of the disc
        // either side of it lands one sample on each face. The hazard replaces the soft limb outright
        // rather than adding to it, which is why the unlocked disc's two faces are within a tenth of
        // each other where the locked one's are an order of magnitude apart.
        val plain = render(surveyed(), CARD)
        val locked = render(surveyed(hazards = setOf(Hazard.TIDALLY_LOCKED)), CARD)
        val quarter = (plain.drawnDiameter() / 4f).roundToInt()

        assertTrue(
            locked.brightnessAcross(-quarter) > 4f * locked.brightnessAcross(quarter),
            "a locked world's far side is ${locked.brightnessAcross(quarter)} against " +
                "${locked.brightnessAcross(-quarter)} lit",
        )
        assertTrue(
            plain.brightnessAcross(quarter) > 0.75f * plain.brightnessAcross(-quarter),
            "an unlocked world has no terminator to be dark behind",
        )
    }

    @Test
    fun `the radiation halo is drawn outside the limb rather than on it`() {
        // A ring of its own beyond the disc, with a gap — not a rim on the edge of the fill, which is
        // what it would decay into if the halo were sized from the disc's radius rather than from it
        // plus the gap. Nothing else the component draws escapes the limb, so a painted pixel out
        // there is the halo and can be nothing else.
        val plain = render(surveyed(), CARD)
        val belt = render(surveyed(hazards = setOf(Hazard.RADIATION_BELT)), CARD)
        val beyondTheLimb = plain.drawnDiameter() / 2f + 2f

        assertEquals(0, plain.paintedBeyond(beyondTheLimb), "a world without the belt paints nothing out there")
        assertTrue(belt.paintedBeyond(beyondTheLimb) > 0, "a world with it paints its halo out there")
    }

    @Test
    fun `seismic instability replaces thin crust rather than adding to it`() {
        // Three deeper lines, never five: they are the same mark at two strengths because they are
        // the same kind of fact, so the deeper one wins outright. The second assertion is what stops
        // this passing on a component that had stopped drawing fractures altogether.
        val both = surveyed(hazards = setOf(Hazard.SEISMIC_INSTABILITY, Hazard.THIN_CRUST))
        val seismic = surveyed(hazards = setOf(Hazard.SEISMIC_INSTABILITY))
        val thin = surveyed(hazards = setOf(Hazard.THIN_CRUST))

        assertTrue(render(both, CARD).sameAs(render(seismic, CARD)), "thin crust adds nothing under seismic")
        assertFalse(render(thin, CARD).sameAs(render(surveyed(), CARD)), "though on its own it draws its two")
    }

    // ── What a row-scale disc drops ──────────────────────────────────────────────────────────

    @Test
    fun `a row-scale disc drops the marks that would be noise and keeps the ones that still read`() {
        // The gate is on the box rather than on the drawn diameter, and this is the pair of claims
        // that says so from both sides. Dropped is not *drawn smaller*: at 26dp a swirl, a fracture
        // and a ring are sub-pixel, so they are drawn not at all and the row is identical to a world
        // without them. The halo and the terminator survive because they are still legible at that
        // size — a hazard the row is allowed to keep saying.
        val dropped = mapOf(
            "ion storms" to surveyed(hazards = setOf(Hazard.ION_STORMS)),
            "seismic fractures" to surveyed(hazards = setOf(Hazard.SEISMIC_INSTABILITY)),
            "thin-crust fractures" to surveyed(hazards = setOf(Hazard.THIN_CRUST)),
            "a ring" to surveyed(ring = true),
        )
        val kept = mapOf(
            "a radiation halo" to surveyed(hazards = setOf(Hazard.RADIATION_BELT)),
            "a terminator" to surveyed(hazards = setOf(Hazard.TIDALLY_LOCKED)),
        )

        (dropped + kept).forEach { (mark, world) ->
            assertFalse(render(world, CARD).sameAs(render(surveyed(), CARD)), "$mark is drawn on a card")
        }
        dropped.forEach { (mark, world) ->
            assertTrue(render(world, ROW).sameAs(render(surveyed(), ROW)), "$mark is drawn at row scale")
        }
        kept.forEach { (mark, world) ->
            assertFalse(render(world, ROW).sameAs(render(surveyed(), ROW)), "$mark is gone at row scale")
        }
    }
}

// The two boxes the design draws this at: the discovery card and a list row, either side of the 60dp
// gate. The renders below are a pixel to the dp, so a box is also the size of the bitmap it is drawn
// on — which is what the component asks its layout for.
private val CARD = 96.dp
private val ROW = 26.dp

// One degree from inside each temperature step, coldest first.
private val FIVE_STEPS = listOf(-273, -50, 0, 60, 200)

private fun step(celsius: Int): Color = temperatureStep(Temperature(celsius))

private fun surface(milliAtm: Int, large: Boolean): WorldSurface =
    surfaceFor(Pressure(milliAtm), large)

private fun bands(milliAtm: Int, large: Boolean): Int =
    assertIs<WorldSurface.Bands>(surface(milliAtm, large)).count

// Middling on every axis unless a test is about that axis, and a waste by default: bare ground is the
// one surface that paints nothing over the fill, so a test reading the fill reads the fill.
private fun surveyed(
    celsius: Int = -6,
    milliG: Int = 1_000,
    milliAtm: Int = 20,
    hazards: Set<Hazard> = emptySet(),
    ring: Boolean = false,
): WorldPortraitUiState.Surveyed = WorldPortraitUiState.Surveyed(
    temperature = Temperature(celsius),
    gravity = Gravity(milliG),
    pressure = Pressure(milliAtm),
    hazards = hazards,
    hasRing = ring,
)

// **The drawing, executed.** A `CanvasDrawScope` over an `ImageBitmap` is the whole of what a
// `DrawScope` needs, so this runs as a plain unit test with no UI toolkit, no window and no clock.
// At `Density(1f)` a dp is a pixel, which is what lets every assertion below talk in the design's
// own units.
private fun render(uiState: WorldPortraitUiState, box: Dp): PixelMap {
    val px = box.value.roundToInt()
    return ImageBitmap(px, px).also { bitmap ->
        CanvasDrawScope().draw(
            density = Density(1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(bitmap),
            size = Size(px.toFloat(), px.toFloat()),
        ) {
            drawWorldPortrait(uiState = uiState, box = box)
        }
    }.toPixelMap()
}

// The bitmap starts empty, so any alpha at all is something the component put there. The threshold is
// only so that an antialiased fringe does not read as a mark of its own.
private val Color.isPainted: Boolean get() = alpha > 0.05f

private fun PixelMap.middle(): Color = this[width / 2, height / 2]

private fun PixelMap.brightnessAcross(offset: Int): Float =
    this[width / 2 + offset, height / 2].let { (it.red + it.green + it.blue) / 3f }

// The width of what is painted across the middle row — the disc's limb, or the socket's outline, or
// whatever else reaches that row. Nothing but a halo does, and no test asks this of a world with one.
private fun PixelMap.drawnDiameter(): Float {
    val painted = (0 until width).filter { this[it, height / 2].isPainted }
    return if (painted.isEmpty()) 0f else (painted.last() - painted.first() + 1).toFloat()
}

private fun PixelMap.paintedBeyond(radius: Float): Int = (0 until height).sumOf { y ->
    (0 until width).count { x ->
        val dx = x + 0.5f - width / 2f
        val dy = y + 0.5f - height / 2f
        this[x, y].isPainted && dx * dx + dy * dy > radius * radius
    }
}

// Stripes are hard-edged and everything else on a disc is a gradient, so a step down the middle
// column bigger than a gradient ever takes is a band boundary. Two things keep it honest: the walk
// covers the middle half of the disc only, so the limb's own ramp is never in it, and a run of
// adjacent steps counts once, because a boundary at 8° is antialiased across two rows.
private fun PixelMap.bandEdgesDownTheMiddle(): Int {
    val reach = drawnDiameter() / 4f
    val x = width / 2
    var edges = 0
    var inEdge = false
    for (y in (height / 2f - reach).roundToInt() + 1..(height / 2f + reach).roundToInt()) {
        val above = this[x, y - 1]
        val below = this[x, y]
        val step = abs(above.red - below.red) + abs(above.green - below.green) + abs(above.blue - below.blue)
        // Comfortably above the 0.075 the limb's first ramped row takes and below the 0.22 the
        // faintest stripe boundary takes.
        val edge = step > 0.12f
        if (edge && !inEdge) edges++
        inEdge = edge
    }
    return edges
}

private fun PixelMap.sameAs(other: PixelMap): Boolean = (0 until height).all { y ->
    (0 until width).all { x -> this[x, y] == other[x, y] }
}
