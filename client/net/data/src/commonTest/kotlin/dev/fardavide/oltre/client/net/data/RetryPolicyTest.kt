package dev.fardavide.oltre.client.net.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RetryPolicyTest {

    // The awkward case the shape was chosen for: a policy that asks once has no waits at all, and
    // saying so takes no fields that mean nothing.
    @Test
    fun `asking once means waiting for nothing`() {
        assertEquals(emptyList(), RetryPolicy.ONCE.waits)
        assertEquals(1, RetryPolicy.ONCE.attempts)
    }

    @Test
    fun `three attempts are separated by two waits`() {
        // given / when
        val policy = RetryPolicy.exponential(attempts = 3, first = 1.seconds, factor = 3, cap = 30.seconds)

        // then
        assertEquals(listOf(1.seconds, 3.seconds), policy.waits)
        assertEquals(3, policy.attempts)
    }

    @Test
    fun `each wait is longer than the one before it`() {
        // given / when
        val waits = RetryPolicy.exponential(attempts = 5, first = 1.seconds, factor = 2, cap = 1.seconds * 999).waits

        // then
        assertEquals(listOf(1.seconds, 2.seconds, 4.seconds, 8.seconds), waits)
    }

    // Without a ceiling a long enough outage waits hours between attempts, and the app that comes
    // back to signal sits there.
    @Test
    fun `the wait stops growing at the cap`() {
        // given / when
        val waits = RetryPolicy.exponential(attempts = 5, first = 4.seconds, factor = 10, cap = 30.seconds).waits

        // then
        assertEquals(listOf(4.seconds, 30.seconds, 30.seconds, 30.seconds), waits)
    }

    @Test
    fun `the shipping policy asks three times over four seconds`() {
        // The number a dead server actually meets. It is invented and meant to move once `#111`
        // gives it something to measure against — what it must never be is zero.
        assertEquals(3, RetryPolicy.DEFAULT.attempts)
        assertTrue(RetryPolicy.DEFAULT.waits.all { it > Duration.ZERO })
        assertEquals(4.seconds, RetryPolicy.DEFAULT.waits.fold(Duration.ZERO) { total, wait -> total + wait })
    }

    @Test
    fun `a policy that never asks is refused`() {
        assertFailsWith<IllegalArgumentException> {
            RetryPolicy.exponential(attempts = 0, first = 1.seconds, factor = 2, cap = 30.seconds)
        }
    }

    @Test
    fun `a factor that shortens the wait each time is refused`() {
        assertFailsWith<IllegalArgumentException> {
            RetryPolicy.exponential(attempts = 3, first = 1.seconds, factor = 0, cap = 30.seconds)
        }
    }

    @Test
    fun `a wait that runs backwards is refused`() {
        assertFailsWith<IllegalArgumentException> {
            RetryPolicy.exponential(attempts = 3, first = -1.seconds, factor = 2, cap = 30.seconds)
        }
    }
}
