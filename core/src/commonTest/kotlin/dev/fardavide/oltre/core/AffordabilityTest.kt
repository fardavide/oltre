package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class AffordabilityTest {

    @Test
    fun `shortfall lists the resources the stock cannot cover`() {
        // given plenty of metal, nothing else, against a three-resource cost
        val stock = Resources.of(metal = 1_000)
        val cost = PlaceholderBalance.upgradeCost(BuildingType.ROBOTICS_FACTORY, BuildingLevel(1))

        // when
        val short = stock.shortfallOf(cost)

        // then
        assertEquals(setOf(ResourceKind.CRYSTAL, ResourceKind.DEUTERIUM), short)
    }

    @Test
    fun `shortfall is empty when the stock covers the cost`() {
        // given
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2))
        val stock = Resources.of(metal = cost.metal, crystal = cost.crystal)

        // when
        val short = stock.shortfallOf(cost)

        // then
        assertEquals(emptySet(), short)
    }

    @Test
    fun `time until affordable is zero when the stock already covers`() {
        // given
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2))
        val stock = Resources.of(metal = cost.metal, crystal = cost.crystal)

        // when
        val wait = timeUntilAffordable(stock, cost, Buildings.initial())

        // then
        assertEquals(Duration.ZERO, wait)
    }

    @Test
    fun `time until affordable is the largest per-resource deficit over its effective rate`() {
        // given an empty stock and initial buildings (90 metal + 30 crystal per hour);
        // metal mine → 2 costs 90 metal (60 minutes) and 22 crystal (44 minutes)
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2))

        // when
        val wait = timeUntilAffordable(Resources.of(), cost, Buildings.initial())

        // then
        assertEquals(60.minutes, wait)
    }

    @Test
    fun `time until affordable rounds up to the next millisecond`() {
        // given metal mine 7 (340/h, solar raised so energy stays whole): a 1-metal deficit
        // is 3,600,000 fine / 340 = 10,588.2ms
        val buildings = Buildings.initial()
            .withLevel(BuildingType.METAL_MINE, BuildingLevel(7))
            .withLevel(BuildingType.SOLAR_PLANT, BuildingLevel(2))

        // when
        val wait = timeUntilAffordable(Resources.of(), Resources.of(metal = 1), buildings)

        // then
        assertEquals(10_589.milliseconds, wait)
    }

    @Test
    fun `time until affordable is infinite when a needed resource has no production`() {
        // given no deuterium synthesizer, so deuterium never accrues
        val buildings = Buildings.initial().withLevel(BuildingType.DEUTERIUM_SYNTHESIZER, BuildingLevel(0))
        val cost = PlaceholderBalance.upgradeCost(BuildingType.ROBOTICS_FACTORY, BuildingLevel(1))
        val stock = Resources.of(metal = 400, crystal = 120)

        // when
        val wait = timeUntilAffordable(stock, cost, buildings)

        // then
        assertEquals(Duration.INFINITE, wait)
    }
}
