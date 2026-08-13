package dev.fardavide.oltre.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

// How deep a world is, and how slowly it comes back. `.claude/docs/deposit-sheet.md` is the design;
// this is its arithmetic.
//
// **Its own object rather than a section of `FleetBalance`, and that is not tidiness.** The hull
// prices in that file are another session's subject, and two sessions editing one object is a merge
// nobody needs. The split also states the shape of the mechanic: `FleetBalance` answers *how fast a
// fleet lifts*, this answers *how much there is to lift*, and the whole design turns on those two
// carrying the same multiplier.
object DepositBalance {

    // ── The cap ──────────────────────────────────────────────────────────────────────────────
    //
    // **Derived from a rule rather than chosen.** Davide, 2026-08-13, asked for a number and gave a
    // rule instead: *"I would expect a regular ship to take two rounds or a whole day to deplete a
    // planet, the ship capacity (a basic one) is never more than the planet resources."* One skiff on
    // a 24h run spends 1,418 minutes on the surface and lifts exactly 1,418 priced units, so the
    // smallest cap that never lets a basic hull overflow a world is 1,418 — and at 1,450 the doorstep
    // takes 0.99 days, or 2.0 runs at the 12h window. Both halves of the rule, to two decimals.
    //
    // **1,000 was his first answer and the sweep rejected it.** Below a single skiff's day the
    // deposit binds on essentially every dispatch, and when the deposit binds nothing else does: at
    // 1,000 a four-skiff fleet brings home the identical figure at 6h, 12h and 24h, which makes the
    // window ladder and the hull stepper ornamental. The sheet's 2.5 has the grid.
    const val BASE_PRICED: Long = 1_450

    // Priced at the game's own 1 : 2 : 3, the convention `FleetBalance.cargo` already spends — so a
    // world's metal and crystal deposits are worth the same and differ only by richness.
    private fun pricePerUnit(kind: ResourceKind): Long = when (kind) {
        ResourceKind.METAL -> 1
        ResourceKind.CRYSTAL -> 2
        ResourceKind.DEUTERIUM -> error("unreachable — guarded by cap()")
    }

    private const val PERCENT: Long = 100
    private const val DANGER_BONUS_PERCENT: Long = 35
    private const val MINUTES_PER_HOUR: Long = 60

    // **The cap carries the multiplier the rate carries, and that is the load-bearing line of the
    // whole design.** Time to strip a world is `cap / rate`; give the two different multipliers and
    // that ratio becomes a function of where the world is, so how long a planet lasts would depend on
    // where you are standing. With them matched it is the same everywhere on the map — which is what
    // lets the dispatch sheet teach the rule off its own legs line instead of out of a tooltip.
    //
    // It costs one thing, named in the sheet's 11 and accepted knowingly: `danger` contains
    // `distanceBand`, which is measured from *your* home, so two players sharing a world would
    // disagree about how much is in it. That is a multiplayer-day problem and the fix is to freeze
    // the band against something intrinsic. **Do not "tidy" this to hazards alone** — uniformity of
    // strip time is what pays for it.
    fun cap(world: World, gathering: ResourceKind, danger: Int): Long {
        require(gathering != ResourceKind.DEUTERIUM) { "a world holds no deuterium deposit" }
        require(danger >= 0) { "danger cannot be negative, was $danger" }
        val paid = PERCENT + DANGER_BONUS_PERCENT * danger
        var numerator = checkedTimes(BASE_PRICED, richnessOf(world, gathering).perMillion.toLong()) { "cap richness" }
        numerator = checkedTimes(numerator, paid) { "cap danger" }
        return numerator / (GalaxyBalance.RICHNESS_BASIS.toLong() * PERCENT * pricePerUnit(gathering))
    }

    private fun richnessOf(world: World, gathering: ResourceKind): Richness = when (gathering) {
        ResourceKind.METAL -> world.traits.metalRichness
        ResourceKind.CRYSTAL -> world.traits.crystalRichness
        ResourceKind.DEUTERIUM -> error("unreachable — guarded by cap()")
    }

