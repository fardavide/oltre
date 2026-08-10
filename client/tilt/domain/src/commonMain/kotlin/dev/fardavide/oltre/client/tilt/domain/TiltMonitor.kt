package dev.fardavide.oltre.client.tilt.domain

import kotlin.math.exp
import kotlin.math.round
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

// Turning a stream of attitudes into how far the sky is pushed. It lives here, with no platform in
// it, on `ShakeMonitor`'s argument: the platforms supply samples and this says what they add up to,
// so the sky leans the same way on both phones and the whole judgement is testable without a
// device.
//
// **It is a band-pass filter, and naming it that is the clearest way to say what it does.** Two
// exponential averages of the same signal are kept — a `fast` one and a `slow` one — and the tilt
// is the gap between them. Each end earns its place:
//
//   *The fast average is the noise floor.* A gravity sensor at rest still wanders a tenth of a
//   degree, and a field that shimmers in a still hand is worse than no field at all.
//
//   *The slow average is the centre, and it is the part that makes this usable at all.* There is no
//   correct pose to measure from: the phone is held flat on a desk, at forty degrees on a sofa, or
//   overhead in bed, and any fixed zero point makes two of those three permanently pushed against
//   the stop. Letting the centre follow the pose over RECENTRING seconds means wherever the phone
//   is held becomes level, a *movement* registers, and a pose that is simply held fades back to
//   centre over about ten seconds. The alternative — a centre captured once, at the first sample —
//   was rejected for the case that breaks it: the app is opened flat on a table and then picked up,
//   which is most launches, and the field spends the rest of the session pinned at full deflection.
//
// Neither average has a target it converges to and stops at, so nothing here loops or repeats.
// **What it does have is running state and a time constant, which the parallax did not**, and the
// honest reading of that is in `Starfield.kt` rather than smuggled past here.
data class TiltMonitor(val filtered: FilteredAttitude? = null) {

    // Null until the first sample, and that is the whole of the start-up behaviour: an app that has
    // just opened has no idea how the phone is being held, and any guess at it is a jerk on the
    // first frame the player sees.
    val tilt: Tilt
        get() {
            val reading = filtered ?: return Tilt.NONE
            // **Both signs are one subtraction from being the other way round**, and this is the
            // pair of lines to swap. The sky moves *against* the tilt, which is what makes it read
            // as something seen past the cards rather than something sitting on them: drop the
            // right edge and your eye moves right of the screen, so what was hidden behind the
            // right margin comes into view and the field slides left. Tip the top away and your
            // eye moves above it, so the field slides up. Nobody has held a phone running this yet
            // — see the constants below.
            //
            // Written as `slow − fast` rather than as a negated `fast − slow` so that a difference
            // of nothing is a positive zero. See `deflection`.
            return Tilt(
                x = deflection(reading.slow.roll - reading.fast.roll),
                y = deflection(reading.slow.pitch - reading.fast.pitch),
            )
        }

    // A value, exactly as `ShakeMonitor` is: the same monitor sampled with the same arguments always
    // gives the same answer, so a test is arithmetic and a dropped frame cannot corrupt anything.
    fun sample(attitude: Attitude, at: Instant): TiltMonitor {
        val previous = filtered
            // The first sample is the centre, so the first frame is level whatever the pose.
            ?: return TiltMonitor(FilteredAttitude(fast = attitude, slow = attitude, at = at))
        val elapsed = at - previous.at
        // **A long gap is an absence, not a long interval, and the difference is the whole of what
        // happens when the app comes back.** The sensor stops when the app is backgrounded, so the
        // next sample can arrive an hour later against a phone now held completely differently.
        // Feeding that to the averages does not re-centre — it does the opposite: the fast one
        // arrives instantly and the slow one, which is slow by construction, crawls a fifth of the
        // way, so the gap between them opens to full deflection and the sky slams against the stop
        // on the first frame back. Starting again from the new pose is the only reading of a gap
        // that long which is both true and calm.
        if (elapsed > MAX_GAP) {
            return TiltMonitor(FilteredAttitude(fast = attitude, slow = attitude, at = at))
        }
        // The other end, and it has happened too: iOS delivers motion on a queue and two samples can
        // be handed over out of order. A negative gap makes an easing factor greater than one — an
        // average that overshoots its target and rings — so it is floored rather than restarted,
        // because one sample arriving early is a hiccup and not an absence.
        val gap = elapsed.coerceAtLeast(Duration.ZERO)
        return TiltMonitor(
            FilteredAttitude(
                fast = previous.fast.towards(attitude, easing(over = SMOOTHING, elapsed = gap)),
                slow = previous.slow.towards(attitude, easing(over = RECENTRING, elapsed = gap)),
                // `previous.at + gap` rather than `at`, so the stored instant can never go backwards
                // and always matches the gap actually applied.
                at = previous.at + gap,
            ),
        )
    }

