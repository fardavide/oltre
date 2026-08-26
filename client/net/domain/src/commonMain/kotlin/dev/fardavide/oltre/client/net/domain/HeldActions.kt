package dev.fardavide.oltre.client.net.domain

import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.AlertCategory
import dev.fardavide.oltre.core.AlertDelivery
import dev.fardavide.oltre.core.AlertMode
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.WatchTarget
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.IdempotencyKey
import dev.fardavide.oltre.protocol.OfflineRule
import dev.fardavide.oltre.protocol.VerbEnvelope
import dev.fardavide.oltre.protocol.offlineRule

// **What a ladder's held state is**, and the one control that needs more than a key to draw itself.
// Every other held control is a thing that was asked for once — an upgrade, a bell — so *held* is the
// whole of what there is to say. A ladder has two facts at once: the stop the server is on, which it
// already knows, and the stop that was asked for, which is here. Two lit chips is exactly true.
data class HeldStop<out T>(val asked: T, val key: IdempotencyKey)

// **The outbox read as a set of controls rather than as a list of verbs**, and it exists so that eight
// mappers ask one question each instead of eight of them walking the same list in eight slightly
// different ways.
//
// **A held control has a key, and the key is why every answer is one rather than a `Boolean`.** The
// design turns the action button into an amber ghost reading `Held`, and that ghost is still a target:
// pressing it withdraws the request. `Outbox.withdraw` takes an `IdempotencyKey`, so the thing that
// draws the ghost is also the thing that has to be able to name what pressing it would take back — a
// `Boolean` here would mean re-walking the queue at the tap, in a second place, with a second chance
// to get the match wrong.
//
// **Galaxy-touching verbs are dropped on the way in.** They refuse at the tap and are never written,
// so in practice the file cannot hold one — but a queue that somehow did must not turn a world row
// amber, because amber promises the tap will happen when the network is back and `LOOK_DONT_ACT` is
// this game refusing to promise exactly that. Reading `offlineRule` rather than listing the two verbs
// is the same discipline `Outbox.queue` keeps: the split is stated once, in `:protocol`, and never
// re-derived.
class HeldActions(queued: List<VerbEnvelope>) {

    private val outstanding = queued.filter { it.verb.offlineRule == OfflineRule.QUEUE_AND_VALIDATE }

    // **Envelopes rather than controls**, which is what the chrome line means by *3 actions held*: a
    // player who reads it can go and count three ambers. A control that somehow carried two would be
    // two things the server has not answered, and saying "2" would be under-reporting the queue.
    val count: Int get() = outstanding.size

    // The three verbs with no subject at all, so there is one answer rather than a lookup.
    val flightAlert: IdempotencyKey? get() = keyOf { it is ClientVerb.ToggleFlightAlerts }

    val alertMode: HeldStop<AlertMode>? get() = stopOf<ClientVerb.SetAlertMode, AlertMode> { it.mode }

    val alertDelivery: HeldStop<AlertDelivery>?
        get() = stopOf<ClientVerb.SetAlertDelivery, AlertDelivery> { it.delivery }

    fun upgrade(building: BuildingType): IdempotencyKey? =
        keyOf { it is ClientVerb.StartUpgrade && it.building == building }

    fun research(technology: Technology): IdempotencyKey? =
        keyOf { it is ClientVerb.StartResearch && it.technology == technology }

    fun adaptation(technology: AdaptationTechnology): IdempotencyKey? =
        keyOf { it is ClientVerb.StartAdaptation && it.technology == technology }

    // **The card is one hull type and the verb is a whole manifest**, so the question a card can
    // actually ask is whether the order it would place is outstanding. `buildShips` is called with
    // `Ships.of(type, 1)` from every finger in the app today, but the verb carries what `core` takes
    // and a manifest is what that is — so this matches on membership rather than on equality.
    //
    // Membership is enough because `Ships` refuses a count that is not positive at construction, so
    // a key that is present is an order for at least one. A guard here would be dead code asserting
    // something the type has already made unrepresentable.
    fun build(ship: ShipType): IdempotencyKey? =
        keyOf { it is ClientVerb.BuildShips && ship in it.ships.counts }

    fun watch(target: WatchTarget): IdempotencyKey? =
        keyOf { it is ClientVerb.ToggleAlert && it.target == target }

    fun hullAlert(ship: ShipType): IdempotencyKey? =
        keyOf { it is ClientVerb.CycleHullAlert && it.ship == ship }

    fun alertCategory(category: AlertCategory): IdempotencyKey? =
        keyOf { it is ClientVerb.ToggleAlertCategory && it.category == category }

    // **The newest, and the search runs backwards to find it.** A tap on a held control withdraws
    // rather than queueing a second verb, so two envelopes for one control cannot come from a finger —
    // but a file written by an older build can hold them, and the one a withdrawal has to reach is the
    // one that would be sent last.
    private inline fun keyOf(matches: (ClientVerb) -> Boolean): IdempotencyKey? =
        outstanding.lastOrNull { matches(it.verb) }?.idempotencyKey

    private inline fun <reified V : ClientVerb, T> stopOf(stop: (V) -> T): HeldStop<T>? =
        outstanding.lastOrNull { it.verb is V }
            ?.let { HeldStop(stop(it.verb as V), it.idempotencyKey) }

    companion object {

        // A colony with signal, and the default every frame that is not about the queue is drawn with.
        val NONE: HeldActions = HeldActions(emptyList())
    }
}
