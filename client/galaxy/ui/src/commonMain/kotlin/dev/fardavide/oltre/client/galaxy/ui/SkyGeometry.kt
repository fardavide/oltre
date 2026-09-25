package dev.fardavide.oltre.client.galaxy.ui

import dev.fardavide.oltre.core.GalaxyBalance
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

// **The sky, as arithmetic** — one coordinate space from a field of galaxies down to the face of one
// world, and the whole of what `UniverseCanvas` paints. Separated from the drawing for the reason
// `MapGeometry` was: the property the design rests on is a claim about numbers, and a recorded frame
// of 2,250 dots cannot tell a correct one from a transposed one. `SkyGeometryTest` walks it.
//
// **What replaced the fold kept the fold's rule.** A galaxy is no longer ten ruled bands; it is two
// spiral arms through a bar, read against NGC 1300. But the 250 systems still ride *one* path — arm
// A from the rim in to one end of the bar, the bar through the core, arm B from the other end out to
// the rim — so the index is still arc length, a round trip is still a distance along the line, and
// `SurveyBalance` still prices what the eye sees. **Looks near is near**, at every depth.
//
// Everything here is in *sky units*, a bare `Float`, and never in `Dp` or pixels. A unit becomes
// pixels exactly once, at the canvas's `ppu` — pixels per unit — which runs from 0.05 to 4,000: five
// orders of magnitude, one space, no screens in between. The same reason `MapGeometry` is unitless
// applies twice over here, because the same number places a star at the universe depth and a
// world's night side at the world depth.
//
// **What is here and what is not.** Where a system *is* along its arm is generated in `core` — the
// drift is `layoutAt`'s, capped at half a pitch, and it is the same number that made a star wander
// on the fold. Where it sits *across* the arm is not: it carries no ordering, no distance and no
// game meaning, exactly as the dust and the tilts do not, so it is drawing noise and it lives here.
// The split is the point — a number that could reorder two systems comes from the seed the game
// charges against, and a number that cannot is the picture's own business.
class SkyGeometry(private val homeGalaxy: Int) {

    // The four kinds and their parameters, from the seed. Yours is barred, because it is the one
    // you will look at every day and a bar is the most legible thing a galaxy can be; the rest are
    // drawn from the seed and each is inclined by its own tilt, so no two read the same.
    fun shapeOf(galaxy: Int): GalaxyShape {
        val kind = if (galaxy == homeGalaxy) GalaxyKind.BARRED else KINDS[(hash(galaxy * 131) * KINDS.size).toInt() % KINDS.size]
        val rotation = if (galaxy == homeGalaxy) HOME_ROTATION else hash(galaxy * 137) * PI.toFloat()
        val spin = hash(galaxy * 139)
        val tilt = when {
            galaxy == homeGalaxy -> HOME_TILT
            kind == GalaxyKind.ELLIPTICAL -> 0.70f + spin * 0.30f
            kind == GalaxyKind.RING -> 0.80f + spin * 0.20f
            else -> 0.55f + spin * 0.45f
        }
        val scale = if (kind == GalaxyKind.BARRED) BARRED_SCALE else SPIRAL_SCALE
        val winding = if (kind == GalaxyKind.BARRED) BARRED_WINDING else SPIRAL_WINDING
        val scatter = when (kind) {
            GalaxyKind.ELLIPTICAL -> 7.0f
            GalaxyKind.RING -> 3.0f
            else -> 2.4f
        }
        return GalaxyShape(
            kind = kind,
            rotationRadians = rotation,
            tilt = tilt,
            scatter = scatter,
            scale = scale,
            winding = winding,
            // **Derived for a plain spiral and the design's own 14 for a barred one**, and the
            // difference is the honesty rule rather than taste. The core crossing spans `2 * scale`
            // units however many systems are laid along it, so two systems across a 10-unit crossing
            // is a five-unit stride in a map whose pitch is 2.6 — a visible tear at the one place
            // every round trip passes through. Four closes it. A bar of 14 across 28 units strides
            // 2.15, which is already inside the pitch, so it is left where the sheet put it.
            barSystems = if (kind == GalaxyKind.BARRED) BARRED_BAR_SYSTEMS else spiralBarSystems(scale),
        )
    }

