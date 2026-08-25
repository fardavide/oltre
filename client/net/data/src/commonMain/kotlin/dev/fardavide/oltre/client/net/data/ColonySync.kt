package dev.fardavide.oltre.client.net.data

import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.SyncResponse
import dev.fardavide.oltre.protocol.VerbEnvelope
import dev.fardavide.oltre.protocol.VerbRejection
import kotlinx.coroutines.delay
import kotlin.time.Clock

// **What a sync did**, and it is three answers because there are three different things to do next.
sealed interface SyncOutcome {

    // The authoritative colony, and what became of what was sent. **`rejected` is never empty by
    // accident and never dropped**: a row whose verb landed and a row whose verb was refused cannot
    // look the same, which is the dead-control rule reaching the outbox. The queue has already been
    // reconciled against both lists by the time this is handed back.
    data class Synced(val colony: GameSnapshot, val rejected: List<VerbRejection>) : SyncOutcome

    // **Nothing to say and nothing to undo.** Either nothing answered, or another device won the
    // compare-and-set every time — and those are one member rather than two because they are one
    // instruction: the queue is intact, the colony on screen is still the last one the server
    // agreed to, and the answer is to sync again in a moment.
    //
    // `ApiError`'s taxonomy splits what gets different *words*. These get the same silence, so
    // splitting them would be a distinction the player could never be shown.
    data object NotNow : SyncOutcome

    // The server answered, and the answer is not about any one verb: sign in again, update the
    // app, found a colony first. Each of those is a different sentence, which is why `ApiError`
    // travels whole rather than being flattened here.
    data class Failed(val error: ApiError) : SyncOutcome
}

// **What one tap did.** It differs from `SyncOutcome` in the case that only exists here: a tap that
// did not reach the server has a second question — *was it kept?* — and the answer is the whole of
// `#106` §3.
sealed interface ActOutcome {

    data class Synced(val colony: GameSnapshot, val rejected: List<VerbRejection>) : ActOutcome

    // On disk before this returned, and it goes up on the next sync. **The row it came from cannot
    // look identical to a row whose verb landed** — that is `#113`'s to draw, and this is the fact
    // it draws from.
    data object Queued : ActOutcome

    // **Refused, and it says why by being its own answer.** The verb is galaxy-touching, so its
    // outcome depends on a world somebody else may now hold, and the game will not promise to
    // replay it later — `#106` §3, *"look, don't act, from day one"*. `#113` turns this into a
    // sentence in the product's own idiom; a data module cannot build one, and a control that just
    // did nothing is the failure the whole rule exists to prevent.
    //
    // **What it does not claim is that nothing happened.** A response lost after the server acted
    // arrives here too, and the reconciliation for that is the next sync: the authoritative colony
    // comes back with the dispatch in it. This says the tap was not *kept*, which is the only thing
    // the client can honestly know.
    data object NotQueueable : ActOutcome

    data class Failed(val error: ApiError) : ActOutcome
}

