package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The galaxy gains geography, which it has never had: until 0.10 `starClassAt` hashed every system
// independently, so any two neighbourhoods of 250 systems were drawn from the same distribution and
// **nothing about any region of the map could be learned**. See `galaxy-identity-sheet.md` §1.
//
// Two properties carry the whole design and both are asserted here rather than left to good
// intentions:
//
// 1. **The temperaments are a permutation of a fixed multiset**, not ten independent draws. So the
//    galaxy-wide star-class distribution is identical for *every* seed rather than in expectation,
//    and "there is a Deep somewhere in your galaxy" is a promise that always holds.
// 2. **A region's bias is real.** A Deep really does run dim, which is what makes "in a Deep you
//    settle close in" a fact a player learns rather than a label the map wears.
class GalaxyRegionTest {

    @Test
    fun `a galaxy is cut into ten regions of twenty five systems`() {
        assertEquals(10, GalaxyBalance.REGIONS_PER_GALAXY)
        assertEquals(25, GalaxyBalance.SYSTEMS_PER_REGION)

        assertEquals(1, regionOf(1))
        assertEquals(1, regionOf(GalaxyBalance.SYSTEMS_PER_REGION))
        assertEquals(2, regionOf(GalaxyBalance.SYSTEMS_PER_REGION + 1))
        assertEquals(GalaxyBalance.REGIONS_PER_GALAXY, regionOf(GalaxyBalance.SYSTEMS_PER_GALAXY))
    }

    @Test
    fun `every galaxy holds four deep two settled and four burning regions`() {
        // The promise a permutation buys and ten independent draws could not. A galaxy that rolled
        // ten Settled regions would be a galaxy this whole slice did nothing for.
        val expected = GalaxyBalance.REGION_TEMPERAMENTS.sortedBy { it.ordinal }

        for (seed in REGION_SEEDS) {
            for (galaxy in 1..GalaxyBalance.GALAXIES) {
                val drawn = (1..GalaxyBalance.REGIONS_PER_GALAXY)
                    .map { region -> temperamentOf(seed, galaxy, region) }
                    .sortedBy { it.ordinal }

                assertEquals(expected, drawn, "galaxy $galaxy of seed ${seed.value} is not a permutation")
            }
        }
    }

    @Test
    fun `every system of a region shares its temperament`() {
        // A region is a *place*, so its character cannot change halfway across it.
        for (region in 1..GalaxyBalance.REGIONS_PER_GALAXY) {
            val first = (region - 1) * GalaxyBalance.SYSTEMS_PER_REGION + 1
            val expected = temperamentOf(TEST_GALAXY_SEED, galaxy = 1, region = region)

            for (offset in 0 until GalaxyBalance.SYSTEMS_PER_REGION) {
                assertEquals(expected, temperamentAt(TEST_GALAXY_SEED, galaxy = 1, system = first + offset))
            }
        }
    }

    @Test
    fun `two seeds do not lay their regions out the same way`() {
        val here = (1..GalaxyBalance.REGIONS_PER_GALAXY).map { temperamentOf(TEST_GALAXY_SEED, 1, it) }
        val there = (1..GalaxyBalance.REGIONS_PER_GALAXY).map { temperamentOf(OTHER_GALAXY_SEED, 1, it) }

        assertTrue(here != there, "two seeds put their regions in the same order: $here")
    }

    @Test
    fun `a deep region really runs dim and a burning one really runs bright`() {
        // The bias has to be *measurable*, because it is the only thing a player can read off the
        // charted map for free. A tendency nobody can see is the failure mode this slice exists to
        // avoid.
        val dimShare = shareOf(StarClass.DIM)
        val brightShare = shareOf(StarClass.BRIGHT)

        assertTrue(dimShare(RegionTemperament.DEEP) in 55..65, "a Deep ran ${dimShare(RegionTemperament.DEEP)}% dim")
        assertTrue(
            brightShare(RegionTemperament.BURNING) in 55..65,
            "a Burning ran ${brightShare(RegionTemperament.BURNING)}% bright",
        )
        assertTrue(
            dimShare(RegionTemperament.SETTLED) in 15..25,
            "a Settled ran ${dimShare(RegionTemperament.SETTLED)}% dim",
        )
    }

    @Test
    fun `the galaxy wide star class mix is what the multiset averages to`() {
        // 4 Deep + 2 Settled + 4 Burning pools to 32 / 36 / 32 — near enough the equal thirds every
        // §9 target was measured against that the galaxy's verdict distribution stays inside its
        // bands, which `3 / 4 / 3` did not. The pooled mix is a *consequence* of the multiset rather
        // than a number of its own, and pinning it here is what stops the multiset moving quietly.
        val classes = REGION_SEEDS.flatMap { seed ->
            (1..GalaxyBalance.GALAXIES).flatMap { galaxy ->
                (1..GalaxyBalance.SYSTEMS_PER_GALAXY).map { system -> starClassAt(seed, galaxy, system) }
            }
        }
        fun percent(starClass: StarClass) = classes.count { it == starClass } * 100 / classes.size

        assertTrue(percent(StarClass.DIM) in 30..34, "dim was ${percent(StarClass.DIM)}%")
        assertTrue(percent(StarClass.STANDARD) in 34..38, "standard was ${percent(StarClass.STANDARD)}%")
        assertTrue(percent(StarClass.BRIGHT) in 30..34, "bright was ${percent(StarClass.BRIGHT)}%")
    }

    private fun shareOf(starClass: StarClass): (RegionTemperament) -> Int = { temperament ->
        var matching = 0
        var total = 0
        for (seed in REGION_SEEDS) {
            for (galaxy in 1..GalaxyBalance.GALAXIES) {
                for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
                    if (temperamentAt(seed, galaxy, system) != temperament) continue
                    total++
                    if (starClassAt(seed, galaxy, system) == starClass) matching++
                }
            }
        }
        matching * 100 / total
    }
}

// Enough galaxies that a 60% bias cannot pass on luck: four galaxies a seed, three seeds, 3,000
// systems.
private val REGION_SEEDS: List<GalaxySeed> =
    listOf(TEST_GALAXY_SEED, OTHER_GALAXY_SEED, GalaxySeed(20_260_814))
