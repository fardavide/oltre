package dev.fardavide.oltre.client.tilt.domain

import kotlin.math.abs
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// See the note at the top of `GravityTest` about commas in a backticked name.
class TiltMonitorTest {

    @Test
    fun `a monitor that has seen nothing is level`() {
        assertEquals(Tilt.NONE, TiltMonitor().tilt)
    }

    @Test
    fun `the first sample is level whatever the pose`() {
        // The start-up behaviour that matters: the app has no idea how the phone is being held when
        // it opens, so the first frame must not lean. A monitor that guessed at a resting pose would
        // jerk the sky on the frame the player is first looking at.
        EVERY_POSE.forEach { elevation ->
            assertEquals(Tilt.NONE, TiltMonitor().sampled(Pose(elevation), at = EPOCH).tilt, "at $elevation")
        }
    }

    @Test
    fun `holding perfectly still keeps the sky perfectly still`() {
        // The performance property as much as the visual one. A `Tilt` that changes is a redraw of a
        // hundred and one stars; a phone on a table has to stop producing them entirely.
        val monitor = held(TiltMonitor(), Pose(40.0), from = EPOCH, for_ = 6.seconds)

        assertEquals(Tilt.NONE, monitor.tilt)
    }

    @Test
    fun `a lean to the right pushes the sky left`() {
        // The sign convention in one test. Dropping the right edge is a positive turn about the
        // axis out of the glass and the field moves against it — see the note on `TiltMonitor.tilt`
        // for why that direction and not the other. If a device session flips the feel this is the
        // test that changes with it.
        val monitor = leaned(to = Pose(50.0, lean = 6.0))

        assertTrue(monitor.tilt.x < 0f, "x was ${monitor.tilt.x}")
    }

    @Test
    fun `tipping the top away pushes the sky up`() {
        val monitor = leaned(to = Pose(REST.elevation - 6.0))

        assertTrue(monitor.tilt.y < 0f, "y was ${monitor.tilt.y}")
    }

    @Test
    fun `the sky leans the same way from every pose a hand can hold`() {
        // **The regression test for the defect the elevation formulation shipped with**, at the
        // level a player would feel it: the same wrist movement has to move the sky the same way and
        // by about the same amount whether the phone started flat on a desk, upright, or tipped
        // past vertical in bed. It used to rectify at exactly upright and invert past it.
        val readings = EVERY_POSE.map { elevation ->
            elevation to leaned(from = Pose(elevation), to = Pose(elevation - 6.0)).tilt.y
        }

        readings.forEach { (elevation, y) ->
            assertTrue(y < 0f, "tipping away at $elevation degrees pushed the sky the wrong way: $y")
        }
        val spread = readings.maxOf { it.second } - readings.minOf { it.second }
        assertTrue(abs(spread) <= 2 * TiltMonitor.STEP, "the same movement varied by $spread across poses")
    }

    @Test
    fun `a sideways lean does not visibly push the sky up or down`() {
        // The other half of that defect, at the level a player would feel it: an upright phone
        // leaned sideways used to move the field diagonally, because the two axes were not
        // independent. What is left is a step or two of vertical out of a hundred — a fraction of a
        // pixel on the nearest plane — where it used to be the whole movement again.
        EVERY_POSE.filter { it != 0.0 && it != 180.0 }.forEach { elevation ->
            val monitor = leaned(from = Pose(elevation), to = Pose(elevation, lean = 8.0))

            assertTrue(
                abs(monitor.tilt.y) <= 2 * TiltMonitor.STEP,
                "a lean at $elevation moved the sky ${monitor.tilt.y} vertically",
            )
            assertTrue(monitor.tilt.x < 0f, "a lean at $elevation did not move it sideways")
        }
    }

    @Test
    fun `both phones report the same movement the same way`() {
        // Android's numbers are the negation of iOS's and ten times bigger. Feeding one and then
        // the other through the whole filter has to land on the same tilt, or the sky leans one way
        // on an iPhone and the other way on a Pixel — the class of defect nobody finds without
        // owning both. This is the end-to-end form of the property `GravityTest` pins on its own.
        val walk = listOf(Pose(55.0), Pose(52.0, lean = 3.0), Pose(49.0, lean = 6.0))

        val ios = walk.fold(held(TiltMonitor(), walk.first(), from = EPOCH, for_ = 3.seconds)) { monitor, pose ->
            held(monitor, pose, from = monitor.at(), for_ = 120.milliseconds)
        }
        val android = walk.fold(
            held(TiltMonitor(), walk.first(), from = EPOCH, for_ = 3.seconds, android = true),
        ) { monitor, pose ->
            held(monitor, pose, from = monitor.at(), for_ = 120.milliseconds, android = true)
        }

        assertEquals(ios.tilt, android.tilt)
    }

