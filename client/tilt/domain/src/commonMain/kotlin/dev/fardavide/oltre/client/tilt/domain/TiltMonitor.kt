package dev.fardavide.oltre.client.tilt.domain

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.round
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// Turning a stream of gravity readings into how far the sky has travelled. It lives here, with no
// platform in it, on `ShakeMonitor`'s argument: the platforms supply samples and this says what they
// add up to, so the sky leans the same way on both phones and the whole judgement is testable
// without a device.
//
// **It keeps a running total of turn, and that is the whole of it.** Each sample smooths the gravity
// direction, reads the two angles off it, and adds however far each one moved to a total that
// started at zero when the app opened. The sky is that total, divided by FULL_TRAVEL_DEGREES.
//
// Three things follow, and each one is a thing the band-pass this replaced could not do:
//
//   *There is no stop.* 0.4.2 clamped at twelve degrees, so every movement larger than a small wrist
//   flick arrived at the same place — which is exactly what the first device session reported as the
//   effect having too narrow an area. A total counts turns, and the field it feeds wraps, so a phone
//   rolled all the way round carries the sky a whole turn's worth. **Sideways only**: the tip runs
//   from face up to face down and turns back, which is not a shortfall but the corner this module is
//   forced to pick — the proof is on `Gravity`, and the reason is on `Gravity.tip`.
//
//   *There is no centre to chase, so nothing moves on its own.* The old slow average existed only
//   because of the stop: with a clamp, a pose simply held would have pinned the sky against it for
//   the rest of the session, so the zero point had to follow the pose — and that is what left the
//   sky settling back to level for ten seconds after the hand had stopped. Without a stop the
//   question does not arise. **The sky now moves if and only if a hand moves it**, which is the
//   claim 0.4.0 made, 0.4.2 had to retract, and this restores.
//
//   *It does not drift.* Every angle is measured from the device's own axes, so the total is the
//   current angle plus a whole number of turns minus wherever it started — see `turnedFrom`. An
//   integrated gyroscope, the other way to get an unbounded reading, has no such guarantee.
//
//   **Stated exactly, because the loose version of it was a defect.** Return the phone to a pose and
//   the sky returns, *for any path that stays where the reading can be taken and does not roll a net
//   whole number of turns on the way*. Both qualifiers are real and both are pinned: a net whole turn
//   is travel that genuinely happened (`a full turn of the phone is a full turn of travel`), and a
//   path that dips below `FAINT` leaves the arc rolled down there uncounted, because down there the
//   roll is a spin about the vertical. 0.4.3's first draft claimed the unqualified version and paid
//   for it — a weight that varied with the pose made an ordinary four-movement loop leave 2.2 units
//   behind and repeat it every lap. See `Bearing.turnTo`.
//
// What survives from the band-pass is the fast average, and only that: a gravity sensor at rest
// wanders a fraction of a degree, and a field that shimmers in a still hand is worse than no field
// at all.
data class TiltMonitor(val turned: Turned? = null) {

    // Null until the first sample, and that is the whole of the start-up behaviour: an app that has
    // just opened has no idea how the phone is being held, and any guess at it is a jerk on the
    // first frame the player sees.
    //
    // **Both signs are one minus sign from being the other way round**, and this is the pair of
    // lines to change. The sky moves *against* the lean, which is what makes it read as something
    // seen past the cards rather than something sitting on them: drop the right edge and your eye
    // moves right of the screen, so what was hidden behind the right margin comes into view and the
    // field slides left. Tip the top away and your eye moves above it, so the field slides up.
    //
    // Yaw — turning the phone left and right about the vertical, which is the movement most people
    // reach for first — is absent, and it is absent because gravity cannot see it: spinning a phone
    // flat on a table changes nothing about where down is. Mapping an in-plane lean to a horizontal
    // slide is therefore an artistic choice rather than a literal parallax, and it is the one this
    // field is built on. A version that answered yaw would need the fused rotation vector and a
    // magnetometer with it.
    val tilt: Tilt
        get() {
            val reading = turned ?: return Tilt.NONE
            return Tilt(x = travel(-reading.lean), y = travel(reading.tip))
        }

