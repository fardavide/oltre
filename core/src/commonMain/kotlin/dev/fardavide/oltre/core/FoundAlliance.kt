package dev.fardavide.oltre.core

import kotlin.time.Instant

sealed interface FoundAllianceResult {

    data class Paid(val state: GameState) : FoundAllianceResult

    data object InsufficientResources : FoundAllianceResult
}

// **What founding an alliance takes out of the colony, and the whole of what `core` knows about
// founding one.** `contribute` above is the pattern and the argument is the same one said again: the
// resources go, nothing comes back, and every fact that makes founding *possible* — whether the name
// is taken, whether this player is already in one, whether the tag is spelled the way the contract
// wants — is about other people and belongs to the server. `core` holds one colony and does not
// learn that alliances exist.
//
// **There is no `NothingOffered` arm and there must not be.** A contribution of zero is a player
// pressing a chip that sends nothing, which is a dead control; a founding price of zero is a
// *balance* choice — founding is free today in the sense that nobody has argued it should not be —
// and refusing it here would make a free founding impossible to configure rather than merely
// unwise. So an empty price is a founding that costs nothing, and the colony is untouched but for
// the line in the log, which is the truth about it.
//
// The shape is `contribute`'s: result type first, state first and `at` last, cost then construct
// then append.
fun foundAlliance(state: GameState, price: Resources, at: Instant): FoundAllianceResult {
    if (!state.resources.covers(price)) return FoundAllianceResult.InsufficientResources
    return FoundAllianceResult.Paid(
        state.copy(resources = state.resources.minus(price))
            // `logging` and never a hand-written `copy(eventLog = …)`, for the reason `contribute`
            // states: it is the only thing that pays the running experience total as well as
            // appending. That total does not move for this event, and it is `logging` rather than
            // this function that decides so — see `ExperienceBalance.awardFor`.
            .logging(Event.AllianceFounded(price = price, at = at)),
    )
}
