package dev.fardavide.oltre.client.tilt.data

import dev.fardavide.oltre.client.tilt.domain.Tilt
import dev.fardavide.oltre.client.tilt.domain.TiltMonitor
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled
import kotlin.time.Clock

actual fun defaultTiltSource(): TiltSource = IosTiltSource()

// Device motion rather than the raw accelerometer its sibling `IosShakeDetector` uses, and the two
// wanting different things is the reason. A shake is a jolt, which is exactly what the accelerometer
// reports and what device motion has already subtracted out; a lean is a pose, which is what device
// motion isolates as `gravity` and what the accelerometer only approximates whenever the hand
// happens to be still. Neither is the gyroscope: `startGyroUpdates` reports angular rate, so a held
// pose reports nothing and reaching a pose means integrating a rate that drifts.
private class IosTiltSource : TiltSource {

    // `CMAcceleration` is a C struct, so reading its three fields goes through `useContents` — the
    // one piece of interop on this path and the reason the opt-in sits here rather than file-wide.
    @OptIn(ExperimentalForeignApi::class)
    override fun tilts(): Flow<Tilt> = callbackFlow {
        val motion = CMMotionManager()
        // A simulator with no motion support, which is where this will first be run, and a player
        // who has asked iOS for less movement. Apple names parallax as the example of what Reduce
        // Motion is for, so honouring it is not a courtesy — the platform's own wallpaper stops
        // doing this when it is set.
        if (!motion.deviceMotionAvailable || UIAccessibilityIsReduceMotionEnabled()) {
            send(Tilt.NONE)
            awaitClose { }
            return@callbackFlow
        }

        // ~50 Hz, matching Android's SENSOR_DELAY_GAME so the shared filter sees the same shape of
        // stream on both phones — its two time constants are meaningless if one device reports at
        // five times the rate of the other.
        motion.deviceMotionUpdateInterval = 1.0 / 50.0

        var monitor = TiltMonitor()
        // The main queue, matching `IosShakeDetector`. The work per sample is two exponential
        // averages and a cross product, which is nothing next to drawing the frame it feeds; a
        // background queue would buy an unmeasurable amount of main-thread time and pay for it with
        // samples arriving out of order — a case `TiltMonitor` is written to survive, but not one
        // worth provoking on purpose.
        motion.startDeviceMotionUpdatesToQueue(NSOperationQueue.mainQueue) { data, _ ->
            // Multiples of g, gravity already separated from whatever the hand is doing. iOS
            // reports a phone lying face up as `z = -1` where Android reports `+9.81`, and neither
            // the sign nor the scale is corrected here — see the note on the Android source, which
            // does not correct them either. `TiltMonitor` normalises the vector and reads movement
            // as a cross product, which is blind to both.
            val reading = data?.gravity?.useContents { Triple(x, y, z) }
            if (reading != null) {
                monitor = monitor.sample(
                    x = reading.first,
                    y = reading.second,
                    z = reading.third,
                    at = Clock.System.now(),
                )
                trySend(monitor.tilt)
            }
        }

        awaitClose { motion.stopDeviceMotionUpdates() }
    }
        // See the note on the Android source: `distinctUntilChanged` is what makes a still phone a
        // still sky, and `conflate` keeps a late frame from drawing a queue of stale poses.
        .distinctUntilChanged()
        .conflate()
}