    // What iOS's `CMDeviceMotion.gravity` hands over: the vector pointing at the ground.
    //
    // A value, exactly as `ShakeMonitor` is: the same monitor sampled with the same arguments always
    // gives the same answer, so a test is arithmetic and a dropped frame cannot corrupt anything.
    //
    // Three raw components rather than a `Gravity`, so the platform edges hand over exactly what
    // their sensor gave them and every judgement about it — including whether it is usable at all —
    // is made once, here.
    fun sampleGravity(x: Double, y: Double, z: Double, at: Instant): TiltMonitor {
        // A reading with no direction in it is dropped rather than filtered. See `Gravity.normalised`.
        val now = Gravity.normalised(x = x, y = y, z = z) ?: return this
        val previous = turned
            // The first sample is where the count starts, so the first frame is level whatever the
            // pose the app happened to be opened in.
            ?: return TiltMonitor(Turned(smoothed = now, at = at, tip = 0.0, lean = 0.0))
        val elapsed = at - previous.at
        // **A long gap is an absence, not a long interval.** The sensor stops when the app is
        // backgrounded — both platforms see to that themselves — so the next sample can arrive an
        // hour later against a phone now held completely differently. The turn across that gap is
        // not a movement anybody made and counting it would throw the sky a long way for nothing.
        //
        // **The totals are kept rather than reset, and that is the difference from 0.4.2.** Resetting
        // them was harmless when the travel was clamped and usually near zero anyway; against an
        // unbounded one it would snap the field back on the first frame after a resume, which is the
        // lurch this cut exists to prevent, arriving from the other side. Adopting the new pose
        // silently leaves the sky exactly where the player left it and starts answering the hand
        // again from wherever the phone now is.
        //
        // It is a hard cut, so MAX_GAP has to be long enough that ordinary jank cannot reach it: at
        // fifty samples a second, two seconds is a hundred dropped in a row, which is a stall rather
        // than a slow frame.
        if (elapsed > MAX_GAP) return TiltMonitor(previous.copy(smoothed = now, at = at))
        // The other end, and it has happened too: iOS delivers motion on a queue and two samples can
        // be handed over out of order. A negative gap makes an easing factor greater than one — an
        // average that overshoots its target and rings — so it is floored rather than restarted,
        // because one sample arriving early is a hiccup and not an absence.
        val gap = elapsed.coerceAtLeast(Duration.ZERO)
        // Falling back to the previous direction covers the one case an interpolation can fail: two
        // exactly opposite readings, which is a phone that flipped over between two samples forty
        // milliseconds apart. Holding still through it beats a `NaN`, which would stay in the average
        // for the life of the process — and holding still is all it takes, because a step from a
        // direction to itself is a turn of zero and the totals below simply do not move.
        val eased = easing(over = SMOOTHING, elapsed = gap)
        val smoothed = previous.smoothed.towards(now, eased) ?: previous.smoothed
        return TiltMonitor(
            Turned(
                smoothed = smoothed,
                // `previous.at + gap` rather than `at`, so the stored instant can never go backwards
                // and always matches the gap actually applied.
                at = previous.at + gap,
                // A plain subtraction rather than a shortest arc, because `Gravity.tip` runs `0..π`
                // and cannot wrap — and no trust term, because it cannot be unreadable either. The
                // running sum is kept rather than an anchor only to hold the two axes in the same
                // shape; it telescopes to exactly `now − where the count started` either way.
                tip = previous.tip + (smoothed.tip - previous.smoothed.tip),
                lean = previous.lean + previous.smoothed.lean.turnTo(smoothed.lean),
            ),
        )
    }

