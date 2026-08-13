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
    ): Resources {
        require(gathering != ResourceKind.DEUTERIUM) { "a run never gathers deuterium" }
        val stationMinutes = station.inWholeMinutes
        if (stationMinutes <= 0 || ships.isEmpty) return Resources.of()
        val paid = PERCENT + DANGER_BONUS_PERCENT * danger.coerceAtLeast(0)
        var numerator = checkedTimes(ships.total.toLong(), EXTRACTION_PER_HOUR) { "cargo hulls" }
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
    // **This curve is the fleet's ceiling, and it is why there is no Shipyard building.** It proves
    // boundedness the way every ceiling in this game is proved — a compounding price against a linear
    // return — and it needs no seventh facility, no berth concept and no new noun. The mine is the
    // better rate buy permanently and by construction; the fleet is bought anyway, because
    // `startUpgrade` refuses a facility that is already building and a check-in that has tapped all
    // six has nowhere left to put its metal.
    //
    // **Metal-led, for `SurveyBalance`'s own reason** — *"metal is the resource with nothing to buy,
    // and this is the thing to buy with it."* The 1 : 4 crystal component is there so the fleet is not
    // entirely free of the scarce resource, and it is small enough never to compete with a ladder.
    // Deuterium is absent for the reason the payout excludes it: it is the Robotics gate's currency.
    //
    // PROPOSED, NOT DECIDED, like everything else in this object. The sweep is in the balance log.
    const val HULL_BASE_METAL: Long = 80
    const val HULL_BASE_CRYSTAL: Long = 20

    // The game's one cost curve, +50% a step, through `Curves.compound` so the flooring happens at
    // every step rather than once at the end — the rule the building and adaptation curves already
    // follow. Each component compounds on its own base, so the crystal column is a quarter of the
    // metal one only until the flooring separates them at the fifth hull.
    private const val HULL_COST_NUMERATOR: Long = 3
    private const val HULL_COST_DENOMINATOR: Long = 2

    // **Only the skiff has a price this slice, and the other three raise rather than guess.** Each of
    // them waits on exactly one design call — the hauler on slice 4's speed-against-hold axis, the
    // escort on a combat model, the settler on colonisation — and a plausible number invented here
    // would be indistinguishable, to every later reader, from one somebody chose.
    fun shipCost(type: ShipType, alreadyOwned: Int): Resources {
        require(alreadyOwned >= 0) { "a fleet cannot be negative, was $alreadyOwned" }
        return when (type) {
            ShipType.SKIFF -> Resources.of(
                metal = compound(HULL_BASE_METAL, alreadyOwned, HULL_COST_NUMERATOR, HULL_COST_DENOMINATOR),
                crystal = compound(HULL_BASE_CRYSTAL, alreadyOwned, HULL_COST_NUMERATOR, HULL_COST_DENOMINATOR),
            )
            ShipType.HAULER, ShipType.ESCORT, ShipType.SETTLER ->
                error("$type has no price until the slice that gives it a job")
        }
    }
}
