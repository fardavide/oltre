package dev.fardavide.oltre.core

import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

// PLACEHOLDER balance, the standing `SurveyBalance` has and not the standing `GalaxyBalance` has:
// build-authored against a measured economy rather than lifted from a sheet Davide approved. **The
// shape is the part worth defending**, and `.claude/docs/fleet-sheet.md` argues it.
//
// **Nothing costs anything per run.** The hull is the cost and it is bought once — no fuel, no
// dispatch fee. Davide's call, 2026-08-10 ("No fuel this slice"), and it inherits `SurveyBalance`'s
// own argument for why distance may never be priced: a cost that grew with distance *"would tax the
// player who is away longest, which is the one thing the check-in loop must never do."*
//
// **What the player chooses is a window**, and flight eats it rather than extending it: a far world
// delivers fewer station-hours out of the same absence and has to be richer to be worth it.
//
// **There is no hold cap**, and the draft that had one was wrong. At a cap of twelve station-hours a
// once-a-day player earned 480 priced units against a twice-a-day player's 933 — the check-in loop
// taxing absence, which is precisely what Davide ruled out. Uncapped, every cadence lands within 4%
// and the small edge belongs to the player who checks in more often, which is the right direction
// and a rounding error in size.
object FleetBalance {

    // ── Distance: the galaxy sheet's §4 metric, implemented for the first time ────────────────
    //
    // Settled two slices ago and left unimplemented — *"Slice #7 picks seconds-per-unit and whether
    // fuel is a cost"* — and this is that slice. Pure integer arithmetic on bounded inputs, so it is
    // identical on the JVM and on Kotlin/Native; `core` compiles for both and a galaxy that differs
    // by a unit between platforms is a different map.
    //
    // **This is deliberately not `SurveyBalance.distanceUnits`, and the two will disagree.** A probe
    // is aimed at a *star*, so its metric is system-to-system and prices a galaxy hop at 250; a hold
    // is filled at a *world*, so this one is world-to-world and prices a galaxy hop at 2,700.
    // Folding them would re-time every shipped probe flight and redraw the reach band's hour marks,
    // which is a change to working, shipped behaviour bought for tidiness. Named here so the next
    // reader does not think it is an oversight; it is an open call in the sheet's §9.
    private const val GALAXY_HOP_UNITS: Int = 2_700
    private const val SYSTEM_HOP_BASE_UNITS: Int = 95
    private const val UNITS_PER_SYSTEM: Int = 5
    private const val UNITS_PER_SLOT: Int = 5

    fun distanceUnits(from: GalaxyCoordinate, to: GalaxyCoordinate): Int = when {
        from.galaxy != to.galaxy -> GALAXY_HOP_UNITS * abs(from.galaxy - to.galaxy)
        from.system != to.system -> SYSTEM_HOP_BASE_UNITS + UNITS_PER_SYSTEM * abs(from.system - to.system)
        else -> UNITS_PER_SLOT * abs(from.slot - to.slot)
    }

    // ── The clock ────────────────────────────────────────────────────────────────────────────
    //
    // `BASE_FLIGHT_MINUTES` is what makes a target in your own system real rather than instant, and
    // it is the same failure `SurveyBalance.BASE_MINUTES` exists to prevent: *"without it the nearest
    // targets would land inside the check-in that ordered them."*
    private const val BASE_FLIGHT_MINUTES: Int = 10
    private const val UNITS_PER_FLIGHT_MINUTE: Int = 10

    fun flight(from: GalaxyCoordinate, to: GalaxyCoordinate): Duration =
        (BASE_FLIGHT_MINUTES + distanceUnits(from, to) / UNITS_PER_FLIGHT_MINUTE).minutes

    fun roundTrip(from: GalaxyCoordinate, to: GalaxyCoordinate): Duration = flight(from, to) * 2

    // ── The window ladder ────────────────────────────────────────────────────────────────────
    //
    // Read straight off the measured cadence: 3h is the "every two or three hours" rhythm, 6h covers
    // the daytime gaps, 12h covers the nine-hour overnight with room, 24h is the once-a-day player,
    // and 1h is the run you start while still holding the phone.
    //
    // A rung is **absent** rather than disabled when the trip does not fit — which is the only way to
    // show "too far" without a dead control, and it means a narrowing ladder teaches distance before
    // any copy does.
    val WINDOWS: List<Duration> = listOf(1.hours, 3.hours, 6.hours, 12.hours, 24.hours)

