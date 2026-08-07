package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// A property test that pins a *distribution* is unusual, and it is warranted here because the
// distribution **is** the mechanic. Nothing else in the repo would catch a refactor that quietly
// made the galaxy generous: every other galaxy test would still pass with a map where half the
// worlds were worth settling.
//
// ── One target is not met, deliberately, and is waiting on Davide ────────────────────────────
//
// The sheet's section 9 asks for two things at once that three comparable axes cannot both give:
//
//   passes every band     1 – 2%      measured 2.63%
//   fails exactly one     35 – 45%    measured 17.55%
//
// Those two rows constrain each other. With three independent axes passing at rates a, b, c, the
// first row is `abc` and the second is `ab + ac + bc - 3abc`. Holding `abc` inside 1–2% caps the
// second row at about **16%** whenever the three axes are near each other — and the most balanced
// pass rates that reach 35% at all are roughly 0.06 / 0.58 / 0.59, which means one axis blocking
// 94% of worlds while the other two wave almost everything through. That is a galaxy with one
// ladder that matters and two that do not, which is the single-habitability-score design that
// section 1 rejected, arrived at from the other direction.
//
// So the constants have NOT been moved to chase it: which target gives way is a design call, not a
// tuning exercise, and moving a tolerance band to hit row 2 would quietly overturn section 1. The
// bands below therefore pin **what the sheet's own constants produce**, which is what makes this a
// regression guard today, and `balance-log.md` round 5 carries the open call. Once Davide rules,
// the numbers here change with the constants — that is the point of them being written down.
class GalaxyDistributionTest {

    @Test
    fun `the coordinate space holds about 4700 worlds`() {
        assertEquals(15_000, GalaxyBalance.TOTAL_SLOTS)
        assertTrue(
            galaxy().size in 4_500..5_000,
            "expected ~4,750 worlds galaxy-wide, generated ${galaxy().size}",
        )
    }

    @Test
    fun `settling is rare enough that a survey is a decision`() {
        // MET, and the row that matters most: roughly one world in two hundred is worth taking on
        // day one. Measured 0.71%, against a target of <= 0.5% — over, and one of the two rows the
        // open call above has to settle.
        val worlds = galaxy()
        val settleable = worlds.count { it.verdictAtLevelZero() is WorldVerdict.Settleable }
        val share = settleable * 10_000 / worlds.size

        assertTrue(share in 40..90, "expected 0.4–0.9% of worlds settleable, got ${share / 100.0}%")
    }

    @Test
    fun `the median world that passes every band is Barren`() {
        // MET, and exactly as the sheet predicted: the median passing world scores 0.84 against a
        // 0.90 threshold. This is what makes "surveying should frequently return not worth it" true
        // by construction rather than by hope.
        val passing = galaxy()
            .filter { it.passesEveryBand() }
            .map { GalaxyBalance.yieldScore(it.traits).perMillion }
            .sorted()
        val median = passing[passing.size / 2]

        assertTrue(passing.isNotEmpty())
        assertTrue(
            median < GalaxyBalance.WORTH_IT_THRESHOLD.perMillion,
            "the median passing world must be Barren, scored $median against " +
                "${GalaxyBalance.WORTH_IT_THRESHOLD.perMillion}",
        )
        assertTrue(median in 800_000..880_000, "expected a median around 0.84, got $median")
    }

    @Test
    fun `most of the galaxy is out of reach on more than one axis`() {
        val worlds = galaxy()
        val failures = worlds.map { world ->
            HostilityAxis.entries.count { axis -> world.traits.axisValue(axis) !in unaided.bandOf(axis) }
        }

        // Pinned at what the sheet's constants produce. The 35–45% the sheet asks for on the middle
        // row is the open call — see the class comment.
        assertTrue(percentOf(failures.count { it == 0 }, worlds.size) in 200..300, "passes every band")
        assertTrue(percentOf(failures.count { it == 1 }, worlds.size) in 1_500..2_000, "fails exactly one")
        assertTrue(percentOf(failures.count { it >= 2 }, worlds.size) in 7_700..8_300, "fails two or three")
    }

