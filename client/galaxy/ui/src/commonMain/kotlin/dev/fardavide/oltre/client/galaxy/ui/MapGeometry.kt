package dev.fardavide.oltre.client.galaxy.ui

import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

// The fold, as arithmetic. **Separated from the drawing on purpose**: the property the whole design
// rests on — that path order is index order — is a claim about numbers, and a recorded frame of 250
// dots cannot tell a correct one from a transposed one. `MapGeometryTest` walks it; `GalaxyMap` only
// paints what this returns.
//
// Everything is in dp as a bare `Float`, not in `Dp` and not in pixels, for one reason: the same two
// functions place a circle inside a `DrawScope` (pixels) and a `Text` inside a `BoxWithConstraints`
// (dp), and a number that has to serve both cannot carry either unit. The two call sites convert.
// It is the idiom `SystemMap` already uses for exactly the same reason — a label has to land under
// its own world.
//
// **Public rather than internal, on `GalaxyTestTags`' precedent.** A star is not a tap target — the
// fold is one scrub surface — so a robot in `:client:galaxy:ui-testing` selects a system by landing
// on the drawing where that system is drawn, and it has to do the same arithmetic the Canvas does.
// A second copy of it in the test module would be a copy that could disagree with the drawing.
object MapGeometry {

    const val BANDS: Int = 10
    const val PER_BAND: Int = 25

    // The twenty-four gaps between twenty-five stars, which is what a pitch is measured over.
    private const val GAPS_PER_BAND: Int = PER_BAND - 1

    // 13 of label, 32 of lane, 9 of gap. Ten of those less the last gap is 531dp — the height Claude
    // Design drew the fold at, and the height it is drawn at wherever there is room for it.
    //
    // **It is a ceiling now rather than a fixed size, and a device is what changed that.** The sheet
    // put the content area at 587dp on a 393dp phone; the shipped 0.12.0 got less — a 55dp resource
    // rail, a 52dp tab bar and two safe-area insets come off the window before a destination sees any
    // of it — so 531 of map plus 22 of gap plus 58 of caption did not fit, and **the caption was
    // squeezed off the bottom of the screen.** The map is the map's only control, so that is a screen
    // that selects and cannot act: Davide, on the TestFlight build, *"I'm tapping on the systems, but
    // nothing happens."*
    //
    // The label row and the band gap are held and the **lane** is what gives, because a lane is the
    // one part of a band that is drawing rather than type: a name set at the 9.5sp floor cannot
    // shrink and a 32dp lane can.
    const val LABEL_ROW_DP: Float = 13f
    const val LANE_DP: Float = 32f
    const val BAND_GAP_DP: Float = 9f
    const val BAND_DP: Float = LABEL_ROW_DP + LANE_DP + BAND_GAP_DP
    const val HEIGHT_DP: Float = BAND_DP * BANDS - BAND_GAP_DP

    // The ribbon does not run to the edges: a star at the very edge has nowhere to put its halo, and
    // a name set beside it would be cut.
    const val INSET_DP: Float = 12f

    // A gentle wave along each lane, so a band is a drawn line rather than a ruled one. One and a
    // half turns across the band and a per-band phase, so no two lanes crest together. **The
    // amplitude is not here**: it is a `Fold`'s, because a disc waves a fifth as far as the map does
    // and one constant would have had to be right for both.
    private const val WAVE_TURNS: Float = 1.5f
    private const val WAVE_PHASE_PER_BAND: Float = 2.1f

    val labelRow = LABEL_ROW_DP.dp
    val lane = LANE_DP.dp
    val bandGap = BAND_GAP_DP.dp
    val band = BAND_DP.dp
    val height = HEIGHT_DP.dp
    val inset = INSET_DP.dp

    // Zero-based, because it indexes the band list. `regionOf` in core is one-based, because it names
    // a region — the two are the same fold counted from different ends of the same convention, and
    // keeping them apart is cheaper than a silent off-by-one at the boundary.
    fun bandOf(system: Int): Int = (system - 1) / PER_BAND

    fun columnOf(system: Int): Int = (system - 1) % PER_BAND

    fun firstSystemOf(band: Int): Int = band * PER_BAND + 1

    fun pitchOf(span: Float): Float = span / GAPS_PER_BAND

    // 0 at the band's entry, 1 at its exit — not 0 at its left edge. On an odd band the entry is the
    // right-hand end, which is what makes the two systems either side of a fold neighbours on the
    // drawing as well as in the index.
    fun pathFractionOf(system: Int): Float = columnOf(system).toFloat() / GAPS_PER_BAND

    fun xOf(system: Int, span: Float, inset: Float): Float {
        val fraction = pathFractionOf(system)
        val forward = bandOf(system) % 2 == 0
        return if (forward) inset + fraction * span else inset + (1f - fraction) * span
    }

    fun laneMidOf(band: Int): Float = laneMidOf(band = band, labelRow = LABEL_ROW_DP, lane = LANE_DP, gap = BAND_GAP_DP)

    // Ten bands and nine gaps into whatever height the fold was handed. Never taller than the design,
    // so a roomy window draws what the sheet drew rather than ten stretched lanes.
    fun bandHeightOf(height: Float): Float = (height.coerceAtMost(HEIGHT_DP) + BAND_GAP_DP) / BANDS

    // Parameterised because the universe view draws the same fold at a fifth of the size with no
    // label row and no gap between bands. **Same arithmetic, different numbers** — which is the
    // reason a disc is the real galaxy rather than a picture of one.
    fun laneMidOf(band: Int, labelRow: Float, lane: Float, gap: Float): Float =
        band * (labelRow + lane + gap) + labelRow + lane / 2f

    fun waveOf(band: Int, fraction: Float, amplitude: Float): Float =
        amplitude * sin(WAVE_TURNS * 2f * PI.toFloat() * fraction + band * WAVE_PHASE_PER_BAND)

    // Half a pitch at the extremes and no more. That cap is the reason the drift is allowed to exist
    // at all — it is what lets the band read as sky without the drawing ever putting two systems in
    // the wrong order.
    //
    // **The divisor is a thousand and it was two thousand until a test executed the drawing.** The
    // permille is already a signed fraction of the cap, so halving it again put every star at a
    // quarter pitch of travel where the design asked for a half — 3.5dp of wander instead of 7 at
    // 393dp, which is a band that reads as a ruled line with a wobble rather than as sky. Nothing
    // else moved: the cap the ordering depends on is the same number it always was.
    fun driftOf(permille: Int, pitch: Float): Float = pitch * permille / 1_000f

    // **The nearest star, not the cell the touch fell in** — and that is the opposite of the reach
    // strip's rule, deliberately. A strip tick owned a cell of the axis; a star here is a *point* on
    // the path with visible space either side of it, so what the finger means is the one it landed
    // nearest. Rounding is what says that.
    //
    // Both axes are clamped rather than rejected, so a drag that leaves the map keeps scrubbing along
    // its last edge instead of stopping dead under the thumb.
    fun systemAt(x: Float, y: Float, width: Float, height: Float): Int {
        val band = (y / bandHeightOf(height)).toInt().coerceIn(0, BANDS - 1)
        val span = (width - INSET_DP * 2f).coerceAtLeast(1f)
        val fromLeft = ((x - INSET_DP) / span).coerceIn(0f, 1f)
        val fraction = if (band % 2 == 0) fromLeft else 1f - fromLeft
        val column = (fraction * GAPS_PER_BAND).roundToInt().coerceIn(0, GAPS_PER_BAND)
        return firstSystemOf(band) + column
    }
}
