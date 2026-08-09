package dev.fardavide.oltre.client.debug.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class DebugClockTest {

    @Test
    fun `an untouched clock is the wall clock`() {
        assertEquals(EPOCH, DebugClock().now(EPOCH))
        assertEquals(EPOCH, DebugClock().toRealTime(EPOCH))
    }

    @Test
    fun `skipping moves game time and leaves the wall clock alone`() {
        // when
        val clock = DebugClock().skippingTo(EPOCH + 4.hours, wallClock = EPOCH)

        // then
        assertEquals(4.hours, clock.offset)
        assertEquals(EPOCH + 4.hours, clock.now(EPOCH))
    }

    @Test
    fun `game time keeps running after a skip`() {
        // The property that makes an offset the right model rather than a jump: the one-second tick
        // carries on from where the skip left the colony instead of stalling at it.
        val clock = DebugClock().skippingTo(EPOCH + 4.hours, wallClock = EPOCH)

        assertEquals(EPOCH + 4.hours + 30.minutes, clock.now(EPOCH + 30.minutes))
    }

    @Test
    fun `skips accumulate`() {
        // given
        val once = DebugClock().skippingTo(EPOCH + 1.hours, wallClock = EPOCH)

        // when — the second skip is asked for in game time, which is already an hour ahead
        val twice = once.skippingTo(once.now(EPOCH) + 1.hours, wallClock = EPOCH)

        // then
        assertEquals(2.hours, twice.offset)
    }

    @Test
    fun `a target behind the present is clamped rather than rewinding the colony`() {
        // An append-only log cannot un-apply events, so there is nothing sensible for a backwards
        // skip to mean. Clamped rather than refused, for the reason `resume` clamps a save from the
        // future: an arithmetic edge must not be able to cost a colony.
        val clock = DebugClock(2.hours).skippingTo(EPOCH - 1.hours, wallClock = EPOCH)

        assertEquals(Duration.ZERO, clock.offset)
    }

    @Test
    fun `a negative offset cannot be constructed at all`() {
        assertFailsWith<IllegalArgumentException> { DebugClock((-1).hours) }
    }

    @Test
    fun `real time is the inverse of game time`() {
        // The round trip a notification depends on: an alert computed in game time has to come back
        // to the instant the operating system should actually raise it.
        val clock = DebugClock(6.hours)
        val gameTime = clock.now(EPOCH)

        assertEquals(EPOCH, clock.toRealTime(gameTime))
    }

    @Test
    fun `an alert is booked at the real instant the device will reach it`() {
        // Booked without the translation, a colony skipped six hours forward has every alert fire
        // six hours late — the check-in loop, which on iPhone is the whole game, quietly broken by
        // the debug menu.
        val clock = DebugClock(6.hours)
        val completesAt = clock.now(EPOCH) + 30.minutes

        assertEquals(EPOCH + 30.minutes, clock.toRealTime(completesAt))
    }

    @Test
    fun `a colony that was never skipped resumes at the wall clock`() {
        // given — the ordinary case, and every save written before this module existed
        val savedAt = EPOCH - 3.hours

        // then
        assertEquals(Duration.ZERO, DebugClock.resuming(savedAt, wallClock = EPOCH).offset)
    }

    @Test
    fun `a first launch resumes at the wall clock`() {
        assertEquals(Duration.ZERO, DebugClock.resuming(savedAt = null, wallClock = EPOCH).offset)
    }

    @Test
    fun `a skipped colony picks up where the skip left it`() {
        // The reason the offset needs no file of its own: a skipped colony is written down stamped
        // in the future, and that stamp is the offset. Without this the shell's own clamp freezes
        // the game until the wall clock catches up — skip four hours, close the app, and it is dead
        // for four hours.
        val savedAt = EPOCH + 4.hours

        val clock = DebugClock.resuming(savedAt, wallClock = EPOCH)

        assertEquals(4.hours, clock.offset)
        assertEquals(savedAt, clock.now(EPOCH))
    }

    @Test
    fun `the offset shrinks as the wall clock catches up`() {
        // A skip is a loan against real time, not a permanent lead: an hour after a four-hour skip
        // the colony is only three hours ahead, and once the wall clock passes the saved instant the
        // colony is back on ordinary time with nothing to undo.
        val savedAt = EPOCH + 4.hours

        assertEquals(3.hours, DebugClock.resuming(savedAt, wallClock = EPOCH + 1.hours).offset)
        assertEquals(Duration.ZERO, DebugClock.resuming(savedAt, wallClock = EPOCH + 5.hours).offset)
    }
}
