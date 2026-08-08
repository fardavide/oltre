package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// The 0.3 adaptation decision sheet published its numbers and they are the design, so they are
// asserted value by value rather than left to whatever the arithmetic happens to produce. The same
// treatment `ResearchBalanceTest` and `GalaxyBalanceTest` give their sheets, for the same reason:
// if one of these has to change, the sheet changed, and that is a design call rather than a
// refactor.
class AdaptationBalanceTest {

    @Test
    fun `all three ladders open on Robotics Factory 4`() {
        // One shared gate, not three that differ: three ladders are worth having because *which one
        // you push first* is a real choice, and a gate that opens one before another makes that
        // choice for the player.
        for (technology in AdaptationTechnology.entries) {
            assertEquals(
                ResearchRequirement.Facility(BuildingType.ROBOTICS_FACTORY, BuildingLevel(4)),
                AdaptationBalance.requirementFor(technology),
                "$technology",
            )
        }
    }

    @Test
    fun `the gate is later than the applied branch's so the galaxy is met first`() {
        val applied = ResearchBalance.requirementFor(Technology.PHOTOVOLTAICS)
        assertEquals(ResearchRequirement.Facility(BuildingType.ROBOTICS_FACTORY, BuildingLevel(1)), applied)
        assertTrue(
            AdaptationBalance.GATE.value > BuildingLevel(1).value,
            "adaptation must open after the applied branch, not alongside it",
        )
    }

    @Test
    fun `level 1 costs what the sheet's table says`() {
        assertCost(AdaptationTechnology.THERMAL, level = 1, metal = 900, crystal = 600, deuterium = 900)
        assertCost(AdaptationTechnology.GRAVITIC, level = 1, metal = 2_400, crystal = 900, deuterium = 200)
        assertCost(AdaptationTechnology.ATMOSPHERIC, level = 1, metal = 850, crystal = 1_600, deuterium = 250)
    }

    @Test
    fun `the three ladders cost exactly the same in three different currencies`() {
        // The load-bearing property of the whole cost table. Each ladder is priced in the resource
        // its own axis makes rich — Gravitic in metal, Atmospheric in crystal, Thermal in the
        // deuterium the research branch already made scarce — and the identical priced total is
        // what keeps that a preference rather than a right answer. If this stops being flat, the
        // sheet's section 4 argument has quietly stopped being true.
        for (technology in AdaptationTechnology.entries) {
            assertEquals(4_800L, priced(AdaptationBalance.adaptationCost(technology, TechLevel(1))), "$technology")
        }
    }

    @Test
    fun `no two ladders are priced alike or there would be nothing to choose between them`() {
        val costs = AdaptationTechnology.entries.map { AdaptationBalance.adaptationCost(it, TechLevel(1)) }
        assertEquals(costs.size, costs.distinct().size, "three ladders that charge the same are one ladder")
    }

    @Test
    fun `cost compounds fifty percent per level on the game's one cost curve`() {
        assertCost(AdaptationTechnology.THERMAL, level = 2, metal = 1_350, crystal = 900, deuterium = 1_350)
        assertCost(AdaptationTechnology.GRAVITIC, level = 2, metal = 3_600, crystal = 1_350, deuterium = 300)
        assertCost(AdaptationTechnology.ATMOSPHERIC, level = 2, metal = 1_275, crystal = 2_400, deuterium = 375)

        assertCost(AdaptationTechnology.THERMAL, level = 3, metal = 2_025, crystal = 1_350, deuterium = 2_025)
        assertCost(AdaptationTechnology.GRAVITIC, level = 3, metal = 5_400, crystal = 2_025, deuterium = 450)
        assertCost(AdaptationTechnology.ATMOSPHERIC, level = 3, metal = 1_913, crystal = 3_600, deuterium = 563)
    }

    @Test
    fun `the branch is the expensive one — level 1 costs nearly twice the priciest technology`() {
        val enrichment = priced(ResearchBalance.researchCost(Technology.ENRICHMENT, TechLevel(1)))
        val thermal = priced(AdaptationBalance.adaptationCost(AdaptationTechnology.THERMAL, TechLevel(1)))

        assertEquals(2_500L, enrichment)
        assertTrue(thermal > enrichment * 3 / 2, "adaptation must cost meaningfully more, was $thermal vs $enrichment")
    }

    @Test
    fun `cost is only defined for levels the curve is bounded for`() {
        assertFailsWith<IllegalArgumentException> {
            AdaptationBalance.adaptationCost(AdaptationTechnology.THERMAL, TechLevel(0))
        }
    }

    @Test
    fun `the deepest defined level still costs a positive amount`() {
        // The bound `TechLevel.MAX` exists for: past it the exact numerator wraps a Long negative,
        // and a negative cost is one `covers()` reads as free.
        val deepest = AdaptationBalance.adaptationCost(AdaptationTechnology.GRAVITIC, TechLevel(TechLevel.MAX))

        assertTrue(deepest.metal > 0 && deepest.crystal > 0 && deepest.deuterium > 0, "was $deepest")
    }

    @Test
    fun `duration is the sheet's table and equal across the three ladders`() {
        for (technology in AdaptationTechnology.entries) {
            assertMinutes(technology, level = 1, robotics = 4, expected = 182)
            assertMinutes(technology, level = 3, robotics = 4, expected = 545)
            assertMinutes(technology, level = 5, robotics = 4, expected = 909)
            assertMinutes(technology, level = 8, robotics = 4, expected = 1_455)

            assertMinutes(technology, level = 1, robotics = 8, expected = 146)
            assertMinutes(technology, level = 3, robotics = 8, expected = 439)
            assertMinutes(technology, level = 5, robotics = 8, expected = 732)
            assertMinutes(technology, level = 8, robotics = 8, expected = 1_171)
        }
    }

