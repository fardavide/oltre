package dev.fardavide.oltre.client.notifications.data

// Android needs a Context to reach AlarmManager and NotificationManager, and to hold the
// POST_NOTIFICATIONS permission it has required since API 33 — none of which exists without an
// app module (see `.claude/docs/architecture.md`). Doing nothing is the honest stand-in: the
// alternative is a half-implementation that compiles, silently schedules nothing, and looks
// finished.
//
// This file goes away with the `androidApp` module, which is where the permission prompt and the
// exact-alarm decision belong.
actual fun defaultNotificationScheduler(): NotificationScheduler = NoNotificationScheduler

private object NoNotificationScheduler : NotificationScheduler {

    override suspend fun replaceAll(notifications: List<LocalNotification>) = Unit
}
