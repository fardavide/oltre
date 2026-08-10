package dev.fardavide.oltre.client.tilt.domain

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// See the note at the top of `AttitudeTest` about commas in a backticked name.
class TiltMonitorTest {

    @Test
    fun `a monitor that has seen nothing is level`() {
        assertEquals(Tilt.NONE, TiltMonitor().tilt)
    }

    @Test
    fun `the first sample is level whatever the pose`() {
        // The start-up behaviour that matters: the app has no idea how the phone is being held when
        // it opens, so the first frame must not lean. A monitor that guessed at a resting pose
        // would jerk the sky on the frame the player is first looking at.
        val overhead = TiltMonitor().sample(Attitude(pitch = -73.0, roll = 21.0), at = EPOCH)

        assertEquals(Tilt.NONE, overhead.tilt)
    }

    @Test
    fun `holding perfectly still keeps the sky perfectly still`() {
        // The performance property as much as the visual one. A `Tilt` that changes is a redraw of
        // a hundred and one stars; a phone on a table has to stop producing them entirely.
        var monitor = TiltMonitor()
        repeat(300) { tick ->
            monitor = monitor.sample(Attitude(pitch = -40.0, roll = 0.0), at = EPOCH + (tick * 20).milliseconds)
        }

        assertEquals(Tilt.NONE, monitor.tilt)
    }

    @Test
    fun `a tilt to the right pushes the sky left`() {
        // The sign convention in one test. Dropping the right edge is positive roll and the field
        // moves against it — see the note on `TiltMonitor.tilt` for why that direction and not the
        // other. If a device session flips the feel this is the test that changes with it.
        val monitor = tilted(to = Attitude(pitch = REST.pitch, roll = 6.0), over = 300.milliseconds)

        assertTrue(monitor.tilt.x < 0f, "x was ${monitor.tilt.x}")
    }

    @Test
    fun `tipping the top away pushes the sky up`() {
        val monitor = tilted(to = Attitude(pitch = REST.pitch + 6.0, roll = 0.0), over = 300.milliseconds)

        assertTrue(monitor.tilt.y < 0f, "y was ${monitor.tilt.y}")
    }

    @Test
    fun `a tilt past full deflection is held at the stop`() {
        val monitor = tilted(to = Attitude(pitch = REST.pitch, roll = 80.0), over = 400.milliseconds)

        assertEquals(-1f, monitor.tilt.x)
    }

    @Test
    fun `the stated angle is most of the travel and a small movement is not`() {
        // What FULL_DEFLECTION_DEGREES actually buys, measured rather than assumed. It is not the
        // angle at which the stop is reached: the centre is following the whole time, so a movement
        // held for half a second has already given about a fifth of itself back and lands near
        // four-fifths of full travel. That is the intended behaviour rather than a shortfall — a
        // gesture that pegs the axis has nowhere left to go — and the constant's real job is the
        // ratio pinned here, that an ordinary wrist movement is most of the effect and a twitch is
        // almost none of it.
        val full = tilted(
            to = Attitude(pitch = REST.pitch, roll = TiltMonitor.FULL_DEFLECTION_DEGREES),
            over = 400.milliseconds,
        )
        val slight = tilted(to = Attitude(pitch = REST.pitch, roll = 3.0), over = 400.milliseconds)

        assertTrue(abs(full.tilt.x) > 0.7f, "a full movement gave only ${full.tilt.x}")
        assertTrue(abs(slight.tilt.x) < 0.3f, "a slight movement gave ${slight.tilt.x}")
    }

    @Test
    fun `a pose that is simply held fades back to level`() {
        // The whole argument for a centre that follows. Anything else leaves a player who reads in
        // bed with the sky pinned against the stop for the entire session.
        var monitor = tilted(to = Attitude(pitch = -40.0, roll = 9.0), over = 300.milliseconds)
        assertTrue(monitor.tilt.x < -0.5f, "the tilt never registered: ${monitor.tilt.x}")

        monitor = held(monitor, Attitude(pitch = -40.0, roll = 9.0), for_ = 20.seconds)

        assertEquals(Tilt.NONE, monitor.tilt)
    }

    @Test
    fun `a movement is passed through even though a held pose is not`() {
        // The two time constants have to be far enough apart that the filter can tell a gesture
        // from a posture. If they were close the centre would chase every movement and nothing
        // would ever deflect.
        val moved = tilted(to = Attitude(pitch = -40.0, roll = 8.0), over = 250.milliseconds)

        assertTrue(abs(moved.tilt.x) > 0.5f, "a real movement barely moved the sky: ${moved.tilt.x}")
    }

    @Test
    fun `coming back after a long absence re-centres instead of lurching`() {
        // The backgrounded app. The sensor stops, the player puts the phone in a pocket and opens
        // the game again an hour later holding it completely differently. Extrapolating across that
        // gap would deflect straight to the stop on the first frame back.
        val before = tilted(to = Attitude(pitch = -40.0, roll = 0.0), over = 300.milliseconds)
        val after = before.sample(Attitude(pitch = 12.0, roll = 30.0), at = EPOCH + 1.hoursLater())

        assertEquals(Tilt.NONE, after.tilt)
    }

