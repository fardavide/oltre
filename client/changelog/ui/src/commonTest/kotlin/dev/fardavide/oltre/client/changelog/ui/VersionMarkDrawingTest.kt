package dev.fardavide.oltre.client.changelog.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.fardavide.oltre.client.changelog.domain.ReleaseVersion
import dev.fardavide.oltre.client.changelog.domain.skyAt
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

// **The one thing the geometry cannot prove: that the drawing walks it.** `VersionSkyTest` says where
// every body is; this says the ink is there — rasterised, and read back pixel by pixel.
//
// It exists because the two halves can drift silently. A `drawCircle` handed the wrong centre, a
// stroke that never runs, a `filled` branch inverted: none of it moves a domain property, and at 20dp
// none of it moves a baseline either — which is exactly the reason the design asked for the mark to
// be checked in numbers rather than in pictures.
class VersionMarkDrawingTest {

    @Test
    fun `a body is drawn where the sky puts it`() {
        val version = ReleaseVersion(0, 12, 0)
        val pixels = draw(version)
        val sky = version.skyAt(SIDE.toFloat())

        for (body in sky.bodies) {
            val lit = pixels[body.x.roundToInt(), body.y.roundToInt()]

            assertTrue(lit.alpha > 0f, "nothing is drawn at (${body.x}, ${body.y})")
        }
    }

    @Test
    fun `the empty sky between bodies is left empty`() {
        // The other half, and without it the first assertion passes on a mark that fills its box.
        // The centre of the disc is the one place the spiral never puts a body: the radius is
        // `R x sqrt(i / N)` and `i` starts at one, so nothing is drawn within a body's width of it.
        val pixels = draw(ReleaseVersion(0, 12, 0))

        assertTrue(pixels[SIDE / 2, (0.44f * SIDE).roundToInt()].alpha == 0f, "the centre is not empty")
    }

    @Test
    fun `a filled body reads heavier than a hollow one`() {
        // The `filled` branch, which is the whole of what a minor line looks like against a patch —
        // and the one line of the drawing that an inversion would leave the geometry happy about.
        // A disc is solid to its edge; a ring is a stroke around a hole, so its own centre is empty.
        val version = ReleaseVersion(0, 1, 1)
        val pixels = draw(version)
        val sky = version.skyAt(SIDE.toFloat())
        val filled = sky.bodies.first { it.filled }
        val hollow = sky.bodies.first { !it.filled }

        assertTrue(pixels[filled.x.roundToInt(), filled.y.roundToInt()].alpha > 0f, "the disc is hollow")
        assertTrue(pixels[hollow.x.roundToInt(), hollow.y.roundToInt()].alpha == 0f, "the ring is solid")
    }

    @Test
    fun `the limb is drawn across the foot of the mark`() {
        val version = ReleaseVersion(0, 12, 0)
        val pixels = draw(version)
        val crest = version.skyAt(SIDE.toFloat()).limb.crestY.roundToInt()

        // At the crest, in the middle of the box, and nowhere near a body: only the horizon is there.
        assertTrue(
            (crest..crest + 2).any { y -> pixels[SIDE / 2, y].alpha > 0f },
            "the horizon is missing at y = $crest",
        )
    }

    @Test
    fun `a major draws its world`() {
        // 1.0.0 empties the sky, so the only ink on it is the world the major finished. If the world
        // were not drawn, the whole mark would be blank and nothing else would notice.
        val version = ReleaseVersion(1, 0, 0)
        val pixels = draw(version)
        val world = version.skyAt(SIDE.toFloat()).worlds.single()

        assertTrue(
            pixels[world.x.roundToInt(), (world.y - world.radius / 2).roundToInt()].alpha > 0f,
            "the world is not drawn",
        )
    }

    // One density, so a dp is a pixel and the geometry's own numbers index the bitmap directly.
    private fun draw(version: ReleaseVersion) = ImageBitmap(SIDE, SIDE).also { bitmap ->
        CanvasDrawScope().draw(
            density = Density(density = 1f, fontScale = 1f),
            layoutDirection = LayoutDirection.Ltr,
            canvas = Canvas(bitmap),
            size = Size(SIDE.toFloat(), SIDE.toFloat()),
        ) {
            drawVersionMark(version = version, side = SIDE.dp)
        }
    }.toPixelMap()

    private companion object {

        // The page's own mark at the reference width, so what is rasterised is the size a player
        // actually looks at rather than a size chosen for a test.
        const val SIDE = 319
    }
}
