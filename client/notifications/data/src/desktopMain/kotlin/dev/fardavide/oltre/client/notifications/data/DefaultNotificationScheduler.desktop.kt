package dev.fardavide.oltre.client.notifications.data

// Desktop is the dev loop, and the dev loop has the app open — an alert raised while you are
// looking at the countdown it describes is noise. The schedule is printed instead, which is the
// thing that is actually worth checking on desktop: that the right alerts, at the right
// instants, are being derived from the state.
//
// Rejected: java.awt.SystemTray. Firing at a future instant means holding a timer for the whole
// wait, which is precisely the mechanism this game is built to avoid, in exchange for a toast on
// the one platform that does not need one.
actual fun defaultNotificationScheduler(): NotificationScheduler = PrintingNotificationScheduler()

private class PrintingNotificationScheduler : NotificationScheduler {

    override suspend fun replaceAll(notifications: List<LocalNotification>) {
        if (notifications.isEmpty()) {
            println("notifications: nothing pending")
            return
        }
        println("notifications: ${notifications.size} pending")
        for (notification in notifications) {
            println("  ${notification.at} — ${notification.title}")
        }
    }
}
