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
    // nothing** — `openingDiscount` in `Curves.kt`, a third of full price at level 1 climbing in
    // equal steps to full price at `FULL_PRICE_LEVEL`. Above that the curve is the same curve it
    // has been since round 2, integer for integer.
    //
    // It is in `Curves.kt` rather than here because **all three cost tables answer to it**. Round
    // 13 shipped it on the buildings alone and Davide's correction was immediate — *"Everything
    // must be cheaper and quicker across the board"* — which was fair: `ResearchBalance` and
    // `AdaptationBalance` are separate objects with separate curves, and discounting a mine while
    // leaving a technology at full price is not a cheaper opening, it is a changed ratio between
    // the two. The applied branch now carries the same discount. The adaptation ladders do not, and
    // that is not an omission: the landmark *is* the moment they become buyable, so their level 1
    // sits exactly on the boundary where the discount has already run out.
    //
    // Two consequences that are the point rather than side effects:
    //
    // - **It buys the second and third verbs as well as the levels.** Every gate in the game is a
    //   Robotics Factory level and the Robotics Factory is discounted like everything else, so
    //   round 12's gate clock moves without touching a single gate — which round 12 measured as
    //   unreachable by any of the three levers aimed straight at it.
    // - **It shortens the early builds too, for free**, because round 11 made duration a function
    //   of cost. A third of the price is 0.58 of the clock, and no second constant had to move.
    //   Research had to be told separately, because its duration is a table rather than a function
    //   of what it costs.
    //
    // The growth *rate* inside the ramp is steeper than the ×1.5 outside it — between ×1.9 and
    // ×1.6, falling as the discount runs out. That is the cost of converging, and it is the right
    // place to pay it: the early levels are cheap in absolute terms even while climbing fast, and
    // the player who feels the slope is one who already has three verbs to spend on.
    // **Nine, because that is where a colony's mines stand when the galaxy opens.** The landmark
    // Davide named is Robotics Factory 4 — where a probe's findings become buyable — and `:sim:run`
    // puts the mines at level 8 or 9 at the moment that lands. So the mines reach full price and
    // the galaxy becomes actionable together, which is the sentence *"1x at the moment you can have
    // the first expedition"* turned into a level.
    //
    // The Robotics Factory rides the same schedule rather than converging at its own 4th level, and
    // that is deliberate: it is the building that opens the landmark, so keeping it cheap past the
    // landmark's own level is what brings the landmark forward.
    private const val FULL_PRICE_LEVEL: Int = 9

    fun upgradeCost(building: BuildingType, toLevel: BuildingLevel): Resources {
        val full = fullPriceCost(building, toLevel)
        fun priced(resource: Long): Long = openingDiscount(resource, toLevel.value, FULL_PRICE_LEVEL)
        return Resources.of(
            metal = priced(full.metal),
            crystal = priced(full.crystal),
            deuterium = priced(full.deuterium),
        )
    }

    // What the row costs with the ramp taken off — the curve as it stands from `FULL_PRICE_LEVEL`
    // upward, evaluated at any level. Split out because **the duration reads off this one and the
    // price reads off the discounted one**, which is the whole of round 16's construction change;
    // see `upgradeDuration`.
    //
    // `internal` rather than private only so `BalanceCurveTest` can state the duration rule against
    // it. That is the same standard the test already held the rule to — it read the *cost* off this
    // object and wrote the *root* out by hand — and the ramp is now written out by hand beside it.
    internal fun fullPriceCost(building: BuildingType, toLevel: BuildingLevel): Resources {
        require(toLevel.value in 1..MAX_UPGRADE_LEVEL) {
            "upgrade cost is only defined up to level $MAX_UPGRADE_LEVEL, asked for $toLevel"
        }
        val steps = toLevel.value - 1
        val base = baseCost(building)
        fun full(resource: Long): Long = compound(resource, steps, COST_GROWTH_NUMERATOR, COST_GROWTH_DENOMINATOR)
        return Resources.of(
            metal = full(base.metal),
            crystal = full(base.crystal),
            deuterium = full(base.deuterium),
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

    // Nothing is instant, however deep the Robotics Factory goes or however steep the opening
    // speed-up gets. **Two minutes rather than the five it was until 0.2.7**, and the number is
    // Davide's rather than the build's: *"I want a 2/3 min build time at the very first levels."*
    // Five would have been the answer to that question instead of the curve's, since the first
    // taps now land at two to five minutes and a five-minute floor would flatten the whole of the
    // opening's shape into one value. Two is the bottom of the band he named, so it bounds the
    // curve without becoming it.
    private val MINIMUM_UPGRADE_DURATION: Duration = 2.minutes

    // ── The clock gets its own ramp, because it was asked for in minutes ────────────────────────
    //
    // Round 13 shipped the opening discount and got the early builds shortened "for free", because
    // round 11 had made duration a function of cost. Free, and **wrong by exactly the square root**:
    // the price fell by the ramp's factor while the wait fell by its root, so inside the ramp a
    // build no longer took as long as earning it did — it took `1 / sqrt(discount)` times longer.
    // At the 3x that shipped, the opening's builds ran **1.73x long** against the income paying for
    // them; at 10x it would have been 3.16x. Which is to say the thing the ramp set out to speed up
    // was, in the one unit the player actually waits in, the part of the game furthest behind its
    // own rule. So the root is taken of `fullPriceCost` and a ramp applied to the **minutes**,
    // which is how `ResearchBalance` and `AdaptationBalance` have always done theirs.
    //
    // **The ramp it applies is not the price's.** Davide, 2026-08-09: *"I want a 2/3 min build time
    // at the very first levels, then 30min should be ok when you can use Galaxy ... we need to give
    // some adrenaline to users."* Two anchors in minutes, and the linear ramp cannot reach the
    // first — see `openingSpeedUp`, which is where the arithmetic for that is written down. So the
    // price recovers linearly from a tenth and the clock recovers geometrically at two thirds a
    // level, both converging on the same `FULL_PRICE_LEVEL` so there is still one landmark rather
    // than two.
    //
    // What that costs is worth stating plainly, because it is a rule of round 11's being bent on
    // purpose: inside the ramp a build is now deliberately *shorter* than the time it takes to earn
    // it — a fifth of it at level 2, closing to nine tenths by level 9. That is the adrenaline, in
    // the one number it can be measured in. Round 11's identity is untouched where it was aimed,
    // from `FULL_PRICE_LEVEL` up, and `BalanceCurveTest` now asserts the two halves separately
    // rather than averaging a deliberate divergence into a bound that admits it by accident.
    // ── The late game is where the wait belongs, and the Nanite Factory is what answers it ──────
    //
    // Davide, 2026-08-11: *"I'd expect late game upgrade to be extremely slow, and expensive Nanite
    // upgrades to make them reasonable. I still think a late game upgrade could take various hours,
    // even with Nanite. Implement it as such, considering that Nanite gets unlocked a bit late, so
    // let's not impact build times before then. It's reasonable for build times to be long only when
    // the user has many things to do: manage ships, travels, and co, not when it has only a few
    // things."*
    //
    // Three sentences, three constraints, and they pin the shape between them:
    //
    // - **long waits are earned by breadth**, not charged in advance — so nothing below the ramp
    //   moves, and the opening keeps every number round 16 gave it;
    // - **the ramp starts where the Nanite Factory does**, because a wait the player has no answer
    //   to is just a slower game;
    // - **and the answer is partial**. Hours, still. A Nanite that made deep levels quick would put
    //   the late game back where it is today with an extra 20,000-metal toll on the way.
    //
    // `LATE_GAME_FIRST_LEVEL` is measured rather than picked, the same way `FULL_PRICE_LEVEL` is —
    // it is the level a colony's mines stand at when Robotics reaches 10 and the Nanite Factory
    // becomes buildable, measured at 17, so the ramp opens one level after the answer to it does.
    // `BalanceBenchmark`'s `[opening]` section prints that measurement, so if the opening ever
    // speeds up or slows down this constant is wrong in a way the page says out loud.
    //
    // `internal` rather than private for the reason `fullPriceCost` is: so `BalanceCurveTest` can
    // state the rule against it rather than hard-coding the boundary and drifting from it silently.
    internal const val LATE_GAME_FIRST_LEVEL: Int = 18

    // Compounding, because the thing it is correcting compounds: without it the wait grows at the
    // root of a x1.5 curve, which is x1.2247 a level, and round 11 chose that precisely so a build
    // would track the time it takes to *earn* it. That identity is right for the mid-game and is
    // exactly what Davide is overruling above the ramp — so this is the amount by which the late
    // game is allowed to pull away from its own income, per level, and nothing else.
    private const val LATE_GROWTH_NUMERATOR: Long = 5
    private const val LATE_GROWTH_DENOMINATOR: Long = 4

    private fun lateGameWait(minutes: Long, level: Int): Long =
        if (level <= LATE_GAME_FIRST_LEVEL) {
            minutes
        } else {
            compound(minutes, level - LATE_GAME_FIRST_LEVEL, LATE_GROWTH_NUMERATOR, LATE_GROWTH_DENOMINATOR)
        }

    // **Two thirds a level, which is `openingSpeedUp`'s own rational and deliberately so.** The game
    // now has two places where a building buys back time, and they are the two ends of it — the
    // opening speed-up hands back time the ramp took, and the Nanite hands back time the late game
    // took. One shape for both means a reader who has understood one has understood the other.
    //
    // Multiplicative rather than another term in the Robotics divisor, because the divisor is linear
    // and the thing it would be fighting is not: at Robotics 15 an additive Nanite worth three
    // Robotics levels each buys a fifth off the first level and a twentieth off the fifth, which is
    // a building that stops mattering exactly as the player finishes paying for it.
    private const val NANITE_SPEEDUP_NUMERATOR: Long = 2
    private const val NANITE_SPEEDUP_DENOMINATOR: Long = 3

    fun upgradeDuration(
        building: BuildingType,
        toLevel: BuildingLevel,
        roboticsFactory: BuildingLevel,
        naniteFactory: BuildingLevel,
    ): Duration {
        val fullPrice = fullPriceCost(building, toLevel)
        val fullMinutes = MINUTES_PER_ROOT_COST * rootOf(fullPrice.metal + fullPrice.crystal)
        val ramped = openingSpeedUp(fullMinutes, toLevel.value, FULL_PRICE_LEVEL)
        // Order is the rule, not an implementation detail. The late-game ramp reads the *unhelped*
        // wait, so how slow the late game is does not depend on which buildings the player happens
        // to own; then the Nanite takes its share of that; then the Robotics divisor takes its share
        // of what is left. Reversing the last two would change nothing, and reversing the first two
        // would make the ramp a function of the answer to it.
        val late = lateGameWait(ramped, toLevel.value)
        val helped = compound(late, naniteFactory.value, NANITE_SPEEDUP_NUMERATOR, NANITE_SPEEDUP_DENOMINATOR)
        // The floor is applied last, to what the player actually waits — not to the base before the
        // divisor. A Robotics Factory that shortens a build below the floor has bought all the
        // shortening there is; a floor placed ahead of it would let the divisor cut *through* the
        // minimum and put instant builds back at depth. The speed-up is inside it for the same
        // reason — at two thirds a level the first Metal Mine works out at a minute and a quarter.
        return maxOf(MINIMUM_UPGRADE_DURATION, helped.minutes / (1 + roboticsFactory.value))
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
