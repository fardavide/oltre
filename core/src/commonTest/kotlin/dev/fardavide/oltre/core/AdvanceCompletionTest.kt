package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class AdvanceCompletionTest {

    @Test
    fun `advance completes a queued upgrade at its completion instant`() {
        // given
        val t0 = Instant.fromEpochMilliseconds(0)
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2))
        val funded = GameState.initial().copy(
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal),
        )
        val started = assertIs<StartUpgradeResult.Started>(
            startUpgrade(funded, BuildingType.METAL_MINE, at = t0),
        ).state
        val completesAt = started.completionOf(BuildingType.METAL_MINE)

        // when
        val after = advance(started, from = t0, to = completesAt + 1.hours)

        // then
        assertEquals(BuildingLevel(2), after.buildings.metalMine)
        assertTrue(after.builds.isEmpty(), "completed job must leave the build map")
        assertEquals(
            listOf(
                Event.BuildStarted(
                    building = BuildingType.METAL_MINE,
                    toLevel = BuildingLevel(2),
                    at = t0,
                ),
                Event.BuildCompleted(
                    building = BuildingType.METAL_MINE,
                    newLevel = BuildingLevel(2),
                    at = completesAt,
                ),
            ),
            after.eventLog,
        )
    }

    @Test
    fun `production switches to the new rate only after the completion instant`() {
        // given
        val t0 = Instant.fromEpochMilliseconds(0)
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2))
        val funded = GameState.initial().copy(
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal),
        )
        val started = assertIs<StartUpgradeResult.Started>(
            startUpgrade(funded, BuildingType.METAL_MINE, at = t0),
        ).state
        val completesAt = started.completionOf(BuildingType.METAL_MINE)

        // when
        val after = advance(started, from = t0, to = completesAt + 1.hours)

        // then
        val buildSpanHours = (completesAt - t0).inWholeMilliseconds
        val expectedMetalFine =
            PlaceholderBalance.metalProductionPerHour(BuildingLevel(1)) * buildSpanHours +
                PlaceholderBalance.metalProductionPerHour(BuildingLevel(2)) * 1.hours.inWholeMilliseconds
        assertEquals(expectedMetalFine / Resources.FINE_PER_UNIT, after.resources.metal)
    }

    @Test
    fun `composability holds across a completion boundary for any split`() {
        // given
        val t0 = Instant.fromEpochMilliseconds(0)
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2))
        val funded = GameState.initial().copy(
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal),
        )
        val started = assertIs<StartUpgradeResult.Started>(
            startUpgrade(funded, BuildingType.METAL_MINE, at = t0),
        ).state
        val completesAt = started.completionOf(BuildingType.METAL_MINE)
        val t2 = completesAt + 3.hours
        val oneShot = advance(started, from = t0, to = t2)

        val splits = listOf(
            t0 + 1.milliseconds,
            completesAt - 1.milliseconds,
            completesAt,
            completesAt + 1.milliseconds,
            t2 - 1.milliseconds,
        )
        for (t1 in splits) {
            // when
            val stepped = advance(advance(started, from = t0, to = t1), from = t1, to = t2)

            // then
            assertEquals(oneShot, stepped, "split at $t1 diverged")
        }
    }

    @Test
    fun `composability holds for sub-millisecond wall-clock instants`() {
        // given a start instant carrying a 600 microsecond fraction, as real clocks produce
        val t0 = Instant.fromEpochSeconds(0, 600_000)
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2))
        val funded = GameState.initial().copy(
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal),
        )
        val started = assertIs<StartUpgradeResult.Started>(
            startUpgrade(funded, BuildingType.METAL_MINE, at = t0),
        ).state
        val t2 = started.completionOf(BuildingType.METAL_MINE) + 3.hours
        val oneShot = advance(started, from = t0, to = t2)

        val splits = listOf(
            Instant.fromEpochMilliseconds(100_000),
            Instant.fromEpochSeconds(200, 123_456_789),
            started.completionOf(BuildingType.METAL_MINE) - 1.milliseconds,
        )
        for (t1 in splits) {
            // when
            val stepped = advance(advance(started, from = t0, to = t1), from = t1, to = t2)

            // then
            assertEquals(oneShot, stepped, "split at $t1 diverged")
        }
    }

    @Test
    fun `a job whose completion predates the span still completes`() {
        // given a state resumed with a from beyond the job's completion instant
        val t0 = Instant.fromEpochMilliseconds(0)
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2))
        val funded = GameState.initial().copy(
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal),
        )
        val started = assertIs<StartUpgradeResult.Started>(
            startUpgrade(funded, BuildingType.METAL_MINE, at = t0),
        ).state
        val completesAt = started.completionOf(BuildingType.METAL_MINE)
        val lateFrom = completesAt + 2.hours

        // when
        val after = advance(started, from = lateFrom, to = lateFrom + 1.hours)

        // then
        assertEquals(BuildingLevel(2), after.buildings.metalMine)
        assertTrue(after.builds.isEmpty(), "completed job must leave the build map")
    }

    @Test
    fun `advancing backwards in time is rejected`() {
        // given
        val t0 = Instant.fromEpochMilliseconds(1_000_000)
        val state = GameState.initial()

        // when / then
        assertFailsWith<IllegalArgumentException> {
            advance(state, from = t0, to = t0 - 1.hours)
        }
    }
}
