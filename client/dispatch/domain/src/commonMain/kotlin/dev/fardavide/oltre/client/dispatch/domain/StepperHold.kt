package dev.fardavide.oltre.client.dispatch.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

// **What a finger left on a stepper buys, as a list of waits.** Davide, 2026-08-17, on a sheet that
// opened at 55 hulls above a note saying three would do: *"Going from 55 to 3 is a lot of taps 😅"*.
// The suggested manifest makes that walk short in the common case; this is what stops the uncommon
// one being fifty taps.
//
// **A sequence rather than four constants and a loop**, and that is the point of the type. The claim
// the changelog makes — *55 down to 3 in about two seconds* — is unverifiable while it lives inside a
// `while (true)` in a draw scope, and it is a test here. The gesture that consumes it reads
// `wait, step, wait, step` and holds no arithmetic of its own.
//
// **All four numbers are invented and expected to move on the first device session**, under the
// motion-tuning precedent in `.claude/rules/session-roles.md`: nobody knows how long a thumb should
// rest before a control starts running, or how fast is fast without being unreadable, until they are
// holding a phone. They are arithmetic rather than measurement, and marked as such here rather than
// presented as settled. `StepperHoldTest` pins the *shape* — a rest, a ramp that only accelerates, a
// floor — so a tuning pass may move all four and leave every assertion standing.
object StepperHold {

    // The rest before anything happens, which is what keeps a tap a tap. Shorten it far and an
    // ordinary press starts running away; lengthen it far and the control feels dead under a thumb.
    val BEFORE_FIRST: Duration = 350.milliseconds

    // Slow enough to read the number it lands on, because the first repeat is the one that tells
    // the player what holding does.
    val FIRST: Duration = 120.milliseconds

    // Forty a second, which is about where a count stops being a number and becomes a blur. The
    // ramp settles here rather than accelerating without bound: past this the control overshoots
    // whatever it was aimed at, which costs a tap back and is the worse failure of the two.
    val FASTEST: Duration = 25.milliseconds

    // How much each repeat takes off the last. **Fifteen rather than the ten this shipped with for
    // an hour**, and the test is what moved it: at ten, two seconds bought 47 steps against the 52
    // that 55-down-to-3 needs, so the number was a fifth short of the trip it was chosen for. That
    // is the whole argument for this module — the claim was in a comment and the comment was wrong.
    // Fifteen reaches the floor in seven steps, which is soon enough for a real press to feel and
    // late enough to read as a ramp rather than two speeds.
    val GAIN: Duration = 15.milliseconds

    // Infinite by construction: a press ends when the finger comes off, not when a list runs out,
    // so the caller stops rather than this. Lazy, so an unbounded sequence costs nothing.
    fun waits(): Sequence<Duration> = sequence {
        yield(BEFORE_FIRST)
        var interval = FIRST
        while (true) {
            yield(interval)
            interval = maxOf(FASTEST, interval - GAIN)
        }
    }
}
