package dev.fardavide.oltre.client.net.data

import dev.fardavide.oltre.core.BuildShipsResult
import dev.fardavide.oltre.core.GameSnapshot
import dev.fardavide.oltre.core.GameState
import dev.fardavide.oltre.core.StartAdaptationResult
import dev.fardavide.oltre.core.StartResearchResult
import dev.fardavide.oltre.core.StartRunResult
import dev.fardavide.oltre.core.StartSurveyResult
import dev.fardavide.oltre.core.StartUpgradeResult
import dev.fardavide.oltre.core.advance
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
import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.ApiVersion
import dev.fardavide.oltre.protocol.AuthProvider
import dev.fardavide.oltre.protocol.ClientVerb
import dev.fardavide.oltre.protocol.IdToken
import dev.fardavide.oltre.protocol.IdempotencyKey
import dev.fardavide.oltre.protocol.PlayerProfile
import dev.fardavide.oltre.protocol.RejectionReason
import dev.fardavide.oltre.protocol.SessionResponse
import dev.fardavide.oltre.protocol.SessionToken
import dev.fardavide.oltre.protocol.SignInNonce
import dev.fardavide.oltre.protocol.SyncResponse
import dev.fardavide.oltre.protocol.VerbEnvelope
import dev.fardavide.oltre.protocol.VerbRejection
import kotlinx.coroutines.CompletableDeferred
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

// **What an account holds before anybody has chosen**, and what it holds again once it is deleted.
// A factory rather than a default on `PlayerProfile` itself: nothing on this wire has one — that is
// what `RequiredFieldsTest` pins — and null here means *has not chosen* rather than *this build does
// not say*, which is a distinction a default would quietly erase.
private val UNCHOSEN: PlayerProfile = PlayerProfile(name = null, mark = null)

