package dev.fardavide.oltre.client.galaxy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.fardavide.oltre.client.design.core.LocalTranslations
import dev.fardavide.oltre.client.design.core.OltreColors
import dev.fardavide.oltre.client.design.core.oltreMono
import dev.fardavide.oltre.client.design.text.Translations
import dev.fardavide.oltre.client.world.ui.WorldPortraitUiState
import dev.fardavide.oltre.client.world.ui.drawWorldPortrait
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.StarClass
import dev.fardavide.oltre.core.SystemAddress
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

// **One canvas, five depths, no screens between them.** The universe, a galaxy, a region, a system
// and a world are five zooms of this one drawing — everything here is a function of `SkyView`,
// and `SkyGeometry`'s four ramps decide what is legible at the zoom rather than a switch deciding
// what is drawn. A pinch never crosses a boundary because there is none to cross.
//
// The one gesture: a pan and a pinch, which `SkyScene.zoomedAt` anchors on a body once the orbit
// view is in; a tap, which selects, and a second tap on the selection, which dives — so a quick
// double tap is a dive, without the delay a double-tap detector would put on every single tap.
// A tap that dives flies the `SkyViewState`, the same way the bar does.
//
// Labels are drawn on the canvas rather than laid as composables — a region's name is set along
// its arm, and the collision pass needs measured widths — and the robots get tagged, sizeless
// anchors at every tappable thing instead: a `performClick` on one lands on the canvas underneath.
@Composable
fun UniverseCanvas(
    scene: SkyScene,
    viewState: SkyViewState,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer(cacheSize = LABEL_CACHE)
    val translations = LocalTranslations.current
    val mono = oltreMono()
    // The scene is rebuilt on every tick of the caption's countdown; the dust outlives it.
    val dust = remember(scene.home.galaxy) { SkyDust() }
    val paint = remember(scene, translations, measurer, mono, dust) { SkyPaint(scene, translations, measurer, mono, dust) }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    // Read through the latest value inside the gesture loops, which outlive any one composition.
    val latestScene by rememberUpdatedState(scene)

    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds().testTag(GalaxyTestTags.SKY)) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val viewport = SkyViewport(widthPx, heightPx)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val vp = SkyViewport(size.width.toFloat(), size.height.toFloat())
                        var next = viewState.view
                        if (zoom != 1f) next = latestScene.zoomedAt(next, centroid.x, centroid.y, zoom, vp)
                        if (pan != Offset.Zero) next = next.panned(pan.x, pan.y)
                        viewState.view = latestScene.settled(next, vp)
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val vp = SkyViewport(size.width.toFloat(), size.height.toFloat())
                        val move = latestScene.tapped(viewState.view, offset.x, offset.y, vp)
                        if (move.flown) scope.launch { viewState.fly(move.view) } else viewState.view = move.view
                    }
                }
                // The wheel, for the desktop loop: a notch is a step of zoom about the pointer.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type != PointerEventType.Scroll) continue
                            val change = event.changes.firstOrNull() ?: continue
                            val vp = SkyViewport(size.width.toFloat(), size.height.toFloat())
                            val factor = exp(-change.scrollDelta.y * WHEEL_STEP)
                            val next = latestScene.zoomedAt(viewState.view, change.position.x, change.position.y, factor, vp)
                            viewState.view = latestScene.settled(next, vp)
                            change.consume()
                        }
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) { with(paint) { drawSky(viewState.view) } }
        }

        for (anchor in scene.anchors(viewState.view, viewport)) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(anchor.x.roundToInt(), anchor.y.roundToInt()) }
                    .size(1.dp)
                    .testTag(anchor.tag),
            )
        }
    }
}

// A tappable thing and where it is on screen, for the robots. Exactly the things `SkyScene.nearest`
// can return at this depth: galaxies at the universe, regions at the galaxy, stars past it, and the
// worlds of the selected system once the orbit view is in.
data class SkyAnchor(val tag: String, val x: Float, val y: Float)

internal fun SkyScene.anchors(view: SkyView, viewport: SkyViewport): List<SkyAnchor> {
    val anchors = mutableListOf<SkyAnchor>()
    val depth = view.depth
    if (depth == SkyDepth.UNIVERSE) {
        for (galaxy in 1..GalaxyBalance.GALAXIES) {
            val q = view.screenOf(sky.centreOf(galaxy), viewport)
            anchors += SkyAnchor(GalaxyTestTags.galaxy(galaxy), q.x, q.y)
        }
        return anchors
    }
    for (galaxy in 1..GalaxyBalance.GALAXIES) {
        if (!inView(view, galaxy, viewport)) continue
        if (depth == SkyDepth.GALAXY) {
            for (region in 1..GalaxyBalance.REGIONS_PER_GALAXY) {
                val q = view.screenOf(sky.regionCentreOf(galaxy, region), viewport)
                if (q.onScreen(viewport)) anchors += SkyAnchor(GalaxyTestTags.region(galaxy, region), q.x, q.y)
            }
        } else {
            for (star in galaxyOf(galaxy).stars) {
                val q = view.screenOf(positionOf(SystemAddress(galaxy, star.system)), viewport)
                if (q.onScreen(viewport)) anchors += SkyAnchor(GalaxyTestTags.system(galaxy, star.system), q.x, q.y)
            }
        }
    }
    val system = view.selection.system
    if ((depth == SkyDepth.SYSTEM || depth == SkyDepth.WORLD) && system != null) {
        for (body in starOf(system).worlds) {
            val at = GalaxyCoordinate(system.galaxy, system.system, body.slot)
            val q = view.screenOf(worldPositionOf(at), viewport)
            if (q.onScreen(viewport)) anchors += SkyAnchor(GalaxyTestTags.world(at), q.x, q.y)
        }
    }
    return anchors
}

