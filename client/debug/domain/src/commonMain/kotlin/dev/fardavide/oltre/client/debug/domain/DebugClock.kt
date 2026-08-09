package dev.fardavide.oltre.client.debug.domain

import kotlin.time.Duration
import kotlin.time.Instant

// How far ahead of the wall clock the colony is running, and the whole of what "skipping" means in
// this game. There is no multiplier and no timer — Davide's call, 2026-08-09, and the right one for
// an asynchronous game: a ×10 speed-up still asks you to sit and wait, where an offset arrives at
// the answer immediately and leaves `advance` to compute the same state it would have computed on
// its own.
//
// The invariant that makes it safe is that game time is still a *monotone function of wall time*:
//
//     gameTime = wallTime + offset,  offset >= 0
//
// so every rule the shell already relies on survives. `advance` is never asked to run backwards,
// the one-second tick keeps ticking from wherever the skip left the colony, and a save written
// after a skip is an ordinary save that happens to be stamped in the future.
//
// **Never negative.** Skipping backwards would mean asking core to un-apply events, which is not
// something an append-only log can do; and a colony cannot be un-built. A target behind the current
// game time is clamped rather than refused, for the same reason `resume` clamps a save from the
// future: losing a colony over an arithmetic edge would be absurd.
data class DebugClock(val offset: Duration = Duration.ZERO) {

    init {
        require(!offset.isNegative()) { "the debug clock cannot run behind the wall clock: was $offset" }
    }

    // The instant the colony believes it is. Takes the wall clock as a parameter for the reason
    // `core` does: a clock this reads for itself is a clock no test can place.
    fun now(wallClock: Instant): Instant = wallClock + offset

    // Where the colony is now, translated back to when the device will actually get there. This is
    // what a local notification has to be booked at: the alert instants come out of `futureEvents`
    // in *game* time, and the operating system fires alarms in *real* time. Booked without this,
    // a colony skipped four hours forward would have every one of its alerts fire four hours late
    // — the check-in loop, which on iPhone is the entire game, silently broken by the debug menu.
    fun toRealTime(gameTime: Instant): Instant = gameTime - offset

    // Move game time to `target` without moving the wall clock. Clamped at the present: see above.
    fun skippingTo(target: Instant, wallClock: Instant): DebugClock =
        DebugClock(maxOf(target - wallClock, Duration.ZERO))

    companion object {

        // The offset a save implies, and the reason none of this needs a second file on disk.
        //
        // A colony that was skipped is written down with `lastUpdatedAt` in the future. Read it back
        // with a clock at ×1 and the shell's own clamp (`to = maxOf(now, saved.lastUpdatedAt)`)
        // freezes the colony until the wall clock catches up — skip four hours, close the app, and
        // the game is dead for four hours. Deriving the offset from the save instead means the
        // colony simply carries on from where the skip left it, and the save is the only record
        // needed to do it.
        //
        // Zero for every save that was never skipped, which is every save the game has ever written
        // before this module existed.
        fun resuming(savedAt: Instant?, wallClock: Instant): DebugClock =
            DebugClock(maxOf((savedAt ?: wallClock) - wallClock, Duration.ZERO))
    }
}
