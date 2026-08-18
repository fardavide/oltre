package dev.fardavide.oltre.client.galaxy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.resolve
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.world.ui.drawWorldPortrait
import dev.fardavide.oltre.core.RegionTemperament
import dev.fardavide.oltre.core.StarClass

// **The galaxy, drawn.** Ten bands of twenty-five stars, folded so that path order is index order,
// with a drawn turn at each end so the ten read as one line rather than as a grid. The argument for
// the shape is in `GalaxyMapUiState`; the arithmetic is in `MapGeometry`, tested; this file only
// paints.
//
// One Canvas for every mark, in one pass, and laid-out `Text` for every string — which is the rule
// the module already keeps: no Canvas in `:client:galaxy:ui` draws text, because a number that goes
// through a different text stack from the rest of the app is a number that will not match its
// baseline on another machine.
//
// **The one lie the fold tells, stated so the next reader does not have to find it.** Two stars
// stacked across a band gap are twenty-five systems apart and drawn 22dp apart; horizontally,
// twenty-five systems is 337dp. That is a fifteenfold understatement in one direction, and it is the
// price of folding — a straight line at true pitch is 3,500dp and fits on nothing. Three things pay
// it down: the turn is *drawn*, so the eye is handed the path; the labels alternate sides, which
// states the reading direction without an arrow; and vertical neighbours are always in different
// named regions, which is the strongest "not next door" signal the map has.
@Composable
internal fun GalaxyMap(
    uiState: GalaxyMapUiState,
    onSelectSystem: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = MapGeometry.height)
            .testTag(GalaxyTestTags.GALAXY_MAP),
    ) {
        // **The height it was given, not the height it would like.** A fixed 531dp here is what put
        // the caption off the bottom of a real phone: the map is the only thing on this screen that
        // can afford to give, because the caption is the map's one control and the head is type.
        val fold = Fold.full(width = maxWidth.value, height = maxHeight.value)
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightDp = maxHeight.value
        // **A tap snaps the selection to the nearest star and a drag scrubs it**, which is what makes
        // 250 targets smaller than a fingertip cost nothing to miss. There is no zoom and no pan: a
        // pinch would buy names for unpinned systems and nothing else, and the caption already gives
        // you those one at a time under your thumb — with no camera to restore on a foreground.
        val systemAt = { offset: Offset ->
            MapGeometry.systemAt(
                x = offset.x / density.density,
                y = offset.y / density.density,
                width = maxWidth.value,
                height = heightDp,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(widthPx) {
                    detectDragGestures { change, _ -> onSelectSystem(systemAt(change.position)) }
                }
                .pointerInput(widthPx) {
                    detectTapGestures { offset -> onSelectSystem(systemAt(offset)) }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) { drawFold(uiState = uiState, fold = fold) }
        }
        uiState.bands.forEachIndexed { index, band ->
            RegionLabel(band = band, band0 = index, fold = fold, inset = MapGeometry.inset)
        }
        uiState.hours.forEach { hour -> HourLabel(hour = hour, fold = fold) }
        uiState.names.forEach { name -> NameLabel(name = name, fold = fold) }
    }
}

// The same fold at a fifth of the size, with no label row, no gap, no hour marks and no spikes.
// Nothing is interactive: a disc is a summary you tap as a whole, and the card around it owns that.
@Composable
internal fun GalaxyDisc(uiState: GalaxyMapUiState, lane: Dp, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val fold = Fold.mini(width = maxWidth.value, lane = lane.value)
        Canvas(modifier = Modifier.fillMaxWidth().height(fold.height.dp).clipToBounds()) {
            drawFold(uiState = uiState, fold = fold)
        }
    }
}

