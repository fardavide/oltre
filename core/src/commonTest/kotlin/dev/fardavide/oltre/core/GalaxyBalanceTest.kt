package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// The galaxy decision sheet's section 8 published a table of constants and they are the design, so
// they are asserted value by value rather than left to whatever the arithmetic happens to produce.
// This is the treatment `ResearchBalanceTest` gives the research tables, for the same reason: if
// one of these numbers has to change, the sheet changed, and that is Davide's call than a refactor.
class GalaxyBalanceTest {

    @Test
    fun `the coordinate space is 4 galaxies of 250 systems of 15 slots`() {
        assertEquals(4, GalaxyBalance.GALAXIES)
        assertEquals(250, GalaxyBalance.SYSTEMS_PER_GALAXY)
        assertEquals(15, GalaxyBalance.SLOTS_PER_SYSTEM)
        assertEquals(15_000, GalaxyBalance.TOTAL_SLOTS)
    }

    @Test
    fun `the inner slots hold a world more often than the edges`() {
        // 45% for slots 4-10 and 20% for the rest, which averages 4_75 worlds per system and makes
        // the mockup's "4 / 15 occupied" typical rather than lucky.
        for (slot in 1..3) assertEquals(20, GalaxyBalance.occupancyPercent(slot), "slot $slot")
        for (slot in 4..10) assertEquals(45, GalaxyBalance.occupancyPercent(slot), "slot $slot")
        for (slot in 11..15) assertEquals(20, GalaxyBalance.occupancyPercent(slot), "slot $slot")
    }

    @Test
    fun `a system averages 4_75 worlds and the galaxy holds about 4700`() {
        val perSystem = (1..GalaxyBalance.SLOTS_PER_SYSTEM).sumOf { GalaxyBalance.occupancyPercent(it) }
        assertEquals(475, perSystem, "hundredths of a world per system")
        val total = perSystem * GalaxyBalance.SYSTEMS_PER_GALAXY * GalaxyBalance.GALAXIES / 100
        assertEquals(4_750, total, "expected worlds galaxy-wide")
    }

    @Test
    fun `star class offsets the orbit by 40 degrees either way`() {
        assertEquals(-40, GalaxyBalance.starOffset(StarClass.DIM))
        assertEquals(0, GalaxyBalance.starOffset(StarClass.STANDARD))
        assertEquals(40, GalaxyBalance.starOffset(StarClass.BRIGHT))
    }

    @Test
    fun `temperature falls 28 degrees an orbit from a 220 degree centre`() {
        // The sheet's formula: 220 - 28 x slot + starOffset + jitter. Position *is* a trait, which
        // is what makes the charted map readable before anything has been surveyed.
        assertEquals(
            Temperature(192),
            GalaxyBalance.temperature(slot = 1, starClass = StarClass.STANDARD, jitter = 0),
        )
        assertEquals(
            Temperature(-200),
            GalaxyBalance.temperature(slot = 15, starClass = StarClass.STANDARD, jitter = 0),
        )
        assertEquals(
            Temperature(-4),
            GalaxyBalance.temperature(slot = 8, starClass = StarClass.STANDARD, jitter = 0),
        )
        // and the star class and the jitter both move it
        assertEquals(
            Temperature(-44),
            GalaxyBalance.temperature(slot = 8, starClass = StarClass.DIM, jitter = 0),
        )
        assertEquals(
            Temperature(56),
            GalaxyBalance.temperature(slot = 8, starClass = StarClass.BRIGHT, jitter = 20),
        )
    }

    @Test
    fun `the published temperature range spans the hottest and coldest orbits`() {
        val extremes = buildList {
            for (slot in 1..GalaxyBalance.SLOTS_PER_SYSTEM) {
                for (starClass in StarClass.entries) {
                    for (jitter in listOf(-GalaxyBalance.TEMPERATURE_JITTER, GalaxyBalance.TEMPERATURE_JITTER)) {
                        add(GalaxyBalance.temperature(slot, starClass, jitter).celsius)
                    }
                }
            }
        }
        assertEquals(-260, extremes.min(), "the coldest orbit of the dimmest star")
        assertEquals(252, extremes.max(), "the hottest orbit of the brightest star")
    }

    @Test
    fun `gravity is quadratic so the median world is lighter than home`() {
        // 0_15 + 2_6 x u squared. Quadratic rather than uniform is what makes heavy worlds rare
        // and therefore worth adapting to.
        assertEquals(Gravity(150), GalaxyBalance.gravity(Uniform(0)))
        assertEquals(Gravity(2_750), GalaxyBalance.gravity(Uniform.MAX))
        assertEquals(Gravity(800), GalaxyBalance.gravity(Uniform.ofPercent(50)))
        assertTrue(
            GalaxyBalance.gravity(Uniform.ofPercent(50)).milliG < 1_000,
            "the median world must be lighter than home",
        )
    }

