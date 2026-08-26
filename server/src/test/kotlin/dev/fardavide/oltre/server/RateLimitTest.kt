package dev.fardavide.oltre.server

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// **What stops `/v1/auth/*` costing money.** Step 45 of the provisioning walkthrough asks for it by
// name: those routes are unauthenticated, publicly reachable and do a signature check per request, on
// a host that bills per request. Everything here is a plain `…Test` because the limiter holds a clock
// and a map and nothing else — the socket it protects is `OltreServer.kt`'s.

// Four permits and a minute, so the arithmetic is readable in an assertion: one permit every fifteen
// seconds, and a burst of four. The shipped numbers are `RateLimit.kt`'s and are twenty times larger.
private const val PERMITS = 4
private val WINDOW = 1.minutes
private val EMISSION = 15.seconds

private fun limiter(clock: MovableClock, maxKeys: Int = 100): RateLimiter =
    RateLimiter(clock = clock, permits = PERMITS, window = WINDOW, maxKeys = maxKeys)

class RateLimitTest {

    @Test
    fun `a burst up to the permit count is allowed`() = runTest {
        val limiter = limiter(MovableClock(TEST_NOW))

        val verdicts = (1..PERMITS).map { limiter.admit("one-caller") }

        assertEquals(List(PERMITS) { RateVerdict.Allowed }, verdicts)
    }

    // The point of the whole class, and the one assertion that fails if the bucket never empties.
    @Test
    fun `the request after the burst is refused`() = runTest {
        val limiter = limiter(MovableClock(TEST_NOW))
        repeat(PERMITS) { limiter.admit("one-caller") }

        assertEquals(RateVerdict.Refused(EMISSION), limiter.admit("one-caller"))
    }

    // **The wait is a number and not a shrug**, because `ApiError.TooManyRequests` carries it to the
    // client and a client told only "no" has to guess. Half a permit's worth of waiting leaves half
    // of it still to do.
    @Test
    fun `the wait shrinks as it is waited out`() = runTest {
        val clock = MovableClock(TEST_NOW)
        val limiter = limiter(clock)
        repeat(PERMITS) { limiter.admit("one-caller") }

        clock.advanceBy(5.seconds)

        assertEquals(RateVerdict.Refused(10.seconds), limiter.admit("one-caller"))
    }

    @Test
    fun `one permit is earned back after one emission interval`() = runTest {
        val clock = MovableClock(TEST_NOW)
        val limiter = limiter(clock)
        repeat(PERMITS) { limiter.admit("one-caller") }

        clock.advanceBy(EMISSION)

        assertEquals(RateVerdict.Allowed, limiter.admit("one-caller"))
        assertEquals(RateVerdict.Refused(EMISSION), limiter.admit("one-caller"))
    }

    // A player who signs in, closes the game and comes back tomorrow must meet a full bucket rather
    // than the one permit an hour of arithmetic would leave them.
    @Test
    fun `a quiet window restores the whole burst`() = runTest {
        val clock = MovableClock(TEST_NOW)
        val limiter = limiter(clock)
        repeat(PERMITS) { limiter.admit("one-caller") }

        clock.advanceBy(WINDOW)

        assertEquals(List(PERMITS) { RateVerdict.Allowed }, (1..PERMITS).map { limiter.admit("one-caller") })
    }

    // **A limiter that pooled every caller would be a denial of service somebody else can trigger.**
    // One player in a retry loop must not sign the rest of them out.
    @Test
    fun `callers are counted apart`() = runTest {
        val limiter = limiter(MovableClock(TEST_NOW))
        repeat(PERMITS) { limiter.admit("the-loop") }

        assertEquals(RateVerdict.Refused(EMISSION), limiter.admit("the-loop"))
        assertEquals(RateVerdict.Allowed, limiter.admit("somebody-else"))
    }

    // **The map is the attack surface the limiter itself adds.** A caller rotating its address is a
    // caller minting a map entry per request, and an unbounded map on an instance with 512 MiB is a
    // way to take the server down through the thing built to keep it up.
    @Test
    fun `a caller that has recovered is forgotten rather than held`() = runTest {
        val clock = MovableClock(TEST_NOW)
        val limiter = limiter(clock, maxKeys = 2)

        limiter.admit("first")
        limiter.admit("second")
        // Both have spent one permit, so both are clear again one emission interval later — and a
        // third caller arriving then needs a slot that only forgetting them can give.
        clock.advanceBy(EMISSION)
        limiter.admit("third")

        assertEquals(1, limiter.tracked())
    }