// Every number the drawing needs, resolved once from the width it was handed. **In dp, not pixels**,
// because the labels are laid out in dp from the same values and a name has to land beside its own
// star — the idiom `SystemMap` established for exactly that reason.
internal data class Fold(
    val width: Float,
    // What the fold was actually handed. Never more than the design's 531dp — a roomy window draws
    // what the sheet drew — and as much less as the screen demands.
    val height: Float,
    val inset: Float,
    val labelRow: Float,
    val gap: Float,
    val dim: Float,
    val standard: Float,
    val bright: Float,
    val mini: Boolean,
) {

    val span: Float get() = width - inset * 2f
    val pitch: Float get() = MapGeometry.pitchOf(span)
    val bandHeight: Float get() = (height + gap) / MapGeometry.BANDS

    // **The lane is what gives.** The label row is type at the 9.5sp floor and cannot shrink, and the
    // gap between bands is the one signal that vertical neighbours are 25 systems apart — so what a
    // tight screen takes is the room the stars drift in, which is the only part of a band that is
    // drawing rather than reading.
    val lane: Float get() = bandHeight - labelRow - gap

    // The wave rides the lane rather than a constant, for the same reason the drift is capped by it:
    // at the design's 32dp lane this is the 5.5dp Claude Design drew, and it narrows with the lane
    // instead of filling a shorter one.
    val amplitude: Float get() = if (mini) lane * MINI_WAVE_OF_LANE else lane * FULL_WAVE_OF_LANE

    fun x(system: Int): Float = MapGeometry.xOf(system = system, span = span, inset = inset)

    // **Two caps, and the second one only bites on a squeezed screen.** The drift is half a pitch,
    // which is the ordering promise — a star may never cross the midpoint between itself and its
    // neighbour. But half a pitch is a *horizontal* figure and the room a star has is *vertical*, so
    // on a phone that folds the map into 26dp lanes instead of 32 the same 7dp of wander walks the
    // stars up into the region name above them. The lane is what gives, so what the lane can hold has
    // to give with it.
    fun y(system: Int, driftPermille: Int): Float {
        val band = MapGeometry.bandOf(system)
        val laneMid = MapGeometry.laneMidOf(band = band, labelRow = labelRow, lane = lane, gap = gap)
        val wave = MapGeometry.waveOf(
            band = band,
            fraction = MapGeometry.pathFractionOf(system),
            amplitude = amplitude,
        )
        val room = lane * LANE_DRIFT_SHARE
        val drift = MapGeometry.driftOf(permille = driftPermille, pitch = pitch).coerceIn(-room, room)
        return laneMid + wave + drift
    }

    fun laneTopOf(band: Int): Float = band * bandHeight + labelRow

    fun radiusOf(starClass: StarClass, sizePermille: Int): Float {
        val base = when (starClass) {
            StarClass.DIM -> dim
            StarClass.STANDARD -> standard
            StarClass.BRIGHT -> bright
        }
        return base * sizePermille / 1_000f
    }

    companion object {

        fun full(width: Float, height: Float = MapGeometry.HEIGHT_DP): Fold = Fold(
            width = width,
            height = height.coerceAtMost(MapGeometry.HEIGHT_DP),
            inset = MapGeometry.INSET_DP,
            labelRow = MapGeometry.LABEL_ROW_DP,
            gap = MapGeometry.BAND_GAP_DP,
            dim = 1.3f,
            standard = 1.9f,
            bright = 2.6f,
            mini = false,
        )

        // A disc's lane comes from the card it sits in rather than from a constant, so the four of
        // them fill whatever grid the universe view gives them. Everything else scales off it.
        fun mini(width: Float, lane: Float): Fold = Fold(
            width = width,
            height = lane * MapGeometry.BANDS,
            inset = MINI_INSET_DP,
            labelRow = 0f,
            gap = 0f,
            dim = 0.8f,
            standard = 1.15f,
            bright = 1.6f,
            mini = true,
        )
    }
}

// Internal rather than private, for the reason `drawWorldPortrait` is: the drawing is separable from
// the composable that hosts it, and separating it is what lets a test *execute* it against a
// `CanvasDrawScope` over an `ImageBitmap` and read back where a star landed. A recorded frame says
// the fold looks like something; only this says it agrees with `MapGeometry`.
internal fun DrawScope.drawFold(uiState: GalaxyMapUiState, fold: Fold) {
    uiState.bands.forEachIndexed { index, band -> drawRegionField(band = band, band0 = index, fold = fold) }
    drawSpines(fold)
    drawTurns(fold)
    if (!fold.mini) uiState.hours.forEach { hour -> drawHourMark(system = hour.system, fold = fold) }
    uiState.stars.forEach { star -> drawHalo(star = star, fold = fold) }
    if (!fold.mini) uiState.stars.forEach { star -> drawSpikes(star = star, fold = fold) }
    uiState.stars.forEach { star -> drawStar(star = star, fold = fold) }
    uiState.stars.forEach { star -> drawMarks(star = star, fold = fold) }
}

