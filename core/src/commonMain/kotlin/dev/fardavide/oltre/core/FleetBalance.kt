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

    // **Halved from 10, and the halving is the change Davide asked for three times.** 2026-08-12:
    // *"travel towards far planes to be way more time consuming, and require upgraded fleets to get
    // there faster."* 2026-08-16: *"navigating distance takes way more time, without powered up
    // ships."* The first half of that sentence is this constant; the second half is `PROPULSION`.
    //
    // **5 is not an arbitrary "slower", it is a calibration.** Because `unitsPerMinute` is
    // `base x (1 + level)`, drive 1 lands on exactly 10 — so **drive 0 is half of what 0.14 flew at
    // and drive 1 is 0.14 to the minute.** That is what makes the technology read as an unlock rather
    // than as a bonus: the first level does not make the player faster than they were, it gives them
    // back the game they had, and everything past it is new ground.
    //
    // The cost of that, stated plainly because it is the thing an install has to judge: a colony
    // that has researched nothing has a map that is honestly smaller than it looks. Two galaxy hops
    // out and back is 36h 20m at drive 0, past the longest window there is, so `windowsFor` offers
    // nothing at all for the far end of the map until the first level lands. That is intended —
    // `exploration-rewards-sheet.md` §8.5 raised it as an open call and the answer is that the
    // frontier is *bought*, not given.
    private const val UNITS_PER_MINUTE_BASE: Long = 5

    // How far a hull covers in a minute, which is the only thing the drive touches. Routed through
    // `ResearchBalance.multiplier` rather than reading the level directly, for `extractionPerHour`'s
    // own reason one section down: **there is no second place for the effect to be forgotten**, and
    // the Research screen's percentage is read off the same function that flies the ship.
    fun unitsPerMinute(research: Research): Long =
        UNITS_PER_MINUTE_BASE *
            ResearchBalance.multiplier(Technology.PROPULSION, research.levelOf(Technology.PROPULSION)) /
            ResearchBalance.MULTIPLIER_BASIS

    // ── The clock a manifest flies on ────────────────────────────────────────────────────────
    //
    // **A hull's whole cost, in one integer.** A skiff is 1; a hauler is 2, which is the ship set's
    // *"half speed"* said once rather than as a base and a divisor that have to be kept in step. The
    // factor scales the base term **and** the distance term, so a hauler is `20 + 2u/U` against a
    // skiff's `10 + u/U` — which is the design's own formula, and it reproduces its frames to the
    // minute: 20m and 42m out and back at the doorstep, 1h 48m and 3h 36m at 69 systems out.
    //
    // **It is *about* double rather than exactly double**, and the difference is the base term. The
    // design's prose says exactly and its own frames say 2.1x at the doorstep, where the flat ten
    // minutes doubles but the distance rounds to nothing. Nothing turns on the exactness — what the
    // shape needs is that a manifest has **one** clock and that the slowest hull sets it, which is
    // true of any number of hulls drawn from two types.
    //
    // A `SCOUT` has no factor because it cannot be in a gathering manifest; `startRun` refuses it at
    // the door. Zero would be a division by nothing and one would be a lie about a hull that has no
    // clock at all, so the map simply has no entry and `maxOf` never sees one.
    private fun ShipType.flightFactor(): Int = when (this) {
        ShipType.SKIFF -> 1
        ShipType.HAULER -> 2
        ShipType.SCOUT, ShipType.ESCORT, ShipType.SETTLER -> 0
    }

    // **The slowest hull sets the clock for all of them**, which is what makes a run have one
    // `returnsAt` however mixed its manifest. One is the floor, so an empty or scout-only manifest
    // still answers with a flight rather than a division by zero — `cargo` is what refuses those,
    // and it refuses them by returning nothing rather than by raising.
    fun flightFactor(ships: Ships): Int =
        ships.counts.keys.maxOfOrNull { it.flightFactor() }?.coerceAtLeast(1) ?: 1

    // **`BASE_FLIGHT_MINUTES` is outside the division, so the drive is worthless next door and
    // transformative at the frontier.** A target five units away is ten minutes at every level there
    // is; another galaxy goes from 9h 10m to 4h 40m on the first one. That asymmetry is the whole
    // design — the technology pays exactly where the sheet wants the player to go.
    //
    // **The factor multiplies the numerator rather than dividing the denominator**, which is the same
    // arithmetic wherever `unitsPerMinute` is even and better where it is odd — and it is odd at
    // drive 0, where the base is 5. `20 + 2u/5` is the honest half-speed; `20 + u/(5/2)` would floor
    // the *speed* to 2 and make a hauler slower than the design says.
    fun flight(from: GalaxyCoordinate, to: GalaxyCoordinate, research: Research, ships: Ships): Duration {
        val factor = flightFactor(ships)
        return (BASE_FLIGHT_MINUTES * factor + factor * distanceUnits(from, to) / unitsPerMinute(research)).minutes
    }

    fun roundTrip(from: GalaxyCoordinate, to: GalaxyCoordinate, research: Research, ships: Ships): Duration =
        flight(from, to, research, ships) * 2

    // **The fast clock, for every reading that is not about a manifest.** The galaxy map's caption,
    // a world row's trailing chip and the ledger's sort all quote a round trip before any hulls are
    // chosen, and they have quoted the skiff's since 0.7.0 — so this keeps those numbers exactly
    // where they were rather than moving them for a hull the reader has not picked.
    //
    // **The design raised this and did not decide it**, and it is worth quoting because it is the
    // open call rather than an oversight: *"an unlabelled time that belongs to a hull nobody named…
    // Cheapest fix is to drop the reach from the header and leave `69 systems out · 440 units`, since
    // the sheet one tap away prints both. Raised, not decided."* Until Davide answers, these readings
    // stay the fast clock and this constant is what makes every one of them greppable.
    val FASTEST_HULL: Ships = Ships.of(ShipType.SKIFF, 1)

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

    // **These three are three readings of one flight and must be given the same research**, or the
    // sheet offers a rung whose station time it then prices differently. The drive parameter carries
    // no default for exactly that reason: every call site is a compile error until somebody looks at
    // it, which is how all four of them were found.
    //
    // The drive is also what makes a *narrowing* ladder into a teaching device that runs both ways.
    // A rung is absent rather than disabled when the trip does not fit, so a level bought is a
    // control reappearing on a world the player already knows — no copy required.
    fun windowsFor(
        from: GalaxyCoordinate,
        to: GalaxyCoordinate,
        research: Research,
        ships: Ships,
    ): List<Duration> {
        val floor = roundTrip(from, to, research, ships) + MINIMUM_STATION
        return WINDOWS.filter { it >= floor }
    }

    fun stationFor(
        from: GalaxyCoordinate,
        to: GalaxyCoordinate,
        window: Duration,
        research: Research,
        ships: Ships,
    ): Duration = window - roundTrip(from, to, research, ships)

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

    // **The two resources a hull can actually lift, as a type rather than as three `when` arms that
    // say "unreachable".** `cargo` takes a `ResourceKind` because that is what a `Run` stores and what
    // a save round-trips, and it has always refused deuterium at the door — but the refusal lived in a
    // `require` and then three separate `when`s downstream each had to carry a dead `DEUTERIUM` arm.
    // Dead in the exact sense that matters: unreachable by construction, so no test could ever reach
    // them, which is what `status.md` recorded as *"two `error("unreachable")` arms stay uncovered —
    // the fix is a type rather than a test"*.
    //
    // This is that type, kept private and kept small. It converts the guard into a mapping that
    // happens once: past `gathered()` the impossible case is not representable, so the arithmetic
    // below has nothing left to assert about it. The public signature, `Run.gathering` and the save
    // format are all untouched — the narrowing is entirely inside this object.
    private enum class Gathered(val pricePerUnit: Long) {
        METAL(1),
        CRYSTAL(2),
        ;

        fun richnessOf(world: World): Richness = when (this) {
            METAL -> world.traits.metalRichness
            CRYSTAL -> world.traits.crystalRichness
        }

        fun holding(whole: Long): Resources = when (this) {
            METAL -> Resources.of(metal = whole)
            CRYSTAL -> Resources.of(crystal = whole)
        }
    }

    // Null is "a hull cannot lift this", and the one caller turns it into the same
    // `IllegalArgumentException` the `require` used to raise, with the same message.
    private fun ResourceKind.gathered(): Gathered? = when (this) {
        ResourceKind.METAL -> Gathered.METAL
        ResourceKind.CRYSTAL -> Gathered.CRYSTAL
        ResourceKind.DEUTERIUM -> null
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
        val gathered = requireNotNull(gathering.gathered()) { "a run never gathers deuterium" }
        val stationMinutes = station.inWholeMinutes
        // **Berths rather than hulls**, which is the same number for a skiff-only manifest and the
        // whole of the hauler's side of the composition trade for any other. A manifest of nothing
        // but scouts has no berths at all and returns here, which is the `isEmpty` guard generalised
        // rather than replaced.
        val carrying = berths(ships)
        if (stationMinutes <= 0 || carrying <= 0) return Resources.of()
        val paid = PERCENT + DANGER_BONUS_PERCENT * danger.coerceAtLeast(0)
        var numerator = checkedTimes(carrying.toLong(), extractionPerHour(research)) { "cargo berths" }
        numerator = checkedTimes(numerator, stationMinutes) { "cargo station" }
        numerator = checkedTimes(numerator, gathered.richnessOf(world).perMillion.toLong()) { "cargo richness" }
        numerator = checkedTimes(numerator, paid) { "cargo danger" }
        val denominator = MINUTES_PER_HOUR *
            GalaxyBalance.RICHNESS_BASIS *
            PERCENT *
            gathered.pricePerUnit
        return gathered.holding(numerator / denominator)
    }

    // ── The smallest fleet that empties the vein — RETIRED 2026-08-21, REPLACED ──────────────
    //
    // `hullsToLift` lived here from 0.13.1 and answered *"the smallest fleet that takes everything
    // there is"* as a **hull count**. It had exactly one caller, the dispatch sheet's default, and it
    // stopped having a single answer the day the picker landed: four skiffs and one hauler lift the
    // same and cost different things, so *"smallest"* became a question about a **composition**.
    //
    // Claude Design saw it coming and said so — *"hullsToLift stops having one answer once four
    // skiffs and one hauler lift the same and cost differently. Widening this is the picker's slice,
    // not a tidy-up: what it should return is a composition, and which composition is a design
    // question rather than an arithmetic one."* That question is answered — fewest berths, hauler
    // first — so this is the widening rather than a deletion, and `smallestThatEmpties` below is what
    // it became.
    //
    // Recorded rather than silently dropped, for `FRONTIER_PERCENT`'s reason one section up: the
    // argument that produced it is still the right argument, and it is now carried in berths.

    // **The fewest berths that empty the vein, packed hauler-first** — Claude Design's third ruling,
    // *Twice the Flight*. Null when no manifest here can, which is the honest answer for a window
    // with no surface time and for a vein deeper than the whole pool: the caller sends everything.
    //
    // `candidates` is filtered by the caller rather than here, and that is the one constraint the
    // rule carries — *"it may never lock the rung it is defaulting to"* — expressed as the list it is
    // handed instead of as a second check it would have to repeat.
    fun smallestThatEmpties(
        candidates: List<ReachableManifest>,
        world: World,
        gathering: ResourceKind,
        remaining: Long,
        station: (Ships) -> Duration,
        danger: Int,
        research: Research,
    ): ReachableManifest? = candidates.firstOrNull { manifest ->
        val onStation = station(manifest.ships)
        cargo(world, gathering, manifest.ships, onStation, danger, research).of(gathering) >= remaining
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

    // ── The scout — DAVIDE'S CALL, 2026-08-21 ────────────────────────────────────────────────
    //
    // **200 metal / 50 crystal, and what sizes it is the genesis stock rather than the mid game.** A
    // colony owns no hulls (0.11.3) and this is the first thing it buys, so the constant is an
    // *opening* number capped by the 500 metal a colony wakes up with — which is what makes it the
    // one price in this object that a player meets before they have produced anything.
    //
    // 300 priced units against the skiff's 1,200: **a quarter of a skiff**, which is the legible half
    // of the number. The ratio is the part to defend. A scout that drifts towards a skiff stops being
    // the thing you buy before anything else, and the Galaxy tab — which 0.12.0 made the screen a
    // player lands on — goes back to having nothing behind it for the first two days.
    //
    // **It is deliberately not free, and not granted at genesis.** Davide, 2026-08-16: a colony owns
    // nothing and buys this first. A starter scout would have made the hull a formality rather than
    // the first decision, and the first decision is what the price is for.
    const val SCOUT_METAL: Long = 200
    const val SCOUT_CRYSTAL: Long = 50

    // **Two hulls have a price and the other three raise rather than guess.** Each of those waits on
    // exactly one design call — the hauler on slice 4's speed-against-hold axis, the escort on a
    // combat model, the settler on colonisation — and a plausible number invented here would be
    // indistinguishable, to every later reader, from one somebody chose.
    //
    // **There is no `alreadyOwned` parameter, deliberately.** Carrying one that every branch ignores
    // would leave the callers threading a fleet count through to a price that does not read it — and
    // the day somebody restores the curve, a live parameter is exactly how it comes back without a
    // decision. The day it *is* a decision, this signature is the thing that has to change.
    // ── The hauler — DAVIDE'S CALL, 2026-08-21, AND IT IS A RE-DECISION ──────────────────────
    //
    // **The 2026-08-10 ruling expired without being wrong.** It priced the hauler at 1,000 metal /
    // 250 crystal *on its own x1.5 curve*, against a skiff base of 80 / 20 — twelve and a half times
    // a skiff, which was the whole of its case: *"its entire case is price; 240 would delete the
    // skiff."* Two later calls took the ground out from under that number without touching it.
    // 0.9.0 raised the skiff base tenfold, and 0.10.1 deleted the compounding curve outright — so
    // 1,000 / 250 is now **1.25x a skiff for four berths**, which deletes the skiff instead. Same
    // failure shape round 27 named: *a constant derived from a rule carries the rule's premise, and
    // a later round can invalidate the premise without touching the constant.*
    //
    // **Three skiffs of price for four skiffs of hold.** The ratio is the decision and the absolute
    // number follows it: a 25% discount on hold, paid for in half speed and in putting a whole
    // window's cargo in one basket. Four would make the hauler strictly worse than the four skiffs
    // it replaces — same hold, half speed, and no splitting across targets — so nobody would buy
    // one; two would leave the skiff nothing but speed, which the drive is about to make more
    // valuable but not by enough to carry a hull on its own.
    const val HAULER_METAL: Long = 2_400
    const val HAULER_CRYSTAL: Long = 600

    // **The hulls a slice has actually given a job to, and the one list that says so.** `buildShips`
    // refuses anything outside it and the Shipyard draws a card for everything inside it — two
    // statements of one fact, which is why they may not be two lists.
    //
    // **They were two, and it shipped a hull nobody could buy.** At 0.15 this gained the scout and
    // the Shipyard's own copy list did not, so a colony that owns no hulls could not buy the one hull
    // that surveys: the Galaxy tab was dead for the whole game rather than for the first check-in.
    // Nothing in `core` could catch it — every test here calls the verb directly — so the guard is a
    // test in `:client:shipyard:presentation` holding its card list against this one.
    //
    // The **hauler joined at 0.15.0**, with the manifest picker — *Twice the Flight* — which is what
    // it was waiting for: until the dispatch sheet could send a two-hull manifest, a purchasable
    // hauler was a hull a player could own and never use.
    val FOR_SALE: Set<ShipType> = setOf(ShipType.SCOUT, ShipType.SKIFF, ShipType.HAULER)

    fun shipCost(type: ShipType): Resources = when (type) {
        ShipType.SCOUT -> Resources.of(metal = SCOUT_METAL, crystal = SCOUT_CRYSTAL)
        ShipType.SKIFF -> Resources.of(metal = HULL_BASE_METAL, crystal = HULL_BASE_CRYSTAL)
        ShipType.HAULER -> Resources.of(metal = HAULER_METAL, crystal = HAULER_CRYSTAL)
        ShipType.ESCORT, ShipType.SETTLER ->
            error("$type has no price until the slice that gives it a job")
    }

    // ── Berths — what a manifest can actually carry ──────────────────────────────────────────
    //
    // **The hold is counted in berths and not in hulls, and until the hauler those were the same
    // number.** `cargo` summed `ships.total` because every hull that could be sent had exactly one
    // berth; the ship set's own table has always said otherwise — *"HAULER: four berths of hold,
    // half speed"* — and this is where that stops being a comment.
    //
    // A `SCOUT` has none, which is what makes it not a fleet asset. It cannot reach `cargo` through
    // `startRun`, which refuses the manifest at the door, but the arithmetic must not be the thing
    // standing between a scout and a berth it does not have.
    //
    // The escort and the settler are zero **pending their slices** rather than by design: one of
    // them will have a hold and neither has a number. A hull with no price cannot be bought, so no
    // manifest can contain one, and the day either ships this line is what has to move with it.
    private fun ShipType.berthsEach(): Int = when (this) {
        ShipType.SKIFF -> 1
        ShipType.HAULER -> 4
        ShipType.SCOUT, ShipType.ESCORT, ShipType.SETTLER -> 0
    }

    fun berths(ships: Ships): Int = ships.counts.entries.sumOf { (type, count) -> type.berthsEach() * count }

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

    // Every manifest the idle pool can actually fly, ordered by hold.
    //
    // **Packed hauler-first, which is the design's default rule and not a convenience.** *"Fewest
    // berths, hauler first"* — so the hold climbs 1, 2, then 4, 5, 6 at one hauler and two skiffs,
    // because a hauler is four berths and it does not divide. The gaps are the point: a cell whose hold
    // is smaller than the stepper asked for says so before it is tapped, and tapping it clamps.
    //
    // **Skiffs are fungible and haulers are not**, which is why this enumerates hauler *counts* and lets
    // the skiffs fill in: you never care which skiff, and the design rejected a per-skiff control for
    // exactly that reason — *"false precision"*.
    //
    // Empty when the pool holds nothing that can gather, which is the honest answer for a colony whose
    // hulls are all out or all scouts. The sheet draws its refusal from that emptiness rather than from
    // a count.
    fun reachableManifests(idle: Ships): List<ReachableManifest> {
        val skiffs = idle.countOf(ShipType.SKIFF)
        val haulers = idle.countOf(ShipType.HAULER)
        val all = buildList {
            for (hauler in 0..haulers) {
                for (skiff in 0..skiffs) {
                    if (hauler == 0 && skiff == 0) continue
                    val counts = buildMap {
                        if (hauler > 0) put(ShipType.HAULER, hauler)
                        if (skiff > 0) put(ShipType.SKIFF, skiff)
                    }
                    val ships = Ships(counts)
                    add(
                        ReachableManifest(
                            ships = ships,
                            berths = berths(ships),
                            flightFactor = flightFactor(ships),
                        ),
                    )
                }
            }
        }
        // One entry per hold, and where two manifests carry the same berths the **hauler-first** one
        // wins — which at four berths is one hauler against four skiffs, and is the rule that keeps the
        // skiffs at home for the second target the sheet cannot see.
        return all
            .groupBy { it.berths }
            .map { (_, sameHold) -> sameHold.maxBy { it.ships.countOf(ShipType.HAULER) } }
            .sortedBy { it.berths }
    }
}

// **The one derived list the whole manifest picker falls out of** — Claude Design, *Twice the
// Flight*, 2026-08-21: *"The reachable manifests, ordered by berths, each with its round trip and
// its clamped cargo. The stepper is an index into it, the two cells are the first manifest of each
// clock, and the ladder is the same legality test the sheet already runs, once per rung, against the
// selected manifest."*
//
// It lives in `core` because every term in it is arithmetic — a pool, a berth count, a flight — and
// none of it is a rendering. What the sheet adds is which entry is selected and what the cells say.
data class ReachableManifest(val ships: Ships, val berths: Int, val flightFactor: Int)

// The one resource a run is out to fetch, read off a basket. Private because `Resources` is the
// game's one shape for a stock and a public reader would invite a second `when` per caller.
private fun Resources.of(kind: ResourceKind): Long = when (kind) {
    ResourceKind.METAL -> metal
    ResourceKind.CRYSTAL -> crystal
    ResourceKind.DEUTERIUM -> deuterium
}