    // What Android's `TYPE_GRAVITY` hands over: the *reaction* to gravity, pointing at the sky. Its
    // own documentation says a phone lying flat on a table reads `z = +9.81` where iOS calls the
    // same phone `z = -1.0`.
    //
    // **A named entry point rather than a correction inside one of the two platform files, and the
    // difference is a defect this module has already had twice.** Until 0.4.3 nothing here needed
    // the sign: the cross product was blind to it, and so was the pair of `atan2`s that replaced the
    // cross product, because negating all three components turned both bearings by exactly half a
    // circle and every difference cancelled it. Reading the tip off `√(x²+y²)` and `z` breaks that
    // for the first time — the magnitude does not care about the sign and `z` does, so the two
    // platforms come out *reflected* rather than offset, and differences negate instead of
    // cancelling. A phone would have leaned the right way on an iPhone and upside down on a Pixel.
    //
    // Since a convention is now needed, it is stated here, in the module both platforms share and
    // the one place a test can drive both — rather than as a minus sign in a sensor callback that
    // only one of the two files would ever grow. `both phones report the same movement the same way`
    // walks it.
    fun sampleReactionToGravity(x: Double, y: Double, z: Double, at: Instant): TiltMonitor =
        sampleGravity(x = -x, y = -y, z = -z, at = at)

    // The smoothed direction, the instant it was taken at, and how far each axis has turned since
    // the count started. One nullable holder rather than four nullable fields, because they are only
    // ever all present or all absent and a shape that cannot express the half-filled case cannot be
    // left in it.
    data class Turned(val smoothed: Gravity, val at: Instant, val tip: Double, val lean: Double)

    companion object {

        // **Every number here is arithmetic rather than a measurement, and a session with a phone in
        // hand should move them.** That is the same caveat `ShakeMonitor`'s three carry and it is the
        // honest one: how far a sky *should* lean is not a thing that can be derived. The two below
        // that came back from the first such session — the scale and the smoothing — are unchanged,
        // because what that session reported was not that they were wrong.

        // The turn that moves the sky one full unit of travel — TILT_TRAVEL in `Starfield`, before
        // each plane's own parallax factor is applied.
        //
        // **A scale and no longer a stop**, which is the change 0.4.3 is mostly about. It used to be
        // both, and being a stop is what made the effect feel like it had an edge twenty degrees out.
        // As a scale it says only how much wrist buys how much sky: twelve degrees is about the range
        // of a wrist that is not trying, which is the point — the effect has to be well underway
        // inside the movement somebody makes without meaning to, or it only exists for people who go
        // looking for it. A full turn of the phone is thirty of these, which carries the nearest
        // plane a little over one screen width, so turning the phone right round takes the sky right
        // round.
        const val FULL_TRAVEL_DEGREES: Double = 12.0

        // The smoothing average's time constant. Long enough to swallow sensor noise, short enough
        // that the field is not visibly behind the hand — a tenth of a second is roughly the
        // threshold below which a response reads as immediate. It is now the only filter on this
        // path, so there is nothing downstream to hide a longer one behind.
        val SMOOTHING: Duration = 120.milliseconds

        // Beyond this, a gap is an absence rather than a long interval. See `sample`.
        val MAX_GAP: Duration = 2.seconds

        // How much of `down` has to lie in the plane of the glass before a roll is counted at all.
        // Below it the sideways axis holds rather than answering noise: an in-plane roll of a phone
        // lying flat is a spin about the vertical, which does not move `down` and which gravity
        // therefore cannot see.
        //
        // **A threshold in *readability*, and the distinction is the fix 0.4.3 is named for.** 0.4.2
        // had no such number because the cross product folded the same fact into the sideways gain
        // as `sin²(elevation)` — so the sky answered a lean at a quarter strength on a phone held at
        // thirty degrees, half at forty-five, and it read as the axis being lazy rather than as the
        // instrument being unsure. Kept apart, the response is flat across every pose a hand rests
        // in and stops only where the reading stops meaning anything.
        //
        // 0.26 is a phone about fifteen degrees off flat — all but face up on a desk, and well under
        // anything somebody holds a phone at to look at it. See `Bearing.turnTo` for why this is a
        // hard edge rather than the fade the first draft had.
        const val FAINT: Double = 0.26

        // The grid every reported value is snapped to, and it is a performance decision rather than
        // a visual one. **A still phone should produce a still field**: without a grid the last digit
        // of a gravity reading jitters forever, every sample is a new value, and the sky redraws
        // sixty times a second for the whole session to show nothing. Snapped, a still hand produces
        // the same `Tilt` twice and the source stops emitting. The step is a hundredth of a unit of
        // travel — well under a pixel of movement on the nearest plane, so nothing about it is
        // visible.
        //
        // How well it silences a still phone depends on how much the sensor actually wanders, which
        // is a device fact nobody here has measured; the smoothing knocks that down by roughly three
        // before it reaches the grid. **If a phone lying on a table still redraws, this is the
        // lever**, and raising it costs nothing visible for a long way yet.
        const val STEP: Float = 0.01f
    }
}