    @Test
    fun `a sample that arrives out of order cannot make the filter overshoot`() {
        // iOS delivers motion on a background queue and two samples can be handed over the wrong
        // way round. A negative gap would give an easing factor above one — an average that
        // overshoots its target and rings — so the gap is clamped at zero and the stored instant
        // never goes backwards.
        val monitor = TiltMonitor()
            .sample(Attitude(pitch = -40.0, roll = 0.0), at = EPOCH + 1.seconds)
            .sample(Attitude(pitch = -40.0, roll = 40.0), at = EPOCH)

        assertEquals(Tilt.NONE, monitor.tilt)
        assertEquals(EPOCH + 1.seconds, monitor.filtered?.at)
    }

    @Test
    fun `the same monitor sampled the same way twice gives the same answer`() {
        // A value rather than a device handle — the property that makes every test above arithmetic.
        val monitor = tilted(to = Attitude(pitch = -40.0, roll = 5.0), over = 200.milliseconds)
        val at = EPOCH + 10.seconds

        assertEquals(
            monitor.sample(Attitude(pitch = -38.0, roll = 7.0), at = at),
            monitor.sample(Attitude(pitch = -38.0, roll = 7.0), at = at),
        )
    }

    @Test
    fun `every reported value sits on the grid`() {
        // What stops a still hand redrawing the sky sixty times a second forever.
        val monitor = tilted(to = Attitude(pitch = -37.3, roll = 4.7), over = 260.milliseconds)
        val steps = listOf(monitor.tilt.x, monitor.tilt.y).map { it / TiltMonitor.STEP }

        steps.forEach { step ->
            assertTrue(abs(step - kotlin.math.round(step)) < 1e-3, "$step is not a whole step")
        }
    }

    @Test
    fun `the sampling rate does not change the feel`() {
        // The easing is derived from the real gap rather than fixed per sample, so a phone
        // reporting at 30 Hz and one reporting at 60 Hz have to arrive at the same place after the
        // same movement. Without that the sky leans twice as fast on whichever device polls faster.
        val slow = tilted(to = Attitude(pitch = -40.0, roll = 7.0), over = 300.milliseconds, every = 33.milliseconds)
        val fast = tilted(to = Attitude(pitch = -40.0, roll = 7.0), over = 300.milliseconds, every = 16.milliseconds)

        assertTrue(abs(slow.tilt.x - fast.tilt.x) <= 2 * TiltMonitor.STEP, "${slow.tilt.x} vs ${fast.tilt.x}")
    }

    @Test
    fun `the two time constants are a coherent filter rather than two separate guesses`() {
        // Not taste — arithmetic, in the shape `ShakeMonitorTest` uses for its three. If the
        // centre followed as fast as the reading did, the gap between them would be zero at every
        // moment and the sky would never move at all. An order of magnitude is what makes a gesture
        // and a posture distinguishable.
        assertTrue(
            TiltMonitor.RECENTRING >= TiltMonitor.SMOOTHING * 10,
            "${TiltMonitor.RECENTRING} is not far enough above ${TiltMonitor.SMOOTHING}",
        )
        // A gap long enough to be an absence has to be longer than any sampling interval a platform
        // would choose, or an ordinary frame would read as the app having been closed.
        assertTrue(TiltMonitor.MAX_GAP >= 500.milliseconds, "was ${TiltMonitor.MAX_GAP}")
        // Inside the range of a wrist that is not trying. Above about 25 degrees the effect only
        // exists for somebody who goes looking for it.
        assertTrue(TiltMonitor.FULL_DEFLECTION_DEGREES in 5.0..25.0, "was ${TiltMonitor.FULL_DEFLECTION_DEGREES}")
    }
}

private val EPOCH = Instant.fromEpochSeconds(0)

// Somebody looking at a phone in their hand. Nothing depends on the exact figure — the centre is
// worked out rather than assumed — but a pose that is neither flat nor upright is the honest one to
// measure movements from.
private val REST = Attitude(pitch = -40.0, roll = 0.0)

private fun Int.hoursLater(): Duration = (this * 60 * 60).seconds

// A movement from REST to `to`, sampled the way a device would sample it: the centre is established
// first, the phone is then moved over `over`, and it is **held there for `settle`**.
//
// The hold is not padding. A band-pass passes a movement in progress only partly — the fast average
// is still catching up while the hand is still moving — so a ramp measured at the instant it stops
// reads about three-quarters of the deflection the same movement reaches a fifth of a second later.
// Every gesture a person makes ends by arriving somewhere, and these tests measure it there.
private fun tilted(
    to: Attitude,
    over: Duration,
    settle: Duration = 300.milliseconds,
    every: Duration = 20.milliseconds,
): TiltMonitor {
    var monitor = held(TiltMonitor().sample(REST, at = EPOCH), REST, for_ = 2.seconds)
    var at = monitor.filtered?.at ?: EPOCH
    val steps = (over / every).toInt().coerceAtLeast(1)
    repeat(steps) { step ->
        at += every
        val progress = (step + 1).toDouble() / steps
        monitor = monitor.sample(
            Attitude(
                pitch = REST.pitch + (to.pitch - REST.pitch) * progress,
                roll = REST.roll + (to.roll - REST.roll) * progress,
            ),
            at = at,
        )
    }
    return held(monitor, to, for_ = settle)
}

private fun held(monitor: TiltMonitor, attitude: Attitude, for_: Duration): TiltMonitor {
    var result = monitor
    var at = monitor.filtered?.at ?: EPOCH
    val end = at + for_
    while (at < end) {
        at += 20.milliseconds
        result = result.sample(attitude, at = at)
    }
    return result
}
