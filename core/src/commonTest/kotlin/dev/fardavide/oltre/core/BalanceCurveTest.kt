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
    fun `metal is produced in the proportion the colony is actually upgraded in`() {
        // given the three facilities a player buys a level of *every session* — the basket that
        // repeats, not the whole tree. The Robotics Factory (3.3:1) and the Deuterium Synthesizer
        // (3:1) are bought a handful of times each in a game and are the two most metal-heavy rows
        // in it; averaging them in as equals is what pulled this target up to ~3:1 at 0.0.12, and
        // a colony upgraded at that ratio starves for crystal while metal piles up unspent. The
        // Nanite Factory is out for the same reason it always was: a different economy.
        val basket = listOf(
            BuildingType.METAL_MINE,
            BuildingType.CRYSTAL_MINE,
            BuildingType.SOLAR_PLANT,
        )
        val demandedMetal = basket.sumOf { PlaceholderBalance.upgradeCost(it, BuildingLevel(1)).metal }
        val demandedCrystal = basket.sumOf { PlaceholderBalance.upgradeCost(it, BuildingLevel(1)).crystal }

        // when
        val producedMetal = PlaceholderBalance.metalProductionPerHour(BuildingLevel(1))
        val producedCrystal = PlaceholderBalance.crystalProductionPerHour(BuildingLevel(1))

        // then production must track that demand within a tenth **in both directions**. The
        // one-sided bound this replaces could only ever catch metal being too poor, which is the
        // 0.0.12 failure; it waved through metal being too rich, which is the 0.1.0 one. `:sim:run`
        // measures the same thing end to end: at 90:30 the greedy week spends 130 of its 168 hours
        // with a purchase blocked by crystal *alone*, holding 49,544 idle metal it cannot use.
        assertTrue(
            producedMetal * demandedCrystal * 10 >= demandedMetal * producedCrystal * 9,
            "metal production ($producedMetal:$producedCrystal) is too poor in metal against the " +
                "$demandedMetal:$demandedCrystal the colony is upgraded in",
        )
        assertTrue(
            producedMetal * demandedCrystal * 10 <= demandedMetal * producedCrystal * 11,
            "metal production ($producedMetal:$producedCrystal) is too rich in metal against the " +
                "$demandedMetal:$demandedCrystal the colony is upgraded in — crystal becomes the " +
                "only thing anyone waits for, and metal accumulates with nothing to buy",
        )
    }

    @Test
    fun `production is human-scale at the levels a first week reaches`() {
        // then
        assertEquals(90L, PlaceholderBalance.metalProductionPerHour(BuildingLevel(1)))
        assertEquals(663L, PlaceholderBalance.metalProductionPerHour(BuildingLevel(10)))
        assertEquals(36L, PlaceholderBalance.crystalProductionPerHour(BuildingLevel(1)))
        assertEquals(262L, PlaceholderBalance.crystalProductionPerHour(BuildingLevel(10)))
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
    fun `cost outgrows production so deep levels pay back slower`() {
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
    fun `a build takes as long as it costs`() {
        // The shape balance round 8 held for four rounds and this slice lands. What it replaces was
        // linear in the level while cost compounds at +50%, so the two curves diverged from level
        // one and a colony spent 87.5% of its opening two days with nothing at all in flight.
        //
        // Asserted as a proportion rather than against a table of minutes, because the *ratio* is
        // the decision. A per-building table would let one row drift out of shape and still pass.
        for (level in 2..20) {
            val cheap = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(level))
            val dear = PlaceholderBalance.upgradeCost(BuildingType.ROBOTICS_FACTORY, BuildingLevel(level))
            val cheapMinutes = PlaceholderBalance
                .upgradeDuration(BuildingType.METAL_MINE, BuildingLevel(level), BuildingLevel(0))
                .inWholeMinutes
            val dearMinutes = PlaceholderBalance
                .upgradeDuration(BuildingType.ROBOTICS_FACTORY, BuildingLevel(level), BuildingLevel(0))
                .inWholeMinutes

            assertEquals((cheap.metal + cheap.crystal) / 3, cheapMinutes, "metal mine $level")
            assertEquals((dear.metal + dear.crystal) / 3, dearMinutes, "robotics factory $level")
        }
    }

    @Test
    fun `deuterium buys the research branch and never the clock`() {
        // The Robotics Factory and the Nanite Factory are the only two rows that cost deuterium —
        // the Deuterium Synthesizer *produces* it and is bought with metal and crystal like any
        // mine — and the Robotics Factory is what gates the whole research branch. Pricing time in
        // deuterium as well would make one scarcity govern two trade-offs the player has to make
        // separately, so the duration sum is metal and crystal, as OGame's is.
        for (building in listOf(BuildingType.ROBOTICS_FACTORY, BuildingType.NANITE_FACTORY)) {
            val cost = PlaceholderBalance.upgradeCost(building, BuildingLevel(4))
            assertTrue(cost.deuterium > 0, "the fixture needs a row that costs deuterium")
            assertEquals(
                (cost.metal + cost.crystal) / 3,
                PlaceholderBalance.upgradeDuration(building, BuildingLevel(4), BuildingLevel(0)).inWholeMinutes,
                "$building must not be slowed by the resource that gates research",
            )
        }
    }

    @Test
    fun `no build is ever instant however deep the Robotics Factory goes`() {
        // At Robotics 10 a first mine level divides to under three minutes, which is not a build —
        // it is a tap with a delay on it, and it would undo at depth exactly the emptiness the
        // cost-proportional curve exists to fill. The floor is applied to what the player waits,
        // *after* the divisor, so the divisor cannot cut through it.
        val instant = PlaceholderBalance
            .upgradeDuration(BuildingType.METAL_MINE, BuildingLevel(2), BuildingLevel(10))
        assertEquals(5, instant.inWholeMinutes)

        for (robotics in 0..20) {
            for (building in BuildingType.entries) {
                assertTrue(
                    PlaceholderBalance
                        .upgradeDuration(building, BuildingLevel(2), BuildingLevel(robotics))
                        .inWholeMinutes >= 5,
                    "$building at robotics $robotics",
                )
            }
        }
    }

    @Test
    fun `the opening still fits in a check-in`() {
        // The curve makes builds longer, and the one it must not make longer is the first. A new
        // colony opens on a decision it can see the end of: round 8 measured the whole change at an
        // identical 25 building levels after 48 hours, so the price of covering the gaps is paid at
        // depth rather than at the door.
        val first = PlaceholderBalance
            .upgradeDuration(BuildingType.METAL_MINE, BuildingLevel(2), BuildingLevel(0))
        assertTrue(first.inWholeMinutes in 5..60, "the first upgrade was ${first.inWholeMinutes} minutes")
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