    // A window has to leave enough time on the surface to be worth the trip. It is also what keeps
    // every run's duration strictly positive, which `advance` needs: a zero-duration job completing
    // at its own boundary would re-enter `advance` at the same instant and recurse forever.
    val MINIMUM_STATION: Duration = 20.minutes

    fun windowsFor(from: GalaxyCoordinate, to: GalaxyCoordinate): List<Duration> {
        val floor = roundTrip(from, to) + MINIMUM_STATION
        return WINDOWS.filter { it >= floor }
    }

    fun stationFor(from: GalaxyCoordinate, to: GalaxyCoordinate, window: Duration): Duration =
        window - roundTrip(from, to)

    // ── Danger ───────────────────────────────────────────────────────────────────────────────
    //
    // **Split where it is generated, and no row ever prints the sum** — Claude Design's call,
    // 2026-08-10, and the argument is epistemic rather than visual. The distance band is astronomy:
    // free, known from the first launch, and identical for all fifteen slots of a system. Hazards are
    // per-world and need a survey. A row printing `danger 2` cannot say which half it came from, and
    // on an unsurveyed world it would be claiming knowledge it does not have. So the band is stated
    // once under the system header and the hazards sit on the row carrying their own arithmetic; the
    // dispatch sheet, the only place the number is spent, states the sum and both sources.
    //
    // Both inputs already existed. Hazards are generated on 45% of worlds and were read by nothing
    // except a −0.05 yield penalty — the galaxy sheet put them there for slice #10 and this is the
    // first thing that consumes them. **No new generation, no new roll, no new save state, and no
    // distribution moved.**
    private const val NEAR_SYSTEMS: Int = 125

    fun distanceBand(from: GalaxyCoordinate, to: GalaxyCoordinate): Int = when {
        from.galaxy != to.galaxy -> 3
        from.system == to.system -> 0
        abs(from.system - to.system) <= NEAR_SYSTEMS -> 1
        else -> 2
    }

    // Your own system with no hazards is danger 0 — a completely safe, completely deterministic first
    // run. The home system holds ~4.75 worlds and ~55% of worlds carry no hazard, so almost every
    // colony gets one on day one. That is "close planets are less hostile" delivered in the only unit
    // a fleet can feel it in.
    fun danger(from: GalaxyCoordinate, world: World): Int =
        world.traits.hazards.size + distanceBand(from, world.at)

    // ── Danger pays ──────────────────────────────────────────────────────────────────────────
    //
    // **Each point of danger ADDS a third to the hold, and until 0.7.1 each point took a tenth away.**
    // Davide, 2026-08-12, having played it: *"I would expect that more challenging planets are even
    // more rewarding. We need to push users towards planets explorations, otherwise it is pointless,
    // now it not rewarding AT ALL."* `exploration-rewards-sheet.md` §2.1 argues it; the short version
    // is that the game charged you for going far and paid you for staying home, and the measured
    // consequence was the sim's bot sending **56 of 56 dispatches to band 0** while 276 of 283
    // surveyed worlds sat further out.
    //
    // `danger` itself is untouched — still `hazards.size + distanceBand`, still 0…5. Only the sign
    // moved, so a fully exposed frontier run is worth 2.75 holds where it used to keep half.
    //
    // **This is also why `FRONTIER_PERCENT` is gone rather than wired in.** It was ratified at 0.3.0
    // as `[100, 115, 155, 230]` and read by nothing; those are *break-even* constants derived against
    // the penalty this replaces, so they were arithmetic about a formula that no longer exists.
    // `danger` already contains `distanceBand`, so paying for danger pays for distance once — and two
    // multipliers for one thing is two places to keep consistent.
    //
    // **Deterministic, and stated before the tap.** Nothing is rolled anywhere in this mechanic. The
    // permanent-loss pillar is still deferred to the combat slice, so for now the word "danger" names
    // something that is purely good — a promise the game does not yet keep. When `resolve(a, b, seed)`
    // exists, danger stops paying cargo and starts taking hulls, and that is when it earns the name
    // back.
    private const val DANGER_BONUS_PERCENT: Long = 35
    private const val PERCENT: Long = 100

