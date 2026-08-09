package dev.fardavide.oltre.client.notifications.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

// What an Android alarm actually does when it fires. iOS hands the notification centre a title
// and a body and the system raises them by itself; Android wakes a component instead, so this is
// the component, and it is as small as it can be — the alert's text arrived with the intent, and
// nothing here reads game state.
//
// Declared in `androidApp/src/main/AndroidManifest.xml`, like `MainActivity`: the manifest names
// classes from the modules it packages.
class NotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val body = intent.getStringExtra(EXTRA_BODY) ?: return

        val manager = context.getSystemService(NotificationManager::class.java)
        // Idempotent, and cheap. Creating it here rather than only at startup means an alarm that
        // fires into a process started for this broadcast alone still has a channel to post to.
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = CHANNEL_DESCRIPTION },
        )

        manager.notify(
            id.hashCode(),
            Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .setContentIntent(openTheGame(context))
                .build(),
        )
    }

    // Tapping the alert opens the game. Resolved through the package manager rather than by
    // naming the Activity: that class lives in `:client:shell`, which this module cannot see and
    // should not — a data module knowing the name of a screen is the wrong direction entirely.
    private fun openTheGame(context: Context): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE)
    }

    private companion object {
        const val CHANNEL_ID = "oltre-colony-events"

        // Shown in the system's notification settings, so they are read by a player rather than
        // by a developer. PLACEHOLDER, like the alert copy in `GameNotifications` and for the
        // same reason: what the game says to a player is Davide's.
        const val CHANNEL_NAME = "Colony events"
        const val CHANNEL_DESCRIPTION =
            "Tells you when a build or a research project has finished, and when a fleet lands."
    }
}
