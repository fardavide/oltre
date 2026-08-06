package dev.fardavide.oltre.client.notifications.data

import kotlin.time.Instant

// One alert the platform is asked to raise at a future instant, while the app is not running.
// Absolute rather than a delay on purpose: the instant is the thing the simulation computed, and
// a platform that wants "in N seconds" converts at the edge where the clock is read anyway.
data class LocalNotification(
    val id: String,
    val title: String,
    val body: String,
    val at: Instant,
)

// The impure edge of the check-in loop: the platform's local-notification service, with nothing
// game-shaped in it. Everything above is pure, so tests swap in a fake and never touch a device.
interface NotificationScheduler {

    // The whole pending set is replaced, never amended. The schedule is *derived* from game
    // state, exactly like the save is, so recomputing it wholesale is the only way it can stay
    // truthful: a build that finished, a fleet that landed and (later) an order that was
    // cancelled all disappear by simply not being in the new list, with no cancellation
    // bookkeeping to get wrong.
    //
    // Best effort, like a save write. There is no surface in the game to report a refused
    // permission to, and the next transition schedules the whole set again.
    suspend fun replaceAll(notifications: List<LocalNotification>)
}

// The platform's notification service. Called once, at the composition root.
expect fun defaultNotificationScheduler(): NotificationScheduler
