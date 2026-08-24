package dev.fardavide.oltre.protocol

import dev.fardavide.oltre.core.GameSnapshot
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.time.Instant

// **The key that makes a retry safe**, and it is not optional. A verb whose response is lost on a
// flaky train connection gets retried; without a key that is a double-spend, and `buildShips` would
// happily take the money twice.
//
// Minted at the edge, for the reason the galaxy seed is: `core` reads no clock and no random source,
// so the thing that mints one is whatever already holds both. What can be checked here is only that
// something was minted — the shape of the string is the minting client's business, and pinning it
// would strand the day it changes.
@Serializable
@JvmInline
value class IdempotencyKey(val value: String) {

    init {
        require(value.isNotBlank()) { "an idempotency key is minted at the edge and cannot be blank" }
    }
}

// One verb, when the player says they did it, and the key that lets it be sent twice safely.
//
// **`clientInstant` is a claim rather than a fact**, and the honest thing to do with it is state the
// residual rather than pretend it away — `#106` §3. The server clamps it into
// `[lastAcceptedAt, serverNow]`, so a modified client can claim it acted at the *start* of its
// offline window instead of the end, which buys at most one window's head start on one job. In a
// single-player game against scripted AI that is not worth defending against; the clamp is what
// stops it being unbounded. Revisit when there is a second player to take something from.
//
// The clamp lives on the server (`#108`) and not here, because a client cannot know `serverNow` and
// a type that could hold only clamped instants would be a type the client could not construct.
@Serializable
data class VerbEnvelope(
    val verb: ClientVerb,
    val clientInstant: Instant,
    val idempotencyKey: IdempotencyKey,
)

// Everything queued since the last sync, in the order the player tapped it. **Order is load-bearing
// and the list says so**: two upgrades on one facility, or a purchase and the dispatch it pays for,
// are only replayable in the sequence they happened.
//
// An empty list is the normal case rather than a degenerate one — it is what opening the app sends,
// and what comes back is the colony brought up to date.
@Serializable
data class SyncRequest(
    val apiVersion: ApiVersion,
    val envelopes: List<VerbEnvelope>,
)

// The authoritative colony, and what became of what was sent.
//
// **A rejection is data and not an exception**, which is the difference between this and `ApiError`:
// the sync succeeded, the colony came back, and one of the things the player queued did not survive
// the replay. The client has to be able to say *which* — a row whose verb landed and a row whose
// verb was refused cannot look the same, which is the dead-control rule reaching the outbox.
@Serializable
data class SyncResponse(
    val apiVersion: ApiVersion,
    val snapshot: GameSnapshot,
    // A set rather than a list, because a key is unique by construction and a list would have two
    // ways to say the same thing.
    val applied: Set<IdempotencyKey>,
    val rejected: List<VerbRejection>,
) {
    init {
        val refused = rejected.map { it.envelope.idempotencyKey }
        // A client folds one list into "these landed" and the other into "these did not", so a key
        // in both would put one row in two places and leave no honest way to draw it.
        //
        // **These two are the only guards in the module that judge the far end**, and unlike
        // `IdempotencyKey`'s they run on *decode* as well as on construction — which is the point.
        // A response that cannot be read coherently is one `#112` turns into `ApiError.Malformed`,
        // exactly as `GameSave.decode` turns a broken model invariant into a `Failure`, rather than
        // one the client renders half of.
        require(refused.none { it in applied }) {
            "a verb cannot be applied and rejected at once: was ${refused.filter { it in applied }}"
        }
        require(refused.distinct().size == refused.size) {
            "a verb is rejected once: was $refused"
        }
    }
}

// Which envelope did not survive, and why. The envelope whole rather than its key alone: the client
// has the queued verb already, but the screen that explains this is rendered from what came back,
// and a lookup that missed would be a row saying nothing.
@Serializable
data class VerbRejection(
    val envelope: VerbEnvelope,
    val reason: RejectionReason,
)
