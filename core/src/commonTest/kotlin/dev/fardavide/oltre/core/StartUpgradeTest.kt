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
        val expectedCompletion = now + PlaceholderBalance.upgradeDuration(BuildingType.METAL_MINE, toLevel, BuildingLevel(0))
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

    @Test
    fun `upgrading the crystal mine levels the crystal mine and only it`() {
        // given
        val now = Instant.fromEpochMilliseconds(0)
        val cost = PlaceholderBalance.upgradeCost(BuildingType.CRYSTAL_MINE, BuildingLevel(2))
        val funded = GameState.initial().copy(
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal),
        )
        val started = assertIs<StartUpgradeResult.Started>(
            startUpgrade(funded, BuildingType.CRYSTAL_MINE, at = now),
        ).state

        // when
        val after = advance(started, from = now, to = checkNotNull(started.buildQueue).completesAt)

        // then
        assertEquals(BuildingLevel(2), after.buildings.crystalMine)
        assertEquals(BuildingLevel(1), after.buildings.metalMine)
    }

    @Test
    fun `upgrading the deuterium synthesizer levels it and only it`() {
        // given
        val now = Instant.fromEpochMilliseconds(0)
        val cost = PlaceholderBalance.upgradeCost(BuildingType.DEUTERIUM_SYNTHESIZER, BuildingLevel(2))
        val funded = GameState.initial().copy(
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal),
        )
        val started = assertIs<StartUpgradeResult.Started>(
            startUpgrade(funded, BuildingType.DEUTERIUM_SYNTHESIZER, at = now),
        ).state

        // when
        val after = advance(started, from = now, to = checkNotNull(started.buildQueue).completesAt)

        // then
        assertEquals(BuildingLevel(2), after.buildings.deuteriumSynthesizer)
        assertEquals(BuildingLevel(1), after.buildings.metalMine)
    }

    @Test
    fun `the robotics factory shortens build durations`() {
        // given
        val now = Instant.fromEpochMilliseconds(0)
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2))
        val slow = GameState.initial().copy(
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal),
        )
        val fast = slow.copy(
            buildings = slow.buildings.copy(roboticsFactory = BuildingLevel(2)),
        )

        // when
        val slowJob = checkNotNull(
            assertIs<StartUpgradeResult.Started>(startUpgrade(slow, BuildingType.METAL_MINE, at = now)).state.buildQueue,
        )
        val fastJob = checkNotNull(
            assertIs<StartUpgradeResult.Started>(startUpgrade(fast, BuildingType.METAL_MINE, at = now)).state.buildQueue,
        )

        // then
        val base = PlaceholderBalance.upgradeDuration(BuildingType.METAL_MINE, BuildingLevel(2), BuildingLevel(0))
        assertEquals(now + base, slowJob.completesAt)
        assertEquals(now + base / 3, fastJob.completesAt)
    }

    @Test
    fun `the nanite factory is locked until robotics reaches level 10`() {
        // given
        val now = Instant.fromEpochMilliseconds(0)
        val cost = PlaceholderBalance.upgradeCost(BuildingType.NANITE_FACTORY, BuildingLevel(1))
        val funded = GameState.initial().copy(
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal, deuterium = cost.deuterium),
        )

        // when / then
        assertIs<StartUpgradeResult.RequirementsNotMet>(
            startUpgrade(funded, BuildingType.NANITE_FACTORY, at = now),
        )

        val unlocked = funded.copy(
            buildings = funded.buildings.copy(roboticsFactory = BuildingLevel(10)),
        )
        assertIs<StartUpgradeResult.Started>(
            startUpgrade(unlocked, BuildingType.NANITE_FACTORY, at = now),
        )
    }
}
