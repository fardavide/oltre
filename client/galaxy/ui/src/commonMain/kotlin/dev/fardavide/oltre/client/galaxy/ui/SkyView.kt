package dev.fardavide.oltre.client.galaxy.ui

import dev.fardavide.oltre.client.world.ui.WorldPortraitUiState
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.SystemAddress
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// **What is selected, at whichever depth.** One value for the four things a tap can land on, so
// the bar, the caption and the canvas all read the same address; the extensions below are the
// address's parts, for the depths that have them.
sealed interface SkySelection {
    val galaxy: Int

    data class Galaxy(override val galaxy: Int) : SkySelection

    data class Region(override val galaxy: Int, val region: Int) : SkySelection

    data class System(val at: SystemAddress) : SkySelection {
        override val galaxy: Int get() = at.galaxy
    }

    data class World(val at: GalaxyCoordinate) : SkySelection {
        override val galaxy: Int get() = at.galaxy
    }
}

val SkySelection.system: SystemAddress?
    get() = when (this) {
        is SkySelection.Galaxy, is SkySelection.Region -> null
        is SkySelection.System -> at
        is SkySelection.World -> SystemAddress.of(at)
    }

val SkySelection.world: GalaxyCoordinate?
    get() = when (this) {
        is SkySelection.Galaxy, is SkySelection.Region, is SkySelection.System -> null
        is SkySelection.World -> at
    }

val SkySelection.region: Int?
    get() = when (this) {
        is SkySelection.Galaxy -> null
        is SkySelection.Region -> region
        is SkySelection.System -> SkyGeometry.regionOf(at.system)
        is SkySelection.World -> SkyGeometry.regionOf(at.system)
    }

data class SkyViewport(val width: Float, val height: Float)

// **Where the eye is.** A zoom, a centre in sky units and a selection: the whole state of the tab,
// and everything the canvas needs to paint a frame. The depth is read off it rather than stored,
// so a pinch can never leave the bar lying about where you are.
data class SkyView(
    val ppu: Float,
    val centreX: Float,
    val centreY: Float,
    val selection: SkySelection,
) {

    val depth: SkyDepth
        get() = SkyGeometry.depthAt(ppu, hasSystem = selection.system != null, hasWorld = selection.world != null)

    fun screenOf(point: SkyPoint, viewport: SkyViewport): SkyPoint = SkyPoint(
        x = viewport.width / 2f + (point.x - centreX) * ppu,
        y = viewport.height / 2f + (point.y - centreY) * ppu,
    )

    fun skyOf(sx: Float, sy: Float, viewport: SkyViewport): SkyPoint = SkyPoint(
        x = centreX + (sx - viewport.width / 2f) / ppu,
        y = centreY + (sy - viewport.height / 2f) / ppu,
    )

    fun panned(dx: Float, dy: Float): SkyView = copy(centreX = centreX - dx / ppu, centreY = centreY - dy / ppu)

    // One frame of a flight: the zoom in log space, so a dive through three orders of magnitude
    // reads as one even motion, and the centre in a straight line. The selection is the target's
    // from the first frame, so the bar and the caption lead the picture rather than trail it.
    fun towards(target: SkyView, fraction: Float): SkyView = SkyView(
        ppu = exp(ln(ppu) + (ln(target.ppu) - ln(ppu)) * fraction),
        centreX = centreX + (target.centreX - centreX) * fraction,
        centreY = centreY + (target.centreY - centreY) * fraction,
        selection = target.selection,
    )
}

// A tap's outcome: the view after it, and whether it began a flight.
data class SkyMove(val view: SkyView, val flown: Boolean)

// **The rules of the one gesture**, over one sky. `SkyGeometry` says where everything is;
// this says what a tap, a pan and a pinch mean once they land there — and it is arithmetic on a
// `SkyUiState`, so `SkyViewTest` can hold every rule without a frame being drawn.
class SkyScene(private val state: SkyUiState) {

    val sky: SkyGeometry = SkyGeometry(state.home.galaxy)

    val home: GalaxyCoordinate get() = state.home

    val words: SkyWordsUiState get() = state.words

    fun galaxyOf(galaxy: Int): SkyGalaxyUiState = state.galaxies[galaxy - 1]

