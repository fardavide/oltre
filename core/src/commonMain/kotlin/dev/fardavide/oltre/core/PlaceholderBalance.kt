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

    // ── The opening is on a discount, and the discount runs out ──────────────────────────────
    //
    // Davide, 2026-08-09, after round 11 shipped: *"I want the user to be able to gather resources
    // and build quickly the first 2/3/4 days ... Actually I don't want more resources, but cheaper
    // upgrades at the start."*
    //
    // Both halves of that sentence are load-bearing and they rule out the two obvious moves.
    // **Not more resources**, so the production curve is untouched — round 3 raised metal and round
    // 7 raised crystal, and a third income raise would inflate every payback in the game and undo
    // the ratio `BalanceCurveTest` pins against the repeating basket. **Cheaper at the *start***, so
    // not a lower base either: dividing `baseCost` would discount level 30 exactly as much as level
    // 1 and hand back the whole late game with it.
    //
    // What is left is the shape nobody had tried: **a discount on the early levels that decays to
    // nothing.** Full price is unchanged from `FULL_PRICE_LEVEL` upward — the deep curve is the
    // same curve it has been since round 2 — and below it every cost is multiplied by
    // (9/10)^(levels remaining to full price), which is ~0.35 at level 1 and climbs back to 1.
    //
    // Two consequences that are the point rather than side effects:
    //
    // - **It buys the second and third verbs as well as the levels.** Every gate in the game is a
    //   Robotics Factory level and the Robotics Factory is discounted like everything else, so
    //   round 12's gate clock moves without touching a single gate — which round 12 measured as
    //   unreachable by any of the three levers aimed straight at it.
    // - **It shortens the early builds too, for free**, because round 11 made duration a function
    //   of cost. A third of the price is 0.58 of the clock, and no second constant had to move.
    //
    // The growth *rate* inside the ramp is steeper than the ×1.5 outside it — ×1.667 a level, since
    // each step also gives back a tenth of the discount. That is the cost of converging, and it is
    // the right place to pay it: the early levels are cheap in absolute terms even while climbing
    // fast, and the player who feels the slope is one who already has three verbs to spend on.
    private const val FULL_PRICE_LEVEL: Int = 11
    private const val DISCOUNT_RECOVERY_NUMERATOR: Long = 9
    private const val DISCOUNT_RECOVERY_DENOMINATOR: Long = 10

    // `exactGeometric` rather than `compound`, and this is not a preference: flooring a tenth off a
    // small number ten times over is catastrophic where flooring a half off a large one is not.
    // The Metal Mine's 15 crystal comes out at 5 carried exactly and **2** floored per step, which
    // is a different game. Steps are bounded by `FULL_PRICE_LEVEL`, which is what keeps the exact
    // numerator inside Long — the bound `exactGeometric` documents and demands of every caller.
    private fun openingPrice(fullPrice: Long, toLevel: BuildingLevel): Long {
        val stepsToFullPrice = FULL_PRICE_LEVEL - toLevel.value
        if (stepsToFullPrice <= 0) return fullPrice
        return exactGeometric(
            fullPrice,
            stepsToFullPrice,
            DISCOUNT_RECOVERY_NUMERATOR,
            DISCOUNT_RECOVERY_DENOMINATOR,
        )
    }

    fun upgradeCost(building: BuildingType, toLevel: BuildingLevel): Resources {
        require(toLevel.value in 1..MAX_UPGRADE_LEVEL) {
            "upgrade cost is only defined up to level $MAX_UPGRADE_LEVEL, asked for $toLevel"
        }
        val steps = toLevel.value - 1
        val base = baseCost(building)
        return Resources.of(
            metal = openingPrice(compound(base.metal, steps, COST_GROWTH_NUMERATOR, COST_GROWTH_DENOMINATOR), toLevel),
            crystal = openingPrice(
                compound(base.crystal, steps, COST_GROWTH_NUMERATOR, COST_GROWTH_DENOMINATOR),
                toLevel,
            ),
            deuterium = openingPrice(
                compound(base.deuterium, steps, COST_GROWTH_NUMERATOR, COST_GROWTH_DENOMINATOR),
                toLevel,
            ),
        )
    }

    // A build takes about as long as **earning** it does. Round 10 said "as long as it costs" and
    // divided the metal-and-crystal sum by three, which is OGame's shape and reads well; round 11
    // is the correction, and the thing it corrects is a divergence rather than a level.
    //
    // Cost compounds at +50% a level. Production compounds at +25%. So a duration read straight
    // off the cost pulls away from the income that pays for it by x1.2 a level, from level one,
    // without bound: the Metal Mine's sixth level cost 3h 07m of building against 1h 50m of
    // earning, its eighth 7h 01m against 2h 39m, and its twentieth **911 hours against 24**. The
    // Robotics Factory's divisor was the only thing pushing back, and it is the one facility that
    // raises no rate, is priced in the slowest resource, and is therefore the one a player is most
    // likely not to have — `:sim:run` measured a colony that never bought it waiting 6h 48m for a
    // single tap on day two and finishing 48 hours two levels behind one that did.
    //
    // The root closes it, and the arithmetic is why rather than a coincidence: cost-over-income
    // grows at 1.5 / 1.25 = **x1.2** a level, and the square root of a x1.5 curve grows at
    // **x1.2247**. Cutting the duration from the root of the cost therefore tracks the time it
    // takes to earn the thing at *every* depth — 0.75 of it at level 3, 1.13 at level 20, with no
    // help from any building. `BalanceCurveTest` bounds that ratio on both sides, which is the
    // check round 10's shape could not have passed at any constant.
    //
    // Four rather than three or five: `:sim:run` swept the band and every value in it answers
    // Davide's complaint, so what the constant buys is how much of round 10's cover survives. At 3
    // the colony idles 85.4% of its opening, which is where it was *before* round 10 — the change
    // undone. At 5 the deepest tap on day two is back to 2h 55m for a player at Robotics 0, which
    // is the complaint. At 4 no repeating facility passes two hours before level 8, and 81.25% of
    // the opening still has the colony busy. Round 11 of `balance-log.md` has the sweep.
    //
    // Deuterium is deliberately outside the sum, as in OGame and as in round 10. It is the resource
    // that gates the Robotics Factory and therefore the whole research branch, and pricing *time*
    // in it too would make one scarcity govern two things the player has to trade off separately.
    private const val MINUTES_PER_ROOT_COST: Long = 4

    // Integer, and Newton's rather than `sqrt`: `core` is pure and must give the same answer on
    // every platform it compiles for, and a float root that lands a hair under a perfect square
    // would truncate to a different minute on one target than on another. Converges in a handful
    // of steps and is only ever called on a cost.
    private fun rootOf(value: Long): Long {
        if (value <= 0) return 0
        var root = value
        var next = (root + 1) / 2
        while (next < root) {
            root = next
            next = (root + value / root) / 2
        }
        return root
    }

    // Nothing is instant, however deep the Robotics Factory goes. At Robotics 10 a first mine level
    // divides to under three minutes, which is not a build — it is a tap with a delay on it, and it
    // would quietly undo at depth exactly the emptiness this curve exists to fill.
    private val MINIMUM_UPGRADE_DURATION: Duration = 5.minutes

    fun upgradeDuration(
        building: BuildingType,
        toLevel: BuildingLevel,
        roboticsFactory: BuildingLevel,
    ): Duration {
        val cost = upgradeCost(building, toLevel)
        val base = (MINUTES_PER_ROOT_COST * rootOf(cost.metal + cost.crystal)).minutes
        // The floor is applied last, to what the player actually waits — not to the base before the
        // divisor. A Robotics Factory that shortens a build below the floor has bought all the
        // shortening there is; a floor placed ahead of it would let the divisor cut *through* the
        // minimum and put instant builds back at depth.
        return maxOf(MINIMUM_UPGRADE_DURATION, base / (1 + roboticsFactory.value))
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
