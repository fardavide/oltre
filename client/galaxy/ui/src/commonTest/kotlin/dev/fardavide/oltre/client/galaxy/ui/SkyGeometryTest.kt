package dev.fardavide.oltre.client.galaxy.ui

import dev.fardavide.oltre.core.GalaxyBalance
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The sky, as numbers. **Separated from the drawing for the reason `MapGeometryTest` was**: the
// property the whole design rests on — that path order is index order, at every depth and in five
// orders of zoom — is a claim about arithmetic, and a recorded frame of 2,250 dots cannot tell a
// correct one from a transposed one.
//
// What replaced the fold did not replace its rule. A galaxy is no longer ten ruled bands; it is two
// spiral arms through a bar, and the 250 systems ride that one curve from the rim in, across the
// core and out again. The index is still arc length, so a round trip is still a distance along the
// line — which is the only thing `SurveyBalance` was ever drawn against.
class SkyGeometryTest {

    private val sky = SkyGeometry(homeGalaxy = HOME_GALAXY)

    @Test
    fun `every galaxy puts its systems on one continuous path`() {
        // The claim is that the path never jumps: consecutive systems are always about a pitch
        // apart, so there is no seam where the drawing stops being a line. A barred spiral has two
        // arms and a bar and this walks through all three of them.
        for (galaxy in 1..GalaxyBalance.GALAXIES) {
            for (system in 1 until GalaxyBalance.SYSTEMS_PER_GALAXY) {
                val here = sky.pathOf(galaxy, system)
                val next = sky.pathOf(galaxy, system + 1)
                val step = hypot(next.x - here.x, next.y - here.y)
                assertTrue(
                    step <= SkyGeometry.PITCH * 2f,
                    "galaxy $galaxy jumps ${step}u between $system and ${system + 1}",
                )
            }
        }
    }

    @Test
    fun `the path runs from the rim in through the core and out to the rim again`() {
        // Arm A from the rim to one end of the bar, the bar through the core, arm B out. So the
        // two ends of the index are the two ends of the galaxy and the middle is its centre —
        // which is what makes a round trip across the index a round trip across the sky.
        val centre = sky.centreOf(HOME_GALAXY)
        fun radiusAt(system: Int): Float = sky.pathOf(HOME_GALAXY, system).let {
            hypot(it.x - centre.x, it.y - centre.y)
        }

        val first = radiusAt(1)
        val middle = radiusAt(GalaxyBalance.SYSTEMS_PER_GALAXY / 2)
        val last = radiusAt(GalaxyBalance.SYSTEMS_PER_GALAXY)

        assertTrue(first > middle, "system 1 should be out at the rim, was ${first}u against ${middle}u")
        assertTrue(last > middle, "system 250 should be out at the rim, was ${last}u against ${middle}u")
    }

    @Test
    fun `the drift along an arm never reaches half a pitch`() {
        // **The honesty rule, and it is `core`'s number rather than the drawing's.** What moves a
        // system *along* its own path is the same permille the fold used, capped at half a pitch —
        // so however far a star is flung across the arm, two of them can never change places in the
        // index, and a distance read off the drawing is still the distance the game charges.
        for (permille in -2_000..2_000) {
            assertTrue(
                abs(SkyGeometry.driftOf(permille)) <= SkyGeometry.PITCH / 2f + TOLERANCE,
                "a drift of $permille travels ${SkyGeometry.driftOf(permille)}u",
            )
        }
        assertEquals(SkyGeometry.PITCH / 2f, SkyGeometry.driftOf(DRIFT_LIMIT))
    }

