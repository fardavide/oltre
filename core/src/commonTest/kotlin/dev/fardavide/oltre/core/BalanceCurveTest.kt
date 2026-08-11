package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The shape of the placeholder curves is a design decision, so it is asserted rather than left
// to whatever the arithmetic happens to produce: an upgrade is a raise, not a doubling, and
// cost outgrows output so depth stays a decision.
class BalanceCurveTest {

    @Test
    fun `an upgrade raises production well short of doubling it`() {
        for (level in 1..20) {
            // when
            val current = PlaceholderBalance.metalProductionPerHour(BuildingLevel(level))
            val next = PlaceholderBalance.metalProductionPerHour(BuildingLevel(level + 1))

            // then
            assertTrue(next > current, "level ${level + 1} must out-produce level $level")
            assertTrue(next * 2 < current * 3, "level ${level + 1} must stay under +50% of level $level")
        }
    }

    @Test
    fun `metal is produced in the proportion the colony is actually upgraded in`() {
        // given the three facilities a player buys a level of *every session* — the basket that
        // repeats, not the whole tree. The Robotics Factory (3.3:1) and the Deuterium Synthesizer
        // (3:1) are bought a handful of times each in a game and are the two most metal-heavy rows
        // in it; averaging them in as equals is what pulled this target up to ~3:1 at 0.0.12, and
        // a colony upgraded at that ratio starves for crystal while metal piles up unspent. The
        // Nanite Factory is out for the same reason it always was: a different economy.
        val basket = listOf(
            BuildingType.METAL_MINE,
            BuildingType.CRYSTAL_MINE,
            BuildingType.SOLAR_PLANT,
        )
        // Priced at **full price** (level 9) rather than at level 1, since the opening now carries
        // a decaying discount. The discount multiplies all three resources by the same fraction, so
        // it cannot change this ratio by design — but it is floored per resource, and at level 1 the
        // integers are small enough for that flooring to move the measured basket a little. The
        // design ratio is therefore read where the integers are big, and the opening's own skew is
        // bounded separately below so a future change to the ramp cannot hide in the rounding.
        val fullPrice = BuildingLevel(9)
        val demandedMetal = basket.sumOf { PlaceholderBalance.upgradeCost(it, fullPrice).metal }
        val demandedCrystal = basket.sumOf { PlaceholderBalance.upgradeCost(it, fullPrice).crystal }

        // when
        val producedMetal = PlaceholderBalance.metalProductionPerHour(BuildingLevel(1))
        val producedCrystal = PlaceholderBalance.crystalProductionPerHour(BuildingLevel(1))

        // then production must track that demand within a tenth **in both directions**. The
        // one-sided bound this replaces could only ever catch metal being too poor, which is the
        // 0.0.12 failure; it waved through metal being too rich, which is the 0.1.0 one. `:sim:run`
        // measures the same thing end to end: at 90:30 the greedy week spends 130 of its 168 hours
        // with a purchase blocked by crystal *alone*, holding 49,544 idle metal it cannot use.
        assertTrue(
            producedMetal * demandedCrystal * 10 >= demandedMetal * producedCrystal * 9,
            "metal production ($producedMetal:$producedCrystal) is too poor in metal against the " +
                "$demandedMetal:$demandedCrystal the colony is upgraded in",
        )
        assertTrue(
            producedMetal * demandedCrystal * 10 <= demandedMetal * producedCrystal * 11,
            "metal production ($producedMetal:$producedCrystal) is too rich in metal against the " +
                "$demandedMetal:$demandedCrystal the colony is upgraded in — crystal becomes the " +
                "only thing anyone waits for, and metal accumulates with nothing to buy",
        )

        // And the opening's own basket, rounded and all, must not drift far from the one above.
        // Looser because it is measuring rounding noise on two-digit numbers rather than a curve,
        // but bounded, so a future change to the ramp cannot quietly skew what the first days cost.
        val openingMetal = basket.sumOf { PlaceholderBalance.upgradeCost(it, BuildingLevel(1)).metal }
        val openingCrystal = basket.sumOf { PlaceholderBalance.upgradeCost(it, BuildingLevel(1)).crystal }
        assertTrue(
            openingMetal * demandedCrystal * 10 <= demandedMetal * openingCrystal * 12 &&
                openingMetal * demandedCrystal * 10 >= demandedMetal * openingCrystal * 8,
            "the discounted opening is upgraded in $openingMetal:$openingCrystal against the " +
                "$demandedMetal:$demandedCrystal it settles at — more than a fifth apart",
        )
    }