    // ── Working time: the fourth segment of the legs line ─────────────────────────────────────
    //
    // The first minute at which this fleet has lifted everything that is there. Design put it on the
    // dispatch sheet — `out 10m · on station 11h 40m · working 6h 03m · home 10m` — because it is the
    // invariant made visible with no copy at all: `working` reads the same on the doorstep as in the
    // next galaxy, so the rule teaches itself.
    //
    // **Derived from `cargo`'s own expression rather than from a second rate.** `cargo(m)` is
    // `floor(m x K)`, so `cargo(m) >= remaining` for an integer `remaining` exactly when `m >= r / K`
    // — one ceiling division, and the two can never disagree about a minute. A second rate constant
    // here would be a second rounding convention, which is the defect `Curves.kt` exists to prevent.
    fun workingTime(
        world: World,
        gathering: ResourceKind,
        ships: Ships,
        danger: Int,
        remaining: Long,
        research: Research,
    ): Duration {
        require(gathering != ResourceKind.DEUTERIUM) { "a run never gathers deuterium" }
        require(remaining >= 0) { "a deposit cannot be negative, was $remaining" }
        if (remaining == 0L || ships.isEmpty) return Duration.ZERO
        val paid = PERCENT + DANGER_BONUS_PERCENT * danger.coerceAtLeast(0)
        val rate = FleetBalance.extractionPerHour(research)
        if (rate <= 0) return Duration.ZERO
        var numerator = checkedTimes(remaining, GalaxyBalance.RICHNESS_BASIS.toLong()) { "working remaining" }
        numerator = checkedTimes(numerator, PERCENT * pricePerUnit(gathering)) { "working price" }
        numerator = checkedTimes(numerator, MINUTES_PER_HOUR) { "working hour" }
        var denominator = checkedTimes(ships.total.toLong(), richnessOf(world, gathering).perMillion.toLong()) {
            "working fleet"
        }
        denominator = checkedTimes(denominator, paid) { "working danger" }
        denominator = checkedTimes(denominator, rate) { "working rate" }
        // Ceiled: a partial minute is still a minute the fleet is on the surface.
        return ((numerator + denominator - 1) / denominator).minutes
    }

    // ── Refill ───────────────────────────────────────────────────────────────────────────────
    //
    // Davide's number — *"perhaps 5% of the total per day"* — so twenty days from empty to full.
    // Slow on purpose: the point is that going back to the same world soon is never the answer.
    const val REFILL_PERCENT_PER_DAY: Long = 5

    private const val MILLISECONDS_PER_DAY: Long = 86_400_000

    // What a deposit holds now, given what was stored and how long ago. **Computed, never ticked** —
    // `advance` reads nothing here and writes nothing except the prune, so a deposit moves only when
    // a run is dispatched. That is what makes this exact as well as free: floors do not telescope, so
    // an accumulated version would drift a fine unit per span and two spans would not agree with one.
    // Here there is only ever one division, from the stored instant to now.
    //
    // **The stored value is clamped to the current cap**, which is what keeps `BASE_PRICED` a number
    // that can still move after this ships: lower it and every save is consistent on the next read
    // rather than needing a migration.
    //
    // The elapsed span is bounded before it is multiplied, the technique — and the reason —
    // `Advance.accrued` already states: a save whose instant is far in the past, or a clock that
    // jumped, would otherwise form a product that wraps negative on the way to an answer that is
    // simply the cap.
    // How long until this world holds what is being asked of it — **null when it never will**,
    // because the ask is bigger than the world.
    //
    // That null is not an edge case, it is the finding Claude Design built the waiting state around:
    // the vein and the rate carry one multiplier, so a full fleet's lift is about the size of a vein,
    // and "four skiffs at 6h" is routinely an ask no world can ever satisfy. **The countdown is only
    // honest because the offer above it can move** — shrink the ask to one skiff at 3h and the same
    // world is worth visiting in days rather than never.
    //
    // Ceiled to the millisecond, so the answer is the first instant the ask is covered rather than
    // the last instant it is not.
    fun timeUntil(storedFine: Long, capFine: Long, wanted: Long): Duration? {
        require(wanted >= 0) { "an ask cannot be negative, was $wanted" }
        val wantedFine = wanted * Resources.FINE_PER_UNIT
        if (storedFine >= wantedFine) return Duration.ZERO
        if (wantedFine > capFine) return null
        val perDayFine = capFine / PERCENT * REFILL_PERCENT_PER_DAY
        if (perDayFine <= 0) return null
        val shortBy = wantedFine - storedFine
        return ((shortBy * MILLISECONDS_PER_DAY + perDayFine - 1) / perDayFine).milliseconds
    }

    fun regenerated(storedFine: Long, capFine: Long, elapsed: Duration): Long {
        require(storedFine >= 0) { "a deposit cannot be negative, was $storedFine" }
        require(capFine >= 0) { "a cap cannot be negative, was $capFine" }
        if (storedFine >= capFine) return capFine
        val elapsedMilliseconds = elapsed.inWholeMilliseconds
        if (elapsedMilliseconds <= 0) return storedFine
        // Exact: `capFine` is a whole number of units and a unit is 3,600,000 fine, so five hundredths
        // of it is `units x 180,000` with nothing left over.
        val perDayFine = capFine / PERCENT * REFILL_PERCENT_PER_DAY
        if (perDayFine <= 0) return capFine
        val headroom = capFine - storedFine
        val millisecondsToFill = headroom / perDayFine * MILLISECONDS_PER_DAY + MILLISECONDS_PER_DAY
        val effective = minOf(elapsedMilliseconds, millisecondsToFill)
        return minOf(capFine, storedFine + checkedTimes(perDayFine, effective) { "refill" } / MILLISECONDS_PER_DAY)
    }
}
