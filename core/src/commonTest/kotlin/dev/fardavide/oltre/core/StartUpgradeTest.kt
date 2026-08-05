package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class StartUpgradeTest {

    @Test
    fun `starting an upgrade deducts its cost and enqueues the job`() {
        // given
        val now = Instant.fromEpochMilliseconds(0)
        val toLevel = BuildingLevel(2)
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, toLevel)
        val state = GameState.initial().copy(
            resources = Resources.of(metal = cost.metal + 7, crystal = cost.crystal + 5),
        )

        // when
        val result = startUpgrade(state, BuildingType.METAL_MINE, at = now)

        // then
        val started = assertIs<StartUpgradeResult.Started>(result)
        assertEquals(7, started.state.resources.metal)
        assertEquals(5, started.state.resources.crystal)
        val expectedCompletion = now + PlaceholderBalance.upgradeDuration(BuildingType.METAL_MINE, toLevel)
        assertEquals(
            BuildJob(building = BuildingType.METAL_MINE, toLevel = toLevel, completesAt = expectedCompletion),
            started.state.buildQueue,
        )
    }

    @Test
    fun `starting an upgrade while another is in progress is rejected`() {
        // given
        val now = Instant.fromEpochMilliseconds(0)
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2))
        val funded = GameState.initial().copy(
            resources = Resources.of(metal = cost.metal * 10, crystal = cost.crystal * 10),
        )
        val started = assertIs<StartUpgradeResult.Started>(
            startUpgrade(funded, BuildingType.METAL_MINE, at = now),
        ).state

        // when
        val second = startUpgrade(started, BuildingType.METAL_MINE, at = now)

        // then
        assertIs<StartUpgradeResult.QueueBusy>(second)
    }

    @Test
    fun `starting an unaffordable upgrade is rejected without changing state`() {
        // given
        val now = Instant.fromEpochMilliseconds(0)
        val broke = GameState.initial()

        // when
        val result = startUpgrade(broke, BuildingType.METAL_MINE, at = now)

        // then
        assertIs<StartUpgradeResult.InsufficientResources>(result)
    }
}