private fun SkyPoint.onScreen(viewport: SkyViewport): Boolean =
    x >= 0f && y >= 0f && x <= viewport.width && y <= viewport.height

// **The drawing, separated from the composable that hosts it**, for the reason `drawWorldPortrait`
// is: a `Canvas` body cannot be reached by anything but a rendered frame, and this is `DrawScope`
// code, so a test can hand it a `CanvasDrawScope` over an `ImageBitmap` and read the pixels back.
// The dust — fifteen hundred motes a galaxy — is generated once and kept in a `SkyDust` that
// outlives the paint, which is rebuilt with the scene.
class SkyPaint(
    private val scene: SkyScene,
    private val translations: Translations,
    private val measurer: TextMeasurer,
    private val mono: FontFamily,
    private val dust: SkyDust = SkyDust(),
) {

    fun DrawScope.drawSky(view: SkyView) {
        val frame = Frame(view, SkyViewport(size.width, size.height))
        drawRect(OltreColors.background)
        drawGas(frame)
        drawFarField(frame)
        val boxes = mutableListOf<Rect>()
        for (galaxy in 1..GalaxyBalance.GALAXIES) drawGalaxy(frame, galaxy, boxes)
        drawOrbitView(frame)
    }

    // ── the view, once per frame ─────────────────────────────────────────────────────────────

    private inner class Frame(val view: SkyView, val viewport: SkyViewport) {
        val ppu = view.ppu
        val stars = SkyGeometry.starsLod(ppu)
        val sector = SkyGeometry.sectorLod(ppu)
        val hood = SkyGeometry.hoodLod(ppu)
        val system = SkyGeometry.systemLod(ppu)
        val selectedSystem: SystemAddress? = view.selection.system
        val selectedWorld: GalaxyCoordinate? = view.selection.world
        val selectedRegion: Int? = view.selection.region

        fun screen(p: SkyPoint): Offset = view.screenOf(p, viewport).let { Offset(it.x, it.y) }

        fun onScreen(q: Offset, margin: Float = 0f): Boolean =
            q.x >= -margin && q.y >= -margin && q.x <= viewport.width + margin && q.y <= viewport.height + margin
    }

    // A galaxy as it is placed this frame: its centre on screen, and the magnification that keeps
    // it a legible disc at the universe depth. `f` is 1 whenever the galaxy is drawn at true size.
    private inner class Placed(val frame: Frame, val galaxy: Int) {
        val centre = scene.sky.centreOf(galaxy)
        val cq = frame.screen(centre)
        val trueRadius = scene.sky.radiusOf(galaxy) * frame.ppu
        val radius = max(trueRadius, MIN_GALAXY_PX)
        val f = radius / trueRadius
        val k = frame.ppu * f

        fun place(p: SkyPoint): Offset = Offset(cq.x + (p.x - centre.x) * k, cq.y + (p.y - centre.y) * k)
    }

    // ── the field behind everything ──────────────────────────────────────────────────────────

    private fun DrawScope.drawGas(frame: Frame) {
        val alpha = GAS_ALPHA * (1f - frame.sector * 0.6f)
        if (alpha <= 0.002f) return
        for (k in 0 until GAS_CLOUDS) {
            val p = SkyPoint(
                x = (SkyGeometry.hash(k * 3 + 11) - 0.5f) * GAS_SPREAD_X,
                y = (SkyGeometry.hash(k * 3 + 12) - 0.5f) * GAS_SPREAD_Y,
            )
            val radius = (GAS_RADIUS + SkyGeometry.hash(k * 3 + 13) * GAS_RADIUS_SPREAD) * frame.ppu
            if (radius < 4f) continue
            val q = frame.screen(p)
            val colour = GAS_COLOURS[k % GAS_COLOURS.size]
            drawCircle(
                brush = Brush.radialGradient(
                    0f to colour.copy(alpha = alpha),
                    1f to colour.copy(alpha = 0f),
                    center = q,
                    radius = radius,
                ),
                radius = radius,
                center = q,
            )
        }
    }

    private fun DrawScope.drawFarField(frame: Frame) {
        if (frame.ppu >= FAR_FIELD_UNTIL) return
        val fade = 1f - frame.sector * 0.7f
        for (i in 0 until FAR_FIELD_STARS) {
            val p = SkyPoint(
                x = (SkyGeometry.hash(i * 3 + 900) - 0.5f) * FAR_FIELD_SPREAD,
                y = (SkyGeometry.hash(i * 3 + 901) - 0.5f) * FAR_FIELD_SPREAD,
            )
            val q = frame.screen(p)
            if (!frame.onScreen(q)) continue
            drawRect(
                color = OltreColors.text,
                topLeft = q,
                size = Size(1f, 1f),
                alpha = (0.1f + SkyGeometry.hash(i * 3 + 902) * 0.3f) * fade,
            )
        }
    }

    // ── one galaxy: the glyph, the stars, the regions, the hood, the names ───────────────────

    private fun DrawScope.drawGalaxy(frame: Frame, galaxy: Int, boxes: MutableList<Rect>) {
        val placed = Placed(frame, galaxy)
        val reach = placed.radius + GALAXY_CULL_PX
        if (!frame.onScreen(placed.cq, reach)) return
        val state = scene.galaxyOf(galaxy)
        val dark = !state.charted
        val selected = frame.selectedSystem
        // once the orbit view is in the rest of the sky steps back — unless the selected star is off
        // screen, in which case the sky must stay legible enough to find the way back
        val fade = when {
            selected == null -> 1f
            frame.onScreen(frame.screen(scene.positionOf(selected)), 0f) -> 1f - frame.system
            else -> max(BACKGROUND_FLOOR, 1f - frame.system)
        }
        val glyphAlpha = (if (dark) DARK_GLYPH else 1f) * fade * (1f - 0.85f * frame.hood)
        if (glyphAlpha > 0.005f) drawGlyph(placed, glyphAlpha)

        drawStars(frame, placed, state, dark, fade)
        if (frame.stars < 1f) drawGalaxyLabel(frame, placed, state, boxes)
        if (frame.sector > 0f && frame.hood < 1f) drawRegions(frame, placed, state)
        if (frame.sector > 0f && frame.system < 1f) {
            drawHours(frame, placed, state)
            drawFlights(frame, placed, state)
        }
        if (frame.stars > 0f && frame.system < 1f && galaxy == scene.home.galaxy) {
            val q = placed.place(scene.positionOf(SystemAddress.of(scene.home)))
            drawCircle(
                color = OltreColors.text,
                radius = HOME_RING_PX,
                center = q,
                alpha = 0.55f * frame.stars * (1f - frame.system),
                style = Stroke(1.2f),
            )
        }
        val hoodBoxes = mutableListOf<Rect>()
        if (frame.hood > 0f && frame.system < 1f) drawHood(frame, placed, state, hoodBoxes)
        if (frame.sector > 0f && frame.system < 1f) drawNames(frame, placed, state, hoodBoxes)
    }

    // The galaxy as a picture: a halo, a bar where there is one, the arms as light and as dust.
    // Every colour here is astronomy and none is a status hue.
    private fun DrawScope.drawGlyph(placed: Placed, alpha: Float) {
        val shape = scene.sky.shapeOf(placed.galaxy)
        val rim = scene.sky.radiusOf(placed.galaxy)
        withTransform({
            translate(placed.cq.x, placed.cq.y)
            scale(placed.k, placed.k * shape.tilt, Offset.Zero)
            rotate(shape.rotationRadians * DEGREES, Offset.Zero)
        }) {
            val haloRadius = rim * 1.15f
            val halo = if (shape.kind == GalaxyKind.ELLIPTICAL) {
                Brush.radialGradient(
                    0f to Color(255, 230, 195).copy(alpha = 0.85f * alpha),
                    0.35f to Color(245, 215, 180).copy(alpha = 0.22f * alpha),
                    1f to Color(245, 215, 180).copy(alpha = 0f),
                    center = Offset.Zero,
                    radius = haloRadius,
                )
            } else {
                Brush.radialGradient(
                    0f to Color(255, 238, 210).copy(alpha = 0.7f * alpha),
                    0.16f to Color(255, 225, 185).copy(alpha = 0.22f * alpha),
                    0.6f to Color(175, 190, 240).copy(alpha = 0.07f * alpha),
                    1f to Color(175, 190, 240).copy(alpha = 0f),
                    center = Offset.Zero,
                    radius = haloRadius,
                )
            }
            drawCircle(brush = halo, radius = haloRadius, center = Offset.Zero)
            if (shape.kind == GalaxyKind.RING) {
                drawCircle(
                    color = Color(160, 190, 250),
                    radius = rim,
                    center = Offset.Zero,
                    alpha = 0.16f * alpha,
                    style = Stroke(RING_BAND_UNITS),
                )
            }
            if (shape.kind == GalaxyKind.BARRED) {
                withTransform({ scale(1f, BAR_SQUASH, Offset.Zero) }) {
                    val barRadius = shape.scale * 1.45f
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to Color(255, 232, 196).copy(alpha = 0.55f * alpha),
                            0.5f to Color(255, 224, 180).copy(alpha = 0.22f * alpha),
                            1f to Color(255, 224, 180).copy(alpha = 0f),
                            center = Offset.Zero,
                            radius = barRadius,
                        ),
                        radius = barRadius,
                        center = Offset.Zero,
                    )
                }
            }
        }
        if (shape.kind == GalaxyKind.SPIRAL || shape.kind == GalaxyKind.BARRED) drawArms(placed, alpha)
        drawDust(placed, alpha)
    }

    // The arms in screen space, as the path the systems ride: a wide soft band, a thin bright line
    // and a dust lane set just inside the curve.
    private fun DrawScope.drawArms(placed: Placed, alpha: Float) {
        val band = Path()
        val lane = Path()
        var first = true
        var system = 1
        while (system <= GalaxyBalance.SYSTEMS_PER_GALAXY) {
            val point = scene.sky.pathOf(placed.galaxy, system)
            val q = placed.place(SkyPoint(point.x, point.y))
            val l = placed.place(SkyPoint(point.x + point.normalX * LANE_OFFSET_UNITS, point.y + point.normalY * LANE_OFFSET_UNITS))
            if (first) {
                band.moveTo(q.x, q.y)
                lane.moveTo(l.x, l.y)
                first = false
            } else {
                band.lineTo(q.x, q.y)
                lane.lineTo(l.x, l.y)
            }
            system += ARM_STEP
        }
        drawPath(band, Color(150, 185, 245), alpha = 0.12f * alpha, style = Stroke(7.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(band, Color(205, 222, 255), alpha = 0.11f * alpha, style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(lane, Color(105, 58, 52), alpha = 0.55f * alpha, style = Stroke(1.3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    private fun DrawScope.drawDust(placed: Placed, alpha: Float) {
        val motes = dust.of(placed.galaxy) { motesOf(placed.galaxy) }
        val step = max(1, (0.9f / min(0.9f, placed.k)).roundToInt())
        var i = 0
        while (i < motes.size) {
            val mote = motes[i]
            val q = placed.place(mote.at)
            drawCircle(color = mote.colour, radius = mote.size / 2f, center = q, alpha = mote.alpha * alpha)
            i += step
        }
    }

    // Fifteen hundred motes, from the picture's own noise: on the arms where the arms are, in the
    // bulge, on the ring, or spread through an elliptical. Generated once per galaxy, in sky units,
    // so drawing one is a lookup and a circle.
    private fun motesOf(galaxy: Int): List<SkyMote> {
        val shape = scene.sky.shapeOf(galaxy)
        val sky = scene.sky
        val motes = ArrayList<SkyMote>(DUST_MOTES)
        val half = GalaxyBalance.SYSTEMS_PER_GALAXY / 2
        for (i in 0 until DUST_MOTES) {
            val seed = galaxy * DUST_STRIDE + i * 4
            val u = SkyGeometry.hash(seed)
            val v = SkyGeometry.hash(seed + 1) + SkyGeometry.hash(seed + 2) - 1f
            val w = SkyGeometry.hash(seed + 3)
            val h = SkyGeometry.hash(seed + 5)
            val onArm = (shape.kind == GalaxyKind.SPIRAL || shape.kind == GalaxyKind.BARRED) && w > 0.3f
            when {
                onArm -> {
                    // dense towards the core, either arm, spread across it
                    val along = (half * u.pow(1.3f)).toInt()
                    val system = (if (h < 0.5f) half - along else half + 1 + along).coerceIn(1, GalaxyBalance.SYSTEMS_PER_GALAXY)
                    val point = sky.pathOf(galaxy, system)
                    val across = v * DUST_ACROSS
                    val jitter = (SkyGeometry.hash(seed + 7) - 0.5f) * SkyGeometry.PITCH
                    val at = SkyPoint(
                        x = point.x + point.normalX * across + point.tangentX * jitter,
                        y = point.y + point.normalY * across + point.tangentY * jitter,
                    )
                    val colour = when {
                        h < 0.1f -> Color(255, 140, 185)
                        h < 0.55f -> Color(195, 215, 255)
                        else -> Color(232, 238, 255)
                    }
                    val size = when {
                        h < 0.1f -> 2.2f
                        h < 0.22f -> 1.6f
                        else -> 1.25f
                    }
                    motes += SkyMote(at, colour, size, if (h < 0.1f) 0.22f else 0.16f)
                }

                shape.kind == GalaxyKind.RING -> {
                    val angle = u * 2f * PI.toFloat()
                    val r = if (w > 0.88f) abs(v) * 12f else sky.radiusOf(galaxy) + v * 4f
                    val at = sky.skyOf(galaxy, r * cos(angle), r * sin(angle))
                    val colour = when {
                        w > 0.88f -> Color(255, 228, 190)
                        h < 0.12f -> Color(255, 140, 185)
                        else -> Color(195, 215, 255)
                    }
                    motes += SkyMote(at, colour, 1.25f, 0.18f)
                }

                shape.kind == GalaxyKind.ELLIPTICAL -> {
                    val angle = u * 2f * PI.toFloat()
                    val r = abs(v) * 42f
                    val at = sky.skyOf(galaxy, r * cos(angle), r * sin(angle) * 0.85f)
                    motes += SkyMote(at, Color(255, 226, 190), 1.25f, 0.12f + SkyGeometry.hash(seed + 9) * 0.28f)
                }

                else -> {
                    // the bulge: old gold stars, stretched along a bar where there is one
                    val angle = u * 2f * PI.toFloat()
                    val r = abs(v) * shape.scale * 1.3f + SkyGeometry.hash(seed + 9) * 2f
                    val stretch = if (shape.kind == GalaxyKind.BARRED) 1.5f else 1f
                    val at = sky.skyOf(galaxy, r * cos(angle) * stretch, r * sin(angle) * 0.7f)
                    motes += SkyMote(at, Color(255, 228, 190), 1.25f, 0.12f + SkyGeometry.hash(seed + 9) * 0.28f)
                }
            }
        }
        return motes
    }

    private fun DrawScope.drawStars(frame: Frame, placed: Placed, state: SkyGalaxyUiState, dark: Boolean, fade: Float) {
        val step = if (frame.stars >= 1f) 1 else max(1, (4f - frame.stars * 3f).roundToInt())
        val selected = frame.selectedSystem
        var index = 0
        while (index < state.stars.size) {
            val star = state.stars[index]
            index += step
            val at = SystemAddress(placed.galaxy, star.system)
            if (frame.system > 0f && at == selected) continue
            val q = placed.place(scene.positionOf(at))
            if (!frame.onScreen(q, STAR_MARGIN_PX)) continue
            when (val ink = star.ink) {
                SkyStarInk.Grain -> drawCircle(
                    color = OltreColors.text,
                    radius = if (frame.stars < 1f) 0.7f else 1.05f,
                    center = q,
                    alpha = (if (dark) 0.18f else 0.24f) * fade,
                )

                is SkyStarInk.Charted -> {
                    val base = when (ink.starClass) {
                        StarClass.DIM -> 1.3f
                        StarClass.STANDARD -> 1.9f
                        StarClass.BRIGHT -> 2.6f
                    }
                    val r = min(
                        base * (0.55f + 0.45f * frame.sector + 0.9f * frame.hood) * (ink.sizePermille / 1_000f),
                        placed.k * 0.36f + 0.35f,
                    )
                    val bright = ink.starClass == StarClass.BRIGHT
                    if (frame.sector > 0f && ink.starClass != StarClass.DIM && (frame.ppu >= GLOW_FROM || bright)) {
                        val glowRadius = r * 4.2f
                        val glow = OltreColors.text.copy(alpha = (if (bright) 0.22f else 0.12f) * frame.sector * fade)
                        drawCircle(
                            brush = Brush.radialGradient(0f to glow, 1f to glow.copy(alpha = 0f), center = q, radius = glowRadius),
                            radius = glowRadius,
                            center = q,
                        )
                    }
                    val (fill, fillAlpha) = when (ink.starClass) {
                        StarClass.DIM -> DIM_STAR to 0.6f
                        StarClass.STANDARD -> OltreColors.text to 0.75f
                        StarClass.BRIGHT -> BRIGHT_STAR to 1f
                    }
                    drawCircle(color = fill, radius = r, center = q, alpha = fillAlpha * fade)
                    if (star.surveyed || star.inFlight) {
                        drawCircle(
                            color = if (star.inFlight) OltreColors.warn else OltreColors.text,
                            radius = r + 2.6f,
                            center = q,
                            alpha = (if (star.inFlight) 0.55f else 0.30f) * frame.sector * fade,
                            style = Stroke(1f),
                        )
                    }
                }
            }
        }
        if (selected != null && selected.galaxy == placed.galaxy && frame.system < 1f && frame.stars > 0f) {
            drawCircle(
                color = OltreColors.accent,
                radius = SELECTED_RING_PX,
                center = placed.place(scene.positionOf(selected)),
                alpha = (1f - frame.system) * frame.stars,
                style = Stroke(1.4f),
            )
        }
    }

    // At the universe depth a galaxy is a name and a word under it, set clear of the disc and of
    // every label already placed; yours and the selected one are ringed.
    private fun DrawScope.drawGalaxyLabel(frame: Frame, placed: Placed, state: SkyGalaxyUiState, boxes: MutableList<Rect>) {
        val a = 1f - frame.stars
        val mine = placed.galaxy == scene.home.galaxy
        val name = label(translations.resolve(state.label), 9.5.sp, FontWeight.SemiBold)
        val known = label(translations.resolve(state.known), 8.5.sp)
        val width = max(name.size.width, known.size.width).toFloat()
        val x = placed.cq.x.coerceIn(LABEL_EDGE_PX, frame.viewport.width - LABEL_EDGE_PX)
        val y = if (placed.galaxy % 2 == 1) placed.cq.y + placed.radius + 14f else placed.cq.y - placed.radius - 22f
        val box = Rect(x - width / 2f - LABEL_PAD_PX, y - LABEL_PAD_PX, x + width / 2f + LABEL_PAD_PX, y + LABEL_HEIGHT_PX + LABEL_PAD_PX)
        if (boxes.none { it.overlaps(box) }) {
            boxes += box
            drawText(name, color = OltreColors.text, topLeft = Offset(x - name.size.width / 2f, y), alpha = (if (mine) 1f else 0.55f) * a)
            drawText(known, color = OltreColors.text, topLeft = Offset(x - known.size.width / 2f, y + 12f), alpha = 0.36f * a)
        }
        if (mine) {
            drawCircle(OltreColors.accent, placed.radius + 5f, placed.cq, alpha = 0.55f * a, style = Stroke(1.2f))
        }
        if (frame.view.selection is SkySelection.Galaxy && frame.view.selection.galaxy == placed.galaxy) {
            drawCircle(OltreColors.accent, placed.radius + 9f, placed.cq, alpha = a, style = Stroke(1.4f))
        }
    }

    // The selected region as a lit stretch of the arm, and every region's name set along it.
    private fun DrawScope.drawRegions(frame: Frame, placed: Placed, state: SkyGalaxyUiState) {
        val a = frame.sector * (1f - frame.hood)
        val selectedRegion = if (frame.view.selection.galaxy == placed.galaxy) frame.selectedRegion else null
        val highlight = selectedRegion != null && (frame.view.selection is SkySelection.Region || frame.ppu < ORBIT_FROM)
        for (region in state.regions) {
            val systems = SkyGeometry.systemsOfRegion(region.region)
            val selected = region.region == selectedRegion
            if (selected && highlight) {
                val path = Path()
                for ((i, system) in systems.withIndex()) {
                    val point = scene.sky.pathOf(placed.galaxy, system)
                    val q = placed.place(SkyPoint(point.x, point.y))
                    if (i == 0) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y)
                }
                drawPath(path, OltreColors.accent, alpha = 0.22f * a, style = Stroke(max(16f, frame.ppu * 8f), cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(path, OltreColors.accent, alpha = 0.9f * a, style = Stroke(1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            val text = label(translations.resolve(region.name), 8.5.sp, FontWeight.SemiBold, spacing = 0.14.em)
            if (systems.count() * frame.ppu * SkyGeometry.PITCH < text.size.width + 8f) continue
            val point = scene.sky.pathOf(placed.galaxy, systems.first + REGION_LABEL_AT)
            val offset = 10f + frame.ppu * 3.2f
            val q = placed.place(SkyPoint(point.x + point.normalX * offset / placed.k, point.y + point.normalY * offset / placed.k))
            if (!frame.onScreen(q, text.size.width.toFloat())) continue
            var angle = point.tangentRadians
            if (cos(angle) < 0f) angle += PI.toFloat()
            val colour = if (selected) OltreColors.accent else OltreColors.text
            val alpha = when {
                selected -> a
                region.lit -> 0.42f * a
                else -> 0.24f * a
            }
            rotate(angle * DEGREES, pivot = q) {
                drawText(text, color = colour, topLeft = Offset(q.x - text.size.width / 2f, q.y - text.size.height / 2f), alpha = alpha)
            }
        }
    }

    // The hour rings: a tick across the arm at each system a probe reaches in a whole hour, and
    // the hour beside it.
    private fun DrawScope.drawHours(frame: Frame, placed: Placed, state: SkyGalaxyUiState) {
        val a = frame.sector * (1f - frame.system)
        for (hour in state.hours) {
            val point = scene.sky.pathOf(placed.galaxy, hour.system)
            val q = placed.place(SkyPoint(point.x, point.y))
            if (!frame.onScreen(q, HOUR_MARGIN_PX)) continue
            val n = Offset(point.normalX, point.normalY)
            drawLine(OltreColors.text, q - n * 6f, q + n * 6f, strokeWidth = 1f, alpha = 0.18f * a)
            val text = label(translations.resolve(hour.label), 8.5.sp)
            val at = q + n * 9f
            drawText(text, color = OltreColors.text, topLeft = Offset(at.x - text.size.width / 2f, at.y - text.size.height / 2f), alpha = 0.4f * a)
        }
    }

    // A fleet out is the stretch of arm it travels, in amber, because amber is your fleet.
    private fun DrawScope.drawFlights(frame: Frame, placed: Placed, state: SkyGalaxyUiState) {
        val a = 0.85f * (1f - frame.system)
        for (flight in state.flights) {
            val path = Path()
            val from = min(flight.from, flight.to)
            val to = max(flight.from, flight.to)
            for (system in from..to) {
                val point = scene.sky.pathOf(placed.galaxy, system)
                val q = placed.place(SkyPoint(point.x, point.y))
                if (system == from) path.moveTo(q.x, q.y) else path.lineTo(q.x, q.y)
            }
            drawPath(path, OltreColors.warn, alpha = a, style = Stroke(1.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }

    // The neighbourhood: the worlds of every surveyed star strung across its arm, as portraits
    // the size of a grain, so a region reads as where the probes have been.
    private fun DrawScope.drawHood(frame: Frame, placed: Placed, state: SkyGalaxyUiState, boxes: MutableList<Rect>) {
        val a = frame.hood * (1f - frame.system)
        val gap = max(13f, frame.ppu * 0.9f)
        val radius = min(4.5f, 2.2f + frame.ppu * 0.06f)
        for (star in state.stars) {
            if (star.worlds.isEmpty()) continue
            val at = SystemAddress(placed.galaxy, star.system)
            if (frame.system > 0f && at == frame.selectedSystem) continue
            val point = scene.sky.pathOf(placed.galaxy, star.system)
            val q = placed.place(scene.positionOf(at))
            if (!frame.onScreen(q, gap * star.worlds.size)) continue
            val n = Offset(point.normalX, point.normalY)
            val start = -(star.worlds.size - 1) / 2f
            for ((i, body) in star.worlds.withIndex()) {
                val c = q + n * ((start + i) * gap)
                withAlpha(a, Rect(c.x - radius * 2f, c.y - radius * 2f, c.x + radius * 2f, c.y + radius * 2f)) {
                    drawBody(body.portrait, c, radius)
                }
                if (body.home) drawCircle(OltreColors.accent, radius + 2f, c, alpha = 0.75f * a, style = Stroke(1f))
                boxes += Rect(c.x - radius - 2f, c.y - radius - 2f, c.x + radius + 2f, c.y + radius + 2f)
            }
        }
    }

    // A name beside every star that has one, placed in the first of four spots that is clear of
    // the light and of everything already written. Home first, because home is the name you look
    // for; the selection next.
    private fun DrawScope.drawNames(frame: Frame, placed: Placed, state: SkyGalaxyUiState, boxes: MutableList<Rect>) {
        val a = frame.sector * (1f - frame.system)
        val home = SystemAddress.of(scene.home)
        val selected = frame.selectedSystem
        val bulge = scene.sky.shapeOf(placed.galaxy).scale * 1.5f * placed.k
        boxes += Rect(placed.cq.x - bulge, placed.cq.y - bulge * scene.sky.shapeOf(placed.galaxy).tilt, placed.cq.x + bulge, placed.cq.y + bulge * scene.sky.shapeOf(placed.galaxy).tilt)
        val named = state.stars.filter { it.name != null }.sortedBy { star ->
            when (SystemAddress(placed.galaxy, star.system)) {
                home -> 0
                selected -> 1
                else -> 2
            }
        }
        for (star in named) {
            val at = SystemAddress(placed.galaxy, star.system)
            val isHome = at == home
            val isSelected = at == selected
            if (star.ink is SkyStarInk.Grain && !isHome) continue
            if (!isHome && !isSelected && frame.hood <= 0f) continue
            val q = placed.place(scene.positionOf(at))
            if (!frame.onScreen(q, NAME_MARGIN_PX)) continue
            val text = label(translations.resolve(star.name ?: continue), 9.5.sp, if (isHome) FontWeight.SemiBold else FontWeight.Normal)
            val w = text.size.width.toFloat()
            val h = text.size.height.toFloat()
            val spots = listOf(
                Offset(q.x + NAME_GAP_PX, q.y - 5f),
                Offset(q.x - NAME_GAP_PX - w, q.y - 5f),
                Offset(q.x + NAME_GAP_PX, q.y - 19f),
                Offset(q.x - NAME_GAP_PX - w, q.y - 19f),
                Offset(q.x - w / 2f, q.y - NAME_ABOVE_PX - h),
            )
            val spot = spots.firstOrNull { s ->
                val box = Rect(s.x - 3f, s.y - 2f, s.x + w + 3f, s.y + h + 2f)
                box.left >= 0f && box.right <= frame.viewport.width && boxes.none { it.overlaps(box) }
            } ?: continue
            boxes += Rect(spot.x - 3f, spot.y - 2f, spot.x + w + 3f, spot.y + h + 2f)
            drawText(text, color = if (isSelected) OltreColors.accent else OltreColors.text, topLeft = spot, alpha = (if (isHome || isSelected) 1f else 0.6f) * a)
        }
    }

    // ── the orbit view: the selected star and what sits about it ─────────────────────────────

    private fun DrawScope.drawOrbitView(frame: Frame) {
        val system = frame.selectedSystem ?: return
        if (frame.system <= 0f) return
        val a = frame.system
        val star = scene.starOf(system)
        val ink = star.ink
        val q = frame.screen(scene.positionOf(system))
        val starClass = (ink as? SkyStarInk.Charted)?.starClass ?: StarClass.STANDARD
        val (floorPx, scaleOf) = when (starClass) {
            StarClass.DIM -> 10f to 0.75f
            StarClass.STANDARD -> 13f to 1f
            StarClass.BRIGHT -> 16f to 1.25f
        }
        val shortSide = min(frame.viewport.width, frame.viewport.height)
        val starRadius = min(
            0.12f * shortSide,
            max(floorPx, SkyGeometry.STAR_RADIUS * scaleOf * min(frame.ppu, ORBIT_PPU * sqrt(max(1f, frame.ppu / ORBIT_PPU)))),
        )
        val glowRadius = starRadius * (if (frame.ppu > WORLD_FROM) 1.6f else 4f)
        val dim = starClass == StarClass.DIM
        val glow = (if (dim) DIM_STAR else Color(255, 220, 160)).copy(alpha = 0.35f * a)
        drawCircle(
            brush = Brush.radialGradient(0f to glow, 1f to glow.copy(alpha = 0f), center = q, radius = glowRadius),
            radius = glowRadius,
            center = q,
        )
        val (core, edge) = if (dim) Color(220, 205, 255) to Color(150, 120, 220) else Color(255, 246, 220) to Color(255, 170, 70)
        drawCircle(
            brush = Brush.radialGradient(
                0f to core.copy(alpha = a),
                1f to edge.copy(alpha = a),
                center = Offset(q.x - starRadius * 0.3f, q.y - starRadius * 0.3f),
                radius = starRadius * 1.3f,
            ),
            radius = starRadius,
            center = q,
        )

        if (star.worlds.isEmpty()) {
            val note = label(translations.resolve(scene.words.alone), 9.sp, spacing = 0.12.em)
            drawText(note, color = OltreColors.text, topLeft = Offset(q.x - note.size.width / 2f, q.y + starRadius + 22f), alpha = 0.45f * a)
            return
        }

        for ((rank, body) in star.worlds.withIndex()) {
            val at = GalaxyCoordinate(system.galaxy, system.system, body.slot)
            val rx = SkyGeometry.orbitRadiusOf(rank) * frame.ppu
            val ry = rx * SkyScene.ORBIT_TILT
            drawOval(
                color = OltreColors.text,
                topLeft = Offset(q.x - rx, q.y - ry),
                size = Size(rx * 2f, ry * 2f),
                alpha = 0.09f * a,
                style = Stroke(1f),
            )
            val c = frame.screen(scene.worldPositionOf(at))
            val rr = max(MIN_BODY_PX, scene.worldRadiusOf(at) * frame.ppu)
            if (!frame.onScreen(c, rr + 60f)) continue
            val selected = at == frame.selectedWorld
            withAlpha(a, Rect(c.x - rr - 40f, c.y - rr - 40f, c.x + rr + 40f, c.y + rr + 40f)) {
                drawBody(body.portrait, c, rr)
                val gap = max(2.4f, rr * 0.16f)
                val width = max(1.4f, rr * 0.05f)
                if (body.portrait is WorldPortraitUiState.Surveyed && !body.home) {
                    body.metal?.let { drawDeposit(c, rr + gap, it, OltreColors.metal, width, from = 90f) }
                    body.crystal?.let { drawDeposit(c, rr + gap, it, OltreColors.crystal, width, from = -90f) }
                }
                if (body.home) drawCircle(OltreColors.accent, rr + gap + 0.6f, c, alpha = 0.75f, style = Stroke(width))
                if (body.yours) drawCircle(OltreColors.warn, rr + gap + 2.1f, c, alpha = 0.95f, style = Stroke(width))
                if (selected && rr < BIG_BODY_PX) drawCircle(OltreColors.accent, rr + 7f, c, style = Stroke(1.4f))
            }
            if (rr < DEPOSIT_WORDS_FROM) {
                val numeral = label(body.slot.toString(), 9.sp)
                drawText(numeral, color = OltreColors.text, topLeft = Offset(c.x - numeral.size.width / 2f, c.y + rr + 12f - numeral.size.height / 2f), alpha = 0.45f * a)
            } else if (body.portrait is WorldPortraitUiState.Surveyed) {
                body.metal?.let {
                    val word = label(translations.resolve(scene.words.metal), 8.5.sp, spacing = 0.12.em)
                    drawText(word, color = OltreColors.metal, topLeft = Offset(c.x - rr - 22f - word.size.width, c.y - rr - 12f), alpha = 0.85f * a)
                }
                body.crystal?.let {
                    val word = label(translations.resolve(scene.words.crystal), 8.5.sp, spacing = 0.12.em)
                    drawText(word, color = OltreColors.crystal, topLeft = Offset(c.x + rr + 22f, c.y - rr - 12f), alpha = 0.85f * a)
                }
            }
        }
    }

    // What is left of a deposit as an arc about the world: metal from the bottom, crystal from the
    // top, each sweeping half a turn when full.
    private fun DrawScope.drawDeposit(centre: Offset, radius: Float, fraction: Float, colour: Color, width: Float, from: Float) {
        drawArc(
            color = colour,
            startAngle = from,
            sweepAngle = 180f * fraction.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(centre.x - radius, centre.y - radius),
            size = Size(radius * 2f, radius * 2f),
            alpha = 0.85f,
            style = Stroke(width, cap = StrokeCap.Round),
        )
    }

    // **The portrait, at any size.** `drawWorldPortrait` draws into its own box — gravity is the
    // fraction of it the disc fills, and a socket is 0.62 of it — so the box is sized back from the
    // radius wanted here and the portrait is drawn into an inset of that size about the centre. One
    // drawing for a grain in the hood and for a world filling the frame, which is the point: what
    // you learned to read on the ledger is what you see beside the star.
    private fun DrawScope.drawBody(portrait: WorldPortraitUiState, centre: Offset, radius: Float) {
        val boxPx = when (portrait) {
            WorldPortraitUiState.Unsurveyed -> radius * 2f
            is WorldPortraitUiState.Surveyed -> {
                val gravity = (portrait.gravity.milliG / 3_000f).coerceIn(0.06f, 1f)
                radius * 2f / (0.50f + 0.32f * gravity)
            }
        }
        val half = boxPx / 2f
        inset(
            left = centre.x - half,
            top = centre.y - half,
            right = size.width - centre.x - half,
            bottom = size.height - centre.y - half,
        ) {
            drawWorldPortrait(portrait, boxPx.toDp())
        }
    }

    // A faded drawing is a layer: the portrait has no alpha of its own, and it must not, because a
    // fade is the canvas's business and never a world's.
    private inline fun DrawScope.withAlpha(alpha: Float, bounds: Rect, block: DrawScope.() -> Unit) {
        if (alpha >= 0.995f) {
            block()
            return
        }
        val canvas = drawContext.canvas
        canvas.saveLayer(bounds, Paint().apply { this.alpha = alpha })
        block()
        canvas.restore()
    }

    // ── words ────────────────────────────────────────────────────────────────────────────────

    private fun label(text: String, size: TextUnit, weight: FontWeight = FontWeight.Normal, spacing: TextUnit = TextUnit.Unspecified): TextLayoutResult =
        measurer.measure(
            text = AnnotatedString(text),
            style = TextStyle(fontFamily = mono, fontSize = size, fontWeight = weight, letterSpacing = spacing),
            softWrap = false,
        )

    private companion object {
        const val DEGREES: Float = (180.0 / PI).toFloat()

        // A galaxy is never drawn smaller than this at the universe depth, whatever the zoom.
        const val MIN_GALAXY_PX: Float = 22f
        const val GALAXY_CULL_PX: Float = 60f
        const val DARK_GLYPH: Float = 0.4f
        const val BACKGROUND_FLOOR: Float = 0.3f

        const val GAS_CLOUDS: Int = 6
        const val GAS_ALPHA: Float = 0.07f
        const val GAS_SPREAD_X: Float = 4_200f
        const val GAS_SPREAD_Y: Float = 4_600f
        const val GAS_RADIUS: Float = 700f
        const val GAS_RADIUS_SPREAD: Float = 900f
        val GAS_COLOURS: List<Color> = listOf(OltreColors.deuterium, OltreColors.crystal, OltreColors.metal)

        const val FAR_FIELD_STARS: Int = 700
        const val FAR_FIELD_SPREAD: Float = 7_000f
        const val FAR_FIELD_UNTIL: Float = 4f

        const val RING_BAND_UNITS: Float = 9f
        const val BAR_SQUASH: Float = 0.36f
        const val ARM_STEP: Int = 2
        const val LANE_OFFSET_UNITS: Float = 1.9f

        const val DUST_MOTES: Int = 1_500
        const val DUST_STRIDE: Int = 100_003
        const val DUST_ACROSS: Float = 3.4f

        const val STAR_MARGIN_PX: Float = 6f
        const val GLOW_FROM: Float = 5f
        val DIM_STAR: Color = Color(186, 164, 240)
        val BRIGHT_STAR: Color = Color(0xFFF5FAFF)
        const val HOME_RING_PX: Float = 6.4f
        const val SELECTED_RING_PX: Float = 8.2f

        const val LABEL_EDGE_PX: Float = 84f
        const val LABEL_PAD_PX: Float = 6f
        const val LABEL_HEIGHT_PX: Float = 24f

        const val REGION_LABEL_AT: Int = 12
        const val ORBIT_FROM: Float = 34f
        const val HOUR_MARGIN_PX: Float = 30f
        const val NAME_MARGIN_PX: Float = 120f
        const val NAME_GAP_PX: Float = 9f
        const val NAME_ABOVE_PX: Float = 8f

        const val ORBIT_PPU: Float = 70f
        const val WORLD_FROM: Float = 260f
        const val MIN_BODY_PX: Float = 6.5f
        const val BIG_BODY_PX: Float = 28f
        const val DEPOSIT_WORDS_FROM: Float = 40f
    }
}

// A notch of the wheel is a step of zoom about the pointer, small enough to be walked.
private const val WHEEL_STEP: Float = 0.0015f

// Every word the canvas sets, at every depth, fits in here; past it the measurer measures again.
private const val LABEL_CACHE: Int = 256

// A galaxy's dust, kept between frames: one mote is a place in the sky, a colour, a size and how
// faint it is.
class SkyMote(val at: SkyPoint, val colour: Color, val size: Float, val alpha: Float)

class SkyDust {
    private val motes = HashMap<Int, List<SkyMote>>()

    fun of(galaxy: Int, generate: () -> List<SkyMote>): List<SkyMote> = motes.getOrPut(galaxy, generate)
}
