package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class AdvanceTest {

    @Test
    fun `advancing one hour accrues metal at the hourly production rate`() {
        // given
        val start = Instant.fromEpochMilliseconds(0)
        val state = GameState.initial()

        // when
        val result = advance(state, from = start, to = start + 1.hours)

        // then
        val expected = state.resources.metal + PlaceholderBalance.METAL_PRODUCTION_PER_HOUR
        assertEquals(expected, result.resources.metal)
    }

    @Test
    fun `advancing one hour accrues crystal at the hourly production rate`() {
        // given
        val start = Instant.fromEpochMilliseconds(0)
        val state = GameState.initial()

        // when
        val result = advance(state, from = start, to = start + 1.hours)

        // then
        val expected = state.resources.crystal + PlaceholderBalance.CRYSTAL_PRODUCTION_PER_HOUR
        assertEquals(expected, result.resources.crystal)
    }

    @Test
    fun `advancing one hour accrues deuterium at the hourly production rate`() {
        // given
        val start = Instant.fromEpochMilliseconds(0)
        val state = GameState.initial()

        // when
        val result = advance(state, from = start, to = start + 1.hours)

        // then
        val expected = state.resources.deuterium + PlaceholderBalance.DEUTERIUM_PRODUCTION_PER_HOUR
        assertEquals(expected, result.resources.deuterium)
    }

    @Test
    fun `metal production grows with the metal mine level`() {
        // given
        val start = Instant.fromEpochMilliseconds(0)
        val initial = GameState.initial()
        val upgraded = initial.copy(
            buildings = initial.buildings.copy(metalMine = BuildingLevel(2)),
        )

        // when
        val producedAtLevel1 = advance(initial, from = start, to = start + 1.hours).metalAccruedSince(initial)
        val producedAtLevel2 = advance(upgraded, from = start, to = start + 1.hours).metalAccruedSince(upgraded)

        // then
        assertEquals(PlaceholderBalance.metalProductionPerHour(BuildingLevel(1)), producedAtLevel1)
        assertEquals(PlaceholderBalance.metalProductionPerHour(BuildingLevel(2)), producedAtLevel2)
        assertTrue(producedAtLevel2 > producedAtLevel1, "level 2 must out-produce level 1")
    }

    @Test
    fun `crystal production grows with the crystal mine level`() {
        // given
        val start = Instant.fromEpochMilliseconds(0)
        val initial = GameState.initial()
        // Solar plant raised so the scenario stays energy-sufficient: this test isolates
        // the level->production relation, EnergyTest owns the scaling behaviour.
        val upgraded = initial.copy(
            buildings = initial.buildings.copy(crystalMine = BuildingLevel(3), solarPlant = BuildingLevel(2)),
        )

        // when
        val produced = advance(upgraded, from = start, to = start + 1.hours).resources.crystal -
            upgraded.resources.crystal

        // then
        assertEquals(PlaceholderBalance.crystalProductionPerHour(BuildingLevel(3)), produced)
        assertTrue(
            produced > advance(initial, from = start, to = start + 1.hours).resources.crystal -
                initial.resources.crystal,
            "level 3 must out-produce level 1",
        )
    }

    @Test
    fun `deuterium production grows with the deuterium synthesizer level`() {
        // given
        val start = Instant.fromEpochMilliseconds(0)
        val initial = GameState.initial()
        val upgraded = initial.copy(
            buildings = initial.buildings.copy(deuteriumSynthesizer = BuildingLevel(4), solarPlant = BuildingLevel(3)),
        )

        // when
        val produced = advance(upgraded, from = start, to = start + 1.hours).resources.deuterium -
            upgraded.resources.deuterium

        // then
        assertEquals(PlaceholderBalance.deuteriumProductionPerHour(BuildingLevel(4)), produced)
        assertTrue(
            produced > advance(initial, from = start, to = start + 1.hours).resources.deuterium -
                initial.resources.deuterium,
            "level 4 must out-produce level 1",
        )
    }

    private fun GameState.metalAccruedSince(before: GameState): Long =
        resources.metal - before.resources.metal

    @Test
    fun `advancing in one span equals advancing through any intermediate instant`() {
        // given
        val t0 = Instant.fromEpochMilliseconds(0)
        val t2 = t0 + 7.days
        val state = GameState.initial()
        val oneShot = advance(state, from = t0, to = t2)

        val splitMilliseconds = listOf(
            1L,
            750L,
            1_499L,
            3_600_000L,
            86_399_999L,
            t2.toEpochMilliseconds() - 1,
        )
        for (milliseconds in splitMilliseconds) {
            val t1 = Instant.fromEpochMilliseconds(milliseconds)

            // when
            val stepped = advance(advance(state, from = t0, to = t1), from = t1, to = t2)

            // then
            assertEquals(oneShot, stepped, "split at ${milliseconds}ms diverged")
        }
    }

    // The property everything downstream rests on, held against a colony with all three kinds of
    // event in flight at once. Research is the newest of them and the one that changes what the
    // *following* span accrues, so a completion the split lands on either side of is exactly where
    // it would break.
    @Test
    fun `advancing in one span equals advancing through any intermediate instant with everything in flight`() {
        // given a colony building two facilities, researching, and expecting a fleet
        val t0 = Instant.fromEpochMilliseconds(0)
        val t2 = t0 + 7.days
        val busy = GameState.initial()
            .fundedFor(BuildingType.METAL_MINE, BuildingType.SOLAR_PLANT)
            .started(BuildingType.METAL_MINE, at = t0)
            .started(BuildingType.SOLAR_PLANT, at = t0)
            .researching(Technology.EXTRACTION, at = t0)
            .copy(
                runs = listOf(
                    FleetRun(
                        target = GalaxyCoordinate(galaxy = 2, system = 117, slot = 9),
                        ships = Ships.of(ShipType.SKIFF, 8),
                        gathering = ResourceKind.METAL,
                        cargo = Resources.of(metal = 400, crystal = 120),
                        dispatchedAt = t0,
                        returnsAt = t0 + 3.hours,
                    ),
                ),
            )
        val oneShot = advance(busy, from = t0, to = t2)
        val researchCompletesAt = busy.project().completesAt

        // when the span is split around every event boundary and on each of them exactly
        val splits = buildList {
            add(t0 + 1.hours)
            add(researchCompletesAt - 1.milliseconds)
            add(researchCompletesAt)
            add(researchCompletesAt + 1.milliseconds)
            add(t0 + 3.hours)
            busy.builds.values.forEach { job ->
                add(job.completesAt - 1.milliseconds)
                add(job.completesAt)
                add(job.completesAt + 1.milliseconds)
            }
        }

        // then
        for (t1 in splits) {
            assertEquals(oneShot, advance(advance(busy, from = t0, to = t1), from = t1, to = t2), "split at $t1 diverged")
        }
    }
}