    // ── The frontier band — DECIDED 2026-08-10, DELETED UNBUILT 2026-08-12 ───────────────────
    //
    // `FRONTIER_PERCENT = [100, 115, 155, 230]` lived here from 0.3.0 to 0.7.1 and **was read by
    // nothing for the whole of its life**. It is gone rather than finally wired because round 21
    // inverted the danger term it was arithmetic about: those four numbers are the *break-even*
    // points that cancel a −10%-per-point penalty, and with danger paying +35% instead there is no
    // penalty left for them to cancel. Keeping them would have paid for distance twice, since
    // `danger` already contains `distanceBand`.
    //
    // Recorded rather than silently dropped, because it was a ratified decision and the argument that
    // produced it is still the right argument: metal and crystal richness are plain uniform draws, so
    // at equal richness the nearest world wins at every window and distance is pure cost unless
    // something pays for it. **Something now does** — see the danger bonus above.

    // ── The hold ─────────────────────────────────────────────────────────────────────────────
    //
    // Priced units at the game's own 1 : 2 : 3, which is the convention the adaptation sheet's cost
    // table and `:sim:run`'s `priced()` already use — so a hold of 480 buys 480 metal or 240 crystal
    // and the choice is entirely about what the colony is short of rather than about which is bigger.
    //
    // **MEASURED, not guessed — 40 was the draft's number and the sweep halved it.** `:sim:run`'s
    // `printFleetReport` swept {10, 20, 30, 40} against {40, 80, 140} metal of hull, and
    // `balance-log.md` round 17 has the grid. What decided it was not a guardrail: levels at 48h
    // never left 32–34 and Robotics 4 never left hour 33–34 at any candidate, so **nothing in the
    // guardrails constrains this choice at all.** Three readings did.
    //
    // 1. **A fleet-first player must not out-produce their own colony.** Buying hulls *before* the
    //    buildings rather than out of what is left takes the fleet's crystal from 31% of the colony's
    //    to **98.6%** at 40 — a fleet delivering as much crystal in 48 hours as everything else put
    //    together. At 20 the same aggressive player reaches 49%. **20 is the highest rate at which no
    //    purchase order makes the fleet the economy**, and a constant that is only safe if the player
    //    buys in the order the designer imagined is not safe.
    // 2. **Per hull it is legible.** At 20 one skiff on a 6h run brings home 1.7 hours of a genesis
    //    colony's crystal income — about 28% of a Crystal Mine while it is away, so three or four
    //    skiffs match the mine. At 40 that is 3.4 hours and ~55%, which is the "47%" the draft was
    //    already warned about by its own reviewer.
    // 3. **§3.5's frontier band is not built yet and multiplies this by up to ×2.30.** Sizing at 20
    //    leaves the frontier landing near an effective 46; sizing at 40 would put it at 92 the day
    //    slice 2 ships, which is a rebalance disguised as a feature.
    //
    // **TRIPLED TO 60 AT ROUND 21, SWEPT AT ROUND 22, AND 60 STANDS.** Davide, 2026-08-12: *"Just
    // adjust the rate, but I don't think a 20% is enough!"* — and, on being shown the sweep below
    // and a build that had taken it back to 20: *"Why did you revert the rate? Bring it back."*
    //
    // The rate went to 60 on the argument that **round 17's binding constraint could not be tripped
    // by any shipped player** — "a fleet-first player must not out-produce their own colony" was
    // measured against a bot owning six to nine hulls, and at 0.7.2 `buildShips` did not exist, so a
    // player owned the one skiff genesis granted and could never own two. That argument had an
    // expiry date written into it: *"this must be re-swept the day `buildShips` lands."* It landed
    // at 0.8.0, the sweep ran, and **the constraint it was waiting on now binds**:
    //
    // | rate | hulls from what is left | **hulls first** |
    // |---|---|---|
    // | 20 | 31.4% | **89.3%** |
    // | 30 | 47.2% | **134.0%** |
    // | 40 | 63.0% | **178.7%** |
    // | **60** | 94.5% | **268.1%** |
    //
    // **So round 17's criterion is not satisfied at 60, and it is overruled rather than met.** A
    // player who buys hulls before buildings at every check-in reaches a fleet delivering 2.7x their
    // own colony's crystal. That is a real reading and it is not a defect in the measurement — it is
    // the trade Davide has taken, knowing it, and it makes "the fleet must never be the economy" no
    // longer the constraint that sizes this number. The next round to touch the rate should argue
    // against *his* bar rather than reinstating round 17's by default.
    //
    // 60 is also the legible number rather than merely a bigger one: **one priced unit per hull per
    // station-minute.** A consequence worth knowing, because it moved a test — at 60 the priced hold
    // is exactly the station in minutes, so `hulls x RATE x minutes / 60` has no fraction left to
    // lose and the flooring hazard `FleetBalanceTest` pins lives entirely in the richness and danger
    // terms.
    //
    // **What to watch, since the guardrail is spent rather than intact.** Whether the fleet-first
    // player is a real player: the 268% assumes somebody buys hulls before the buildings at every
    // single check-in, and if nobody plays that way the honest column is the 94.5% one. A device
    // session is what says. If the mines start feeling optional, this is the number that did it.
    const val EXTRACTION_PER_HOUR: Long = 60

