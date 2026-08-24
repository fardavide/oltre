package dev.fardavide.oltre.client.notifications.data

// Handwritten, per the repo's no-mocking-framework rule. Keeps the last set it was handed —
// which, since the set is replaced wholesale, is the entire state a scheduler has.
internal class FakeNotificationScheduler : NotificationScheduler {

    var scheduled: List<LocalNotification> = emptyList()
        private set

    var replaceCount: Int = 0
        private set

    var clearCount: Int = 0
        private set

    override suspend fun replaceAll(notifications: List<LocalNotification>) {
        scheduled = notifications
        replaceCount++
    }

    // Counted rather than recorded as a flag: the number is what the rule is about — a clear must
    // happen when the app opens and must *not* ride along with a sync.
    override suspend fun clearDelivered() {
        clearCount++
    }
}
