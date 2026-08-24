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
                // **The closest iOS gets to Android's replace, and it is not the same thing.**
                // Android's tray id genuinely overwrites what is showing; here a thread identifier
                // only collapses the run into one stack in Notification Centre, newest on top.
                //
                // There is no way to do better while the app is not running. Replacing a *delivered*
                // notification needs a new request under the same identifier, and two *pending*
                // requests cannot share one — the second would silently cancel the first, which
                // would leave a colony holding only the last alert of the day. iOS runs nothing in
                // the background, so nothing can retract on delivery either. Flagged rather than
                // hidden: on iPhone `One in total` is one stack, not one notification.
                setThreadIdentifier(notification.collapseId)
            }
            // Positional throughout: Kotlin rejects named arguments for Objective-C functions.
            center.addNotificationRequest(
                UNNotificationRequest.requestWithIdentifier(
                    notification.id,
                    content,
                    UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, false),
                ),
                null,
            )
        }
    }

    // **The one thing iOS will let this game do about a tray it cannot update.** `One in total`
    // promises a single notification kept current and on iPhone delivers a stack, because replacing a
    // *delivered* notification needs a request under the same identifier and nothing here runs while
    // the app is shut — see #120. Clearing on open does not fix that; it bounds it, to one check-in's
    // worth rather than every alert since the app was last opened.
    //
    // Delivered only. `removeAllPendingNotificationRequests` is `replaceAll`'s business and calling it
    // here would cancel the schedule the launch is about to re-derive.
    override suspend fun clearDelivered() {
        UNUserNotificationCenter.currentNotificationCenter().removeAllDeliveredNotifications()
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
