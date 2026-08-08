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
    //
    // Metal is produced 2.5:1 against crystal, tracking the 183:69 the *repeating* basket costs —
    // a level of Metal Mine, Crystal Mine and Solar Plant, which is what a session actually buys.
    //
    // This ratio has now been wrong in both directions, which is why `BalanceCurveTest` bounds it
    // from both. At 2:1 (before 0.0.12) metal was the permanent bottleneck. At 3:1 (0.0.12 to
    // 0.1.0) crystal was: the target was averaged over the whole early tree, including the
    // Robotics Factory and the Deuterium Synthesizer — the two most metal-heavy rows in the game,
    // and the two you buy a handful of times rather than every session. `:sim:run` measured what
    // that cost: 130 of a greedy week's 168 hours had a purchase blocked by crystal *alone*, and
    // the week closed holding 49,544 metal it had nothing to spend on against 1,410 crystal.
    const val METAL_PRODUCTION_PER_HOUR: Long = 90
    const val CRYSTAL_PRODUCTION_PER_HOUR: Long = 36
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
    // Both sides are linear, so the tension never escalates: one plant level buys five metal or
    // crystal levels at level 1 and at level 40. Solar output varies with nothing but
    // Photovoltaics — there is no OGame-style temperature or position modifier.
    //
    // Photovoltaics raises supply *before* the deficit ratio is computed, so a deficit has two
    // answers with different shapes: build the plant (metal, now) or research it (deuterium, over
    // hours). Nothing else in the research branch touches energy, which is why the multiplier sits
    // on this one term rather than anywhere downstream of it.
    fun energySupply(building: BuildingType, level: BuildingLevel, research: Research): Long = when (building) {
        BuildingType.SOLAR_PLANT ->
            50L * level.value *
                ResearchBalance.multiplier(Technology.PHOTOVOLTAICS, research.photovoltaics) /
                ResearchBalance.MULTIPLIER_BASIS
        BuildingType.METAL_MINE,
        BuildingType.CRYSTAL_MINE,
        BuildingType.DEUTERIUM_SYNTHESIZER,
        BuildingType.ROBOTICS_FACTORY,
        BuildingType.NANITE_FACTORY,
        -> 0L
    }

    fun energyProduction(buildings: Buildings, research: Research): Long =
        BuildingType.entries.sumOf { energySupply(it, buildings.levelOf(it), research) }

    // Per building, so a caller can ask whether *this* facility is one of the ones a shortage
    // throttles rather than re-deriving the list. The colony total is the sum over the tree.
    fun energyConsumption(building: BuildingType, level: BuildingLevel): Long = when (building) {
        BuildingType.METAL_MINE -> 10L * level.value
        BuildingType.CRYSTAL_MINE -> 10L * level.value
        BuildingType.DEUTERIUM_SYNTHESIZER -> 20L * level.value
        BuildingType.SOLAR_PLANT,
        BuildingType.ROBOTICS_FACTORY,
        BuildingType.NANITE_FACTORY,
        -> 0L
    }

    fun energyConsumption(buildings: Buildings): Long =
        BuildingType.entries.sumOf { energyConsumption(it, buildings.levelOf(it)) }

    // Spare energy in the unit the player spends it in: how many further levels of the
    // cheapest-drawing facility the colony could power before it starts throttling. A bare
    // surplus figure is unreadable at a glance, because 10 energy means nothing until you know
    // what a level costs. Zero once the colony is in deficit — there the percentage is the
    // reading, and headroom has nothing left to say.
    fun energyHeadroomLevels(buildings: Buildings, research: Research): Long {
        val balance = energyBalance(buildings, research)
        val cheapestDrawPerLevel = BuildingType.entries
            .map { energyConsumption(it, BuildingLevel(1)) }
            .filter { it > 0 }
            .minOrNull()
        return if (balance.isDeficit || cheapestDrawPerLevel == null) 0 else balance.surplus / cheapestDrawPerLevel
    }

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

    fun energyBalance(buildings: Buildings, research: Research): EnergyBalance = EnergyBalance(
        produced = energyProduction(buildings, research),
        consumed = energyConsumption(buildings),
    )

    private fun researched(fullRate: Long, technology: Technology, research: Research): Long =
        fullRate * ResearchBalance.multiplier(technology, research.levelOf(technology)) /
            ResearchBalance.MULTIPLIER_BASIS

    private fun scaleByEnergy(fullRate: Long, buildings: Buildings, research: Research): Long {
        val energy = energyBalance(buildings, research)
        return if (!energy.isDeficit) fullRate else fullRate * energy.produced / energy.consumed
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
