package dev.fardavide.oltre.client.tilt.domain

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.round
import kotlin.math.sqrt

// Which way is down, as a unit vector in the device's own frame: `x` across the screen to the right,
// `y` up the long edge, `z` out of the glass towards the player. It exists here, in a module with no
// platform in it, for the reason `ShakeMonitor` does — the same geometry written once against
// Android's `SensorManager` and again against iOS's `CoreMotion` is geometry that drifts until the
// sky leans a different way on each phone.
//
// **A direction, read as two full-circle angles — and the two wrong ways to do that both shipped
// here, so both are named.**
//
//   *An angle per axis, taken as `asin` of one component.* Defined everywhere, looks safe, folds.
//   `asin(y)` cannot tell a phone tipped 80° from one tipped 100°, so the response rectified at
//   exactly upright-in-portrait and inverted past it. Worse, because `sin²(pitch) + sin²(roll) ≤ 1`
//   the two axes were not independent, and a pure sideways lean of an upright phone produced six
//   degrees of spurious pitch. Both were measured before it was replaced.
//
//   *The rotation between two directions, as a cross product.* Correct, and it fixed both of those —
//   but it is a **sine**, so it reads only the half-turn either side of where it started and folds
//   in exactly the same place the first one did, one quadrant further out. Inside the twelve degrees
//   0.4.2 clamped to, nothing showed. Asked for a full turn, it has nowhere past 90° to report. It
//   also carried the pose into the *gain* of the sideways axis rather than into its precision — see
//   `Bearing.inPlane` — which is what made the lean read as lazy on a phone held at any angle.
//
// What is here now is `atan2` of a *pair* of components, which is not the first mistake wearing a
// hat: knowing the quadrant is exactly what a fold is the absence of, and it is measured from the
// axes rather than from a previous reading, so nothing accumulates.
//
// **And the two axes are read differently, which is forced rather than chosen.** No reading of the
// tip can be all three of: unmoved by a roll, monotonic through a full end-over-end turn, and
// dependent only on where the phone is now. The proof is one line of this module's own pose model:
//
//     g(elevation, lean + 180°) == g(-elevation, lean)
//
// Roll the phone upside down and tip it *forward*, and gravity reports exactly what tipping it
// *backward* the right way up reports. One of those has to add and the other to subtract, so a
// reading that is blind to the roll cannot tell them apart, and one that can tell them apart is
// answering the roll. It is not a shortcoming of gravity — a fused attitude quaternion is subject
// to the same argument — so a second sensor buys nothing here.
//
// 0.4.3's first draft picked the corner that gives up being unmoved by a roll, and it was measured
// on a device before it shipped: at a fifty-degree hold a ninety-degree roll dragged the sky 4.17
// units *vertically* and a half turn dragged it 8.33, off a gesture that asked for none. What is
// here now gives up the full turn instead. See `tip`.
data class Gravity(val x: Double, val y: Double, val z: Double) {

    // How far the glass is tipped back from facing straight up — the elevation of the screen's own
    // normal, which is the thing a window's vertical parallax is actually a function of. Zero is
    // face up on a table, a quarter turn is upright in portrait, half a turn is face down.
    //
    // **Exactly independent of the roll, at every pose, and never undefined** — `x² + y²` and `z`
    // cannot both vanish on a unit vector, so unlike every other reading in this module it needs no
    // companion measure of how far to trust it. Rolling the phone spins it about this very axis and
    // moves the normal not at all; the draft this replaced read the elevation of the phone's long
    // *edge*, which a roll sweeps round a cone, and that was the whole of the defect.
    //
    // **The price is that it runs `0..π` and turns back rather than round**, so tipping the phone on
    // past face-down retraces its travel instead of continuing. That is the trade named above, taken
    // deliberately: the range it gives up is the half of a turn where the screen is pointing away
    // from the player, and the range it keeps is every pose the screen can be read from.
    //
    // **It also fixes a pose the draft lost entirely.** That reading died in landscape held with the
    // long edge horizontal — measured as no vertical response at all at any roll — because its plane
    // had emptied out. This one has no such pose.
    val tip: Double get() = atan2(sqrt(x * x + y * y), -z)