    @Test
    fun `pressure is cubic so a thick atmosphere is rarer still`() {
        // 12 x u cubed. Cubic rather than quadratic because crystal is the resource the sheet
        // wanted hardest to reach.
        assertEquals(Pressure(0), GalaxyBalance.pressure(Uniform(0)))
        assertEquals(Pressure(12_000), GalaxyBalance.pressure(Uniform.MAX))
        assertEquals(Pressure(1_500), GalaxyBalance.pressure(Uniform.ofPercent(50)))
    }

    @Test
    fun `fields scale with gravity so heavy worlds are roomy`() {
        // The one extra thing an axis does, because it has an obvious home and no other: gravity is
        // the cost and the reward twice over — rich in metal, roomy, hardest to stand on.
        assertEquals(80, GalaxyBalance.fields(Gravity(0)))
        assertEquals(260, GalaxyBalance.fields(Gravity(2_750)))
        assertEquals(GalaxyBalance.fields(Gravity(150)), GalaxyBalance.fields(Gravity(150)))
        assertTrue(GalaxyBalance.fields(Gravity(2_000)) > GalaxyBalance.fields(Gravity(1_000)))
    }

    @Test
    fun `richness is derived from the hostility axes rather than rolled`() {
        // The pillar in one function: an easy world is a poor world, because the same number that
        // makes a world hostile is the number that makes it rich.
        assertEquals(Richness(1_100_000), GalaxyBalance.metalRichness(Gravity(1_400)))
        assertEquals(Richness(1_100_000), GalaxyBalance.crystalRichness(Pressure(3_000)))
        assertEquals(Richness(1_100_000), GalaxyBalance.deuteriumRichness(Temperature(-40)))
        // 1_0 is "as good as home", and home sits at the mild end of every axis
        assertEquals(Richness(1_000_000), GalaxyBalance.metalRichness(Gravity(1_120)))
        assertEquals(Richness(1_000_000), GalaxyBalance.deuteriumRichness(Temperature(-28)))
    }

    @Test
    fun `richness clamps to the published band at both ends`() {
        // Pressure reaches 12 atm and temperature reaches -260, both of which run the raw formula
        // well past the band; without the clamp a single extreme world would outscore everything.
        assertEquals(Richness(1_600_000), GalaxyBalance.crystalRichness(Pressure(12_000)))
        assertEquals(Richness(1_600_000), GalaxyBalance.deuteriumRichness(Temperature(-260)))
        assertEquals(Richness(600_000), GalaxyBalance.deuteriumRichness(Temperature(252)))
        assertEquals(Richness(600_000), GalaxyBalance.crystalRichness(Pressure(0)))
    }

    @Test
    fun `yield weights each resource by its share of the reference colony's priced output`() {
        // 51 / 33 / 16, from the 698 / 224 / 72 per hour at 1 : 2 : 3 in the balance log — so the
        // score means "worth it to this economy" rather than "big numbers".
        assertEquals(51, GalaxyBalance.METAL_WEIGHT_PERCENT)
        assertEquals(33, GalaxyBalance.CRYSTAL_WEIGHT_PERCENT)
        assertEquals(16, GalaxyBalance.DEUTERIUM_WEIGHT_PERCENT)
        assertEquals(
            100,
            GalaxyBalance.METAL_WEIGHT_PERCENT +
                GalaxyBalance.CRYSTAL_WEIGHT_PERCENT +
                GalaxyBalance.DEUTERIUM_WEIGHT_PERCENT,
        )
    }

    @Test
    fun `a world as good as home in every resource scores exactly 1`() {
        val asGoodAsHome = traits(metal = 1_000_000, crystal = 1_000_000, deuterium = 1_000_000, hazards = emptySet())
        assertEquals(YieldScore(1_000_000), GalaxyBalance.yieldScore(asGoodAsHome))
    }

    @Test
    fun `each hazard takes 5 percent off the yield`() {
        val clean = traits(metal = 1_000_000, crystal = 1_000_000, deuterium = 1_000_000, hazards = emptySet())
        val one = traits(1_000_000, 1_000_000, 1_000_000, setOf(Hazard.ION_STORMS))
        val two = traits(1_000_000, 1_000_000, 1_000_000, setOf(Hazard.ION_STORMS, Hazard.THIN_CRUST))
        assertEquals(YieldScore(1_000_000), GalaxyBalance.yieldScore(clean))
        assertEquals(YieldScore(950_000), GalaxyBalance.yieldScore(one))
        assertEquals(YieldScore(900_000), GalaxyBalance.yieldScore(two))
    }

    @Test
    fun `the worth-it threshold is 0_92`() {
        // Deliberately above the score of the median world that passes every band, so that the
        // median settleable world is Barren. Notion — surveying should frequently return not worth it.
        // Raised from 0.90 at 0.0.15: it is the one lever that thins the settleable share without
        // changing which worlds pass, so the three axes keep their comparable pass rates.
        assertEquals(YieldScore(920_000), GalaxyBalance.WORTH_IT_THRESHOLD)
    }

