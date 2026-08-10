package dev.fardavide.oltre.client.tilt.data

import dev.fardavide.oltre.client.tilt.domain.Tilt
import kotlinx.coroutines.flow.Flow

// The impure edge of the parallax: the device's motion service, with nothing game-shaped in it.
// Everything above is pure, so tests swap in a fake and never touch a sensor — the shape
// `ShakeDetector` established one sensor earlier.
fun interface TiltSource {

    // Emits whenever the sky should move, and **stops emitting when it should not**. That is a
    // stronger promise than it looks: the values are snapped to a grid in `TiltMonitor`, so a phone
    // resting on a table settles onto one value and this goes quiet, rather than pushing a
    // fractionally different number sixty times a second at a `Canvas` holding a hundred and one
    // stars. Cold, and collected only while the frame that draws them is composed.
    fun tilts(): Flow<Tilt>
}

// The platform's motion service. Called once, at the composition root.
//
// **Known limitation, named rather than discovered later: the tilt is in the device's frame, not the
// interface's.** Neither platform rotates its motion frame when the UI rotates — Android's sensor
// axes and CoreMotion's are both bolted to the hardware — and this app ships both landscape
// orientations on iPhone and all four on iPad. So in landscape the two axes are swapped and one is
// mirrored: leaning the phone still moves the sky, but it moves diagonally where it should move
// sideways. It degrades rather than breaks, which is why it ships this way.
//
// The fix is a rotation of the pair by the interface orientation, and it is deliberately not
// attempted here. Android would read it from `DisplayManager` in about five lines; iOS has no
// equivalent that is not a main-thread UIKit call reaching for a window scene from inside a sensor
// callback. Writing the easy half alone is exactly the drift this module's `domain` exists to
// prevent — one platform holding a correction the other does not — and neither half can be run from
// the session that wrote this. It wants a device in a hand and both halves at once.
expect fun defaultTiltSource(): TiltSource