    // Where `down` sits in the plane of the glass — the in-plane roll, positive dropping the right
    // edge, and the axis a lean moves.
    val lean: Bearing get() = Bearing(radians = atan2(x, -y), inPlane = sqrt(x * x + y * y))

    // Towards `other` by `fraction`, renormalised. An average of two directions is not itself a
    // direction until it is put back on the sphere, and `inPlane` beside a bearing only says
    // something about the pose if the vector it came off is unit length.
    internal fun towards(other: Gravity, fraction: Double): Gravity? = normalised(
        x = x + (other.x - x) * fraction,
        y = y + (other.y - y) * fraction,
        z = z + (other.z - z) * fraction,
    )

    companion object {

        // Null when the vector has no direction to give, which is a real reading rather than a
        // defensive gesture: a device in free fall reports nothing in every axis, and the fallback
        // path on a phone with no fused gravity sensor can pass through zero mid-shake. Left
        // unguarded it is a division by zero, and one `NaN` entering an exponential average stays
        // there for the life of the process — a sky frozen for the rest of the session.
        //
        // **Scale is discarded here and that is why nothing else divides by 9.81.** Android reports
        // metres per second squared and iOS multiples of g; both arrive as the same unit vector.
        fun normalised(x: Double, y: Double, z: Double): Gravity? {
            val magnitude = sqrt(x * x + y * y + z * z)
            if (!magnitude.isFinite() || magnitude < MINIMUM_MAGNITUDE) return null
            return Gravity(x = x / magnitude, y = y / magnitude, z = z / magnitude)
        }

        // Small enough that no real reading in either platform's units is rejected — iOS rests at
        // 1.0 and Android at 9.81 — and large enough that what is left cannot survive the division.
        private const val MINIMUM_MAGNITUDE = 1e-6
    }
}

// An angle read off `down` in the plane of the glass, together with how much of `down` was in that
// plane to read it from. Only the lean needs this shape — `tip` above is readable from every pose
// there is — and that asymmetry is the honest one: a phone lying flat has no in-plane roll gravity
// can see, and there is no pose in which the screen has no elevation.
//
// **The second field is the whole reason this is a pair rather than a `Double`, and getting it wrong
// is what 0.4.2 shipped.** Approaching flat does not make the roll *smaller*, it makes it *less
// certain* — the projection it is read off shrinks towards nothing and the last digits become noise.
//
// The cross product conflated those two things, because a sine of a turn about a shrinking
// projection is one number carrying both. So the sideways axis moved the sky by `sin²(elevation)` of
// what the same wrist gave upright — a quarter at 30°, half at 45° — and it read as the axis being
// half asleep rather than as the instrument being unsure. Kept apart, `radians` is the movement and
// `inPlane` is only ever allowed to say *whether to trust it*.
data class Bearing(val radians: Double, val inPlane: Double)

// The turn from one bearing to another, taking the short way round: `(-π, π]`, so a phone rolling
// forwards through the seam reads as a small step onwards rather than as a full turn backwards.
//
// **This is what makes the travel unbounded**, and the reason it does not drift while doing it.
// Every angle here is measured from the device's own axes, so a running total of these steps is
// exactly *the current angle, plus a whole number of turns, minus wherever the count started* — the
// integer is the only part that accumulates, and it only changes when the phone really does go round.
// Integrating a rate, which a gyroscope would have forced, has no such guarantee: it random-walks on
// noise and picks up a little more every time a hand traces a loop in the air.
internal fun turnedFrom(bearing: Double, to: Double): Double {
    val raw = to - bearing
    return raw - TURN * round(raw / TURN)
}

private const val TURN = 2 * PI
