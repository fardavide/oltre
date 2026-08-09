package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

// The 0.1 research decision sheet published three tables and they are the design, so they are
// asserted value by value rather than left to whatever the arithmetic happens to produce. If one
// of these numbers has to change, the sheet changed — which is Davide's call, not a refactor.
class ResearchBalanceTest {

    @Test
    fun `Photovoltaics matches the published table`() {
        assertTable(Technology.PHOTOVOLTAICS, PHOTOVOLTAICS)
    }

    @Test
    fun `Extraction matches the published table`() {
        assertTable(Technology.EXTRACTION, EXTRACTION)
    }

    @Test
    fun `Enrichment matches the published table`() {
        assertTable(Technology.ENRICHMENT, ENRICHMENT)
    }

    @Test
    fun `an unresearched technology multiplies nothing`() {
        for (technology in Technology.entries) {
            assertEquals(
                ResearchBalance.MULTIPLIER_BASIS,
                ResearchBalance.multiplier(technology, TechLevel(0)),
                "$technology at level 0",
            )
            assertEquals(0, ResearchBalance.effectPercent(technology, TechLevel(0)), "$technology at level 0")
        }
    }

    @Test
    fun `every technology compounds so a deep level is still a raise`() {
        // The sheet overruled the obvious precedent here: with a linear effect and an exponential
        // cost, payback doubles every level and the branch is dead by level 4.
        for (technology in Technology.entries) {
            for (level in 0..20) {
                val here = ResearchBalance.multiplier(technology, TechLevel(level))
                val next = ResearchBalance.multiplier(technology, TechLevel(level + 1))
                assertTrue(next > here, "$technology level ${level + 1} must beat level $level")
                assertTrue(
                    next - here >= here - ResearchBalance.multiplier(technology, TechLevel((level - 1).coerceAtLeast(0))),
                    "$technology must compound rather than grow linearly at level $level",
                )
            }
        }
    }

    @Test
    fun `cost outgrows effect so depth stays a decision`() {
        // Davide's rule for the buildings, which the sheet keeps: the cost curve is steeper than
        // the output curve, so every level pays back more slowly than the one before it.
        for (technology in Technology.entries) {
            for (level in 1..10) {
                val cost = ResearchBalance.researchCost(technology, TechLevel(level)).deuterium
                val nextCost = ResearchBalance.researchCost(technology, TechLevel(level + 1)).deuterium
                val gain = ResearchBalance.multiplier(technology, TechLevel(level)) -
                    ResearchBalance.multiplier(technology, TechLevel(level - 1))
                val nextGain = ResearchBalance.multiplier(technology, TechLevel(level + 1)) -
                    ResearchBalance.multiplier(technology, TechLevel(level))
                assertTrue(
                    nextCost * gain > cost * nextGain,
                    "$technology level ${level + 1} must pay back slower than level $level",
                )
            }
        }
    }

    @Test
    fun `research leans on deuterium harder than the mine of the same era`() {
        // The whole reason research exists in 0.1 economically: nothing else wants deuterium
        // except Robotics and Nanite, so without it deuterium accumulates unspent.
        val extractionToSix = (1..6).sumOf { ResearchBalance.researchCost(Technology.EXTRACTION, TechLevel(it)).deuterium }
        val extractionMetalToSix = (1..6).sumOf { ResearchBalance.researchCost(Technology.EXTRACTION, TechLevel(it)).metal }
        // The sheet's CUM DEUT column reads 4,156 because it sums the costs before rounding them;
        // a player pays six rounded levels, which is one unit more. The per-level table is the
        // one that has to match to the unit, and it does — this is what is actually charged.
        assertEquals(3_789L, extractionToSix, "six levels of Extraction cost 3789 deuterium in total")
        assertEquals(11_369L, extractionMetalToSix, "and 11369 metal - the first three levels carry the opening discount")
    }

    @Test
    fun `duration is base minutes times level before any Robotics divisor, once the discount is out`() {
        val idle = BuildingLevel(0)
        assertEquals(600.minutes, ResearchBalance.researchDuration(Technology.PHOTOVOLTAICS, TechLevel(10), idle))
        assertEquals(900.minutes, ResearchBalance.researchDuration(Technology.EXTRACTION, TechLevel(10), idle))
        assertEquals(1_500.minutes, ResearchBalance.researchDuration(Technology.ENRICHMENT, TechLevel(10), idle))

        // And under it, the same third: "cheaper **and quicker**". A building got the second half
        // for nothing because round 11 made its duration a function of its cost; this branch is a
        // table times a level, so it had to be told.
        assertEquals(20.minutes, ResearchBalance.researchDuration(Technology.PHOTOVOLTAICS, TechLevel(1), idle))
        assertEquals(30.minutes, ResearchBalance.researchDuration(Technology.EXTRACTION, TechLevel(1), idle))
        assertEquals(50.minutes, ResearchBalance.researchDuration(Technology.ENRICHMENT, TechLevel(1), idle))
    }

