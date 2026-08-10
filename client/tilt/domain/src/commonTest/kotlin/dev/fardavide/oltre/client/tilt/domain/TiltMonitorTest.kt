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
        // for why that direction and not the other.
        val monitor = leaned(to = Pose(50.0, lean = 6.0))

        assertTrue(monitor.tilt.x < 0f, "x was ${monitor.tilt.x}")
    }

    @Test
    fun `tipping the top away pushes the sky up`() {
        val monitor = leaned(to = Pose(REST.elevation - 6.0))

        assertTrue(monitor.tilt.y < 0f, "y was ${monitor.tilt.y}")
    }

    @Test
    fun `the sky tips the same way and the same far from every pose a hand can hold`() {
        // **The regression test for the defect the elevation formulation shipped with**, at the
        // level a player would feel it: the same wrist movement has to move the sky the same way and
        // by about the same amount whether the phone started flat on a desk, upright, or tipped
        // past vertical in bed. It used to rectify at exactly upright and invert past it.
        // Face up on a table is excluded because it is the one end of the reading's range: tipping
        // *past* flat is the fold, and `tipping on past face down retraces its travel` is where that
        // is pinned deliberately rather than tripped over here.
        val readings = EVERY_POSE.filter { it != 0.0 }.map { elevation ->
            elevation to leaned(from = Pose(elevation), to = Pose(elevation - 6.0)).tilt.y
        }

        readings.forEach { (elevation, y) ->
            assertTrue(y < 0f, "tipping away at $elevation degrees pushed the sky the wrong way: $y")
        }
        val spread = readings.maxOf { it.second } - readings.minOf { it.second }
        assertTrue(abs(spread) <= 2 * TiltMonitor.STEP, "the same movement varied by $spread across poses")
    }

    @Test
    fun `the sky leans the same far sideways from every pose a hand can hold`() {
        // **The defect the first device session reported — the sideways axis being 'very lazy' — at
        // the level it was felt.** It was not a constant that wanted raising: the sideways gain
        // carried a `sin²(elevation)` factor out of the cross product, so the same wrist gave a
        // quarter of the travel on a phone held at 30 degrees and half of it at 45, while the tip
        // axis kept full gain in every pose. Reading the lean as an angle rather than as the sine of
        // a turn is what removes the factor; `Bearing.inPlane` is where the pose is allowed to act
        // instead, and it only says *whether* to trust the angle.
        //
        // Held at anything a hand rests in, the two axes now answer identically — which is the whole
        // of the fix and the one property most easily lost again.
        val readings = HELD_IN_A_HAND.map { elevation ->
            elevation to leaned(from = Pose(elevation), to = Pose(elevation, lean = 8.0)).tilt.x
        }

        readings.forEach { (elevation, x) ->
            assertTrue(x < 0f, "a lean at $elevation degrees pushed the sky the wrong way: $x")
        }
        val spread = readings.maxOf { it.second } - readings.minOf { it.second }
        assertTrue(abs(spread) <= 2 * TiltMonitor.STEP, "the same lean varied by $spread across poses")
    }

    @Test
    fun `a tip and a lean of the same size move the sky the same distance`() {
        // The other half of "lazy", and the one a player states rather than measures: the two axes
        // have to answer the same wrist by the same amount, or the field feels hinged.
        val tipped = leaned(to = Pose(REST.elevation - 9.0))
        val leant = leaned(to = Pose(REST.elevation, lean = 9.0))

        assertEquals(abs(tipped.tilt.y), abs(leant.tilt.x), 2 * TiltMonitor.STEP)
    }

    @Test
    fun `tipping on past face down retraces its travel rather than continuing`() {
        // **The price of the axis being unmoved by a roll, pinned rather than left to be discovered.**
        // No reading of the tip can be all three of blind to the roll, monotonic through a full
        // end-over-end turn, and a function of where the phone is now — see the note on `Gravity`,
        // where the proof is one line of the pose model. This is the corner that was chosen: the
        // reading runs from face up through upright to face down and then comes back the way it
        // went, so an end-over-end turn returns the sky to level rather than carrying it round.
        //
        // The range given up is the half turn in which the screen is pointing away from the player.
        // The range kept is every pose the screen can be read from, and the sideways axis keeps its
        // whole turn — `a full turn of the phone is a full turn of travel` still holds.
        val short = leaned(to = Pose(170.0), over = 1.seconds, settle = 1.seconds)
        val far = leaned(from = Pose(170.0), to = Pose(180.0), over = 1.seconds, settle = 1.seconds, of = short)
        val past = leaned(from = Pose(180.0), to = Pose(190.0), over = 1.seconds, settle = 1.seconds, of = far)

        assertTrue(far.tilt.y > short.tilt.y, "tipping towards face down did not travel: ${far.tilt.y}")
        assertEquals(short.tilt.y, past.tilt.y, 2 * TiltMonitor.STEP, "tipping past face down kept going")
    }

    @Test
    fun `a roll of any size never pushes the sky up or down`() {
        // **The regression test for the defect 0.4.3's first draft shipped**, and the one property
        // the suite it replaced could not have caught: every sideways test in it rolled by six or
        // eight degrees, where the leak is second order and genuinely invisible. Measured at the
        // angles a full turn actually passes through, the same formulation dragged the sky 4.17
        // units up on a 90-degree roll and 8.33 on a 180-degree one — more vertical than the
        // sideways movement that was asked for, off a purely sideways gesture.
        //
        // The cause was reading the tip as the elevation of the phone's *long edge*, which a roll
        // sweeps round a cone. The screen normal does not move when the phone is rolled, so reading
        // the tip off that is exact rather than nearly right: this asserts zero, not a small number.
        listOf(30.0, 50.0, 90.0, 120.0).forEach { elevation ->
            listOf(8.0, 45.0, 90.0, 180.0).forEach { roll ->
                val monitor = leaned(from = Pose(elevation), to = Pose(elevation, lean = roll), settle = 1.seconds)

                assertEquals(0f, monitor.tilt.y, TiltMonitor.STEP, "a $roll roll at $elevation")
            }
        }
    }

    @Test
    fun `a closed loop of poses leaves the sky exactly where it started`() {
        // **The second regression test for that draft**, and the deeper of the two. The pose weight
        // was applied to each accumulated step rather than to the reading, which makes the total a
        // line integral of a form that is not closed — so retracing a path cancelled, which is all
        // the suite tested, while going round a *loop* did not. Every lap of the walk below left
        // 2.2 units behind, without bound: five laps came to eleven units, about 154dp of permanent
        // sideways offset on the nearest plane, with the phone back in the pose it started in.
        //
        // Every pose here is readable, so nothing about this loop is a case the instrument cannot
        // see — it is four ordinary movements that happen to end where they began.
        val loop = listOf(Pose(50.0, lean = 40.0), Pose(20.0, lean = 40.0), Pose(20.0), REST)

        var monitor = held(TiltMonitor(), REST, from = EPOCH, for_ = 3.seconds)
        repeat(5) { lap ->
            var from = REST
            // A full second of settle per leg, not because the loop needs it but because the reading
            // does: a roll in progress dips the smoothed direction inside the cone it is sweeping,
            // so the tip is momentarily low and only exact once the hand has arrived. Every gesture
            // ends by arriving somewhere; this measures it there, as the rest of the file does.
            loop.forEach { to ->
                monitor = leaned(from = from, to = to, over = 500.milliseconds, settle = 1.seconds, of = monitor)
                from = to
            }
            assertEquals(Tilt.NONE, monitor.tilt, "after lap ${lap + 1}")
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
    fun `a turn far past the old stop keeps travelling`() {
        // **What 0.4.2 could not do and this exists to do.** The travel used to be clamped at one
        // unit and reached it after twelve degrees, so every movement bigger than a small wrist flick
        // arrived at the same place and the sky stopped answering the hand. There is no stop now:
        // sixty degrees is five times twelve and moves the field five times as far.
        val monitor = leaned(to = Pose(REST.elevation, lean = 60.0), settle = 1.seconds)

        assertEquals(-5f, monitor.tilt.x, 0.05f)
    }

    @Test
    fun `a full turn of the phone is a full turn of travel`() {
        // The ask behind the whole change, stated as far as it goes: turn the phone all the way
        // round and the sky goes all the way round with it, because the field wraps and the reading
        // it takes counts turns rather than clamping inside one.
        val monitor = turned(through = 360.0, over = 2.seconds)

        assertEquals(-30f, monitor.tilt.x, 0.1f)
    }

    @Test
    fun `coming back to a pose comes back to the same tilt`() {
        // **The property that makes an unbounded reading safe**, and the one a gyroscope could not
        // have given. Every bearing is measured from the device's own axes, so a total of shortest
        // steps between them is the current angle plus a whole number of turns — put the phone back
        // and the sky is back, however far it went in between and however long it took.
        val out = leaned(to = Pose(120.0, lean = 75.0), over = 1.seconds, settle = 500.milliseconds)
        val back = leaned(from = Pose(120.0, lean = 75.0), to = REST, over = 1.seconds, settle = 1.seconds, of = out)

        assertEquals(Tilt.NONE, back.tilt)
    }

    @Test
    fun `the stated angle is exactly one unit of travel and a twitch is a fraction of it`() {
        // What FULL_TRAVEL_DEGREES buys, and it is now a plain statement rather than a ratio: the
        // reading is the angle, so a movement of exactly this size is exactly one unit of travel
        // once the smoothing has caught up. Under the band-pass it was about four-fifths of one and
        // the shortfall had to be explained.
        //
        // Measured on the tip because that is the axis 0.4.2 got right; the test above pins the
        // sideways one against it.
        val full = leaned(to = Pose(REST.elevation - TiltMonitor.FULL_TRAVEL_DEGREES), settle = 1.seconds)
        val twitch = leaned(to = Pose(REST.elevation - 2.0), settle = 1.seconds)

        assertEquals(-1f, full.tilt.y, 0.02f)
        assertEquals(-2f / TiltMonitor.FULL_TRAVEL_DEGREES.toFloat(), twitch.tilt.y, 0.02f)
    }

    @Test
    fun `a pose that is simply held holds the sky exactly where it was put`() {
        // **The retraction of 0.4.2's one apology.** A centre that followed the pose meant a lean
        // that had already finished went on settling back to level for about ten seconds, so there
        // were ten seconds after the hand stopped in which the sky moved with nobody touching the
        // device — the one thing in this app a player could watch happen with their hands in their
        // lap, and a plain breach of the rule that nothing animates on its own.
        //
        // The centre existed because the travel was clamped: any pose held off centre would
        // otherwise have pinned the sky against a stop for the rest of the session. Once the field
        // wraps there is no stop to be pinned against and no correct centre to chase, so the sky can
        // simply stay where the hand left it. Nothing here moves without one.
        //
        // Measured from a second after the hand stopped rather than from the instant it did, and the
        // gap between those two is the honest remainder: the smoothing is still arriving for a few
        // tenths of a second afterwards, which is a response to a movement that has just happened
        // rather than something starting on its own. Twelve time constants later it is over, and
        // then the reading does not change again however long the game is left open.
        val leant = leaned(to = Pose(REST.elevation, lean = 9.0), settle = 1.seconds)
        assertTrue(leant.tilt.x < -0.7f, "the lean never registered: ${leant.tilt.x}")

        val settled = held(leant, Pose(REST.elevation, lean = 9.0), from = leant.at(), for_ = 25.seconds)

        assertEquals(leant.tilt, settled.tilt)
    }

    @Test
    fun `coming back after a long absence leaves the sky where it was and answers the hand again`() {
        // The backgrounded app. The sensor stops, the player pockets the phone and opens the game an
        // hour later holding it completely differently, so the turn across that gap is not a
        // movement anybody made and must not be counted. What is left of the old behaviour is the
        // re-anchoring; what is gone is the jump, because the travel already made is kept rather than
        // thrown away — a sky that snapped back to level on the first frame after a resume would be
        // the lurch this cut exists to prevent, arriving from the other direction.
        val before = leaned(to = Pose(REST.elevation, lean = 8.0))
        val back = before.sampled(Pose(140.0, lean = 25.0), at = before.at() + 1.hoursLater())
        assertEquals(before.tilt, back.tilt)

        val moved = held(back, Pose(140.0, lean = 31.0), from = back.at(), for_ = 1.seconds)

        assertTrue(moved.tilt.x < back.tilt.x, "the hand was ignored after the gap: ${moved.tilt.x}")
    }

    @Test
    fun `a stall shorter than the cut is filtered rather than restarted`() {
        // MAX_GAP is a hard cut, so it has to sit well clear of ordinary jank: at fifty samples a
        // second a second of silence is fifty dropped in a row. A gap under it is a movement the
        // player made slowly rather than an absence.
        val leant = leaned(to = Pose(REST.elevation, lean = 9.0))
        val stalled = leant.sampled(Pose(REST.elevation, lean = 12.0), at = leant.at() + 1.seconds)

        assertTrue(stalled.tilt.x < leant.tilt.x, "a one-second stall was treated as an absence")
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
        val freeFall = monitor.sampleGravity(x = 0.0, y = 0.0, z = 0.0, at = monitor.at() + 20.milliseconds)

        assertEquals(monitor, freeFall)
    }

    @Test
    fun `the sideways axis stops at the pose gravity cannot read and holds rather than jumping`() {
        // The pose gravity genuinely cannot read: an in-plane lean of a phone lying flat is a spin
        // about the vertical and moves `down` not at all. 0.4.2 handled it by having the gain go to
        // zero *through the whole useful range on the way*, which is the laziness this round removed;
        // 0.4.3's first draft replaced that with a fade across a band of poses, which is what made
        // the total path-dependent — the fade and the drift were the same thing seen twice.
        //
        // What is left is a hard edge low enough that no hand reaches it, full response above it,
        // and a hold rather than a lurch below: a phone put down on a desk and spun leaves the sky
        // exactly where it was, and picks up again from wherever the roll then is.
        val flat = leaned(from = Pose(6.0), to = Pose(6.0, lean = 20.0))
        val shallow = leaned(from = Pose(20.0), to = Pose(20.0, lean = 20.0))
        val handHeld = leaned(from = Pose(50.0), to = Pose(50.0, lean = 20.0))

        assertEquals(0f, flat.tilt.x)
        assertEquals(handHeld.tilt.x, shallow.tilt.x, 2 * TiltMonitor.STEP)
        assertTrue(shallow.tilt.x < 0f, "a lean at 20 degrees moved nothing: ${shallow.tilt.x}")
    }

    @Test
    fun `a phone laid down and spun leaves the sky where it was`() {
        // The other half of the gate, and the behaviour it is chosen for: what happens below the
        // edge is a *hold*, not a discard and not a return. The reference bearing keeps advancing
        // while the steps are dropped, so the spin is silently absorbed and nothing jumps when the
        // phone comes back up.
        val leant = leaned(to = Pose(REST.elevation, lean = 12.0), settle = 1.seconds)
        val down = leaned(from = Pose(REST.elevation, lean = 12.0), to = Pose(4.0, lean = 12.0), of = leant)
        val spun = leaned(from = Pose(4.0, lean = 12.0), to = Pose(4.0, lean = 192.0), over = 2.seconds, of = down)
        val up = leaned(from = Pose(4.0, lean = 192.0), to = Pose(REST.elevation, lean = 192.0), settle = 1.seconds, of = spun)

        assertEquals(leant.tilt.x, up.tilt.x, 2 * TiltMonitor.STEP)
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
    fun `the constants are a coherent reading rather than a set of separate guesses`() {
        // Not taste — arithmetic, in the shape `ShakeMonitorTest` uses for its three.
        //
        // The smoothing has to stay under the threshold at which a response stops reading as
        // immediate, because it is now the *only* filter: with the centre gone there is nothing
        // downstream of it to hide a lag behind.
        assertTrue(TiltMonitor.SMOOTHING <= 200.milliseconds, "was ${TiltMonitor.SMOOTHING}")
        // The absence cut has to be clear of any stall a running app can produce.
        assertTrue(TiltMonitor.MAX_GAP >= 2.seconds, "was ${TiltMonitor.MAX_GAP}")
        // Inside the range of a wrist that is not trying. This is no longer a stop the effect
        // reaches — it is the scale, and a wrist that is not trying is what the scale is set by.
        assertTrue(TiltMonitor.FULL_TRAVEL_DEGREES in 5.0..25.0, "was ${TiltMonitor.FULL_TRAVEL_DEGREES}")
        // The gate has to sit under any pose a hand rests in, or the fix above is undone by the
        // thing meant to keep it honest. 0.26 is about fifteen degrees off flat.
        assertTrue(TiltMonitor.FAINT <= 0.3, "was ${TiltMonitor.FAINT}")
    }
}

private val EPOCH = Instant.fromEpochSeconds(0)

// Somebody looking at a phone in their hand. Nothing depends on the exact figure — every reading is
// a difference from wherever the walk started — but a pose that is neither flat nor upright is the
// honest one to measure movements from.
private val REST = Pose(elevation = 50.0)

// The poses of `EVERY_POSE` a hand actually rests in, which is where the sideways axis has to answer
// identically. The gate sits at about fifteen degrees off flat, well below any of these — see
// `the sideways axis stops at the pose gravity cannot read and holds rather than jumping`.
private val HELD_IN_A_HAND = EVERY_POSE.filter { it in 20.0..160.0 }

private fun Int.hoursLater(): Duration = (this * 60 * 60).seconds

private fun TiltMonitor.at(): Instant = turned?.at ?: EPOCH

private fun TiltMonitor.sampled(pose: Pose, at: Instant, android: Boolean = false): TiltMonitor {
    return if (android) {
        val (x, y, z) = pose.androidReading
        sampleReactionToGravity(x = x, y = y, z = z, at = at)
    } else {
        val (x, y, z) = pose.gravity
        sampleGravity(x = x, y = y, z = z, at = at)
    }
}

// A movement from `from` to `to`, sampled the way a device would sample it: the pose is established
// first, the phone is then moved over `over`, and it is **held there for `settle`**.
//
// The hold is not padding. The smoothing is still catching up while the hand is still moving, so a
// reading taken at the instant a movement ends is a reading of a filter mid-flight. Every gesture a
// person makes ends by arriving somewhere, and these tests measure it there.
private fun leaned(
    to: Pose,
    from: Pose = REST,
    over: Duration = 300.milliseconds,
    settle: Duration = 400.milliseconds,
    every: Duration = 20.milliseconds,
    of: TiltMonitor? = null,
): TiltMonitor {
    var monitor = of ?: held(TiltMonitor(), from, from = EPOCH, for_ = 3.seconds)
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

// A phone rolled in its own plane through `through` degrees and held there — the gesture that has
// nowhere to go under a clamp and is the point of not having one.
private fun turned(through: Double, over: Duration): TiltMonitor =
    leaned(to = Pose(REST.elevation, lean = through), over = over, settle = 1.seconds)

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
