package dev.fardavide.oltre.client.notifications.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlin.time.Clock

// Android needs a Context to reach AlarmManager, and there is no context-free way to get one.
// Filled once by `OltreApplication`, before any component of the app can run — the same shape
// `AndroidSaveLocation` uses, and for the same reason.
//
// The *application* context, never an Activity's: this is held for the life of the process, and
// an Activity held that long is a leaked window.
object AndroidNotificationHost {
    var context: Context? = null
}

// The Android half of the check-in loop. iOS hands the whole set to
// `UNUserNotificationCenter`, which remembers it; Android has no such register, so this schedules
// one inexact alarm per notification and keeps its own list of what it scheduled.
actual fun defaultNotificationScheduler(): NotificationScheduler = AndroidNotificationScheduler(
    requireNotNull(AndroidNotificationHost.context) {
        "AndroidNotificationHost.context must be set before the first notification sync"
    },
)

private class AndroidNotificationScheduler(private val context: Context) : NotificationScheduler {

    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val store = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    override suspend fun replaceAll(notifications: List<LocalNotification>) {
        // Replacing, not amending — the same contract iOS gets from
        // `removeAllPendingNotificationRequests`. The difference is that Android cannot be asked
        // "what is pending?", so the ids of the last set are written down and read back here.
        // Persisted rather than held in memory because the process this scheduler lived in is
        // usually long gone by the time the next sync runs.
        for (id in store.getStringSet(SCHEDULED_IDS, null).orEmpty()) cancel(id)

        // The one clock read on this path, exactly as on iOS — except that AlarmManager takes an
        // absolute instant, so the conversion the iOS side has to do (an instant into a delay)
        // does not happen here at all.
        val now = Clock.System.now()
        val scheduled = mutableSetOf<String>()
        for (notification in notifications) {
            // `notificationsFor` has already dropped anything due; this catches the millisecond
            // that may have passed since. An alarm in the past fires immediately, which would
            // announce something the player is already looking at.
            if (notification.at <= now) continue
            alarms.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                notification.at.toEpochMilliseconds(),
                pendingIntentFor(notification),
            )
            scheduled += notification.id
        }
        store.edit().putStringSet(SCHEDULED_IDS, scheduled).apply()
    }

    // `setAndAllowWhileIdle`, deliberately, and it is the one place Android is meaningfully
    // different from iOS. An *exact* alarm needs `SCHEDULE_EXACT_ALARM`, which since API 33 is
    // denied by default and can only be granted by the player walking into system settings —
    // and Play restricts the permission that avoids that to alarm clocks and timers, which this
    // is not. Inexact means Doze may hold an alert for minutes; a game whose sessions are five
    // minutes long and whose builds run for hours can afford that, and cannot afford a
    // permission dialog nobody would grant. Overrule if a late alert ever reads as a broken one.
    private fun pendingIntentFor(notification: LocalNotification): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            // Zero for every alarm, because identity comes from the intent's data URI below.
            // A request code derived from the id would be a hash, and two colliding hashes
            // would silently overwrite one alert with another.
            0,
            intentFor(notification.id)
                .putExtra(EXTRA_ID, notification.id)
                .putExtra(EXTRA_TITLE, notification.title)
                .putExtra(EXTRA_BODY, notification.body),
            // UPDATE_CURRENT because extras are not part of an intent's identity: without it, a
            // rescheduled alert would keep the title it had the first time it was booked.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun cancel(id: String) {
        // NO_CREATE returns null when nothing was scheduled under this id, which is the normal
        // case for an id that has already fired.
        val existing = PendingIntent.getBroadcast(
            context,
            0,
            intentFor(id),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarms.cancel(existing)
        existing.cancel()
    }

    // What makes two alarms different alarms. `Intent.filterEquals` — which is what the
    // PendingIntent register compares — reads the data URI and ignores extras, so the id has to
    // be in the URI or every notification would replace the last one.
    private fun intentFor(id: String): Intent =
        Intent(context, NotificationReceiver::class.java)
            .setData(Uri.parse("oltre://notification/$id"))

    private companion object {
        const val PREFERENCES = "oltre-notifications"
        const val SCHEDULED_IDS = "scheduled-ids"
    }
}

internal const val EXTRA_ID = "oltre.notification.id"
internal const val EXTRA_TITLE = "oltre.notification.title"
internal const val EXTRA_BODY = "oltre.notification.body"
