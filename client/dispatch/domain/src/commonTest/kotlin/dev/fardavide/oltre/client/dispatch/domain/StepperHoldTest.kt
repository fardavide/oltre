package dev.fardavide.oltre.client.dispatch.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

// The cadence of a held stepper as arithmetic, which is the whole reason it is a module. Every
// number here is invented and expected to move on the first device session — see `StepperHold` —
// so what these tests pin is the *shape*: a rest before anything happens, a ramp that only ever
// gets faster, and a floor it settles on. A tuning pass may move all four constants and should
// leave every assertion below standing.
class StepperHoldTest {

    @Test
    fun `nothing happens until the finger has rested`() {
        // The first wait is what makes a tap a tap. Without it every press would step twice — once
        // for the repeat and once for the release — which is the off-by-one `DispatchSheet` guards.
        assertEquals(StepperHold.BEFORE_FIRST, StepperHold.waits().first())
        assertTrue(StepperHold.BEFORE_FIRST > StepperHold.FIRST, "the rest is longer than a repeat")
    }

    @Test
    fun `a hold only ever gets faster and never faster than the floor`() {
        val waits = StepperHold.waits().take(200).toList()

        // Monotone from the first repeat onwards: the rest at the head is longer on purpose.
        waits.drop(1).zipWithNext { a, b -> assertTrue(b <= a, "the ramp sped up then slowed: $a then $b") }
        assertTrue(waits.all { it >= StepperHold.FASTEST }, "a wait went under the floor")
        assertEquals(StepperHold.FASTEST, waits.last(), "the ramp never reached its floor")
    }

    @Test
    fun `the ramp reaches its floor quickly enough to matter and not so fast that it is a jump`() {
        // A ramp that arrived at the floor immediately would be a two-speed control with no ramp at
        // all; one that took a hundred steps would never reach it in a real press.
        val toFloor = StepperHold.waits().indexOfFirst { it == StepperHold.FASTEST }

        assertTrue(toFloor in 5..30, "the ramp took $toFloor steps to reach its floor")
    }

    @Test
    fun `a two second hold crosses the pool the suggestion was introduced for`() {
        // **The claim the changelog makes, as a test rather than a comment.** Davide counted the
        // trip — 55 hulls down to 3 — and a control that could not make it in one comfortable press
        // would not have answered him. Two seconds is about as long as a thumb rests before it
        // starts wondering whether the control is broken.
        assertTrue(stepsWithin(2.seconds) >= 52, "two seconds bought only ${stepsWithin(2.seconds)} steps")
    }

    @Test
    fun `a press shorter than the rest buys no repeats at all`() {
        assertEquals(0, stepsWithin(StepperHold.BEFORE_FIRST - 1.milliseconds))
        assertEquals(1, stepsWithin(StepperHold.BEFORE_FIRST))
    }

    // How many steps a press of this length would fire, which is the sequence read the way the
    // gesture reads it: wait, step, wait, step.
    private fun stepsWithin(press: Duration): Int {
        var spent = Duration.ZERO
        var steps = 0
        for (wait in StepperHold.waits().take(1_000)) {
            spent += wait
            if (spent > press) break
            steps++
        }
        return steps
    }
}