    // **What `Technology.PROSPECTING` multiplies, and the only rate in the game a fleet owns.** The
    // other three technologies multiply something the colony produces; this one multiplies what a
    // hull pulls out of a world it is standing on. It reaches every figure that quotes the rate —
    // the hold, the per-ship split, and `DepositBalance.workingTime` — through this one function, so
    // there is no second place for the multiplier to be forgotten.
    fun extractionPerHour(research: Research): Long =
        EXTRACTION_PER_HOUR *
            ResearchBalance.multiplier(Technology.PROSPECTING, research.levelOf(Technology.PROSPECTING)) /
            ResearchBalance.MULTIPLIER_BASIS

    private const val MINUTES_PER_HOUR: Long = 60

    private fun pricePerUnit(kind: ResourceKind): Long = when (kind) {
        ResourceKind.METAL -> 1
        ResourceKind.CRYSTAL -> 2
        ResourceKind.DEUTERIUM -> 3
    }

    private fun richnessOf(world: World, gathering: ResourceKind): Richness = when (gathering) {
        ResourceKind.METAL -> world.traits.metalRichness
        ResourceKind.CRYSTAL -> world.traits.crystalRichness
        ResourceKind.DEUTERIUM -> world.traits.deuteriumRichness
    }

    // **One rounding rule, here and on every screen: the whole expression divides once, at the end.**
    // Flooring the priced hold first and the richness after would drift a unit low against the figure
    // the dispatch sheet states, and the sheet states it to the unit — so the two would disagree about
    // a number the player was shown before they committed. It is the same discipline `advance` already
    // applies to accrual, and the reason a 3h run next door reads 132 and not 133.
    //
    // Every term is bounded — at most a few hulls, a 1,440-minute window, richness under 1.6e6 and a
    // percentage — so the numerator stays five orders of magnitude inside a Long. It goes through
    // `checkedTimes` anyway, because a curve in this codebase that multiplies directly is how a
    // negative cost got shipped once already.
    fun cargo(
        world: World,
        gathering: ResourceKind,
        ships: Ships,
        station: Duration,
        danger: Int,
        research: Research,
    ): Resources {
        require(gathering != ResourceKind.DEUTERIUM) { "a run never gathers deuterium" }
        val stationMinutes = station.inWholeMinutes
        if (stationMinutes <= 0 || ships.isEmpty) return Resources.of()
        val paid = PERCENT + DANGER_BONUS_PERCENT * danger.coerceAtLeast(0)
        var numerator = checkedTimes(ships.total.toLong(), extractionPerHour(research)) { "cargo hulls" }
        numerator = checkedTimes(numerator, stationMinutes) { "cargo station" }
        numerator = checkedTimes(numerator, richnessOf(world, gathering).perMillion.toLong()) { "cargo richness" }
        numerator = checkedTimes(numerator, paid) { "cargo danger" }
        val denominator = MINUTES_PER_HOUR *
            GalaxyBalance.RICHNESS_BASIS *
            PERCENT *
            pricePerUnit(gathering)
        val whole = numerator / denominator
        return when (gathering) {
            ResourceKind.METAL -> Resources.of(metal = whole)
            ResourceKind.CRYSTAL -> Resources.of(crystal = whole)
            ResourceKind.DEUTERIUM -> error("unreachable — guarded above")
        }
    }

