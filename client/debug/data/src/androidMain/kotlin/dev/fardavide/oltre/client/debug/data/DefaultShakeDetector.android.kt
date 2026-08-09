package dev.fardavide.oltre.client.debug.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dev.fardavide.oltre.client.debug.domain.ShakeMonitor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.math.sqrt
import kotlin.time.Clock

// Android needs a Context to reach SensorManager, and there is no context-free way to get one.
// Filled once by `OltreApplication`, before any component of the app can run — the same shape
// `AndroidNotificationHost` and `AndroidSaveLocation` use, and for the same reason.
//
// The *application* context, never an Activity's: an Activity held for the life of the process is
// a leaked window.
object AndroidShakeHost {
    var context: Context? = null
}

actual fun defaultShakeDetector(): ShakeDetector {
    // Absent rather than required, unlike the save directory and the alarm context: those two are
    // load-bearing and a null there is a bug worth crashing on, while a debug gesture that cannot
    // arm itself should leave the game running.
    val context = AndroidShakeHost.context ?: return ShakeDetector { emptyFlow() }
    return AndroidShakeDetector(context)
}

private class AndroidShakeDetector(private val context: Context) : ShakeDetector {

    override fun shakes(): Flow<Unit> = callbackFlow {
        val sensors = context.getSystemService(SensorManager::class.java)
        val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        // A device with no accelerometer is not an error — it is a Chromebook, or an emulator with
        // the sensor turned off. The menu is simply unreachable by gesture there, which is what the
        // desktop build already lives with.
        if (accelerometer == null) {
            awaitClose { }
            return@callbackFlow
        }

        var monitor = ShakeMonitor()
        val listener = object : SensorEventListener {

            override fun onSensorChanged(event: SensorEvent) {
                // TYPE_ACCELEROMETER reports metres per second squared *including* gravity, so a
                // phone on a table reads about 9.81. Divided by standard gravity here, at the edge
                // that knows the units, so the shared judgement can be written in multiples of g.
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val gForce = sqrt(x * x + y * y + z * z) / SensorManager.STANDARD_GRAVITY

                // The event's own timestamp is nanoseconds since boot on an unspecified base, so
                // the wall clock is read instead. Only the gaps between samples matter, and this
                // is a debug gesture rather than game state — the rule against reading a clock is
                // core's, and this is as far from core as the code gets.
                monitor = monitor.sample(gForce.toDouble(), at = Clock.System.now())
                if (monitor.shaken) {
                    monitor = monitor.reset()
                    trySend(Unit)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        // SENSOR_DELAY_GAME is ~50 Hz — fast enough that a shake is several distinct jolts rather
        // than one smear, and slow enough not to spend a battery on a gesture nobody is making.
        sensors.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
        awaitClose { sensors.unregisterListener(listener) }
    }
}
