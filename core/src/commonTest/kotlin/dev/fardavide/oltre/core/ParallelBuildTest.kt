package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class ParallelBuildTest {

    @Test
    fun `a second facility can start while the first is still building`() {
        // given
        val t0 = Instant.fromEpochMilliseconds(0)
        val funded = GameState.initial().fundedFor(BuildingType.METAL_MINE, BuildingType.SOLAR_PLANT)
        val first = assertIs<StartUpgradeResult.Started>(
            startUpgrade(funded, BuildingType.METAL_MINE, at = t0),
        ).state

        // when
        val second = assertIs<StartUpgradeResult.Started>(
            startUpgrade(first, BuildingType.SOLAR_PLANT, at = t0),
        ).state

        // then
        assertEquals(
            setOf(BuildingType.METAL_MINE, BuildingType.SOLAR_PLANT),
            second.builds.keys,
        )
    }

    @Test
    fun `parallel jobs complete independently and earliest first`() {
        // given a metal mine and a solar plant started together, one of which costs more and
        // therefore takes longer
        val t0 = Instant.fromEpochMilliseconds(0)
        val both = twoJobs(t0)
        val quick = both.landsFirst()
        val slow = both.landsSecond()
        assertTrue(both.completionOf(quick) < both.completionOf(slow), "scenario needs two different lengths")

        // when
        val betweenCompletions = advance(both, from = t0, to = both.completionOf(slow) - 1.milliseconds)
        val afterBoth = advance(both, from = t0, to = both.completionOf(slow))

        // then
        assertEquals(BuildingLevel(2), betweenCompletions.buildings.levelOf(quick))
        assertEquals(BuildingLevel(1), betweenCompletions.buildings.levelOf(slow))
        assertEquals(setOf(slow), betweenCompletions.builds.keys)
        assertEquals(BuildingLevel(2), afterBoth.buildings.levelOf(slow))
        assertTrue(afterBoth.builds.isEmpty(), "both jobs must have left the build map")
    }

    @Test
    fun `completions are logged in the order they happen`() {
        // given
        val t0 = Instant.fromEpochMilliseconds(0)
        val both = twoJobs(t0)

        // when
        val quick = both.landsFirst()
        val slow = both.landsSecond()
        val after = advance(both, from = t0, to = both.completionOf(slow))

        // then
        assertEquals(
            listOf(
                Event.BuildCompleted(
                    building = quick,
                    newLevel = BuildingLevel(2),
                    at = both.completionOf(quick),
                ),
                Event.BuildCompleted(
                    building = slow,
                    newLevel = BuildingLevel(2),
                    at = both.completionOf(slow),
                ),
            ),
            after.eventLog.filterIsInstance<Event.BuildCompleted>(),
        )
    }

    @Test
    fun `composability holds across two completion boundaries`() {
        // given
        val t0 = Instant.fromEpochMilliseconds(0)
        val both = twoJobs(t0)
        val firstDone = both.completionOf(both.landsFirst())
        val secondDone = both.completionOf(both.landsSecond())
        val t2 = secondDone + 3.hours
        val oneShot = advance(both, from = t0, to = t2)

        val splits = listOf(
            firstDone - 1.milliseconds,
            firstDone,
            firstDone + 1.milliseconds,
            secondDone - 1.milliseconds,
            secondDone,
            secondDone + 1.milliseconds,
        )
        for (t1 in splits) {
            // when
            val stepped = advance(advance(both, from = t0, to = t1), from = t1, to = t2)

            // then
            assertEquals(oneShot, stepped, "split at $t1 diverged")
        }
    }

    @Test
    fun `two jobs landing on the same instant both complete`() {
        // given the longer of two upgrades started first, and the shorter one started exactly late
        // enough to land on the same instant. Which of the two is longer is read off the curve
        // rather than written down: the pair used to be "crystal 24 minutes, metal 20", and
        // cost-proportional durations put the metal mine a minute ahead — which made the shorter
        // one start *before* t0 and `advance` refuse to run backwards.
        val t0 = Instant.fromEpochMilliseconds(0)
        val pair = listOf(BuildingType.CRYSTAL_MINE, BuildingType.METAL_MINE)
            .sortedBy { PlaceholderBalance.upgradeDuration(it, BuildingLevel(2), BuildingLevel(0)) }
        val shorter = pair.first()
        val longer = pair.last()

        val started = assertIs<StartUpgradeResult.Started>(startUpgrade(GameState.initial().fundedFor(*pair.toTypedArray()), longer, at = t0)).state
        val together = started.completionOf(longer)
        val shorterStart = together -
            PlaceholderBalance.upgradeDuration(shorter, BuildingLevel(2), BuildingLevel(0))
        val both = assertIs<StartUpgradeResult.Started>(
            startUpgrade(advance(started, from = t0, to = shorterStart), shorter, at = shorterStart),
        ).state
        assertEquals(together, both.completionOf(shorter))

        // when
        val after = advance(both, from = shorterStart, to = together)

        // then
        assertEquals(BuildingLevel(2), after.buildings.metalMine)
        assertEquals(BuildingLevel(2), after.buildings.crystalMine)
        assertTrue(after.builds.isEmpty(), "both jobs must have left the build map")
    }

    @Test
    fun `a facility can be upgraded again once its job completes`() {
        // given
        val t0 = Instant.fromEpochMilliseconds(0)
        val funded = GameState.initial().fundedFor(BuildingType.METAL_MINE)
        val started = assertIs<StartUpgradeResult.Started>(
            startUpgrade(funded, BuildingType.METAL_MINE, at = t0),
        ).state
        val done = started.completionOf(BuildingType.METAL_MINE)
        val settled = advance(started, from = t0, to = done)
            .let { it.fundedFor(BuildingType.METAL_MINE) }

        // when
        val again = startUpgrade(settled, BuildingType.METAL_MINE, at = done)

        // then
        assertEquals(BuildingLevel(3), assertIs<StartUpgradeResult.Started>(again).state.jobOf(BuildingType.METAL_MINE).toLevel)
    }

    private fun twoJobs(at: Instant): GameState {
        val funded = GameState.initial().fundedFor(BuildingType.METAL_MINE, BuildingType.SOLAR_PLANT)
        val first = assertIs<StartUpgradeResult.Started>(
            startUpgrade(funded, BuildingType.METAL_MINE, at = at),
        ).state
        return assertIs<StartUpgradeResult.Started>(
            startUpgrade(first, BuildingType.SOLAR_PLANT, at = at),
        ).state
    }

    // Which of the two lands first is read off the state rather than written down, and that is the
    // point of this pair of helpers. These tests are about *parallelism* — jobs completing
    // independently, earliest first, logged in that order — and they used to encode a balance fact
    // to get there: under the old per-building-minutes curve the Solar Plant was the quickest thing
    // in the game while costing more than a Metal Mine. Cost-proportional durations invert that,
    // and three tests failed on an ordering they were never actually about.
    private fun GameState.landsFirst(): BuildingType =
        builds.values.minBy { it.completesAt }.building

    private fun GameState.landsSecond(): BuildingType =
        builds.values.maxBy { it.completesAt }.building
}
