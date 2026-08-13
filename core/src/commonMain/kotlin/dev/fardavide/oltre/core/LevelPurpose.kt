package dev.fardavide.oltre.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

// What one more level is worth to *this* colony, right now.
//
// A row has always stated a price and a wait. What it has never stated is whether the level is
// worth taking, and the one place the game already answers that — the adaptation shortlist,
// counted against the worlds this player has surveyed — is the proof the harder version is
// buildable. This is that answer generalised to the other twelve rows.
//
// `core` computes the verdict and the screen renders a sentence, exactly as the Galaxy row already
// works. Nothing here formats anything: every member is a number or a duration, and which words go
// round it is the screen's business, because the same fact reads differently on a plant and on the
// technology that multiplies it.
sealed interface LevelPurpose {

    // The level raises income. `kind` is the resource that gains the most of it — one resource,
    // because the row has one clause to say it in, and the largest gain is the one the level is
    // recognisably *for*.
    //
    // The pair is carried whole rather than only its difference: the row states the delta, and the
    // sheet states the two rates it came from, which are the numbers the verdict displaced.
    data class Output(
        val kind: ResourceKind,
        val from: Long,
        val to: Long,
        val payback: Duration,
    ) : LevelPurpose {
        val perHour: Long get() = to - from
    }

    // The level raises a supply this colony is not limited by, so it buys nothing today.
    // `mineLevelsSpare` is how much more draw it would take before that stopped being true, in the
    // unit the power indicator already reports headroom in.
    data class Inert(val suppliesMore: Long, val mineLevelsSpare: Long) : LevelPurpose

    // The level draws more power than the colony makes, so taking it would slow every mine it has —
    // the delta is not small, it is **negative**. `coveredAtPlantLevel` is the Solar Plant level
    // that would carry the new draw, which is the same sentence the power indicator's fix line
    // already writes, arriving one row earlier and before the money is spent.
    //
    // Not in the design's frames, and reachable on day one: at genesis one plant carries 50 against
    // 40 drawn, and a second Deuterium Synthesizer level draws 20 more. Without this member that
    // row falls through to `Unmeasured` and states nothing at all, which is the one outcome the
    // whole design exists to prevent.
    data class Throttled(val coveredAtPlantLevel: Int) : LevelPurpose

    // The level raises no rate at all: it shortens what you build. `on` is the longest build this
    // colony could start now, which is the one the wait is actually about.
    data class Sooner(val on: BuildingType, val before: Duration, val after: Duration) : LevelPurpose

    // The level raises no rate the colony owns: it raises what a hull pulls out of a world it is
    // standing on. **The first purpose in this game measured away from home**, and it exists because
    // the generic answer was actively wrong — `purposeOfRaise` reads the three effective production
    // rates, `Technology.PROSPECTING` moves none of them, so the row would have fallen through to
    // `Inert` and told a player the level "does nothing while you are in surplus". It does something;
    // it does it somewhere else.
    //
    // Quoted per hull per hour in priced units, which is the one figure that is a property of the
    // technology rather than of a fleet, a window or a world — the row has no target selected and
    // must not imply one.
    data class Haul(val from: Long, val to: Long) : LevelPurpose

    // There is no next level to price. Reached only at a ceiling — a facility at 40 or a
    // technology at 30 — where the row has no upgrade to offer either.
    data object Unmeasured : LevelPurpose
}

// The late-game wait and the answer to it, both stated from day one. This is the Nanite Factory's
// whole argument, and it has to read while the building is still twelve days out and 42% dim — so
// it is deliberately *not* about the next level of anything. It is about the shape of the curve.
data class DeepBuildRelief(val level: Int, val naniteLevel: Int, val unaided: Duration, val helped: Duration)

// The reference build the relief is quoted on: a level-30 Metal Mine, which is the brief's own
// example and roughly where a colony's mines stand when the gate opens.
private const val DEEP_BUILD_LEVEL: Int = 30

// Six levels, which is where the brief quotes the eleven-fold cut. Not the first level and not the
// last: one level is a rounding error against a 186-hour wait, and the ceiling is not a plan.
private const val NANITE_RELIEF_LEVEL: Int = 6