    @Test
    fun `adaptation is the longest project in the game`() {
        val enrichment = ResearchBalance.researchDuration(Technology.ENRICHMENT, TechLevel(1), BuildingLevel(4))
        val thermal = AdaptationBalance.adaptationDuration(AdaptationTechnology.THERMAL, TechLevel(1), BuildingLevel(4))

        assertTrue(thermal > enrichment, "was $thermal against $enrichment")
    }

    @Test
    fun `duration rides the research Robotics divisor and never construction's steeper one`() {
        // Research keeps the gentle 25 / (25 + 2 x Robotics) its published tables were computed
        // against; construction halves a build at Robotics 1. Adaptation is research.
        val atZero = AdaptationBalance.adaptationDuration(AdaptationTechnology.THERMAL, TechLevel(1), BuildingLevel(0))
        val atOne = AdaptationBalance.adaptationDuration(AdaptationTechnology.THERMAL, TechLevel(1), BuildingLevel(1))

        assertEquals(240L, atZero.inWholeMinutes)
        assertEquals(240L * 25 / 27, atOne.inWholeMinutes, "a level of Robotics must not halve it")
    }

    @Test
    fun `each ladder saturates where the generator's own extreme stops moving`() {
        // Past these levels a purchase buys nothing, because every world the generator can produce
        // already passes. No cap is added — the sheet declines to add a concept to prevent a
        // purchase nobody has a reason to make — but the numbers are pinned so the day a generation
        // constant moves, this says so.
        assertEquals(17, saturationLevel(HostilityAxis.TEMPERATURE))
        assertEquals(12, saturationLevel(HostilityAxis.GRAVITY))
        assertEquals(11, saturationLevel(HostilityAxis.PRESSURE))
    }

    @Test
    fun `a saturated ladder tolerates every value the generator can reach`() {
        // The property the three numbers above are worth having: at the saturation level, the band
        // really does contain both ends of the published range.
        for (axis in HostilityAxis.entries) {
            val level = saturationLevel(axis)
            val band = GalaxyBalance.tolerance(levelsWith(axis, level)).bandOf(axis)
            for (extreme in extremesOf(axis)) {
                assertTrue(extreme in band, "$axis $extreme must be inside $band at level $level")
            }
        }
    }

    private fun assertCost(
        technology: AdaptationTechnology,
        level: Int,
        metal: Long,
        crystal: Long,
        deuterium: Long,
    ) {
        val cost = AdaptationBalance.adaptationCost(technology, TechLevel(level))
        assertEquals(Resources.of(metal = metal, crystal = crystal, deuterium = deuterium), cost, "$technology $level")
    }

    // Rounded to the nearest minute, the same arithmetic `:sim:run` prints the table with — so the
    // sheet, the harness and this test are three views of one number rather than three numbers.
    private fun assertMinutes(technology: AdaptationTechnology, level: Int, robotics: Int, expected: Long) {
        val duration = AdaptationBalance.adaptationDuration(technology, TechLevel(level), BuildingLevel(robotics))
        assertEquals(
            expected,
            (duration.inWholeSeconds + 30) / 60,
            "$technology level $level at Robotics $robotics",
        )
    }

    // The game's 1 : 2 : 3, the same weighting `balance-log.md` prices the colony's output at.
    private fun priced(cost: Resources): Long = cost.metal + 2 * cost.crystal + 3 * cost.deuterium

    private fun saturationLevel(axis: HostilityAxis): Int =
        extremesOf(axis).maxOf { GalaxyBalance.levelThatTolerates(axis, it) }

    // The ends of each axis's published range, from the sheet's section 8 generation table, taken
    // from the generator's own functions rather than retyped — so a constant that moves moves these
    // with it instead of leaving a stale literal behind.
    private fun extremesOf(axis: HostilityAxis): List<Int> = when (axis) {
        HostilityAxis.TEMPERATURE -> listOf(
            GalaxyBalance.temperature(
                slot = GalaxyBalance.SLOTS_PER_SYSTEM,
                starClass = StarClass.DIM,
                jitter = -GalaxyBalance.TEMPERATURE_JITTER,
            ).celsius,
            GalaxyBalance.temperature(
                slot = 1,
                starClass = StarClass.BRIGHT,
                jitter = GalaxyBalance.TEMPERATURE_JITTER,
            ).celsius,
        )
        HostilityAxis.GRAVITY -> listOf(
            GalaxyBalance.gravity(Uniform(0)).milliG,
            GalaxyBalance.gravity(Uniform.MAX).milliG,
        )
        HostilityAxis.PRESSURE -> listOf(
            GalaxyBalance.pressure(Uniform(0)).milliAtm,
            GalaxyBalance.pressure(Uniform.MAX).milliAtm,
        )
    }

    private fun levelsWith(axis: HostilityAxis, level: Int): AdaptationLevels = when (axis.adaptation) {
        AdaptationTechnology.THERMAL -> AdaptationLevels.NONE.copy(thermal = level)
        AdaptationTechnology.GRAVITIC -> AdaptationLevels.NONE.copy(gravitic = level)
        AdaptationTechnology.ATMOSPHERIC -> AdaptationLevels.NONE.copy(atmospheric = level)
    }
}