    fun starOf(at: SystemAddress): SkyStarUiState = galaxyOf(at.galaxy).stars[at.system - 1]

    fun positionOf(at: SystemAddress): SkyPoint = sky.positionOf(at.galaxy, at.system, starOf(at).driftPermille)

    fun bodyOf(at: GalaxyCoordinate): SkyBodyUiState? =
        starOf(SystemAddress.of(at)).worlds.firstOrNull { it.slot == at.slot }

    // The centre of the universe: the middle of the nine.
    val universeCentre: SkyPoint by lazy {
        val centres = (1..GalaxyBalance.GALAXIES).map(sky::centreOf)
        SkyPoint(x = centres.map { it.x }.average().toFloat(), y = centres.map { it.y }.average().toFloat())
    }

    // A world on its orbit about its star, in sky units. Ranked among the star's worlds rather than
    // spaced by slot, alternating sides so a system of five does not stack on one flank, and jittered
    // a little so no two systems read as the same diagram.
    fun worldPositionOf(at: GalaxyCoordinate): SkyPoint {
        val star = starOf(SystemAddress.of(at))
        val centre = positionOf(SystemAddress.of(at))
        val rank = star.worlds.indexOfFirst { it.slot == at.slot }
        if (rank < 0) return centre
        val rx = SkyGeometry.orbitRadiusOf(rank)
        val ry = rx * ORBIT_TILT
        val angle = -PI.toFloat() / 2f +
            rank * (2f * PI.toFloat() / max(1, star.worlds.size)) +
            (if (rank % 2 == 1) PI.toFloat() else 0f) +
            (SkyGeometry.hash(at.galaxy * 5_000 + at.system * 13 + at.slot) - 0.5f) * 0.5f
        return SkyPoint(x = centre.x + rx * cos(angle), y = centre.y + ry * sin(angle))
    }

    fun worldRadiusOf(at: GalaxyCoordinate): Float = when (val portrait = bodyOf(at)?.portrait) {
        is WorldPortraitUiState.Surveyed -> SkyGeometry.worldRadiusOf(portrait.gravity.milliG)
        is WorldPortraitUiState.Unsurveyed, null -> SkyGeometry.worldRadiusOf(SOCKET_MILLI_G)
    }

    // The zoom at which a world fills the frame.
    fun worldPpuOf(at: GalaxyCoordinate): Float = WORLD_FILL_PX / worldRadiusOf(at)

    // What a tap lands on: a galaxy at the universe depth, a region at the galaxy depth (250 stars
    // at two pixels each are not tap targets; ten clusters of 25 are), a star past it, and a world
    // of the selected system once its orbit view is in and the finger is within reach of one.
    fun nearest(view: SkyView, sx: Float, sy: Float, viewport: SkyViewport): SkySelection? {
        val depth = view.depth
        if (depth == SkyDepth.UNIVERSE) {
            return SkySelection.Galaxy(
                (1..GalaxyBalance.GALAXIES).minBy { galaxy -> distance(view.screenOf(sky.centreOf(galaxy), viewport), sx, sy) },
            )
        }
        val system = view.selection.system
        if ((depth == SkyDepth.SYSTEM || depth == SkyDepth.WORLD) && system != null) {
            val body = nearestBody(view, system, sx, sy, viewport)
            if (body != null && body.second < max(TOUCH_PX, view.ppu * BODY_REACH)) return SkySelection.World(body.first)
        }
        var best: SystemAddress? = null
        var bestDistance = Float.MAX_VALUE
        for (galaxy in 1..GalaxyBalance.GALAXIES) {
            if (!inView(view, galaxy, viewport)) continue
            for (star in galaxyOf(galaxy).stars) {
                val at = SystemAddress(galaxy, star.system)
                val q = view.screenOf(positionOf(at), viewport)
                if (q.x < -TAP_MARGIN || q.y < -TAP_MARGIN || q.x > viewport.width + TAP_MARGIN || q.y > viewport.height + TAP_MARGIN) continue
                val d = distance(q, sx, sy)
                if (d < bestDistance) {
                    bestDistance = d
                    best = at
                }
            }
        }
        val found = best ?: return null
        return if (depth == SkyDepth.GALAXY) SkySelection.Region(found.galaxy, SkyGeometry.regionOf(found.system)) else SkySelection.System(found)
    }

