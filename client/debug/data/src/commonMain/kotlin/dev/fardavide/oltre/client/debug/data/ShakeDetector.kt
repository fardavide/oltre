package dev.fardavide.oltre.client.debug.data

import kotlinx.coroutines.flow.Flow

// The impure edge of the gesture: the device's accelerometer, with nothing game-shaped in it.
// Everything above is pure, so tests swap in a fake and never touch a sensor.
fun interface ShakeDetector {

    // Emits once per completed shake. Cold and collected only while the menu's host is composed —
    // an accelerometer left running is a battery cost, and this game's whole premise is that it is
    // not running most of the time.
    fun shakes(): Flow<Unit>
}

// The platform's motion service. Called once, at the composition root.
expect fun defaultShakeDetector(): ShakeDetector
