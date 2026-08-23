package dev.fardavide.oltre.client.colony.presentation

import dev.fardavide.oltre.client.design.format.watchedAtLabel
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.StringId
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.core.Experience
import dev.fardavide.oltre.client.colony.ui.ColonyUiState
import dev.fardavide.oltre.client.colony.ui.EnergyUiState
import dev.fardavide.oltre.client.colony.ui.FacilityActionUiState
import dev.fardavide.oltre.client.colony.ui.FacilityPowerUiState
import dev.fardavide.oltre.client.colony.ui.FacilityRowUiState
import dev.fardavide.oltre.client.colony.ui.ReturningFleetUiState
import dev.fardavide.oltre.client.colony.ui.toRowSheetUiState
import dev.fardavide.oltre.client.design.component.CostChipUiState
import dev.fardavide.oltre.client.design.component.SheetAction
import dev.fardavide.oltre.client.design.component.SheetFooter
import dev.fardavide.oltre.client.design.component.SheetLadderStep
import dev.fardavide.oltre.client.design.component.SheetPointer
import dev.fardavide.oltre.client.design.component.VerdictUiState
import dev.fardavide.oltre.client.design.component.WatchUiState
import dev.fardavide.oltre.client.design.component.figure
import dev.fardavide.oltre.client.design.component.sheetLine
import dev.fardavide.oltre.client.design.component.words
import dev.fardavide.oltre.core.BuildingLevel
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.AlertSettings
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
import dev.fardavide.oltre.core.toggleAlert
import dev.fardavide.oltre.core.timeUntilAffordable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
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
                verdict = Strings.energyRoomForMineLevels(1),
                terms = Strings.clauses(
                listOf(
                    Strings.energyProduced(Strings.groupedNumber(50)),
                    Strings.energyDrawn(Strings.groupedNumber(40)),
                    Strings.energySpare(Strings.groupedNumber(10)),
                ),
            ),
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
        assertEquals("room for 3 mine levels", English.resolve(energy.verdict))
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
        assertEquals("break even", English.resolve(energy.verdict))
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
                verdict = Strings.energyEveryMineAt(55),
                terms = Strings.clauses(
                listOf(
                    Strings.energyProduced(Strings.groupedNumber(50)),
                    Strings.energyDrawn(Strings.groupedNumber(90)),
                    Strings.energyShort(Strings.groupedNumber(40)),
                ),
            ),
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
        assertEquals("every mine stopped", English.resolve(energy.verdict))
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
                BuildingType.METAL_MINE to FacilityPowerUiState(label = Strings.powerDraw(Strings.groupedNumber(30)), supply = false),
                BuildingType.CRYSTAL_MINE to FacilityPowerUiState(label = Strings.powerDraw(Strings.groupedNumber(20)), supply = false),
                BuildingType.DEUTERIUM_SYNTHESIZER to FacilityPowerUiState(label = Strings.powerDraw(Strings.groupedNumber(40)), supply = false),
                BuildingType.SOLAR_PLANT to FacilityPowerUiState(label = Strings.powerSupply(Strings.groupedNumber(50)), supply = true),
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
        assertEquals("→ LV 2 covers all 90 drawn", English.resolve(checkNotNull(rows.first { it.building == BuildingType.SOLAR_PLANT }.fix)))
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
        assertEquals("Metal Mine", English.resolve(metalMine.name))
        assertEquals(BuildingLevel(1), metalMine.level)
        assertEquals(
            listOf(
                CostChipUiState(kind = ResourceKind.METAL, amount = Strings.groupedNumber(19), short = false),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = Strings.groupedNumber(4), short = true),
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
        assertEquals("2m", English.resolve(metalMine.duration))
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
                CostChipUiState(kind = ResourceKind.METAL, amount = Strings.groupedNumber(40), short = true),
                CostChipUiState(kind = ResourceKind.CRYSTAL, amount = Strings.groupedNumber(12), short = true),
                CostChipUiState(kind = ResourceKind.DEUTERIUM, amount = Strings.groupedNumber(20), short = true),
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
        assertEquals("16m", English.resolve(synth.duration))
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
        assertEquals(FacilityActionUiState.AffordableIn(Strings.availableIn(Strings.durationMinutes(13))), metalMine.action)
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
        assertEquals(FacilityActionUiState.AffordableIn(Strings.availableNever()), robotics.action)
    }

    @Test
    fun `facility rows mark the nanite factory locked below robotics 10`() {
        // given
        val state = colony(resources = Resources.of(metal = 1_000_000))

        // when
        val nanite = state.rowFor(BuildingType.NANITE_FACTORY)

        // then
        assertEquals(FacilityActionUiState.Locked(Strings.requiresRobotics(10)), nanite.action)
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
                countdown = Strings.countdown(0, 1, 36),
                progressPercent = 20,
                doneAt = Strings.doneAt(hour = 0, minute = 2),
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
        assertEquals("done 12:02", English.resolve(assertIs<FacilityActionUiState.Upgrading>(action).doneAt))
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
        assertEquals("00:00:01", English.resolve(assertIs<FacilityActionUiState.Upgrading>(action).countdown))
    }

    @Test
    fun `a returning fleet appears as the strip with its target composition and countdown`() {
        // given one run of 14 skiffs and 1 hauler home from [2:117:9], 4h 11m 52s out, and long
        // enough since it left that the outbound leg is behind it. The coordinate is where it *went*
        // rather than where it is: a run names its target, and the strip says which world the hold
        // was filled at
        val now = Instant.fromEpochMilliseconds(0)
        val state = colony().copy(
            runs = listOf(fleetRun(returnsAt = now + 4.hours + 11.minutes + 52.seconds, leftDaysAgo = 1)),
        )

        // when
        val strip = state.toColonyUiState(now = now, timeZone = TimeZone.UTC).returningFleet

        // then
        assertEquals(
            ReturningFleetUiState(
                title = Strings.fleetReturning(),
                subtitle = Strings.clauses(
                    listOf(
                        Strings.fromTarget(Strings.coordinate(2, 117, 9)),
                        Strings.shipsOfType(14, ShipType.SKIFF),
                        Strings.shipsOfType(1, ShipType.HAULER),
                    ),
                ),
                countdown = Strings.countdown(4, 11, 52),
            ),
            checkNotNull(strip),
        )
    }

    // Claude Design's fifth call, 2026-08-10: the strip names the next **event**, not the next
    // return. A run has two moments a player waits on and for the first half of every run the nearer
    // one is the arrival — so a skiff still on its way out says so, and only turns into "Fleet
    // returning" once it has turned for home. The strip has always been amber for in transit and a
    // run is in transit in both directions, so nothing about its colour changes.
    @Test
    fun `a run still on its way out names the arrival rather than the return`() {
        // given a run dispatched at this instant and home in twenty hours: the next thing to happen
        // to it is not the return
        //
        // **Twenty rather than nine, since 0.15.** The target is a galaxy away and base flight speed
        // halved, so that leg is 9h 10m each way — a nine-hour window is one the ladder would not
        // offer and `startRun` would refuse, and a hand-built run carrying it put `flightEndsAt`
        // *past* `returnsAt`, which read as a fleet already coming home. Twenty hours is a run the
        // game would really book: 18h 20m of flight and 1h 40m on the surface.
        val now = Instant.fromEpochMilliseconds(0)
        val window = 20.hours
        val state = colony().copy(
            runs = listOf(fleetRun(returnsAt = now + window, leftDaysAgo = 0)),
        )

        // when
        val strip = checkNotNull(state.toColonyUiState(now = now, timeZone = TimeZone.UTC).returningFleet)

        // then the target is in the title, where the verb needs it, and not repeated below it
        assertEquals("On station at [2:117:9]", English.resolve(strip.title))
        assertEquals("14 skiff · 1 hauler", English.resolve(strip.subtitle))
        // ...and the countdown is to the arrival, so it is strictly shorter than the twenty hours the
        // run is out for
        assertTrue(English.resolve(strip.countdown) < "20:00:00", English.resolve(strip.countdown))
    }

    // Runs are parallel where the old model held exactly one fleet, so the strip has a case it never
    // had before — and it is one 48dp row, so the ones that are not next are a count rather than a
    // row each. The list is deliberately not in return order: nothing orders `runs`.
    @Test
    fun `several runs out name the soonest and count the rest`() {
        // given three runs home at two five and nine hours, all of them already on station
        val now = Instant.fromEpochMilliseconds(0)
        val state = colony().copy(
            runs = listOf(
                fleetRun(
                    returnsAt = now + 9.hours,
                    target = GalaxyCoordinate(galaxy = 1, system = 42, slot = 7),
                    leftDaysAgo = 1,
                ),
                fleetRun(returnsAt = now + 2.hours, ships = Ships.of(ShipType.SKIFF, 3), leftDaysAgo = 1),
                fleetRun(
                    returnsAt = now + 5.hours,
                    target = GalaxyCoordinate(galaxy = 3, system = 8, slot = 1),
                    leftDaysAgo = 1,
                ),
            ),
        )

        // when
        val strip = checkNotNull(state.toColonyUiState(now = now, timeZone = TimeZone.UTC).returningFleet)

        // then the soonest is the one named in full and the other two are the door to Fleets
        assertEquals("from [2:117:9] · 3 skiff · 2 more away", English.resolve(strip.subtitle))
        assertEquals("02:00:00", English.resolve(strip.countdown))
    }

    // **Sorted by event rather than by return, and the two orderings genuinely differ**: a run
    // dispatched at a far world can still be outbound when a nearer one has already turned for home.
    // Picking the soonest *return* and then labelling it would name a moment that is not the next
    // thing to happen.
    @Test
    fun `the strip names whichever event lands first even when it belongs to the later run`() {
        // given a near run home in twenty hours and already on station, and a far one dispatched just
        // now, whose arrival is inside those twenty hours.
        //
        // **The premise this used to state is no longer true and that is the whole of the edit.** It
        // read *"the longest flight anywhere on the map is 9h 20m"*, which was a skiff at the speed
        // 0.14 shipped at. This fixture's manifest carries a hauler, a hauler flies at half speed,
        // and 0.15 halved the base — so the far corner is now 36h 20m one way and its *arrival*
        // falls outside the near run's return, which makes the near run the next event and the test
        // a statement about the opposite thing. Same galaxy, 170 systems out: 6h 38m, which is what
        // the fixture always meant by "far".
        val now = Instant.fromEpochMilliseconds(0)
        val far = GalaxyCoordinate(galaxy = 3, system = 1, slot = 7)
        val state = colony().copy(
            runs = listOf(
                fleetRun(returnsAt = now + 20.hours, leftDaysAgo = 1),
                fleetRun(returnsAt = now + 24.hours, target = far, leftDaysAgo = 0),
            ),
        )

        // when
        val strip = checkNotNull(state.toColonyUiState(now = now, timeZone = TimeZone.UTC).returningFleet)

        // then it is the far run's arrival that is named, not the near run's return
        assertEquals("On station at [3:1:7]", English.resolve(strip.title))
        assertTrue(English.resolve(strip.countdown) < "20:00:00", English.resolve(strip.countdown))
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
    fun `every row with an instant to name offers a square and nothing else does`() {
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

        // then — the crystal mine is building, which since this version is something to ask about
        // rather than something already told; the nanite factory is locked and has no price yet;
        // the other four are waiting on stocks they have none of
        assertEquals(
            listOf(
                BuildingType.METAL_MINE,
                BuildingType.CRYSTAL_MINE,
                BuildingType.DEUTERIUM_SYNTHESIZER,
                BuildingType.SOLAR_PLANT,
                BuildingType.ROBOTICS_FACTORY,
            ),
            offering,
        )
    }

    @Test
    fun `a subscribed build lights its square and adds no line`() {
        // given — the row already prints "→ LV 13 · done 11:23", so there is nothing for the
        // subscription to say that the card is not saying
        val t0 = Instant.fromEpochMilliseconds(0)
        val state = upgrading(BuildingType.METAL_MINE, at = t0)
            .let { toggleAlert(it, WatchTarget.Facility(BuildingType.METAL_MINE)) }

        // then
        assertEquals(WatchUiState.Subscribed, state.rowFor(BuildingType.METAL_MINE, now = t0).watch)
    }

    @Test
    fun `a build nobody has asked about offers its square unlit`() {
        // given
        val t0 = Instant.fromEpochMilliseconds(0)
        val state = upgrading(BuildingType.METAL_MINE, at = t0)

        // then
        assertEquals(WatchUiState.Offered, state.rowFor(BuildingType.METAL_MINE, now = t0).watch)
    }

    @Test
    fun `one name shortens at a Slide Over's width and the rest do not`() {
        // given — the square costs the name column nothing once it stacks under the ghost, so only
        // the one name that never fit is authored short. "Robotics" is what the game already calls
        // it in "Requires Robotics 10".
        val rows = colony().toColonyUiState(now = Instant.fromEpochMilliseconds(0), timeZone = TimeZone.UTC)
            .facilities

        // then
        assertEquals(
            listOf("Metal Mine", "Crystal Mine", "Deuterium Synth.", "Solar Plant", "Robotics", "Nanite Factory"),
            rows.map { English.resolve(it.compactName) },
        )
        assertEquals(
            listOf("Metal Mine", "Crystal Mine", "Deuterium Synth.", "Solar Plant", "Robotics Factory", "Nanite Factory"),
            rows.map { English.resolve(it.name) },
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
            English.resolve(watchedAtLabel(hour = expected.hour, minute = expected.minute)),
            English.resolve(assertIs<WatchUiState.Booked>(row.watch).affordableAt),
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

    // ── What one more level is worth ─────────────────────────────────────────────────────────
    //
    // A row has always stated a price and a wait. What it never stated is whether the level is worth
    // taking, and every row on this screen now answers that in one shape: a clause a narrow window
    // keeps, and a second one it drops. Every number below is `core`'s — what is asserted here is
    // which words go round it.

    @Test
    fun `a mine states the rate it hands you and when it pays for itself`() {
        // given genesis, where a 19-and-4 upgrade lifts the metal mine from 90/h to 112/h
        val state = colony()

        // when
        val verdict = state.rowFor(BuildingType.METAL_MINE).verdict

        // then the clause a Slide Over keeps is the gain; the one it drops is what the gain costs
        // in time, which is the sheet's to repeat
        assertEquals(
            VerdictUiState(label = Strings.clauses(
                    listOf(
                        Strings.outputGain(Strings.groupedNumber(22), ResourceKind.METAL),
                        Strings.backIn(Strings.durationHoursMinutes(1, 13)),
                    ),
                ), compactLabel = Strings.outputGain(Strings.groupedNumber(22), ResourceKind.METAL)),
            verdict,
        )
    }

    @Test
    fun `a level the plant cannot carry states the throttle rather than a gain`() {
        // given genesis exactly as the game deals it: one plant supplying 50 against 40 drawn — and
        // a synthesizer level that would draw 20 more
        val state = colony()

        // when
        val verdict = state.rowFor(BuildingType.DEUTERIUM_SYNTHESIZER).verdict

        // then the level is not a small gain but a loss, and the row says which plant level ends it
        assertEquals(
            VerdictUiState(
                label = Strings.clauses(listOf(Strings.throttlesEveryMine(), Strings.solarPlantCovers(2))),
                compactLabel = Strings.throttlesEveryMine(),
            ),
            verdict,
        )
    }

    @Test
    fun `the plant states what it supplies while nothing is limited by supply`() {
        // given a colony with power to spare
        val state = colony()

        // when
        val verdict = state.rowFor(BuildingType.SOLAR_PLANT).verdict

        // then the honest reading is that the level buys nothing today — said as what it does add
        assertEquals(
            VerdictUiState(label = Strings.clauses(
                    listOf(Strings.suppliesMore(Strings.groupedNumber(50)), Strings.drawAlreadyCovered()),
                ), compactLabel = Strings.suppliesMore(Strings.groupedNumber(50))),
            verdict,
        )
    }

    @Test
    fun `the robotics factory states the time it takes off a build and what it opens next`() {
        // given a colony deep enough to have a build worth shortening: metal mine 12 is 1h 37m at
        // robotics 3 and 1h 18m at robotics 4
        val state = colony(buildings = deep())

        // when
        val verdict = state.rowFor(BuildingType.ROBOTICS_FACTORY).verdict

        // then the one row on this screen that gates anything says so in its second clause
        assertEquals(
            VerdictUiState(label = Strings.clauses(
                    listOf(
                        Strings.savedPerBuild(Strings.durationMinutes(20)),
                        Strings.gateClause(level = 10, opens = Strings.gateSummaryNanite()),
                    ),
                ), compactLabel = Strings.savedPerBuild(Strings.durationMinutes(20))),
            verdict,
        )
    }

    @Test
    fun `the gate clause names the lowest gate the colony has not passed yet`() {
        // given robotics 0 — the applied branch at 1 and the ladders at 2 are both still ahead
        val state = colony(buildings = Buildings.initial().withLevel(BuildingType.METAL_MINE, BuildingLevel(12)))

        // when
        val verdict = checkNotNull(state.rowFor(BuildingType.ROBOTICS_FACTORY).verdict)

        // then a group of technologies is described rather than listed: two names in a clause is
        // the beginning of a table
        assertEquals("−3h 14m per build · LV 1 → research", English.resolve(verdict.label))
    }

    @Test
    fun `a locked nanite factory states the payoff rather than the next level's saving`() {
        // given a colony nowhere near the gate — the whole point is that this reads on day one
        val state = colony()

        // when
        val verdict = state.rowFor(BuildingType.NANITE_FACTORY).verdict

        // then it is about the shape of the curve rather than about the next level of anything —
        // and the figures are quoted at the Robotics level the gate demands rather than at this
        // colony's, so a claim about the building does not move when the reader does
        assertEquals(
            VerdictUiState(
                label = Strings.naniteReliefLong(
                    unaided = Strings.durationHours(271),
                    helped = Strings.durationHoursMinutes(23, 49),
                    level = 6,
                ),
                compactLabel = Strings.naniteReliefShort(
                    unaided = Strings.durationHours(271),
                    helped = Strings.durationHoursMinutes(23, 49),
                    level = 6,
                ),
            ),
            verdict,
        )
    }

    @Test
    fun `a row in flight carries no verdict at all`() {
        // given the metal mine already building
        val t0 = Instant.fromEpochMilliseconds(0)
        val state = upgrading(BuildingType.METAL_MINE, at = t0)

        // then the decision was made when the player tapped; the slot belongs to the countdown now
        assertEquals(null, state.rowFor(BuildingType.METAL_MINE, now = t0).verdict)
    }

    // There is no test for a row at its ceiling, and that is a statement about this screen rather
    // than a gap: `toFacilityRow` prices the level after the one you hold, and the cost curve
    // *throws* past 40 rather than returning anything. So a colony that could produce an unmeasured
    // verdict has already crashed the row it would appear on. The branch exists because the `when`
    // over `LevelPurpose` is exhaustive, and for no other reason.

    // ── What the sheet says ──────────────────────────────────────────────────────────────────

    @Test
    fun `the sheet behind a mine row states the two rates and the payback`() {
        // given
        val state = colony()

        // when
        val lines = state.rowFor(BuildingType.METAL_MINE).detail.lines

        // then the numbers the verdict displaced — the rate it came from and the rate it goes to
        assertEquals(
            listOf(
                sheetLine(
                    words(Strings.sheetMineMakes()),
                    figure(Strings.perHour(Strings.groupedNumber(90))),
                    words(Strings.sheetMineAtLevel(kind = ResourceKind.METAL, level = 2)),
                    figure(Strings.perHour(Strings.groupedNumber(112))),
                    words(Strings.sheetFullStop()),
                ),
                sheetLine(
                    words(Strings.sheetPaybackPrefix()),
                    figure(Strings.durationHoursMinutes(1, 13)),
                    words(Strings.sheetFullStop()),
                ),
            ),
            lines,
        )
    }

    @Test
    fun `the sheet behind a throttled row says what to build first`() {
        // given
        val state = colony()

        // when
        val lines = state.rowFor(BuildingType.DEUTERIUM_SYNTHESIZER).detail.lines

        // then the fix arrives one row earlier than the power indicator's — before the money is
        // spent rather than after
        assertEquals(
            listOf(
                sheetLine(
                    words(
                        Strings.sheetCannotPowerLevel(),
                    ),
                ),
                sheetLine(
                    words(Strings.sheetPlantCarriesPrefix()),
                    figure(Strings.plainNumber(2)),
                    words(Strings.sheetPlantCarriesSuffix()),
                ),
            ),
            lines,
        )
    }

    @Test
    fun `the sheet behind the plant states the two terms and how far the crossing is`() {
        // given genesis: 50 supplied against 40 drawn, with room for exactly one more mine level
        val state = colony()

        // when
        val lines = state.rowFor(BuildingType.SOLAR_PLANT).detail.lines

        // then a count of one is spelled, because "1 more mine levels" is not a sentence
        assertEquals(
            listOf(
                sheetLine(
                    words(Strings.sheetPlantsSupply()),
                    figure(Strings.groupedNumber(50)),
                    words(Strings.sheetColonyDraws()),
                    figure(Strings.groupedNumber(40)),
                    words(Strings.sheetFullStop()),
                ),
                sheetLine(
                    words(Strings.sheetSupplyNotLimiting()),
                    figure(Strings.plusAmount(Strings.groupedNumber(50))),
                    words(Strings.sheetChangesNoRate()),
                ),
                sheetLine(
                    words(Strings.sheetPaysWhenDrawPasses()),
                    figure(Strings.sheetOneSpelled()),
                    words(Strings.sheetMoreMineLevelAway()),
                ),
            ),
            lines,
        )
    }

    @Test
    fun `a colony with room to spare counts the crossing in whole mine levels`() {
        // given four plants against three level-1 mines: 200 supplied against 40 drawn
        val state = colony(buildings = Buildings.initial().withLevel(BuildingType.SOLAR_PLANT, BuildingLevel(4)))

        // when
        val lines = state.rowFor(BuildingType.SOLAR_PLANT).detail.lines

        // then
        assertEquals(
            sheetLine(
                words(Strings.sheetPaysWhenDrawPasses()),
                figure(Strings.groupedNumber(16)),
                words(Strings.sheetMoreMineLevelsAway()),
            ),
            lines.last(),
        )
    }

    @Test
    fun `a colony already at break even is told the next mine level is the crossing`() {
        // given metal mine 2: 50 produced against 50 drawn, with no headroom left to count
        val state = colony(buildings = Buildings.initial().withLevel(BuildingType.METAL_MINE, BuildingLevel(2)))

        // when
        val lines = state.rowFor(BuildingType.SOLAR_PLANT).detail.lines

        // then no figure at all — there is no number left to state
        assertEquals(sheetLine(words(Strings.sheetPaysNextMineLevel())), lines.last())
    }

    @Test
    fun `the plant reads as income once the colony is throttled`() {
        // given the report's colony: 50 produced against 90 drawn, every mine at 55%
        val state = colony(buildings = starved())

        // when
        val row = state.rowFor(BuildingType.SOLAR_PLANT)

        // then the same row that bought nothing a moment ago is now the best buy on the screen
        assertEquals(
            VerdictUiState(label = Strings.clauses(
                    listOf(
                        Strings.outputGain(Strings.groupedNumber(63), ResourceKind.METAL),
                        Strings.backIn(Strings.durationMinutes(19)),
                    ),
                ), compactLabel = Strings.outputGain(Strings.groupedNumber(63), ResourceKind.METAL)),
            row.verdict,
        )
        assertEquals(
            listOf(
                sheetLine(
                    words(Strings.sheetPlantsSupply()),
                    figure(Strings.groupedNumber(50)),
                    words(Strings.sheetColonyDraws()),
                    figure(Strings.groupedNumber(90)),
                    words(Strings.sheetSoEveryMineAt()),
                    figure(Strings.percent(55)),
                    words(Strings.sheetFullStop()),
                ),
                sheetLine(
                    words(Strings.sheetThisLevelLifts()),
                    figure(Strings.plusPerHour(Strings.groupedNumber(63))),
                    words(Strings.sheetRatherThanEnergy(ResourceKind.METAL)),
                ),
                sheetLine(
                    words(Strings.sheetPaybackPrefix()),
                    figure(Strings.durationMinutes(19)),
                    words(Strings.sheetFullStop()),
                ),
            ),
            row.detail.lines,
        )
    }

    @Test
    fun `the sheet behind the robotics factory names the build it shortens`() {
        // given
        val state = colony(buildings = deep())

        // when
        val lines = state.rowFor(BuildingType.ROBOTICS_FACTORY).detail.lines

        // then it says out loud that it raises nothing — the row's price line cannot
        assertEquals(
            listOf(
                sheetLine(
                    words(Strings.sheetShortensEveryBuild()),
                ),
                sheetLine(
                    words(Strings.sheetNextBuildTakes(Strings.buildingName(BuildingType.METAL_MINE))),
                    figure(Strings.durationHoursMinutes(1, 37)),
                    words(Strings.sheetAtBuildingLevelTakes(Strings.buildingName(BuildingType.ROBOTICS_FACTORY), 4)),
                    figure(Strings.durationHoursMinutes(1, 18)),
                    words(Strings.sheetFullStop()),
                ),
            ),
            lines,
        )
    }

    @Test
    fun `the ladder says what every level of the robotics factory opens`() {
        // given a colony that has not built one at all
        val state = colony()

        // when
        val ladder = state.rowFor(BuildingType.ROBOTICS_FACTORY).detail.ladder

        // then in the order the player reaches them — and the one facility on it carries its price
        assertEquals(
            listOf(
                SheetLadderStep(level = Strings.levelBadge(1), opens = Strings.gateSummaryResearchLong(), held = false),
                SheetLadderStep(level = Strings.levelBadge(2), opens = Strings.gateSummaryAdaptationLong(), held = false),
                SheetLadderStep(level = Strings.levelBadge(10), opens = Strings.gateFacilityLong(
                    name = Strings.buildingName(BuildingType.NANITE_FACTORY),
                    metal = Strings.groupedNumber(2_000),
                ), held = false),
            ),
            ladder,
        )
    }

    @Test
    fun `the ladder marks the levels the colony already holds`() {
        // given robotics 3 — past the applied branch and past the ladders
        val state = colony(buildings = deep())

        // when
        val ladder = state.rowFor(BuildingType.ROBOTICS_FACTORY).detail.ladder

        // then held is computed rather than written, so "you have this" cannot go stale
        assertEquals(
            listOf(
                SheetLadderStep(level = Strings.levelBadge(1), opens = Strings.ladderStepHeld(Strings.gateSummaryResearchLong()), held = true),
                SheetLadderStep(
                    level = Strings.levelBadge(2),
                    opens = Strings.ladderStepHeld(Strings.gateSummaryAdaptationLong()),
                    held = true,
                ),
                SheetLadderStep(level = Strings.levelBadge(10), opens = Strings.gateFacilityLong(
                    name = Strings.buildingName(BuildingType.NANITE_FACTORY),
                    metal = Strings.groupedNumber(2_000),
                ), held = false),
            ),
            ladder,
        )
    }

    @Test
    fun `a row that gates nothing carries no ladder`() {
        // given
        val state = colony()

        // then five of the six rows on this screen gate nothing — which is what makes the sixth
        // worth a ladder
        assertEquals(
            listOf(BuildingType.ROBOTICS_FACTORY),
            state.toColonyUiState(now = Instant.fromEpochMilliseconds(0), timeZone = TimeZone.UTC)
                .facilities
                .filter { it.detail.ladder.isNotEmpty() }
                .map { it.building },
        )
    }

    @Test
    fun `the locked nanite sheet counts the levels to the gate`() {
        // given
        val state = colony()

        // when
        val lines = state.rowFor(BuildingType.NANITE_FACTORY).detail.lines

        // then the late-game wait and the answer to it are both stated while the building is still
        // twelve days out and 42% dim
        assertEquals(
            listOf(
                sheetLine(
                    words(Strings.sheetShortensDeepBuild()),
                ),
                sheetLine(
                    words(Strings.sheetNaniteMineTakes(30)),
                    figure(Strings.durationHours(271)),
                    words(Strings.sheetNaniteUnaidedAt(6)),
                    figure(Strings.durationHoursMinutes(23, 49)),
                    words(Strings.sheetFullStop()),
                ),
                sheetLine(
                    words(Strings.sheetRoboticsIsAt()),
                    figure(Strings.plainNumber(0)),
                    words(Strings.sheetLevelsToGo(10)),
                    figure(Strings.groupedNumber(2_000)),
                    words(Strings.sheetMetalSuffix()),
                ),
            ),
            lines,
        )
    }

    @Test
    fun `the last level before the gate is counted in the singular`() {
        // given robotics 9
        val state = colony(
            buildings = Buildings.initial().withLevel(BuildingType.ROBOTICS_FACTORY, BuildingLevel(9)),
        )

        // when
        val lines = state.rowFor(BuildingType.NANITE_FACTORY).detail.lines

        // then "1 levels to go" is not a sentence either
        assertEquals(
            sheetLine(
                words(Strings.sheetRoboticsIsAt()),
                figure(Strings.plainNumber(9)),
                words(Strings.sheetLevelsToGo(1)),
                figure(Strings.groupedNumber(2_000)),
                words(Strings.sheetMetalSuffix()),
            ),
            lines.last(),
        )
    }

    @Test
    fun `the locked nanite sheet points at the row that moves the gate`() {
        // given
        val state = colony()

        // when
        val pointer = state.rowFor(BuildingType.NANITE_FACTORY).detail.pointer

        // then a locked row ends on something to do rather than on a number it cannot reach
        assertEquals(SheetPointer(name = Strings.buildingName(BuildingType.ROBOTICS_FACTORY), detail = Strings.pointerLevelStep(from = 0, to = 1, wait = Strings.durationMinutes(3))), pointer)
    }

    @Test
    fun `the plant's sheet points at the shortest payback on the screen`() {
        // given genesis, where the plant buys nothing and the metal mine repays in 1h 13m against
        // the crystal mine's 1h 36m
        val state = colony()

        // when
        val pointer = state.rowFor(BuildingType.SOLAR_PLANT).detail.pointer

        // then the only useful thing a verdict of "nothing" can end on is the row to read instead
        assertEquals(SheetPointer(name = Strings.buildingName(BuildingType.METAL_MINE), detail = Strings.pointerBestBuy(level = 2, payback = Strings.durationHoursMinutes(1, 13))), pointer)
    }

    @Test
    fun `a row that is worth something points at nothing`() {
        // given
        val state = colony()

        // then the pointer is the answer to "then what" and a mine has already answered it
        assertEquals(null, state.rowFor(BuildingType.METAL_MINE).detail.pointer)
    }

    @Test
    fun `a running row's sheet keeps one sentence and offers nothing`() {
        // given
        val t0 = Instant.fromEpochMilliseconds(0)
        val state = upgrading(BuildingType.METAL_MINE, at = t0)

        // when
        val row = state.rowFor(BuildingType.METAL_MINE, now = t0)

        // then the arithmetic that argued for the level is gone: it has already been paid for
        assertEquals(1, row.detail.lines.size)
        assertEquals(null, row.detail.pointer)
        assertEquals(null, row.toRowSheetUiState().footer)
    }

    @Test
    fun `a running row's sheet is headed by the line the row is already showing`() {
        // given a two-minute build started at midnight
        val t0 = Instant.fromEpochMilliseconds(0)
        val state = upgrading(BuildingType.METAL_MINE, at = t0)

        // then a row that said one thing and a sheet that said another would be the worst failure
        // this pass has available
        assertEquals("→ LV 2 · done 00:02", English.resolve(state.rowFor(BuildingType.METAL_MINE, now = t0).toRowSheetUiState().verdict))
    }

    @Test
    fun `the sheet repeats the whole verdict when there is no ladder to say the rest`() {
        // given
        val state = colony()

        // when
        val sheet = state.rowFor(BuildingType.METAL_MINE).toRowSheetUiState()

        // then
        assertEquals("Metal Mine", English.resolve(sheet.name))
        assertEquals(1, sheet.level)
        assertEquals("+22/h metal · back in 1h 13m", English.resolve(sheet.verdict))
    }

    @Test
    fun `the sheet repeats only the first clause when it carries the ladder`() {
        // given the one row whose dropped clause *is* the ladder
        val state = colony(buildings = deep())

        // when
        val sheet = state.rowFor(BuildingType.ROBOTICS_FACTORY).toRowSheetUiState()

        // then saying "LV 10 → Nanite" above a ladder that says it in full would say it twice
        assertEquals("−20m per build", English.resolve(sheet.verdict))
    }

    @Test
    fun `a locked row's sheet is headed by its requirement`() {
        // given
        val state = colony()

        // when
        val sheet = state.rowFor(BuildingType.NANITE_FACTORY).toRowSheetUiState()

        // then — and no footer, because a locked row has no price yet
        assertEquals(Strings.requiresRobotics(10), sheet.verdict)
        assertEquals(null, sheet.footer)
    }

    @Test
    fun `a row the colony can pay for offers the same upgrade inside the sheet`() {
        // given
        val state = colony(resources = Resources.of(metal = 1_000, crystal = 1_000))

        // when
        val footer = state.rowFor(BuildingType.METAL_MINE).toRowSheetUiState().footer

        // then the sheet is somewhere a decision can be made rather than somewhere you read about
        // one and then go back
        assertEquals(
            SheetFooter(
                costs = listOf(
                    CostChipUiState(kind = ResourceKind.METAL, amount = Strings.groupedNumber(19), short = false),
                    CostChipUiState(kind = ResourceKind.CRYSTAL, amount = Strings.groupedNumber(4), short = false),
                ),
                duration = Strings.durationMinutes(2),
                action = SheetAction.Live(Strings.upgradeVerb()),
            ),
            footer,
        )
    }

    @Test
    fun `a row still filling its stores carries the same wait into the sheet`() {
        // given an empty stock
        val state = colony()

        // when
        val footer = checkNotNull(state.rowFor(BuildingType.METAL_MINE).toRowSheetUiState().footer)

        // then no disabled state here either: a player who cannot afford the level is told when
        assertEquals(SheetAction.Ghost(Strings.availableIn(Strings.durationMinutes(13))), footer.action)
    }

    // The one row on either screen whose verdict changes shape when a gate opens, and the only
    // `Sooner` in the game with nothing left to gate: `gatesOf(NANITE_FACTORY)` is empty, so this is
    // the single-clause form. Below Robotics 10 the row is locked and states the curve instead.
    @Test
    fun `once the gate opens the nanite factory states a saving like the factory below it`() {
        // given a colony that has just cleared Robotics 10
        val state = colony(buildings = pastTheGate())

        // when
        val row = state.rowFor(BuildingType.NANITE_FACTORY)

        // then it stops being about the shape of the curve and starts being about this colony's
        // longest build — and it has no gate clause, because it opens nothing
        val verdict = checkNotNull(row.verdict)
        assertEquals(StringId.SavedPerBuild, assertIs<TextRes.Message>(verdict.label).id)
        assertEquals(verdict.label, verdict.compactLabel)
        assertTrue(English.resolve(verdict.label).startsWith("−"), English.resolve(verdict.label))
    }

    // Every rate and every energy figure on both screens goes through `groupedByThousands`, and
    // nothing else in either suite puts four digits through one — so without this the comma could be
    // deleted from a dozen call sites and every test would still pass.
    @Test
    fun `a four-figure rate carries its comma`() {
        // given a colony deep enough for one more mine level to be worth more than a thousand an
        // hour — and with the plant to carry it, or the deficit would scale the gain back down
        val state = colony(
            buildings = Buildings.initial()
                .withLevel(BuildingType.METAL_MINE, BuildingLevel(20))
                .withLevel(BuildingType.SOLAR_PLANT, BuildingLevel(6)),
        )

        // then
        val verdict = checkNotNull(state.rowFor(BuildingType.METAL_MINE).verdict)
        assertTrue(English.resolve(verdict.label).startsWith("+1,"), English.resolve(verdict.label))
    }

    // Deep enough for a build to be worth shortening, and past two of the three gates: the metal
    // mine's thirteenth level is 1h 37m at robotics 3, which is the wait the factory's row is about.
    private fun deep(): Buildings = Buildings.initial()
        .withLevel(BuildingType.METAL_MINE, BuildingLevel(12))
        .withLevel(BuildingType.ROBOTICS_FACTORY, BuildingLevel(3))

    // Robotics at the level the Nanite Factory demands, with mines deep enough to have a build the
    // factory is worth shortening.
    private fun pastTheGate(): Buildings = Buildings.initial()
        .withLevel(BuildingType.METAL_MINE, BuildingLevel(20))
        .withLevel(
            BuildingType.ROBOTICS_FACTORY,
            BuildingLevel(PlaceholderBalance.NANITE_ROBOTICS_REQUIREMENT),
        )

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
        subscribed: Set<WatchTarget> = emptySet(),
        // **`CARRIED_FORWARD` rather than a new colony's own settings**, and it is the input half of
        // the square exactly as `watching` and `subscribed` are: under `BY_CATEGORY` a row carries no
        // square at all, so every assertion in this file about what the square shows would be an
        // assertion about `null`. The tests that *are* about the mode pass the other pair and say so.
        alerts: AlertSettings = AlertSettings.CARRIED_FORWARD,
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
        // The slipway competes with this screen's upgrades for the same metal and for nothing else:
        // a hull being made holds no construction slot and appears on no facility row.
        yard = emptyList(),
        // Which row the empire is watching, when it is watching one. A parameter rather than a
        // constant because it is the input half of the square: `watch` on a row is derived from it.
        watching = watching,
        subscribed = subscribed,
        // The yard's own ask, which lives on a Shipyard card and reaches no facility row — the
        // slipway is invisible to this screen and so is the question about it.
        hullAlerts = emptyMap(),
        // The flights' ask, which lives on a dispatch sheet and reaches no facility row either.
        announceFlights = false,
        // The settings sheet, and the one field here that *does* reach every facility row — it
        // decides whether the row carries a square at all. See the parameter's own note.
        alerts = alerts,
        // The player's standing, which is drawn by the strip above every screen and by nothing on
        // this one. Zero and empty together, which is the pair `core` keeps in step.
        experience = Experience.NONE,
        eventLog = emptyList(),
    )

    // A run in the shape `FleetRun` insists on — it left before it comes back, and it never gathers
    // deuterium. The 14 skiffs and 1 hauler are the manifest the strip was drawn with at 0.0.6.
    //
    // **`leftDaysAgo` is the phase, and it has to be said rather than defaulted.** The strip names
    // the next *event*, so which of a run's two moments it is counting down to depends on how long
    // ago it was dispatched: a day covers the longest flight on the map twice over, so 1 means "on
    // station, coming home" and 0 means "still on its way out". Until the strip cared, this helper
    // put `dispatchedAt` an hour before the return — which for a run four hours out is a dispatch in
    // the *future*, harmless then and meaningless now.
    private fun fleetRun(
        returnsAt: Instant,
        leftDaysAgo: Int,
        target: GalaxyCoordinate = GalaxyCoordinate(galaxy = 2, system = 117, slot = 9),
        ships: Ships = Ships(mapOf(ShipType.SKIFF to 14, ShipType.HAULER to 1)),
    ): FleetRun = FleetRun(
        target = target,
        ships = ships,
        gathering = ResourceKind.METAL,
        cargo = Resources.of(metal = 500),
        dispatchedAt = Instant.fromEpochMilliseconds(0) - leftDaysAgo.days,
        returnsAt = returnsAt,
        // Nothing this screen draws reads it: the strip names the next event, and whether the player
        // asked to be buzzed about it is the notification layer's question.
        announced = false,
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
            watching = Strings.watching(Strings.technologyName(Technology.EXTRACTION)),
        )

        // then
        assertEquals("watching Extraction", English.resolve(checkNotNull(uiState.watching)))
    }
}
