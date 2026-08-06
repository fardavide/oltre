package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class EnergyTest {

    @Test
    fun `production runs at full rate while the solar plant covers consumption`() {
        // given
        val state = GameState.initial()

        // then
        assertTrue(
            PlaceholderBalance.energyProduction(state.buildings) >=
                PlaceholderBalance.energyConsumption(state.buildings),
            "initial solar plant must cover initial mines",
        )
        val start = Instant.fromEpochMilliseconds(0)
        val produced = advance(state, from = start, to = start + 1.hours).resources.metal - state.resources.metal
        assertEquals(PlaceholderBalance.metalProductionPerHour(state.buildings.metalMine), produced)
    }

    @Test
    fun `production scales down proportionally when energy is short`() {
        // given
        val start = Instant.fromEpochMilliseconds(0)
        val initial = GameState.initial()
        val hungry = initial.copy(
            buildings = initial.buildings.copy(metalMine = BuildingLevel(9)),
        )
        val produced = PlaceholderBalance.energyProduction(hungry.buildings)
        val consumed = PlaceholderBalance.energyConsumption(hungry.buildings)
        assertTrue(produced < consumed, "scenario must actually be energy-starved")

        // when
        val metalAfterHour = advance(hungry, from = start, to = start + 1.hours).resources.metal -
            hungry.resources.metal

        // then
        val fullRate = PlaceholderBalance.metalProductionPerHour(BuildingLevel(9))
        val expected = fullRate * produced / consumed
        assertEquals(expected, metalAfterHour)
        assertTrue(metalAfterHour < fullRate, "starved mine must under-produce")
    }

    @Test
    fun `an energy balance reports the rate every mine is actually running at`() {
        // given the colony from Davide's report: metal 3, crystal 2, deuterium 2, solar 1
        val buildings = Buildings(
            metalMine = BuildingLevel(3),
            crystalMine = BuildingLevel(2),
            deuteriumSynthesizer = BuildingLevel(2),
            solarPlant = BuildingLevel(1),
            roboticsFactory = BuildingLevel(0),
            naniteFactory = BuildingLevel(0),
        )

        // when
        val energy = PlaceholderBalance.energyBalance(buildings)

        // then
        assertEquals(50L, energy.produced)
        assertEquals(90L, energy.consumed)
        assertTrue(energy.isDeficit, "one solar plant cannot carry seven levels of mine")
        assertEquals(55, energy.outputPercent, "the player was losing 45% of every mine, unannounced")
    }

    @Test
    fun `a covered colony runs at full output with its surplus reported`() {
        // given
        val buildings = GameState.initial().buildings

        // when
        val energy = PlaceholderBalance.energyBalance(buildings)

        // then
        assertTrue(energy.isDeficit.not())
        assertEquals(100, energy.outputPercent)
        assertEquals(energy.produced - energy.consumed, energy.surplus)
    }

    @Test
    fun `a colony that consumes nothing is not a deficit`() {
        // given a razed colony — the ratio would divide by zero if it were computed blindly
        val buildings = Buildings(
            metalMine = BuildingLevel(0),
            crystalMine = BuildingLevel(0),
            deuteriumSynthesizer = BuildingLevel(0),
            solarPlant = BuildingLevel(0),
            roboticsFactory = BuildingLevel(0),
            naniteFactory = BuildingLevel(0),
        )

        // when
        val energy = PlaceholderBalance.energyBalance(buildings)

        // then
        assertTrue(energy.isDeficit.not())
        assertEquals(100, energy.outputPercent)
    }

    @Test
    fun `the reported percentage matches what the mines actually accrue`() {
        // given
        val start = Instant.fromEpochMilliseconds(0)
        val initial = GameState.initial()
        val hungry = initial.copy(buildings = initial.buildings.copy(metalMine = BuildingLevel(9)))
        val energy = PlaceholderBalance.energyBalance(hungry.buildings)

        // when
        val accrued = advance(hungry, from = start, to = start + 1.hours).resources.metal - hungry.resources.metal
        val full = PlaceholderBalance.metalProductionPerHour(BuildingLevel(9))

        // then the headline percentage is the same ratio the accrual uses, to within the
        // per-rate flooring that keeps whole-unit accrual exact
        assertEquals(energy.produced * 100 / energy.consumed, accrued * 100 / full)
    }

    @Test
    fun `a facility reports the energy it supplies at its own level`() {
        // then the solar plant is the only thing that supplies, and it supplies 50 a level
        assertEquals(150L, PlaceholderBalance.energySupply(BuildingType.SOLAR_PLANT, BuildingLevel(3)))
        assertEquals(0L, PlaceholderBalance.energySupply(BuildingType.METAL_MINE, BuildingLevel(3)))
    }

    @Test
    fun `the colony's production is the sum of what each facility supplies`() {
        // given
        val buildings = GameState.initial().buildings.copy(solarPlant = BuildingLevel(4))

        // then the per-facility figure the UI attributes with and the colony total cannot drift
        assertEquals(
            BuildingType.entries.sumOf { PlaceholderBalance.energySupply(it, buildings.levelOf(it)) },
            PlaceholderBalance.energyProduction(buildings),
        )
    }

    @Test
    fun `spare energy is counted in the facility levels it would buy`() {
        // given a new colony: 50 produced against 40 drawn, and the cheapest level draws 10
        val buildings = GameState.initial().buildings

        // then a bare surplus means nothing until you know what a level costs
        assertEquals(1L, PlaceholderBalance.energyHeadroomLevels(buildings))
    }

    @Test
    fun `headroom grows as the plant outruns the mines`() {
        // given the solar 4 / metal 5 / crystal 4 / deuterium 4 colony: 200 produced, 170 drawn
        val buildings = Buildings(
            metalMine = BuildingLevel(5),
            crystalMine = BuildingLevel(4),
            deuteriumSynthesizer = BuildingLevel(4),
            solarPlant = BuildingLevel(4),
            roboticsFactory = BuildingLevel(0),
            naniteFactory = BuildingLevel(0),
        )

        // then
        assertEquals(3L, PlaceholderBalance.energyHeadroomLevels(buildings))
    }

    @Test
    fun `a colony that exactly covers its draw has no headroom left`() {
        // given metal mine 2: 50 produced against 50 drawn
        val buildings = GameState.initial().buildings.copy(metalMine = BuildingLevel(2))

        // then
        assertEquals(0L, PlaceholderBalance.energyHeadroomLevels(buildings))
    }

    @Test
    fun `a colony already in deficit has no headroom to report`() {
        // given
        val buildings = GameState.initial().buildings.copy(metalMine = BuildingLevel(9))

        // then headroom is a healthy-state reading; in deficit the percentage is the reading
        assertEquals(0L, PlaceholderBalance.energyHeadroomLevels(buildings))
    }

    @Test
    fun `resources stop accruing at the storage cap`() {
        // given
        val start = Instant.fromEpochMilliseconds(0)
        val cap = PlaceholderBalance.STORAGE_CAPACITY
        val nearlyFull = GameState.initial().copy(
            resources = Resources.of(metal = cap - 1),
        )

        // when
        val after = advance(nearlyFull, from = start, to = start + 24.hours)

        // then
        assertEquals(cap, after.resources.metal)
    }

    @Test
    fun `capping preserves composability across splits`() {
        // given
        val start = Instant.fromEpochMilliseconds(0)
        val cap = PlaceholderBalance.STORAGE_CAPACITY
        val nearlyFull = GameState.initial().copy(
            resources = Resources.of(metal = cap - 1),
        )
        val t2 = start + 24.hours
        val oneShot = advance(nearlyFull, from = start, to = t2)

        // when
        val stepped = advance(advance(nearlyFull, from = start, to = start + 7.hours), from = start + 7.hours, to = t2)

        // then
        assertEquals(oneShot, stepped)
    }
}