    @Test
    fun `the Robotics divisor matches the published AT ROB 4 column`() {
        // Rounded to the nearest minute, which is how the sheet's tables are written. A cost chip
        // in the UI may read one minute more because it ceils - the colony's convention, so that
        // a sub-minute chip never reads 0m - and that is a display rule, not this one.
        val robotics = BuildingLevel(4)
        val photovoltaics = listOf(45, 91, 136, 182, 227, 273, 318, 364, 409, 455)
        val extraction = listOf(68, 136, 205, 273, 341, 409, 477, 545, 614, 682)
        val enrichment = listOf(114, 227, 341, 455, 568, 682, 795, 909, 1_023, 1_136)
        for (level in 4..10) {
            assertEquals(
                photovoltaics[level - 1].toLong(),
                ResearchBalance.researchDuration(Technology.PHOTOVOLTAICS, TechLevel(level), robotics).roundedMinutes(),
                "Photovoltaics level $level at Robotics 4",
            )
            assertEquals(
                extraction[level - 1].toLong(),
                ResearchBalance.researchDuration(Technology.EXTRACTION, TechLevel(level), robotics).roundedMinutes(),
                "Extraction level $level at Robotics 4",
            )
            assertEquals(
                enrichment[level - 1].toLong(),
                ResearchBalance.researchDuration(Technology.ENRICHMENT, TechLevel(level), robotics).roundedMinutes(),
                "Enrichment level $level at Robotics 4",
            )
        }

        // Levels 1 to 3 carry the opening discount, so the sheet's column is what they would have
        // cost rather than what they cost. Same divisor, applied after it.
        assertEquals(15L, ResearchBalance.researchDuration(Technology.PHOTOVOLTAICS, TechLevel(1), robotics).roundedMinutes())
        assertEquals(50L, ResearchBalance.researchDuration(Technology.PHOTOVOLTAICS, TechLevel(2), robotics).roundedMinutes())
        assertEquals(106L, ResearchBalance.researchDuration(Technology.PHOTOVOLTAICS, TechLevel(3), robotics).roundedMinutes())
        assertEquals(23L, ResearchBalance.researchDuration(Technology.EXTRACTION, TechLevel(1), robotics).roundedMinutes())
        assertEquals(38L, ResearchBalance.researchDuration(Technology.ENRICHMENT, TechLevel(1), robotics).roundedMinutes())
    }

    @Test
    fun `a higher Robotics level shortens every research`() {
        for (technology in Technology.entries) {
            val idle = ResearchBalance.researchDuration(technology, TechLevel(3), BuildingLevel(0))
            val helped = ResearchBalance.researchDuration(technology, TechLevel(3), BuildingLevel(4))
            val helpedMore = ResearchBalance.researchDuration(technology, TechLevel(3), BuildingLevel(10))
            assertTrue(helped < idle, "$technology must be quicker with a Robotics Factory")
            assertTrue(helpedMore < helped, "$technology must be quicker still with a deeper one")
        }
    }

    @Test
    fun `the branch opens behind the deuterium wall the game already has`() {
        assertEquals(
            ResearchRequirement.Facility(BuildingType.ROBOTICS_FACTORY, BuildingLevel(1)),
            ResearchBalance.requirementFor(Technology.PHOTOVOLTAICS),
        )
        assertEquals(
            ResearchRequirement.Facility(BuildingType.ROBOTICS_FACTORY, BuildingLevel(1)),
            ResearchBalance.requirementFor(Technology.EXTRACTION),
        )
    }

    @Test
    fun `Enrichment sits behind Extraction so the branch opens on one decision`() {
        assertEquals(
            ResearchRequirement.Tech(Technology.EXTRACTION, TechLevel(3)),
            ResearchBalance.requirementFor(Technology.ENRICHMENT),
        )
    }

    @Test
    fun `a requirement is met only once the level it names is reached`() {
        // given a colony one level short of its first Robotics Factory
        val locked = GameState.initial()
        val unlocked = locked.copy(buildings = locked.buildings.copy(roboticsFactory = BuildingLevel(1)))

        // then
        assertFalse(ResearchBalance.requirementFor(Technology.EXTRACTION).isMetBy(locked))
        assertTrue(ResearchBalance.requirementFor(Technology.EXTRACTION).isMetBy(unlocked))
    }

    @Test
    fun `a tech requirement reads the research levels rather than the buildings`() {
        // given
        val short = GameState.initial().copy(research = Research.initial().withLevel(Technology.EXTRACTION, TechLevel(2)))
        val met = GameState.initial().copy(research = Research.initial().withLevel(Technology.EXTRACTION, TechLevel(3)))

        // then
        assertFalse(ResearchBalance.requirementFor(Technology.ENRICHMENT).isMetBy(short))
        assertTrue(ResearchBalance.requirementFor(Technology.ENRICHMENT).isMetBy(met))
    }