    // Where a galaxy sits in the universe: one serpentine in index order, three across. Cohorts open
    // at the path's end, so the next galaxy is always the nearest one and where a commander sits on
    // the serpentine says which week they arrived.
    fun centreOf(galaxy: Int): SkyPoint {
        val row = (galaxy - 1) / GALAXIES_ACROSS
        val column = if (row % 2 == 0) (galaxy - 1) % GALAXIES_ACROSS else GALAXIES_ACROSS - 1 - (galaxy - 1) % GALAXIES_ACROSS
        return SkyPoint(
            x = (column - 1) * GALAXY_SPACING_X + (hash(galaxy * 977) - 0.5f) * GALAXY_JITTER_X,
            y = (row - 1) * GALAXY_SPACING_Y + (hash(galaxy * 991) - 0.5f) * GALAXY_JITTER_Y,
        )
    }

    // The rim, in units: how big the galaxy is drawn and how far off screen it can be before the
    // canvas stops asking about it.
    fun radiusOf(galaxy: Int): Float {
        val shape = shapeOf(galaxy)
        return when (shape.kind) {
            GalaxyKind.RING -> RING_RADIUS
            GalaxyKind.ELLIPTICAL -> ELLIPTICAL_RADIUS
            else -> armAt(shape, armSystems(shape).toFloat(), OUTWARD).let { hypot(it.x, it.y) }
        }
    }

    // **The path itself**, with the two directions a drawing needs at every point on it: along the
    // arm, which is where a region's name is set and where a drift travels, and across it, which is
    // where a system's worlds are strung and where a label is pushed clear of the light.
    fun pathOf(galaxy: Int, system: Int): SkyPathPoint {
        val shape = shapeOf(galaxy)
        val local = localOf(shape, system)
        val centre = centreOf(galaxy)
        val point = incline(shape, local.x, local.y)
        val tangent = unit(incline(shape, local.tangentX, local.tangentY))
        val normal = unit(incline(shape, -local.tangentY, local.tangentX))
        return SkyPathPoint(
            x = centre.x + point.x,
            y = centre.y + point.y,
            tangentX = tangent.x,
            tangentY = tangent.y,
            normalX = normal.x,
            normalY = normal.y,
            tangentRadians = atan2(tangent.y, tangent.x),
        )
    }

    // Where the star is actually drawn: on the path, moved along it by the drift `core` generated
    // and across it by the arm's own width. **An arm is a band of stars and not a wire**, which is
    // the one thing the fold could not say.
    fun positionOf(galaxy: Int, system: Int, driftPermille: Int): SkyPoint {
        val shape = shapeOf(galaxy)
        val local = localOf(shape, system)
        val centre = centreOf(galaxy)
        val across = gauss(galaxy * SCATTER_STRIDE + system * SCATTER_STEP) * shape.scatter
        val along = driftOf(driftPermille)
        val placed = incline(
            shape,
            local.x - local.tangentY * across + local.tangentX * along,
            local.y + local.tangentX * across + local.tangentY * along,
        )
        return SkyPoint(x = centre.x + placed.x, y = centre.y + placed.y)
    }

    // The furthest a star can be from its own path point, which is what a culling pass and a label's
    // collision box both have to allow for.
    fun scatterLimitOf(galaxy: Int): Float = GAUSS_LIMIT * shapeOf(galaxy).scatter + PITCH / 2f

    // A region's place on the arm: the mean of its twenty-five path points. **The tap target the
    // galaxy depth has**, because at 2.1 pixels a unit a star is two pixels across and a thumb is
    // forty-four — so a tap picks the twenty-five, and the second tap picks one of them.
    fun regionCentreOf(galaxy: Int, region: Int): SkyPoint {
        var x = 0f
        var y = 0f
        val systems = systemsOfRegion(region)
        for (system in systems) {
            val path = pathOf(galaxy, system)
            x += path.x
            y += path.y
        }
        val count = GalaxyBalance.SYSTEMS_PER_REGION
        return SkyPoint(x = x / count, y = y / count)
    }

    // ── the galaxy's own plane, before it is inclined to the view ────────────────────────────────

