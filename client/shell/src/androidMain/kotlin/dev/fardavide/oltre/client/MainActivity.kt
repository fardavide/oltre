package dev.fardavide.oltre.client

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import dev.fardavide.oltre.client.auth.data.AndroidSignInHost

// The Android entry point, beside the desktop `main()` and the iOS `MainViewController()`. It is
// named by `androidApp/src/main/AndroidManifest.xml`, which is the whole of the module that
// packages it — so this class is referenced by a string, and renaming it fails the manifest
// merge rather than the compiler.
class MainActivity : ComponentActivity() {

    // Registered as a field because `registerForActivityResult` has to be called before the
    // Activity is started, and there is nothing to do with the answer: like iOS, a refusal is
    // unreported and unrecoverable in-app by design. There is no surface for it, and the game is
    // entirely playable without alerts.
    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Draw behind the system bars. Not a style choice: from targetSdk 35 the platform
        // ignores an app that asks otherwise, and `MainScaffold` already pads itself with
        // `WindowInsets.safeDrawing` — insets are the frame's job on every platform.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // **Credential Manager raises a system sheet, so it needs an Activity and not the
        // application context** — the one place in this app where that is true, which is why this
        // host differs from the notification and shake ones by a word. Set before `setContent`,
        // because the gate is the first thing composed and a provider button that found no Activity
        // would be a control that opens nothing.
        AndroidSignInHost.activity = this

        askAboutNotificationsOnce()
        setContent { App() }
    }

    // **An Activity held past its own death is a leaked window**, and the process outlives this one
    // every time the app is backgrounded. Cleared rather than left, and guarded on identity because
    // a configuration change creates the next Activity before destroying this one — without the
    // check, a rotation would clear the host the new Activity had just filled.
    override fun onDestroy() {
        if (AndroidSignInHost.activity === this) AndroidSignInHost.activity = null
        super.onDestroy()
    }

    // The same moment iOS asks: the first frame, when the colony is loaded. Deliberately not
    // deferred to a "better moment" — the alerts *are* the check-in loop, so a player who
    // declines has declined something they can see the shape of.
    //
    // Android differs from iOS in one way that matters here: the system stops showing this
    // dialog after two refusals and answers "denied" silently thereafter, so asking on every
    // launch costs nothing and cannot nag. Below API 33 there is no permission to ask for and
    // notifications post regardless.
    private fun askAboutNotificationsOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
