package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

// Davide, 2026-08-09: *"lets find a solution to overflow, the game must be solid against large
// numbers for super lategame."*
//
// Every number in this game is a Long, and a Long that wraps does not fail — it comes back negative,
// and a negative cost is one `covers()` reads as **free**. This file is the standing proof that it
// cannot happen: it walks every curve to the deepest level the game defines, and it drives the
// accrual with spans no player will ever produce.
//
// It has caught one real bug already. The opening discount was carried as an exact power, and a
// convergence level of 18 priced the Nanite Factory at −70 deuterium; nothing failed until
// `Resources.of` refused the negative, which is a crash in a running game rather than a red build.
class OverflowSafetyTest {

    @Test
    fun `every building cost is computable and positive at every level the game defines`() {
        for (building in BuildingType.entries) {
            for (level in 1..MAX_BUILDING_LEVEL) {
                val cost = PlaceholderBalance.upgradeCost(building, BuildingLevel(level))
                assertTrue(cost.metal > 0, "$building $level priced ${cost.metal} metal")
                assertTrue(cost.crystal > 0, "$building $level priced ${cost.crystal} crystal")
                assertTrue(cost.deuterium >= 0, "$building $level priced ${cost.deuterium} deuterium")
            }
        }
    }

    @Test
    fun `every project cost is computable and positive at every level the game defines`() {
        for (technology in Technology.entries) {
            for (level in 1..TechLevel.MAX) {
                val cost = ResearchBalance.researchCost(technology, TechLevel(level))
                assertTrue(
                    cost.metal > 0 && cost.crystal > 0 && cost.deuterium > 0,
                    "$technology $level priced $cost",
                )
            }
        }
        for (ladder in AdaptationTechnology.entries) {
            for (level in 1..TechLevel.MAX) {
                val cost = AdaptationBalance.adaptationCost(ladder, TechLevel(level))
                assertTrue(
                    cost.metal > 0 && cost.crystal > 0 && cost.deuterium > 0,
                    "$ladder $level priced $cost",
                )
            }
        }
    }

    @Test
    fun `every duration is computable and positive at every level the game defines`() {
        for (building in BuildingType.entries) {
            for (level in 1..MAX_BUILDING_LEVEL) {
                for (robotics in listOf(0, 1, 10, 40)) {
                    val duration = PlaceholderBalance
                        .upgradeDuration(building, BuildingLevel(level), BuildingLevel(robotics), BuildingLevel(0))
                    assertTrue(duration.isPositive(), "$building $level at robotics $robotics took $duration")
                }
            }
        }
        for (level in 1..TechLevel.MAX) {
            for (robotics in listOf(0, 1, 10, 40)) {
                assertTrue(
                    ResearchBalance.researchDuration(Technology.ENRICHMENT, TechLevel(level), BuildingLevel(robotics))
                        .isPositive(),
                )
                assertTrue(
                    AdaptationBalance
                        .adaptationDuration(AdaptationTechnology.THERMAL, TechLevel(level), BuildingLevel(robotics))
                        .isPositive(),
                )
            }
        }
    }

    @Test
    fun `a curve that would leave Long says so instead of wrapping`() {
        // The behaviour the two tests above depend on. `checkedTimes` throws rather than saturating,
        // because a cost of Long.MAX is not a cost anyone designed — it is a wrong answer wearing a
        // plausible face, and it would be spent against rather than crashed on.
        assertFailsWith<IllegalArgumentException> { checkedTimes(Long.MAX_VALUE / 2, 3) { "deliberate" } }
        assertFailsWith<IllegalArgumentException> { exactGeometric(2_400, steps = 60, numerator = 3, denominator = 2) }

        // And the boundary is not over-eager: the largest product that still fits comes back exact.
        assertEquals(Long.MAX_VALUE - 1, checkedTimes(Long.MAX_VALUE / 2, 2) { "fits" })
        assertEquals(0L, checkedTimes(0, Long.MAX_VALUE) { "zero is never an overflow" })
    }