    private fun localOf(shape: GalaxyShape, system: Int): LocalPoint = when (shape.kind) {
        // One circle, walked in index order: a ring galaxy is the one kind where the path closes.
        GalaxyKind.RING -> {
            val angle = 2f * PI.toFloat() * system / GalaxyBalance.SYSTEMS_PER_GALAXY
            LocalPoint(
                x = RING_RADIUS * cos(angle),
                y = RING_RADIUS * sin(angle),
                tangentX = -sin(angle),
                tangentY = cos(angle),
            )
        }

        // An Archimedean coil with no arms to speak of, which is what an elliptical is: the systems
        // are in it rather than on anything, and the index still runs from the middle outwards.
        GalaxyKind.ELLIPTICAL -> {
            val arc = system * PITCH + ELLIPTICAL_OFFSET
            val angle = sqrt(2f * arc / ELLIPTICAL_COIL)
            val radius = ELLIPTICAL_COIL * angle
            LocalPoint(
                x = radius * cos(angle),
                y = radius * sin(angle),
                tangentX = -sin(angle),
                tangentY = cos(angle),
            )
        }

        else -> {
            val arm = armSystems(shape)
            when {
                // Arm A, walked *inwards*: system 1 is out at the rim and system `arm` is at the
                // bar's near end, so the index enters the galaxy rather than leaving it.
                system <= arm -> armAt(shape, (arm - system).toFloat(), INWARD)
                // The bar, straight through the core, which is the only stretch of the path that is
                // not a spiral and the only place the two arms can meet without a seam.
                system <= arm + shape.barSystems -> {
                    val along = if (shape.barSystems > 1) {
                        (system - arm - 1).toFloat() / (shape.barSystems - 1)
                    } else {
                        0.5f
                    }
                    LocalPoint(
                        x = -shape.scale + 2f * shape.scale * along,
                        y = 0f,
                        tangentX = 1f,
                        tangentY = 0f,
                    )
                }
                // Arm B, walked outwards, back to the rim.
                else -> armAt(shape, (system - arm - shape.barSystems - 1).toFloat(), OUTWARD)
            }
        }
    }

    // A point on a logarithmic arm at arc length `step * PITCH` from its root. Parameterised by arc
    // length rather than by angle, which is the whole reason the index can *be* arc length: a coil
    // walked at constant angle bunches at the core and stretches at the rim, and a map that did that
    // would charge the same for two very different-looking hops.
    private fun armAt(shape: GalaxyShape, step: Float, arm: Int): LocalPoint {
        val arc = maxOf(0f, step) * PITCH
        val diagonal = sqrt(1f + shape.winding * shape.winding)
        val angle = ln(1f + arc * shape.winding / (shape.scale * diagonal)) / shape.winding
        val radius = shape.scale * exp(shape.winding * angle)
        val heading = angle + if (arm == OUTWARD) 0f else PI.toFloat()
        val x = radius * cos(heading)
        val y = radius * sin(heading)
        val tangent = unit(
            SkyPoint(
                x = shape.winding * x - y,
                y = shape.winding * y + x,
            ),
        )
        return LocalPoint(x = x, y = y, tangentX = tangent.x, tangentY = tangent.y)
    }

    // The inclination, and it is the only thing that makes a galaxy a picture rather than a plan:
    // one rotation and one squash, applied to every point and every direction alike.
    private fun incline(shape: GalaxyShape, x: Float, y: Float): SkyPoint {
        val c = cos(shape.rotationRadians)
        val s = sin(shape.rotationRadians)
        return SkyPoint(x = x * c - y * s, y = (x * s + y * c) * shape.tilt)
    }

    private fun armSystems(shape: GalaxyShape): Int =
        (GalaxyBalance.SYSTEMS_PER_GALAXY - shape.barSystems) / 2