    // The other half of the same rule, and the one that decides what happens under an actual flood:
    // every slot is taken by a caller still in debt, so somebody has to be dropped. It is the one
    // closest to recovery, because dropping the worst offender is what would reward being one.
    @Test
    fun `under a flood the caller closest to recovery is the one dropped`() = runTest {
        val clock = MovableClock(TEST_NOW)
        val limiter = limiter(clock, maxKeys = 2)

        repeat(PERMITS) { limiter.admit("early") }
        clock.advanceBy(1.seconds)
        repeat(PERMITS) { limiter.admit("late") }
        limiter.admit("newcomer")

        // `early` was dropped, so it meets a fresh bucket; `late` still owes what it owed.
        assertEquals(RateVerdict.Allowed, limiter.admit("early"))
        assertEquals(RateVerdict.Refused(EMISSION), limiter.admit("late"))
    }
}

// **Three settings that would make the limiter a lie rather than a limit**, refused where they are
// written rather than where they would first be noticed. None is hypothetical: all three are numbers
// somebody tuning this would reach for, and each has a silent failure on the other side of it — a
// closed door that reads as an outage, a window that lets everything through, and a map that can
// remember nobody and therefore counts nobody.
class RateLimiterSettingsTest {

    @Test
    fun `a limiter with no permits is refused`() {
        assertFailsWith<IllegalArgumentException> {
            RateLimiter(clock = MovableClock(TEST_NOW), permits = 0, window = WINDOW, maxKeys = 10)
        }
    }

    @Test
    fun `a limiter with no window is refused`() {
        assertFailsWith<IllegalArgumentException> {
            RateLimiter(clock = MovableClock(TEST_NOW), permits = PERMITS, window = Duration.ZERO, maxKeys = 10)
        }
    }

    @Test
    fun `a limiter that can remember nobody is refused`() {
        assertFailsWith<IllegalArgumentException> {
            RateLimiter(clock = MovableClock(TEST_NOW), permits = PERMITS, window = WINDOW, maxKeys = 0)
        }
    }
}

// **What the wait looks like once it is a number on the wire**, which is where truncation would undo
// the whole class: `Retry-After: 0` and `TooManyRequests(0)` both say *ask again now*.
class RetryAfterTest {

    @Test
    fun `a whole number of seconds is itself`() {
        assertEquals(15, RateVerdict.Refused(15.seconds).retryAfterSeconds)
    }

    @Test
    fun `a part of a second rounds up rather than away`() {
        assertEquals(1, RateVerdict.Refused(900.milliseconds).retryAfterSeconds)
        assertEquals(16, RateVerdict.Refused(15.seconds + 1.milliseconds).retryAfterSeconds)
    }
}

// **Where a caller's name comes from, and why it is the *last* hop rather than the first.** Cloud Run
// hands the app an `X-Forwarded-For` whose entries before the last are whatever the caller wrote —
// forging one is a header away, and a limiter keyed on a forgeable string is not a limiter. The last
// entry is the address Google's front end observed for itself.
//
// The failure modes are deliberately asymmetric. Read the last hop and a trusted proxy in front would
// pool every caller into one bucket: too strict, visible, and nobody is let through who should not
// be. Read the first and a caller rotating a forged header defeats the limiter outright, silently.
class ClientKeyTest {

    @Test
    fun `the last hop is the caller`() {
        assertEquals("203.0.113.7", clientKey(forwardedFor = "198.51.100.1, 203.0.113.7", remoteHost = "10.0.0.1"))
    }

    @Test
    fun `a single hop is the caller`() {
        assertEquals("203.0.113.7", clientKey(forwardedFor = "203.0.113.7", remoteHost = "10.0.0.1"))
    }

    @Test
    fun `spacing around a hop is not part of it`() {
        assertEquals("203.0.113.7", clientKey(forwardedFor = "198.51.100.1 ,  203.0.113.7  ", remoteHost = "10.0.0.1"))
    }

    // The dev loop, where nothing is in front of the server and the header is simply absent.
    @Test
    fun `with no header the socket answers`() {
        assertEquals("10.0.0.1", clientKey(forwardedFor = null, remoteHost = "10.0.0.1"))
    }

    // A header that is present and says nothing is the same as no header. Left as-is it would be one
    // empty-string bucket that every caller sending it would share.
    @Test
    fun `a header with nothing in it is not a caller`() {
        assertEquals("10.0.0.1", clientKey(forwardedFor = " , ", remoteHost = "10.0.0.1"))
    }
}
