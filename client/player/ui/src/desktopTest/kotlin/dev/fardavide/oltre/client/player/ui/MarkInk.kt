package dev.fardavide.oltre.client.player.ui

import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.assertTrue

// **One bitmap per drawing, probed as many times as the assertion needs**, and that is a
// measurement rather than tidiness. `PlayerMarkTest.sampleAt` renders the whole 360×360 canvas once
// per sample, which is 240 renders for a single ray walk — affordable for one glyph and not for
// this slice, where six marks, eleven parts and twelve body-and-path pairs would come to thousands
// of bitmaps and the allocations behind them. `SettingsGlyphTest.inkArcsAt` already had the answer
// and kept it private: render once, then ask the pixels. This is that, lifted out so every mark test
// shares it.
//
// The unit and the padding are the ones `GlyphPixels` already defaults to, restated here because
// probing needs the same two numbers the rendering used: ten pixels to the box unit, so a 1.6-unit
// stroke is sixteen pixels wide and a gap is measurable to a tenth of a unit, and six units of air
// on every side so ink that leaves the box lands on canvas and can be measured instead of being
// silently clipped.
internal const val INK_UNIT = 10f
internal const val INK_PAD = 6f

// The air the box rule asks for. A glyph that filled its 24 units to the edge would be clipped by
// one container and not another, and the frame that caught it would read as a rendering difference
// rather than as a geometry error.
internal const val INK_AIR = 1f

internal fun markPixels(draw: DrawScope.() -> Unit): GlyphPixels =
    glyphPixels(unit = INK_UNIT, pad = INK_PAD, draw = draw)

// A single viewbox coordinate, asked of an already-rendered bitmap.
internal fun GlyphPixels.inkedAt(x: Float, y: Float): Boolean =
    inked(x = ((x + INK_PAD) * INK_UNIT).toInt(), y = ((y + INK_PAD) * INK_UNIT).toInt())

// One contiguous stretch of ink along a ray, in viewbox units from where the walk started.
internal data class InkRun(val from: Float, val to: Float)

// Where the ink starts and stops along a ray out of a point, which is the one measurement a 20dp
// baseline cannot make: a picture says the glyph changed, and this says *where the ink is*. The
// step is a fifth of a pixel, so a run's ends are quantised to the tenth of a unit a pixel is worth
// and no finer.
internal fun GlyphPixels.inkRunsAlong(fromX: Float, fromY: Float, degrees: Float, length: Float): List<InkRun> {
    val radians = degrees * RADIANS_PER_DEGREE
    val runs = mutableListOf<InkRun>()
    var start: Float? = null
    var previous = 0f
    var t = 0f
    while (t <= length) {
        val inked = inkedAt(x = fromX + t * cos(radians), y = fromY + t * sin(radians))
        if (inked && start == null) start = t
        if (!inked && start != null) {
            runs += InkRun(from = start, to = previous)
            start = null
        }
        previous = t
        t += RAY_STEP
    }
    start?.let { runs += InkRun(from = it, to = previous) }
    return runs
}

// How much ink there is at all, which is what an *absence* has to be asserted with: `bounds()`
// raises on an empty canvas, and a part that deliberately draws nothing would be checked by catching
// that exception — an assertion written as a failure. The side is recomputed from the same two
// numbers `glyphPixels` used, because `GlyphPixels` keeps its own.
internal fun GlyphPixels.inkedPixels(): Int {
    val side = ((MARK_VIEWBOX + 2 * INK_PAD) * INK_UNIT).toInt()
    var count = 0
    for (y in 0 until side) {
        for (x in 0 until side) {
            if (inked(x = x, y = y)) count++
        }
    }
    return count
}

// The bound a 20dp picture cannot state, and the one every drawing in this file's neighbourhood is
// held to. `what` is in the message because seventeen drawings go through it and "ink is right of
// the box" on its own says nothing about which one left.
internal fun assertInsideTheBox(ink: InkBounds, what: String) {
    val low = (INK_PAD + INK_AIR) * INK_UNIT
    val high = (INK_PAD + MARK_VIEWBOX - INK_AIR) * INK_UNIT
    assertTrue(ink.minX >= low, "$what: ink ${ink.minX} is left of the box")
    assertTrue(ink.minY >= low, "$what: ink ${ink.minY} is above the box")
    assertTrue(ink.maxX <= high, "$what: ink ${ink.maxX} is right of the box")
    assertTrue(ink.maxY <= high, "$what: ink ${ink.maxY} is below the box")
}

// Named rather than inlined because two files walk rays and a second copy of the step would be a
// second decision about how finely a gap is measured.
private const val RAY_STEP = 0.05f
private const val RADIANS_PER_DEGREE = 0.017453292f
