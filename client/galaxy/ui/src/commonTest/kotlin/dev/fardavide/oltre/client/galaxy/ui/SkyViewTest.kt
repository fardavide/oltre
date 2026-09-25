package dev.fardavide.oltre.client.galaxy.ui

import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.world.ui.WorldPortraitUiState
import dev.fardavide.oltre.core.GalaxyBalance
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.Gravity
import dev.fardavide.oltre.core.Pressure
import dev.fardavide.oltre.core.StarClass
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.Temperature
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// **The one gesture, as rules.** A pinch, a pan and a tap are the whole of the sky's controls, and
// what each of them means changes with the depth: a tap picks a galaxy, then a region, then a star,
// then a world, and past the galaxy the selection stops waiting for a tap and follows the centre of
// the screen. None of that is drawing, so none of it is asserted through a screenshot — it is asserted
// here, on a `SkyScene` over a fixture sky, in pixels and units.
class SkyViewTest {

    private val scene = SkyScene(skyFixture())
    private val viewport = SkyViewport(width = 361f, height = 440f)

    @Test
    fun `a tap at the universe depth picks the nearest galaxy`() {
        // given — the universe, centred on the fourth galaxy
        val fourth = scene.sky.centreOf(4)
        val view = scene.flightTo(home(), SkyDepth.UNIVERSE).copy(centreX = fourth.x, centreY = fourth.y)

        // when — a tap at the centre of the screen
        val picked = scene.nearest(view, viewport.width / 2f, viewport.height / 2f, viewport)

        // then
        assertEquals(SkySelection.Galaxy(4), picked)
    }

    @Test
    fun `a tap at the galaxy depth picks the region of the nearest star and never the star`() {
        // At 2.1 pixels a unit a star is two pixels across and a thumb is forty-four, so the
        // galaxy depth's tap target is the twenty-five and not the one.
        val star = SystemAddress(galaxy = HOME.galaxy, system = 40)
        val view = scene.flightTo(home(), SkyDepth.GALAXY)
        val at = view.screenOf(scene.positionOf(star), viewport)

        val picked = scene.nearest(view, at.x, at.y, viewport)

        assertEquals(SkySelection.Region(galaxy = HOME.galaxy, region = SkyGeometry.regionOf(40)), picked)
    }

    @Test
    fun `a tap at the region depth picks the nearest star`() {
        // A thumb lands near a star and not on it — but an arm is a band, and scatter can put two
        // neighbours within a couple of pixels of each other, so the miss here is a fraction of one.
        val star = SystemAddress(galaxy = HOME.galaxy, system = 118)
        val view = scene.flightTo(home(), SkyDepth.REGION)
        val at = view.screenOf(scene.positionOf(star), viewport)

        val picked = scene.nearest(view, at.x + 0.4f, at.y - 0.3f, viewport)

        assertEquals(SkySelection.System(star), picked)
    }

    @Test
    fun `a tap at the system depth picks the world under the finger`() {
        val world = GalaxyCoordinate(galaxy = HOME.galaxy, system = HOME.system, slot = 3)
        val view = scene.flightTo(home(), SkyDepth.SYSTEM)
        val at = view.screenOf(scene.worldPositionOf(world), viewport)

        val picked = scene.nearest(view, at.x, at.y, viewport)

        assertEquals(SkySelection.World(world), picked)
    }

    @Test
    fun `a tap on the selection dives and a tap elsewhere only selects`() {
        // Two taps, two meanings: the first says *this one*, the second says *in*.
        val view = scene.flightTo(home(), SkyDepth.REGION)
        val other = SystemAddress(galaxy = HOME.galaxy, system = 118)
        val at = view.screenOf(scene.positionOf(other), viewport)

        val selected = scene.tapped(view, at.x, at.y, viewport)
        val dived = scene.tapped(selected.view, at.x, at.y, viewport)

        assertEquals(SkySelection.System(other), selected.view.selection)
        assertFalse(selected.flown, "a first tap must not fly")
        assertTrue(dived.flown, "a second tap on the selection must fly")
        assertEquals(SkyDepth.SYSTEM, dived.view.depth)
    }

    @Test
    fun `a dive from a galaxy lands on your own region at home and on the middle region elsewhere`() {
        val universe = scene.flightTo(home(), SkyDepth.UNIVERSE)

        val home = scene.dived(universe.copy(selection = SkySelection.Galaxy(HOME.galaxy)))
        val away = scene.dived(universe.copy(selection = SkySelection.Galaxy(7)))

        assertEquals(SkySelection.Region(HOME.galaxy, SkyGeometry.regionOf(HOME.system)), home.selection)
        assertEquals(SkySelection.Region(7, MIDDLE_REGION), away.selection)
        assertEquals(SkyDepth.GALAXY, home.depth)
    }

