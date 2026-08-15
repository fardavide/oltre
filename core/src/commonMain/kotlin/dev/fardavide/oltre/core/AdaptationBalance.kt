package dev.fardavide.oltre.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

// DECIDED balance, not placeholders — the same standing `ResearchBalance` and `GalaxyBalance` have,
// and for the same reason: every number here comes from the 0.3 adaptation decision sheet, and
// `AdaptationBalanceTest` pins its published tables value by value. Changing one is a design change.
//
// The shape of the branch, in one paragraph: three ladders that widen the three tolerance bands and
// do nothing else, behind one shared gate, one at a time in **a slot of their own**. That last part
// is 0.12.1's and it is the sheet's §2 overruled: the branch competed for the applied branch's slot
// until then, so *"every adaptation level is paid for in production levels you did not buy"* — the
// sentence this header carried for eleven versions, and the one Davide's *"a queue each"* deletes.
// The numbers below have not moved with it; what a ladder costs is still the sheet's, measured
// against the old trade, and the first round after this lands is what says whether they should.
//
// What a level widens is not here: it was settled at 0.0.15 and lives in `GalaxyBalance`, because the
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
    // Level 2 rather than the applied branch's level 1 so the branch is still its own decision and
    // a locked row still appears in normal play, but the wait is a day rather than two.
    //
    // ── Why it came down from 4 (0.5.1) ────────────────────────────────────────────────────
    //
    // Round 6 chose 4 "so the branch opens after the player has met the Galaxy screen and read a
    // BLOCKED row", and pre-authorised this exact review in `balance-log.md` round 12: *"If the
    // gate turns out to sit far past the first BLOCKED screen, lowering it to 2 or 3 is cheaper
    // than re-pricing anything."* Davide then played it — *"I needed 2 day to get robotics to level
    // 4"* — and `printGateClock` agrees to the hour: Robotics 4 lands at **hour 33**, Robotics 2 at
    // **hour 12**.
    //
    // The ordering argument survives the move intact, and that is what makes 2 legal rather than
    // merely cheaper. Nothing gates the Galaxy tab: the home system is surveyed at genesis, so a
    // player has read a `BLOCKED` row before the first Robotics Factory exists. Round 6's clause
    // was about the *order* of two screens, and the order is the same at 2, at 4 and at 1.
    //
    // What 2 rather than 1 buys is the thing round 6's second sentence was really protecting —
    // Robotics 1 is already the applied branch's gate, so sharing it would open five rows at once
    // and delete the locked row from normal play entirely.
    //
    // Measured cost, over the census's first two days: refusals for an unmet **requirement** fall
    // 35.25% → 25.64% and refusals for the **price** rise 5.12% → 14.10%, which is the trade being
    // bought rather than a side effect — round 12's own reading is that *"a price is a curve, a
    // slot is a rule, a requirement is a gate — and only the first of those is fixed by tuning a
    // number."* Median *kinds* of action offered in the opening goes 3 → **4**, which rounds 8 and
    // 12 both concluded no number in `PlaceholderBalance` could reach. Crystal, which round 12
    // warned this lever leans on, is short at 331 hours of a fortnight's 336 against 320 before —
    // an 11-hour move, well inside the ~50-hour band round 12 says not to read as a signal.
    val GATE: BuildingLevel = BuildingLevel(2)

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