    // ── The hull ─────────────────────────────────────────────────────────────────────────────
    //
    // **FLAT — DAVIDE'S CALL, 2026-08-14, AND IT REPLACES THE ONE CEILING THIS DESIGN HAD.** *"Why is
    // skiff pricing increasing at every buy? This is wrong."* A hull costs what a hull costs; the
    // fleet a player already owns is not an input to the price of the next one.
    //
    // The x1.5-per-hull curve lived here from 0.3.0 to 0.9.0 and the argument for it is worth keeping
    // in full, because it is the argument this decision overrules rather than one that stopped being
    // true: *"this curve is the fleet's ceiling, and it is why there is no Shipyard building. It
    // proves boundedness the way every ceiling in this game is proved — a compounding price against a
    // linear return — and it needs no seventh facility, no berth concept and no new noun."*
    //
    // **What actually broke it was the tenfold base at 0.9.0, and the break is arithmetic rather than
    // aesthetic.** The 0.9.0 call was about *which end* was too cheap — *"the x1.5 already bites by
    // the eighth hull, so what was free was the bottom of the curve"* — but a base multiplied by ten
    // multiplies every rung by ten, so the bite that was scheduled for the eighth hull arrived at the
    // second. The first hull a player could buy went to 1,200 priced and the fourth to 4,050, against
    // a Metal Mine 5 -> 6 at 444. A curve sized to bite late, scaled to bite immediately, is not the
    // curve anybody ratified.
    //
    // **So the fleet is now bounded by the yard rather than by the price** — the serial queue and
    // `buildDuration`, which no longer compounds either because it is taken from this price. That is
    // a real loss and it is the one to watch: a check-in with deep stores can buy as many hulls as it
    // can pay for, and only the slipway makes it wait. `balance-log.md` round 24 has what the sim says
    // about it; if the fleet starts out-producing the colony again, this paragraph is where it
    // started.
    //
    // **Metal-led, for `SurveyBalance`'s own reason** — *"metal is the resource with nothing to buy,
    // and this is the thing to buy with it."* The 1 : 4 crystal component is there so the fleet is not
    // entirely free of the scarce resource, and it is small enough never to compete with a ladder.
    // Deuterium is absent for the reason the payout excludes it: it is the Robotics gate's currency.
    //
    // **TENFOLD AT 0.9.0 — DAVIDE'S CALL, 2026-08-13, AND IT IS A FLOOR RATHER THAN A FIGURE.**
    // *"I think ships are WAY too cheap, considered the benefits they bring back"*, and, asked where
    // the increase should go: *"Raise the base, I'd say at least 10x the current price. As ships are
    // a single investment that pays back forever."*
    //
    // The base and not the exponent, on his call, and the reading says why that is the right end:
    // the x1.5 already bites by the eighth hull, and what was free was the *bottom* of the curve. At
    // 80/20 the second skiff cost 180 priced units against a hold of 60 priced units per hull per
    // station-hour — **three station-hours to repay a permanent asset**, which is half of one 6h
    // window. A genesis colony could buy it out of its opening stock before it had made anything.
    //
    // At 800/200 the second skiff is 1,800 priced and repays in **30 station-hours**: two or three
    // overnight runs, or a couple of days for the once-a-day player. That is the shape Davide's
    // sentence asks for — an investment that has to be earned before it pays forever — and it is the
    // first time in this object's life that the hull competes with a mine level on the reading the
    // sheet's own table used to lose.
    //
    // **The wait is the other half, and since 0.10.1 it is the only half.** See `buildDuration`: a
    // price the player cannot pay yet is a wait they serve without knowing it, and with the curve
    // gone the yard is what stops a check-in with full stores becoming a fleet. The balance log's
    // round 23 has the sweep that sized the base and round 24 has what removing the curve did to it.
    const val HULL_BASE_METAL: Long = 800
    const val HULL_BASE_CRYSTAL: Long = 200

