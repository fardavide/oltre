package dev.fardavide.oltre.client.galaxy.ui

import dev.fardavide.oltre.client.design.text.TextRes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.fardavide.oltre.core.RegionTemperament
import dev.fardavide.oltre.core.StarClass
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// **What a recorded frame of 250 dots cannot say.** `GalaxyScreenshotTest` owns what the fold looks
// like, and it is the right owner: the drift, the halos and the region fields are exactly the kind of
// drawing only a picture catches. What a picture cannot say is *which system it is showing* — a field
// of dots with two of them transposed is a field of dots — so the claims the whole design rests on
// are read back here, in pixels, by handing `drawFold` a `CanvasDrawScope` over an `ImageBitmap`.
//
// That is `WorldPortraitTest`'s method and it is why `drawFold` is `internal`: the drawing is
// separable from the composable that hosts it, and separating it is what makes it executable.
//
// `MapGeometryTest` proves the arithmetic on its own. This proves the *paint agrees with it* — which
// is a different claim, and the one that would break silently if a `y` were computed twice with two
// different amplitudes.
class GalaxyMapDrawingTest {

    @Test
    fun `every star is painted where the geometry puts it`() {
        // given — one bright star a band, so each is unmistakable and none can be another's halo.
        val fold = Fold.full(width = WIDTH)
        val stars = (0 until MapGeometry.BANDS).map { band -> MapGeometry.firstSystemOf(band) + 12 }

        // when
        val painted = render(mapOf(stars = stars.map { star(it, StarClass.BRIGHT) }), fold)

        // then — the drawing and the arithmetic are the same fold, band by band. Read at the pixel
        // the geometry names rather than by searching for ink, because "there is a star somewhere
        // near here" is the assertion a transposition would survive.
        stars.forEach { system ->
            val x = fold.x(system).roundToInt()
            val y = fold.y(system, driftPermille = 0).roundToInt()

            assertTrue(painted[x, y].alpha > PAINTED, "system $system is not drawn at $x,$y")
        }
    }

    @Test
    fun `a drifted star is painted off its lane and never past its neighbour`() {
        // The cap the whole drawing rests on: half a pitch, so a star can wander enough to read as
        // sky and never enough to change which of two systems comes first.
        val fold = Fold.full(width = WIDTH)
        val system = 40

        val low = fold.y(system, driftPermille = -500)
        val high = fold.y(system, driftPermille = 500)

        // **A full pitch of travel end to end, which is ±half a pitch either side of the lane.** The
        // upper bound is the one that matters: at any more, a drifted star could cross its neighbour
        // and the drawing would start disagreeing about which of two systems comes first.
        assertEquals(fold.pitch, high - low, absoluteTolerance = 0.01f)
        assertEquals(fold.pitch / 2f, high - fold.y(system, driftPermille = 0), absoluteTolerance = 0.01f)
    }

    @Test
    fun `a bright star is drawn larger than a standard one and a standard larger than a dim one`() {
        // Size *is* class on this map, which is what keeps knowledge and astronomy on separate
        // channels — a surveyed dim star and an unsurveyed bright one can never be the same mark.
        val fold = Fold.full(width = WIDTH)

        val dim = fold.radiusOf(StarClass.DIM, sizePermille = 1_000)
        val standard = fold.radiusOf(StarClass.STANDARD, sizePermille = 1_000)
        val bright = fold.radiusOf(StarClass.BRIGHT, sizePermille = 1_000)

        assertTrue(dim < standard && standard < bright, "$dim $standard $bright")
    }

    @Test
    fun `the size wobble can never promote a star into the next class`() {
        // **The bound Claude Design drew did not keep this promise and this test is what found it.**
        // At its 820…1180 the widest standard is 2.24dp against a narrowest bright of 2.13 — a
        // standard drawn larger than a bright, on a map whose entire legend is that size is class.
        // `core` narrowed the band to 870…1130, which is the widest that keeps both gaps open.
        val fold = Fold.full(width = WIDTH)

        val widestDim = fold.radiusOf(StarClass.DIM, sizePermille = 1_130)
        val narrowestStandard = fold.radiusOf(StarClass.STANDARD, sizePermille = 870)
        val widestStandard = fold.radiusOf(StarClass.STANDARD, sizePermille = 1_130)
        val narrowestBright = fold.radiusOf(StarClass.BRIGHT, sizePermille = 870)

        assertTrue(widestDim < narrowestStandard, "$widestDim reaches a standard's $narrowestStandard")
        assertTrue(widestStandard < narrowestBright, "$widestStandard reaches a bright's $narrowestBright")
    }

