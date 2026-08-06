package dev.fardavide.oltre.client.colony.presentation

import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Buildings
import dev.fardavide.oltre.core.Coordinates
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ReturningFleet
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.startUpgrade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.TimeZone

class ColonyUiStateTest {

    @Test
    fun `facility rows expose typed level with per-resource cost chips and duration`() {
        // given plenty of metal but no crystal
        val state = colony(resources = Resources.of(metal = 1_000_000))

        // when
        val metalMine = state.rowFor(BuildingType.METAL_MINE)

        // then
        assertEquals("Metal Mine", metalMine.name)
        assertEquals(BuildingLevel(1), metalMine.level)
        assertEquals(
            listOf(
                CostChipUiState(kind = ResourceKind.METAL, amount = "90", short = false),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "22", short = true),
            ),
            metalMine.costs,
        )
        assertEquals("20m", metalMine.duration)
    }

    @Test
    fun `the deuterium cost chip appears only when the building costs deuterium`() {
        // given
        val state = colony()

        // when
        val robotics = state.rowFor(BuildingType.ROBOTICS_FACTORY)

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
        val state = colony(
            buildings = Buildings.initial().withLevel(BuildingType.DEUTERIUM_SYNTHESIZER, BuildingLevel(3)),
        )

        // when
        val synth = state.rowFor(BuildingType.DEUTERIUM_SYNTHESIZER)

        // then
        assertEquals("1h 20m", synth.duration)
    }

    @Test
    fun `an affordable row offers the upgrade action`() {
        // given
        val state = colony(resources = Resources.of(metal = 1_000, crystal = 1_000))

        // when
        val metalMine = state.rowFor(BuildingType.METAL_MINE)

        // then
        assertEquals(FacilityActionUiState.Upgrade, metalMine.action)
    }

    @Test
    fun `an unaffordable row shows the time until affordable instead of a dead button`() {
        // given an empty stock: metal mine → 2 needs 90 metal (90m at 60/h) and 22 crystal (44m)
        val state = colony()

        // when
        val metalMine = state.rowFor(BuildingType.METAL_MINE)

        // then
        assertEquals(FacilityActionUiState.AffordableIn("in 1h 30m"), metalMine.action)
    }

    @Test
    fun `an unaffordable row with no production for a needed resource shows a stalled ghost`() {
        // given no deuterium synthesizer, so the robotics deuterium cost never accrues
        val state = colony(
            resources = Resources.of(metal = 400, crystal = 120),
            buildings = Buildings.initial().withLevel(BuildingType.DEUTERIUM_SYNTHESIZER, BuildingLevel(0)),
        )

        // when
        val robotics = state.rowFor(BuildingType.ROBOTICS_FACTORY)

        // then
        assertEquals(FacilityActionUiState.AffordableIn("—"), robotics.action)
    }

    @Test
    fun `facility rows mark the nanite factory locked below robotics 10`() {
        // given
        val state = colony(resources = Resources.of(metal = 1_000_000))

        // when
        val nanite = state.rowFor(BuildingType.NANITE_FACTORY)

        // then
        assertEquals(FacilityActionUiState.Locked("Requires Robotics 10"), nanite.action)
    }

    @Test
    fun `a building facility carries its own target level countdown and progress`() {
        // given a metal mine upgrade to 2 (20 minutes at robotics 0), a quarter through
        val t0 = Instant.fromEpochMilliseconds(0)
        val started = upgrading(BuildingType.METAL_MINE, at = t0)

        // when
        val metalMine = started.rowFor(BuildingType.METAL_MINE, now = t0 + 5.minutes)

        // then
        assertEquals(
            FacilityActionUiState.Upgrading(
                toLevel = BuildingLevel(2),
                countdown = "00:15:00",
                progressPercent = 25,
                doneAt = "done 00:20",
            ),
            metalMine.action,
        )
    }

    @Test
    fun `only the building facility shows progress while the rest stay actionable`() {
        // given
        val t0 = Instant.fromEpochMilliseconds(0)
        val started = upgrading(BuildingType.METAL_MINE, at = t0)

        // when
        val rows = started.toColonyUiState(now = t0, timeZone = TimeZone.UTC).facilities

        // then
        assertEquals(
            listOf(BuildingType.METAL_MINE),
            rows.filter { it.action is FacilityActionUiState.Upgrading }.map { it.building },
        )
    }

    @Test
    fun `a building facility shows the local completion time`() {
        // given a 20-minute build started 2026-08-06T10:00Z, viewed from UTC+2
        val t0 = Instant.parse("2026-08-06T10:00:00Z")
        val started = upgrading(BuildingType.METAL_MINE, at = t0)

        // when
        val action = started.rowFor(BuildingType.METAL_MINE, now = t0, timeZone = TimeZone.of("Europe/Rome")).action

        // then
        assertEquals("done 12:20", assertIs<FacilityActionUiState.Upgrading>(action).doneAt)
    }

    @Test
    fun `countdown ceils a sub-second remainder so zero means done`() {
        // given a build with 900ms left
        val t0 = Instant.fromEpochMilliseconds(0)
        val started = upgrading(BuildingType.METAL_MINE, at = t0)
        val completesAt = checkNotNull(started.builds[BuildingType.METAL_MINE]).completesAt

        // when
        val action = started.rowFor(BuildingType.METAL_MINE, now = completesAt - 900.milliseconds).action

        // then
        assertEquals("00:00:01", assertIs<FacilityActionUiState.Upgrading>(action).countdown)
    }

    @Test
    fun `a returning fleet appears as the strip with origin composition and countdown`() {
        // given a fleet of 14 cargo and 1 cruiser from [2:117:9], 4h 11m 52s out
        val now = Instant.fromEpochMilliseconds(0)
        val state = colony().copy(
            returningFleet = ReturningFleet(
                ships = mapOf(ShipType.CARGO to 14, ShipType.CRUISER to 1),
                cargo = Resources.of(metal = 500),
                origin = Coordinates(galaxy = 2, system = 117, position = 9),
                arrivesAt = now + 4.hours + 11.minutes + 52.seconds,
            ),
        )

        // when
        val strip = state.toColonyUiState(now = now, timeZone = TimeZone.UTC).returningFleet

        // then
        assertEquals(
            ReturningFleetUiState(
                title = "Fleet returning",
                subtitle = "from [2:117:9] · 14 cargo · 1 cruiser",
                countdown = "04:11:52",
            ),
            checkNotNull(strip),
        )
    }

    @Test
    fun `no fleet in flight means no strip`() {
        // given
        val state = colony()

        // when
        val strip = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0), timeZone = TimeZone.UTC).returningFleet

        // then
        assertEquals(null, strip)
    }

    private fun colony(
        resources: Resources = Resources.of(),
        buildings: Buildings = Buildings.initial(),
    ): GameState = GameState(
        resources = resources,
        buildings = buildings,
        builds = emptyMap(),
        research = Research.initial(),
        activeResearch = null,
        returningFleet = null,
        eventLog = emptyList(),
    )

    private fun upgrading(building: BuildingType, at: Instant): GameState {
        val cost = PlaceholderBalance.upgradeCost(building, BuildingLevel(2))
        val funded = colony(resources = Resources.of(metal = cost.metal, crystal = cost.crystal))
        return assertIs<StartUpgradeResult.Started>(startUpgrade(funded, building, at = at)).state
    }

    private fun GameState.rowFor(
        building: BuildingType,
        now: Instant = Instant.fromEpochMilliseconds(0),
        timeZone: TimeZone = TimeZone.UTC,
    ): FacilityRowUiState =
        toColonyUiState(now = now, timeZone = timeZone).facilities.first { it.building == building }
}