    @Test
    fun `the level ladder is computable far past anything a save can hold`() {
        // The experience curve is the one number in the game with **no ceiling** — buildings stop at
        // 40 and technologies at 30, and a log just keeps growing. So the ladder is walked far past
        // any level a player will reach rather than to a declared maximum, and the round trip is what
        // is checked: a threshold that wrapped would come back as a level that had gone backwards.
        var previous = Experience(0)
        for (level in 0..1_000) {
            val threshold = ExperienceBalance.thresholdOf(PlayerLevel(level))
            assertTrue(threshold >= previous, "the threshold for level $level fell to $threshold")
            assertEquals(PlayerLevel(level), ExperienceBalance.levelFor(threshold))
            previous = threshold
        }
    }

    @Test
    fun `a total no log could ever reach still reads as a level rather than wrapping`() {
        // A hundred million points is about sixty thousand days of the sim's own player. `levelFor`
        // multiplies the total by eight times the step to take a root of it, so this is the term that
        // would leave Long first — and it says so rather than handing back a negative level.
        val absurd = Experience(100_000_000)
        assertTrue(ExperienceBalance.levelFor(absurd).value > 0)
        assertFailsWith<IllegalArgumentException> { ExperienceBalance.levelFor(Experience(Long.MAX_VALUE)) }
    }

    @Test
    fun `a colony away for a thousand years fills its store rather than overflowing`() {
        // The one that would have bitten a real save. `stock + rate x elapsed` clamped afterwards is
        // correct arithmetic and unsafe storage: the clamp is small and the product it clamps is
        // not. A device clock that jumped, or a save whose `lastUpdatedAt` is far in the past, is
        // enough — and `Resources`' own non-negative guard turns the wrap into a crash on load.
        val deep = GameState.initial(GalaxySeed(1)).copy(
            buildings = Buildings(
                metalMine = BuildingLevel(MAX_BUILDING_LEVEL),
                crystalMine = BuildingLevel(MAX_BUILDING_LEVEL),
                deuteriumSynthesizer = BuildingLevel(MAX_BUILDING_LEVEL),
                solarPlant = BuildingLevel(MAX_BUILDING_LEVEL),
                roboticsFactory = BuildingLevel(0),
                naniteFactory = BuildingLevel(0),
            ),
            research = Research.initial()
                .withLevel(Technology.EXTRACTION, TechLevel(TechLevel.MAX))
                .withLevel(Technology.ENRICHMENT, TechLevel(TechLevel.MAX)),
        )

        val epoch = Instant.fromEpochMilliseconds(0)
        for (span in listOf(1.days, 365.days, 365_000.days)) {
            val after = advance(deep, from = epoch, to = epoch + span)
            assertEquals(
                PlaceholderBalance.STORAGE_CAPACITY,
                after.resources.metal,
                "a colony this deep fills its store within a day, and must still be full after $span",
            )
            assertTrue(after.resources.crystal > 0 && after.resources.deuterium > 0, "after $span")
        }
    }

    @Test
    fun `accrual still composes across the spans it now clamps`() {
        // The clamp is applied to the elapsed time rather than to the product, which is a change to
        // *how* the sum is reached. This is the property that says it is still the same sum: the
        // composability the whole simulation rests on, exercised either side of the store filling.
        val colony = GameState.initial(GalaxySeed(1))
        val epoch = Instant.fromEpochMilliseconds(0)
        for (span in listOf(1.days, 4_000.days)) {
            val whole = advance(colony, from = epoch, to = epoch + span)
            val halved = advance(
                advance(colony, from = epoch, to = epoch + span / 2),
                from = epoch + span / 2,
                to = epoch + span,
            )
            assertEquals(whole.resources, halved.resources, "advancing $span in one step and in two")
        }
    }

    // `PlaceholderBalance.MAX_UPGRADE_LEVEL` is `internal`, so this file could read it — and
    // deliberately does not. This is the same number stated as the *specification*, and the next
    // test is what pins the two together; reading the constant here would make that test assert
    // that 40 equals 40.
    private val MAX_BUILDING_LEVEL = 40

    @Test
    fun `the declared building ceiling is the one the game enforces`() {
        PlaceholderBalance.upgradeCost(BuildingType.NANITE_FACTORY, BuildingLevel(MAX_BUILDING_LEVEL))
        assertFailsWith<IllegalArgumentException> {
            PlaceholderBalance.upgradeCost(BuildingType.NANITE_FACTORY, BuildingLevel(MAX_BUILDING_LEVEL + 1))
        }
    }
}