// The region as weather rather than as a boundary: a wide, flat radial fade behind each band, at
// 5–9% and under everything. The hue follows the temperament the way the world portrait's ramp
// does — Deep leans the deuterium violet, a Reach is the metal grey, a Blaze is white-hot — so the
// field and the star colours are saying the same thing twice rather than two different things.
private fun DrawScope.drawRegionField(band: MapBandUiState, band0: Int, fold: Fold) {
    val centreY = MapGeometry
        .laneMidOf(band = band0, labelRow = fold.labelRow, lane = fold.lane, gap = fold.gap)
        .dp
        .toPx()
    val radiusX = (fold.width / 2f).dp.toPx()
    val radiusY = (fold.lane * FIELD_OF_LANE).dp.toPx()
    val centre = Offset(x = size.width / 2f, y = centreY)
    val hue = when (band.temperament) {
        RegionTemperament.DEEP -> OltreColors.deuterium.copy(alpha = FIELD_DEEP_ALPHA)
        RegionTemperament.SETTLED -> OltreColors.metal.copy(alpha = FIELD_SETTLED_ALPHA)
        RegionTemperament.BURNING -> BLAZE_WHITE.copy(alpha = FIELD_BURNING_ALPHA)
    }
    // A circular gradient squashed onto the lane, rather than an oval filled with one: `drawOval`
    // with a radial brush clips a circle to the oval and fades along one axis only, which reads as a
    // hard edge top and bottom exactly where the field is meant to be softest.
    scale(scaleX = 1f, scaleY = radiusY / radiusX, pivot = centre) {
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(0f to hue, 1f to Color.Transparent),
                center = centre,
                radius = radiusX,
            ),
            radius = radiusX,
            center = centre,
        )
    }
}