    @Test
    fun `tolerance at adaptation level 0 is what the species handles unaided`() {
        // Gravity and pressure were tightened at 0.0.15 so all three axes gate a comparable share
        // — 25.9 / 25.3 / 25.0 per cent — which is what makes three adaptation ladders three
        // decisions rather than one gate and two ornaments. See `balance-log.md` round 5.
        val unaided = GalaxyBalance.tolerance(AdaptationLevels.NONE)
        assertEquals(ToleranceBand(-30, 45), unaided.temperature)
        assertEquals(ToleranceBand(650, 1_400), unaided.gravity)
        assertEquals(ToleranceBand(500, 2_600), unaided.pressure)
    }

    @Test
    fun `each adaptation level widens its own axis and no other`() {
        // Three ladders rather than one, so which one you push first is a real choice that makes
        // two empires differ. A level of Thermal must not move the gravity band.
        val thermal = GalaxyBalance.tolerance(AdaptationLevels(thermal = 1, gravitic = 0, atmospheric = 0))
        assertEquals(ToleranceBand(-44, 59), thermal.temperature)
        assertEquals(ToleranceBand(650, 1_400), thermal.gravity)
        assertEquals(ToleranceBand(500, 2_600), thermal.pressure)

        val gravitic = GalaxyBalance.tolerance(AdaptationLevels(thermal = 0, gravitic = 1, atmospheric = 0))
        assertEquals(ToleranceBand(-30, 45), gravitic.temperature)
        assertEquals(ToleranceBand(600, 1_520), gravitic.gravity)

        val atmospheric = GalaxyBalance.tolerance(AdaptationLevels(thermal = 0, gravitic = 0, atmospheric = 1))
        assertEquals(ToleranceBand(440, 3_500), atmospheric.pressure)
        assertEquals(ToleranceBand(-30, 45), atmospheric.temperature)
    }

    @Test
    fun `the published widening per level matches the sheet`() {
        assertEquals(14, GalaxyBalance.THERMAL_WIDENING_PER_LEVEL)
        assertEquals(50, GalaxyBalance.GRAVITIC_LOWER_WIDENING_PER_LEVEL)
        assertEquals(120, GalaxyBalance.GRAVITIC_UPPER_WIDENING_PER_LEVEL)
        assertEquals(60, GalaxyBalance.ATMOSPHERIC_LOWER_WIDENING_PER_LEVEL)
        assertEquals(900, GalaxyBalance.ATMOSPHERIC_UPPER_WIDENING_PER_LEVEL)
    }

    @Test
    fun `each axis names the resource it makes rich and the technology that widens it`() {
        // The sheet's table, in the type system: the branch that gates research is gated by the map.
        assertEquals(ResourceKind.METAL, HostilityAxis.GRAVITY.richResource)
        assertEquals(ResourceKind.CRYSTAL, HostilityAxis.PRESSURE.richResource)
        assertEquals(ResourceKind.DEUTERIUM, HostilityAxis.TEMPERATURE.richResource)
        assertEquals(AdaptationTechnology.GRAVITIC, HostilityAxis.GRAVITY.adaptation)
        assertEquals(AdaptationTechnology.ATMOSPHERIC, HostilityAxis.PRESSURE.adaptation)
        assertEquals(AdaptationTechnology.THERMAL, HostilityAxis.TEMPERATURE.adaptation)
    }

    @Test
    fun `one system in 40 carries a relay`() {
        // Generated now, inert until multiplayer — the point is that the sub-stream exists from the
        // start, so adding the holding mechanic later shifts nothing.
        assertEquals(40, GalaxyBalance.RELAY_SYSTEM_IN)
    }

    @Test
    fun `hazards land on 45 percent of worlds and never more than two`() {
        assertEquals(35, GalaxyBalance.ONE_HAZARD_PERCENT)
        assertEquals(10, GalaxyBalance.TWO_HAZARD_PERCENT)
        assertTrue(GalaxyBalance.ONE_HAZARD_PERCENT + GalaxyBalance.TWO_HAZARD_PERCENT < 100)
    }

    @Test
    fun `a coordinate outside the published space is refused rather than generated`() {
        assertFailsWith<IllegalArgumentException> { GalaxyCoordinate(galaxy = 0, system = 1, slot = 1) }
        assertFailsWith<IllegalArgumentException> { GalaxyCoordinate(galaxy = 5, system = 1, slot = 1) }
        assertFailsWith<IllegalArgumentException> { GalaxyCoordinate(galaxy = 1, system = 251, slot = 1) }
        assertFailsWith<IllegalArgumentException> { GalaxyCoordinate(galaxy = 1, system = 1, slot = 16) }
        assertFailsWith<IllegalArgumentException> { GalaxyCoordinate(galaxy = 1, system = 1, slot = 0) }
    }

    private fun traits(metal: Int, crystal: Int, deuterium: Int, hazards: Set<Hazard>): WorldTraits = WorldTraits(
        temperature = Temperature(0),
        gravity = Gravity(1_000),
        pressure = Pressure(1_000),
        metalRichness = Richness(metal),
        crystalRichness = Richness(crystal),
        deuteriumRichness = Richness(deuterium),
        hazards = hazards,
        fields = 150,
    )
}
