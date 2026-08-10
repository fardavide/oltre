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
// hat: knowing the quadrant is exactly what a fold is the absence of, so it walks the whole circle,
// and it is measured from the axes rather than from a previous reading, so nothing accumulates.
data class Gravity(val x: Double, val y: Double, val z: Double) {

    // Where `down` sits in the plane of the long edge and the glass — the elevation the phone is
    // held at, and the axis a tip moves. Zero is face up on a table, a quarter turn is upright in
    // portrait, half a turn is face down, and it keeps going.
    val tip: Bearing get() = Bearing(radians = atan2(-y, -z), inPlane = sqrt(y * y + z * z))

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

// An angle read off `down` in one of the device's two turning planes, together with how much of
// `down` was in that plane to read it from.
//
// **The second field is the whole reason this is a pair rather than a `Double`, and getting it wrong
// is what 0.4.2 shipped.** Each plane has a pose in which the turn it describes is a spin about the
// vertical, which gravity physically cannot see: for the lean that is a phone lying flat, for the
// tip it is a phone in landscape with its long edge horizontal. Approaching such a pose does not
// make the angle *smaller*, it makes it *less certain* — the projection it is read off shrinks
// towards nothing and the last digits become noise.
//
// The cross product conflated those two things, because a sine of a turn about a shrinking
// projection is one number carrying both. So the sideways axis moved the sky by `sin²(elevation)` of
// what the same wrist gave upright — a quarter at 30°, half at 45° — and it read as the axis being
// half asleep rather than as the instrument being unsure. Kept apart, `radians` is the movement and
// `inPlane` is only ever allowed to say *how much to trust it*.
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