    companion object {

        // ── the one space, and what a pixel is worth in it ───────────────────────────────────────

        // Arc length between two systems on an arm. Everything else in the galaxy is measured
        // against it: the bar's stride, the drift's cap, how wide a scatter may be.
        const val PITCH: Float = 2.6f

        const val MIN_PPU: Float = 0.05f
        const val MAX_PPU: Float = 4_000f

        fun clampPpu(ppu: Float): Float = ppu.coerceIn(MIN_PPU, MAX_PPU)

        // Where the bar's five steps land. **Values rather than thresholds** — a tap on a step flies
        // the view to this zoom, and the reading below decides what is drawn once it gets there.
        fun ppuOf(depth: SkyDepth): Float = when (depth) {
            SkyDepth.UNIVERSE -> 0.082f
            SkyDepth.GALAXY -> 2.1f
            SkyDepth.REGION -> 5.6f
            SkyDepth.SYSTEM -> 70f
            SkyDepth.WORLD -> 940f
        }

        // **Which step the bar lights, and nothing about what is drawn** — the drawing fades between
        // depths over a range of zoom (see the four ramps below) and never switches. This is the
        // reading the address is made of.
        //
        // The two flags are the fog and the bar's own rule in one: a step exists only once something
        // at that depth is selected, so a pinch past the system depth with no system under it is
        // still the region, and a pinch past the world depth with no world under it is still the
        // system. There is no orbit view of nothing.
        fun depthAt(ppu: Float, hasSystem: Boolean, hasWorld: Boolean): SkyDepth = when {
            ppu < 0.45f -> SkyDepth.UNIVERSE
            ppu < 3.4f -> SkyDepth.GALAXY
            ppu < 34f || !hasSystem -> SkyDepth.REGION
            ppu >= 260f && hasWorld -> SkyDepth.WORLD
            else -> SkyDepth.SYSTEM
        }

        // **The four fades, and there is no step anywhere.** Each runs 0 to 1 across its own band of
        // zoom, so a depth arrives by growing out of the one before it: galaxies resolve into stars,
        // stars gain a class and a name, worlds appear beside the surveyed ones, and the orbit view
        // takes the screen. Everything the canvas draws is one of these multiplied by an alpha.
        fun starsLod(ppu: Float): Float = ramp(ppu, from = 0.25f, to = 0.60f)

        fun sectorLod(ppu: Float): Float = ramp(ppu, from = 1.0f, to = 2.2f)

        fun hoodLod(ppu: Float): Float = ramp(ppu, from = 7f, to = 15f)

        fun systemLod(ppu: Float): Float = ramp(ppu, from = 34f, to = 56f)

        private fun ramp(ppu: Float, from: Float, to: Float): Float = ((ppu - from) / (to - from)).coerceIn(0f, 1f)

        // ── the system, in the same units, so the pinch carries on through it ────────────────────

        // An orbit is `ORBIT0 + rank * ORBIT_STEP` units and a world is a fraction of `PLANET`, so
        // the orbit view is not a screen: it is what this space looks like at seventy pixels a unit,
        // and a pinch past it reaches a sphere without anything switching.
        const val STAR_RADIUS: Float = 0.19f
        const val ORBIT_FIRST: Float = 0.95f
        const val ORBIT_STEP: Float = 0.40f
        const val PLANET_RADIUS: Float = 0.085f

        // **Ranked rather than spaced by slot**, which is the call `SystemMapUiState` already made
        // and for the same reason: fifteen slots across a frame puts neighbours closer than the
        // numeral printed under them. What is kept is the order; what is lost is the scale.
        fun orbitRadiusOf(rank: Int): Float = ORBIT_FIRST + rank * ORBIT_STEP

        // Gravity is the drawn diameter — the portrait's own rule, arriving as soon as the sphere is
        // big enough to carry it. Never more than the orbit gap can clear, which is what
        // `SkyGeometryTest` holds it to.
        fun worldRadiusOf(milliG: Int): Float =
            PLANET_RADIUS * (0.72f + 0.28f * min(1f, milliG / 3_000f))

        // ── what `core` generates, arriving here as the one number that can reorder anything ─────

        // The drift, along the arm, capped at half a pitch — the same number and the same cap the
        // fold used, so the rule that survived the redraw is enforced by the arithmetic rather than
        // by the picture.
        fun driftOf(permille: Int): Float = PITCH * permille.coerceIn(-DRIFT_CAP, DRIFT_CAP) / 1_000f

        fun systemsOfRegion(region: Int): IntRange {
            val first = (region - 1) * GalaxyBalance.SYSTEMS_PER_REGION + 1
            return first until first + GalaxyBalance.SYSTEMS_PER_REGION
        }

        fun regionOf(system: Int): Int = (system - 1) / GalaxyBalance.SYSTEMS_PER_REGION + 1

        // ── the picture's own noise ──────────────────────────────────────────────────────────────

        // **Drawing noise, and deliberately not a seed.** Where a star sits across its arm, how a
        // galaxy is tilted, where a mote of dust lies: none of it carries an ordering, a distance or
        // a price, so none of it belongs in `core` and none of it is charged for. It only has to be
        // the same every frame and the same on every platform, which is what this is for. Integer
        // arithmetic on bounded inputs, the rule the galaxy generator already follows.
        internal fun hash(value: Int): Float {
            var x = (value.toLong() * 2_654_435_761L + 20_260_807L) and MASK
            x = (x xor (x ushr 13)) and MASK
            x = (x * 1_103_515_245L + 12_345L) and MASK
            return (x % 10_000L).toFloat() / 10_000f
        }

        // Three uniforms make a passable bell, which is what an arm's cross-section wants: most
        // stars near the path and a few out at the edge of the light.
        private fun gauss(seed: Int): Float = hash(seed) + hash(seed + 1) + hash(seed + 2) - 1.5f

        // What `gauss` can reach, so a culling box can be sized rather than guessed.
        const val GAUSS_LIMIT: Float = 1.5f

        private fun unit(point: SkyPoint): SkyPoint {
            val length = hypot(point.x, point.y)
            return if (length < 1e-6f) SkyPoint(1f, 0f) else SkyPoint(point.x / length, point.y / length)
        }

        private fun spiralBarSystems(scale: Float): Int = maxOf(2, ceil(2f * scale / PITCH).toInt())

        private const val MASK: Long = 0x7fffffffL
        private const val DRIFT_CAP: Int = 500

        private const val GALAXIES_ACROSS: Int = 3
        private const val GALAXY_SPACING_X: Float = 1_500f
        private const val GALAXY_SPACING_Y: Float = 1_700f
        private const val GALAXY_JITTER_X: Float = 520f
        private const val GALAXY_JITTER_Y: Float = 420f

        private const val SPIRAL_SCALE: Float = 5f
        private const val SPIRAL_WINDING: Float = 0.28f
        private const val BARRED_SCALE: Float = 14f
        private const val BARRED_WINDING: Float = 0.22f
        private const val BARRED_BAR_SYSTEMS: Int = 14

        private const val ELLIPTICAL_COIL: Float = 6f
        private const val ELLIPTICAL_OFFSET: Float = 40f
        private const val ELLIPTICAL_RADIUS: Float = 100f
        private val RING_RADIUS: Float = GalaxyBalance.SYSTEMS_PER_GALAXY * PITCH / (2f * PI.toFloat())

        // Yours faces the reader: a fixed lean and a fixed tilt, so the galaxy you open every day is
        // the one shape in the sky that never surprises you.
        private const val HOME_ROTATION: Float = 0.35f
        private const val HOME_TILT: Float = 0.78f

        private const val INWARD: Int = 0
        private const val OUTWARD: Int = 1

        // Two strides that do not share a factor with the system count, so a scatter never lines up
        // into a pattern down an arm.
        private const val SCATTER_STRIDE: Int = 5_000
        private const val SCATTER_STEP: Int = 7

        private val KINDS: List<GalaxyKind> = listOf(
            GalaxyKind.SPIRAL,
            GalaxyKind.BARRED,
            GalaxyKind.ELLIPTICAL,
            GalaxyKind.SPIRAL,
            GalaxyKind.RING,
            GalaxyKind.BARRED,
            GalaxyKind.SPIRAL,
            GalaxyKind.ELLIPTICAL,
        )
    }
}

