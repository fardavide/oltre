package dev.fardavide.oltre.client.debug.data

import dev.fardavide.oltre.client.debug.domain.ShakeMonitor
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue
import kotlin.math.sqrt
import kotlin.time.Clock

// CoreMotion rather than UIKit's `motionEnded(.motionShake)`, which is the more obvious answer and
// the wrong one here. The shake event arrives on the responder chain, so catching it means owning a
// `UIWindow` or `UIViewController` subclass — and the window belongs to the Xcode wrapper in
// `iosApp/` while the controller belongs to `ComposeUIViewController`. Reading the accelerometer
// directly needs neither, and it means both phones run the *same* judgement about what a shake is
// (`ShakeMonitor`) rather than one running Apple's and one running ours.
actual fun defaultShakeDetector(): ShakeDetector = IosShakeDetector()

private class IosShakeDetector : ShakeDetector {

    // `CMAcceleration` is a C struct, so reading its fields goes through `useContents` — the one
    // piece of interop on this path, and the reason the opt-in is here rather than file-wide.
    @OptIn(ExperimentalForeignApi::class)
    override fun shakes(): Flow<Unit> = callbackFlow {
        val motion = CMMotionManager()
        // A simulator with no motion support, which is where this will first be run.
        if (!motion.accelerometerAvailable) {
            awaitClose { }
            return@callbackFlow
        }

        // ~50 Hz, matching Android's SENSOR_DELAY_GAME so the shared monitor sees the same shape of
        // stream on both platforms — its jolt-gap constant is meaningless if one phone samples at
        // five times the rate of the other.
        motion.accelerometerUpdateInterval = 1.0 / 50.0

        var monitor = ShakeMonitor()
        motion.startAccelerometerUpdatesToQueue(NSOperationQueue.mainQueue) { data, _ ->
            // iOS reports acceleration in multiples of g already, gravity included — so unlike
            // Android there is nothing to divide by, and a phone on a table reads about 1.0.
            val gForce = data?.acceleration?.useContents { sqrt(x * x + y * y + z * z) }
            if (gForce != null) {
                monitor = monitor.sample(gForce, at = Clock.System.now())
                if (monitor.shaken) {
                    monitor = monitor.reset()
                    trySend(Unit)
                }
            }
        }

        awaitClose { motion.stopAccelerometerUpdates() }
    }
}