    @Test
    fun `a lean past full deflection is held at the stop`() {
        val monitor = leaned(to = Pose(REST.elevation, lean = 60.0))

        assertEquals(-1f, monitor.tilt.x)
    }

    @Test
    fun `the stated angle is most of the travel and a twitch is almost none of it`() {
        // What FULL_DEFLECTION_DEGREES actually buys, measured rather than assumed. It is not the
        // angle at which the stop is reached: a band-pass passes about seven-eighths of a step at
        // its peak and the centre is following the whole time, so a movement of exactly this size
        // lands near four-fifths of full travel. That is the intended behaviour rather than a
        // shortfall — a gesture that pegs the axis has nowhere left to go — and the constant's real
        // job is the ratio pinned here.
        // Measured on the tip rather than on the lean, because the tip is the axis with the same
        // gain in every pose. The sideways axis carries a `sin²(elevation)` term — a lean of a phone
        // lying flat is a spin about the vertical and moves nothing — so a figure taken from it
        // would be a statement about the pose it was measured in as much as about this constant.
        val full = leaned(to = Pose(REST.elevation - TiltMonitor.FULL_DEFLECTION_DEGREES))
        val twitch = leaned(to = Pose(REST.elevation - 2.0))

        assertTrue(abs(full.tilt.y) > 0.6f, "a full movement gave only ${full.tilt.y}")
        assertTrue(abs(twitch.tilt.y) < 0.25f, "a twitch gave ${twitch.tilt.y}")
    }

    @Test
    fun `a pose that is simply held fades back to level`() {
        // The whole argument for a centre that follows. Anything else leaves a player who reads in
        // bed with the sky pinned against the stop for the entire session.
        val leaned = leaned(to = Pose(REST.elevation, lean = 9.0))
        assertTrue(leaned.tilt.x < -0.3f, "the lean never registered: ${leaned.tilt.x}")

        val settled = held(leaned, Pose(REST.elevation, lean = 9.0), from = leaned.at(), for_ = 25.seconds)

        assertEquals(Tilt.NONE, settled.tilt)
    }

    @Test
    fun `coming back after a long absence re-centres instead of lurching`() {
        // The backgrounded app. The sensor stops, the player pockets the phone and opens the game an
        // hour later holding it completely differently. Feeding that gap to the averages does the
        // opposite of re-centring: the fast one arrives instantly and the slow one crawls a fifth of
        // the way, so the sky slams against the stop on the first frame back.
        val before = leaned(to = Pose(REST.elevation, lean = 8.0))
        val after = before.sampled(Pose(140.0, lean = 25.0), at = before.at() + 1.hoursLater())

        assertEquals(Tilt.NONE, after.tilt)
    }

    @Test
    fun `a stall shorter than the cut is filtered rather than restarted`() {
        // MAX_GAP is a hard cut, so it has to sit well clear of ordinary jank: at fifty samples a
        // second a second of silence is fifty dropped in a row. A gap under it keeps the centre the
        // player already had rather than throwing it away.
        val leaned = leaned(to = Pose(REST.elevation, lean = 9.0))
        val stalled = leaned.sampled(Pose(REST.elevation, lean = 9.0), at = leaned.at() + 1.seconds)

        assertTrue(stalled.tilt.x < 0f, "a one-second stall reset the centre: ${stalled.tilt.x}")
    }

    @Test
    fun `a sample that arrives out of order cannot make the filter overshoot`() {
        // iOS delivers motion on a queue and two samples can be handed over the wrong way round. A
        // negative gap would give an easing factor above one — an average that overshoots its target
        // and rings — so the gap is floored at zero and the stored instant never goes backwards.
        val monitor = TiltMonitor()
            .sampled(Pose(50.0), at = EPOCH + 1.seconds)
            .sampled(Pose(50.0, lean = 40.0), at = EPOCH)

        assertEquals(Tilt.NONE, monitor.tilt)
        assertEquals(EPOCH + 1.seconds, monitor.at())
    }

    @Test
    fun `a reading with no direction in it leaves the monitor alone`() {
        val monitor = leaned(to = Pose(REST.elevation, lean = 6.0))
        val freeFall = monitor.sample(x = 0.0, y = 0.0, z = 0.0, at = monitor.at() + 20.milliseconds)

        assertEquals(monitor, freeFall)
    }

    @Test
    fun `the same monitor sampled the same way twice gives the same answer`() {
        // A value rather than a device handle — the property that makes every test above arithmetic.
        val monitor = leaned(to = Pose(REST.elevation, lean = 5.0))
        val at = monitor.at() + 40.milliseconds

        assertEquals(
            monitor.sampled(Pose(48.0, lean = 7.0), at = at),
            monitor.sampled(Pose(48.0, lean = 7.0), at = at),
        )
    }

