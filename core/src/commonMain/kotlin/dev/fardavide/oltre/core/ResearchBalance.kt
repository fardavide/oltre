package dev.fardavide.oltre.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

// DECIDED balance, not placeholders — unlike `PlaceholderBalance` next door, every number here
// comes from the 0.1 research decision sheet Davide approved, and `ResearchBalanceTest` pins its
// three published tables value by value. Changing one of these is a design change.
//
// The shape of the branch, in one paragraph: three technologies behind one shared gate, each a
// single multiplier on a per-hour rate the simulation already computes, each open-ended. Costs
// compound at the same +50% per level as the buildings so the game has one cost curve rather than
// two, and lean on deuterium — in 0.1 nothing else wants it except Robotics and Nanite, so without
// research it accumulates unspent. Duration rides the Robotics Factory, which is what answers
// "where does research happen" without a seventh building.
//
// Two of the sheet's five open calls are recorded rather than settled, because they change nothing
// until Davide answers them: whether effects should be linear instead of compounding (they
// compound here — with a linear effect and an exponential cost, payback doubles every level and
// the branch is dead by level 4), and whether Automation, the deferred fourth technology, joins in
// 0.2. The third is settled and worth knowing: the sheet's Robotics divisor is deliberately
// gentler than the one `PlaceholderBalance.upgradeDuration` uses for construction — see
// `researchDuration`.
object ResearchBalance {

    // Effects are integers in parts-per-million of "no effect", so a multiplier stays exact
    // integer arithmetic all the way into the accrued stock. A rate is multiplied by the value
    // and divided by this basis.
    const val MULTIPLIER_BASIS: Long = 1_000_000

    // Cost compounds +50% per level, exactly as every building does.
    private const val COST_GROWTH_NUMERATOR: Long = 3
    private const val COST_GROWTH_DENOMINATOR: Long = 2

    // The Robotics Factory shortens research by 8% of its level: duration is
    // base minutes x level / (1 + 0.08 x Robotics), which is 25 / (25 + 2 x Robotics) in integers.
    //
    // This is deliberately NOT the divisor construction uses (`/ (1 + Robotics)`, which halves a
    // build at Robotics 1). The sheet flagged the mismatch and Davide called it: research keeps
    // the gentle curve its published tables were computed against, and construction keeps the
    // steep one the 0.0.8 balance round settled. Making the two agree is a rebalance of the
    // colony, not of this branch.
    private const val RESEARCH_ROBOTICS_NUMERATOR: Int = 25
    private const val RESEARCH_ROBOTICS_PER_LEVEL: Int = 2

    fun requirementFor(technology: Technology): ResearchRequirement = when (technology) {
        // Robotics 1 is already the deuterium wall, so reusing it adds no concept and costs no
        // explanation: the player who has earned their first deuterium finds a new tab live.
        Technology.PHOTOVOLTAICS -> ResearchRequirement.Facility(BuildingType.ROBOTICS_FACTORY, BuildingLevel(1))
        Technology.EXTRACTION -> ResearchRequirement.Facility(BuildingType.ROBOTICS_FACTORY, BuildingLevel(1))
        // A second gate so the branch opens on one decision rather than three, and so a locked row
        // appears in normal play rather than only before the first gate.
        Technology.ENRICHMENT -> ResearchRequirement.Tech(Technology.EXTRACTION, TechLevel(3))
        // Behind Extraction rather than beside it, for the reason Enrichment is: the branch opens on
        // one decision rather than three, and a fourth row live at Robotics 1 would make it four.
        // Level 1 rather than Enrichment's 3, because this is the away half of the same idea and a
        // chain a player can say out loud — pull more out of your own ground, then out of anyone's.
        Technology.PROSPECTING -> ResearchRequirement.Tech(Technology.EXTRACTION, TechLevel(1))
    }

    // What a level is worth, in parts of MULTIPLIER_BASIS. Compounds per level in the same curve
    // family as the buildings, floored at every step so it stays defined however deep a level is.
    fun multiplier(technology: Technology, level: TechLevel): Long =
        compound(MULTIPLIER_BASIS, level.value, technology.effectNumerator(), technology.effectDenominator())

    // The same number as a percentage, which is what the row shows: "+36% -> +47%". Rounded half
    // up, which is how the sheet's EFFECT column is written.
    fun effectPercent(technology: Technology, level: TechLevel): Int =
        (((multiplier(technology, level) - MULTIPLIER_BASIS) * 100 + MULTIPLIER_BASIS / 2) / MULTIPLIER_BASIS).toInt()