// **Quoted at the gate's Robotics level rather than at the colony's own, and that is the whole
// design of this function.** `upgradeDuration` divides by `1 + robotics` last, so a colony reading
// this on day one with no Robotics Factory would be told that a level-30 mine takes 2,982 hours —
// which is true, and useless: it is not a number anybody can hold, and it would fall by half every
// time an unrelated building went up, so the row's headline claim would churn while the building it
// describes stayed exactly the same.
//
// At Robotics 10 the figures are the ones a player will actually meet, because Robotics 10 is what
// the Nanite Factory costs to unlock — there is no state in which somebody builds one at Robotics 3.
// The relief is a fact about the building; what it would take *you* is the pointer underneath.
fun deepBuildRelief(): DeepBuildRelief {
    val atTheGate = BuildingLevel(PlaceholderBalance.NANITE_ROBOTICS_REQUIREMENT)
    return DeepBuildRelief(
        level = DEEP_BUILD_LEVEL,
        naniteLevel = NANITE_RELIEF_LEVEL,
        unaided = PlaceholderBalance.upgradeDuration(
            building = BuildingType.METAL_MINE,
            toLevel = BuildingLevel(DEEP_BUILD_LEVEL),
            roboticsFactory = atTheGate,
            naniteFactory = BuildingLevel(0),
        ),
        helped = PlaceholderBalance.upgradeDuration(
            building = BuildingType.METAL_MINE,
            toLevel = BuildingLevel(DEEP_BUILD_LEVEL),
            roboticsFactory = atTheGate,
            naniteFactory = BuildingLevel(NANITE_RELIEF_LEVEL),
        ),
    )
}

fun GameState.purposeOfNextLevel(building: BuildingType): LevelPurpose {
    val toLevel = BuildingLevel(buildings.levelOf(building).value + 1)
    if (toLevel.value > PlaceholderBalance.MAX_UPGRADE_LEVEL) return LevelPurpose.Unmeasured
    return when (building) {
        // The two buildings that raise nothing. What they are worth is measured in hours off a
        // build rather than in units per hour, which is the same grammar in a different unit.
        BuildingType.ROBOTICS_FACTORY -> sooner(subject = building, roboticsFactory = toLevel)
        BuildingType.NANITE_FACTORY -> sooner(subject = building, naniteFactory = toLevel)
        BuildingType.METAL_MINE,
        BuildingType.CRYSTAL_MINE,
        BuildingType.DEUTERIUM_SYNTHESIZER,
        BuildingType.SOLAR_PLANT,
        -> purposeOfRaise(
            raisedBuildings = buildings.withLevel(building, toLevel),
            raisedResearch = research,
            cost = PlaceholderBalance.upgradeCost(building, toLevel),
        )
    }
}

fun GameState.purposeOfNextLevel(technology: Technology): LevelPurpose {
    val next = research.levelOf(technology).value + 1
    if (next > TechLevel.MAX) return LevelPurpose.Unmeasured
    val toLevel = TechLevel(next)
    // The one technology whose payoff is not a rate this colony produces. Routed before
    // `purposeOfRaise` rather than inside it, because that function's whole method — diff the three
    // effective rates — cannot see this effect at all and would report a truthful zero about the
    // wrong quantity.
    if (technology == Technology.PROSPECTING) {
        return LevelPurpose.Haul(
            from = FleetBalance.extractionPerHour(research),
            to = FleetBalance.extractionPerHour(research.withLevel(technology, toLevel)),
        )
    }
    return purposeOfRaise(
        raisedBuildings = buildings,
        raisedResearch = research.withLevel(technology, toLevel),
        cost = ResearchBalance.researchCost(technology, toLevel),
    )
}

// The three effective rates are the ones `advance` accrues with, so a delta over them carries the
// research multipliers and the energy penalty for free — and gets the two cases that are easy to
// get wrong right without special-casing either. A Solar Plant level raises metal, crystal *and*
// deuterium while the colony is throttled; Photovoltaics raises exactly nothing while it is not.
private fun GameState.purposeOfRaise(
    raisedBuildings: Buildings,
    raisedResearch: Research,
    cost: Resources,
): LevelPurpose {
    val before = ResourceKind.entries.associateWith { effectiveRate(it, buildings, research) }
    val after = ResourceKind.entries.associateWith { effectiveRate(it, raisedBuildings, raisedResearch) }
    // Signed on purpose, and summed before it is judged: a mine level that tips the colony into
    // deficit raises its own rate and lowers the other two by more, and only the priced total says
    // so. Reading the row's own resource alone would sell that level as a gain.
    val pricedGain = ResourceKind.entries.sumOf { it.weight * (after.getValue(it) - before.getValue(it)) }
    if (pricedGain > 0) {
        val kind = ResourceKind.entries.maxBy { after.getValue(it) - before.getValue(it) }
        return LevelPurpose.Output(
            kind = kind,
            from = before.getValue(kind),
            to = after.getValue(kind),
            payback = paybackOf(cost, pricedGain),
        )
    }
    // Two ways to be worth nothing, and the difference between them is the whole of the advice: a
    // level that draws more power than it earns has made things worse, and one that does not has
    // only failed to make them better. Everything that raises no income falls into one or the
    // other — no row on either screen is ever left with nothing to say.
    val raisedDraw = PlaceholderBalance.energyConsumption(raisedBuildings)
    return if (raisedDraw > PlaceholderBalance.energyConsumption(buildings)) {
        LevelPurpose.Throttled(coveredAtPlantLevel = plantLevelCovering(raisedDraw, raisedResearch))
    } else {
        LevelPurpose.Inert(
            suppliesMore = PlaceholderBalance.energyProduction(raisedBuildings, raisedResearch) -
                PlaceholderBalance.energyProduction(buildings, research),
            mineLevelsSpare = PlaceholderBalance.energyHeadroomLevels(buildings, research),
        )
    }
}

