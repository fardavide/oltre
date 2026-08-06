package dev.fardavide.oltre.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

// PLACEHOLDER balance numbers — every value here is provisional until decided on the Notion
// page or by Davide. This object is the single place placeholders live; never scatter literals.
object PlaceholderBalance {
    // Flat placeholder cap per resource, in whole units — sized so every placeholder cost
    // curve stays reachable. The rule that raises it (storage building? mine-level-scaled?) is
    // an open design question for Davide.
    const val STORAGE_CAPACITY: Long = 10_000_000

    const val NANITE_ROBOTICS_REQUIREMENT: Int = 10

    // Level-1 hourly output, in whole units. Deliberately human-scale: a fresh colony makes
    // tens of units an hour, so a stock is a number the player reads rather than a wall of
    // digits, and an upgrade cost is something they can hold in their head.
    const val METAL_PRODUCTION_PER_HOUR: Long = 60
    const val CRYSTAL_PRODUCTION_PER_HOUR: Long = 30
    const val DEUTERIUM_PRODUCTION_PER_HOUR: Long = 15

    // Output compounds +25% per level, so an upgrade is a raise and never a doubling: level 10
    // out-produces level 1 by ~7x rather than 10x, and the difference between levels stays
    // legible. Cost compounds faster (+50%), which is what makes a deep level a decision
    // instead of an obvious yes — payback time grows with depth.
    private const val PRODUCTION_GROWTH_NUMERATOR: Long = 5
    private const val PRODUCTION_GROWTH_DENOMINATOR: Long = 4
    private const val COST_GROWTH_NUMERATOR: Long = 3
    private const val COST_GROWTH_DENOMINATOR: Long = 2

    // Beyond this the cost curve leaves human territory; the guard also keeps the fine-unit
    // conversion inside Long (nanite metal at level 40 is ~1.5e11 whole units, ~5.3e17 fine).
    private const val MAX_UPGRADE_LEVEL: Int = 40

    // A new colony opens on a decision, not on a wait: enough metal and crystal for the first
    // few mine levels. Deuterium is earned, never granted — it gates the Robotics Factory.
    fun startingResources(): Resources = Resources.of(metal = 500, crystal = 300)

    fun metalProductionPerHour(level: BuildingLevel): Long =
        productionPerHour(METAL_PRODUCTION_PER_HOUR, level)

    fun crystalProductionPerHour(level: BuildingLevel): Long =
        productionPerHour(CRYSTAL_PRODUCTION_PER_HOUR, level)

    fun deuteriumProductionPerHour(level: BuildingLevel): Long =
        productionPerHour(DEUTERIUM_PRODUCTION_PER_HOUR, level)

    // Energy: mines consume, the solar plant produces; on deficit every mine's effective
    // hourly rate is scaled by produced/consumed using integer division. The scaled rate is
    // itself the rule (an integer per level configuration), so accrual stays exact and the
    // composability property is untouched.
    //
    // Photovoltaics raises supply *before* the deficit ratio is computed, so a deficit now has two
    // answers with different shapes: build the plant (metal, now) or research it (deuterium, over
    // hours). Nothing else in the branch touches energy.
    fun energyProduction(buildings: Buildings, research: Research): Long =
        50L * buildings.solarPlant.value *
            ResearchBalance.multiplier(Technology.PHOTOVOLTAICS, research.photovoltaics) /
            ResearchBalance.MULTIPLIER_BASIS

    fun energyConsumption(buildings: Buildings): Long =
        10L * buildings.metalMine.value +
            10L * buildings.crystalMine.value +
            20L * buildings.deuteriumSynthesizer.value

    // The order of application is the rule, not an implementation detail: the building level curve
    // first, then the research multiplier, then the energy deficit last. So Extraction's bonus is
    // scaled down by a deficit exactly as the mine's own output is, rather than escaping it.
    fun effectiveMetalProductionPerHour(buildings: Buildings, research: Research): Long =
        scaleByEnergy(
            researched(metalProductionPerHour(buildings.metalMine), Technology.EXTRACTION, research),
            buildings,
            research,
        )

