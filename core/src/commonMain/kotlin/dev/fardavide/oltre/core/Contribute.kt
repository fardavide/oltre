package dev.fardavide.oltre.core

import kotlin.time.Instant

sealed interface ContributeResult {

    data class Started(val state: GameState) : ContributeResult

    data object InsufficientResources : ContributeResult

    // **Not fussiness.** A contribution of zero is a control that appears to work and changes
    // nothing, which is the dead-control failure in its purest form — and it would put an entry in
    // the log saying a colony did something it did not do. Refused here rather than on the screen so
    // that the client and the server's replay read the same answer out of the same function.
    data object NothingOffered : ContributeResult
}

// **The one verb in this game whose payoff is outside the colony**, and the only thing about that
// which reaches `core` is that the resources go and nothing comes back.
//
// What is deliberately *not* here: whether the player is in an alliance, whether that alliance has
// room in its vault, and what the basket is worth on the alliance's own ladder. Every one of those
// is a fact about other people, and `core` is pure, holds one colony, and must not learn that
// alliances exist. `applyVerb` takes `(verb, state, at)` and nothing else, which is the seam that
// keeps it that way; the refusals those facts produce are minted in the server's `replay`.
//
// The shape is `startAdaptation`'s and `startRun`'s — result type first, state first and `at` last,
// and the body in the stated order of validity, then requirements, then cost, then construct, then
// append. Here there are no requirements and nothing to construct, so it collapses to two checks
// and a `copy`.
fun contribute(state: GameState, amount: Resources, at: Instant): ContributeResult {
    // Emptiness before affordability, and the order is load-bearing: a colony with no stock covers a
    // basket of no stock, so the other order would answer `InsufficientResources` for a broke colony
    // and `NothingOffered` for a rich one, which is one control giving two names to one mistake.
    if (amount == Resources.of()) return ContributeResult.NothingOffered
    if (!state.resources.covers(amount)) return ContributeResult.InsufficientResources
    return ContributeResult.Started(
        state.copy(resources = state.resources.minus(amount))
            // `logging` and never a hand-written `copy(eventLog = ...)`: it is the only thing that
            // pays the running experience total as well as appending, and an append that forgot
            // would leave the player's level quietly low forever.
            .logging(Event.ResourcesContributed(amount = amount, at = at)),
    )
}