    @Test
    fun `a surveyed star wears a ring outside its own disc`() {
        // Outside, not over: the ring is what you know and the disc is what the star is, and a mark
        // that landed on the ink would be one fact hiding another.
        val fold = Fold.full(width = WIDTH)
        val system = 63
        val ringed = star(system, StarClass.DIM).copy(marks = setOf(MapStarMark.SURVEYED))

        val painted = render(mapOf(stars = listOf(ringed)), fold)

        val x = fold.x(system).roundToInt()
        val y = fold.y(system, driftPermille = 0).roundToInt()
        val radius = fold.radiusOf(StarClass.DIM, sizePermille = 1_000)
        // Scanned rather than sampled at one pixel: a 1dp stroke lands between two rows as often as
        // on one, and a test that demanded the exact row would be asserting the rounding rather than
        // the ring. What is asserted is that there is ink *beyond the disc at all*, which is where
        // nothing but a ring can be.
        val beyond = (1..6).count { step ->
            painted[x, (y + radius + step).roundToInt()].alpha > PAINTED
        }
        assertTrue(beyond > 0, "no ring beyond the disc")
    }

    @Test
    fun `a selection is drawn further out than home and home further out than a probe`() {
        // Four channels that stack rather than one that wins: a star can be yours, selected and have
        // a probe on its way back to it at once, and each ring has to have room of its own.
        val fold = Fold.full(width = WIDTH)
        val system = 63
        val all = star(system, StarClass.STANDARD).copy(
            marks = setOf(MapStarMark.SURVEYED, MapStarMark.IN_FLIGHT, MapStarMark.HOME, MapStarMark.SELECTED),
        )

        val painted = render(mapOf(stars = listOf(all)), fold)

        // Reading outwards from the star, every ring radius carries ink and the gaps between them do
        // not — which is what "they stack" means in pixels.
        val x = fold.x(system).roundToInt()
        val y = fold.y(system, driftPermille = 0).roundToInt()
        listOf(5.4f, 6.4f, 8.2f).forEach { radius ->
            assertTrue(painted[x, (y - radius).roundToInt()].alpha > PAINTED, "no ring at $radius")
        }
    }

    @Test
    fun `a region field is painted behind its own band and not behind the next`() {
        // The region as weather rather than as a boundary. It is the faintest thing on the map at
        // 5-10%, so it is read at the lane's own centre where it is strongest, with no star to
        // confuse it.
        val fold = Fold.full(width = WIDTH)
        val deep = mapOf(stars = emptyList(), bands = bands(RegionTemperament.DEEP))

        val painted = render(deep, fold)

        val centre = (fold.width / 2f).roundToInt()
        val firstLane = MapGeometry.laneMidOf(band = 0).roundToInt()
        assertTrue(painted[centre, firstLane].alpha > FIELD, "the first band has no field behind it")
    }

    @Test
    fun `a mini disc draws no spikes and no hour marks`() {
        // A disc is the same fold at a fifth of the size, and the two things it drops are the two
        // that need room: a four-armed spike on a 1.6dp star is a blur, and an hour mark with no
        // label beside it is a line meaning nothing.
        val mini = Fold.mini(width = 148f, lane = 19f)
        val full = Fold.full(width = WIDTH)

        assertTrue(mini.mini)
        assertEquals(0f, mini.labelRow)
        assertEquals(0f, mini.gap)
        // Every class is drawn smaller than its full-size self, and the three stay in their order —
        // a disc is the same fold rather than a different one, so its legend has to survive the
        // scaling too.
        StarClass.entries.forEach { starClass ->
            assertTrue(
                mini.radiusOf(starClass, 1_000) < full.radiusOf(starClass, 1_000),
                "$starClass is not smaller on a disc",
            )
        }
    }

    @Test
    fun `the drawn height is the ten bands the geometry names`() {
        assertEquals(MapGeometry.HEIGHT_DP, Fold.full(width = WIDTH).height)
    }

