package dev.fardavide.oltre.client.colony.presentation

import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Buildings
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.startUpgrade
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.test.assertEquals

class ColonyUiStateTest {

    @Test
    fun `metal stock is formatted with thousands separators`() {
        // given
        val state = GameState(resources = Resources.of(metal = 482_910), buildings = Buildings.initial(), buildQueue = null, eventLog = emptyList())

        // when
        val uiState = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0))

        // then
        assertEquals("482,910", uiState.metal)
    }

    @Test
    fun `metal production rate reflects the effective rate of the current buildings`() {
        // given
        val buildings = Buildings.initial().copy(
            metalMine = BuildingLevel(2),
            solarPlant = BuildingLevel(2),
        )
        val state = GameState(
            resources = Resources.of(metal = 0),
            buildings = buildings,
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
    fun `facility rows expose level, cost and affordability`() {
        // given plenty of metal but no crystal
        val state = GameState(
            resources = Resources.of(metal = 1_000_000),
            buildings = Buildings.initial(),
            buildQueue = null,
            eventLog = emptyList(),
        )

        // when
        val rows = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0)).facilities

        // then
        val metalMine = rows.first { it.building == BuildingType.METAL_MINE }
        assertEquals("Metal Mine", metalMine.name)
        assertEquals(1, metalMine.level)
        assertEquals("120", metalMine.metalCost)
        assertEquals("30", metalMine.crystalCost)
        assertEquals(false, metalMine.affordable)

        val solar = rows.first { it.building == BuildingType.SOLAR_PLANT }
        assertEquals("Solar Plant", solar.name)
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
        val nanite = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0)).facilities.first { it.building == BuildingType.NANITE_FACTORY }

        // then
        assertEquals(true, nanite.locked)
        assertEquals("Requires Robotics 10", nanite.lockedReason)
    }

    @Test
    fun `an in-flight build appears as the in-progress card with countdown and progress`() {
        // given a metal mine upgrade to 2 (20 minutes at robotics 0), a quarter through
        val t0 = Instant.fromEpochMilliseconds(0)
        val cost = PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2))
        val funded = GameState.initial().copy(
            resources = Resources.of(metal = cost.metal, crystal = cost.crystal),
        )
        val started = (startUpgrade(funded, BuildingType.METAL_MINE, at = t0) as StartUpgradeResult.Started).state

        // when
        val card = started.toColonyUiState(now = t0 + 5.minutes).inProgress

        // then
        assertEquals(
            InProgressUiState(
                title = "Metal Mine \u2192 2",
                countdown = "00:15:00",
                progressPercent = 25,
            ),
            checkNotNull(card),
        )
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