    @Test
    fun `every reported value sits on the grid`() {
        // What stops a still hand redrawing the sky sixty times a second forever.
        val monitor = leaned(to = Pose(43.0, lean = 4.7))

        listOf(monitor.tilt.x, monitor.tilt.y).forEach { value ->
            val steps = value / TiltMonitor.STEP
            assertTrue(abs(steps - round(steps)) < 1e-3, "$value is not a whole number of steps")
        }
    }

    @Test
    fun `the sampling rate does not change the feel`() {
        // The easing is derived from the real gap rather than fixed per sample, so a phone reporting
        // at 30 Hz and one at 60 Hz have to arrive at the same place after the same movement.
        // Without that the sky leans twice as fast on whichever device polls faster.
        val slow = leaned(to = Pose(REST.elevation, lean = 7.0), every = 33.milliseconds)
        val fast = leaned(to = Pose(REST.elevation, lean = 7.0), every = 16.milliseconds)

        assertTrue(abs(slow.tilt.x - fast.tilt.x) <= 2 * TiltMonitor.STEP, "${slow.tilt.x} vs ${fast.tilt.x}")
    }

    @Test
    fun `the two time constants are a coherent filter rather than two separate guesses`() {
        // Not taste — arithmetic, in the shape `ShakeMonitorTest` uses for its three. If the centre
        // followed as fast as the reading did, the gap between them would be zero at every moment
        // and the sky would never move at all. An order of magnitude is what makes a gesture and a
        // posture distinguishable.
        assertTrue(
            TiltMonitor.RECENTRING >= TiltMonitor.SMOOTHING * 10,
            "${TiltMonitor.RECENTRING} is not far enough above ${TiltMonitor.SMOOTHING}",
        )
        // The absence cut has to be clear of any stall a running app can produce.
        assertTrue(TiltMonitor.MAX_GAP >= 2.seconds, "was ${TiltMonitor.MAX_GAP}")
        // Inside the range of a wrist that is not trying. Above about 25 degrees the effect only
        // exists for somebody who goes looking for it.
        assertTrue(TiltMonitor.FULL_DEFLECTION_DEGREES in 5.0..25.0, "was ${TiltMonitor.FULL_DEFLECTION_DEGREES}")
    }
}

private val EPOCH = Instant.fromEpochSeconds(0)

// Somebody looking at a phone in their hand. Nothing depends on the exact figure — the centre is
// worked out rather than assumed — but a pose that is neither flat nor upright is the honest one to
// measure movements from.
private val REST = Pose(elevation = 50.0)

private fun Int.hoursLater(): Duration = (this * 60 * 60).seconds

private fun TiltMonitor.at(): Instant = filtered?.at ?: EPOCH

private fun TiltMonitor.sampled(pose: Pose, at: Instant, android: Boolean = false): TiltMonitor {
    val (x, y, z) = if (android) pose.androidReading else pose.gravity
    return sample(x = x, y = y, z = z, at = at)
}

// A movement from `from` to `to`, sampled the way a device would sample it: the centre is
// established first, the phone is then moved over `over`, and it is **held there for `settle`**.
//
// The hold is not padding. A band-pass passes a movement in progress only partly — the fast average
// is still catching up while the hand is still moving — and its response peaks a beat *after* the
// hand stops. Every gesture a person makes ends by arriving somewhere, and these tests measure it
// there rather than at the instant the movement ends.
private fun leaned(
    to: Pose,
    from: Pose = REST,
    over: Duration = 300.milliseconds,
    settle: Duration = 400.milliseconds,
    every: Duration = 20.milliseconds,
): TiltMonitor {
    var monitor = held(TiltMonitor(), from, from = EPOCH, for_ = 3.seconds)
    var at = monitor.at()
    val steps = (over / every).toInt().coerceAtLeast(1)
    repeat(steps) { step ->
        at += every
        val progress = (step + 1).toDouble() / steps
        monitor = monitor.sampled(
            Pose(
                elevation = from.elevation + (to.elevation - from.elevation) * progress,
                lean = from.lean + (to.lean - from.lean) * progress,
            ),
            at = at,
        )
    }
    return held(monitor, to, from = at, for_ = settle)
}

private fun held(
    monitor: TiltMonitor,
    pose: Pose,
    from: Instant,
    for_: Duration,
    android: Boolean = false,
): TiltMonitor {
    var result = monitor
    var at = from
    val end = from + for_
    while (at < end) {
        at += 20.milliseconds
        result = result.sampled(pose, at = at, android = android)
    }
    return result
}
