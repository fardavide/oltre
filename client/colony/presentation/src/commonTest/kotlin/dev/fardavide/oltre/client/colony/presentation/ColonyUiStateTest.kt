package dev.fardavide.oltre.client.colony.presentation

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Buildings
import dev.fardavide.oltre.core.Coordinates
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.Research
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
    fun `a colony within its power budget counts its headroom in the levels it would buy`() {
        // given a new colony: 50 produced against 40 drawn
        val state = colony()

        // when
        val energy = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0), timeZone = TimeZone.UTC).energy

        // then the track spans the larger term, so the fill is the draw and the empty tail is
        // the headroom the verdict names
        assertEquals(
            EnergyUiState(
                verdict = "room for 1 mine level",
                terms = "50 produced · 40 drawn · 10 spare",
                coveredFraction = 40f / 50f,
                deficit = false,
            ),
            energy,
        )
    }

    @Test
    fun `headroom for more than one level reads as a plural`() {
        // given solar 4 against metal 5, crystal 4 and deuterium 4: 200 produced, 170 drawn
        val state = colony(
            buildings = Buildings(
                metalMine = BuildingLevel(5),
                crystalMine = BuildingLevel(4),
                deuteriumSynthesizer = BuildingLevel(4),
                solarPlant = BuildingLevel(4),
                roboticsFactory = BuildingLevel(0),
                naniteFactory = BuildingLevel(0),
            ),
        )

        // when
        val energy = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0), timeZone = TimeZone.UTC).energy

        // then
        assertEquals("room for 3 mine levels", energy.verdict)
    }

    @Test
    fun `a colony that exactly covers its draw reads as break even`() {
        // given metal mine 2: 50 produced against 50 drawn, the upgrade after the first one
        val state = colony(
            buildings = Buildings.initial().withLevel(BuildingType.METAL_MINE, BuildingLevel(2)),
        )

        // when
        val energy = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0), timeZone = TimeZone.UTC).energy

        // then "room for 0 mine levels" is a sentence about nothing; this is the same fact
        assertEquals("break even", energy.verdict)
        assertEquals(false, energy.deficit)
    }

    @Test
    fun `a power shortage names the rate it is costing every mine`() {
        // given the colony from Davide's report — metal 3, crystal 2, deuterium 2, solar 1 —
        // which was losing 45% of every mine with nothing on screen to say so
        val state = colony(buildings = starved())

        // when
        val energy = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0), timeZone = TimeZone.UTC).energy

        // then the fill stops where the plant stops, so the boundary is the plant's ceiling
        assertEquals(
            EnergyUiState(
                verdict = "every mine at 55%",
                terms = "50 produced · 90 drawn · 40 short",
                coveredFraction = 50f / 90f,
                deficit = true,
            ),
            energy,
        )
    }

    @Test
    fun `a colony with no plant at all reports every mine stopped`() {
        // given there is no floor: at solar 0 production is 0 and every mine stops dead
        val state = colony(buildings = starved().withLevel(BuildingType.SOLAR_PLANT, BuildingLevel(0)))

        // when
        val energy = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0), timeZone = TimeZone.UTC).energy

        // then a track with no green in it already says the state is total, so no new colour
        assertEquals("every mine stopped", energy.verdict)
        assertEquals(0f, energy.coveredFraction)
    }

    @Test
    fun `a shortage attributes itself to each facility's own signed figure`() {
        // given
        val state = colony(buildings = starved())

        // when
        val rows = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0), timeZone = TimeZone.UTC).facilities

        // then the percentage is not repeated per card — each mine floors independently, so three
        // cards saying "55%" would each be slightly wrong. The draw is exactly true per card.
        assertEquals(
            mapOf(
                BuildingType.METAL_MINE to FacilityPowerUiState(label = "−30", supply = false),
                BuildingType.CRYSTAL_MINE to FacilityPowerUiState(label = "−20", supply = false),
                BuildingType.DEUTERIUM_SYNTHESIZER to FacilityPowerUiState(label = "−40", supply = false),
                BuildingType.SOLAR_PLANT to FacilityPowerUiState(label = "+50", supply = true),
            ),
            rows.mapNotNull { row -> row.power?.let { row.building to it } }.toMap(),
        )
    }

    @Test
    fun `an unbuilt facility draws nothing so it carries no mark`() {
        // given a shortage, with robotics and nanite both at level 0
        val state = colony(buildings = starved())

        // when
        val rows = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0), timeZone = TimeZone.UTC).facilities

        // then there is nothing to attribute, and nothing to fight the locked row's dim
        assertEquals(null, rows.first { it.building == BuildingType.NANITE_FACTORY }.power)
        assertEquals(null, rows.first { it.building == BuildingType.ROBOTICS_FACTORY }.power)
    }

    @Test
    fun `no facility is marked while the colony is within its power budget`() {
        // given
        val state = colony()

        // when
        val rows = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0), timeZone = TimeZone.UTC).facilities

        // then the mark is the deficit's vocabulary; a healthy colony has nothing to say with it
        assertEquals(emptyList(), rows.filter { it.power != null }.map { it.building })
    }

    @Test
    fun `the plant that would end the shortage says so on its own card`() {
        // given the report's colony: 50 produced against 90 drawn, and a solar plant one level
        // from covering all of it
        val state = colony(buildings = starved())

        // when
        val rows = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0), timeZone = TimeZone.UTC).facilities

        // then no banner and no reordering — one line, in the slot a card already uses to say
        // what its next level is
        assertEquals("→ LV 2 covers all 90 drawn", rows.first { it.building == BuildingType.SOLAR_PLANT }.fix)
        assertEquals(emptyList(), rows.filter { it.building != BuildingType.SOLAR_PLANT && it.fix != null })
    }

    @Test
    fun `no fix is offered when one plant level would not be enough`() {
        // given metal mine 15, which takes the draw to 210 against a plant that reaches 100
        val state = colony(buildings = starved().withLevel(BuildingType.METAL_MINE, BuildingLevel(15)))

        // when
        val rows = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0), timeZone = TimeZone.UTC).facilities

        // then the line states a fact or it is absent; it never states an intention
        assertEquals(null, rows.first { it.building == BuildingType.SOLAR_PLANT }.fix)
    }

    @Test
    fun `a healthy colony is offered no fix`() {
        // given
        val state = colony()

        // when
        val rows = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0), timeZone = TimeZone.UTC).facilities

        // then
        assertEquals(emptyList(), rows.filter { it.fix != null })
    }

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
        // given an empty stock: metal mine → 2 needs 90 metal (60m at 90/h) and 22 crystal (44m)
        val state = colony()

        // when
        val metalMine = state.rowFor(BuildingType.METAL_MINE)

        // then
        assertEquals(FacilityActionUiState.AffordableIn("in 1h 00m"), metalMine.action)
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

    // Seven levels of mine on one solar plant: 50 produced against 90 consumed.
    private fun starved(): Buildings = Buildings(
        metalMine = BuildingLevel(3),
        crystalMine = BuildingLevel(2),
        deuteriumSynthesizer = BuildingLevel(2),
        solarPlant = BuildingLevel(1),
        roboticsFactory = BuildingLevel(0),
        naniteFactory = BuildingLevel(0),
    )

    private fun colony(
        resources: Resources = Resources.of(),
        buildings: Buildings = Buildings.initial(),
    ): GameState = GameState(
        resources = resources,
        buildings = buildings,
        builds = emptyMap(),
        research = Research.initial(),
        activeResearch = null,
        // `GameState.initial` takes a galaxy seed rather than defaulting one, so production cannot
        // found every colony in the same galaxy. The Colony screen draws none of it.
        galaxy = GameState.initial(GalaxySeed(20_260_807)).galaxy,
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
