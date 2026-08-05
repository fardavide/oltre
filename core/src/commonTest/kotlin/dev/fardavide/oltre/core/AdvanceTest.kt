package dev.fardavide.oltre.core

import kotlin.test.Test
import kotlin.test.assertEquals
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
