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

    // Each point of danger costs a tenth of the hold, so a fully exposed run keeps half of it. Sized
    // for legibility rather than for a curve: at 8% a two-hazard far world is a rounding error, and at
    // 15% the frontier is unreachable at every window.
    //
    // **Deterministic, and stated before the tap.** Nothing is rolled anywhere in this mechanic. The
    // permanent-loss pillar is deferred to the combat slice rather than delivered as a hidden
    // sub-1% catastrophe: at three dispatches a day that is either invisible, so it is not a pillar,
    // or it fires once, costs a week of fleet to something the player was never shown, and ends the
    // session. When `resolve(a, b, seed)` exists, danger stops taking cargo and starts taking hulls.
    private const val DANGER_PERCENT_PER_POINT: Long = 10
    private const val PERCENT: Long = 100

    // ── The hold ─────────────────────────────────────────────────────────────────────────────
    //
    // Priced units at the game's own 1 : 2 : 3, which is the convention the adaptation sheet's cost
    // table and `:sim:run`'s `priced()` already use — so a hold of 480 buys 480 metal or 240 crystal
    // and the choice is entirely about what the colony is short of rather than about which is bigger.
    //
    // **PROPOSED, NOT DECIDED, and the number most likely to be wrong.** Two corrections already
    // landed on it and both push down: the draft's "16% of a genesis colony" divided by a colony
    // 0.2.7 deleted, and it measured the *priced basket* rather than the chosen currency — taken as
    // crystal, 34 priced/hour is 47% of a genesis colony's crystal income, not 16% of everything. The
    // sim sweep in the sheet's §6 reads metal and crystal separately and is expected to land this
    // well below 40.
    const val EXTRACTION_PER_HOUR: Long = 40

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
        val kept = (PERCENT - DANGER_PERCENT_PER_POINT * danger).coerceAtLeast(0)
        var numerator = checkedTimes(ships.total.toLong(), EXTRACTION_PER_HOUR) { "cargo hulls" }
        numerator = checkedTimes(numerator, stationMinutes) { "cargo station" }
        numerator = checkedTimes(numerator, richnessOf(world, gathering).perMillion.toLong()) { "cargo richness" }
        numerator = checkedTimes(numerator, kept) { "cargo danger" }
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
}
