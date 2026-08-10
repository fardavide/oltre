package dev.fardavide.oltre.client.tilt.domain

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// A phone held somewhere, as the gravity vector a device in that pose actually reports.
//
// **Every test in this module drives poses rather than hand-written numbers, and that is the whole
// lesson of the version this replaced.** The first `TiltMonitor` was tested against pitch/roll pairs
// typed in by hand, several of which no device can produce — `sin²(pitch) + sin²(roll)` came to 1.38
// on one of them — and the suite therefore explored a space real hardware never reaches while
// missing two defects that any real pose walk would have caught in a line.
//
// `elevation` is degrees from flat: 0 is face up on a table, 90 is upright in portrait, 180 is face
// down. `lean` is degrees of in-plane roll, positive dropping the right edge. Between them they
// reach every pose a hand can hold, which is what `everyPose` walks.
internal data class Pose(val elevation: Double, val lean: Double = 0.0) {

    // iOS's convention — the vector pointing at the ground — at unit scale.
    val gravity: Triple<Double, Double, Double>
        get() {
            val e = elevation.radians()
            val tipped = Triple(0.0, -sin(e), -cos(e))
            val l = lean.radians()
            return Triple(
                cos(l) * tipped.first - sin(l) * tipped.second,
                sin(l) * tipped.first + cos(l) * tipped.second,
                tipped.third,
            )
        }

    // What Android reports for the same phone: the reaction to gravity, pointing at the sky, and in
    // metres per second squared rather than multiples of g. Every component is negated and the whole
    // thing is ten times bigger — which is exactly the difference that has to come out in the wash.
    val androidReading: Triple<Double, Double, Double>
        get() = gravity.let { Triple(-it.first * 9.81, -it.second * 9.81, -it.third * 9.81) }
}

// Poses spanning the full half-turn, including the two that broke the previous formulation: exactly
// upright, where an elevation-of-an-axis reading rectifies, and past it, where it inverts.
internal val EVERY_POSE = listOf(0.0, 20.0, 45.0, 60.0, 85.0, 89.0, 90.0, 91.0, 120.0, 160.0, 180.0)

private fun Double.radians(): Double = this * PI / 180.0