    // **Only the skiff has a price this slice, and the other three raise rather than guess.** Each of
    // them waits on exactly one design call — the hauler on slice 4's speed-against-hold axis, the
    // escort on a combat model, the settler on colonisation — and a plausible number invented here
    // would be indistinguishable, to every later reader, from one somebody chose.
    //
    // **There is no `alreadyOwned` parameter, deliberately.** Carrying one that every branch ignores
    // would leave the callers threading a fleet count through to a price that does not read it — and
    // the day somebody restores the curve, a live parameter is exactly how it comes back without a
    // decision. The day it *is* a decision, this signature is the thing that has to change.
    fun shipCost(type: ShipType): Resources = when (type) {
        ShipType.SKIFF -> Resources.of(metal = HULL_BASE_METAL, crystal = HULL_BASE_CRYSTAL)
        ShipType.HAULER, ShipType.ESCORT, ShipType.SETTLER ->
            error("$type has no price until the slice that gives it a job")
    }

    // ── The yard clock — DAVIDE'S CALL, 2026-08-13 ───────────────────────────────────────────
    //
    // *"I think we need to add time to build ships, it shouldn't be instantaneous."*
    //
    // **This overrules §4's "Purchase is instant, and that is a sizing decision".** That section
    // argued the wait a hull costs you is the flight rather than the yard, and that the compounding
    // price was already the ceiling a timer would have been protecting. The price half of that
    // argument survives and is now much stronger — see `HULL_BASE_METAL` — but the sizing half does
    // not: a hull bought and dispatched inside one check-in is a fleet that grows as fast as the
    // stores allow, and 0.8.0 measured what that does. `printFleetReport`'s purchase-order bracket
    // put a fleet-first player's hulls at **268%** of their own colony's crystal income at 48 hours,
    // on a curve whose second rung a genesis colony can pay for out of its opening stock.
    //
    // **Four minutes per root of the hull's own price, divided by the Robotics Factory.** Both
    // halves are borrowed rather than invented: `PlaceholderBalance.MINUTES_PER_ROOT_COST` is the
    // colony's own rate, swept at round 11, so a hull and a facility that cost the same take the
    // same time to make; and the divisor is the same `1 + level` the facilities serve.
    //
    // **It used to compound and it does not any more**, because it is taken from a price that used to
    // compound: the root of a x1.5 curve grows at x1.2247 a hull, so the tenth skiff was a different
    // decision from the second. At a flat price every skiff is 2h 04m at Robotics 0, and the tenth is
    // the second again — nine of them in a row. That is the shape of a flat price, not a second
    // decision, and it is stated here because this is where a reader will look for the ceiling.
    //
    // Two consequences worth having in front of you. The **queue** is now the whole of the cost of
    // buying deep, so the serial rule in `buildShips` is load-bearing in a way it was not. And the
    // **floor** below is no longer unreachable: Robotics 25 divides 124 minutes to five.
    //
    // **Robotics divides it although nothing gates it**, which is not a contradiction: §4's "gated
    // by nothing" is about the *requirement* to buy, and it is untouched — a colony at Robotics 0
    // can order a hull in its first minute. What the factory buys is the answer to a wait the player
    // is already serving, which is the only role this game gives a building over a clock.
    //
    // The level is read at the order and never again, the rule every other job in the game follows:
    // *"a Robotics Factory finishing mid-flight must not retroactively shorten a build."*
    fun buildDuration(type: ShipType, roboticsFactory: BuildingLevel): Duration {
        val cost = shipCost(type)
        val minutes = PlaceholderBalance.MINUTES_PER_ROOT_COST * integerRoot(cost.metal + cost.crystal)
        // The floor is applied last, to what the player actually waits, for `upgradeDuration`'s own
        // reason: a floor ahead of the divisor would let the divisor cut through the minimum and put
        // instant hulls back at depth.
        return maxOf(MINIMUM_YARD_DURATION, minutes.minutes / (1 + roboticsFactory.value))
    }

    // **Reachable in play as of 0.10.1, which it was not before.** A hull is 2h 04m at Robotics 0 and
    // flat, so Robotics 25 divides it to exactly this and every level past that buys nothing — where
    // the compounding wait needed a factory past level 30 to touch the floor. The reason for the
    // floor is unchanged: a zero-duration job completes at its own boundary, re-enters `advance` at
    // the same instant and recurses forever, the same failure `MINIMUM_STATION` exists to prevent one
    // section up. It is what makes the yard queue's strictly-increasing invariant provable rather
    // than merely observed.
    val MINIMUM_YARD_DURATION: Duration = 5.minutes
}
