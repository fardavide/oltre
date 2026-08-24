package dev.fardavide.oltre.protocol

import dev.fardavide.oltre.core.AdaptationTechnology
import dev.fardavide.oltre.core.AlertCategory
import dev.fardavide.oltre.core.AlertDelivery
import dev.fardavide.oltre.core.AlertMode
import dev.fardavide.oltre.core.BuildingType
import dev.fardavide.oltre.core.GalaxyCoordinate
import dev.fardavide.oltre.core.ResourceKind
import dev.fardavide.oltre.core.ShipType
import dev.fardavide.oltre.core.Ships
import dev.fardavide.oltre.core.SystemAddress
import dev.fardavide.oltre.core.Technology
import dev.fardavide.oltre.core.WatchTarget
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration

// **Everything a player can do to a colony, as data.** One member per mutating function in `core`,
// and that correspondence is the whole specification: a verb here carries exactly the arguments its
// function takes, minus the state it is applied to and minus the instant — the state is the
// server's and the instant rides on the envelope.
//
// **Twelve, not the nine `#106` names.** The epic was written against 0.17.1 and its sentence
// *"there is nothing else in `core` that mutates a `GameState`"* was true then. 0.18 shipped the
// settings sheet, which added `setAlertMode`, `toggleAlertCategory` and `setAlertDelivery` — three
// more, all of them colony-local, all of them a control a player taps. Left out, the sheet would go
// on working on the device and change nothing on the server, which is a control that silently does
// nothing to the thing that now owns the answer.
//
// **That is the failure this file's shape exists to prevent, and it happened once already before a
// line of it was written.** A verb `core` can apply and the wire cannot carry is invisible to the
// compiler, invisible to a screenshot and invisible to a behaviour test — the tap works, the screen
// updates, and the next sync hands the colony back without it. `ClientVerbTest` is the registry
// that makes a thirteenth impossible to forget; `offlineRule` below is the second half of it, since
// a new member cannot compile without answering what it does on a train.
@Serializable
sealed interface ClientVerb {

    // The `@SerialName` values are wire identifiers from the first deploy, for the reason a
    // `BuildingType` constant is an on-disk one: a server has to keep answering the build already on
    // somebody's phone. Renaming a class is free; changing these is a wire break. Pinned by
    // `ClientVerbTest.the wire names are pinned`.

    @Serializable
    @SerialName("StartUpgrade")
    data class StartUpgrade(val building: BuildingType) : ClientVerb

    @Serializable
    @SerialName("StartResearch")
    data class StartResearch(val technology: Technology) : ClientVerb

    @Serializable
    @SerialName("StartAdaptation")
    data class StartAdaptation(val technology: AdaptationTechnology) : ClientVerb

    @Serializable
    @SerialName("BuildShips")
    data class BuildShips(val ships: Ships) : ClientVerb

    // The one verb that takes three subjects, and it carries all three for `startRun`'s own reason:
    // the target, the resource and the window are three facets of one commitment rather than three
    // decisions, and a verb that sent them separately would need a partial-commitment state that
    // nothing in this game has.
    @Serializable
    @SerialName("StartRun")
    data class StartRun(
        val target: GalaxyCoordinate,
        val gathering: ResourceKind,
        val ships: Ships,
        val window: Duration,
    ) : ClientVerb

    @Serializable
    @SerialName("StartSurvey")
    data class StartSurvey(val target: SystemAddress) : ClientVerb

    @Serializable
    @SerialName("ToggleAlert")
    data class ToggleAlert(val target: WatchTarget) : ClientVerb

    @Serializable
    @SerialName("CycleHullAlert")
    data class CycleHullAlert(val ship: ShipType) : ClientVerb

    // The three verbs below carry no subject at all, and `data object` is what says so — a class
    // with no properties would encode identically and let a caller construct two of them.
    @Serializable
    @SerialName("ToggleFlightAlerts")
    data object ToggleFlightAlerts : ClientVerb

    @Serializable
    @SerialName("SetAlertMode")
    data class SetAlertMode(val mode: AlertMode) : ClientVerb

    @Serializable
    @SerialName("ToggleAlertCategory")
    data class ToggleAlertCategory(val category: AlertCategory) : ClientVerb

    @Serializable
    @SerialName("SetAlertDelivery")
    data class SetAlertDelivery(val delivery: AlertDelivery) : ClientVerb
}

// **What a verb tapped with no signal is allowed to do** — `#106` §3, and it is on the type rather
// than in the client because it is a property of the verb and not a policy the client is free to
// choose.
enum class OfflineRule {

    // The outcome depends only on this player's colony, so the server can replay it: advance the
    // authoritative state to the claimed instant, apply the verb, and keep the result only if `core`
    // accepted it. That is implementable precisely because the server runs the same deterministic
    // `core`, and "can the backend validate this" already has an answer in the type system — every
    // one of these has a result type that can refuse.
    QUEUE_AND_VALIDATE,

    // The outcome depends on a world somebody else may now hold. **Look, don't act, from day one**,
    // even though nothing else can currently take a coordinate: the day AI empires or another player
    // can, a retroactively-validated dispatch to a world taken ten minutes ago is unresolvable, and
    // the rule would have to change *after* players had learned it. Better never to promise it.
    LOOK_DONT_ACT,
}

// A `when` with no `else`, so a thirteenth verb cannot be added without somebody deciding this. That
// is the point of putting it here rather than in the outbox: an outbox with a default arm would
// queue a galaxy-touching verb by omission, and the tell would be a player finding a world they had
// dispatched to already worked.
val ClientVerb.offlineRule: OfflineRule
    get() = when (this) {
        is ClientVerb.StartUpgrade,
        is ClientVerb.StartResearch,
        is ClientVerb.StartAdaptation,
        is ClientVerb.BuildShips,
        is ClientVerb.ToggleAlert,
        is ClientVerb.CycleHullAlert,
        ClientVerb.ToggleFlightAlerts,
        is ClientVerb.SetAlertMode,
        is ClientVerb.ToggleAlertCategory,
        is ClientVerb.SetAlertDelivery,
        -> OfflineRule.QUEUE_AND_VALIDATE

        is ClientVerb.StartRun,
        is ClientVerb.StartSurvey,
        -> OfflineRule.LOOK_DONT_ACT
    }
