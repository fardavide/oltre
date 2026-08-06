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
        // given a solar plant → 2 (16 minutes) started alongside a metal mine → 2 (20 minutes)
        val t0 = Instant.fromEpochMilliseconds(0)
        val both = twoJobs(t0)
        val solarDone = both.completionOf(BuildingType.SOLAR_PLANT)
        val mineDone = both.completionOf(BuildingType.METAL_MINE)
        assertTrue(solarDone < mineDone, "scenario needs the solar plant to land first")

        // when
        val betweenCompletions = advance(both, from = t0, to = mineDone - 1.milliseconds)
        val afterBoth = advance(both, from = t0, to = mineDone)

        // then
        assertEquals(BuildingLevel(2), betweenCompletions.buildings.solarPlant)
        assertEquals(BuildingLevel(1), betweenCompletions.buildings.metalMine)
        assertEquals(setOf(BuildingType.METAL_MINE), betweenCompletions.builds.keys)
        assertEquals(BuildingLevel(2), afterBoth.buildings.metalMine)
        assertTrue(afterBoth.builds.isEmpty(), "both jobs must have left the build map")
    }

    @Test
    fun `completions are logged in the order they happen`() {
        // given
        val t0 = Instant.fromEpochMilliseconds(0)
        val both = twoJobs(t0)

        // when
        val after = advance(both, from = t0, to = both.completionOf(BuildingType.METAL_MINE))

        // then
        assertEquals(
            listOf(
                Event.BuildCompleted(
                    building = BuildingType.SOLAR_PLANT,
                    newLevel = BuildingLevel(2),
                    at = both.completionOf(BuildingType.SOLAR_PLANT),
                ),
                Event.BuildCompleted(
                    building = BuildingType.METAL_MINE,
                    newLevel = BuildingLevel(2),
                    at = both.completionOf(BuildingType.METAL_MINE),
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
        val solarDone = both.completionOf(BuildingType.SOLAR_PLANT)
        val mineDone = both.completionOf(BuildingType.METAL_MINE)
        val t2 = mineDone + 3.hours
        val oneShot = advance(both, from = t0, to = t2)

        val splits = listOf(
            solarDone - 1.milliseconds,
            solarDone,
            solarDone + 1.milliseconds,
            mineDone - 1.milliseconds,
            mineDone,
            mineDone + 1.milliseconds,
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
        // given a crystal mine → 2 (24 minutes) and, started 4 minutes later, a metal mine → 2
        // (20 minutes) — both landing on the same instant
        val t0 = Instant.fromEpochMilliseconds(0)
        val funded = GameState.initial().fundedFor(BuildingType.CRYSTAL_MINE, BuildingType.METAL_MINE)
        val crystal = assertIs<StartUpgradeResult.Started>(
            startUpgrade(funded, BuildingType.CRYSTAL_MINE, at = t0),
        ).state
        val crystalDone = crystal.completionOf(BuildingType.CRYSTAL_MINE)
        val metalStart = crystalDone -
            PlaceholderBalance.upgradeDuration(BuildingType.METAL_MINE, BuildingLevel(2), BuildingLevel(0))
        val both = assertIs<StartUpgradeResult.Started>(
            startUpgrade(advance(crystal, from = t0, to = metalStart), BuildingType.METAL_MINE, at = metalStart),
        ).state
        assertEquals(crystalDone, both.completionOf(BuildingType.METAL_MINE))

        // when
        val after = advance(both, from = metalStart, to = crystalDone)

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
}
