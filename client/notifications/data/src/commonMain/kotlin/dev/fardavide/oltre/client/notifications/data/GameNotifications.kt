package dev.fardavide.oltre.client.notifications.data

import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Coordinates
import dev.fardavide.oltre.core.FutureEvent
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.futureEvents
import kotlin.time.Instant

// The check-in loop, and on iPhone the only one there can be: iOS runs nothing in the
// background, so the game's single way of saying "something happened" is an alert booked in
// advance at an instant the simulation already knows.
//
// Kept honest by deriving the whole set from state on every discrete transition — the same rule
// that decides when to write the save. Nothing is ever amended, so a build that completed, a
// fleet that landed, or a colony reloaded from a different save can never leave a stale alert
// behind to fire about something that is no longer true.
class GameNotifications(private val scheduler: NotificationScheduler) {

    suspend fun sync(state: GameState, now: Instant) {
        scheduler.replaceAll(notificationsFor(state, now))
    }
}

internal fun notificationsFor(state: GameState, now: Instant): List<LocalNotification> =
    futureEvents(state)
        // core hands back everything still in flight; an event at or before `now` is either
        // about to be applied by `advance` or already has been, and either way an alert for it
        // would fire in the past. The platforms reject that anyway — dropping it here means one
        // rule instead of one per platform.
        .filter { it.at > now }
        .map { it.toNotification() }

private fun FutureEvent.toNotification(): LocalNotification = when (this) {
    is FutureEvent.BuildCompletes -> LocalNotification(
        // Stable and derived from the thing it is about: the same colony always produces the
        // same alerts, which is what makes replacing the set idempotent.
        id = "build-${building.name}",
        title = "${building.displayName()} reached level ${toLevel.value}",
        body = "Construction is complete — pick what your colony builds next.",
        at = at,
    )
    is FutureEvent.FleetArrives -> LocalNotification(
        id = "fleet-arrival",
        title = "Your fleet has landed",
        body = "The cargo from ${origin.label()} is in your stores.",
        at = at,
    )
}

// PLACEHOLDER copy. What a notification says is player-facing content and therefore Davide's
// call; these say the one thing a check-in alert has to say — what happened, and that there is
// a decision waiting — and stay short enough to read on a lock screen.
//
// Written out in full rather than reusing the Colony screen's names, which abbreviate
// ("Deuterium Synth.") to fit a row that a notification does not have.
private fun BuildingType.displayName(): String = when (this) {
    BuildingType.METAL_MINE -> "Metal Mine"
    BuildingType.CRYSTAL_MINE -> "Crystal Mine"
    BuildingType.DEUTERIUM_SYNTHESIZER -> "Deuterium Synthesizer"
    BuildingType.SOLAR_PLANT -> "Solar Plant"
    BuildingType.ROBOTICS_FACTORY -> "Robotics Factory"
    BuildingType.NANITE_FACTORY -> "Nanite Factory"
}

private fun Coordinates.label(): String = "[$galaxy:$system:$position]"
