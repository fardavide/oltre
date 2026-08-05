package dev.fardavide.oltre.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

// PLACEHOLDER balance numbers — every value here is provisional until decided on the Notion
// page or by Davide. This object is the single place placeholders live; never scatter literals.
object PlaceholderBalance {
    // Flat placeholder cap per resource, in whole units — sized so every placeholder cost
    // curve (nanite L1 = 1M metal) stays reachable. The rule that raises it (storage building?
    // mine-level-scaled?) is an open design question for Davide.
    const val STORAGE_CAPACITY: Long = 10_000_000

    const val NANITE_ROBOTICS_REQUIREMENT: Int = 10

    const val METAL_PRODUCTION_PER_HOUR: Long = 3_600
    const val CRYSTAL_PRODUCTION_PER_HOUR: Long = 1_800
    const val DEUTERIUM_PRODUCTION_PER_HOUR: Long = 900

    // Linear placeholder curve; the real curve comes from Notion or sim tuning.
    fun metalProductionPerHour(level: BuildingLevel): Long =
        METAL_PRODUCTION_PER_HOUR * level.value

    fun crystalProductionPerHour(level: BuildingLevel): Long =
        CRYSTAL_PRODUCTION_PER_HOUR * level.value

    fun deuteriumProductionPerHour(level: BuildingLevel): Long =
        DEUTERIUM_PRODUCTION_PER_HOUR * level.value

    // Energy: mines consume, the solar plant produces; on deficit every mine's effective
    // hourly rate is scaled by produced/consumed using integer division. The scaled rate is
    // itself the rule (an integer per level configuration), so accrual stays exact and the
    // composability property is untouched.
    fun energyProduction(buildings: Buildings): Long = 50L * buildings.solarPlant.value

    fun energyConsumption(buildings: Buildings): Long =
        10L * buildings.metalMine.value +
            10L * buildings.crystalMine.value +
            20L * buildings.deuteriumSynthesizer.value

    fun effectiveMetalProductionPerHour(buildings: Buildings): Long =
        scaleByEnergy(metalProductionPerHour(buildings.metalMine), buildings)

    fun effectiveCrystalProductionPerHour(buildings: Buildings): Long =
        scaleByEnergy(crystalProductionPerHour(buildings.crystalMine), buildings)

    fun effectiveDeuteriumProductionPerHour(buildings: Buildings): Long =
        scaleByEnergy(deuteriumProductionPerHour(buildings.deuteriumSynthesizer), buildings)

    private fun scaleByEnergy(fullRate: Long, buildings: Buildings): Long {
        val produced = energyProduction(buildings)
        val consumed = energyConsumption(buildings)
        return if (produced >= consumed) fullRate else fullRate * produced / consumed
    }

    // Exponential placeholder curves: cost doubles per level, duration grows linearly.
    fun upgradeCost(building: BuildingType, toLevel: BuildingLevel): Resources {
        // 2^40 × the largest base (1e6) × FINE_PER_UNIT still fits in a Long; beyond that the
        // shift itself wraps, so reject before computing.
        require(toLevel.value <= 40) { "upgrade cost overflows beyond level 40, asked for $toLevel" }
        return when (building) {
        BuildingType.METAL_MINE -> Resources.of(
            metal = 60L shl (toLevel.value - 1),
            crystal = 15L shl (toLevel.value - 1),
        )
        BuildingType.CRYSTAL_MINE -> Resources.of(
            metal = 48L shl (toLevel.value - 1),
            crystal = 24L shl (toLevel.value - 1),
        )
        BuildingType.DEUTERIUM_SYNTHESIZER -> Resources.of(
            metal = 225L shl (toLevel.value - 1),
            crystal = 75L shl (toLevel.value - 1),
        )
        BuildingType.SOLAR_PLANT -> Resources.of(
            metal = 75L shl (toLevel.value - 1),
            crystal = 30L shl (toLevel.value - 1),
        )
        BuildingType.ROBOTICS_FACTORY -> Resources.of(
            metal = 400L shl (toLevel.value - 1),
            crystal = 120L shl (toLevel.value - 1),
            deuterium = 200L shl (toLevel.value - 1),
        )
        BuildingType.NANITE_FACTORY -> Resources.of(
            metal = 1_000_000L shl (toLevel.value - 1),
            crystal = 500_000L shl (toLevel.value - 1),
            deuterium = 100_000L shl (toLevel.value - 1),
        )
    }
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
}
