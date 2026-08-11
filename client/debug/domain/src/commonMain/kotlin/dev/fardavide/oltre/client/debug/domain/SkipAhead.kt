package dev.fardavide.oltre.client.debug.domain

import dev.fardavide.oltre.core.FutureEvent
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.advance
import dev.fardavide.oltre.core.futureEvents
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// Where "skip ahead" lands. Two cases, and the second exists because the first has a hole in it.
sealed interface SkipAhead {

    val to: Instant

    // The next thing the simulation already knows is going to happen — a build completing, a
    // project finishing, a probe landing. This is the interesting case and the reason the action is
    // "skip to the next event" rather than "skip an hour": in an asynchronous game the only
    // instants worth visiting are the ones something happens at, and `futureEvents` is already the
    // authority on where they are. The same list the notifications are booked from, read the same
    // way, so the menu can never disagree with the lock screen about what happens next.
    data class ToEvent(val event: FutureEvent) : SkipAhead {
        override val to: Instant get() = event.at
    }

    // Nothing is in flight, so there is no next event to skip to. Without this case the debug menu
    // has a dead end that is easy to reach and impossible to escape: finish a build, find the next
    // one unaffordable, and "skip to next event" does nothing while the only way forward is to wait
    // out the accrual in real time. A flat hour is not a speed-up — it is what makes the action
    // total. Flagged to Davide, 2026-08-09, as the one thing added beyond the chosen list.
    data class ByFallback(override val to: Instant) : SkipAhead
}

// How far a skip goes when there is nothing to skip *to*. An hour, because it is long enough to
// move the stocks visibly at these rates and short enough to step through a morning in a few taps.
val SKIP_FALLBACK: Duration = 1.hours

// The whole of a skip: where it lands, what the colony looks like there, and the clock that gets it
// there. Returned together because they are one decision — a caller that computed the target and
// then forgot to move the clock would leave a colony stamped in the future and running at ×1, which
// is the frozen-game bug `DebugClock.resuming` exists to prevent.
data class SkipOutcome(val state: GameState, val at: Instant, val clock: DebugClock)

// Everything the debug menu's one time verb decides, as a function of its inputs.
//
// It lives here rather than in the composition root, and that is the point: at 0.2.5 this arithmetic
// was written inside a composable, where nothing could execute it — the shell's coverage fell 7.7
// points in one commit and the only thing holding the logic up was that its pieces were tested
// separately. `session-roles.md` says a cloud session should push a feature's logic down into a
// module it can test; this is that, applied to the file that needed it.
//
// Two `advance` calls rather than one, deliberately. The first brings the colony up to *now*,
// because the target has to be chosen against the state the player is actually looking at; the
// second carries it to the target. Composing them in one hop would ask `futureEvents` about a
// colony that has not accrued yet.
fun skipping(
    state: GameState,
    lastUpdatedAt: Instant,
    clock: DebugClock,
    wallClock: Instant,
): SkipOutcome {
    // The same clamp the tick loop applies, and for the same reason: a wall clock that stepped
    // backwards must not ask `advance` to run backwards.
    val at = maxOf(clock.now(wallClock), lastUpdatedAt)
    val advanced = advance(state, from = lastUpdatedAt, to = at)
    val target = skipAhead(advanced, now = at).to
    return SkipOutcome(
        state = advance(advanced, from = at, to = target),
        at = target,
        clock = clock.skippingTo(target, wallClock = wallClock),
    )
}

// Pure, like everything else that decides a debug action: `now` is a parameter, the state is the
// only other input, and the answer is a description of the jump rather than the jump itself. The
// shell performs it by calling `advance` — this module never touches a colony.
fun skipAhead(state: GameState, now: Instant): SkipAhead {
    // Strictly after `now`, the same rule `notificationsFor` applies and for the same reason: an
    // event at or before this instant is one `advance` is about to apply or already has, so
    // "skipping" to it would move nothing and the menu would look broken.
    //
    // The minimum is taken rather than the first, so this does not quietly depend on `futureEvents`
    // staying sorted — it is sorted today, and nothing here needs it to be.
    val next = futureEvents(state, now = now).filter { it.at > now }.minByOrNull { it.at }
    return if (next != null) SkipAhead.ToEvent(next) else SkipAhead.ByFallback(now + SKIP_FALLBACK)
}