// Linear in the level and never zero — one plant level supplies 50 before Photovoltaics and more
// after — so this always terminates on a real level rather than on a ceiling.
private fun plantLevelCovering(draw: Long, research: Research): Int {
    val perLevel = PlaceholderBalance.energySupply(BuildingType.SOLAR_PLANT, BuildingLevel(1), research)
    return ((draw + perLevel - 1) / perLevel).toInt()
}

// The game's own 1 : 2 : 3, per unit rather than per basket, so a signed delta can be priced
// without going through `Resources` — which cannot hold a negative.
private val ResourceKind.weight: Long
    get() = when (this) {
        ResourceKind.METAL -> 1
        ResourceKind.CRYSTAL -> 2
        ResourceKind.DEUTERIUM -> 3
    }

private fun effectiveRate(kind: ResourceKind, buildings: Buildings, research: Research): Long = when (kind) {
    ResourceKind.METAL -> PlaceholderBalance.effectiveMetalProductionPerHour(buildings, research)
    ResourceKind.CRYSTAL -> PlaceholderBalance.effectiveCrystalProductionPerHour(buildings, research)
    ResourceKind.DEUTERIUM -> PlaceholderBalance.effectiveDeuteriumProductionPerHour(buildings, research)
}

// In minutes rather than hours, because the shortest payback in the game is the one the research
// branch is sold on — 1.7 hours — and an integer hour would print it as "1h".
private fun paybackOf(cost: Resources, pricedGain: Long): Duration =
    (checkedTimes(cost.priced(), MINUTES_PER_HOUR) { "payback of $cost" } / pricedGain).minutes

private const val MINUTES_PER_HOUR: Long = 60

// The longest build this colony could start, and what one more level of `subject` takes off it.
//
// The subject is excluded from its own candidate list: a Robotics Factory does shorten the next
// Robotics Factory, but a row that answers "what is this worth" by naming itself is a row that has
// not answered. Anything gated or at its ceiling is excluded too — a build that cannot be started
// is not a wait anybody is serving.
private fun GameState.sooner(
    subject: BuildingType,
    roboticsFactory: BuildingLevel = buildings.roboticsFactory,
    naniteFactory: BuildingLevel = buildings.naniteFactory,
): LevelPurpose {
    val longest = BuildingType.entries
        .filter { it != subject && it.isStartableBy(buildings) }
        .maxByOrNull { waitFor(it, buildings.roboticsFactory, buildings.naniteFactory) }
        ?: return LevelPurpose.Unmeasured
    return LevelPurpose.Sooner(
        on = longest,
        before = waitFor(longest, buildings.roboticsFactory, buildings.naniteFactory),
        after = waitFor(longest, roboticsFactory, naniteFactory),
    )
}

private fun GameState.waitFor(
    building: BuildingType,
    roboticsFactory: BuildingLevel,
    naniteFactory: BuildingLevel,
): Duration = PlaceholderBalance.upgradeDuration(
    building = building,
    toLevel = BuildingLevel(buildings.levelOf(building).value + 1),
    roboticsFactory = roboticsFactory,
    naniteFactory = naniteFactory,
)

private fun BuildingType.isStartableBy(buildings: Buildings): Boolean {
    if (buildings.levelOf(this).value + 1 > PlaceholderBalance.MAX_UPGRADE_LEVEL) return false
    return this != BuildingType.NANITE_FACTORY ||
        buildings.roboticsFactory.value >= PlaceholderBalance.NANITE_ROBOTICS_REQUIREMENT
}

private fun Resources.amountOf(kind: ResourceKind): Long = when (kind) {
    ResourceKind.METAL -> metal
    ResourceKind.CRYSTAL -> crystal
    ResourceKind.DEUTERIUM -> deuterium
}

// The game's own 1 : 2 : 3, which is what `AdaptationBalance` prices its three deliberately-equal
// ladders at and what the balance benchmark has divided every payback by since it was written. A
// basket of three has to become one number before a cost and an hourly gain can be compared at
// all, and this is the ratio the game already believes in rather than a new one invented here.
internal fun Resources.priced(): Long = ResourceKind.entries.sumOf { it.weight * amountOf(it) }