// The four kinds a galaxy comes in, and every colour on one of them is astronomy: gold for old stars
// in the bulge, blue for young ones along the arms, pink where stars are forming, brown where dust
// lies. **No status hue ever lands on a galaxy** — the accent is a selection and amber is your
// fleet, and neither is a fact about the sky.
enum class GalaxyKind { SPIRAL, BARRED, ELLIPTICAL, RING }

data class GalaxyShape(
    val kind: GalaxyKind,
    val rotationRadians: Float,
    // How far the disc is squashed towards the reader. 1 would be face on; nothing is.
    val tilt: Float,
    // The arm's half-width, as the standard deviation of where its stars lie across it.
    val scatter: Float,
    val barSystems: Int,
    val scale: Float,
    val winding: Float,
)

data class SkyPoint(val x: Float, val y: Float)

// A point on a galaxy's one path, with both directions the drawing needs there. The tangent is where
// a drift travels and the angle a region's name is set at; the normal is where a system's worlds are
// strung and where a label is pushed clear of the light.
data class SkyPathPoint(
    val x: Float,
    val y: Float,
    val tangentX: Float,
    val tangentY: Float,
    val normalX: Float,
    val normalY: Float,
    val tangentRadians: Float,
)

// The five steps, which are the bar and the address in one: universe, galaxy, region, system, world,
// read left to right as `[galaxy : system : slot]` with the region — which the address already
// implies — between the first two.
enum class SkyDepth { UNIVERSE, GALAXY, REGION, SYSTEM, WORLD }

private data class LocalPoint(val x: Float, val y: Float, val tangentX: Float, val tangentY: Float)
