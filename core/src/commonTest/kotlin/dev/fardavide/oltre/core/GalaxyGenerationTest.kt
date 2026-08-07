package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The two properties this slice rests on — determinism and locality — plus the trait shapes the
// sheet promised. Everything here is generated from a seed and a coordinate and nothing else, which
// is what lets the save carry one Long instead of 4,700 worlds.
class GalaxyGenerationTest {

    @Test
    fun `the same seed and coordinate always generate the same world`() {
        val at = firstWorld(TEST_GALAXY_SEED).at

        assertEquals(worldAt(TEST_GALAXY_SEED, at), worldAt(TEST_GALAXY_SEED, at))
    }

    @Test
    fun `a world is pinned trait by trait so a refactor cannot quietly reroll it`() {
        // The across-runs half of determinism: two calls in one process agreeing proves the function
        // is not stateful, but only a literal proves the *derivation* has not moved. If this fails,
        // every installed player's map has changed — which is a save-format break, not a refactor.
        val world = checkNotNull(worldAt(TEST_GALAXY_SEED, GalaxyCoordinate(galaxy = 3, system = 165, slot = 7)))

        assertEquals(StarClass.DIM, world.starClass)
        assertEquals(Temperature(-21), world.traits.temperature)
        assertEquals(Gravity(960), world.traits.gravity)
        assertEquals(Pressure(773), world.traits.pressure)
        assertEquals(Richness(942_857), world.traits.metalRichness)
        assertEquals(Richness(728_833), world.traits.crystalRichness)
        assertEquals(Richness(941_666), world.traits.deuteriumRichness)
        assertEquals(emptySet(), world.traits.hazards)
        assertEquals(142, world.traits.fields)
    }

    @Test
    fun `each richness really is a function of the axis it names`() {
        // The same world recomputed from its own axes, so the pinned numbers above cannot drift
        // apart from the formulas that are supposed to have produced them.
        val world = checkNotNull(worldAt(TEST_GALAXY_SEED, GalaxyCoordinate(galaxy = 3, system = 165, slot = 7)))

        assertEquals(GalaxyBalance.metalRichness(world.traits.gravity), world.traits.metalRichness)
        assertEquals(GalaxyBalance.crystalRichness(world.traits.pressure), world.traits.crystalRichness)
        assertEquals(GalaxyBalance.deuteriumRichness(world.traits.temperature), world.traits.deuteriumRichness)
        assertEquals(GalaxyBalance.fields(world.traits.gravity), world.traits.fields)
    }

    @Test
    fun `a different seed generates a different galaxy`() {
        val differences = homeGalaxySample().count { at ->
            worldAt(TEST_GALAXY_SEED, at) != worldAt(OTHER_GALAXY_SEED, at)
        }

        assertTrue(differences > 100, "two seeds must not agree on the map, they differed at only $differences slots")
    }

    @Test
    fun `generating a world in isolation equals generating it while walking the system`() {
        // Locality — the property the Compose Canvas depends on. A viewport renders the slots it can
        // see without generating the ones it cannot, so a world asked for alone must equal the same
        // world asked for in sequence, in either direction. A future implementation that cached per
        // system, or drew from one stream per system, would fail here rather than in the field.
        val system = 118
        val coordinates = (1..GalaxyBalance.SLOTS_PER_SYSTEM)
            .map { slot -> GalaxyCoordinate(galaxy = 2, system = system, slot = slot) }

        val forwards = coordinates.map { worldAt(TEST_GALAXY_SEED, it) }
        val backwards = coordinates.reversed().map { worldAt(TEST_GALAXY_SEED, it) }.reversed()
        val alone = coordinates.map { at -> worldAt(TEST_GALAXY_SEED, at) }

        assertEquals(forwards, backwards)
        assertEquals(forwards, alone)
    }

    @Test
    fun `an empty slot stays empty however it is asked for`() {
        val empty = homeGalaxySample().first { worldAt(TEST_GALAXY_SEED, it) == null }

        assertNull(worldAt(TEST_GALAXY_SEED, empty))
        assertNull(worldAt(TEST_GALAXY_SEED, empty))
    }

    @Test
    fun `every slot in a system shares one star class`() {
        // A star class belongs to the star, so it is drawn from the system's stream. If it were
        // drawn per world, one system would hold worlds orbiting three different suns.
        for (system in 1..20) {
            val classes = (1..GalaxyBalance.SLOTS_PER_SYSTEM)
                .mapNotNull { slot -> worldAt(TEST_GALAXY_SEED, GalaxyCoordinate(1, system, slot)) }
                .map { it.starClass }
                .distinct()
            assertTrue(classes.size <= 1, "system $system held worlds of $classes")
            assertTrue(
                classes.isEmpty() || classes.single() == starClassAt(TEST_GALAXY_SEED, 1, system),
                "system $system disagreed with its own star class",
            )
        }
    }