    @Test
    fun `a star never strays further from its path than the arm is wide`() {
        // An arm is a band of stars rather than a wire, and the band has a width — which a culling
        // box and a label's collision box both have to be able to ask for rather than guess.
        for (galaxy in 1..GalaxyBalance.GALAXIES) {
            val limit = sky.scatterLimitOf(galaxy)
            for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
                val path = sky.pathOf(galaxy, system)
                val placed = sky.positionOf(galaxy, system, driftPermille = DRIFT_LIMIT)
                val strayed = hypot(placed.x - path.x, placed.y - path.y)
                assertTrue(strayed <= limit + TOLERANCE, "[$galaxy:$system] strayed ${strayed}u against ${limit}u")
            }
        }
    }

    @Test
    fun `the galaxies sit on one serpentine in index order`() {
        // The universe keeps the rule a galaxy keeps: the next galaxy is the nearest one. Cohorts
        // open at the path's end, so where a commander sits on the serpentine says which week they
        // arrived — and a stranger's neighbourhood is findable by anyone who reads the sky.
        val far = hypot(
            sky.centreOf(GalaxyBalance.GALAXIES).x - sky.centreOf(1).x,
            sky.centreOf(GalaxyBalance.GALAXIES).y - sky.centreOf(1).y,
        )
        for (galaxy in 1 until GalaxyBalance.GALAXIES) {
            val step = hypot(
                sky.centreOf(galaxy + 1).x - sky.centreOf(galaxy).x,
                sky.centreOf(galaxy + 1).y - sky.centreOf(galaxy).y,
            )
            assertTrue(step < far, "galaxy $galaxy and ${galaxy + 1} are ${step}u apart against ${far}u end to end")
        }
    }

    @Test
    fun `the region a view is centred on is the region it is looking at`() {
        // A region is the tap target the galaxy depth has, because at 2.1 pixels a unit a star is
        // two pixels and a thumb is forty-four. So flying to a region has to land on *that* region:
        // of the ten centres, the one nearest a region's middle system is its own.
        for (region in 1..GalaxyBalance.REGIONS_PER_GALAXY) {
            val middle = sky.pathOf(HOME_GALAXY, SkyGeometry.systemsOfRegion(region).first + MIDDLE_OF_REGION)
            val nearest = (1..GalaxyBalance.REGIONS_PER_GALAXY).minBy { candidate ->
                val centre = sky.regionCentreOf(HOME_GALAXY, candidate)
                hypot(centre.x - middle.x, centre.y - middle.y)
            }
            assertEquals(region, nearest, "the centre nearest region $region's middle belongs to region $nearest")
        }
    }

    @Test
    fun `the home galaxy is the barred spiral and no two galaxies read the same`() {
        // Yours is barred because it is the one you will look at every day and a bar is the most
        // legible thing a galaxy can be. The rest come from the seed, and the tilt is what stops
        // two of a kind reading as a repeat.
        assertEquals(GalaxyKind.BARRED, sky.shapeOf(HOME_GALAXY).kind)
        val tilts = (1..GalaxyBalance.GALAXIES).map { sky.shapeOf(it).tilt }
        assertEquals(tilts.size, tilts.distinct().size, "two galaxies share a tilt: $tilts")
    }

    @Test
    fun `the depth a zoom reads as never goes backwards`() {
        // Depths fade into one another over a range of zoom rather than switching, so the one thing
        // the reading must never do is step back as the pinch goes in.
        var last = SkyDepth.UNIVERSE
        var ppu = SkyGeometry.MIN_PPU
        while (ppu <= SkyGeometry.MAX_PPU) {
            val depth = SkyGeometry.depthAt(ppu, hasSystem = true, hasWorld = true)
            assertTrue(depth.ordinal >= last.ordinal, "at ${ppu}px a unit the sky went back to $depth")
            last = depth
            ppu *= 1.05f
        }
        assertEquals(SkyDepth.WORLD, last)
    }

    @Test
    fun `a depth with nothing selected under it is never reached`() {
        // The bar is the address, so a step exists only once something at that depth is selected.
        // Past the galaxy the same rule guards the *drawing*: there is no orbit view without a
        // system and no sphere without a world, however far the pinch went.
        assertEquals(
            SkyDepth.REGION,
            SkyGeometry.depthAt(SkyGeometry.ppuOf(SkyDepth.SYSTEM), hasSystem = false, hasWorld = false),
        )
        assertEquals(
            SkyDepth.SYSTEM,
            SkyGeometry.depthAt(SkyGeometry.ppuOf(SkyDepth.WORLD), hasSystem = true, hasWorld = false),
        )
    }

    @Test
    fun `every ramp runs from nothing to everything across its own band`() {
        for (ramp in listOf(SkyGeometry::starsLod, SkyGeometry::sectorLod, SkyGeometry::hoodLod, SkyGeometry::systemLod)) {
            assertEquals(0f, ramp(SkyGeometry.MIN_PPU))
            assertEquals(1f, ramp(SkyGeometry.MAX_PPU))
        }
    }

    @Test
    fun `two worlds on one string are never closer than a disc`() {
        // The geometry test the design asked for by name. Orbits are ranked rather than spaced by
        // slot, so the gap between two of them is one constant and it has to clear the widest world
        // the ramp can draw.
        val widest = SkyGeometry.worldRadiusOf(GalaxyBalance.MAX_GRAVITY_MILLI_G)
        for (index in 0 until GalaxyBalance.SLOTS_PER_SYSTEM - 1) {
            val gap = SkyGeometry.orbitRadiusOf(index + 1) - SkyGeometry.orbitRadiusOf(index)
            assertTrue(gap > 2f * widest, "orbits $index and ${index + 1} are ${gap}u apart against ${2f * widest}u of world")
        }
    }

    @Test
    fun `the zoom spans five orders of magnitude and stops at both ends`() {
        assertEquals(SkyGeometry.MIN_PPU, SkyGeometry.clampPpu(0f))
        assertEquals(SkyGeometry.MAX_PPU, SkyGeometry.clampPpu(1_000_000f))
        assertTrue(SkyGeometry.MAX_PPU / SkyGeometry.MIN_PPU >= 10_000f)
    }

    private companion object {
        // Not 1 and not the last, so a test that only works on an edge galaxy fails here.
        const val HOME_GALAXY: Int = 6

        // What `core` caps a drift at, as a permille of the pitch. Handed in at the limit so the
        // ordering claim is tested against the worst case rather than a typical one.
        const val DRIFT_LIMIT: Int = 500
        const val TOLERANCE: Float = 0.001f

        // Twelve past the first of twenty-five.
        const val MIDDLE_OF_REGION: Int = 12
    }
}