    fun effectiveCrystalProductionPerHour(buildings: Buildings, research: Research): Long =
        scaleByEnergy(
            researched(crystalProductionPerHour(buildings.crystalMine), Technology.EXTRACTION, research),
            buildings,
            research,
        )

    fun effectiveDeuteriumProductionPerHour(buildings: Buildings, research: Research): Long =
        scaleByEnergy(
            researched(deuteriumProductionPerHour(buildings.deuteriumSynthesizer), Technology.ENRICHMENT, research),
            buildings,
            research,
        )

    private fun researched(fullRate: Long, technology: Technology, research: Research): Long =
        fullRate * ResearchBalance.multiplier(technology, research.levelOf(technology)) /
            ResearchBalance.MULTIPLIER_BASIS

    private fun scaleByEnergy(fullRate: Long, buildings: Buildings, research: Research): Long {
        val produced = energyProduction(buildings, research)
        val consumed = energyConsumption(buildings)
        return if (produced >= consumed) fullRate else fullRate * produced / consumed
    }

    fun upgradeCost(building: BuildingType, toLevel: BuildingLevel): Resources {
        require(toLevel.value in 1..MAX_UPGRADE_LEVEL) {
            "upgrade cost is only defined up to level $MAX_UPGRADE_LEVEL, asked for $toLevel"
        }
        val steps = toLevel.value - 1
        val base = baseCost(building)
        return Resources.of(
            metal = compound(base.metal, steps, COST_GROWTH_NUMERATOR, COST_GROWTH_DENOMINATOR),
            crystal = compound(base.crystal, steps, COST_GROWTH_NUMERATOR, COST_GROWTH_DENOMINATOR),
            deuterium = compound(base.deuterium, steps, COST_GROWTH_NUMERATOR, COST_GROWTH_DENOMINATOR),
        )
    }

    fun upgradeDuration(
        building: BuildingType,
        toLevel: BuildingLevel,
        roboticsFactory: BuildingLevel,
    ): Duration {
        val base = when (building) {
            BuildingType.METAL_MINE -> (10 * toLevel.value).minutes
            BuildingType.CRYSTAL_MINE -> (12 * toLevel.value).minutes
            BuildingType.DEUTERIUM_SYNTHESIZER -> (20 * toLevel.value).minutes
            BuildingType.SOLAR_PLANT -> (8 * toLevel.value).minutes
            BuildingType.ROBOTICS_FACTORY -> (30 * toLevel.value).minutes
            BuildingType.NANITE_FACTORY -> (120 * toLevel.value).minutes
        }
        return base / (1 + roboticsFactory.value)
    }

    private fun productionPerHour(baseAtLevelOne: Long, level: BuildingLevel): Long =
        if (level.value == 0) {
            0
        } else {
            compound(baseAtLevelOne, level.value - 1, PRODUCTION_GROWTH_NUMERATOR, PRODUCTION_GROWTH_DENOMINATOR)
        }

    private fun baseCost(building: BuildingType): BaseCost = when (building) {
        BuildingType.METAL_MINE -> BaseCost(metal = 60, crystal = 15)
        BuildingType.CRYSTAL_MINE -> BaseCost(metal = 48, crystal = 24)
        BuildingType.DEUTERIUM_SYNTHESIZER -> BaseCost(metal = 225, crystal = 75)
        BuildingType.SOLAR_PLANT -> BaseCost(metal = 75, crystal = 30)
        BuildingType.ROBOTICS_FACTORY -> BaseCost(metal = 400, crystal = 120, deuterium = 200)
        BuildingType.NANITE_FACTORY -> BaseCost(metal = 20_000, crystal = 10_000, deuterium = 4_000)
    }

    private data class BaseCost(val metal: Long, val crystal: Long = 0, val deuterium: Long = 0)
}
