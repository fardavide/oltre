package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        val expectedCompletion = now + PlaceholderBalance.upgradeDuration(BuildingType.METAL_MINE, toLevel, BuildingLevel(0), BuildingLevel(0))
        assertEquals(
            mapOf(
                BuildingType.METAL_MINE to BuildJob(
                    building = BuildingType.METAL_MINE,
                    toLevel = toLevel,
                    startedAt = now,
                    completesAt = expectedCompletion,
                ),
            ),
            started.state.builds,
        )
    }

    @Test
    fun `starting a second upgrade of the same facility is rejected`() {
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
        assertIs<StartUpgradeResult.AlreadyUpgrading>(second)
    }

    @Test
    fun `starting an unaffordable upgrade is rejected without changing state`() {
        // given
        val now = Instant.fromEpochMilliseconds(0)
        val broke = GameState.initial().copy(resources = Resources.of())

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
        val after = advance(started, from = now, to = started.completionOf(BuildingType.CRYSTAL_MINE))

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
        val after = advance(started, from = now, to = started.completionOf(BuildingType.DEUTERIUM_SYNTHESIZER))

        // then
        assertEquals(BuildingLevel(2), after.buildings.deuteriumSynthesizer)
        assertEquals(BuildingLevel(1), after.buildings.metalMine)
    }

    @Test
    fun `the robotics factory shortens build durations`() {
        // given a mine deep enough for the divisor to have something to divide. Since the opening
        // discount went to 10x a second Metal Mine is an eight-minute build, and a third of eight
        // minutes is under the five-minute floor — so at level 2 this would assert the floor and
        // call it the factory. Level 6 is the first that clears it at Robotics 2.
        val now = Instant.fromEpochMilliseconds(0)
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(6))
        val initial = GameState.initial()
        val slow = initial.copy(
            buildings = initial.buildings.copy(metalMine = BuildingLevel(5)),
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal),
        )
        val fast = slow.copy(
            buildings = slow.buildings.copy(roboticsFactory = BuildingLevel(2)),
        )

        // when
        val slowJob = assertIs<StartUpgradeResult.Started>(startUpgrade(slow, BuildingType.METAL_MINE, at = now))
            .state.jobOf(BuildingType.METAL_MINE)
        val fastJob = assertIs<StartUpgradeResult.Started>(startUpgrade(fast, BuildingType.METAL_MINE, at = now))
            .state.jobOf(BuildingType.METAL_MINE)

        // then
        val base = PlaceholderBalance.upgradeDuration(BuildingType.METAL_MINE, BuildingLevel(6), BuildingLevel(0), BuildingLevel(0))
        assertEquals(now + base, slowJob.completesAt)
        assertEquals(now + base / 3, fastJob.completesAt)
    }

    @Test
    fun `the nanite factory is locked below robotics level 10`() {
        // given
        val now = Instant.fromEpochMilliseconds(0)
        val funded = naniteFunded()

        // when
        val result = startUpgrade(funded, BuildingType.NANITE_FACTORY, at = now)

        // then
        assertIs<StartUpgradeResult.RequirementsNotMet>(result)
    }

    @Test
    fun `the nanite factory unlocks at robotics level 10`() {
        // given
        val now = Instant.fromEpochMilliseconds(0)
        val unlocked = naniteFunded().let {
            it.copy(buildings = it.buildings.copy(roboticsFactory = BuildingLevel(10)))
        }

        // when
        val result = startUpgrade(unlocked, BuildingType.NANITE_FACTORY, at = now)

        // then
        assertIs<StartUpgradeResult.Started>(result)
    }

    @Test
    fun `starting an upgrade records a BuildStarted event`() {
        // given
        val now = Instant.fromEpochMilliseconds(0)
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2))
        val funded = GameState.initial().copy(
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal),
        )

        // when
        val started = assertIs<StartUpgradeResult.Started>(
            startUpgrade(funded, BuildingType.METAL_MINE, at = now),
        ).state

        // then
        assertEquals(
            listOf<Event>(
                Event.BuildStarted(building = BuildingType.METAL_MINE, toLevel = BuildingLevel(2), at = now),
            ),
            started.eventLog,
        )
    }

    @Test
    fun `an upgrade cost that would overflow is rejected loudly`() {
        assertFailsWith<IllegalArgumentException> {
            PlaceholderBalance.upgradeCost(BuildingType.NANITE_FACTORY, BuildingLevel(41))
        }
    }

    @Test
    fun `a negative building level is unrepresentable`() {
        assertFailsWith<IllegalArgumentException> {
            BuildingLevel(-1)
        }
    }

    private fun naniteFunded(): GameState {
        val cost = PlaceholderBalance.upgradeCost(BuildingType.NANITE_FACTORY, BuildingLevel(1))
        return GameState.initial().copy(
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal, deuterium = cost.deuterium),
        )
    }

    @Test
    fun `the build job records its start instant`() {
        // given
        val now = Instant.fromEpochMilliseconds(42_000)
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2))
        val funded = GameState.initial().copy(
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal),
        )

        // when
        val started = assertIs<StartUpgradeResult.Started>(
            startUpgrade(funded, BuildingType.METAL_MINE, at = now),
        ).state

        // then
        assertEquals(now, started.jobOf(BuildingType.METAL_MINE).startedAt)
    }
}
