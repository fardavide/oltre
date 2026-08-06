package dev.fardavide.oltre.client.notifications.data

import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.time.Clock

// iPhone is the delivery target and iOS runs nothing in the background, so this is the entire
// mechanism by which the game reaches a player who is not looking at it.
actual fun defaultNotificationScheduler(): NotificationScheduler = IosNotificationScheduler()

private class IosNotificationScheduler : NotificationScheduler {

    private var authorizationRequested = false

    override suspend fun replaceAll(notifications: List<LocalNotification>) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        requestAuthorizationOnce(center)
        // Clearing first is what makes the set a replacement. Every pending request in this app
        // was put there by this method, so dropping all of them and re-adding the current set
        // needs no record of what was scheduled last time.
        center.removeAllPendingNotificationRequests()

        // The one clock read on this path. UNTimeIntervalNotificationTrigger takes a delay
        // rather than a date, so the absolute instants the simulation computed are converted
        // here, at the platform edge, and nowhere above it.
        val now = Clock.System.now()
        for (notification in notifications) {
            val seconds = (notification.at - now).inWholeMilliseconds / MILLISECONDS_PER_SECOND
            // iOS rejects a non-positive interval outright. `notificationsFor` has already
            // dropped anything due, so this only catches the sub-millisecond edge.
            if (seconds <= 0.0) continue
            val content = UNMutableNotificationContent().apply {
                setTitle(notification.title)
                setBody(notification.body)
            }
            center.addNotificationRequest(
                UNNotificationRequest.requestWithIdentifier(
                    notification.id,
                    content,
                    UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, false),
                ),
                withCompletionHandler = null,
            )
        }
    }

    // Asked once per launch, on the first sync — which is the app's first frame, when the
    // colony is loaded. Deliberately not deferred to a "better moment": the alerts *are* the
    // game on this platform, so a player who declines has understood what they declined.
    // Re-asking is pointless anyway; after the first answer iOS never shows the prompt again.
    private fun requestAuthorizationOnce(center: UNUserNotificationCenter) {
        if (authorizationRequested) return
        authorizationRequested = true
        center.requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge,
        ) { _, _ ->
            // Nothing to do either way. There is no surface to report a refusal on, and the
            // game is fully playable without alerts — they only decide whether you find out
            // that a build finished before you next open it.
        }
    }

    private companion object {
        const val MILLISECONDS_PER_SECOND = 1_000.0
    }
}