    // Past the galaxy depth the selection follows the zoom: whatever is under the centre of the
    // screen is what the caption prices, so a pinch alone reaches a world. Once the orbit view is
    // fully in the system holds under a pan — its neighbours are a thumb away on screen and a
    // system that swapped mid-pan would take the orbit view with it.
    fun settled(view: SkyView, viewport: SkyViewport): SkyView {
        if (view.ppu < SETTLE_FROM) return view
        var best = view.selection
        if (view.selection.system == null || view.ppu < SYSTEM_HOLDS_FROM) {
            best = SkySelection.System(nearestStarToCentre(view))
        }
        val system = best.system
        if (view.ppu >= WORLD_FROM && system != null) {
            val body = nearestBody(view, system, viewport.width / 2f, viewport.height / 2f, viewport)
            // a world is adopted only when it is in front of you; over the star the system stays
            best = if (body != null && body.second < min(viewport.width, viewport.height) * IN_FRONT) {
                SkySelection.World(body.first)
            } else {
                SkySelection.System(system)
            }
        }
        return if (best == view.selection) view else view.copy(selection = best)
    }

    // The fixed point of a zoom is the finger — until the finger is over a body at the system depth,
    // where it becomes the body. Fingers cannot hold a 3,000× focus; a sub-pixel miss would be a
    // screen's width by the world depth, so past the galaxy the zoom anchors on the nearest star or
    // world within reach of the touch, and the pinch lands on it.
    fun zoomedAt(view: SkyView, sx: Float, sy: Float, factor: Float, viewport: SkyViewport): SkyView {
        var fx = sx
        var fy = sy
        val system = view.selection.system
        if (view.ppu >= SETTLE_FROM && system != null && factor > 1f) {
            val body = nearestBody(view, system, sx, sy, viewport)
            val target = body?.let { worldPositionOf(it.first) } ?: positionOf(system)
            val q = view.screenOf(target, viewport)
            if (body != null && body.second < SNAP_PX) {
                fx = q.x
                fy = q.y
            }
            // a zoom about a point can never bring a body that is off the screen back onto it, so a
            // pinch into the dark first pans: while no body of the system is in view the zoom holds
            // and the centre halves its distance to the nearest body each step
            if (distance(q, viewport.width / 2f, viewport.height / 2f) > IN_FRONT * min(viewport.width, viewport.height)) {
                return view.copy(
                    centreX = view.centreX + (target.x - view.centreX) * 0.5f,
                    centreY = view.centreY + (target.y - view.centreY) * 0.5f,
                )
            }
        }
        val ppu = SkyGeometry.clampPpu(view.ppu * factor)
        val fixed = view.skyOf(fx, fy, viewport)
        return view.copy(
            ppu = ppu,
            centreX = fixed.x - (fx - viewport.width / 2f) / ppu,
            centreY = fixed.y - (fy - viewport.height / 2f) / ppu,
        )
    }

    // Where a step of the bar flies to: the zoom of that depth, centred on the selection's part
    // of it. The world step is the one that computes its zoom — the world must fill the frame
    // whatever its gravity made of it.
    fun flightTo(view: SkyView, depth: SkyDepth, selection: SkySelection = view.selection): SkyView {
        val world = selection.world
        val system = selection.system
        val (ppu, centre) = when {
            depth == SkyDepth.WORLD && world != null -> worldPpuOf(world) to worldPositionOf(world)
            depth == SkyDepth.REGION ->
                SkyGeometry.ppuOf(depth) to sky.regionCentreOf(selection.galaxy, selection.region ?: MIDDLE_REGION)
            else -> SkyGeometry.ppuOf(depth) to when {
                depth == SkyDepth.UNIVERSE -> universeCentre
                depth == SkyDepth.GALAXY -> sky.centreOf(selection.galaxy)
                system != null -> positionOf(system)
                else -> sky.regionCentreOf(selection.galaxy, selection.region ?: MIDDLE_REGION)
            }
        }
        return SkyView(ppu = ppu, centreX = centre.x, centreY = centre.y, selection = selection)
    }

