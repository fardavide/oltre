package dev.fardavide.oltre.client.notifications.data

import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.FutureEvent
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.SystemAddress
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

    // `now` and everything `futureEvents` computes are in **game** time, which is not the same
    // clock the operating system raises alarms on the moment the debug menu skips the colony
    // forward. `toRealTime` is how the two are reconciled, and it defaults to the identity because
    // for an unskipped colony — every colony, until somebody shakes the phone — they are the same
    // clock. Without it a colony skipped four hours ahead books every alert four hours late, which
    // is the check-in loop, and on iPhone the check-in loop is the whole game.
    //
    // Passed per call rather than held, because the offset moves: the shell knows it at the instant
    // it commits, and a mapping captured at construction would be a stale one by the second skip.
    suspend fun sync(state: GameState, now: Instant, toRealTime: (Instant) -> Instant = { it }) {
        // Applied *after* `notificationsFor`, and it has to be. That function drops events already
        // due and trims the far landings to iOS's 64-request ceiling, both by comparing instants —
        // decisions that must be made in the clock the simulation computed them in. The translation
        // is monotone, so it moves every alert without reordering any of them, and the set that
        // reaches the platform is the same set with a different origin.
        scheduler.replaceAll(notificationsFor(state, now).map { it.copy(at = toRealTime(it.at)) })
    }
}

// **iOS keeps only the 64 soonest-firing pending requests and silently drops the rest.** That is
// the platform's number, not a choice made here, and it is why this file has a cap at all.
//
// The eviction rule is what makes it dangerous: iOS throws away the *furthest out*, which is
// precisely where long builds and research completions live. Uncapped, a player who dispatched
// thirty probes would lose the one alert they planned their evening around and keep thirty they
// did not. The fix is not to schedule fewer things, it is to make the choice **here**, where the
// game knows which alert is worth keeping, rather than at a boundary that only knows which is
// nearest.
internal const val IOS_PENDING_REQUEST_LIMIT: Int = 64

