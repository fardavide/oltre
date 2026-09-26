package dev.fardavide.oltre.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

// How deep a world is, and how slowly it comes back. `.ai/docs/deposit-sheet.md` is the design;
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
    // **Derived from a rule rather than chosen, and the rule's subject changed once.** Davide,
    // 2026-08-13, asked for a number and gave a rule instead: *"I would expect a regular ship to take
    // two rounds or a whole day to deplete a planet, the ship capacity (a basic one) is never more
    // than the planet resources."* One skiff on a 24h run spends 1,418 minutes on the surface and
    // lifts exactly 1,418 priced units, so 1,450 was the smallest tidy cap that never lets a basic
    // hull overflow a world — 0.99 days, or 2.0 runs at the 12h window, both halves to two decimals.
    //
    // **Then 0.10.1 deleted the bound on fleet size and the rule's premise went with it.** The flat
    // hull price removed the only ceiling on hull count, so *"a regular ship"* stopped describing what
    // arrives at a world: the measured manifest is nine hulls by hour 48. Davide's re-derivation,
    // 2026-08-15, on *"I'm so much out of planets to gather resources from"* — **a typical fleet takes
    // about two runs**, where it used to read a typical ship.
    //
    // **So the constant is the same arithmetic with the manifest substituted, and four is the
    // manifest.** Four skiffs on a 24h run lift 5,672 priced units, and at 5,800 the doorstep takes
    // 1.02 days or 2.07 runs at the 12h window — the identical two decimals, one order of magnitude
    // of fleet along. The invariant that pays for everything else is untouched and merely renamed:
    // `workingTime` for four hulls is still 1,450 minutes at every richness and every danger, and a
    // lone skiff now takes four days rather than one.
    //
    // **The floor and the ceiling are both measured, and they are close together.** Round 24 rejected
    // 1,000 from below: under a single skiff's day the deposit binds on essentially every dispatch,
    // and when the deposit binds nothing else does. Issue #68's sweep found the mirror above — the
    // share of dispatches the vein stops falls 48.8% → 5.6% at 4×, and 2.5% at 5×, so a cap much past
    // this one deletes the mechanic 0.10.0 was asked for rather than tuning it. 4× is the bottom of
    // the band Davide named (4–6×) for that reason. The sheet's 2.5 has both grids.
    const val BASE_PRICED: Long = 5_800

    // Priced at the game's own 1 : 2 : 3, the convention `FleetBalance.cargo` already spends — so a
    // world's metal and crystal deposits are worth the same and differ only by richness.
    private fun pricePerUnit(kind: ResourceKind): Long = when (kind) {
        ResourceKind.METAL -> 1
        ResourceKind.CRYSTAL -> 2
        ResourceKind.DEUTERIUM -> error("unreachable — guarded by cap()")
    }

    private const val PERCENT: Long = 100
    private const val DANGER_BONUS_PERCENT: Long = 35

    const val ADAPTATION_REWARD_DENOMINATOR: Long = 12

    // The world's absolute requirements price the reward, even after the player unlocks it.
    // Cap and extraction share this rational multiplier and round only at the end.
    fun adaptationRewardNumerator(world: World): Long = ADAPTATION_REWARD_DENOMINATOR +
        HostilityAxis.entries.sumOf { axis ->
            val level = GalaxyBalance.levelThatTolerates(axis, world.traits.axisValue(axis)).toLong()
            checkedTimes(level, level) { "adaptation reward" }
        }

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
        numerator = checkedTimes(numerator, adaptationRewardNumerator(world)) { "cap adaptation" }
        return numerator / (GalaxyBalance.RICHNESS_BASIS.toLong() * PERCENT * pricePerUnit(gathering) *
            ADAPTATION_REWARD_DENOMINATOR)
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
    // Search `cargo`'s own whole-minute curve so the displayed time and lifted amount agree.
    // A separate inverse rate would duplicate its rounding and overflow for large fleets.
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
        if (remaining == 0L || FleetBalance.berths(ships) == 0) return Duration.ZERO
        // Search cargo's own whole-minute curve: multiplying the inverse rate denominator can
        // overflow for a large fleet even when it needs just one minute to finish this deposit.
        fun covers(minutes: Long): Boolean {
            val cargo = FleetBalance.cargo(world, gathering, ships, minutes.minutes, danger, research)
            val lifted = when (gathering) {
                ResourceKind.METAL -> cargo.metal
                ResourceKind.CRYSTAL -> cargo.crystal
                ResourceKind.DEUTERIUM -> error("unreachable — guarded by workingTime()")
            }
            return lifted >= remaining
        }
        var high = 1L
        while (!covers(high)) high = checkedTimes(high, 2) { "working time bound" }
        var low = 1L
        while (low < high) {
            val middle = low + (high - low) / 2
            if (covers(middle)) high = middle else low = middle + 1
        }
        return low.minutes
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
    // Find the first millisecond the refill covers the ask. Searching the bounded refill span
    // avoids multiplying a large fine-unit stock by a day's milliseconds.
    fun timeUntil(storedFine: Long, capFine: Long, wanted: Long): Duration? {
        require(wanted >= 0) { "an ask cannot be negative, was $wanted" }
        val wantedFine = wanted * Resources.FINE_PER_UNIT
        if (storedFine >= wantedFine) return Duration.ZERO
        if (wantedFine > capFine) return null
        val perDayFine = capFine / PERCENT * REFILL_PERCENT_PER_DAY
        if (perDayFine <= 0) return null
        val shortBy = wantedFine - storedFine
        var low = 0L
        var high = ((shortBy - 1) / perDayFine + 1) * MILLISECONDS_PER_DAY
        while (low < high) {
            val middle = low + (high - low) / 2
            if (regenerated(storedFine, capFine, middle.milliseconds) >= wantedFine) {
                high = middle
            } else {
                low = middle + 1
            }
        }
        return low.milliseconds
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
        // Divide the daily rate first, carrying its remainder through the single final floor.
        // Multiplying the entire rate by twenty days overflows for adaptation-sized deposits.
        val whole = checkedTimes(perDayFine / MILLISECONDS_PER_DAY, effective) { "refill whole" }
        val fraction = checkedTimes(perDayFine % MILLISECONDS_PER_DAY, effective) { "refill fraction" } /
            MILLISECONDS_PER_DAY
        return minOf(capFine, storedFine + whole + fraction)
    }
}
