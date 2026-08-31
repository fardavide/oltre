package dev.fardavide.oltre.client.net.data

import dev.fardavide.oltre.protocol.ApiError
import dev.fardavide.oltre.protocol.IdToken
import dev.fardavide.oltre.protocol.PlayerProfile
import dev.fardavide.oltre.protocol.SessionResponse
import dev.fardavide.oltre.protocol.SessionToken
import dev.fardavide.oltre.protocol.SignInNonce
import dev.fardavide.oltre.protocol.SyncResponse
import dev.fardavide.oltre.protocol.VerbEnvelope

// **What came back, and there are three answers because there are three different things to do.**
//
// The split that matters is between the second and the third. `Refused` is the server having
// answered — it read the request, formed an opinion, and `ApiError` is that opinion; the outbox
// cannot help and retrying the same request will get the same reply. `Unreachable` is nothing
// having answered at all, which is the case the outbox exists for: the verbs are still queued, the
// colony is still the one last seen, and the honest thing is to try again later.
//
// `ApiError` deliberately has no member for *"no connection"*, and this is why: it is the taxonomy
// of what a **server** says, and a server that says nothing has said nothing.
sealed interface ApiResult<out T> {

    data class Answered<out T>(val value: T) : ApiResult<T>

    data class Refused(val error: ApiError) : ApiResult<Nothing>

    data object Unreachable : ApiResult<Nothing>
}

// **The whole of what the client can ask.** Two questions about a colony, three about who is
// holding it, and two about what that player calls themselves — `#112` shipped only the first pair,
// because until `#113` there was no way to become anybody, and the last pair belongs to an account
// that outlives every colony hung off it.
//
// It is an interface rather than the Ktor class itself so that the suite has something to hand the
// shell that is not a socket — `#106` §8: the whole behaviour and screenshot suite runs on the
// desktop target, and it must not try to reach production.
//
// **No method takes a request object.** The client's API version is a fact about this build rather
// than a choice a caller makes, so the implementation states it and a caller cannot get it wrong.
// What a caller supplies is the only thing it knows.
interface OltreApi {

    // **Two methods rather than one taking a provider, because the provider is the path.** That is
    // `Auth.kt`'s call and it is not cosmetic: the two tokens are verified against different
    // issuers, different audiences and different key sets, so a single route would have to branch on
    // its own body to decide who to believe.
    //
    // Neither carries a session — this is what produces one.
    suspend fun signInWithApple(idToken: IdToken, nonce: SignInNonce): ApiResult<SessionResponse>

    suspend fun signInWithGoogle(idToken: IdToken, nonce: SignInNonce): ApiResult<SessionResponse>

    // **What `ApiError.SessionExpired` is answered with**, and the reason a player who checks in
    // twice a day never sees the gate twice. A fresh *pair* comes back rather than a fresh access
    // token alone, so the ninety days slides forward every time the game is opened.
    suspend fun refresh(refreshToken: SessionToken): ApiResult<SessionResponse>

    // App Review guideline 5.1.1(v), and the one call whose success carries nothing at all: the
    // server answers `204`, and it answers `204` the second time too.
    suspend fun deleteAccount(access: SessionToken): ApiResult<Unit>

    // Found this player's colony, and mint its galaxy while doing it. Idempotent at the far end — a
    // second call after a lost response gets the colony that is already there rather than a second
    // one — which is why it needs no envelope and no key of its own.
    suspend fun foundColony(access: SessionToken): ApiResult<SyncResponse>

    // Everything queued since the last sync goes up in the order the player tapped it; the
    // authoritative colony comes back, and what became of each verb comes back with it. An empty
    // list is the normal case rather than a degenerate one — it is what opening the app sends.
    suspend fun sync(access: SessionToken, envelopes: List<VerbEnvelope>): ApiResult<SyncResponse>

    // **What this player chose to be called and to wear**, which hangs off the account and not off
    // the colony — a route of its own rather than a field on `SyncResponse`, and `Profile.kt` argues
    // why: this wire refuses unknown keys, so a field added to a shipped response is a response the
    // build already on somebody's phone cannot decode, while a route added costs an older client
    // nothing because it never calls it.
    //
    // **The profile and not the envelope that carried it**, which is where this pair parts company
    // with `sync`: a caller reads `applied` and `rejected` off a `SyncResponse` and does something
    // about each, whereas a `ProfileResponse` adds only the version — a fact about which build
    // answered, settled by the time it is decoded and nothing a caller acts on.
    suspend fun profile(access: SessionToken): ApiResult<PlayerProfile>

    // **Replaces the profile whole rather than patching the half that moved**, which is
    // `SetProfileRequest`'s call and not this transport's: under a merge `null` would have to mean
    // *leave it alone* and *clear it* at once, and clearing is the only way out of a name a player
    // regrets.
    //
    // **Not a verb, so nothing holds it.** A rename mutates no `GameState` and has no instant to be
    // replayed against, so there is nothing for the outbox to store and nothing for the server to
    // validate; with no signal this refuses exactly as `deleteAccount` does. What comes back is what
    // the account now holds rather than an echo of what went up.
    suspend fun setProfile(access: SessionToken, profile: PlayerProfile): ApiResult<PlayerProfile>
}
