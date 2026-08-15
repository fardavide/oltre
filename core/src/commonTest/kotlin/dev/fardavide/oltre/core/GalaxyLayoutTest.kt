package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// The drawn map's two generated quantities. Everything else about where a star sits is arithmetic —
// a band is twenty-five systems, path order is index order, and a system's place along its band
// falls out of its index — so drift and size wobble are the only things the serpentine has to ask
// the seed for. They are asked for because a band of twenty-five stars on a straight line at an even
// pitch reads as a table, and the map's whole claim is that it is a sky you can point at.
//
// Two bounds carry the design and both are asserted here rather than trusted:
//
// 1. **Drift is capped at half a pitch**, so a star can never wander into a neighbour's lane. Path
//    order is index order and the map exists to make a distance on the drawing a distance in the
//    game; a star drawn past its neighbour would make the drawing disagree with the thing it draws.
// 2. **Size stays inside its class band**, so a big dim star is never drawn the size of a standard
//    one. Size *is* class on this map, and the wobble is there to stop two standards being clones.
class GalaxyLayoutTest {

    @Test
    fun `a system is laid out the same way every time it is generated`() {
        val at = SystemAddress(galaxy = 3, system = 165)

        assertEquals(
            layoutAt(TEST_GALAXY_SEED, at.galaxy, at.system),
            layoutAt(TEST_GALAXY_SEED, at.galaxy, at.system),
        )
    }

    @Test
    fun `drift never leaves the half pitch that keeps two stars in their order`() {
        // The bound the whole cap argument rests on. Checked across the entire universe rather than
        // on a sample, because one star out of 1,000 in the wrong lane is exactly the kind of defect
        // a spot check misses and a player finds.
        for (galaxy in 1..GalaxyBalance.GALAXIES) {
            for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
                val drift = layoutAt(TEST_GALAXY_SEED, galaxy, system).driftPermille

                assertTrue(drift in -500..500, "$galaxy:$system drifted $drift permille of a pitch")
            }
        }
    }

    @Test
    fun `size wobble never carries a star out of its own class band`() {
        // `0.82 + u * 0.36` from the design sheet, in thousandths. The ceiling is what stops a wobble
        // becoming a promotion: a star drawn larger than its class allows would be the map's one
        // measurement telling a player something untrue about the star it names.
        for (galaxy in 1..GalaxyBalance.GALAXIES) {
            for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
                val size = layoutAt(TEST_GALAXY_SEED, galaxy, system).sizePermille

                assertTrue(size in 870..1130, "$galaxy:$system was drawn at $size permille of its class radius")
            }
        }
    }

    @Test
    fun `two neighbouring systems are not drifted alike`() {
        // A constant function would satisfy every bound above and draw a perfectly ruled line, which
        // is the thing this axis exists to avoid. Neighbours rather than distant systems, because
        // adjacent stars are the pair a player's eye actually compares.
        val here = layoutAt(TEST_GALAXY_SEED, galaxy = 1, system = 100)
        val next = layoutAt(TEST_GALAXY_SEED, galaxy = 1, system = 101)

        assertNotEquals(here.driftPermille, next.driftPermille)
    }

    @Test
    fun `two galaxies lay the same system index out differently`() {
        // Four galaxies are drawn into the same frame at 1/5 scale on the universe view. If the
        // layout ignored the galaxy they would be four copies of one texture.
        assertNotEquals(
            layoutAt(TEST_GALAXY_SEED, galaxy = 1, system = 118).driftPermille,
            layoutAt(TEST_GALAXY_SEED, galaxy = 2, system = 118).driftPermille,
        )
    }

    @Test
    fun `two seeds lay the same coordinate out differently`() {
        assertNotEquals(
            layoutAt(TEST_GALAXY_SEED, galaxy = 2, system = 118).driftPermille,
            layoutAt(OTHER_GALAXY_SEED, galaxy = 2, system = 118).driftPermille,
        )
    }

    @Test
    fun `adding the layout axis moved no other generated quantity`() {
        // **A regression pin rather than a specification.** Every value below was read off a run of
        // the generator *before* the LAYOUT tag existed, not predicted from the arithmetic — the
        // point is not that these are the right numbers, it is that they are the numbers every
        // installed player is already looking at. The enum at the top of `GalaxyGeneration.kt` says
        // adding a tag is free and reordering one is a save-format change; this is what "free" means
        // when it is checked rather than asserted.
        val world = checkNotNull(worldAt(TEST_GALAXY_SEED, GalaxyCoordinate(galaxy = 1, system = 1, slot = 7)))

        assertEquals(StarClass.STANDARD, world.starClass)
        assertEquals(Temperature(26), world.traits.temperature)
        assertEquals(Gravity(310), world.traits.gravity)
        assertEquals(Pressure(0), world.traits.pressure)
        assertEquals(setOf(Hazard.RADIATION_BELT), world.traits.hazards)
        assertEquals(100, world.traits.fields)

        assertEquals(StarClass.STANDARD, starClassAt(TEST_GALAXY_SEED, galaxy = 1, system = 202))
        assertEquals(StarClass.BRIGHT, starClassAt(TEST_GALAXY_SEED, galaxy = 2, system = 88))
        assertEquals(StarClass.BRIGHT, starClassAt(TEST_GALAXY_SEED, galaxy = 4, system = 250))
    }

    @Test
    fun `a galaxy's drifts spread across the lane instead of clustering`() {
        // Deterministic and bounded and *different for neighbours* still admits a generator that
        // only ever answers three values, which would draw three ruled lines instead of one. 250
        // systems drawing from 1,001 offsets collide about thirty times by birthday arithmetic, so
        // anything near 220 distinct is healthy and 200 is a floor a degenerate draw cannot reach.
        val drifts = (1..GalaxyBalance.SYSTEMS_PER_GALAXY)
            .map { system -> layoutAt(TEST_GALAXY_SEED, galaxy = 1, system = system).driftPermille }

        assertTrue(drifts.distinct().size >= 200, "250 systems drifted only ${drifts.distinct().size} different ways")
    }

    // The third quantity the drawing asks for, and the only one that is a *choice* rather than a
    // displacement: which of the two halo hues a bright star wears. It is a draw rather than a
    // constant because Claude Design asked for about a third of the brights to lean the crystal
    // blue — variety inside a class, mixed from a resource hue, never a status colour.
    @Test
    fun `halo is a draw in its own thousandths`() {
        for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            val halo = layoutAt(TEST_GALAXY_SEED, galaxy = 1, system = system).haloPermille
            assertTrue(halo in 0..999, "system $system drew $halo")
        }
    }

    @Test
    fun `halo does not follow drift`() {
        val drifts = (1..GalaxyBalance.SYSTEMS_PER_GALAXY).map { layoutAt(TEST_GALAXY_SEED, 1, it).driftPermille }
        val halos = (1..GalaxyBalance.SYSTEMS_PER_GALAXY).map { layoutAt(TEST_GALAXY_SEED, 1, it).haloPermille }
        val agree = drifts.indices.count { (drifts[it] > 0) == (halos[it] >= 500) }
        assertTrue(agree in 90..160, "drift and halo agreed $agree times out of 250")
    }

    @Test
    fun `halo is the same for one seed on every call`() {
        assertEquals(layoutAt(TEST_GALAXY_SEED, 2, 88).haloPermille, layoutAt(TEST_GALAXY_SEED, 2, 88).haloPermille)
    }
}
