package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// **The one thing on a world that means nothing, and it is deliberate.** One world in a few hundred
// is memorable for no reason at all, and a player who says *"the one with the ring"* has built
// exactly the knowledge this slice is for. Claude Design, 2026-08-14.
//
// It reads no trait and no trait reads it, so it can never disagree with anything — which is what
// makes a purely decorative channel safe on a screen where every other mark is a measurement.
class GalaxyRingTest {

    @Test
    fun `a ring is drawn from its own stream so adding it moved no world`() {
        // The sub-stream rule paying for itself, and the assertion that matters most here: this
        // slice must not reroll anybody's map. Pinned trait by trait in `GalaxyGenerationTest`; what
        // this adds is the statement that the *ring* is the thing that changed and nothing else.
        val at = GalaxyCoordinate(galaxy = 3, system = 165, slot = 7)
        val world = checkNotNull(worldAt(TEST_GALAXY_SEED, at))

        assertEquals(Temperature(-21), world.traits.temperature)
        assertEquals(Gravity(960), world.traits.gravity)
        assertEquals(Pressure(773), world.traits.pressure)
    }

    @Test
    fun `about one world in two hundred has a ring`() {
        val worlds = (1..GalaxyBalance.SYSTEMS_PER_GALAXY).flatMap { system ->
            (1..GalaxyBalance.SLOTS_PER_SYSTEM).mapNotNull { slot ->
                worldAt(TEST_GALAXY_SEED, GalaxyCoordinate(1, system, slot))
            }
        }
        val ringed = worlds.count { it.hasRing }

        // Rare enough to be worth remarking on, common enough that a fortnight's play meets one.
        assertTrue(ringed in 2..12, "expected a handful of ringed worlds in a galaxy, found $ringed")
    }

    @Test
    fun `a ring is the same every time it is generated`() {
        val at = GalaxyCoordinate(galaxy = 2, system = 88, slot = 6)

        assertEquals(worldAt(TEST_GALAXY_SEED, at)?.hasRing, worldAt(TEST_GALAXY_SEED, at)?.hasRing)
    }
}