    @Test
    fun `past the region depth the selection follows the centre of the screen`() {
        // A pan alone reaches a star: as the orbit view fades in, whatever is under the centre is
        // what the caption prices.
        val other = SystemAddress(galaxy = HOME.galaxy, system = 118)
        val view = scene.flightTo(home(), SkyDepth.SYSTEM).copy(ppu = ORBIT_FADING_PPU)
        val target = scene.positionOf(other)

        val settled = scene.settled(view.copy(centreX = target.x, centreY = target.y), viewport)

        assertEquals(SkySelection.System(other), settled.selection)
    }

    @Test
    fun `once the orbit view is in the system holds under a pan`() {
        // Its neighbours are a thumb away on screen at seventy pixels a unit, and a system that
        // swapped mid-pan would take the orbit view with it.
        val view = scene.flightTo(home(), SkyDepth.SYSTEM)
        val other = scene.positionOf(SystemAddress(galaxy = HOME.galaxy, system = 126))

        val settled = scene.settled(view.copy(centreX = other.x, centreY = other.y), viewport)

        assertEquals(view.selection, settled.selection)
    }

    @Test
    fun `at the region depth a pan leaves the selection alone`() {
        val view = scene.flightTo(home(), SkyDepth.REGION)
        val other = scene.positionOf(SystemAddress(galaxy = HOME.galaxy, system = 118))

        val settled = scene.settled(view.copy(centreX = other.x, centreY = other.y), viewport)

        assertEquals(view.selection, settled.selection)
    }

    @Test
    fun `past the world depth the world in front of you is adopted and over the star it is not`() {
        val world = GalaxyCoordinate(galaxy = HOME.galaxy, system = HOME.system, slot = 3)
        val deep = scene.flightTo(home(), SkyDepth.WORLD, SkySelection.World(world)).ppu
        val overWorld = scene.worldPositionOf(world)
        val overStar = scene.positionOf(SystemAddress.of(HOME))

        val onWorld = scene.settled(
            SkyView(ppu = deep, centreX = overWorld.x, centreY = overWorld.y, selection = SkySelection.System(SystemAddress.of(HOME))),
            viewport,
        )
        val onStar = scene.settled(
            SkyView(ppu = deep, centreX = overStar.x, centreY = overStar.y, selection = SkySelection.World(world)),
            viewport,
        )

        assertEquals(SkySelection.World(world), onWorld.selection)
        assertEquals(SkySelection.System(SystemAddress.of(HOME)), onStar.selection)
    }

    @Test
    fun `a zoom keeps the point under the finger where it is`() {
        val view = scene.flightTo(home(), SkyDepth.GALAXY)
        val finger = SkyPoint(x = 90f, y = 300f)
        val before = view.skyOf(finger.x, finger.y, viewport)

        val zoomed = scene.zoomedAt(view, finger.x, finger.y, factor = 1.5f, viewport = viewport)

        val after = zoomed.screenOf(before, viewport)
        assertTrue(hypot(after.x - finger.x, after.y - finger.y) < 0.01f, "the fixed point moved to $after")
        assertEquals(view.ppu * 1.5f, zoomed.ppu, absoluteTolerance = 0.001f)
    }

    @Test
    fun `a zoom past either end of the range stops there`() {
        val out = scene.zoomedAt(scene.flightTo(home(), SkyDepth.UNIVERSE), 10f, 10f, factor = 0.001f, viewport = viewport)
        val deep = scene.zoomedAt(scene.flightTo(home(), SkyDepth.SYSTEM), 10f, 10f, factor = 1_000f, viewport = viewport)

        assertEquals(SkyGeometry.MIN_PPU, out.ppu)
        assertEquals(SkyGeometry.MAX_PPU, deep.ppu)
    }

    @Test
    fun `a pinch into the dark past the system depth pans towards the nearest body before it zooms`() {
        // A zoom about a point can never bring a body that is off the screen back onto it, so while
        // no body of the system is in view the zoom holds and the centre halves its distance.
        val view = scene.flightTo(home(), SkyDepth.SYSTEM)
        val far = view.copy(centreX = view.centreX + 40f, centreY = view.centreY)

        val pinched = scene.zoomedAt(far, viewport.width / 2f, viewport.height / 2f, factor = 1.3f, viewport = viewport)

        assertEquals(far.ppu, pinched.ppu, "the zoom must hold while nothing is in view")
        assertTrue(abs(pinched.centreX - view.centreX) < abs(far.centreX - view.centreX), "the centre did not come back")
    }

