package dev.fardavide.oltre.client.colony.presentation

import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Buildings
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.startUpgrade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class ColonyUiStateTest {

    @Test
    fun `metal stock is grouped by thousands`() {
        // given
        val state = GameState(resources = Resources.of(metal = 482_910), buildings = Buildings.initial(), buildQueue = null, eventLog = emptyList())

        // when
        val uiState = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0))

        // then
        assertEquals("482,910", uiState.metal)
    }

    @Test
    fun `metal rate reflects the mine level`() {
        // given
        val state = GameState(
            resources = Resources.of(),
            buildings = Buildings.initial().withLevel(BuildingType.METAL_MINE, BuildingLevel(2)),
            buildQueue = null,
            eventLog = emptyList(),
        )

        // when
        val uiState = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0))

        // then
        assertEquals("+7,200/h", uiState.metalRatePerHour)
    }

    @Test
    fun `all three resources appear with stock and rate`() {
        // given
        val state = GameState(
            resources = Resources.of(metal = 1_000, crystal = 2_000, deuterium = 3_000),
            buildings = Buildings.initial(),
            buildQueue = null,
            eventLog = emptyList(),
        )

        // when
        val uiState = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0))

        // then
        assertEquals("2,000", uiState.crystal)
        assertEquals("+1,800/h", uiState.crystalRatePerHour)
        assertEquals("3,000", uiState.deuterium)
        assertEquals("+900/h", uiState.deuteriumRatePerHour)
    }

    @Test
    fun `facility rows expose typed level, per-resource cost chips and duration`() {
        // given plenty of metal but no crystal
        val state = GameState(
            resources = Resources.of(metal = 1_000_000),
            buildings = Buildings.initial(),
            buildQueue = null,
            eventLog = emptyList(),
        )

        // when
        val metalMine = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0))
            .facilities.first { it.building == BuildingType.METAL_MINE }

        // then
        assertEquals("Metal Mine", metalMine.name)
        assertEquals(BuildingLevel(1), metalMine.level)
        assertEquals(
            listOf(
                CostChipUiState(kind = ResourceKind.METAL, amount = "120", short = false),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "30", short = true),
            ),
            metalMine.costs,
        )
        assertEquals("20m", metalMine.duration)
    }

    @Test
    fun `the deuterium cost chip appears only when the building costs deuterium`() {
        // given
        val state = GameState.initial()

        // when
        val robotics = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0))
            .facilities.first { it.building == BuildingType.ROBOTICS_FACTORY }

        // then
        assertEquals(
            listOf(
                CostChipUiState(kind = ResourceKind.METAL, amount = "400", short = true),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "120", short = true),
                CostChipUiState(kind = ResourceKind.DEUTERIUM, amount = "200", short = true),
            ),
            robotics.costs,
        )
    }

    @Test
    fun `durations of an hour or more read as hours and padded minutes`() {
        // given deuterium synth 3 → level 4 takes 80 minutes at robotics 0
        val state = GameState(
            resources = Resources.of(),
            buildings = Buildings.initial().withLevel(BuildingType.DEUTERIUM_SYNTHESIZER, BuildingLevel(3)),
            buildQueue = null,
            eventLog = emptyList(),
        )

        // when
        val synth = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0))
            .facilities.first { it.building == BuildingType.DEUTERIUM_SYNTHESIZER }

        // then
        assertEquals("1h 20m", synth.duration)
    }

    @Test
    fun `an affordable row offers the upgrade action`() {
        // given
        val state = GameState(
            resources = Resources.of(metal = 1_000, crystal = 1_000),
            buildings = Buildings.initial(),
            buildQueue = null,
            eventLog = emptyList(),
        )

        // when
        val metalMine = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0))
            .facilities.first { it.building == BuildingType.METAL_MINE }

        // then
        assertEquals(FacilityActionUiState.Upgrade, metalMine.action)
    }

    @Test
    fun `an unaffordable row shows the time until affordable instead of a dead button`() {
        // given an empty stock: metal mine → 2 needs 120 metal (2m at 3,600/h) and 30 crystal (1m)
        val state = GameState.initial()

        // when
        val metalMine = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0))
            .facilities.first { it.building == BuildingType.METAL_MINE }

        // then
        assertEquals(FacilityActionUiState.AffordableIn("in 2m"), metalMine.action)
    }

    @Test
    fun `an unaffordable row with no production for a needed resource shows a stalled ghost`() {
        // given no deuterium synthesizer, so the robotics deuterium cost never accrues
        val state = GameState(
            resources = Resources.of(metal = 400, crystal = 120),
            buildings = Buildings.initial().withLevel(BuildingType.DEUTERIUM_SYNTHESIZER, BuildingLevel(0)),
            buildQueue = null,
            eventLog = emptyList(),
        )

        // when
        val robotics = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0))
            .facilities.first { it.building == BuildingType.ROBOTICS_FACTORY }

        // then
        assertEquals(FacilityActionUiState.AffordableIn("—"), robotics.action)
    }

    @Test
    fun `facility rows mark the nanite factory locked below robotics 10`() {
        // given
        val state = GameState(
            resources = Resources.of(metal = 1_000_000),
            buildings = Buildings.initial(),
            buildQueue = null,
            eventLog = emptyList(),
        )

        // when
        val nanite = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0))
            .facilities.first { it.building == BuildingType.NANITE_FACTORY }

        // then
        assertEquals(FacilityActionUiState.Locked("Requires Robotics 10"), nanite.action)
    }

    @Test
    fun `an in-flight build appears as the in-progress card with countdown and progress`() {
        // given a metal mine upgrade to 2 (20 minutes at robotics 0), a quarter through
        val t0 = Instant.fromEpochMilliseconds(0)
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2))
        val funded = GameState.initial().copy(
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal),
        )
        val started = assertIs<StartUpgradeResult.Started>(
            startUpgrade(funded, BuildingType.METAL_MINE, at = t0),
        ).state

        // when
        val card = started.toColonyUiState(now = t0 + 5.minutes).inProgress

        // then
        assertEquals(
            InProgressUiState(
                title = "Metal Mine → 2",
                countdown = "00:15:00",
                progressPercent = 25,
            ),
            checkNotNull(card),
        )
    }

    @Test
    fun `countdown ceils a sub-second remainder so zero means done`() {
        // given a build with 900ms left
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
        val card = started.toColonyUiState(now = completesAt - 900.milliseconds).inProgress

        // then
        assertEquals("00:00:01", checkNotNull(card).countdown)
    }

    @Test
    fun `an empty queue shows no in-progress card`() {
        // given
        val state = GameState.initial()

        // when
        val card = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0)).inProgress

        // then
        assertEquals(null, card)
    }
}
