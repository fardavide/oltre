package dev.fardavide.oltre.client.tilt.domain

import kotlin.math.sqrt

// Which way is down, as a unit vector in the device's own frame: `x` across the screen to the right,
// `y` up the long edge, `z` out of the glass towards the player. It exists here, in a module with no
// platform in it, for the reason `ShakeMonitor` does — the same geometry written once against
// Android's `SensorManager` and again against iOS's `CoreMotion` is geometry that drifts until the
// sky leans a different way on each phone.
//
// **A direction and not a pair of angles, and the first version of this module got that wrong.** It
// described a pose as an elevation of each axis above the horizon — `asin` of a component — which is
// defined everywhere and looks safe, and folds. `asin(y)` cannot tell a phone tipped 80° from flat
// from one tipped 100°, so the response *rectifies* at exactly upright-in-portrait (leaning either
// way moves the sky the same way) and *inverts* past it (reading lying down leans the sky backwards).
// Worse, the two axes were not independent: because `sin²(pitch) + sin²(roll) ≤ 1`, a pure sideways
// lean of a phone held upright produced six degrees of spurious pitch, so the sky went diagonally.
// Both were measured before this replaced it. There is nothing to salvage in that formulation — the
// crease sits on the most common pose there is.
data class Gravity(val x: Double, val y: Double, val z: Double) {

    // The rotation carrying this direction to `other`: the cross product, which is the axis of the
    // turn scaled by the sine of its angle. **Every good property of this module comes from here.**
    //
    //   *Constant gain in every pose.* A six-degree turn reads as `sin 6°` whether the phone was
    //   flat, upright, or tipped past vertical — measured at nine poses across the full half-turn.
    //   There is no fold, because a rotation between two real directions cannot have one.
    //
    //   *Axes that stay independent.* A pure sideways lean puts its whole magnitude on `aboutZ` and
    //   leaves `aboutX` at zero, where the angle formulation spilled the two into each other.
    //
    //   *The platform sign difference cancels for nothing.* Android reports the reaction to gravity
    //   and iOS reports gravity, so the two vectors are exact negations — and `(−a) × (−b) = a × b`.
    //   The first version of this module claimed the platforms needed no reconciliation and was
    //   wrong; this one does not need the claim, because negating both operands is invisible here.
    //
    // `aboutY` is the turn about the axis out of the glass — yaw, which gravity physically cannot
    // observe, since spinning a phone flat on a table changes nothing about where down is. It is
    // returned rather than dropped because a reading near zero is the check that this is being used
    // as intended, and `GravityTest` holds it there.
    fun rotationTo(other: Gravity): Rotation = Rotation(
        aboutX = y * other.z - z * other.y,
        aboutY = z * other.x - x * other.z,
        aboutZ = x * other.y - y * other.x,
    )

    // Towards `other` by `fraction`, renormalised. An average of two directions is not itself a
    // direction until it is put back on the sphere, and the cross product above only reads as a sine
    // if both sides are unit length.
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

// An axis scaled by the sine of the angle turned about it. Not an angle: the sine is what the cross
// product gives, it is what a deflection wants, and taking an `asin` of it would put back the
// trigonometry this module exists without.
data class Rotation(val aboutX: Double, val aboutY: Double, val aboutZ: Double)
