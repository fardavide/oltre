package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
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
        val producedAtLevel1 = advance(initial, from = start, to = start + 1.hours).resources.metal
        val producedAtLevel2 = advance(upgraded, from = start, to = start + 1.hours).resources.metal

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
        val upgraded = initial.copy(
            buildings = initial.buildings.copy(crystalMine = BuildingLevel(3)),
        )

        // when
        val produced = advance(upgraded, from = start, to = start + 1.hours).resources.crystal

        // then
        assertEquals(PlaceholderBalance.crystalProductionPerHour(BuildingLevel(3)), produced)
        assertTrue(
            produced > advance(initial, from = start, to = start + 1.hours).resources.crystal,
            "level 3 must out-produce level 1",
        )
    }

    @Test
    fun `deuterium production grows with the deuterium synthesizer level`() {
        // given
        val start = Instant.fromEpochMilliseconds(0)
        val initial = GameState.initial()
        val upgraded = initial.copy(
            buildings = initial.buildings.copy(deuteriumSynthesizer = BuildingLevel(4)),
        )

        // when
        val produced = advance(upgraded, from = start, to = start + 1.hours).resources.deuterium

        // then
        assertEquals(PlaceholderBalance.deuteriumProductionPerHour(BuildingLevel(4)), produced)
        assertTrue(
            produced > advance(initial, from = start, to = start + 1.hours).resources.deuterium,
            "level 4 must out-produce level 1",
        )
    }

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
}
