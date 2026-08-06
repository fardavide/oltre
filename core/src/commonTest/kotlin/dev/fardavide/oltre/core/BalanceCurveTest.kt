package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The shape of the placeholder curves is a design decision, so it is asserted rather than left
// to whatever the arithmetic happens to produce: an upgrade is a raise, not a doubling, and
// cost outgrows output so depth stays a decision.
class BalanceCurveTest {

    @Test
    fun `an upgrade raises production well short of doubling it`() {
        for (level in 1..20) {
            // when
            val current = PlaceholderBalance.metalProductionPerHour(BuildingLevel(level))
            val next = PlaceholderBalance.metalProductionPerHour(BuildingLevel(level + 1))

            // then
            assertTrue(next > current, "level ${level + 1} must out-produce level $level")
            assertTrue(next * 2 < current * 3, "level ${level + 1} must stay under +50% of level $level")
        }
    }

    @Test
    fun `production is human-scale at the levels a first week reaches`() {
        // then
        assertEquals(60L, PlaceholderBalance.metalProductionPerHour(BuildingLevel(1)))
        assertEquals(440L, PlaceholderBalance.metalProductionPerHour(BuildingLevel(10)))
        assertEquals(30L, PlaceholderBalance.crystalProductionPerHour(BuildingLevel(1)))
        assertEquals(213L, PlaceholderBalance.crystalProductionPerHour(BuildingLevel(10)))
        assertEquals(15L, PlaceholderBalance.deuteriumProductionPerHour(BuildingLevel(1)))
        assertEquals(97L, PlaceholderBalance.deuteriumProductionPerHour(BuildingLevel(10)))
    }

    @Test
    fun `a razed facility produces nothing`() {
        // then
        assertEquals(0L, PlaceholderBalance.metalProductionPerHour(BuildingLevel(0)))
        assertEquals(0L, PlaceholderBalance.crystalProductionPerHour(BuildingLevel(0)))
        assertEquals(0L, PlaceholderBalance.deuteriumProductionPerHour(BuildingLevel(0)))
    }

    @Test
    fun `cost compounds by half again per level`() {
        for (level in 1..20) {
            // when
            val current = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(level))
            val next = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(level + 1))

            // then
            assertEquals(current.metal * 3 / 2, next.metal, "metal cost at level ${level + 1}")
            assertEquals(current.crystal * 3 / 2, next.crystal, "crystal cost at level ${level + 1}")
        }
    }

    @Test
    fun `cost outgrows production, so deep levels pay back slower`() {
        // given the payback of a level: what it costs in metal over what it adds per hour
        fun paybackHours(level: Int): Long {
            val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(level + 1)).metal
            val gain = PlaceholderBalance.metalProductionPerHour(BuildingLevel(level + 1)) -
                PlaceholderBalance.metalProductionPerHour(BuildingLevel(level))
            return cost / gain
        }

        // then
        assertTrue(paybackHours(1) < 12, "the first mine upgrade must pay back within half a day")
        assertTrue(paybackHours(10) > paybackHours(1), "depth must cost patience")
    }

    @Test
    fun `a new colony can afford its first upgrades immediately`() {
        // given
        val stock = GameState.initial().resources

        // then
        assertTrue(
            stock.covers(PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2))),
            "a new colony opens on a decision, not on a wait",
        )
        assertTrue(
            stock.covers(PlaceholderBalance.upgradeCost(BuildingType.SOLAR_PLANT, BuildingLevel(2))),
            "and on more than one, so the first decision is a choice",
        )
    }
}
