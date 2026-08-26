package dev.fardavide.oltre.client.net.data

import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.AuthProvider
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.IdToken
import dev.fardavide.oltre.protocol.IdempotencyKey
import dev.fardavide.oltre.protocol.RejectionReason
import dev.fardavide.oltre.protocol.SessionResponse
import dev.fardavide.oltre.protocol.SessionToken
import dev.fardavide.oltre.protocol.SignInNonce
import dev.fardavide.oltre.protocol.SyncResponse
import dev.fardavide.oltre.protocol.VerbEnvelope
import dev.fardavide.oltre.protocol.VerbRejection
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

// **A fixed instant rather than a clock read**, for the reason nothing in `core` reads one: a fake
// whose expiries moved with the wall clock would make a test that asserts *"this refreshes at"* pass
// or fail depending on when it ran.
private val FAKE_SIGNED_IN_AT: Instant = Instant.parse("2026-08-26T09:00:00Z")

// The two lifetimes `#110` settled, said again here so a test reading this file can see them: an
// hour on the credential that travels on every request, ninety days on the one that does not.
private val FAKE_SESSION: SessionResponse = SessionResponse(
    apiVersion = ApiVersion.CURRENT,
    accessToken = SessionToken("fake.access.token"),
    accessExpiresAt = FAKE_SIGNED_IN_AT + 1.hours,
    refreshToken = SessionToken("fake.refresh.token"),
    refreshExpiresAt = FAKE_SIGNED_IN_AT + 90.days,
)

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

    // The session a sign-in or a refresh hands back. One fixed pair rather than a fresh one per
    // call, deliberately: a fake that minted a new string every time would let a client pass that
    // never stored what it was given, and *"the app keeps asking"* is exactly the bug the gate is
    // most likely to have.
    var session: SessionResponse = FAKE_SESSION,
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

    private val foundings = mutableListOf<SessionToken>()

    private val syncs = mutableListOf<Sent>()

    private val signIns = mutableListOf<SignIn>()

    private val deletions = mutableListOf<SessionToken>()

    // One request, as the two things it actually carries.
    data class Sent(val access: SessionToken, val envelopes: List<VerbEnvelope>)

    // A sign-in, as the three things it carries. The provider is a field here even though it is the
    // *path* on the wire, because a test asserting "they tapped Apple" should not have to know which
    // method that turned into.
    data class SignIn(val provider: AuthProvider, val idToken: IdToken, val nonce: SignInNonce)

    fun lastSync(): Sent? = syncs.lastOrNull()

    // The whole list, because both count and order matter here: *how many times a dead server was
    // asked* is what a backoff test is about, and *which envelopes went up on the second attempt*
    // is what an idempotency test is about.
    fun syncs(): List<Sent> = syncs.toList()

    fun foundings(): List<SessionToken> = foundings.toList()

    fun signIns(): List<SignIn> = signIns.toList()

    // **What `DELETE /v1/account` was asked with**, and it is a list rather than a flag because
    // deleting twice is not an error and a test about the second attempt has to be able to see it.
    fun deletions(): List<SessionToken> = deletions.toList()

    override suspend fun signInWithApple(idToken: IdToken, nonce: SignInNonce): ApiResult<SessionResponse> =
        signIn(AuthProvider.APPLE, idToken, nonce)

    override suspend fun signInWithGoogle(idToken: IdToken, nonce: SignInNonce): ApiResult<SessionResponse> =
        signIn(AuthProvider.GOOGLE, idToken, nonce)

    // **A fresh pair, and the same pair every time.** A fake that minted a new string per call would
    // let a client pass that never stored what it was given — the bug being guarded against is a
    // client that keeps asking rather than one that keeps a session.
    override suspend fun refresh(refreshToken: SessionToken): ApiResult<SessionResponse> =
        refuseOrElse { ApiResult.Answered(session) }

    override suspend fun deleteAccount(access: SessionToken): ApiResult<Unit> = refuseOrElse {
        deletions += access
        // Everything this server holds about them goes, which is what makes signing in again a new
        // colony rather than the old one — `players.id` is a surrogate key, and that is the whole
        // reason the second sign-in cannot land back on the first row.
        colony = null
        ApiResult.Answered(Unit)
    }

    override suspend fun foundColony(access: SessionToken): ApiResult<SyncResponse> {
        foundings += access
        return answer(emptyList()) {
            // Idempotent, like the route it doubles: a second call after a lost response gets the
            // colony that is already there rather than a second galaxy.
            colony = colony ?: founds
        }
    }

    override suspend fun sync(access: SessionToken, envelopes: List<VerbEnvelope>): ApiResult<SyncResponse> {
        syncs += Sent(access, envelopes)
        return answer(envelopes) {}
    }

    private fun signIn(
        provider: AuthProvider,
        idToken: IdToken,
        nonce: SignInNonce,
    ): ApiResult<SessionResponse> {
        signIns += SignIn(provider, idToken, nonce)
        return refuseOrElse { ApiResult.Answered(session) }
    }

    // The three refusals every call shares, in the order the real server applies them. Lifted out of
    // `answer` so that the routes with no colony behind them get exactly the same treatment — a fake
    // where `offline` meant something different on the gate than on a sync would let a broken client
    // through on the one screen that has nowhere to fall back to.
    private fun <T> refuseOrElse(block: () -> ApiResult<T>): ApiResult<T> {
        if (offline) return ApiResult.Unreachable
        transientErrors.removeFirstOrNull()?.let { return ApiResult.Refused(it) }
        error?.let { return ApiResult.Refused(it) }
        return block()
    }

    private fun answer(envelopes: List<VerbEnvelope>, before: () -> Unit): ApiResult<SyncResponse> =
        refuseOrElse { answered(envelopes, before) }

    private fun answered(envelopes: List<VerbEnvelope>, before: () -> Unit): ApiResult<SyncResponse> {
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