    // ── The third tier ──────────────────────────────────────────────────────────────────────
    //
    // **This class is the only thing that can see a star's appearance.** No robot verb reaches a
    // pixel and a screenshot photographs grain as happily as it photographs sky, so every claim the
    // fog design makes about how an uncharted star is drawn is asserted here or nowhere.

    @Test
    fun `an uncharted star is drawn smaller than the dimmest charted one`() {
        // The grain-tier twin of `the size wobble can never promote a star into the next class`. The
        // margin is 0.08dp at full size, and this is what stops a later radius tweak closing it.
        val full = Fold.full(width = WIDTH)
        val mini = Fold.mini(width = 148f, lane = 19f)

        assertTrue(
            full.radiusOf(MapStarInk.Grain) < full.radiusOf(StarClass.DIM, NARROWEST_PERMILLE),
            "grain is not under the narrowest dim star",
        )
        assertTrue(
            mini.radiusOf(MapStarInk.Grain) < mini.radiusOf(StarClass.DIM, NARROWEST_PERMILLE),
            "grain is not under the narrowest dim star on a disc",
        )
    }

    @Test
    fun `an uncharted star takes no wobble at all`() {
        // One size, flat — the whole reason `radiusOf(ink)` exists rather than a third class radius.
        // A wobbling grain star would read as sky, which is exactly what the tier withholds.
        val fold = Fold.full(width = WIDTH)

        assertEquals(fold.radiusOf(MapStarInk.Grain), fold.grain)
    }

    @Test
    fun `an uncharted star is drawn fainter than the dimmest charted one`() {
        // Read back rather than compared as constants: the two thresholds this file already has
        // cannot separate grain from a dim star, so the assertion is the two alphas against each
        // other. Both are painted; one is a third of the other.
        val fold = Fold.full(width = WIDTH)
        val system = MapGeometry.firstSystemOf(0) + 12
        val x = fold.x(system).roundToInt()
        val y = fold.y(system, driftPermille = 0).roundToInt()

        val dim = render(mapOf(stars = listOf(star(system, StarClass.DIM))), fold)[x, y].alpha
        val grain = render(mapOf(stars = listOf(grain(system))), fold)[x, y].alpha

        assertTrue(grain > PAINTED, "an uncharted star is not drawn at all")
        assertTrue(grain < dim, "grain at $grain is not fainter than a dim star at $dim")
    }

    @Test
    fun `an uncharted star carries no halo and no spike`() {
        // **An uncharted BRIGHT is the leak the tier exists to close.** A spike is the loudest thing
        // this drawing can say about a star, so a fixture that could not be grain in production is
        // exactly the one worth pinning: the sealed ink makes it unrepresentable and this says so.
        val fold = Fold.full(width = WIDTH)
        val system = MapGeometry.firstSystemOf(4) + 12
        val x = fold.x(system).roundToInt()
        val y = fold.y(system, driftPermille = 0).roundToInt()
        val reach = (fold.radiusOf(StarClass.BRIGHT, 1_000) * SPIKE_REACH_PROBE).roundToInt()
        // No weather anywhere, because a region field is drawn across a whole band and would be the
        // ink this reads rather than the spike.
        val dark = bands(RegionTemperament.DEEP).map { it.copy(charted = null) }

        val spiked = render(mapOf(stars = listOf(star(system, StarClass.BRIGHT)), bands = dark), fold)
        val painted = render(mapOf(stars = listOf(grain(system)), bands = dark), fold)

        // Read on the vertical arm only, and that is a finding rather than a shortcut: the spine is
        // a horizontal polyline through the lane, so the horizontal arm of a spike shares its pixels
        // with the path the stars sit on and no threshold can tell the two apart.
        assertTrue(spiked[x, y - reach].alpha > PAINTED, "the fixture needs a charted star that does spike")
        assertTrue(painted[x, y - reach].alpha < FIELD, "an uncharted star grew a spike")
        assertTrue(painted[x, y + reach].alpha < FIELD, "an uncharted star grew a spike")
    }

