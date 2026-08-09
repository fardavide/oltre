package dev.fardavide.oltre.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.fardavide.oltre.client.save.data.AndroidSaveLocation

// The Android entry point, beside the desktop `main()` and the iOS `MainViewController()`. It is
// named by `androidApp/src/main/AndroidManifest.xml`, which is the whole of the module that
// packages it — so this class is referenced by a string, and renaming it fails the manifest
// merge rather than the compiler.
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before anything can read or write a save. Android is the one platform with no
        // context-free way to name its own private directory, so `:client:save:data` leaves a
        // slot for the application to fill and throws rather than guessing if it is empty.
        AndroidSaveLocation.directory = filesDir

        // Draw behind the system bars. Not a style choice: from targetSdk 35 the platform
        // ignores an app that asks otherwise, and `MainScaffold` already pads itself with
        // `WindowInsets.safeDrawing` — insets are the frame's job on every platform.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent { App() }
    }
}
