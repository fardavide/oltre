package dev.fardavide.oltre.client.colony.presentation

import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.component.WatchUiState
import dev.fardavide.oltre.client.design.format.pad2
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Buildings
import dev.fardavide.oltre.core.FleetRun
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.GalaxySeed
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.PlaceholderBalance
import dev.fardavide.oltre.core.Research
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.Resources
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.WatchTarget
import dev.fardavide.oltre.core.startUpgrade
import dev.fardavide.oltre.core.timeUntilAffordable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
                CostChipUiState(kind = ResourceKind.METAL, amount = "19", short = false),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "4", short = true),
            ),
            metalMine.costs,
        )
        // 19 and 4 rather than the full-price 90 and 22: since 0.2.7 the opening is sold at a
        // **tenth** of full price, climbing in equal steps to full price at level 9.
        //
        // The clock rides a ramp of its own and a steeper one — two thirds a level, converging on
        // the same level 9 — because Davide asked for the first taps in minutes rather than as a
        // multiple: "a 2/3 min build time at the very first levels". Four minutes per root of the
        // *full* 112 is 40, and two thirds of that seven times over is this.
        assertEquals("2m", metalMine.duration)
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
                // 400 / 120 / 200 at full price, here on the deepest step of the opening
                // discount: level 1 pays exactly a tenth of it.
                CostChipUiState(kind = ResourceKind.METAL, amount = "40", short = true),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = "12", short = true),
                CostChipUiState(kind = ResourceKind.DEUTERIUM, amount = "20", short = true),
            ),
            robotics.costs,
        )
    }

    @Test
    fun `durations of an hour or more read as hours and padded minutes`() {
        // given deuterium synth 3 → level 4, which costs 331 metal and 110 crystal after the
        // opening discount and takes 16 minutes at robotics 0 — four minutes per root of the *full*
        // 1,009 is 124, then the clock's own two-thirds-a-level ramp, five levels short of full
        // price. The dearest row in the opening is still the longest wait in it, by design
        val state = colony(
            buildings = Buildings.initial().withLevel(BuildingType.DEUTERIUM_SYNTHESIZER, BuildingLevel(3)),
        )

        // when
        val synth = state.rowFor(BuildingType.DEUTERIUM_SYNTHESIZER)

        // then
        assertEquals("16m", synth.duration)
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
        // given an empty stock: metal mine → 2 needs 19 metal (12m 40s at 90/h) and 4 crystal
        // (6m 40s at 36/h), and the chip rounds the longer of the two up to the minute
        val state = colony()

        // when
        val metalMine = state.rowFor(BuildingType.METAL_MINE)

        // then
        assertEquals(FacilityActionUiState.AffordableIn("in 13m"), metalMine.action)
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
        // given a metal mine upgrade to 2 — two minutes at robotics 0 since the opening speed-up,
        // so the sample has to be twenty-four seconds in rather than five minutes to catch it
        // running at all. Still the same fifth of the way through, which is what is asserted.
        val t0 = Instant.fromEpochMilliseconds(0)
        val started = upgrading(BuildingType.METAL_MINE, at = t0)

        // when
        val metalMine = started.rowFor(BuildingType.METAL_MINE, now = t0 + 24.seconds)

        // then
        assertEquals(
            FacilityActionUiState.Upgrading(
                toLevel = BuildingLevel(2),
                countdown = "00:01:36",
                progressPercent = 20,
                doneAt = "done 00:02",
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
        // given a 2-minute build started 2026-08-06T10:00Z, viewed from UTC+2
        val t0 = Instant.parse("2026-08-06T10:00:00Z")
        val started = upgrading(BuildingType.METAL_MINE, at = t0)

        // when
        val action = started.rowFor(BuildingType.METAL_MINE, now = t0, timeZone = TimeZone.of("Europe/Rome")).action

        // then
        assertEquals("done 12:02", assertIs<FacilityActionUiState.Upgrading>(action).doneAt)
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
    fun `a returning fleet appears as the strip with its target composition and countdown`() {
        // given one run of 14 skiffs and 1 hauler home from [2:117:9], 4h 11m 52s out. The
        // coordinate is where it *went* rather than where it is: a run names its target, and the
        // strip says which world the hold was filled at
        val now = Instant.fromEpochMilliseconds(0)
        val state = colony().copy(
            runs = listOf(fleetRun(returnsAt = now + 4.hours + 11.minutes + 52.seconds)),
        )

        // when
        val strip = state.toColonyUiState(now = now, timeZone = TimeZone.UTC).returningFleet

        // then
        assertEquals(
            ReturningFleetUiState(
                title = "Fleet returning",
                subtitle = "from [2:117:9] · 14 skiff · 1 hauler",
                countdown = "04:11:52",
            ),
            checkNotNull(strip),
        )
    }

    // Runs are parallel where the old model held exactly one fleet, so the strip has a case it never
    // had before — and it is one 48dp row, so the ones that are not next are a count rather than a
    // row each. The list is deliberately not in return order: nothing orders `runs`.
    @Test
    fun `several runs out name the soonest and count the rest`() {
        // given three runs home at two five and nine hours
        val now = Instant.fromEpochMilliseconds(0)
        val state = colony().copy(
            runs = listOf(
                fleetRun(returnsAt = now + 9.hours, target = GalaxyCoordinate(galaxy = 1, system = 42, slot = 7)),
                fleetRun(returnsAt = now + 2.hours, ships = Ships.of(ShipType.SKIFF, 3)),
                fleetRun(returnsAt = now + 5.hours, target = GalaxyCoordinate(galaxy = 3, system = 8, slot = 1)),
            ),
        )

        // when
        val strip = checkNotNull(state.toColonyUiState(now = now, timeZone = TimeZone.UTC).returningFleet)

        // then the soonest is the one named in full and the other two are the door to Fleets
        assertEquals("from [2:117:9] · 3 skiff · 2 more away", strip.subtitle)
        assertEquals("02:00:00", strip.countdown)
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

    @Test
    fun `the facility that finished while the app was closed is the only row that sweeps`() {
        // given
        val state = colony()

        // when
        val rows = state
            .toColonyUiState(
                now = Instant.fromEpochMilliseconds(0),
                timeZone = TimeZone.UTC,
                finishedWhileAway = BuildingType.SOLAR_PLANT,
            )
            .facilities

        // then
        assertEquals(
            listOf(BuildingType.SOLAR_PLANT),
            rows.filter { it.finishedWhileAway }.map { it.building },
        )
    }

    @Test
    fun `a launch that found nothing finished sweeps no row at all`() {
        // given
        val state = colony()

        // when
        val rows = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0), timeZone = TimeZone.UTC).facilities

        // then
        assertEquals(emptyList(), rows.filter { it.finishedWhileAway })
    }

    @Test
    fun `a row waiting on its stocks offers a square and nothing else does`() {
        // given a colony that can pay for nothing, with one facility already building and one
        // behind its requirement — every kind of row on the screen at once
        val t0 = Instant.fromEpochMilliseconds(0)
        val state = upgrading(BuildingType.CRYSTAL_MINE, at = t0).copy(resources = Resources.of())

        // when
        val offering = state
            .toColonyUiState(now = t0, timeZone = TimeZone.UTC)
            .facilities
            .filter { it.watch != null }
            .map { it.building }

        // then — the crystal mine is building, the nanite factory is locked, and the other four are
        // waiting on stocks they have none of
        assertEquals(
            listOf(
                BuildingType.METAL_MINE,
                BuildingType.DEUTERIUM_SYNTHESIZER,
                BuildingType.SOLAR_PLANT,
                BuildingType.ROBOTICS_FACTORY,
            ),
            offering,
        )
    }

    @Test
    fun `a row the colony can already pay for has nothing to watch`() {
        // given
        val state = colony(resources = Resources.of(metal = 1_000_000, crystal = 1_000_000))

        // then — an affordable row is not waiting for anything, so there is no instant to book
        assertEquals(null, state.rowFor(BuildingType.METAL_MINE).watch)
    }

    @Test
    fun `a row whose binding resource never arrives has no instant to book`() {
        // given no synthesizer, so the robotics deuterium cost never accrues — the same colony the
        // stalled ghost is asserted on, and the square has to be absent for the same reason the
        // ghost reads "—": there is no time to name
        val state = colony(
            resources = Resources.of(metal = 400, crystal = 120),
            buildings = Buildings.initial().withLevel(BuildingType.DEUTERIUM_SYNTHESIZER, BuildingLevel(0)),
        )

        // then
        assertEquals(null, state.rowFor(BuildingType.ROBOTICS_FACTORY).watch)
    }

    @Test
    fun `the watched row names the instant and the others only offer`() {
        // given a colony 90 metal short of a second mine at 90 an hour — one hour out
        val state = colony(
            resources = Resources.of(),
            watching = WatchTarget.Facility(BuildingType.METAL_MINE),
        )

        // when
        val rows = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0), timeZone = TimeZone.UTC).facilities

        // then — an absolute time, because that is what the alert will be stamped with
        assertIs<WatchUiState.Booked>(rows.first { it.building == BuildingType.METAL_MINE }.watch)
        assertEquals(
            WatchUiState.Offered,
            rows.first { it.building == BuildingType.SOLAR_PLANT }.watch,
        )
    }

    @Test
    fun `the watched instant is the one the ghost beside it is counting down to`() {
        // given — the two readings are the same arithmetic seen twice, so a row that disagreed with
        // itself would be the worst failure this control has available
        val now = Instant.parse("2026-08-10T11:38:00Z")
        val state = colony(
            resources = Resources.of(),
            watching = WatchTarget.Facility(BuildingType.METAL_MINE),
        )

        // when
        val row = state.rowFor(BuildingType.METAL_MINE, now = now)
        val wait = timeUntilAffordable(
            state.resources,
            PlaceholderBalance.upgradeCost(BuildingType.METAL_MINE, BuildingLevel(2)),
            state.buildings,
            state.research,
        )

        // then
        val expected = (now + wait).toLocalDateTime(TimeZone.UTC)
        assertEquals(
            "→ affordable ${expected.hour.pad2()}:${expected.minute.pad2()}",
            assertIs<WatchUiState.Booked>(row.watch).affordableAt,
        )
    }

    @Test
    fun `a watch on another screen still leaves this one offering squares`() {
        // given the watch held by a technology, which the colony cannot see and must not claim
        val state = colony(resources = Resources.of(), watching = WatchTarget.Project(Technology.EXTRACTION))

        // when
        val rows = state.toColonyUiState(now = Instant.fromEpochMilliseconds(0), timeZone = TimeZone.UTC).facilities

        // then — no facility is lit, and every waiting one still offers
        assertEquals(emptyList(), rows.filter { it.watch is WatchUiState.Booked })
        assertTrue(rows.any { it.watch == WatchUiState.Offered })
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
        watching: WatchTarget? = null,
    ): GameState = GameState(
        resources = resources,
        buildings = buildings,
        builds = emptyMap(),
        research = Research.initial(),
        activeResearch = null,
        activeAdaptation = null,
        // `GameState.initial` takes a galaxy seed rather than defaulting one, so production cannot
        // found every colony in the same galaxy. The Colony screen draws none of it.
        galaxy = GameState.initial(GalaxySeed(20_260_807)).galaxy,
        // A probe in flight competes with this screen's upgrades for the same metal and for
        // nothing else — it holds no construction slot and appears on no facility row.
        surveys = emptyList(),
        // The idle pool is the Fleets screen's subject: this screen draws only what is *out*, so a
        // colony with no hull at home still renders every row asserted above.
        ships = Ships.NONE,
        runs = emptyList(),
        // Which row the empire is watching, when it is watching one. A parameter rather than a
        // constant because it is the input half of the square: `watch` on a row is derived from it.
        watching = watching,
        eventLog = emptyList(),
    )

    // A run in the shape `FleetRun` insists on — it left before it comes back, and it never gathers
    // deuterium. The 14 skiffs and 1 hauler are the manifest the strip was drawn with at 0.0.6.
    private fun fleetRun(
        returnsAt: Instant,
        target: GalaxyCoordinate = GalaxyCoordinate(galaxy = 2, system = 117, slot = 9),
        ships: Ships = Ships(mapOf(ShipType.SKIFF to 14, ShipType.HAULER to 1)),
    ): FleetRun = FleetRun(
        target = target,
        ships = ships,
        gathering = ResourceKind.METAL,
        cargo = Resources.of(metal = 500),
        dispatchedAt = returnsAt - 1.hours,
        returnsAt = returnsAt,
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

    @Test
    fun `the heading names what the empire is watching whatever screen it is on`() {
        // given — handed in, because the colony cannot name a technology
        val state = colony()

        // when
        val uiState = state.toColonyUiState(
            now = Instant.fromEpochMilliseconds(0),
            timeZone = TimeZone.UTC,
            watching = "watching Extraction",
        )

        // then
        assertEquals("watching Extraction", uiState.watching)
    }
}