    @Test
    fun `a cost past the defined range is refused rather than silently wrapped`() {
        // The exact cost arithmetic multiplies by 3^steps, which leaves Long past this level -
        // and an overflowed cost wraps negative, which covers() would read as free.
        assertFailsWith<IllegalArgumentException> {
            ResearchBalance.researchCost(Technology.ENRICHMENT, TechLevel(TechLevel.MAX + 1))
        }
        assertFailsWith<IllegalArgumentException> {
            ResearchBalance.researchCost(Technology.ENRICHMENT, TechLevel(0))
        }
    }

    @Test
    fun `the deepest defined cost still fits a stock`() {
        for (technology in Technology.entries) {
            val cost = ResearchBalance.researchCost(technology, TechLevel(TechLevel.MAX))
            assertTrue(cost.metal > 0 && cost.crystal > 0 && cost.deuterium > 0, "$technology wrapped negative")
        }
    }

    private fun assertTable(technology: Technology, table: List<Row>) {
        for (row in table) {
            val level = TechLevel(row.level)
            assertEquals(row.effectPercent, ResearchBalance.effectPercent(technology, level), "$technology effect at ${row.level}")
            val cost = ResearchBalance.researchCost(technology, level)
            assertEquals(discounted(row.metal, row.level), cost.metal, "$technology metal at ${row.level}")
            assertEquals(discounted(row.crystal, row.level), cost.crystal, "$technology crystal at ${row.level}")
            assertEquals(discounted(row.deuterium, row.level), cost.deuterium, "$technology deuterium at ${row.level}")
        }
    }

    // The sheet's tables below are still the design, and they are still asserted row by row — but
    // since 2026-08-09 they are the **full** price and the first three levels are sold under it.
    // Davide's call, which is the only kind that may move these numbers: "Everything must be
    // cheaper and quicker across the board, until first expedition."
    //
    // Written out here rather than read from `openingDiscount` so the fixture stays a statement of
    // what the game charges instead of an echo of the code that charges it.
    private fun discounted(fullPrice: Long, level: Int): Long =
        if (level >= 4) fullPrice else fullPrice * (3 + 2 * (level - 1)) / 9

    private fun Duration.roundedMinutes(): Long = (inWholeSeconds + 30) / 60

    private data class Row(val level: Int, val effectPercent: Int, val metal: Long, val crystal: Long, val deuterium: Long)

    private companion object {

        // Solar Plant output x 1.10^level - bases 300 / 150 / 100
        val PHOTOVOLTAICS = listOf(
            Row(1, 10, 300, 150, 100),
            Row(2, 21, 450, 225, 150),
            Row(3, 33, 675, 338, 225),
            Row(4, 46, 1_013, 506, 338),
            Row(5, 61, 1_519, 759, 506),
            Row(6, 77, 2_278, 1_139, 759),
            Row(7, 95, 3_417, 1_709, 1_139),
            Row(8, 114, 5_126, 2_563, 1_709),
            Row(9, 136, 7_689, 3_844, 2_563),
            Row(10, 159, 11_533, 5_767, 3_844),
        )

        // Metal Mine and Crystal Mine output x 1.08^level - bases 600 / 400 / 200
        val EXTRACTION = listOf(
            Row(1, 8, 600, 400, 200),
            Row(2, 17, 900, 600, 300),
            Row(3, 26, 1_350, 900, 450),
            Row(4, 36, 2_025, 1_350, 675),
            Row(5, 47, 3_038, 2_025, 1_013),
            Row(6, 59, 4_556, 3_038, 1_519),
            Row(7, 71, 6_834, 4_556, 2_278),
            Row(8, 85, 10_252, 6_834, 3_417),
            Row(9, 100, 15_377, 10_252, 5_126),
            Row(10, 116, 23_066, 15_377, 7_689),
        )

        // Deuterium Synthesizer output x 1.14^level - bases 500 / 700 / 200
        val ENRICHMENT = listOf(
            Row(1, 14, 500, 700, 200),
            Row(2, 30, 750, 1_050, 300),
            Row(3, 48, 1_125, 1_575, 450),
            Row(4, 69, 1_688, 2_363, 675),
            Row(5, 93, 2_531, 3_544, 1_013),
            Row(6, 119, 3_797, 5_316, 1_519),
            Row(7, 150, 5_695, 7_973, 2_278),
            Row(8, 185, 8_543, 11_960, 3_417),
            Row(9, 225, 12_814, 17_940, 5_126),
            Row(10, 271, 19_222, 26_910, 7_689),
        )
    }
}
