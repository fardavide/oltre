package dev.fardavide.oltre.server

import dev.fardavide.oltre.core.BuildShipsResult
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.StartAdaptationResult
import dev.fardavide.oltre.core.StartResearchResult
import dev.fardavide.oltre.core.StartRunResult
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.buildShips
import dev.fardavide.oltre.core.cycleHullAlert
import dev.fardavide.oltre.core.setAlertDelivery
import dev.fardavide.oltre.core.setAlertMode
import dev.fardavide.oltre.core.startAdaptation
import dev.fardavide.oltre.core.startResearch
import dev.fardavide.oltre.core.startRun
import dev.fardavide.oltre.core.startSurvey
import dev.fardavide.oltre.core.startUpgrade
import dev.fardavide.oltre.core.toggleAlert
import dev.fardavide.oltre.core.toggleAlertCategory
import dev.fardavide.oltre.core.toggleFlightAlerts
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.VerbRefusal
import kotlin.time.Instant

// **What `core` said, in the one shape the replay can carry.** Six of the twelve verbs return a
// result type that can refuse and six return a bare `GameState`, so a loop over envelopes would
// otherwise have to know which kind each verb is — which is exactly the knowledge `ClientVerb`
// exists to hold on its own behalf.
//
// Two members and no third. A verb either moved the colony or it did not: there is no "partly", and
// an error is not representable here because `core` throws nothing a replay could catch.
internal sealed interface VerbOutcome {

    data class Accepted(val state: GameState) : VerbOutcome

    data class Refused(val refusal: VerbRefusal) : VerbOutcome
}

// **The twelve arms, and a `when` with no `else`** — the second half of the guard `ClientVerb`
// itself sets. A thirteenth verb cannot reach this file without somebody deciding which `core`
// function it is, and a verb missing from here would be a tap that works on the phone, updates the
// screen, and comes back undone on the next sync.
//
// The state handed in is already advanced to `at`, which is `GameSession.acting`'s order and is
// load-bearing for the same reason: applying a verb to a stale colony spends resources it has not
// accrued yet. Note it is *not* `GameSession.alerting`'s order — the client toggles a bell before
// advancing because it applies the tap at `now` rather than at the instant of the tap, and here the
// instant of the tap is exactly what the envelope carries. Replaying at the claimed instant asks the
// question against the state the player was actually looking at, which is what that workaround was
// trying to approximate.
internal fun applyVerb(verb: ClientVerb, state: GameState, at: Instant): VerbOutcome = when (verb) {
    is ClientVerb.StartUpgrade -> startUpgrade(state, verb.building, at).outcome()
    is ClientVerb.StartResearch -> startResearch(state, verb.technology, at).outcome()
    is ClientVerb.StartAdaptation -> startAdaptation(state, verb.technology, at).outcome()
    is ClientVerb.BuildShips -> buildShips(state, verb.ships, at).outcome()
    is ClientVerb.StartRun -> startRun(
        state = state,
        target = verb.target,
        gathering = verb.gathering,
        ships = verb.ships,
        window = verb.window,
        at = at,
    ).outcome()

    is ClientVerb.StartSurvey -> startSurvey(state, verb.target, at).outcome()

    // The six below cannot refuse at all — they return a bare `GameState`, so there is no result to
    // inspect and no member of `VerbRefusal` that comes from any of them. `Accepted` is not an
    // optimism here; it is the whole of what these functions can answer.
    is ClientVerb.ToggleAlert -> VerbOutcome.Accepted(toggleAlert(state, verb.target))
    is ClientVerb.CycleHullAlert -> VerbOutcome.Accepted(cycleHullAlert(state, verb.ship))
    ClientVerb.ToggleFlightAlerts -> VerbOutcome.Accepted(toggleFlightAlerts(state))
    is ClientVerb.SetAlertMode -> VerbOutcome.Accepted(setAlertMode(state, verb.mode))
    is ClientVerb.ToggleAlertCategory -> VerbOutcome.Accepted(toggleAlertCategory(state, verb.category))
    is ClientVerb.SetAlertDelivery -> VerbOutcome.Accepted(setAlertDelivery(state, verb.delivery))
}

