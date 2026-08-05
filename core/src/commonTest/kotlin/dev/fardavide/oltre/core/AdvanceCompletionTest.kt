package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
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
        val completesAt = checkNotNull(started.buildQueue).completesAt

        // when
        val after = advance(started, from = t0, to = completesAt + 1.hours)

        // then
        assertEquals(BuildingLevel(2), after.buildings.metalMine)
        assertNull(after.buildQueue)
        assertEquals(
            listOf(
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
        val completesAt = checkNotNull(started.buildQueue).completesAt

        // when
        val after = advance(started, from = t0, to = completesAt + 1.hours)

        // then
        val buildSpanHours = (completesAt - t0).inWholeMilliseconds
        val expectedMetalFine =
            PlaceholderBalance.metalProductionPerHour(BuildingLevel(1)) * buildSpanHours +
                PlaceholderBalance.metalProductionPerHour(BuildingLevel(2)) * 1.hours.inWholeMilliseconds
        assertEquals(expectedMetalFine / 3_600_000, after.resources.metal)
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
        val completesAt = checkNotNull(started.buildQueue).completesAt
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
}