    @Test
    fun `a region field is painted only across the charted stretch of its band`() {
        // The weather is knowledge too. A band lit at one end is tinted at that end and dark at the
        // other, which is what makes a half-known region read as half-known.
        // Read as a difference rather than against a threshold, because a neighbouring band's field
        // reaches into this one's lane — the existing `behind its own band` test only ever asserts
        // positively for that reason. What this has to show is that narrowing the stretch took ink
        // away at the dark end and left it at the lit one.
        val fold = Fold.full(width = WIDTH)
        val first = MapGeometry.firstSystemOf(0)
        val whole = bands(RegionTemperament.DEEP)
        val half = whole.mapIndexed { index, band ->
            if (index == 0) band.copy(charted = first..(first + 4)) else band
        }

        val before = render(mapOf(stars = emptyList(), bands = whole), fold)
        val after = render(mapOf(stars = emptyList(), bands = half), fold)

        val lane = MapGeometry.laneMidOf(band = 0).roundToInt()
        val inside = fold.x(first + 2).roundToInt()
        val outside = fold.x(first + MapGeometry.PER_BAND - 1).roundToInt()
        assertTrue(after[inside, lane].alpha > FIELD, "the charted end of the band has no field")
        assertTrue(
            after[outside, lane].alpha < before[outside, lane].alpha,
            "the dark end of the band kept the weather it had not earned",
        )
    }

    @Test
    fun `a band the light has never reached has no field at all`() {
        val fold = Fold.full(width = WIDTH)
        val dark = bands(RegionTemperament.DEEP).map { it.copy(charted = null) }

        val painted = render(mapOf(stars = emptyList(), bands = dark), fold)

        val centre = (fold.width / 2f).roundToInt()
        assertTrue(
            painted[centre, MapGeometry.laneMidOf(band = 0).roundToInt()].alpha < FIELD,
            "an uncharted band was given weather it has not earned",
        )
    }

    private fun render(uiState: GalaxyMapUiState, fold: Fold): PixelMap {
        val width = fold.width.roundToInt()
        val height = fold.height.roundToInt()
        return ImageBitmap(width, height).also { bitmap ->
            CanvasDrawScope().draw(
                density = Density(1f),
                layoutDirection = LayoutDirection.Ltr,
                canvas = Canvas(bitmap),
                size = Size(width.toFloat(), height.toFloat()),
            ) {
                drawFold(uiState = uiState, fold = fold)
            }
        }.toPixelMap()
    }
}

private const val WIDTH = 361f

// The bitmap starts empty, so any alpha at all is something the drawing put there. The two thresholds
// are different because the marks and the fields are drawn at deliberately different strengths — a
// region field is 5-10% by design, and holding it to the marks' bar would fail on the design rather
// than on the code.
private const val PAINTED = 0.05f
private const val FIELD = 0.01f

// The two ends of `core`'s size wobble, restated here because the tests above already restate them
// inline and the grain assertions need the narrow end by name.
private const val NARROWEST_PERMILLE = 870

// `GalaxyMap`'s own `SPIKE_REACH`. Private there, and a test that probes for the absence of a spike
// has to look where one would have been.
private const val SPIKE_REACH_PROBE = 2.6f

// Charted by default, and the two fixtures below say so together: every assertion written before
// the third tier existed is about a map the light has reached, and defaulting the other way would
// make nine of them fail on the fixture rather than on the drawing.
private fun star(system: Int, starClass: StarClass): MapStarUiState = MapStarUiState(
    system = system,
    driftPermille = 0,
    ink = MapStarInk.Charted(starClass = starClass, sizePermille = 1_000, coolHalo = false),
    marks = emptySet(),
)

private fun grain(system: Int): MapStarUiState = MapStarUiState(
    system = system,
    driftPermille = 0,
    ink = MapStarInk.Grain,
    marks = emptySet(),
)

private fun bands(temperament: RegionTemperament): List<MapBandUiState> =
    (1..MapGeometry.BANDS).map { region ->
        MapBandUiState(
            region = region,
            name = TextRes("Region $region"),
            temperament = temperament,
            charted = MapGeometry.firstSystemOf(region - 1)..(MapGeometry.firstSystemOf(region - 1) + MapGeometry.PER_BAND - 1),
            lit = region == 1,
        )
    }

private fun mapOf(
    stars: List<MapStarUiState>,
    bands: List<MapBandUiState> = bands(RegionTemperament.SETTLED),
): GalaxyMapUiState = GalaxyMapUiState(
    bands = bands,
    stars = stars,
    hours = emptyList(),
    names = emptyList(),
    mini = false,
)
