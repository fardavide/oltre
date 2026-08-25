package dev.fardavide.oltre.client.net.data

import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.IdempotencyKey
import dev.fardavide.oltre.protocol.RejectionReason
import dev.fardavide.oltre.protocol.SyncResponse
import dev.fardavide.oltre.protocol.VerbEnvelope
import dev.fardavide.oltre.protocol.VerbRejection

// **A server that is not there.** Handwritten, per the repository's no-mocking-framework rule, and
// it is the one this slice exists to deliver as much as `OltreApi` is: `#106` §8 — the whole
// behaviour and screenshot suite runs on the desktop target, `App()` is about to require a network,
// and the suite cannot reach production and must not try. Without this ready, `#113` turns every
// behaviour test in the repository red at once.
//
// **What it deliberately does not do is run the game.** A real sync replays each verb through
// `core` and hands back what the colony became; this one hands back whatever colony it was given.
// That is not a corner cut — a second dispatch from `ClientVerb` into `core`'s twelve functions is
// a second place a thirteenth verb can go missing, which is precisely the failure `ClientVerbTest`
// and `offlineRule` exist to make impossible. A test that wants the colony to change says what it
// changed to.
//
// What it **does** model is the two server behaviours the client is built around, because a fake
// that got either wrong would let a broken client pass: **a key already applied is reported applied
// and not applied twice**, and **a refusal comes back inside a successful response** rather than as
// an error.
class FakeOltreApi(

    // The colony this server holds. Null is a player who has none, which is not a failure: it is
    // what a first launch of the online build meets before founding.
    var colony: GameSnapshot? = null,

    // What `foundColony` adopts when this server holds nothing yet. A real one mints the galaxy
    // itself; this one is told what to pretend it minted, because a seed is a decision and a fake
    // has no business making one.
    var founds: GameSnapshot? = null,

    // Nothing answers. Every call reads as `ApiResult.Unreachable`, which is the state the outbox
    // exists for.
    var offline: Boolean = false,

    // The server answers, and says no. Takes precedence over the colony, exactly as `admit` does on
    // the far end: a request that never got past the door was never about a colony.
    var error: ApiError? = null,

    // **The response is lost after the work was done** — the flaky train connection, and the whole
    // reason an idempotency key is not optional. One call applies its envelopes and then reads as
    // `Unreachable`; the flag clears itself, so the retry sees a server that already holds the keys.
    var losesNextResponse: Boolean = false,
) : OltreApi {

    // Verbs this server refuses rather than applies, and why. Keyed by the verb and not by the key,
    // because a caller scripting this knows what it tapped and does not know what was minted for it.
    val refusals: MutableMap<ClientVerb, RejectionReason> = mutableMapOf()

    // **Errors that happen once each and are then gone**, consumed in order, one per call, before
    // `error` applies. It exists because the one error worth retrying is transient by definition:
    // `ApiError.StaleColony` means another device won the compare-and-set *this time*, and a client
    // that could only be shown a server permanently stuck in that state could never be shown the
    // thing it actually does about it, which is ask again.
    val transientErrors: MutableList<ApiError> = mutableListOf()

    // The `applied_verbs` table, in a map. Append-only here as it is there.
    private val applied = mutableSetOf<IdempotencyKey>()

    private val foundings = mutableListOf<PlayerHandle>()

    private val syncs = mutableListOf<Sent>()

    // One request, as the two things it actually carries.
    data class Sent(val player: PlayerHandle, val envelopes: List<VerbEnvelope>)

    fun lastSync(): Sent? = syncs.lastOrNull()

    // The whole list, because both count and order matter here: *how many times a dead server was
    // asked* is what a backoff test is about, and *which envelopes went up on the second attempt*
    // is what an idempotency test is about.
    fun syncs(): List<Sent> = syncs.toList()

    fun foundings(): List<PlayerHandle> = foundings.toList()

    override suspend fun foundColony(player: PlayerHandle): ApiResult<SyncResponse> {
        foundings += player
        return answer(emptyList()) {
            // Idempotent, like the route it doubles: a second call after a lost response gets the
            // colony that is already there rather than a second galaxy.
            colony = colony ?: founds
        }
    }

    override suspend fun sync(player: PlayerHandle, envelopes: List<VerbEnvelope>): ApiResult<SyncResponse> {
        syncs += Sent(player, envelopes)
        return answer(envelopes) {}
    }

    private fun answer(envelopes: List<VerbEnvelope>, before: () -> Unit): ApiResult<SyncResponse> {
        if (offline) return ApiResult.Unreachable
        transientErrors.removeFirstOrNull()?.let { return ApiResult.Refused(it) }
        error?.let { return ApiResult.Refused(it) }
        before()
        val held = colony ?: return ApiResult.Refused(ApiError.NoColony)

        val appliedNow = LinkedHashSet<IdempotencyKey>()
        val rejectedNow = mutableListOf<VerbRejection>()
        val answered = mutableSetOf<IdempotencyKey>()
        for (envelope in envelopes) {
            // A key repeated inside one request is answered once — `replay`'s guard, and it is here
            // because `SyncResponse` refuses to be built with a key rejected twice.
            if (!answered.add(envelope.idempotencyKey)) continue
            when {
                envelope.idempotencyKey in applied -> appliedNow += envelope.idempotencyKey
                refusals[envelope.verb] != null ->
                    rejectedNow += VerbRejection(envelope, refusals.getValue(envelope.verb))

                else -> {
                    applied += envelope.idempotencyKey
                    appliedNow += envelope.idempotencyKey
                }
            }
        }

        // **After the work, not before it.** That is what makes this a lost *response* rather than
        // a lost request, and it is the only arrangement that exercises the key.
        if (losesNextResponse) {
            losesNextResponse = false
            return ApiResult.Unreachable
        }

        return ApiResult.Answered(
            SyncResponse(
                apiVersion = ApiVersion.CURRENT,
                snapshot = held,
                applied = appliedNow,
                rejected = rejectedNow,
            ),
        )
    }
}