// ── The six flattenings ───────────────────────────────────────────────────────────────────────
//
// `VerbRefusal` is these six types collapsed into fifteen constants, and each function below is one
// half of that collapse. Exhaustive `when`s with no `else`, so a refusal added to a `core` result
// type fails to compile here rather than arriving at the client as a success.
//
// What the flattening deliberately loses is precision — `INSUFFICIENT_RESOURCES` reads the same
// whichever of five verbs produced it — and what it must never lose is accuracy, because the
// sentence a player is shown is chosen from this constant. The verb itself rides on the envelope
// the reason is attached to, so nothing is unrecoverable.

internal fun StartUpgradeResult.outcome(): VerbOutcome = when (this) {
    is StartUpgradeResult.Started -> VerbOutcome.Accepted(state)
    StartUpgradeResult.AlreadyUpgrading -> VerbOutcome.Refused(VerbRefusal.ALREADY_UPGRADING)
    StartUpgradeResult.InsufficientResources -> VerbOutcome.Refused(VerbRefusal.INSUFFICIENT_RESOURCES)
    StartUpgradeResult.RequirementsNotMet -> VerbOutcome.Refused(VerbRefusal.REQUIREMENTS_NOT_MET)
}

internal fun StartResearchResult.outcome(): VerbOutcome = when (this) {
    is StartResearchResult.Started -> VerbOutcome.Accepted(state)
    StartResearchResult.SlotBusy -> VerbOutcome.Refused(VerbRefusal.SLOT_BUSY)
    StartResearchResult.InsufficientResources -> VerbOutcome.Refused(VerbRefusal.INSUFFICIENT_RESOURCES)
    StartResearchResult.RequirementsNotMet -> VerbOutcome.Refused(VerbRefusal.REQUIREMENTS_NOT_MET)
}

internal fun StartAdaptationResult.outcome(): VerbOutcome = when (this) {
    is StartAdaptationResult.Started -> VerbOutcome.Accepted(state)
    StartAdaptationResult.SlotBusy -> VerbOutcome.Refused(VerbRefusal.SLOT_BUSY)
    StartAdaptationResult.InsufficientResources -> VerbOutcome.Refused(VerbRefusal.INSUFFICIENT_RESOURCES)
    StartAdaptationResult.RequirementsNotMet -> VerbOutcome.Refused(VerbRefusal.REQUIREMENTS_NOT_MET)
}

internal fun BuildShipsResult.outcome(): VerbOutcome = when (this) {
    is BuildShipsResult.Started -> VerbOutcome.Accepted(state)
    BuildShipsResult.NothingToBuild -> VerbOutcome.Refused(VerbRefusal.NOTHING_TO_BUILD)
    BuildShipsResult.NotForSale -> VerbOutcome.Refused(VerbRefusal.NOT_FOR_SALE)
    BuildShipsResult.InsufficientResources -> VerbOutcome.Refused(VerbRefusal.INSUFFICIENT_RESOURCES)
}

internal fun StartRunResult.outcome(): VerbOutcome = when (this) {
    is StartRunResult.Started -> VerbOutcome.Accepted(state)
    StartRunResult.Unsurveyed -> VerbOutcome.Refused(VerbRefusal.UNSURVEYED)
    StartRunResult.NotAValidTarget -> VerbOutcome.Refused(VerbRefusal.NOT_A_VALID_TARGET)
    StartRunResult.NoSuchShips -> VerbOutcome.Refused(VerbRefusal.NO_SUCH_SHIPS)
    StartRunResult.NotAGatheringHull -> VerbOutcome.Refused(VerbRefusal.NOT_A_GATHERING_HULL)
    StartRunResult.WindowTooShort -> VerbOutcome.Refused(VerbRefusal.WINDOW_TOO_SHORT)
    StartRunResult.Depleted -> VerbOutcome.Refused(VerbRefusal.DEPLETED)
}

internal fun StartSurveyResult.outcome(): VerbOutcome = when (this) {
    is StartSurveyResult.Started -> VerbOutcome.Accepted(state)
    StartSurveyResult.AlreadySurveying -> VerbOutcome.Refused(VerbRefusal.ALREADY_SURVEYING)
    StartSurveyResult.AlreadySurveyed -> VerbOutcome.Refused(VerbRefusal.ALREADY_SURVEYED)
    StartSurveyResult.NoIdleScout -> VerbOutcome.Refused(VerbRefusal.NO_IDLE_SCOUT)
    StartSurveyResult.InsufficientResources -> VerbOutcome.Refused(VerbRefusal.INSUFFICIENT_RESOURCES)
}
