package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class FutureEventsTest {

    @Test
    fun `a colony with nothing in flight has nothing coming`() {
        assertEquals(emptyList(), futureEvents(GameState.initial()))
    }

    @Test
    fun `every running build is one upcoming completion`() {
        // given two facilities building in parallel
        val t0 = Instant.fromEpochMilliseconds(0)
        val funded = GameState.initial().fundedFor(BuildingType.METAL_MINE, BuildingType.SOLAR_PLANT)
        val state = funded
            .started(BuildingType.METAL_MINE, at = t0)
            .started(BuildingType.SOLAR_PLANT, at = t0)

        // when
        val upcoming = futureEvents(state)

        // then
        assertEquals(
            state.builds.values.map { it.building }.toSet(),
            upcoming.filterIsInstance<FutureEvent.BuildCompletes>().map { it.building }.toSet(),
        )
        assertEquals(
            state.completionOf(BuildingType.METAL_MINE),
            upcoming.filterIsInstance<FutureEvent.BuildCompletes>().first { it.building == BuildingType.METAL_MINE }.at,
        )
    }

    @Test
    fun `a build completion carries the level it will reach`() {
        // given
        val t0 = Instant.fromEpochMilliseconds(0)
        val state = GameState.initial().fundedFor(BuildingType.METAL_MINE).started(BuildingType.METAL_MINE, at = t0)

        // when
        val upcoming = futureEvents(state)

        // then
        assertEquals(
            listOf(
                FutureEvent.BuildCompletes(
                    building = BuildingType.METAL_MINE,
                    toLevel = BuildingLevel(2),
                    at = state.completionOf(BuildingType.METAL_MINE),
                ),
            ),
            upcoming,
        )
    }

    @Test
    fun `a running research is one upcoming completion carrying the level it will reach`() {
        // given - the alerts are booked from this list, so a research the player is never told
        // about is the feature failing at its one job
        val t0 = Instant.fromEpochMilliseconds(0)
        val state = GameState.initial().researching(Technology.EXTRACTION, at = t0)

        // when
        val upcoming = futureEvents(state)

        // then
        assertEquals(
            listOf(
                FutureEvent.ResearchCompletes(
                    technology = Technology.EXTRACTION,
                    toLevel = TechLevel(1),
                    at = state.project().completesAt,
                ),
            ),
            upcoming,
        )
    }

    @Test
    fun `a colony researching and building has both coming`() {
        // given
        val t0 = Instant.fromEpochMilliseconds(0)
        val state = GameState.initial()
            .fundedFor(BuildingType.METAL_MINE)
            .started(BuildingType.METAL_MINE, at = t0)
            .researching(Technology.EXTRACTION, at = t0)

        // when
        val upcoming = futureEvents(state)

        // then
        assertEquals(2, upcoming.size)
        assertEquals(upcoming.map { it.at }.sorted(), upcoming.map { it.at })
    }

    @Test
    fun `a build and a research at the same instant put the build first`() {
        // given - mirroring exactly what advance does at a shared instant
        val t0 = Instant.fromEpochMilliseconds(0)
        val together = t0 + 2.hours
        val state = GameState.initial().copy(
            builds = mapOf(
                BuildingType.NANITE_FACTORY to BuildJob(
                    building = BuildingType.NANITE_FACTORY,
                    toLevel = BuildingLevel(1),
                    startedAt = t0,
                    completesAt = together,
                ),
            ),
            activeResearch = ResearchJob(
                technology = Technology.EXTRACTION,
                toLevel = TechLevel(1),
                startedAt = t0,
                completesAt = together,
            ),
        )

        // when
        val upcoming = futureEvents(state)

        // then - the last building in the enum still sorts ahead of the research
        assertEquals(listOf("BuildCompletes", "ResearchCompletes"), upcoming.map { it::class.simpleName })
    }

    @Test
    fun `a research and an arrival at the same instant put the research first`() {
        // given
        val t0 = Instant.fromEpochMilliseconds(0)
        val together = t0 + 2.hours
        val state = GameState.initial().copy(
            activeResearch = ResearchJob(
                technology = Technology.EXTRACTION,
                toLevel = TechLevel(1),
                startedAt = t0,
                completesAt = together,
            ),
            runs = listOf(inboundRun(returnsAt = together)),
        )

        // when
        val upcoming = futureEvents(state)

        // then
        assertEquals(listOf("ResearchCompletes", "FleetReturns"), upcoming.map { it::class.simpleName })
    }

    @Test
    fun `a fleet in flight is one upcoming arrival`() {
        // given
        val t0 = Instant.fromEpochMilliseconds(0)
        val target = GalaxyCoordinate(galaxy = 2, system = 117, slot = 9)
        val ships = Ships.of(ShipType.SKIFF, 14)
        val cargo = Resources.of(metal = 500)
        val run = FleetRun(
            target = target,
            ships = ships,
            gathering = ResourceKind.METAL,
            cargo = cargo,
            dispatchedAt = t0,
            returnsAt = t0 + 3.hours,
        )
        val state = GameState.initial().copy(runs = listOf(run))

        // when
        val upcoming = futureEvents(state)

        // then — the prediction carries the hold as well as the manifest, because an alert booked
        // in advance may only say what `advance` will actually credit
        assertEquals(
            listOf(
                FutureEvent.FleetReturns(
                    target = target,
                    ships = ships,
                    cargo = cargo,
                    dispatchedAt = t0,
                    at = t0 + 3.hours,
                ),
            ),
            upcoming,
        )
    }

    @Test
    fun `upcoming events come back in the order they will happen`() {
        // given a fleet landing between two build completions.
        //
        // The Deuterium Synthesizer rather than the Robotics Factory as the slow half: at the 10x
        // opening discount a first Robotics Factory and a second Metal Mine both come out at eight
        // minutes, and two completions at the same instant are what the *next* test is about. The
        // synthesizer is the cheapest row that still lands well clear of the mine.
        val t0 = Instant.fromEpochMilliseconds(0)
        val state = GameState.initial()
            .fundedFor(BuildingType.METAL_MINE, BuildingType.DEUTERIUM_SYNTHESIZER)
            .started(BuildingType.METAL_MINE, at = t0)
            .started(BuildingType.DEUTERIUM_SYNTHESIZER, at = t0)
            .let { started ->
                val betweenTheTwo = started.completionOf(BuildingType.METAL_MINE) +
                    (started.completionOf(BuildingType.DEUTERIUM_SYNTHESIZER) -
                        started.completionOf(BuildingType.METAL_MINE)) / 2
                started.copy(runs = listOf(inboundRun(returnsAt = betweenTheTwo)))
            }

        // when
        val upcoming = futureEvents(state)

        // then — the metal mine is the cheapest and quickest of the two so it lands first
        assertEquals(
            listOf("BuildCompletes", "FleetReturns", "BuildCompletes"),
            upcoming.map { it::class.simpleName },
        )
        assertEquals(upcoming.map { it.at }.sorted(), upcoming.map { it.at })
    }

    @Test
    fun `two completions at the same instant come back in the order advance applies them`() {
        // given two builds contrived to finish together, listed in the map back to front
        val t0 = Instant.fromEpochMilliseconds(0)
        val together = t0 + 2.hours
        val state = GameState.initial().copy(
            builds = linkedMapOf(
                BuildingType.SOLAR_PLANT to BuildJob(
                    building = BuildingType.SOLAR_PLANT,
                    toLevel = BuildingLevel(2),
                    startedAt = t0,
                    completesAt = together,
                ),
                BuildingType.METAL_MINE to BuildJob(
                    building = BuildingType.METAL_MINE,
                    toLevel = BuildingLevel(2),
                    startedAt = t0,
                    completesAt = together,
                ),
            ),
        )

        // when
        val upcoming = futureEvents(state)

        // then — building order, exactly as the event log will record them
        assertEquals(
            listOf(BuildingType.METAL_MINE, BuildingType.SOLAR_PLANT),
            upcoming.filterIsInstance<FutureEvent.BuildCompletes>().map { it.building },
        )
    }

    @Test
    fun `a build and an arrival at the same instant put the build first`() {
        // given — advance applies completions before the arrival at a shared instant
        val t0 = Instant.fromEpochMilliseconds(0)
        val together = t0 + 2.hours
        val state = GameState.initial().copy(
            builds = mapOf(
                BuildingType.METAL_MINE to BuildJob(
                    building = BuildingType.METAL_MINE,
                    toLevel = BuildingLevel(2),
                    startedAt = t0,
                    completesAt = together,
                ),
            ),
            runs = listOf(inboundRun(returnsAt = together)),
        )

        // when
        val upcoming = futureEvents(state)

        // then
        assertEquals(listOf("BuildCompletes", "FleetReturns"), upcoming.map { it::class.simpleName })
    }
}

// The smallest run that stands in for "a fleet is coming home at this instant" — one skiff and a
// token hold, so the tests above stay about ordering rather than about what a hold is worth.
private fun inboundRun(returnsAt: Instant): FleetRun = FleetRun(
    target = GalaxyCoordinate(galaxy = 1, system = 1, slot = 1),
    ships = Ships.of(ShipType.SKIFF, 1),
    gathering = ResourceKind.METAL,
    cargo = Resources.of(metal = 10),
    dispatchedAt = returnsAt - 1.hours,
    returnsAt = returnsAt,
)