    // One dive: to the depth of whatever is selected. A galaxy opens on your own region when it is
    // yours and on its middle one otherwise.
    fun dived(view: SkyView): SkyView = when (val selection = view.selection) {
        is SkySelection.Galaxy -> {
            val region = if (selection.galaxy == state.home.galaxy) SkyGeometry.regionOf(state.home.system) else MIDDLE_REGION
            flightTo(view, SkyDepth.GALAXY, SkySelection.Region(selection.galaxy, region))
        }
        is SkySelection.Region -> flightTo(view, SkyDepth.REGION, selection)
        is SkySelection.System -> flightTo(view, SkyDepth.SYSTEM, selection)
        is SkySelection.World -> flightTo(view, SkyDepth.WORLD, selection)
    }

    // Two taps, two meanings: on anything else, *this one*; on the selection, *in*.
    fun tapped(view: SkyView, sx: Float, sy: Float, viewport: SkyViewport): SkyMove {
        val picked = nearest(view, sx, sy, viewport) ?: return SkyMove(view, flown = false)
        return if (picked == view.selection) {
            SkyMove(dived(view), flown = true)
        } else {
            SkyMove(view.copy(selection = picked), flown = false)
        }
    }

    // A galaxy is worth walking when any of it can be on screen.
    fun inView(view: SkyView, galaxy: Int, viewport: SkyViewport): Boolean {
        val centre = view.screenOf(sky.centreOf(galaxy), viewport)
        val reach = (sky.radiusOf(galaxy) + sky.scatterLimitOf(galaxy)) * view.ppu
        return centre.x >= -reach && centre.x <= viewport.width + reach &&
            centre.y >= -reach && centre.y <= viewport.height + reach
    }

    private fun nearestStarToCentre(view: SkyView): SystemAddress {
        var best = SystemAddress.of(state.home)
        var bestDistance = Float.MAX_VALUE
        for (galaxy in 1..GalaxyBalance.GALAXIES) {
            for (star in galaxyOf(galaxy).stars) {
                val at = SystemAddress(galaxy, star.system)
                val p = positionOf(at)
                val d = hypot(p.x - view.centreX, p.y - view.centreY)
                if (d < bestDistance) {
                    bestDistance = d
                    best = at
                }
            }
        }
        return best
    }

    private fun nearestBody(view: SkyView, system: SystemAddress, sx: Float, sy: Float, viewport: SkyViewport): Pair<GalaxyCoordinate, Float>? =
        starOf(system).worlds
            .map { body ->
                val at = GalaxyCoordinate(system.galaxy, system.system, body.slot)
                at to distance(view.screenOf(worldPositionOf(at), viewport), sx, sy)
            }
            .minByOrNull { it.second }

    private fun distance(point: SkyPoint, x: Float, y: Float): Float = hypot(point.x - x, point.y - y)

    companion object {
        // The orbit view's foreshortening: an orbit is an ellipse half as tall as it is wide.
        const val ORBIT_TILT: Float = 0.5f

        // A world fills the frame at eighty pixels of radius.
        const val WORLD_FILL_PX: Float = 80f

        // A socket — a slot no probe has been to — is drawn at one g.
        private const val SOCKET_MILLI_G: Int = 1_000

        // The zoom from which the selection follows the centre, the zoom from which the selected
        // system holds under a pan, and the zoom from which a world in front of you is adopted.
        private const val SETTLE_FROM: Float = 34f
        private const val SYSTEM_HOLDS_FROM: Float = 56f
        private const val WORLD_FROM: Float = 260f

        // How much of the shorter side a body may be off centre and still count as in front of you.
        private const val IN_FRONT: Float = 0.45f

        // A thumb, the fraction of the zoom a body's reach grows by, the distance under which a pinch
        // snaps its fixed point to a body, and how far off screen a star may sit and still be tapped.
        private const val TOUCH_PX: Float = 44f
        private const val BODY_REACH: Float = 0.2f
        private const val SNAP_PX: Float = 60f
        private const val TAP_MARGIN: Float = 20f

        // Where an unvisited galaxy opens: its middle region.
        private const val MIDDLE_REGION: Int = 5
    }
}