// The lane itself, as the polyline the stars sit on. Twenty-five samples rather than a curve, because
// the wave is sampled at the stars and a smooth path between them would not pass through them.
private fun DrawScope.drawSpines(fold: Fold) {
    for (band in 0 until MapGeometry.BANDS) {
        val path = Path()
        for (column in 0 until MapGeometry.PER_BAND) {
            val system = MapGeometry.firstSystemOf(band) + column
            val x = fold.x(system).dp.toPx()
            val y = fold.y(system = system, driftPermille = 0).dp.toPx()
            if (column == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = Color.White.copy(alpha = SPINE_ALPHA), style = Stroke(width = HAIRLINE.toPx()))
    }
}

// **The turn is drawn, and that is what makes this a fold rather than a table.** Nine of them, each
// bulging past the edge it turns at, so the eye is given the path from system 25 to system 26
// instead of being left to infer it.
private fun DrawScope.drawTurns(fold: Fold) {
    for (band in 0 until MapGeometry.BANDS - 1) {
        val last = MapGeometry.firstSystemOf(band) + MapGeometry.PER_BAND - 1
        val next = last + 1
        val fromX = fold.x(last).dp.toPx()
        val fromY = fold.y(system = last, driftPermille = 0).dp.toPx()
        val toX = fold.x(next).dp.toPx()
        val toY = fold.y(system = next, driftPermille = 0).dp.toPx()
        val bulge = if (band % 2 == 0) (fold.width - TURN_BULGE_DP).dp.toPx() else TURN_BULGE_DP.dp.toPx()
        val path = Path().apply {
            moveTo(fromX, fromY)
            cubicTo(x1 = bulge, y1 = fromY, x2 = bulge, y2 = toY, x3 = toX, y3 = toY)
        }
        drawPath(path = path, color = Color.White.copy(alpha = TURN_ALPHA), style = Stroke(width = HAIRLINE.toPx()))
    }
}

// A whole hour of probe flight, as a hairline across its band. The reach strip used to be a component
// under the map; on an index-monotone path it is four lines *on* it, and the strip stops existing.
private fun DrawScope.drawHourMark(system: Int, fold: Fold) {
    val band = MapGeometry.bandOf(system)
    val x = fold.x(system).dp.toPx()
    val top = (fold.laneTopOf(band) + HOUR_MARK_MARGIN_DP).dp.toPx()
    val bottom = (fold.laneTopOf(band) + fold.lane - HOUR_MARK_MARGIN_DP).dp.toPx()
    drawLine(
        color = Color.White.copy(alpha = HOUR_MARK_ALPHA),
        start = Offset(x = x, y = top),
        end = Offset(x = x, y = bottom),
        strokeWidth = HAIRLINE.toPx(),
    )
}

// Halos are radial gradients, which the design system otherwise forbids — legal here because a star
// is the one thing it lets glow. A third of the brights lean the crystal hue, which is variety inside
// a class mixed from a resource colour, never a status one.
private fun DrawScope.drawHalo(star: MapStarUiState, fold: Fold) {
    // A disc is 1/5 the size, so only the brights are still worth a halo at all — the other two
    // would be a gradient two pixels across, which is a cost with nothing on the other side of it.
    if (fold.mini && star.starClass != StarClass.BRIGHT) return
    val radius = fold.radiusOf(star.starClass, star.sizePermille)
    val centre = Offset(x = fold.x(star.system).dp.toPx(), y = fold.y(star.system, star.driftPermille).dp.toPx())
    val stops = when {
        star.starClass == StarClass.BRIGHT && star.coolHalo -> arrayOf(
            0f to COOL_HALO.copy(alpha = 0.20f),
            0.45f to COOL_HALO.copy(alpha = 0.08f),
            1f to Color.Transparent,
        )

        star.starClass == StarClass.BRIGHT -> arrayOf(
            0f to BLAZE_WHITE.copy(alpha = 0.24f),
            0.45f to BRIGHT_HALO.copy(alpha = 0.10f),
            1f to Color.Transparent,
        )

        star.starClass == StarClass.STANDARD -> arrayOf(
            0f to STANDARD_HALO.copy(alpha = 0.14f),
            1f to Color.Transparent,
        )

        else -> arrayOf(0f to OltreColors.deuterium.copy(alpha = 0.12f), 1f to Color.Transparent)
    }
    val reach = when {
        star.starClass == StarClass.BRIGHT -> if (fold.mini) MINI_BRIGHT_HALO_REACH else BRIGHT_HALO_REACH
        star.starClass == StarClass.STANDARD -> STANDARD_HALO_REACH
        else -> DIM_HALO_REACH
    }
    val haloRadius = (radius * reach).dp.toPx()
    drawCircle(
        brush = Brush.radialGradient(colorStops = stops, center = centre, radius = haloRadius),
        radius = haloRadius,
        center = centre,
    )
}

private fun DrawScope.drawSpikes(star: MapStarUiState, fold: Fold) {
    if (star.starClass != StarClass.BRIGHT) return
    val reach = (fold.radiusOf(star.starClass, star.sizePermille) * SPIKE_REACH).dp.toPx()
    val centre = Offset(x = fold.x(star.system).dp.toPx(), y = fold.y(star.system, star.driftPermille).dp.toPx())
    val ink = BLAZE_WHITE.copy(alpha = SPIKE_ALPHA)
    drawLine(
        color = ink,
        start = Offset(centre.x - reach, centre.y),
        end = Offset(centre.x + reach, centre.y),
        strokeWidth = SPIKE_WIDTH.toPx(),
        cap = StrokeCap.Round,
    )
    drawLine(
        color = ink,
        start = Offset(centre.x, centre.y - reach),
        end = Offset(centre.x, centre.y + reach),
        strokeWidth = SPIKE_WIDTH.toPx(),
        cap = StrokeCap.Round,
    )
}

// **Heat is brightness and the cold end leans violet** — the same call the world portrait's ramp
// obeys, which is that no status hue ever lands on a celestial body. Size and luminance are both
// class, so a surveyed dim star and an unsurveyed bright one can never be confused.
private fun DrawScope.drawStar(star: MapStarUiState, fold: Fold) {
    drawCircle(
        color = when (star.starClass) {
            StarClass.DIM -> DIM_STAR
            StarClass.STANDARD -> STANDARD_STAR
            StarClass.BRIGHT -> BLAZE_WHITE
        },
        radius = fold.radiusOf(star.starClass, star.sizePermille).dp.toPx(),
        center = Offset(x = fold.x(star.system).dp.toPx(), y = fold.y(star.system, star.driftPermille).dp.toPx()),
    )
}

// What you know, as rings outside the disc — four channels that stack rather than one that wins.
// Amber is the fleet strip's amber and means there what it means here.
private fun DrawScope.drawMarks(star: MapStarUiState, fold: Fold) {
    if (star.marks.isEmpty()) return
    val centre = Offset(x = fold.x(star.system).dp.toPx(), y = fold.y(star.system, star.driftPermille).dp.toPx())
    val radius = fold.radiusOf(star.starClass, star.sizePermille)
    val scale = if (fold.mini) MINI_RING_SCALE else 1f
    // Drawn in radius order rather than in declaration order, so the outer rings never sit under an
    // inner one on a star that carries three of them.
    if (MapStarMark.SURVEYED in star.marks) {
        // The one ring measured from the star's own radius rather than from a constant: it sits just
        // outside the disc, so a bright star's ring has to be wider than a dim one's or it would
        // land on the ink.
        val gap = if (fold.mini) MINI_SURVEYED_RING_GAP else SURVEYED_RING_GAP
        drawRing(centre, radius + gap, OltreColors.text.copy(alpha = SURVEYED_RING_ALPHA), HAIRLINE)
    }
    if (MapStarMark.IN_FLIGHT in star.marks) {
        drawRing(centre, IN_FLIGHT_RING * scale, OltreColors.warn, IN_FLIGHT_RING_WIDTH)
    }
    if (MapStarMark.HOME in star.marks) {
        drawRing(centre, HOME_RING * scale, OltreColors.text.copy(alpha = 0.55f), HOME_RING_WIDTH)
    }
    if (MapStarMark.SELECTED in star.marks) {
        drawRing(centre, SELECTED_RING * scale, OltreColors.accent, SELECTED_RING_WIDTH)
    }
}

private fun DrawScope.drawRing(centre: Offset, radiusDp: Float, colour: Color, width: Dp) {
    drawCircle(
        color = colour,
        radius = radiusDp.dp.toPx(),
        center = centre,
        style = Stroke(width = width.toPx()),
    )
}

// Ten names, all legible at once, which is the whole thing the second dimension was bought for. They
// alternate sides, and that alternation states the reading direction without an arrow.
@Composable
private fun RegionLabel(band: MapBandUiState, band0: Int, fold: Fold, inset: Dp) {
    Box(modifier = Modifier.fillMaxWidth().offset(y = (band0 * fold.bandHeight).dp)) {
        Text(
            text = band.name.resolve().uppercase(),
            color = if (band.lit) OltreColors.text else OltreColors.textTertiary,
            fontFamily = oltreMono(),
            fontSize = LABEL_SIZE,
            letterSpacing = LABEL_TRACKING,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(if (band0 % 2 == 0) Alignment.TopStart else Alignment.TopEnd)
                .offset(x = if (band0 % 2 == 0) inset else -inset),
        )
    }
}

@Composable
private fun HourLabel(hour: MapHourUiState, fold: Fold) {
    val x = fold.x(hour.system)
    val band = MapGeometry.bandOf(hour.system)
    val onTheRight = x < fold.width - HOUR_LABEL_MARGIN_DP
    Text(
        text = hour.label.resolve(),
        color = OltreColors.textTertiary,
        fontFamily = oltreMono(),
        fontSize = LABEL_SIZE,
        letterSpacing = LABEL_TRACKING,
        maxLines = 1,
        softWrap = false,
        textAlign = if (onTheRight) TextAlign.Start else TextAlign.End,
        modifier = Modifier
            .offset(
                x = if (onTheRight) {
                    (x + HOUR_LABEL_GAP_DP).dp
                } else {
                    (x - HOUR_LABEL_GAP_DP).dp - HOUR_LABEL_WIDTH
                },
                y = (fold.laneTopOf(band) + fold.lane - HOUR_LABEL_LIFT_DP).dp,
            )
            .width(HOUR_LABEL_WIDTH),
    )
}

// **A pin is what makes a name appear**, and that is the whole of search on a map. Set beside the
// star rather than above it, so a name never lands in the region's label row.
@Composable
private fun NameLabel(name: MapNameUiState, fold: Fold) {
    val x = fold.x(name.system)
    val toTheLeft = x > fold.width / 2f
    Text(
        text = name.name.resolve(),
        color = when (name.tone) {
            MapNameTone.HOME -> OltreColors.text
            MapNameTone.SELECTED -> OltreColors.accent
            MapNameTone.PINNED -> OltreColors.textSecondary
        },
        fontFamily = oltreMono(),
        fontWeight = if (name.tone == MapNameTone.HOME) FontWeight.Bold else FontWeight.Normal,
        fontSize = LABEL_SIZE,
        maxLines = 1,
        softWrap = false,
        textAlign = if (toTheLeft) TextAlign.End else TextAlign.Start,
        modifier = Modifier
            .offset(
                x = if (toTheLeft) (x - NAME_GAP_DP).dp - NAME_WIDTH else (x + NAME_GAP_DP).dp,
                y = (fold.y(name.system, driftPermille = 0) - NAME_LIFT_DP).dp,
            )
            .width(NAME_WIDTH),
    )
}

private val HAIRLINE = 1.dp
// 5.5dp of wave in the design's 32dp lane, and 22% of the lane for the drift — which is the 7dp
// Claude Design drew at the full size, arrived at as a share so it survives a shorter one.
private const val FULL_WAVE_OF_LANE = 5.5f / 32f
private const val MINI_WAVE_OF_LANE = 0.20f
private const val LANE_DRIFT_SHARE = 0.22f
private const val MINI_INSET_DP = 3f
private const val TURN_BULGE_DP = 1.5f

private const val SPINE_ALPHA = 0.055f
private const val TURN_ALPHA = 0.09f
private const val HOUR_MARK_ALPHA = 0.18f
private const val HOUR_MARK_MARGIN_DP = 1f

private const val FIELD_OF_LANE = 0.95f
private const val FIELD_DEEP_ALPHA = 0.10f
private const val FIELD_SETTLED_ALPHA = 0.05f
private const val FIELD_BURNING_ALPHA = 0.09f

private const val BRIGHT_HALO_REACH = 4.2f
private const val MINI_BRIGHT_HALO_REACH = 3f
private const val STANDARD_HALO_REACH = 2.4f
private const val DIM_HALO_REACH = 2.2f

private const val SPIKE_REACH = 2.6f
private const val SPIKE_ALPHA = 0.20f
private val SPIKE_WIDTH = 0.8.dp

private const val SURVEYED_RING_GAP = 2.6f
private const val MINI_SURVEYED_RING_GAP = 1.4f
private const val SURVEYED_RING_ALPHA = 0.30f
private const val IN_FLIGHT_RING = 5.4f
private val IN_FLIGHT_RING_WIDTH = 1.3.dp
private const val HOME_RING = 6.4f
private val HOME_RING_WIDTH = 1.2.dp
private const val SELECTED_RING = 8.2f
private val SELECTED_RING_WIDTH = 1.4.dp
private const val MINI_RING_SCALE = 0.45f

private val LABEL_SIZE = 9.5.sp
private val LABEL_TRACKING = 1.sp
private val HOUR_LABEL_WIDTH = 20.dp
private const val HOUR_LABEL_GAP_DP = 4f
private const val HOUR_LABEL_LIFT_DP = 2f
private const val HOUR_LABEL_MARGIN_DP = 24f
private val NAME_WIDTH = 96.dp
private const val NAME_GAP_DP = 11f
private const val NAME_LIFT_DP = 6f

// Three ink colours the palette does not name, and each is a lightened member of it rather than a
// new hue: a dim star is the deuterium violet lifted until it reads as a star, a standard one is the
// body text cooled a shade, and a bright one is the white the design system reserves for the hottest
// thing on a screen. The cool halo is the crystal blue lifted the same way.
private val DIM_STAR = Color(0xFFBAA4F0).copy(alpha = 0.60f)
private val STANDARD_STAR = Color(0xFFE2E8F5).copy(alpha = 0.75f)
private val BLAZE_WHITE = Color(0xFFF5FAFF)
private val BRIGHT_HALO = Color(0xFFE9F1FC)
private val STANDARD_HALO = Color(0xFFE4EAF5)
private val COOL_HALO = Color(0xFFBFE9F5)
