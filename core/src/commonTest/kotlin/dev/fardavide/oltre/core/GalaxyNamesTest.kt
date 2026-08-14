package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Names cost the save nothing and the map everything. They are regenerated from the seed exactly
// like a trait — `galaxy-sheet.md` §7 is untouched — and they are what turns "2 pages before" into
// somewhere a player can look up, say out loud and go back to.
//
// Generated only: the player cannot rename a world. Davide's call, 2026-08-14, deferred to
// colonisation on the grounds that a name given to a world you cannot own is a name for a rock you
// visit.
class GalaxyNamesTest {

    @Test
    fun `a name is the same every time it is generated`() {
        val at = GalaxyCoordinate(galaxy = 3, system = 165, slot = 7)

        assertEquals(worldNameAt(TEST_GALAXY_SEED, at), worldNameAt(TEST_GALAXY_SEED, at))
        assertEquals(systemNameAt(TEST_GALAXY_SEED, 3, 165), systemNameAt(TEST_GALAXY_SEED, 3, 165))
    }

    @Test
    fun `names are pinned so a refactor cannot quietly rename the galaxy`() {
        // The same treatment `GalaxyGenerationTest` gives a world's traits. A name is part of what a
        // player remembers about a place, so moving one is a change to their map rather than a
        // refactor — and unlike a trait, nothing else in the suite would notice.
        assertEquals("Elyuvell", systemNameAt(TEST_GALAXY_SEED, galaxy = 1, system = 1))
        assertEquals("Elyuvell VII", worldNameAt(TEST_GALAXY_SEED, GalaxyCoordinate(1, 1, 7)))
        assertEquals("Bramuvell Reach", regionNameAt(TEST_GALAXY_SEED, galaxy = 1, region = 1))
        // A Deep and a Blaze from the same galaxy, so the pin covers all three palettes and would
        // catch a syllable table being edited as readily as the derivation moving.
        assertEquals("Calianova", systemNameAt(TEST_GALAXY_SEED, galaxy = 1, system = 202))
        assertEquals("Kragith", systemNameAt(TEST_GALAXY_SEED, galaxy = 1, system = 51))
    }

    @Test
    fun `two systems of a galaxy never share a name`() {
        // **Structural rather than hoped for, and galaxy-wide rather than per region.** The first
        // cut was unique inside a region only, and it put `Karak` in three different Blaze regions
        // of one galaxy — which is fine for flavour and fatal for the thing names are *for*: a
        // search that returns three places is a search that has not found anything.
        //
        // How: a system's index among the systems sharing its temperament is mapped through a
        // seeded bijection of the 8 x 5 x 5 palette space. A temperament covers at most four
        // regions — 100 systems into 200 slots — so the map is injective, and two temperaments
        // never share a syllable, so a collision cannot cross one either.
        for (seed in listOf(TEST_GALAXY_SEED, OTHER_GALAXY_SEED)) {
            for (galaxy in 1..GalaxyBalance.GALAXIES) {
                val names = (1..GalaxyBalance.SYSTEMS_PER_GALAXY).map { systemNameAt(seed, galaxy, it) }
                val repeated = names.groupingBy { it }.eachCount().filterValues { it > 1 }

                assertEquals(emptyMap(), repeated, "galaxy $galaxy of seed ${seed.value} repeated a name")
            }
        }
    }

    @Test
    fun `a world is its system and the roman numeral of its slot`() {
        val system = systemNameAt(TEST_GALAXY_SEED, galaxy = 2, system = 88)

        assertEquals("$system I", worldNameAt(TEST_GALAXY_SEED, GalaxyCoordinate(2, 88, 1)))
        assertEquals("$system IV", worldNameAt(TEST_GALAXY_SEED, GalaxyCoordinate(2, 88, 4)))
        assertEquals("$system IX", worldNameAt(TEST_GALAXY_SEED, GalaxyCoordinate(2, 88, 9)))
        assertEquals("$system XV", worldNameAt(TEST_GALAXY_SEED, GalaxyCoordinate(2, 88, 15)))
    }

    @Test
    fun `a region's names sound like the region`() {
        // The palette is the whole point: a Deep's names are long and soft and a Burning's are short
        // and hard, so a name places you before the coordinate does. Asserted through the palette
        // rather than through a vowel count, because the palette is the thing that decides it.
        for (galaxy in 1..GalaxyBalance.GALAXIES) {
            for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
                val temperament = temperamentAt(TEST_GALAXY_SEED, galaxy, system)
                val name = systemNameAt(TEST_GALAXY_SEED, galaxy, system)

                assertTrue(
                    GalaxyNames.headsOf(temperament).any { name.startsWith(it) },
                    "$name is in a $temperament region but reads like somewhere else",
                )
            }
        }
    }

    @Test
    fun `a region is named for what it is`() {
        // "the Calanova Deep" — the noun is a function of the temperament, so the label cannot lie
        // about the place the way a purely decorative one would.
        for (region in 1..GalaxyBalance.REGIONS_PER_GALAXY) {
            val name = regionNameAt(TEST_GALAXY_SEED, galaxy = 1, region = region)
            val expected = GalaxyNames.nounOf(temperamentOf(TEST_GALAXY_SEED, 1, region))

            assertTrue(name.endsWith(expected), "$name does not say it is a $expected")
        }
    }

    @Test
    fun `a galaxy's ten regions never share a name`() {
        for (seed in listOf(TEST_GALAXY_SEED, OTHER_GALAXY_SEED)) {
            for (galaxy in 1..GalaxyBalance.GALAXIES) {
                val names = (1..GalaxyBalance.REGIONS_PER_GALAXY).map { regionNameAt(seed, galaxy, it) }

                assertEquals(names.size, names.distinct().size, "galaxy $galaxy repeated a region name: $names")
            }
        }
    }
}