    @Test
    fun `a flight interpolates the zoom in log space and the centre in a straight line`() {
        val from = scene.flightTo(home(), SkyDepth.UNIVERSE)
        val to = scene.flightTo(home(), SkyDepth.SYSTEM)

        val half = from.towards(to, fraction = 0.5f)

        assertEquals(sqrt(from.ppu * to.ppu), half.ppu, absoluteTolerance = 0.01f)
        assertEquals((from.centreX + to.centreX) / 2f, half.centreX, absoluteTolerance = 0.01f)
        assertEquals(to.selection, half.selection)
    }

    @Test
    fun `the world step flies to the zoom at which the world fills the frame`() {
        val world = GalaxyCoordinate(galaxy = HOME.galaxy, system = HOME.system, slot = 3)

        val flown = scene.flightTo(home(), SkyDepth.WORLD, SkySelection.World(world))

        assertEquals(SkyDepth.WORLD, flown.depth)
        assertEquals(WORLD_FILL_PX, scene.worldRadiusOf(world) * flown.ppu, absoluteTolerance = 0.5f)
    }

    private fun home(): SkyView = SkyView(
        ppu = SkyGeometry.ppuOf(SkyDepth.REGION),
        centreX = 0f,
        centreY = 0f,
        selection = SkySelection.System(SystemAddress.of(HOME)),
    )

    private companion object {
        val HOME = GalaxyCoordinate(galaxy = 2, system = 125, slot = 1)
        const val MIDDLE_REGION: Int = 5
        const val WORLD_FILL_PX: Float = 80f
        const val ORBIT_FADING_PPU: Float = 40f
    }
}

// Nine galaxies of grain with a lit stretch in the second, three worlds about the home star and one
// about a neighbour. Enough sky for every rule above to have something to pick, and no more.
internal fun skyFixture(home: GalaxyCoordinate = GalaxyCoordinate(galaxy = 2, system = 125, slot = 1)): SkyUiState {
    val surveyed = mapOf(
        home.system to listOf(1, 3, 5),
        home.system + 5 to listOf(2),
    )
    return SkyUiState(
        home = home,
        galaxies = (1..GalaxyBalance.GALAXIES).map { galaxy ->
            SkyGalaxyUiState(
                galaxy = galaxy,
                label = TextRes("Galaxy $galaxy"),
                known = TextRes("uncharted"),
                charted = galaxy == home.galaxy,
                stars = (1..GalaxyBalance.SYSTEMS_PER_GALAXY).map { system ->
                    val lit = galaxy == home.galaxy && system in 100..150
                    val worlds = if (galaxy == home.galaxy) surveyed[system].orEmpty() else emptyList()
                    SkyStarUiState(
                        system = system,
                        driftPermille = 0,
                        ink = if (lit) SkyStarInk.Charted(StarClass.STANDARD, sizePermille = 1_000) else SkyStarInk.Grain,
                        surveyed = worlds.isNotEmpty(),
                        inFlight = false,
                        name = if (worlds.isNotEmpty()) TextRes("System $system") else null,
                        worlds = worlds.map { slot ->
                            SkyBodyUiState(
                                slot = slot,
                                name = TextRes("World $slot"),
                                portrait = WorldPortraitUiState.Surveyed(
                                    temperature = Temperature(20),
                                    gravity = Gravity(1_000),
                                    pressure = Pressure(1_000),
                                    hazards = emptySet(),
                                    hasRing = false,
                                ),
                                home = system == home.system && slot == home.slot,
                                yours = false,
                                metal = 1f,
                                crystal = 0.5f,
                            )
                        },
                    )
                },
                regions = (1..GalaxyBalance.REGIONS_PER_GALAXY).map { region ->
                    SkyRegionUiState(region = region, name = TextRes("Region $region"), lit = galaxy == home.galaxy && region == 5)
                },
                hours = emptyList(),
                flights = emptyList(),
            )
        },
        steps = emptyList(),
        caption = MapCaptionUiState(
            system = TextRes("Home"),
            coordinate = TextRes("[2:125]"),
            meta = TextRes(""),
            compactMeta = TextRes(""),
            detail = null,
            trailing = null,
            own = true,
        ),
        count = TextRes(""),
        words = SkyWordsUiState(metal = TextRes("METAL"), crystal = TextRes("CRYSTAL"), alone = TextRes("UNCHARTED")),
        dispatch = null,
    )
}