    @Test
    fun `production is human-scale at the levels a first week reaches`() {
        // then
        assertEquals(90L, PlaceholderBalance.metalProductionPerHour(BuildingLevel(1)))
        assertEquals(663L, PlaceholderBalance.metalProductionPerHour(BuildingLevel(10)))
        assertEquals(36L, PlaceholderBalance.crystalProductionPerHour(BuildingLevel(1)))
        assertEquals(262L, PlaceholderBalance.crystalProductionPerHour(BuildingLevel(10)))
        assertEquals(15L, PlaceholderBalance.deuteriumProductionPerHour(BuildingLevel(1)))
        assertEquals(97L, PlaceholderBalance.deuteriumProductionPerHour(BuildingLevel(10)))
    }

    @Test
    fun `a razed facility produces nothing`() {
        // then
        assertEquals(0L, PlaceholderBalance.metalProductionPerHour(BuildingLevel(0)))
        assertEquals(0L, PlaceholderBalance.crystalProductionPerHour(BuildingLevel(0)))
        assertEquals(0L, PlaceholderBalance.deuteriumProductionPerHour(BuildingLevel(0)))
    }

    @Test
    fun `cost compounds by half again per level once the opening discount has run out`() {
        // Full price starts at level 9 — `PlaceholderBalance.FULL_PRICE_LEVEL`, private, so stated
        // here as the specification rather than read from it. From there up this is the same ×1.5
        // the game has had since round 2, and the point of the ramp is that it stays so: the
        // discount buys the opening and gives the deep curve back untouched.
        for (level in 9..20) {
            // when
            val current = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(level))
            val next = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(level + 1))