// **A server that is not there.** Handwritten, per the repository's no-mocking-framework rule, and
// it is the one this slice exists to deliver as much as `OltreApi` is: `#106` §8 — the whole
// behaviour and screenshot suite runs on the desktop target, `App()` is about to require a network,
// and the suite cannot reach production and must not try. Without this ready, `#113` turns every
// behaviour test in the repository red at once.
//
// **What it does not do by default is run the game.** A real sync replays each verb through `core`
// and hands back what the colony became; this one hands back whatever colony it was given. That is
// not a corner cut — a second dispatch from `ClientVerb` into `core`'s twelve functions is a second
// place a thirteenth verb can go missing, which is precisely the failure `ClientVerbTest` and
// `offlineRule` exist to make impossible. A test that wants the colony to change says what it
// changed to.
//
// **`replays` is the opt-in that #113 needed, and the objection above is answered rather than
// waived.** Once `App` sends every tap through `ColonySync`, a fake that ignored what it was sent
// would hand the *unchanged* colony back and visibly undo the tap — so the behaviour suite would be
// asserting against a server that cannot be the one it ships against. The second dispatch is
// `applyVerb` below, and what makes it safe is that it is a `when` with **no `else`**: a thirteenth
// verb cannot go missing there, it fails to compile. Off by default, so every test written against
// the shallower fake still means what it meant.
//
// What it **does** model is the two server behaviours the client is built around, because a fake
// that got either wrong would let a broken client pass: **a key already applied is reported applied
// and not applied twice**, and **a refusal comes back inside a successful response** rather than as
// an error.
class FakeOltreApi(

    // The colony this server holds. Null is a player who has none, which is not a failure: it is
    // what a first launch of the online build meets before founding.
    var colony: GameSnapshot? = null,

    // **The name and the mark this server holds**, which is an account's and not a colony's — so it
    // survives `foundColony` and outlives every snapshot put in `colony`. Both halves null is a
    // player who has never opened the editor, which is what every account founded before this slice
    // reads; what the strip draws for that is the strip's call and not this server's.
    var profile: PlayerProfile = UNCHOSEN,

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

    // **The two profile routes saying no while everything else answers.** `error` above is the whole
    // server refusing and `transientErrors` is the *next call* refusing whichever route that turns
    // out to be — neither can express the one state this slice turns on: a launch or a sign-in that
    // otherwise succeeds, with the account read not landing. That is what leaves `App`'s `profile`
    // null, and a client that treated it as an empty profile would send `{name: null}` and have the
    // server write SQL NULL over a name it never read. A fake that could not produce the state would
    // leave the whole of it untestable.
    var profileError: ApiError? = null,

    // **The response is lost after the work was done** — the flaky train connection, and the whole
    // reason an idempotency key is not optional. One call applies its envelopes and then reads as
    // `Unreachable`; the flag clears itself, so the retry sees a server that already holds the keys.
    var losesNextResponse: Boolean = false,

    // The session a sign-in or a refresh hands back. One fixed pair rather than a fresh one per
    // call, deliberately: a fake that minted a new string every time would let a client pass that
    // never stored what it was given, and *"the app keeps asking"* is exactly the bug the gate is
    // most likely to have.
    var session: SessionResponse = FAKE_SESSION,

    // **Whether this server runs the game**, which is what makes a tap that reaches it come back
    // having happened. See the note above the class for why it is off by default and why the
    // dispatch it turns on is safe.
    //
    // What it does *not* model is the clamp and the freshness window — the instant is the
    // envelope's, unadjusted. Those are `Replay.kt`'s and they are about a colony two devices are
    // writing to; a fake that guessed at them would be a second opinion on a rule with one home.
    var replays: Boolean = false,
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

    private val profileWrites = mutableListOf<PlayerProfile>()

    // **What has left the phone, as opposed to what the account was made to hold.** Two lists rather
    // than one because they answer two different questions, and the difference only exists while a
    // request is held: a write this server has *taken* is one the client is no longer sitting on, and
    // a client blocked behind a lock somebody else is holding leaves nothing here at all.
    private val profilesTaken = mutableListOf<PlayerProfile>()

    // **A profile call the far end has taken and not yet answered**, which is the one thing this
    // fake could not express and the one thing two of this slice's defects live inside. Everything
    // else here answers in the same breath it is asked, so a suspending call never actually
    // suspends — and a test written against it can never have two requests in flight at once, nor
    // catch the frame that composes while one is outstanding.
    //
    // Held rather than delayed, because a delay is a length and this is an *order*: what a test
    // needs to say is "while this one is out", not "for 40ms".
    private var heldReads: CompletableDeferred<Unit>? = null

    private var heldWrites: CompletableDeferred<Unit>? = null

    // **The colony route, held the same way**, and it is the one that says what the rest of the app
    // is doing while a request is out: the tick loop is the only thing in this game that asks on its
    // own, and a fake that always answered in the same breath could never be asked whether the clock
    // went on running underneath it.
    private var heldSyncs: CompletableDeferred<Unit>? = null

    // **And the one call in the app that cannot be repeated**, held for the reason the other two are:
    // a deletion is launched on a scope that outlives the session it was made under, and what its
    // answer is then allowed to touch is only askable while it is out.
    private var heldDeletions: CompletableDeferred<Unit>? = null

    // **How many syncs are sitting here unanswered right now**, which is a different question from
    // how many have been sent and is the one a test about *while a request is out* has to ask. The
    // count rather than a flag, because a launch retries and the answer has to stay true for as long
    // as any of them is still waiting.
    private var syncsHeldNow: Int = 0

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

    // **What this server was actually made to hold**, in the order it was told. A list rather than a
    // flag for `deletions()`'s reason — a rename that had to be made twice is exactly what a test
    // about a refusal and the retry after it is looking at — and a write that never got past the
    // door leaves nothing here, which is how *"offline saved nothing"* is asserted rather than
    // assumed.
    fun profileWrites(): List<PlayerProfile> = profileWrites.toList()

    // See `profilesTaken` above: what reached the far end, whether or not it was ever answered.
    fun profilesTaken(): List<PlayerProfile> = profilesTaken.toList()

    // **Take the call, and then answer it** — one pair per route. Kept as methods rather than as a
    // public `CompletableDeferred` so that the coroutine type stays inside this module: `OltreApi`'s
    // own vocabulary is `ApiResult`, and a caller scripting a fake should not have to speak a second
    // one to say *while that request is still out*.
    fun holdProfileReads() {
        heldReads = CompletableDeferred()
    }

    fun answerProfileReads() {
        heldReads?.complete(Unit)
    }

    fun holdProfileWrites() {
        heldWrites = CompletableDeferred()
    }

    fun answerProfileWrites() {
        heldWrites?.complete(Unit)
    }

    fun holdSyncs() {
        heldSyncs = CompletableDeferred()
    }

    fun answerSyncs() {
        heldSyncs?.complete(Unit)
    }

    // See `syncsHeldNow`: a request this server has taken and not answered.
    fun syncsHeld(): Int = syncsHeldNow

    fun holdDeletions() {
        heldDeletions = CompletableDeferred()
    }

    fun answerDeletions() {
        heldDeletions?.complete(Unit)
    }

    override suspend fun signInWithApple(idToken: IdToken, nonce: SignInNonce): ApiResult<SessionResponse> =
        signIn(AuthProvider.APPLE, idToken, nonce)

    override suspend fun signInWithGoogle(idToken: IdToken, nonce: SignInNonce): ApiResult<SessionResponse> =
        signIn(AuthProvider.GOOGLE, idToken, nonce)

    // **A fresh pair, and the same pair every time.** A fake that minted a new string per call would
    // let a client pass that never stored what it was given — the bug being guarded against is a
    // client that keeps asking rather than one that keeps a session.
    override suspend fun refresh(refreshToken: SessionToken): ApiResult<SessionResponse> =
        refuseOrElse { ApiResult.Answered(session) }

    override suspend fun deleteAccount(access: SessionToken): ApiResult<Unit> {
        heldDeletions?.await()
        return deleted(access)
    }

    private fun deleted(access: SessionToken): ApiResult<Unit> = refuseOrElse {
        deletions += access
        // Everything this server holds about them goes, which is what makes signing in again a new
        // colony rather than the old one — `players.id` is a surrogate key, and that is the whole
        // reason the second sign-in cannot land back on the first row.
        colony = null
        // **The name and the mark go with it**, and this line is the one most easily forgotten: the
        // profile hangs off the account rather than the colony, so a fake that cleared only the
        // colony would let a client pass that showed the deleted player's name to whoever signed in
        // next.
        profile = UNCHOSEN
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
        // Recorded before the hold, so `syncs()` means *what left the phone* and a test can say
        // "while that one is out" about a request that is genuinely out.
        syncs += Sent(access, envelopes)
        heldSyncs?.let { held ->
            syncsHeldNow++
            // `finally`, because a caller giving up is exactly what this count has to be able to
            // report: a request nobody is waiting for any more is not one this server is holding.
            try {
                held.await()
            } finally {
                syncsHeldNow--
            }
        }
        return answer(envelopes) {}
    }

    // **`NoColony` is not one of the answers here**, unlike everything below `answer`: a profile
    // belongs to the account, so a player who has not founded still has one and it reads as both
    // halves null. A fake that refused this before founding would make the strip's own default —
    // what it draws when the answer is null — unreachable in a test.
    override suspend fun profile(access: SessionToken): ApiResult<PlayerProfile> {
        // Before the refusals rather than after them, because what is being modelled is a request
        // that has left the phone: a server holding one has already taken it.
        heldReads?.await()
        return refuseOrElse { profileError?.let { ApiResult.Refused(it) } ?: ApiResult.Answered(profile) }
    }

    // **Replaces whole and answers with what is now held**, which is the far end's shape rather than
    // an echo of what was sent: the real one writes the row and reads it back. Identical here
    // because nothing in this fake edits a profile on the way in — and that is worth stating, since
    // a fake that echoed would hide a client drawing its own draft.
    override suspend fun setProfile(access: SessionToken, profile: PlayerProfile): ApiResult<PlayerProfile> {
        // Before the hold, because what is being modelled is a request that has left the phone: a
        // server holding one has already taken it.
        profilesTaken += profile
        heldWrites?.await()
        return refuseOrElse {
            // **Refused before it is recorded**, which is what makes `profileWrites()` mean *what the
            // account was made to hold* rather than *what was posted at it*. A refused write left
            // nothing behind on the far end and must leave nothing here.
            profileError?.let { return@refuseOrElse ApiResult.Refused(it) }
            profileWrites += profile
            this.profile = profile
            ApiResult.Answered(this.profile)
        }
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
                    // **Advance first, then apply at that instant** — `GameSession.acting`'s order
                    // and `Replay.kt`'s, load-bearing for the same reason: applying a verb to a
                    // colony that has not accrued the time yet spends resources it does not have.
                    if (replays) {
                        val held = colony
                        if (held != null) {
                            val at = maxOf(envelope.clientInstant, held.lastUpdatedAt)
                            val caught = advance(held.state, from = held.lastUpdatedAt, to = at)
                            colony = held.copy(state = applyVerb(envelope.verb, caught, at), lastUpdatedAt = at)
                        }
                    }
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
                // **Re-read rather than the `held` above**, because a replay moves it: what goes back
                // is the colony *after* the verbs, which is the whole of what an authoritative answer
                // means. With `replays` off the two are the same object.
                snapshot = colony ?: held,
                applied = appliedNow,
                rejected = rejectedNow,
            ),
        )
    }
}

