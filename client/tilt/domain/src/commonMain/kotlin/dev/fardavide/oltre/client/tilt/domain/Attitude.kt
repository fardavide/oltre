package dev.fardavide.oltre.client.tilt.domain

import kotlin.math.atan2
import kotlin.math.sqrt

// How the device is being held, as two angles in degrees. It exists here, in a module with no
// platform in it, for the reason `ShakeMonitor` does: the same trigonometry written once against
// Android's `SensorManager` and again against iOS's `CoreMotion` is trigonometry that drifts until
// the sky leans a different way on each phone.
//
// **Both angles are elevations of a device axis above the horizon**, rather than the Euler pitch
// and roll a flight simulator would use, and that is what makes them well behaved. An Euler pair
// has to name an order and gains a singularity where the order stops mattering — and the
// singularity is not in some exotic pose, it is *a phone held upright in portrait*, which is how
// this game is held. `CMAttitude` puts that pose at exactly +π/2 of pitch, where roll and yaw go
// degenerate and roll can snap by half a turn on a millimetre of movement. An elevation has no such
// point: it is the angle between one axis and the ground, it is defined everywhere, and it moves
// smoothly through every pose a hand can hold.
data class Attitude(val pitch: Double, val roll: Double) {

    companion object {

        // From the **gravity vector — the one pointing at the ground** — in the device's own frame:
        // `x` across the screen to the right, `y` up the long edge, `z` out of the glass towards the
        // player. This is what iOS's `CMDeviceMotion.gravity` reports directly.
        //
        // `pitch` grows as the top edge falls away from the player: −90° holding the phone upright,
        // 0° with it flat. `roll` grows as the right edge drops: 0° level, +90° on its right side.
        // Neither zero point is load-bearing, because `TiltMonitor` measures against a centre it
        // works out for itself — what matters is that both are continuous and that the two platforms
        // agree.
        //
        // *Scale does not matter*, which is why nothing divides by 9.81 anywhere: Android reports
        // metres per second squared and iOS reports multiples of g, and `atan2` takes a ratio.
        fun fromGravity(x: Double, y: Double, z: Double): Attitude = Attitude(
            pitch = atan2(y, sqrt(x * x + z * z)).toDegrees(),
            roll = atan2(x, sqrt(y * y + z * z)).toDegrees(),
        )

        // From **the reaction to gravity — the vector pointing at the sky** — which is what Android
        // reports from both `TYPE_ACCELEROMETER` and `TYPE_GRAVITY`, in the same device frame. Its
        // documentation is explicit and counter-intuitive: *"when the device lies flat on a table,
        // the acceleration value along z is +9.81"*, and the gravity sensor is defined to agree with
        // the accelerometer at rest. iOS reports the same phone as `z = −1.0`.
        //
        // **This exists as a named function rather than as a minus sign at the call site, and that
        // is the entire point of it.** The two platforms report opposite vectors, so one of them has
        // to flip; a bare `fromGravity(-x, -y, -z)` in the Android file is a line that looks like
        // clutter to the next person reading it, and deleting it costs nothing that any test in this
        // repository would notice. What it actually costs is that the sky leans one way on an iPhone
        // and the other way on a Pixel — the exact defect nobody finds without owning both phones.
        // Named, the platform edge picks the function whose name matches its own documentation, and
        // `AttitudeTest` holds the two together.
        fun fromReactionToGravity(x: Double, y: Double, z: Double): Attitude =
            fromGravity(x = -x, y = -y, z = -z)
    }
}

private const val DEGREES_PER_RADIAN = 180.0 / kotlin.math.PI

private fun Double.toDegrees(): Double = this * DEGREES_PER_RADIAN