// **The client asking, and the only thing above the transport that decides anything.** Three
// questions — found me a colony, bring me up to date, I tapped this — and one loop underneath all
// of them.
//
// Every collaborator is a parameter for the reason every seam in this repository is one, and two of
// them are the reason this class is a plain `…Test` rather than something that needs a network: the
// clock is what stamps `clientInstant`, and the key mint is what makes a retry safe. A class that
// reached for `Clock.System` and a random source of its own could not be asked whether a second
// attempt carried the first attempt's key, which is the one property the whole mechanism exists
// for.
class ColonySync(
    private val api: OltreApi,
    private val outbox: Outbox,
    private val keys: IdempotencyKeys,
    private val clock: Clock,
    private val retry: RetryPolicy,
) {

    // The one-time call a first launch makes. Idempotent at the far end, so a retry after a lost
    // response finds the colony that is already there rather than minting a second galaxy.
    suspend fun found(player: PlayerHandle): SyncOutcome = drain(retry) { api.foundColony(player) }

    // Bring the colony up to date and drain whatever is queued. An empty queue is the normal case:
    // it is what opening the app sends.
    suspend fun sync(player: PlayerHandle): SyncOutcome {
        // Read once. A retry asks the same question again rather than a different one — the outbox
        // cannot have changed, because nothing was answered.
        val outgoing = outbox.queued()
        return drain(retry) { api.sync(player, outgoing) }
    }

    // **A tap.** The order below is the whole of it and each step is load-bearing:
    //
    //  1. Mint the envelope **once**, here. A key minted per attempt would make every retry a
    //     double-spend, which is the failure `IdempotencyKey` exists to prevent.
    //  2. Offer it to the outbox **before** trying to send it, so a process killed mid-request
    //     still has it. The outbox is also the one place that reads `ClientVerb.offlineRule`, so
    //     the queue-or-refuse split is answered once and never re-derived.
    //  3. Send everything outstanding, not just this verb. Order is load-bearing — a purchase and
    //     the dispatch it pays for are only replayable in sequence.
    //
    // **One attempt, where `sync` retries**, and that is a choice about who is waiting rather than
    // an omission: the outbox has already taken the verb, so a second and third attempt buys a
    // colony four seconds later and nothing else, with the screen waiting the whole time.
    suspend fun act(player: PlayerHandle, verb: ClientVerb): ActOutcome {
        val envelope = VerbEnvelope(
            verb = verb,
            // A claim rather than a fact, and the server says so: it clamps this into
            // `[lastAcceptedAt, serverNow]`, so a phone whose clock is wrong costs at most one
            // offline window and never more.
            clientInstant = clock.now(),
            idempotencyKey = keys.mint(),
        )
        val kept = outbox.queue(envelope)
        val outgoing = when (kept) {
            QueueResult.QUEUED -> outbox.queued()
            // Not written, so it is not in that list. It rides along this once and is not kept —
            // which is exactly what look-don't-act means: it may happen now or not at all.
            QueueResult.NOT_QUEUEABLE -> outbox.queued() + envelope
        }

        return when (val outcome = drain(RetryPolicy.ONCE) { api.sync(player, outgoing) }) {
            is SyncOutcome.Synced -> ActOutcome.Synced(outcome.colony, outcome.rejected)
            is SyncOutcome.Failed -> ActOutcome.Failed(outcome.error)
            SyncOutcome.NotNow -> when (kept) {
                QueueResult.QUEUED -> ActOutcome.Queued
                QueueResult.NOT_QUEUEABLE -> ActOutcome.NotQueueable
            }
        }
    }

    // **Ask, and ask again only for the two answers that are worth asking again about.**
    //
    // Everything else in `ApiError` is terminal by construction and returning it immediately is the
    // point: a second `Unauthenticated` is still `Unauthenticated`, and three attempts at it would
    // delay the sign-in screen by four seconds for no reason.
    private suspend fun drain(policy: RetryPolicy, send: suspend () -> ApiResult<SyncResponse>): SyncOutcome {
        repeat(policy.attempts) { index ->
            when (val result = send()) {
                is ApiResult.Answered -> return reconcile(result.value)

                // **`StaleColony` is answered by syncing again, never by saying anything.** Nothing
                // the player queued was judged — the server lost its own compare-and-set three
                // times and wrote nothing — so the verbs are still in the outbox and the colony on
                // screen is still the one they last saw. There is nothing to tell them and nothing
                // to undo. See `Endpoints.kt`, which is the only thing that produces it.
                is ApiResult.Refused -> if (result.error != ApiError.StaleColony) {
                    return SyncOutcome.Failed(result.error)
                }

                ApiResult.Unreachable -> Unit
            }

            // Running off the end of the list **is** the last attempt, which is why the policy is a
            // list of waits rather than a count and a formula.
            policy.waits.getOrNull(index)?.let { delay(it) }
        }
        return SyncOutcome.NotNow
    }

    // **Reconciliation, and it is one line because `SyncResponse` was shaped for it.** Every
    // envelope the server was sent comes back in exactly one of the two lists, so what is left in
    // the outbox afterwards is precisely what was not judged.
    //
    // **A rejected verb leaves the queue and is handed up in the same breath.** Dropping it is not
    // swallowing it: replaying a refusal would get the same refusal forever, and the caller is
    // given the whole rejection — the envelope and the reason — to say so with.
    private suspend fun reconcile(response: SyncResponse): SyncOutcome {
        outbox.answered(response.applied + response.rejected.map { it.envelope.idempotencyKey })
        return SyncOutcome.Synced(response.snapshot, response.rejected)
    }
}