    // The two averages and the instant they were taken at. One nullable holder rather than three
    // nullable fields, because they are only ever all present or all absent and a shape that cannot
    // express the half-filled case cannot be left in it.
    data class FilteredAttitude(val fast: Attitude, val slow: Attitude, val at: Instant)

    companion object {

        // **Every number here is arithmetic rather than a measurement, and the first session with a
        // phone in hand should move them.** That is the same caveat `ShakeMonitor`'s three carry and
        // it is the honest one: a cloud session cannot hold a device, and how far a sky *should*
        // lean is not a thing that can be derived. They are chosen to be conservative — a player who
        // never thinks about it should never notice the field move on purpose.

        // The tilt at which an axis reaches the stop. Twelve degrees is about the range of a wrist
        // that is not trying, which is the point: the effect has to be complete inside the movement
        // somebody makes without meaning to, or it only exists for people who go looking for it.
        const val FULL_DEFLECTION_DEGREES: Double = 12.0

        // The fast average's time constant. Long enough to swallow sensor noise, short enough that
        // the field is not visibly behind the hand — a tenth of a second is roughly the threshold
        // below which a response reads as immediate.
        val SMOOTHING: Duration = 120.milliseconds

        // The slow average's. Four seconds means a deliberate movement is passed through nearly
        // whole (the centre barely moves inside one), while a pose that is held decays to about a
        // third of its deflection in four seconds and to nothing over ten.
        val RECENTRING: Duration = 4.seconds

        // Beyond this, a gap is treated as an absence rather than as a long interval. One second is
        // comfortably past any sampling rate a platform will use and comfortably short of a
        // backgrounded app.
        val MAX_GAP: Duration = 1.seconds

        // The grid every reported value is snapped to, and it is a performance decision rather than
        // a visual one. **A still phone must produce a still field**: without a grid the last digit
        // of a gravity reading jitters forever, every sample is a new value, and the sky redraws
        // sixty times a second for the whole session to show nothing. Snapped, a still hand
        // produces the same `Tilt` twice and the source stops emitting. The step is a two-hundredth
        // of full travel — well under a pixel of movement on the nearest plane, so nothing about it
        // is visible.
        const val STEP: Float = 0.01f
    }
}

// `1 - e^(-elapsed/over)`, the fraction of the remaining distance an exponential average covers in
// `elapsed`. Derived from the real gap rather than fixed per sample, and that is not a nicety: the
// two platforms sample at different rates, a busy frame drops one, and a filter written as
// "move 8% each time" would then feel different on each phone and different again under load.
private fun easing(over: Duration, elapsed: Duration): Double =
    1.0 - exp(-elapsed.inWholeMicroseconds.toDouble() / over.inWholeMicroseconds.toDouble())

private fun Attitude.towards(target: Attitude, by: Double): Attitude = Attitude(
    pitch = pitch + (target.pitch - pitch) * by,
    roll = roll + (target.roll - roll) * by,
)

// Degrees to a fraction of full travel, clamped at the stop and snapped to the grid. The rounding
// is done in whole steps and multiplied back in `Float`, so a whole number of steps lands on the
// value a reader would write down — `0.5f` rather than `0.49999999`.
//
// **The `+ 0.0f` is load-bearing and is the least obvious line in this module.** A small negative
// difference rounds to `-0.0f`, and IEEE negative zero is a distinct bit pattern that
// `Float.equals` — and therefore the `data class` above, and therefore `distinctUntilChanged` on
// the flow — reads as a *different value* from `0.0f`, while every comparison and every arithmetic
// use of it says they are the same. Left in, a phone lying still on a table flickers between two
// tilts that are both level, and the sky redraws forever to show nothing: exactly the property
// `STEP` exists to guarantee, defeated by a sign bit. Adding positive zero normalises it and
// changes no other value.
private fun deflection(degrees: Double): Float {
    val fraction = (degrees / TiltMonitor.FULL_DEFLECTION_DEGREES).coerceIn(-1.0, 1.0)
    return round(fraction / TiltMonitor.STEP).toFloat() * TiltMonitor.STEP + 0.0f
}
