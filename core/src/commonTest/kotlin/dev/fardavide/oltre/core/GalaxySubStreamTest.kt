package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// **This test is the guard.** Every axis is drawn from its own named stream, hashed from the
// world's seed and an axis tag, so that adding an axis in a later slice cannot shift the axes that
// already exist. Draw a new trait off a shared sequential stream instead — the obvious thing to do,
// and the thing a generator normally does — and every future addition silently rerolls every
// installed player's galaxy. There is no other test in the repo that would notice.
class GalaxySubStreamTest {

    @Test
    fun `every axis draws from a tag of its own`() {
        val tags = GenerationAxis.entries.map { it.tag }

        assertEquals(tags.size, tags.distinct().size, "two axes share a tag, so they share a stream")
    }

    @Test
    fun `no two axes of one world produce the same stream`() {
        val world = worldSeed(TEST_GALAXY_SEED, GalaxyCoordinate(galaxy = 2, system = 118, slot = 7))
        val streams = GenerationAxis.entries.map { streamOf(world, it) }

        assertEquals(streams.size, streams.distinct().size, "two axes collided on one world")
    }

    @Test
    fun `an axis stream depends on its own tag and the world alone`() {
        // The property in one line: a stream is a pure function of (world seed, tag). Nothing about
        // which other axes were drawn, or in what order, can reach it.
        val world = worldSeed(TEST_GALAXY_SEED, GalaxyCoordinate(galaxy = 2, system = 118, slot = 7))

        for (axis in GenerationAxis.entries) {
            assertEquals(streamOf(world, axis), streamOf(world, axis), "$axis is not a pure function")
        }
    }

    @Test
    fun `the per-axis draws of one world are pinned`() {
        // Pinned rather than derived, because the whole point is to notice a change. If these move,
        // the map every installed player is looking at has moved with them, and that is a
        // save-format decision rather than a refactor.
        val world = worldSeed(TEST_GALAXY_SEED, GalaxyCoordinate(galaxy = 2, system = 118, slot = 7))

        assertEquals(45, percentFrom(streamOf(world, GenerationAxis.OCCUPANCY)))
        assertEquals(Uniform(7_179), uniformFrom(streamOf(world, GenerationAxis.GRAVITY)))
        assertEquals(Uniform(4_769), uniformFrom(streamOf(world, GenerationAxis.PRESSURE)))
        assertEquals(27, percentFrom(streamOf(world, GenerationAxis.HAZARDS)))
    }

    @Test
    fun `the system seed is shared by every slot and differs between systems`() {
        val system = systemSeed(TEST_GALAXY_SEED, galaxy = 2, system = 118)

        assertEquals(system, systemSeed(TEST_GALAXY_SEED, galaxy = 2, system = 118))
        assertNotEquals(system, systemSeed(TEST_GALAXY_SEED, galaxy = 2, system = 119))
        assertNotEquals(system, systemSeed(TEST_GALAXY_SEED, galaxy = 3, system = 118))
    }

    @Test
    fun `coordinates that differ in one component get different world seeds`() {
        // A weak hash would let 2:118:7 and 2:119:6 collide, which would make two slots of the map
        // permanently identical twins.
        val seeds = buildList {
            for (galaxy in 1..GalaxyBalance.GALAXIES) {
                for (system in 1..40) {
                    for (slot in 1..GalaxyBalance.SLOTS_PER_SYSTEM) {
                        add(worldSeed(TEST_GALAXY_SEED, GalaxyCoordinate(galaxy, system, slot)))
                    }
                }
            }
        }

        assertEquals(seeds.size, seeds.distinct().size, "two coordinates hashed to the same world seed")
    }

    @Test
    fun `two seeds disagree on every stream of the same coordinate`() {
        val at = GalaxyCoordinate(galaxy = 2, system = 118, slot = 7)
        val mine = worldSeed(TEST_GALAXY_SEED, at)
        val theirs = worldSeed(OTHER_GALAXY_SEED, at)

        assertNotEquals(mine, theirs)
        for (axis in GenerationAxis.entries) {
            assertNotEquals(streamOf(mine, axis), streamOf(theirs, axis), "$axis agreed across two seeds")
        }
    }

    @Test
    fun `successive draws inside one axis do not leak into another`() {
        // Hazards need two picks. They come from indexed draws off the HAZARDS stream, so a world
        // with two hazards must not have different gravity from one with none.
        val world = worldSeed(TEST_GALAXY_SEED, GalaxyCoordinate(galaxy = 2, system = 118, slot = 7))
        val gravityBefore = uniformFrom(streamOf(world, GenerationAxis.GRAVITY))

        repeat(5) { index -> draw(streamOf(world, GenerationAxis.HAZARDS), index) }

        assertEquals(gravityBefore, uniformFrom(streamOf(world, GenerationAxis.GRAVITY)))
    }

    @Test
    fun `the occupancy stream honours the published share per slot band`() {
        // Occupancy is an axis like any other, so this is also a check that its stream is uniform
        // enough to hit the sheet's 45 / 20 split rather than merely being deterministic.
        for (slot in listOf(1, 5, 8, 12)) {
            val held = (1..GalaxyBalance.SYSTEMS_PER_GALAXY).count { system ->
                worldAt(TEST_GALAXY_SEED, GalaxyCoordinate(galaxy = 1, system = system, slot = slot)) != null
            }
            val percent = held * 100 / GalaxyBalance.SYSTEMS_PER_GALAXY
            val expected = GalaxyBalance.occupancyPercent(slot)
            assertTrue(
                percent in (expected - 8)..(expected + 8),
                "slot $slot held a world $percent% of the time, expected about $expected%",
            )
        }
    }
}
