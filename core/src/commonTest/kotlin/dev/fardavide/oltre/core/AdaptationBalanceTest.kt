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
        //
        // *Exact* equality was a property of the undiscounted level-1 table — 4,800 each. Since the
        // opening discount reached this branch it is equality to within **two units in sixteen
        // hundred**, because a third of three differently-shaped baskets does not floor to three
        // equal totals. Asserted as a proportion across the ramp and past it, so the property is
        // checked where it is now true rather than only where it used to be exact.
        for (level in 1..5) {
            val totals = AdaptationTechnology.entries
                .map { priced(AdaptationBalance.adaptationCost(it, TechLevel(level))) }
            assertTrue(
                (totals.max() - totals.min()) * 500 <= totals.min(),
                "level $level is priced $totals — the three ladders have stopped costing the same",
            )
        }
        // And the shape the sheet actually chose, at the first level where nothing is discounted.
        assertEquals(16_202L, priced(AdaptationBalance.adaptationCost(AdaptationTechnology.THERMAL, TechLevel(4))))
    }

    @Test
    fun `no two ladders are priced alike or there would be nothing to choose between them`() {
        val costs = AdaptationTechnology.entries.map { AdaptationBalance.adaptationCost(it, TechLevel(1)) }
        assertEquals(costs.size, costs.distinct().size, "three ladders that charge the same are one ladder")
    }

    @Test
    fun `cost compounds fifty percent per level once the opening discount has run out`() {
        for (technology in AdaptationTechnology.entries) {
            for (level in 4..10) {
                val current = AdaptationBalance.adaptationCost(technology, TechLevel(level))
                val next = AdaptationBalance.adaptationCost(technology, TechLevel(level + 1))
                assertTrue(
                    next.metal * 2 in (current.metal * 3 - 2)..(current.metal * 3 + 2),
                    "$technology $level -> ${level + 1}: ${current.metal} then ${next.metal}",
                )
            }
        }

        assertCost(AdaptationTechnology.THERMAL, level = 2, metal = 1_350, crystal = 900, deuterium = 1_350)
        assertCost(AdaptationTechnology.GRAVITIC, level = 2, metal = 3_600, crystal = 1_350, deuterium = 300)
        assertCost(AdaptationTechnology.ATMOSPHERIC, level = 2, metal = 1_275, crystal = 2_400, deuterium = 375)

        assertCost(AdaptationTechnology.THERMAL, level = 3, metal = 2_025, crystal = 1_350, deuterium = 2_025)
        assertCost(AdaptationTechnology.GRAVITIC, level = 3, metal = 5_400, crystal = 2_025, deuterium = 450)
        assertCost(AdaptationTechnology.ATMOSPHERIC, level = 3, metal = 1_913, crystal = 3_600, deuterium = 563)
    }

    @Test
    fun `the branch is the expensive one — nearly twice the priciest technology at every level`() {
        // The sheet's section 4: adaptation costs about twice the priciest applied technology, and
        // that is what makes it read as the branch you save up for.
        //
        // It briefly stopped being true. The opening discount reached the applied branch first and
        // left this one at full price, so the step from Enrichment 1 to Thermal 1 went from 1.9x to
        // **5.8x** — a cliff exactly where the player meets the galaxy. Putting both branches on
        // one schedule is the fix, and the property to assert is therefore the *ratio*, at every
        // level rather than at the one depth a single pair of numbers would have pinned.
        for (level in 1..6) {
            val enrichment = priced(ResearchBalance.researchCost(Technology.ENRICHMENT, TechLevel(level)))
            val thermal = priced(AdaptationBalance.adaptationCost(AdaptationTechnology.THERMAL, TechLevel(level)))
            assertTrue(
                thermal * 10 in (enrichment * 18)..(enrichment * 21),
                "level $level: $thermal against $enrichment is outside 1.8x to 2.1x",
            )
        }

        // The two ends, written out, so a ratio that holds while both sides drift says so.
        assertEquals(830L, priced(ResearchBalance.researchCost(Technology.ENRICHMENT, TechLevel(1))))
        assertEquals(1_600L, priced(AdaptationBalance.adaptationCost(AdaptationTechnology.THERMAL, TechLevel(1))))
        assertEquals(8_439L, priced(ResearchBalance.researchCost(Technology.ENRICHMENT, TechLevel(4))))
        assertEquals(16_202L, priced(AdaptationBalance.adaptationCost(AdaptationTechnology.THERMAL, TechLevel(4))))
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
            // Levels 1 and 3 carry the opening discount; 5 and 8 are the sheet's own figures.
            assertMinutes(technology, level = 1, robotics = 4, expected = 61)
            assertMinutes(technology, level = 3, robotics = 4, expected = 424)
            assertMinutes(technology, level = 5, robotics = 4, expected = 909)
            assertMinutes(technology, level = 8, robotics = 4, expected = 1_455)

            assertMinutes(technology, level = 1, robotics = 8, expected = 49)
            assertMinutes(technology, level = 3, robotics = 8, expected = 341)
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

        // 80 rather than the sheet's 240: level 1 is the deepest step of the opening discount.
        assertEquals(80L, atZero.inWholeMinutes)
        assertEquals(80L * 25 / 27, atOne.inWholeMinutes, "a level of Robotics must not halve it")
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
        assertEquals(
            Resources.of(
                metal = discounted(metal, level),
                crystal = discounted(crystal, level),
                deuterium = discounted(deuterium, level),
            ),
            cost,
            "$technology $level",
        )
    }

    // The sheet's tables are still the design and are still written out below verbatim — but since
    // 2026-08-09 they are the **full** price, and levels 1 to 3 are sold under it on the same
    // schedule the applied branch uses. Spelled out here rather than read from `openingDiscount`,
    // so the fixture states what the game charges instead of echoing the code that charges it.
    private fun discounted(fullPrice: Long, level: Int): Long =
        if (level >= 4) fullPrice else fullPrice * (3 + 2 * (level - 1)) / 9

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
