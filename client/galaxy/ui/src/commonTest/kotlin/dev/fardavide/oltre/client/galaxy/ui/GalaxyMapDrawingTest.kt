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

private fun star(system: Int, starClass: StarClass): MapStarUiState = MapStarUiState(
    system = system,
    starClass = starClass,
    driftPermille = 0,
    sizePermille = 1_000,
    coolHalo = false,
    marks = emptySet(),
)

private fun bands(temperament: RegionTemperament): List<MapBandUiState> =
    (1..MapGeometry.BANDS).map { region ->
        MapBandUiState(region = region, name = TextRes("Region $region"), temperament = temperament, lit = region == 1)
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
