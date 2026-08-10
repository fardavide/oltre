package dev.fardavide.oltre.client.tilt.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import dev.fardavide.oltre.client.tilt.domain.Tilt
import dev.fardavide.oltre.client.tilt.domain.TiltMonitor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Clock

// Android needs a Context to reach SensorManager, and there is no context-free way to get one.
// Filled once by `OltreApplication`, before any component of the app can run — the same shape
// `AndroidShakeHost`, `AndroidNotificationHost` and `AndroidSaveLocation` use, and for the same
// reason. The *application* context, never an Activity's: an Activity held for the life of the
// process is a leaked window.
object AndroidTiltHost {
    var context: Context? = null
}

actual fun defaultTiltSource(): TiltSource {
    // Absent rather than required, on `AndroidShakeHost`'s grounds: the save directory and the alarm
    // context are load-bearing and a null there is worth crashing on, while a background effect that
    // cannot arm itself should leave the game running and simply hold still.
    val context = AndroidTiltHost.context ?: return TiltSource { flowOf(Tilt.NONE) }
    return AndroidTiltSource(context)
}

private class AndroidTiltSource(private val context: Context) : TiltSource {

    override fun tilts(): Flow<Tilt> = callbackFlow {
        val sensors = context.getSystemService(SensorManager::class.java)
        // **TYPE_GRAVITY rather than TYPE_ACCELEROMETER, and rather than the gyroscope.** A raw
        // gyroscope reports angular *rate*, so holding a pose reports nothing at all and the only
        // way to a pose is to integrate — which accumulates its own error until the sky drifts off
        // on a phone lying still. Gravity is the fused sensor that answers the actual question,
        // "which way is down", with no drift to accumulate. That mattered more from 0.4.3, which
        // made the travel unbounded: a reading that can count whole turns has to be one that cannot
        // wander while nobody is turning it. The accelerometer is the fallback for a device that
        // publishes no fused sensor: it is the same vector plus whatever the hand is doing, and the
        // smoothing behind this rejects the fast end of that.
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        // A device with no motion sensor at all is not an error — it is a Chromebook, or an emulator
        // with the sensors switched off — and neither is a player who has asked for less movement.
        // Both get a sky that holds still, which is a complete answer rather than a degraded one.
        //
        // `sensors` is tested here as well as `sensor`, even though a null service already implies a
        // null sensor: `getSystemService` comes back as a platform type, so relying on the
        // implication would leave `registerListener` below compiling only by Kotlin's willingness to
        // take Java at its word. Naming it makes the smart cast the compiler's rather than the
        // platform's.
        if (sensors == null || sensor == null || context.prefersReducedMotion()) {
            send(Tilt.NONE)
            awaitClose { }
            return@callbackFlow
        }

        var monitor = TiltMonitor()
        val listener = object : SensorEventListener {

            override fun onSensorChanged(event: SensorEvent) {
                // **Handed over raw, and neither the sign nor the scale is corrected here.** Android
                // reports the *reaction* to gravity — its own documentation says a phone lying flat
                // on a table reads `z = +9.81` — where iOS reports gravity itself and would call the
                // same phone `z = -1.0`. Every component is negated and the units differ by ten.
                // `TiltMonitor` normalises the vector away, and negating all three components turns
                // both of the angles it reads by exactly half a circle — so the platforms disagree
                // about every angle and agree about every *difference* between two of them, which is
                // the only thing anything downstream looks at. A correction applied in one of these
                // two files and not the other is exactly how the sky ends up leaning the wrong way on
                // one phone, so neither file holds one.
                monitor = monitor.sample(
                    x = event.values[0].toDouble(),
                    y = event.values[1].toDouble(),
                    z = event.values[2].toDouble(),
                    // The event's own timestamp is nanoseconds since boot on an unspecified base,
                    // so the wall clock is read instead — the same call and the same argument as
                    // `AndroidShakeDetector`. Only the gaps between samples matter, and the rule
                    // against reading a clock is `core`'s.
                    at = Clock.System.now(),
                )
                trySend(monitor.tilt)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        // SENSOR_DELAY_GAME is ~50 Hz, matching the accelerometer this app already registers so the
        // two sensors ask the same of the device, and matching iOS below so the shared filter sees
        // the same shape of stream on both phones.
        sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensors.unregisterListener(listener) }
    }
        // In this order and both needed. `distinctUntilChanged` is what makes a still phone a still
        // sky — `TiltMonitor` snaps its values to a grid exactly so that consecutive samples from a
        // hand that is not moving compare equal here and go no further. `conflate` then says that
        // if the frame is behind, the newest tilt is the only one worth having: a backlog of stale
        // poses is not something anybody wants drawn.
        .distinctUntilChanged()
        .conflate()
}

// The accessibility setting that Android's own "Remove animations" writes, and the one AndroidX
// reads for the same purpose. A parallax driven by the device is the textbook thing this setting
// exists to switch off, so it is honoured rather than offered as a preference of our own.
//
// **Read once, when collection starts.** Toggling it mid-session does nothing until the next launch,
// which is worth naming rather than hiding: watching it properly means a ContentObserver on both
// platforms' equivalents, and the setting is one people change roughly never and always outside the
// app they are changing it for.
private fun Context.prefersReducedMotion(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
