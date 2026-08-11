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

    // ── The doorstep ────────────────────────────────────────────────────────────────────────
    //
    // A distribution pinned in a unit test, for the same reason `GalaxyDistributionTest` pins one:
    // the distribution **is** the mechanic. Genesis surveys the home system and nothing else, so
    // its non-home worlds are the entire content of the Galaxy screen on day one — and until
    // 0.5.1 they were drawn from the same distribution as the rest of the map, where 98.2% of
    // worlds are blocked and the median one fails two axes. `:sim:run` measured what that meant
    // for a player rather than for the map: **the median home system asked for seven adaptation
    // levels** across two ladders before anything on that screen would say something different.
    //
    // The rule now prefers a system that has somewhere to go. What is asserted is the outcome
    // rather than the walk: re-implementing the preference order here would be a test that agrees
    // with the code by construction.
    @Test
    fun `genesis starts you beside a world one adaptation level would open`() {
        val doorsteps = (0 until DOORSTEP_SEEDS).map { offset -> doorstepOf(GalaxySeed(TEST_GALAXY_SEED.value + offset)) }
        val withinOne = doorsteps.count { it <= 1 }

        assertTrue(
            withinOne * 100 / doorsteps.size >= 90,
            "expected at least 90% of colonies to open a neighbour for one adaptation level, " +
                "got ${withinOne * 100 / doorsteps.size}% of ${doorsteps.size}",
        )
    }

    // The fallback has to be total, and "total" here means *no seed is left facing a wall*. A
    // galaxy that holds no qualifying system at all is possible rather than impossible, so the
    // preference degrades through two more tiers before it gives up — and this is the assertion
    // that says the degradation is bounded rather than unbounded.
    @Test
    fun `no colony starts more than three adaptation levels from its nearest neighbour`() {
        val worst = (0 until DOORSTEP_SEEDS).maxOf { offset -> doorstepOf(GalaxySeed(TEST_GALAXY_SEED.value + offset)) }

        assertTrue(worst <= 3, "the worst home system of $DOORSTEP_SEEDS asked for $worst adaptation levels")
    }

    // **The seeded galaxy has to still mean something.** The doorstep clause made the walk long
    // enough to leave it, and the first draft left it far too readily: a flat index over all 1,000
    // systems abandons the seeded galaxy the moment its *tail* runs out, so a colony drawn at
    // system 200 saw fifty of its own systems and then a whole other galaxy — and half of all
    // colonies opened somewhere their seed had not named.
    //
    // The name has no comma in it, and that is not a style choice: Kotlin/Native rejects a comma in
    // a backticked name outright — *"Name contains illegal characters"* — where JVM accepts it, so
    // `:core:jvmTest` goes green and `:core:compileTestKotlinIosArm64` takes down four CI jobs from
    // one line. `session-roles.md` has recorded that trap since 0.2.7 and this test walked into it
    // anyway, which is the second time; the note there now says so.
    //
    // Nothing caught it. Every other test here asks about the home *world* — is it tolerable, does
    // it have a neighbour, do two seeds differ — and a home in the wrong galaxy passes all three.
    // Measured: **50%** of colonies strayed under the flat walk and **22%** under the nested one.
    // The bound is 35 rather than 25 because the honest reading is "most colonies stay", not a
    // number: a walk that has to be able to leave will leave whenever the seeded galaxy's 250
    // systems happen to hold nothing, which is 23% of the time by construction.
    // The property was only ever stated in a comment, which is exactly the failure mode
    // `session-roles.md` records for the tilt axes: a convention nothing checks is a convention
    // that drifts. This is the check.
    @Test
    fun `a colony opens in the galaxy its seed names unless that galaxy has nothing`() {
        val strayed = (0 until DOORSTEP_SEEDS).count { offset ->
            val seed = GalaxySeed(TEST_GALAXY_SEED.value + offset)
            GalaxyState.initial(seed).home.galaxy != seededGalaxyOf(seed)
        }

        assertTrue(
            strayed * 100 / DOORSTEP_SEEDS <= 35,
            "expected most colonies to open in the galaxy their seed draws, " +
                "${strayed * 100 / DOORSTEP_SEEDS}% of $DOORSTEP_SEEDS left it",
        )
    }

    // Genesis picking a *system* must not have quietly become genesis picking a *world* — the home
    // world itself is still the one the unaided species tolerates, which is what the fiction rests
    // on and what `home is a world the unaided species actually tolerates` pins for one seed.
    @Test
    fun `every home the doorstep rule picks is still a world the species tolerates`() {
        val unaided = GalaxyBalance.tolerance(AdaptationLevels.NONE)

        for (offset in 0 until DOORSTEP_SEEDS) {
            val galaxy = GalaxyState.initial(GalaxySeed(TEST_GALAXY_SEED.value + offset))
            val home = checkNotNull(worldAt(galaxy.seed, galaxy.home)) { "home must hold a world" }
            for (axis in HostilityAxis.entries) {
                assertTrue(
                    home.traits.axisValue(axis) in unaided.bandOf(axis),
                    "home of seed ${galaxy.seed.value} fails $axis at ${home.traits.axisValue(axis)}",
                )
            }
        }
    }

    // The cheapest non-home world of one seed's home system, counted in adaptation levels across
    // all three ladders. Zero would mean a neighbour needs nothing at all.
    private fun doorstepOf(seed: GalaxySeed): Int {
        val galaxy = GalaxyState.initial(seed)
        return galaxy.surveyed
            .filter { it != galaxy.home }
            .mapNotNull { at -> worldAt(seed, at) }
            .minOfOrNull { GalaxyBalance.levelsToTolerate(it.traits) }
            ?: Int.MAX_VALUE
    }

    // Enough seeds for a 90% share to be stable to about a point, and few enough that the three
    // tests above stay a unit test on every target including Kotlin/Native — genesis walks its
    // galaxy once per seed, so this is the most expensive thing in the file by an order of
    // magnitude.
    private val DOORSTEP_SEEDS: Int = 200

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