    // ── The opening discount reaches the branch too ──────────────────────────────────────────
    //
    // The published table below is still the design and `ResearchBalanceTest` still asserts it row
    // by row; what changed on 2026-08-09 is that the table is now the **full** price, and the first
    // three levels are sold below it. Davide's call, which is the only kind of call that may move
    // these numbers: *"Everything must be cheaper and quicker across the board, until first
    // expedition."*
    //
    // **Four, because the branch opens at Robotics 1 and the discount ends at Robotics 4.** Between
    // those two the colony gets through two or three technology levels, so a ramp any longer would
    // still be running when the galaxy opened and any shorter would be over before the branch was.
    // The window is genuinely small — that is what it means for a branch to open two thirds of the
    // way through the opening — and the discount is steepest exactly where the player meets it.
    private const val FULL_PRICE_LEVEL: Int = 4

    fun researchCost(technology: Technology, toLevel: TechLevel): Resources {
        require(toLevel.value in 1..TechLevel.MAX) {
            "research cost is only defined for levels 1..${TechLevel.MAX}, asked for $toLevel"
        }
        val steps = toLevel.value - 1
        val base = baseCost(technology)
        fun priced(resource: Long): Long = openingDiscount(
            exactGeometric(resource, steps, COST_GROWTH_NUMERATOR, COST_GROWTH_DENOMINATOR),
            toLevel.value,
            FULL_PRICE_LEVEL,
        )
        return Resources.of(
            metal = priced(base.metal),
            crystal = priced(base.crystal),
            deuterium = priced(base.deuterium),
        )
    }

    fun researchDuration(
        technology: Technology,
        toLevel: TechLevel,
        roboticsFactory: BuildingLevel,
    ): Duration {
        val base = when (technology) {
            Technology.PHOTOVOLTAICS -> 60
            Technology.EXTRACTION -> 90
            Technology.ENRICHMENT -> 150
            Technology.PROSPECTING -> 120
        }
        // "Cheaper **and quicker**" — and a building got the second half for nothing, because round
        // 11 made its duration a function of its cost. This one is a table times a level, so it has
        // to be told, and it is told on the base minutes *before* the Robotics divisor so the two
        // reductions compose the way they read: the discount is the opening, the divisor is the
        // factory.
        val minutes = openingDiscount((base * toLevel.value).toLong(), toLevel.value, FULL_PRICE_LEVEL)
        return minutes.minutes * RESEARCH_ROBOTICS_NUMERATOR /
            (RESEARCH_ROBOTICS_NUMERATOR + RESEARCH_ROBOTICS_PER_LEVEL * roboticsFactory.value)
    }

    // Photovoltaics 1.10, Extraction 1.08, Enrichment 1.14. Enrichment climbs fastest because
    // deuterium output is a tenth of metal in absolute terms; Extraction climbs slowest because it
    // moves two resources at once.
    private fun Technology.effectNumerator(): Long = when (this) {
        Technology.PHOTOVOLTAICS -> 11
        Technology.EXTRACTION -> 27
        Technology.ENRICHMENT -> 57
        // 1.10 a level. **A STARTING VALUE, owed to the sweep** the sheet's §9 makes a merge
        // condition: it is the one constant here that has never been measured against a fourteen-day
        // run, and it accelerates depletion as well as paying for it.
        Technology.PROSPECTING -> 11
    }

    private fun Technology.effectDenominator(): Long = when (this) {
        Technology.PHOTOVOLTAICS -> 10
        Technology.EXTRACTION -> 25
        Technology.ENRICHMENT -> 50
        Technology.PROSPECTING -> 10
    }

    // Metal and crystal are here to make the cost chips mean something; deuterium is the price.
    // Enrichment is crystal-heavier than the others so it is not simply Extraction's little
    // brother, and Photovoltaics is cheapest of the three by design — it is the first research
    // anyone can afford.
    private fun baseCost(technology: Technology): BaseCost = when (technology) {
        Technology.PHOTOVOLTAICS -> BaseCost(metal = 300, crystal = 150, deuterium = 100)
        Technology.EXTRACTION -> BaseCost(metal = 600, crystal = 400, deuterium = 200)
        Technology.ENRICHMENT -> BaseCost(metal = 500, crystal = 700, deuterium = 200)
        // Metal-led, which is the fleet's own house style — `SurveyBalance` and the hull curve both
        // lean on metal for the same reason, that it is the resource with nothing else to buy. The
        // deuterium column stays in line with the other three, because deuterium is what prices this
        // branch and a fleet technology is not an exception to that.
        Technology.PROSPECTING -> BaseCost(metal = 800, crystal = 300, deuterium = 200)
    }

    private data class BaseCost(val metal: Long, val crystal: Long, val deuterium: Long)
}