// **`:server`'s `applyVerb` said again on this side of the wire**, and the duplication is forced
// rather than chosen: module rule 8 forbids `:client` from depending on `:server`, and a fake of a
// server that could not run a verb would hand back a colony where nothing the player did had
// happened.
//
// **A `when` with no `else`, which is the whole of what makes the second copy safe.** A thirteenth
// verb cannot reach here without somebody writing an arm for it — it fails to compile, exactly as it
// does on the server and in `offlineRule`.
//
// **A refusal is not modelled and does not need to be**: what `core` refuses it refuses by handing
// the state back, and every result type below has a `Started` member carrying the new one. A test
// that wants a verb refused says so through `refusals`, which is the server's `RejectionReason`
// travelling as data and is the thing a client actually has to draw.
private fun applyVerb(verb: ClientVerb, state: GameState, at: Instant): GameState = when (verb) {
    is ClientVerb.StartUpgrade -> (startUpgrade(state, verb.building, at) as? StartUpgradeResult.Started)?.state
    is ClientVerb.StartResearch -> (startResearch(state, verb.technology, at) as? StartResearchResult.Started)?.state
    is ClientVerb.StartAdaptation ->
        (startAdaptation(state, verb.technology, at) as? StartAdaptationResult.Started)?.state

    is ClientVerb.BuildShips -> (buildShips(state, verb.ships, at) as? BuildShipsResult.Started)?.state
    is ClientVerb.StartRun -> (
        startRun(
            state = state,
            target = verb.target,
            gathering = verb.gathering,
            ships = verb.ships,
            window = verb.window,
            at = at,
        ) as? StartRunResult.Started
        )?.state

    is ClientVerb.StartSurvey -> (startSurvey(state, verb.target, at) as? StartSurveyResult.Started)?.state

    // The six below cannot refuse at all — they return a bare `GameState`, so there is nothing to
    // inspect and nothing to fall back to.
    is ClientVerb.ToggleAlert -> toggleAlert(state, verb.target)
    is ClientVerb.CycleHullAlert -> cycleHullAlert(state, verb.ship)
    ClientVerb.ToggleFlightAlerts -> toggleFlightAlerts(state)
    is ClientVerb.SetAlertMode -> setAlertMode(state, verb.mode)
    is ClientVerb.ToggleAlertCategory -> toggleAlertCategory(state, verb.category)
    is ClientVerb.SetAlertDelivery -> setAlertDelivery(state, verb.delivery)
} ?: state