// `1 - e^(-elapsed/over)`, the fraction of the remaining distance an exponential average covers in
// `elapsed`. Derived from the real gap rather than fixed per sample, and that is not a nicety: the
// two platforms sample at different rates, a busy frame drops one, and a filter written as
// "move 8% each time" would then feel different on each phone and different again under load.
private fun easing(over: Duration, elapsed: Duration): Double =
    1.0 - exp(-elapsed.inWholeMicroseconds.toDouble() / over.inWholeMicroseconds.toDouble())

// How much of a step between two bearings to count, which is the shortest way round scaled by how
// far the shakier of the two ends can be trusted. Taking the *smaller* of the two is what makes a
// phone being laid down on a desk fade out on the way rather than at the moment it lands.
//
// **A gate and not a fade, and 0.4.3's first draft got that wrong in a way worth keeping written
// down.** It scaled each step by a weight that ramped in over a band of poses, which sounds gentler
// and is unsound: the weight is a function of the *elevation* while the step it scales is a *roll*,
// so the total became a line integral of a form that is not closed. Retracing a path cancelled —
// which is all the suite tested — and going round a loop did not. Four ordinary movements ending
// where they began left 2.2 units behind, and repeating them left 2.2 more, without bound.
//
// No weight that varies with the pose can avoid that; the fix is for it not to vary. Inside the
// readable range the weight is exactly one, so the steps telescope to the current angle minus where
// the count started and sensor noise cannot accumulate however long the session runs. Outside it the
// weight is exactly zero, which is a *re-anchor* rather than a discard, because the reference bearing
// advances every sample regardless: a phone laid down and spun leaves the sky where it was and picks
// up again from wherever the roll now is.
//
// Taking the *smaller* of the two ends keeps the gate symmetric in time, so a path and its reverse
// count exactly the same steps. **No hysteresis, deliberately** — a sticky gate that counted on the
// way down but not on the way up would be the same path dependence rebuilt by hand.
//
// What survives is bounded and forced: a loop that *crosses* the gate leaves the arc rolled below it
// uncounted. At that elevation the roll genuinely is a spin about the vertical, so there is nothing
// there to count.
private fun Bearing.turnTo(other: Bearing): Double =
    if (min(inPlane, other.inPlane) < TiltMonitor.FAINT) 0.0 else turnedFrom(radians, to = other.radians)

// A total turn, in radians, to units of travel — snapped to the grid and **not clamped**, which is
// the one-word version of what changed. The rounding is done in whole steps and multiplied back in
// `Float`, so a whole number of steps lands on the value a reader would write down: `0.5f`, not
// `0.49999999`.
//
// **The `+ 0.0f` is load-bearing and is the least obvious line in this module.** A small negative
// value rounds to `-0.0f`, and IEEE negative zero is a distinct bit pattern that `Float.equals` —
// and therefore the `data class` above, and therefore `distinctUntilChanged` on the flow — reads as
// a *different value* from `0.0f`, while every comparison and every arithmetic use of it says they
// are the same. Left in, a phone lying still on a table flickers between two tilts that are both
// level, and the sky redraws forever to show nothing: exactly the property `STEP` exists to
// guarantee, defeated by a sign bit. Adding positive zero normalises it and changes no other value.
private fun travel(radians: Double): Float {
    val units = radians / FULL_TRAVEL_RADIANS
    return round(units / TiltMonitor.STEP).toFloat() * TiltMonitor.STEP + 0.0f
}

private val FULL_TRAVEL_RADIANS = TiltMonitor.FULL_TRAVEL_DEGREES * PI / 180.0