internal fun notificationsFor(state: GameState, now: Instant): List<LocalNotification> {
    val pending = futureEvents(state)
        // core hands back everything still in flight; an event at or before `now` is either
        // about to be applied by `advance` or already has been, and either way an alert for it
        // would fire in the past. The platforms reject that anyway — dropping it here means one
        // rule instead of one per platform.
        .filter { it.at > now }

    // **Two kinds are now unbounded, not one.** Six facilities and one research slot are bounded by
    // the model — seven at the ceiling, and none of them can ever be the thing that overflows. Probes
    // were the only kind that ran in parallel with no cap; fleet runs are the second, so the
    // partition has to name both or `bounded.size` stops describing the protected set and the trim
    // arithmetic quietly under-counts.
    //
    // **The trim order is a content decision** and it is the sheet's proposal rather than a settled
    // one: protect the model-bounded seven, then returns, then probe landings — because a return
    // carries resources that a full store can void, and a probe carries information that does not
    // spoil. Davide's to overrule.
    val (unbounded, bounded) = pending.partition {
        it is FutureEvent.SurveyLands || it is FutureEvent.FleetReturns
    }
    val (returns, landings) = unbounded.partition { it is FutureEvent.FleetReturns }

    // Trimmed from the far end, keeping the soonest. Two reasons, and the second is the one that
    // makes this safe: the near landings are the ones that will actually fire before the player
    // next opens the app, and every alert that fires causes a transition that re-derives this whole
    // set — so a far landing dropped today is re-booked long before it was due. Keeping the far
    // ones instead would drop alerts that nothing would ever come back for.
    val keptReturns = returns.take((IOS_PENDING_REQUEST_LIMIT - bounded.size).coerceAtLeast(0)).toSet()
    val kept = keptReturns +
        landings.take((IOS_PENDING_REQUEST_LIMIT - bounded.size - keptReturns.size).coerceAtLeast(0))

    // Filtered out of the original list rather than reassembled from the halves, so `futureEvents`'
    // ordering survives intact — including its tie-breaks, which say a landing sorts before a fleet
    // return at a shared instant. Concatenating the halves would put the return first and quietly
    // disagree with the log it is supposed to predict.
    return pending
        .filter { it in bounded || it in kept }
        // Belt and braces, and a no-op today: the bounded kinds top out at nine, so `kept` is
        // always sized to land exactly on the limit. It is here so the one promise this function
        // makes to the platform is enforced on the way out rather than inferred from the arithmetic
        // above.
        .take(IOS_PENDING_REQUEST_LIMIT)
        .map { it.toNotification() }
}

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
    is FutureEvent.SurveyLands -> LocalNotification(
        // The one id that has to carry its subject to stay unique: probes run in parallel with no
        // cap, so a colony can hold thirty of these at once where it holds one research and at most
        // six builds. Derived from the target for the same reason all of them are — it is what
        // makes replacing the whole set idempotent.
        id = "survey-${target.galaxy}-${target.system}",
        title = "Your probe reached ${target.label()}",
        body = charted(worldsFound = worldsFound, settleable = settleable),
        at = at,
    )
    is FutureEvent.FleetReturns -> LocalNotification(
        // **This was the constant string `"fleet-arrival"`, and that was a latent defect that the
        // fleet slice turns into a live one.** A colony could only ever hold one returning fleet, so
        // one id was unique by construction; runs are parallel and uncapped, so two landing at once
        // would collide into a single alert and one would silently vanish. Derived from the run —
        // its target and the instant it left — for the same reason every other id here is derived
        // from its subject: that is what makes replacing the whole set idempotent.
        id = "run-${target.galaxy}-${target.system}-${target.slot}-${dispatchedAt.toEpochMilliseconds()}",
        title = "Your ships are home",
        body = "The cargo from ${target.label()} is in your stores.",
        at = at,
    )
}

// PLACEHOLDER copy, and the two strings are the design rather than a formatting convenience.
//
// **The common one is the second.** Round 9 measured ~60 dispatches to see one settleable world, so
// an alert that only ever counted worlds would read as a payoff nearly every time it fired — and
// the one the verb exists for would look exactly like the fifty-nine that were not. Saying "none"
// plainly is what makes "1 settleable" mean anything when it finally arrives.
//
// **The words are the card's own, deliberately.** The Galaxy screen's landing footer says "none
// settleable" and this says "none settleable", off the same count — because the first version of
// this said "5 worth a look" about a landing whose card read "none settleable", and a game
// contradicting itself between the lock screen and the app is the worst failure a notification has
// available to it. See `FutureEvent.SurveyLands.settleable`.
//
// It also has to be a sentence a player is happy to *miss*, which is the constraint Davide set on
// this whole loop: nothing here asks them to open anything or implies that waiting cost them
// something. A probe that found nothing is a reading they bought, not a failure they slept through.
//
// Zero worlds is not a case: whether a slot holds a world is charted free and galaxy-wide, so
// `startSurvey` refuses a starless system outright rather than selling a flight to one.
private fun charted(worldsFound: Int, settleable: Int): String {
    val worlds = if (worldsFound == 1) "1 world" else "$worldsFound worlds"
    return when (settleable) {
        0 -> "$worlds charted, none settleable."
        else -> "$worlds charted, $settleable settleable."
    }
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

// A world, brackets and all — and now the bounded `GalaxyCoordinate` rather than the unbounded twin
// it replaced, so a label can no longer be written for an address that is off the map.
private fun GalaxyCoordinate.label(): String = "[$galaxy:$system:$slot]"

// No slot and no brackets: a probe is aimed at a star, not at a world, and the Galaxy screen's own
// header writes a system the same way — bare, because there is nothing for a bracket to separate it
// from.
private fun SystemAddress.label(): String = "$galaxy:$system"
