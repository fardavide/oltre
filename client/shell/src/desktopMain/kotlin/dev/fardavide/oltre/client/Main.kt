package dev.fardavide.oltre.client

import androidx.compose.runtime.remember
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import dev.fardavide.oltre.client.debug.data.ShakeDetector
import kotlinx.coroutines.flow.MutableSharedFlow

fun main() = application {
    // A laptop has no accelerometer and shaking a desk is not a gesture, so desktop opens the debug
    // menu with a chord instead — and desktop is the platform that wants it most, because it is the
    // dev loop. Ctrl+D or Cmd+D, so the same key works whichever machine this is.
    //
    // It lives here rather than in `App` because a key chord is platform input, which is the entry
    // point's job: the same slot `MainActivity` fills with edge-to-edge and a permission prompt.
    // `App` takes a `ShakeDetector` and does not care that this one is a keyboard.
    val shakes = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Oltre",
        onKeyEvent = { event ->
            val chord = event.type == KeyEventType.KeyDown &&
                event.key == Key.D &&
                (event.isCtrlPressed || event.isMetaPressed)
            // `tryEmit` rather than `emit`: this is not a coroutine, and a buffer of one is the
            // right answer anyway — a second chord while the first is unhandled opens the same
            // panel the first one did.
            if (chord) shakes.tryEmit(Unit)
            // Consumed only when it was the chord, so every other key still reaches the app.
            chord
        },
    ) {
        App(shakeDetector = ShakeDetector { shakes })
    }
}
