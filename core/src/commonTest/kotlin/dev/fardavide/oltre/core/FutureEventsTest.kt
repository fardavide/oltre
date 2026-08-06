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
    fun `a fleet in flight is one upcoming arrival`() {
        // given
        val t0 = Instant.fromEpochMilliseconds(0)
        val origin = Coordinates(galaxy = 2, system = 117, position = 9)
        val ships = mapOf(ShipType.CARGO to 14)
        val state = GameState.initial().copy(
            returningFleet = ReturningFleet(
                ships = ships,
                cargo = Resources.of(metal = 500),
                origin = origin,
                arrivesAt = t0 + 3.hours,
            ),
        )

        // when
        val upcoming = futureEvents(state)

        // then
        assertEquals(
            listOf(FutureEvent.FleetArrives(origin = origin, ships = ships, at = t0 + 3.hours)),
            upcoming,
        )
    }

    @Test
    fun `upcoming events come back in the order they will happen`() {
        // given a fleet landing between two build completions
        val t0 = Instant.fromEpochMilliseconds(0)
        val state = GameState.initial()
            .fundedFor(BuildingType.METAL_MINE, BuildingType.ROBOTICS_FACTORY)
            .started(BuildingType.METAL_MINE, at = t0)
            .started(BuildingType.ROBOTICS_FACTORY, at = t0)
            .let { started ->
                val betweenTheTwo = started.completionOf(BuildingType.METAL_MINE) +
                    (started.completionOf(BuildingType.ROBOTICS_FACTORY) - started.completionOf(BuildingType.METAL_MINE)) / 2
                started.copy(
                    returningFleet = ReturningFleet(
                        ships = mapOf(ShipType.CARGO to 1),
                        cargo = Resources.of(metal = 10),
                        origin = Coordinates(galaxy = 1, system = 1, position = 1),
                        arrivesAt = betweenTheTwo,
                    ),
                )
            }

        // when
        val upcoming = futureEvents(state)

        // then — the metal mine is the cheapest and quickest of the two so it lands first
        assertEquals(
            listOf("BuildCompletes", "FleetArrives", "BuildCompletes"),
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
            returningFleet = ReturningFleet(
                ships = mapOf(ShipType.CARGO to 1),
                cargo = Resources.of(metal = 10),
                origin = Coordinates(galaxy = 1, system = 1, position = 1),
                arrivesAt = together,
            ),
        )

        // when
        val upcoming = futureEvents(state)

        // then
        assertEquals(listOf("BuildCompletes", "FleetArrives"), upcoming.map { it::class.simpleName })
    }
}

private fun GameState.started(building: BuildingType, at: Instant): GameState =
    when (val result = startUpgrade(this, building, at = at)) {
        is StartUpgradeResult.Started -> result.state
        else -> error("could not start $building: $result")
    }
