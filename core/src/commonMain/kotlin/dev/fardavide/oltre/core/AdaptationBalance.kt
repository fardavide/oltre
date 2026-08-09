package dev.fardavide.oltre.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

// DECIDED balance, not placeholders — the same standing `ResearchBalance` and `GalaxyBalance` have,
// and for the same reason: every number here comes from the 0.3 adaptation decision sheet, and
// `AdaptationBalanceTest` pins its published tables value by value. Changing one is a design change.
//
// The shape of the branch, in one paragraph: three ladders that widen the three tolerance bands and
// do nothing else, behind one shared gate, competing for the *same* empire-wide slot the applied
// branch uses — so every adaptation level is paid for in production levels you did not buy. What a
// level widens is not here: it was settled at 0.0.15 and lives in `GalaxyBalance`, because the
// tolerance bands are the map's business. What is here is what a level costs and how long it takes,
// which is all that was ever missing between a `Blocked` world and a way to unblock it.
//
// Separate from `ResearchBalance` rather than three more rows in it, because an adaptation level
// does not multiply a per-hour rate — it widens a band, in °C, in g, in atm — and the applied
// branch's whole vocabulary (a current percentage, a next percentage, a subject ending in "output")
// has nothing to say about that. See the sheet's §1.
object AdaptationBalance {

    // Cost compounds +50% per level, exactly as every building and every applied technology does.
    // The branch is the expensive one because its *base* is nearly twice Enrichment's, not because
    // its curve is different: the game has one cost curve rather than two.
    private const val COST_GROWTH_NUMERATOR: Long = 3
    private const val COST_GROWTH_DENOMINATOR: Long = 2

    // The longest project in the game — 1.6x Enrichment's 150 — and equal across the three ladders
    // for the same reason the priced costs are equal: nothing here may decide which ladder is
    // pushed first.
    private const val BASE_MINUTES: Int = 240

    // The gentle Robotics divisor 0.1 settled for research, `25 / (25 + 2 x Robotics)`, not
    // construction's steeper one. Adaptation is research and waits research's wait.
    private const val ROBOTICS_NUMERATOR: Int = 25
    private const val ROBOTICS_PER_LEVEL: Int = 2

    // The level of Robotics Factory that opens all three ladders. One shared gate, not three that
    // differ: three ladders are worth having because *which one you push first* is a real choice,
    // and a gate that opens one before another makes that choice for the player.
    //
    // Level 4 rather than the applied branch's level 1 so the branch opens *after* the player has
    // met the Galaxy screen and read a BLOCKED row — which is the order the sentence on that row
    // assumes. It adds no concept, and Robotics is a purchase they want anyway, because it shortens
    // every project including these.
    val GATE: BuildingLevel = BuildingLevel(4)

    fun requirementFor(technology: AdaptationTechnology): ResearchRequirement =
        ResearchRequirement.Facility(BuildingType.ROBOTICS_FACTORY, GATE)

    // ── The opening discount reaches this branch too, on the applied branch's schedule ───────
    //
    // The first cut of the ramp left these three at full price, on the argument that the landmark
    // *is* the moment they become buyable so their level 1 sits exactly on the boundary. That is
    // true and it produced a cliff: Enrichment 1 was sold at a third and Thermal 1 at full price, so
    // the step between the two branches went from the sheet's **1.9×** to **5.8×** — and the sheet's
    // whole argument for 4,800 is that adaptation costs *about twice* the priciest technology.
    // Davide: *"Adjust the Enrichment and Thermal matter."*
    //
    // Same `FULL_PRICE_LEVEL` as `ResearchBalance`, and that is the fix rather than a new number:
    // the two branches share the one research slot and are meant to be weighed against each other,
    // so they have to be on the same side of the discount at every level. At 4 they are, and the
    // ratio the sheet designed holds all the way down — 1.93 at level 1 and 1.92 at level 4 — where
    // any other choice would have preserved it at exactly one depth.
    //
    // The cost is that the discount now runs a little past the landmark, since a player buys these
    // levels from Robotics 4 onward. That is a soft edge instead of a cliff, and a soft edge is what
    // was asked for.
    private const val FULL_PRICE_LEVEL: Int = 4

    fun adaptationCost(technology: AdaptationTechnology, toLevel: TechLevel): Resources {
        require(toLevel.value in 1..TechLevel.MAX) {
            "adaptation cost is only defined for levels 1..${TechLevel.MAX}, asked for $toLevel"
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

    fun adaptationDuration(
        technology: AdaptationTechnology,
        toLevel: TechLevel,
        roboticsFactory: BuildingLevel,
    ): Duration = openingDiscount((BASE_MINUTES * toLevel.value).toLong(), toLevel.value, FULL_PRICE_LEVEL)
        .minutes * ROBOTICS_NUMERATOR /
        (ROBOTICS_NUMERATOR + ROBOTICS_PER_LEVEL * roboticsFactory.value)

    // **Each ladder is priced in the resource its own axis makes rich**, and the three cost exactly
    // the same when priced at the game's 1 : 2 : 3 — 4,800 each. Gravity makes heavy worlds and
    // heavy worlds are rich in metal, so Gravitic costs metal; pressure makes thick atmospheres and
    // those are rich in crystal; temperature makes cold worlds and those hold the deuterium the
    // research branch already made scarce, so Thermal is the hardest of the three to start.
    //
    // That is the whole design of this table. The ladder you can afford first is the one your colony
    // is already good at; the ladder that would fix the shortage you actually have is the one you
    // cannot yet pay for. The identical priced total is what keeps that a preference rather than a
    // right answer — strip the currencies away and there is nothing to choose between them.
    private fun baseCost(technology: AdaptationTechnology): BaseCost = when (technology) {
        AdaptationTechnology.THERMAL -> BaseCost(metal = 900, crystal = 600, deuterium = 900)
        AdaptationTechnology.GRAVITIC -> BaseCost(metal = 2_400, crystal = 900, deuterium = 200)
        AdaptationTechnology.ATMOSPHERIC -> BaseCost(metal = 850, crystal = 1_600, deuterium = 250)
    }

    private data class BaseCost(val metal: Long, val crystal: Long, val deuterium: Long)
}
