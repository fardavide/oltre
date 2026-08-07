package dev.fardavide.oltre.client.notifications.data

import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.Coordinates
import dev.fardavide.oltre.core.FutureEvent
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.Technology
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
    is FutureEvent.ResearchCompletes -> LocalNotification(
        // Only one project runs at a time, so the technology is not needed to keep this unique —
        // it is here because an id derived from the thing it is about is what makes replacing the
        // whole set idempotent, and because a second slot would otherwise silently collide.
        id = "research-${technology.name}",
        title = "${technology.displayName()} reached level ${toLevel.value}",
        body = "The lab is free — pick what your empire researches next.",
        at = at,
    )
    is FutureEvent.AdaptationCompletes -> LocalNotification(
        // A separate id space from research even though the two share one slot, because the id is
        // derived from the thing it is about — and the two branches are not the same thing. Sharing
        // "research-…" would also collide the day a ladder and a technology are named alike.
        id = "adaptation-${technology.name}",
        title = "${technology.displayName()} reached level ${toLevel.value}",
        // The only notification in the game that is about somewhere else. What changed is not the
        // colony but which worlds it could stand on, so the sentence points at the Galaxy tab.
        body = "Worlds you could not settle may have opened up — check the galaxy.",
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

private fun Technology.displayName(): String = when (this) {
    Technology.PHOTOVOLTAICS -> "Photovoltaics"
    Technology.EXTRACTION -> "Extraction"
    Technology.ENRICHMENT -> "Enrichment"
}

// Spelled out in full, with the word the Galaxy screen's blocked rows drop to save eleven
// characters they do not have. A lock screen has the room, and "Gravitic reached level 3" on its
// own does not say what kind of thing climbed.
private fun AdaptationTechnology.displayName(): String = when (this) {
    AdaptationTechnology.THERMAL -> "Thermal Adaptation"
    AdaptationTechnology.GRAVITIC -> "Gravitic Adaptation"
    AdaptationTechnology.ATMOSPHERIC -> "Atmospheric Adaptation"
}

private fun Coordinates.label(): String = "[$galaxy:$system:$position]"
