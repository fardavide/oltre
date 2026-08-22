package dev.fardavide.oltre.core

import kotlinx.serialization.Serializable

// **The first control in the game with three states**, and the reason is the shape of the thing it
// is about rather than a wish for one more setting.
//
// Every other alert points at a job there is one of: a facility builds once, a slot holds one
// project, a probe flies to one system. The square on those rows asks one question because there is
// only one — *tell me when this lands*. A hull card stands over a **queue**, and Davide's call of
// 2026-08-22 is that the queue is asked about per hull type: an order of four skiffs is one decision
// with two reasonable answers, and the card cannot know which the player wants. So it offers both,
// and the second tap is what asks the other one.
//
// **Off is the absence of an entry rather than a third constant.** A `HullAlert.NONE` would be a
// value every reader has to remember to ignore, and the notification layer's gate is exactly a
// lookup that must fail for a card nobody tapped.
@Serializable
enum class HullAlert {

    // One alert, at the instant the *last* queued hull of that type lands — "your three skiffs are
    // built" is not true until the third one is, which is the same rule the upgrade group already
    // fires by.
    WHEN_ALL_DONE,

    // One alert per hull. Davide's call, 2026-08-22: this **replaces** the all-done alert rather
    // than adding to it. The last hull's own alert fires at the instant the order finishes, so a
    // closing "and that is all of them" would be a second buzz saying what the first already said.
    EACH_HULL,
}

// **A pointer at a hull type, never at a job**, for the reason `WatchTarget` is a pointer at a row:
// both ends of the thing being waited for move while the ask is set. Order another skiff and the
// instant slides later; nothing is stored but the question.
//
// One verb rather than three, and the state picks — `toggleAlert`'s own argument, and it has more
// force here: the screen renders a snapshot and the tap is applied to a state that has been advanced
// since, so the queue this decides against is the one the action will act on rather than the one that
// was drawn.
//
// **A card with nothing of its type in the yard is left alone.** There is no control on an idle card
// — the absence of one, the way this app says "there is nothing to wait for" everywhere else — but
// core says so as well rather than trusting the screen, because the last hull may have landed between
// the draw and the tap.
fun cycleHullAlert(state: GameState, ship: ShipType): GameState {
    if (state.yard.none { it.ship == ship }) return state
    val next = when (state.hullAlerts[ship]) {
        null -> HullAlert.WHEN_ALL_DONE
        HullAlert.WHEN_ALL_DONE -> HullAlert.EACH_HULL
        HullAlert.EACH_HULL -> null
    }
    return state.copy(
        hullAlerts = if (next == null) state.hullAlerts - ship else state.hullAlerts + (ship to next),
    )
}

// Spent by the order it was about, exactly as a subscription is spent by its job — see
// `withoutSpentWatch`, whose comment is the argument for both. Nothing is written to the event log:
// the hulls that cleared it have `ShipsBuilt` entries of their own already, and the ask itself was
// never a thing that happened.
//
// Rebuilt by filtering the map rather than by noticing which type emptied, because *"the yard no
// longer holds one"* is the whole rule and a per-delivery hook would have to be right about every
// path that removes a job.
internal fun GameState.withoutFinishedHullAlerts(): GameState {
    val queued = yard.mapTo(mutableSetOf()) { it.ship }
    val still = hullAlerts.filterKeys { it in queued }
    return if (still.size == hullAlerts.size) this else copy(hullAlerts = still)
}