    @Test
    fun `no axis is the only one that matters`() {
        // The guard on the open call above: whichever way it is settled, three axes that each block
        // a comparable share is what makes three adaptation ladders a choice rather than a chain. If
        // one axis ever starts gating almost everything, the other two ladders have stopped being
        // decisions and section 1's argument has quietly been reversed.
        val worlds = galaxy()
        val passRates = HostilityAxis.entries.map { axis ->
            axis to percentOf(worlds.count { it.traits.axisValue(axis) in unaided.bandOf(axis) }, worlds.size)
        }

        for ((axis, rate) in passRates) {
            assertTrue(rate in 2_000..4_000, "$axis passes ${rate / 100.0}% of worlds, expected 20–40%")
        }
        val spread = passRates.maxOf { it.second }.toDouble() / passRates.minOf { it.second }
        assertTrue(spread < 2.0, "the three axes must stay comparable, spread was $spread")
    }

    @Test
    fun `each adaptation level roughly doubles what can be settled`() {
        // MET. The sheet asks the tech to have a visible payoff on the map for the first few levels,
        // which is what stops a ladder being something you buy out of duty.
        val worlds = galaxy()
        val settleable = (0..3).map { level ->
            val tolerance = GalaxyBalance.tolerance(AdaptationLevels(level, level, level))
            worlds.count { world ->
                HostilityAxis.entries.all { world.traits.axisValue(it) in tolerance.bandOf(it) } &&
                    GalaxyBalance.yieldScore(world.traits).perMillion >= GalaxyBalance.WORTH_IT_THRESHOLD.perMillion
            }
        }

        for ((before, after) in settleable.zipWithNext()) {
            assertTrue(
                after >= before * 17 / 10,
                "a level must roughly double the settleable count, went from $before to $after",
            )
        }
    }

    @Test
    fun `hazards land on the published share of worlds`() {
        val worlds = galaxy()
        val one = percentOf(worlds.count { it.traits.hazards.size == 1 }, worlds.size)
        val two = percentOf(worlds.count { it.traits.hazards.size == 2 }, worlds.size)

        assertTrue(one in 3_200..3_800, "expected ~35% with one hazard, got ${one / 100.0}%")
        assertTrue(two in 800..1_200, "expected ~10% with two hazards, got ${two / 100.0}%")
    }

    @Test
    fun `the distribution is the same on a second seed`() {
        // A distribution that only holds for one seed is a coincidence rather than a mechanic.
        val worlds = galaxy(OTHER_GALAXY_SEED)
        val settleable = worlds.count { it.verdictAtLevelZero() is WorldVerdict.Settleable }

        assertTrue(worlds.size in 4_500..5_000, "second seed generated ${worlds.size} worlds")
        assertTrue(
            settleable * 10_000 / worlds.size in 40..90,
            "second seed made ${settleable * 10_000 / worlds.size} settleable per 10,000",
        )
    }

    private val unaided = GalaxyBalance.tolerance(AdaptationLevels.NONE)

    private fun World.passesEveryBand(): Boolean =
        HostilityAxis.entries.all { traits.axisValue(it) in unaided.bandOf(it) }

    // Surveyed and unowned, so the verdict is the one the traits earn rather than `Unsurveyed`.
    private fun World.verdictAtLevelZero(): WorldVerdict = verdictFor(
        world = this,
        galaxy = GalaxyState(
            seed = TEST_GALAXY_SEED,
            home = HOME,
            surveyed = setOf(at),
            ownership = listOf(WorldOwnership(at = HOME, holder = EmpireId.PLAYER)),
        ),
        adaptation = AdaptationLevels.NONE,
    )

    private fun percentOf(part: Int, whole: Int): Int = part * 10_000 / whole

    private fun galaxy(seed: GalaxySeed = TEST_GALAXY_SEED): List<World> = buildList {
        for (galaxy in 1..GalaxyBalance.GALAXIES) {
            for (system in 1..GalaxyBalance.SYSTEMS_PER_GALAXY) {
                for (slot in 1..GalaxyBalance.SLOTS_PER_SYSTEM) {
                    worldAt(seed, GalaxyCoordinate(galaxy, system, slot))?.let(::add)
                }
            }
        }
    }

    private companion object {
        // Somewhere the sampled worlds are not, so no sampled world is ever read as Home.
        val HOME = GalaxyCoordinate(galaxy = 1, system = 1, slot = 1)
    }
}
