package dev.fardavide.oltre.client.notifications.data

import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.NotificationSettings
import kotlin.time.Instant

// **Both entry points refuse a missing `settings`**, deliberately — a caller that forgot to load the
// preferences file would otherwise compile and quietly announce everything the old way, with no test
// able to tell the difference. Here the same default costs nothing: a test that is not about the
// settings should not have to name them, and the ones that *are* about them pass a `settings`
// argument and say so in their name.
//
// Overloads in the test source set rather than default values on the real declarations, so the
// requirement holds everywhere except the one file that opts out of it.

internal suspend fun GameNotifications.sync(
    state: GameState,
    now: Instant,
    toRealTime: (Instant) -> Instant = { it },
) = sync(state, now, settings = NotificationSettings.DEFAULT, toRealTime = toRealTime)

internal fun notificationsFor(state: GameState, now: Instant) =
    notificationsFor(state, now, settings = NotificationSettings.DEFAULT)