            // then
            assertEquals(current.metal * 3 / 2, next.metal, "metal cost at level ${level + 1}")
            assertEquals(current.crystal * 3 / 2, next.crystal, "crystal cost at level ${level + 1}")
        }
    }

    @Test
    fun `the opening is discounted and the discount runs out rather than being given back`() {
        // Davide, 2026-08-09: "Everything must be cheaper and quicker across the board, until first
        // expedition ... arrive to 1x at the moment you can have the first expedition." He named the
        // moment: when the galaxy becomes actionable, which is the adaptation ladders at Robotics 4.
        //
        // The *depth* of the ramp then moved once, later the same day — "I still feel the early game
        // is WAAAY too slow! Let's try a 10x boost, instead of 3x as we did" — and only the depth:
        // full price still starts at level 9, because `:sim:run` still puts the mines at 8 or 9 when
        // Robotics 4 lands. At 10x it lands sooner (hour 33 rather than hour 54) and the mines get
        // there sooner with it, so the two moved together and the convergence level did not have to.
        //
        // Three properties, and the third is what makes it a ramp rather than a price cut.
        val undiscounted = { level: Int ->
            var value = 60L
            repeat(level - 1) { value = value * 3 / 2 }
            value
        }

        // 1. Level one is exactly a tenth of full price — the "10x" he asked for.
        assertEquals(
            undiscounted(1) / 10,
            PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(1)).metal,
            "level 1 must cost a tenth of full price",
        )

        // 2. The deep curve is handed back exactly. Not approximately: the same integers it had
        //    before the ramp existed, which is what makes this a change to the opening alone.
        for (level in 9..20) {
            assertEquals(
                undiscounted(level),
                PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(level)).metal,
                "level $level must be at full price",
            )
        }

        // 3. Inside the ramp the curve climbs *faster* than ×1.5, because each level also gives
        //    back a share of the discount — and it must never stall or fall, which integer rounding
        //    on small numbers is entirely capable of doing. The slope falls as the discount runs
        //    out, from ×3.17 at the first step to ×1.69 at the last.
        //
        //    That first step is what a deeper ramp costs, and it is worth naming rather than
        //    burying: at 3x it was ×1.85, at 10x it is ×3.17, and the arithmetic says it must be —
        //    a linear recovery over the same 8 levels hands back `(divisor − 1) / span` extra on
        //    the first step whatever else changes. The alternative is a longer ramp, which is
        //    `FULL_PRICE_LEVEL`, which is the landmark, which is Davide's. Nothing here can soften
        //    the slope without moving the landmark, so the bound is widened honestly instead.
        for (level in 1 until 9) {
            val current = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(level)).metal
            val next = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(level + 1)).metal
            assertTrue(next > current, "level ${level + 1} ($next) must cost more than level $level ($current)")
            assertTrue(
                next * 100 >= current * 150 && next * 100 <= current * 320,
                "level $level to ${level + 1} steps $current -> $next, outside x1.5 to x3.2",
            )
        }
    }

    @Test
    fun `every cost in the game stays a positive and rising integer`() {
        // The opening discount is carried *exactly* — `exactGeometric` multiplies by the numerator
        // once per step and divides once at the end — so the number of steps is bounded by
        // `FULL_PRICE_LEVEL`, and that bound is load-bearing rather than tidy. Round 13 swept the
        // constant to 18 while measuring and the Nanite Factory came out at **−70 deuterium**:
        // 20,000 × 9^17 leaves Long, and a negative cost is one `covers()` reads as free.
        //
        // `Resources.of` caught it, but only at the point of use — a crash in a running game rather
        // than a red build, and only for the one row deep enough to overflow. This walks the whole
        // table, so the next session that reaches for that constant is told by CI.
        for (building in BuildingType.entries) {
            var previous = PlaceholderBalance.upgradeCost(building, BuildingLevel(1))
            assertTrue(previous.metal > 0, "$building level 1 costs ${previous.metal} metal")
            assertTrue(previous.crystal > 0, "$building level 1 costs ${previous.crystal} crystal")
            // 40 is `MAX_UPGRADE_LEVEL`, private — the top of the table `upgradeCost` will answer
            // for at all, and therefore the range the overflow has to be absent from.
            for (level in 2..40) {
                val cost = PlaceholderBalance.upgradeCost(building, BuildingLevel(level))
                assertTrue(cost.metal > previous.metal, "$building $level: ${cost.metal} after ${previous.metal}")
                assertTrue(
                    cost.crystal > previous.crystal,
                    "$building $level: ${cost.crystal} crystal after ${previous.crystal}",
                )
                // Only two rows cost deuterium at all, so this one is allowed to stay at zero.
                assertTrue(
                    cost.deuterium >= previous.deuterium,
                    "$building $level: ${cost.deuterium} deuterium after ${previous.deuterium}",
                )
                previous = cost
            }
        }
    }

    @Test
    fun `a build takes about as long as earning it does`() {
        // **The two-sided version of the shape round 10 landed one-sided.** Round 10 made a build
        // take as long as it *costs*, which reads well and diverges badly: cost compounds at +50%
        // a level while production compounds at +25%, so a duration read straight off the cost
        // pulls away from the income that pays for it by 20% a level, from level one, without
        // bound. Measured at level 20 that curve asked 911 hours of building for a mine the colony
        // earns in 24 — a factor of 38 — and at level 6 it already asked 3h 07m against 1h 50m,
        // which is the wait Davide opened round 11 complaining about.
        //
        // The root fixes it because the arithmetic lines up: cost-over-income grows at 1.5/1.25 =
        // x1.2 a level, and the square root of a x1.5 curve grows at x1.2247. So a duration cut
        // from the root of the cost tracks the time it takes to earn it *at every depth*, with no
        // help from the Robotics Factory — which matters because the divisor is a building that
        // raises no rate, is priced in the slowest resource, and is therefore the one a player is
        // most likely not to have.
        //
        // Asserted as a two-sided ratio rather than a table of minutes, because the *relationship*
        // is the decision and a table would let it drift while every row still passed. The bounds
        // admit the 3..5 band round 11 swept and pin the 4 it chose: at 3 the build is only 0.57
        // of the earning and the colony idles as it did before round 10, at 5 it reaches 1.41 and
        // the complaint comes back.
        //
        // **From level 9 up, which is where round 11 was aiming.** Below it the opening speed-up
        // breaks this identity on purpose and the test below is the one that holds it to account;
        // running both bands through one loop would have let a deliberate divergence and an
        // accidental one wear the same bound.
        //
        // **And only as far as `LATE_GAME_FIRST_LEVEL`**, which is 0.5.2 narrowing round 11 rather
        // than repealing it. Davide, 2026-08-11: *"I'd expect late game upgrade to be extremely
        // slow, and expensive Nanite upgrades to make them reasonable."* Above the ramp the wait is
        // *supposed* to outgrow the income — that is the whole change — so the test that says it
        // must not is scoped to the stretch where it is still the rule, and
        // `the late game pulls away from its own income on purpose` below owns the other side.
        // Read the two together: the identity holds through the mid-game and is deliberately broken
        // after it, and neither half can drift without one of them failing.
        for (level in 9..PlaceholderBalance.LATE_GAME_FIRST_LEVEL) {
            val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(level))
            // The colony that buys this level is producing at the one below it, on both mines,
            // because the duration sum is metal and crystal and so the income compared with it
            // must be too.
            val perHour = PlaceholderBalance.metalProductionPerHour(BuildingLevel(level - 1)) +
                PlaceholderBalance.crystalProductionPerHour(BuildingLevel(level - 1))
            val earning = (cost.metal + cost.crystal) * 60 / perHour
            val building = PlaceholderBalance
                .upgradeDuration(BuildingType.METAL_MINE, BuildingLevel(level), BuildingLevel(0), BuildingLevel(0))
                .inWholeMinutes

            assertTrue(
                3 * building >= 2 * earning,
                "level $level builds in $building min against $earning min of income — too short to cover a gap",
            )
            assertTrue(
                4 * building <= 5 * earning,
                "level $level builds in $building min against $earning min of income — the wait outgrew the earning",
            )
        }
    }

    @Test
    fun `the late game pulls away from its own income on purpose`() {
        // The other side of round 11, and the half 0.5.2 added. Davide, 2026-08-11: *"I'd expect
        // late game upgrade to be extremely slow, and expensive Nanite upgrades to make them
        // reasonable ... It's reasonable for build times to be long only when the user has many
        // things to do: manage ships, travels, and co, not when it has only a few things."*
        //
        // So above `LATE_GAME_FIRST_LEVEL` the wait is *supposed* to outgrow the income, and the
        // test that forbids exactly that is scoped to stop there. This is the same reading, with the
        // inequality the other way up — asserted as a band on the ratio rather than on minutes,
        // because the decision is the divergence and a table of minutes would let it drift.
        val ratio = { level: Int ->
            val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(level))
            val perHour = PlaceholderBalance.metalProductionPerHour(BuildingLevel(level - 1)) +
                PlaceholderBalance.crystalProductionPerHour(BuildingLevel(level - 1))
            val earning = (cost.metal + cost.crystal) * 60 / perHour
            val building = PlaceholderBalance
                .upgradeDuration(BuildingType.METAL_MINE, BuildingLevel(level), BuildingLevel(0), BuildingLevel(0))
                .inWholeMinutes
            building * 100 / earning
        }

        // The last level before the ramp is still round 11's game, and that is what "do not impact
        // build times before then" means in one assertion.
        assertTrue(
            ratio(PlaceholderBalance.LATE_GAME_FIRST_LEVEL) <= 125,
            "the level below the ramp builds at ${ratio(PlaceholderBalance.LATE_GAME_FIRST_LEVEL) / 100.0}x " +
                "its own income — the late game is not supposed to start before the Nanite Factory does",
        )
        // And past it the gap opens and keeps opening. Measured 1.76x at 20, 5.96x at 25, 20.17x at
        // 30; the floors sit well under those so a round may re-tune the growth rate without this
        // failing, and well over 1 so a round that quietly flattened it could not.
        assertTrue(ratio(20) >= 130, "level 20 builds at ${ratio(20) / 100.0}x its income, was 1.76x")
        assertTrue(ratio(25) >= 300, "level 25 builds at ${ratio(25) / 100.0}x its income, was 5.96x")
        assertTrue(ratio(30) >= 800, "level 30 builds at ${ratio(30) / 100.0}x its income, was 20.17x")
    }

    @Test
    fun `the nanite factory answers the late game without deleting it`() {
        // Both halves of *"expensive Nanite upgrades to make them reasonable. I still think a late
        // game upgrade could take various hours, even with Nanite."* A Nanite that did not visibly
        // help would be the 20,000-metal ornament it was until 0.5.2; one that made deep levels
        // quick would put the late game back where it was and charge for the privilege.
        val wait = { level: Int, nanite: Int ->
            PlaceholderBalance
                .upgradeDuration(BuildingType.METAL_MINE, BuildingLevel(level), BuildingLevel(15), BuildingLevel(nanite))
                .inWholeMinutes
        }

        for (level in listOf(25, 30)) {
            val unaided = wait(level, 0)
            val helped = wait(level, 6)

            assertTrue(
                unaided >= helped * 5,
                "six Nanite levels only take level $level from $unaided to $helped minutes — that is not an answer",
            )
            assertTrue(
                helped >= 60,
                "level $level with six Nanite levels builds in $helped minutes — the late game is " +
                    "supposed to still cost hours, not be bought out of",
            )
        }
    }

    @Test
    fun `the opening builds faster than it earns and closes the gap by the landmark`() {
        // The deliberate half of the divergence above, and the reason the opening speed-up exists.
        // Davide, 2026-08-09: *"I want a 2/3 min build time at the very first levels, then 30min
        // should be ok when you can use Galaxy ... we need to give some adrenaline to users."*
        //
        // A build that took as long as earning it does would put the first Metal Mine at ten
        // minutes rather than two, so inside the ramp the clock is cut below its own income on
        // purpose — and then handed back, geometrically, so that by the level the galaxy opens at
        // the two have converged and round 11's rule takes over with nothing to correct.
        //
        // Both ends are asserted because only the pair says "ramp": the first without the second is
        // a game with no waits in it, and the second without the first is the game Davide has now
        // twice called too slow.
        val ratio = { level: Int ->
            val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(level))
            val perHour = PlaceholderBalance.metalProductionPerHour(BuildingLevel(level - 1)) +
                PlaceholderBalance.crystalProductionPerHour(BuildingLevel(level - 1))
            val earning = (cost.metal + cost.crystal) * 60 / perHour
            val building = PlaceholderBalance
                .upgradeDuration(BuildingType.METAL_MINE, BuildingLevel(level), BuildingLevel(0), BuildingLevel(0))
                .inWholeMinutes
            building to earning
        }

        // 1. Every level of the ramp builds strictly quicker than the income that pays for it.
        for (level in 2 until 9) {
            val (building, earning) = ratio(level)
            assertTrue(
                building < earning,
                "level $level builds in $building min against $earning min of income — the opening " +
                    "is supposed to outrun its own economy, that is what the speed-up is for",
            )
        }

        // 2. And the gap closes rather than persisting: a fifth at the first tap, past half by the
        //    last level of the ramp. Without this the speed-up could deepen without bound and every
        //    assertion above would still pass.
        val (firstBuild, firstEarning) = ratio(2)
        val (lastBuild, lastEarning) = ratio(8)
        assertTrue(
            firstBuild * 4 <= firstEarning,
            "the first tap builds in $firstBuild min against $firstEarning min of income — the " +
                "opening has stopped being quick enough to feel like one",
        )
        assertTrue(
            lastBuild * 2 >= lastEarning,
            "the last level of the ramp builds in $lastBuild min against $lastEarning min of " +
                "income — the speed-up is still wide open where it should have converged",
        )
    }

    @Test
    fun `every building reads its duration off the same root of the same full price`() {
        // One rule, not a per-building table: the cheapest row and the dearest are the same
        // function of what they cost, so a row cannot drift out of shape on its own. Stated here
        // as the rule itself rather than as a proportion, because with a root the ratio between
        // two rows is no longer constant — that is the point of it, and it is why the previous
        // shape could be checked with a division and this one cannot.
        //
        // **The root is taken of the full price and the ramp applied afterwards**, which is round
        // 16's correction. Taking it of the discounted price instead — which is what shipped from
        // round 13 to 15 — divides the wait by the *root* of the discount while dividing the price
        // by the discount, so a build inside the ramp quietly stops taking as long as earning it
        // does. At 3x that was a 1.73x overrun; at 10x it would be 3.16x, in exactly the stretch of
        // the game the ramp exists to speed up. The property below is the fix stated directly, and
        // `a build takes about as long as earning it does` is the same fix stated as its consequence.
        for (level in 2..20) {
            for (building in BuildingType.entries) {
                val full = PlaceholderBalance.fullPriceCost(building, BuildingLevel(level))
                assertEquals(
                    slowed(sped(4 * isqrt(full.metal + full.crystal), level), level),
                    PlaceholderBalance.upgradeDuration(building, BuildingLevel(level), BuildingLevel(0), BuildingLevel(0)).inWholeMinutes,
                    "$building $level",
                )
            }
        }
    }

    // The opening *speed-up*, written out rather than read from `openingSpeedUp` for the same
    // reason `isqrt` below is: a test that calls the arithmetic it is checking agrees with it
    // however wrong that arithmetic is. Two thirds a level below 9, carried exactly and rounded
    // half up once — deliberately the slow obvious loop.
    //
    // Note that this is *not* the ramp the price rides. The price recovers linearly from a tenth;
    // the clock recovers geometrically, because Davide asked for the first taps in minutes ("2/3
    // min") and a linear recovery cannot charge less than `full / span` at level 2 whatever its
    // divisor — 5 minutes where the ask was 2. Same convergence level, two shapes.
    private fun sped(fullPriceMinutes: Long, level: Int): Long {
        if (level >= 9) return fullPriceMinutes
        var top = fullPriceMinutes
        var bottom = 1L
        repeat(9 - level) {
            top *= 2
            bottom *= 3
        }
        return (top + bottom / 2) / bottom
    }

    // The late-game ramp, written out for the same reason `sped` is — and flooring at every step
    // rather than carrying the fraction, because `compound` does and the two have to agree to the
    // minute at level 30 or this test is checking nothing.
    private fun slowed(minutes: Long, level: Int): Long {
        if (level <= PlaceholderBalance.LATE_GAME_FIRST_LEVEL) return minutes
        var value = minutes
        repeat(level - PlaceholderBalance.LATE_GAME_FIRST_LEVEL) { value = value * 5 / 4 }
        return value
    }

    @Test
    fun `deuterium buys the research branch and never the clock`() {
        // The Robotics Factory and the Nanite Factory are the only two rows that cost deuterium —
        // the Deuterium Synthesizer *produces* it and is bought with metal and crystal like any
        // mine — and the Robotics Factory is what gates the whole research branch. Pricing time in
        // deuterium as well would make one scarcity govern two trade-offs the player has to make
        // separately, so the duration sum is metal and crystal, as OGame's is.
        for (building in listOf(BuildingType.ROBOTICS_FACTORY, BuildingType.NANITE_FACTORY)) {
            val full = PlaceholderBalance.fullPriceCost(building, BuildingLevel(4))
            assertTrue(full.deuterium > 0, "the fixture needs a row that costs deuterium")
            assertEquals(
                slowed(sped(4 * isqrt(full.metal + full.crystal), 4), 4),
                PlaceholderBalance.upgradeDuration(building, BuildingLevel(4), BuildingLevel(0), BuildingLevel(0)).inWholeMinutes,
                "$building must not be slowed by the resource that gates research",
            )
        }
    }

    // The rule written out a second time, on purpose. A test that called the production code's own
    // root would agree with it however wrong it was; this one is the specification, and it is
    // deliberately the slow obvious loop rather than the fast method under test.
    private fun isqrt(value: Long): Long {
        var root = 0L
        while ((root + 1) * (root + 1) <= value) root++
        return root
    }

    @Test
    fun `no build is ever instant however deep the Robotics Factory goes`() {
        // At Robotics 10 a first mine level divides to under three minutes, which is not a build —
        // it is a tap with a delay on it, and it would undo at depth exactly the emptiness the
        // cost-proportional curve exists to fill. The floor is applied to what the player waits,
        // *after* the divisor, so the divisor cannot cut through it.
        val instant = PlaceholderBalance
            .upgradeDuration(BuildingType.METAL_MINE, BuildingLevel(2), BuildingLevel(10), BuildingLevel(0))
        assertEquals(2, instant.inWholeMinutes)

        for (robotics in 0..20) {
            for (building in BuildingType.entries) {
                assertTrue(
                    PlaceholderBalance
                        .upgradeDuration(building, BuildingLevel(2), BuildingLevel(robotics), BuildingLevel(0))
                        .inWholeMinutes >= 2,
                    "$building at robotics $robotics",
                )
            }
        }
    }

    @Test
    fun `the opening still fits in a check-in`() {
        // The curve makes builds longer, and the one it must not make longer is the first. A new
        // colony opens on a decision it can see the end of: round 8 measured the whole change at an
        // identical 25 building levels after 48 hours, so the price of covering the gaps is paid at
        // depth rather than at the door.
        val first = PlaceholderBalance
            .upgradeDuration(BuildingType.METAL_MINE, BuildingLevel(2), BuildingLevel(0), BuildingLevel(0))
        assertTrue(first.inWholeMinutes in 2..60, "the first upgrade was ${first.inWholeMinutes} minutes")
    }

    @Test
    fun `a new colony can afford its first upgrades immediately`() {
        // given
        val stock = GameState.initial().resources

        // then
        assertTrue(
            stock.covers(PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2))),
            "a new colony opens on a decision, not on a wait",
        )
        assertTrue(
            stock.covers(PlaceholderBalance.upgradeCost(BuildingType.SOLAR_PLANT, BuildingLevel(2))),
            "and on more than one, so the first decision is a choice",
        )
    }
}
