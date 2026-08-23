package dev.fardavide.oltre.client.notifications.data

import dev.fardavide.oltre.client.design.text.TextRes
import kotlin.time.Instant

// One alert the platform is asked to raise at a future instant, while the app is not running.
// Absolute rather than a delay on purpose: the instant is the thing the simulation computed, and
// a platform that wants "in N seconds" converts at the edge where the clock is read anyway.
data class LocalNotification(
    val id: String,
    // **What the tray treats as the same notification**, which is not the same question as what the
    // scheduler treats as the same booking. Every alert in the game answers both with its own id —
    // except `AlertDelivery.TOTAL`, where the whole point is that a colony holds one notification and
    // each landing brings it up to date rather than adding a second.
    //
    // Two fields rather than one because the platforms will not allow one: two *pending* requests
    // cannot share an identifier on either platform — the second would silently replace the first
    // while it was still waiting — so the bookings must differ even when what they display must not.
    //
    // Android honours it exactly: `NotificationManager.notify` with the same tray id replaces what is
    // there. **iOS cannot**, and that is a platform limit rather than a choice — it runs nothing in
    // the background, so nothing can retract a notification it has already delivered. There it
    // becomes the thread identifier, which collapses the run into one stack in Notification Centre.
    // See `DefaultNotificationScheduler.ios.kt`.
    val collapseId: String,
    val title: String,
    val body: String,
    val at: Instant,
)

// The same alert before its language is chosen. **The split is #86's, and it is the one place in
// the app where the seam is a type rather than a call**: everything the game derives is built here,
// in `TextRes`, and `GameNotifications.sync` resolves the pair the instant before it hands the set
// to the platform — which is the last moment at which a `String` is the right thing to have.
data class PendingNotification(
    val id: String,
    // The tray identity, carried through the seam with everything else. See `LocalNotification`.
    val collapseId: String,
    val title: TextRes,
    val body: TextRes,
    val at: Instant,
)

// **An alert that is its own tray entry**, which is every alert in this game but one. Named so that
// the exception is the thing a reader has to look at: only `AlertDelivery.TOTAL` builds a
// `PendingNotification` by hand, because only there does a booking display as something it is not.
internal fun pendingNotification(id: String, title: TextRes, body: TextRes, at: Instant): PendingNotification =
    PendingNotification(id = id, collapseId = id, title = title, body = body, at = at)

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