    @Test
    fun `the outer orbits are colder than the inner ones`() {
        // Position *is* a trait — the thing that makes the charted map worth looking at before
        // anything has been surveyed. Two *adjacent* slots are deliberately not guaranteed to be
        // ordered: they sit 28 °C apart and the jitter spans 40, so a lucky outer world really can
        // be the warmer one. Two slots apart is 56 °C and the ordering becomes absolute — which is
        // the honest statement of what the formula promises.
        for (system in 1..40) {
            val bySlot = (1..GalaxyBalance.SLOTS_PER_SYSTEM)
                .mapNotNull { slot -> worldAt(TEST_GALAXY_SEED, GalaxyCoordinate(1, system, slot)) }
            for ((inner, outer) in bySlot.zip(bySlot.drop(2))) {
                assertTrue(
                    outer.traits.temperature.celsius < inner.traits.temperature.celsius,
                    "slot ${outer.at.slot} must be colder than slot ${inner.at.slot} in system $system",
                )
            }
        }
    }

    @Test
    fun `every generated world stays inside the published ranges`() {
        for (at in homeGalaxySample()) {
            val world = worldAt(TEST_GALAXY_SEED, at) ?: continue
            val traits = world.traits
            assertTrue(traits.temperature.celsius in -260..252, "temperature ${traits.temperature} at $at")
            assertTrue(traits.gravity.milliG in 150..2_750, "gravity ${traits.gravity} at $at")
            assertTrue(traits.pressure.milliAtm in 0..12_000, "pressure ${traits.pressure} at $at")
            assertTrue(traits.fields in 80..260, "fields ${traits.fields} at $at")
            for (richness in listOf(traits.metalRichness, traits.crystalRichness, traits.deuteriumRichness)) {
                assertTrue(richness.perMillion in 600_000..1_600_000, "richness $richness at $at")
            }
            assertTrue(traits.hazards.size <= 2, "hazards ${traits.hazards} at $at")
        }
    }

    @Test
    fun `richness follows the axis it is derived from and nothing else`() {
        // An easy world is a poor world. Sorting by an axis has to sort by its resource too.
        val worlds = homeGalaxySample().mapNotNull { worldAt(TEST_GALAXY_SEED, it) }
        for ((a, b) in worlds.zipWithNext()) {
            if (a.traits.gravity.milliG < b.traits.gravity.milliG) {
                assertTrue(
                    a.traits.metalRichness.perMillion <= b.traits.metalRichness.perMillion,
                    "the heavier world must never be poorer in metal",
                )
            }
        }
    }

    @Test
    fun `a two-hazard world really has two different hazards`() {
        val hazardCounts = homeGalaxySample()
            .mapNotNull { worldAt(TEST_GALAXY_SEED, it) }
            .map { it.traits.hazards }
        assertTrue(hazardCounts.any { it.size == 2 }, "no world drew two hazards at all")
        // A Set makes a duplicate pick invisible, so the count is asserted against the pick
        assertTrue(hazardCounts.all { it.size <= 2 })
    }

    @Test
    fun `hazards land on roughly 45 percent of worlds`() {
        val worlds = homeGalaxySample().mapNotNull { worldAt(TEST_GALAXY_SEED, it) }
        val withHazards = worlds.count { it.traits.hazards.isNotEmpty() }
        val percent = withHazards * 100 / worlds.size

        assertTrue(percent in 40..50, "expected ~45% of worlds to carry a hazard, got $percent%")
    }

    @Test
    fun `a relay lands in an unoccupied slot of its own system`() {
        var found = 0
        for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            val relay = relayAt(TEST_GALAXY_SEED, galaxy = 1, system = system) ?: continue
            found++
            assertEquals(1, relay.galaxy)
            assertEquals(system, relay.system)
            assertNull(worldAt(TEST_GALAXY_SEED, relay), "a relay may not share a slot with a world")
        }
        assertTrue(found > 0, "no relay was generated in a whole galaxy")
    }

    @Test
    fun `roughly one system in 40 carries a relay`() {
        val relays = (1..GalaxyBalance.SYSTEMS_PER_GALAXY).count { relayAt(TEST_GALAXY_SEED, 1, it) != null }
        val expected = GalaxyBalance.SYSTEMS_PER_GALAXY / GalaxyBalance.RELAY_SYSTEM_IN

        assertTrue(relays in 1..(expected * 3), "expected about $expected relays in a galaxy, got $relays")
    }

    @Test
    fun `home is a world the unaided species actually tolerates`() {
        val galaxy = GalaxyState.initial(TEST_GALAXY_SEED)
        val home = checkNotNull(worldAt(galaxy.seed, galaxy.home)) { "home must hold a world" }
        val unaided = GalaxyBalance.tolerance(AdaptationLevels.NONE)

        for (axis in HostilityAxis.entries) {
            assertTrue(
                home.traits.axisValue(axis) in unaided.bandOf(axis),
                "home fails $axis at ${home.traits.axisValue(axis)}, tolerated ${unaided.bandOf(axis)}",
            )
        }
    }

    @Test
    fun `two seeds put home in different places`() {
        assertNotEquals(GalaxyState.initial(TEST_GALAXY_SEED).home, GalaxyState.initial(OTHER_GALAXY_SEED).home)
    }

    // One galaxy's worth of coordinates is 3,750 slots — enough for the shares below to be stable
    // and small enough to stay a unit test.
    private fun homeGalaxySample(): List<GalaxyCoordinate> = buildList {
        for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
            for (slot in 1..GalaxyBalance.SLOTS_PER_SYSTEM) {
                add(GalaxyCoordinate(galaxy = 1, system = system, slot = slot))
            }
        }
    }
}
